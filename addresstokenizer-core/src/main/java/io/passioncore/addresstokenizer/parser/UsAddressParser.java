/*
 * Copyright (c) 2026 PassionCore Technologies Inc. (dev@passioncore.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package io.passioncore.addresstokenizer.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;
import io.passioncore.addresstokenizer.parser.support.CommaSegmentCityExtractor;
import io.passioncore.addresstokenizer.parser.support.HouseNumberExtractor;
import io.passioncore.addresstokenizer.parser.support.LeadingNameNoiseStripper;
import io.passioncore.addresstokenizer.parser.support.NewlineFallbackSplitter;
import io.passioncore.addresstokenizer.parser.support.PostalCodeStripper;
import io.passioncore.addresstokenizer.parser.support.SelfReferenceStripper;

@Component
public class UsAddressParser implements AddressParser {

    public static final Pattern ZIP =
        Pattern.compile("\\b(\\d{5}(?:-\\d{4})?)\\b");
    public static final Pattern STATE =
        Pattern.compile("\\b(" +
            "AL|AK|AZ|AR|CA|CO|CT|DE|FL|GA|HI|ID|IL|IN|IA|" +
            "KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|" +
            "OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY|DC|" +
            "DISTRICT OF COLUMBIA|" +
            "NORTH CAROLINA|NORTH DAKOTA|SOUTH CAROLINA|SOUTH DAKOTA|" +
            "NEW HAMPSHIRE|NEW JERSEY|NEW MEXICO|NEW YORK|WEST VIRGINIA|RHODE ISLAND|" +
            "ALABAMA|ALASKA|ARIZONA|ARKANSAS|CALIFORNIA|COLORADO|CONNECTICUT|" +
            "DELAWARE|FLORIDA|GEORGIA|HAWAII|IDAHO|ILLINOIS|INDIANA|IOWA|" +
            "KANSAS|KENTUCKY|LOUISIANA|MAINE|MARYLAND|MASSACHUSETTS|MICHIGAN|" +
            "MINNESOTA|MISSISSIPPI|MISSOURI|MONTANA|NEBRASKA|NEVADA|OHIO|" +
            "OKLAHOMA|OREGON|PENNSYLVANIA|TENNESSEE|TEXAS|UTAH|VERMONT|" +
            "VIRGINIA|WASHINGTON|WISCONSIN|WYOMING" +
            ")\\b");
    private static final Pattern HOUSE_NO =
        Pattern.compile("^(\\d+[A-Z]?)\\s+");
    private static final Pattern UNIT =
        Pattern.compile("(?i)(?:\\b(Apt|Suite|Ste|Unit)|(#))\\s*([\\w-]+)");
    private static final Pattern STREET_TYPE =
        Pattern.compile("(?i)\\b(Street|St|Avenue|Ave|Boulevard|Blvd|Drive|Dr|Road|Rd|Lane|Ln|Court|Ct|Place|Pl|Way|Circle|" +
            "Cir|Trail|Trl|Pike|Pkwy|Highway|Hwy|Freeway|Fwy|Expressway|Expy|Park)\\b\\.?");
    private static final Pattern FLOOR =
        Pattern.compile(
            "(?i)\\b(?:Floor|Flr|FL|Level|Lvl)\\s*#?\\s*(\\d+[A-Z]?\\w*)" +
            "|\\b(\\d+(?:st|nd|rd|th)?)\\s+(?:Floor|Flr|Fl)\\b");

    // Redundant self-references to this parser's own country -- not real city names,
    // and would otherwise be mislabeled as CITY.
    private static final Set<String> SELF_REFERENCE = Set.of("US", "USA", "UNITED STATES");

    @Override public String postalCodePattern() { return ZIP.pattern(); }
    @Override public String countryCode() { return "US"; }
    @Override public int detectionPriority() { return 50; }

    @Override
    public ParsedAddress parse(String raw, String country) {
        List<AddressToken> tokens = new ArrayList<>();
        String addr = raw.trim().replaceAll("\\s{2,}", " ");
        addr = LeadingNameNoiseStripper.strip(addr);

        PostalCodeStripper.StripResult zipResult = PostalCodeStripper.stripLastMatch(addr, ZIP);
        String remaining = zipResult.remainingBefore();
        if (zipResult.matchedValue() != null) {
            tokens.add(token(TokenType.POSTAL_CODE, zipResult.matchedValue()));
            // zipResult.remainingAfter() is intentionally not consulted here -- ZIP is the last
            // element in every US address shape this parser supports.
        }

        Matcher stateMatcher = STATE.matcher(remaining);
        int stateStart = -1, stateEnd = -1;
        String stateValue = null;
        while (stateMatcher.find()) {
            stateStart = stateMatcher.start();
            stateEnd   = stateMatcher.end();
            stateValue = stateMatcher.group();
        }
        if (stateStart >= 0) {
            tokens.add(token(TokenType.STATE_CODE, stateValue));
            remaining = remaining.substring(0, stateStart).trim().replaceAll("[,\\s]+$", "");
        }

        List<String> lines = SelfReferenceStripper.strip(
            Arrays.asList(NewlineFallbackSplitter.split(remaining, 0)), SELF_REFERENCE);
        CommaSegmentCityExtractor.Result cityResult =
            CommaSegmentCityExtractor.extractLastAsCity(lines, false);
        if (cityResult.city() != null) {
            tokens.add(token(TokenType.CITY, cityResult.city().toUpperCase()));
        }
        remaining = cityResult.streetLine();

        Matcher unitMatcher = UNIT.matcher(remaining);
        if (unitMatcher.find()) {
            String unitKeyword = unitMatcher.group(1) != null ? unitMatcher.group(1) : unitMatcher.group(2);
            String unitVal = unitKeyword + " " + unitMatcher.group(3);
            tokens.add(token(TokenType.UNIT, unitVal));
            remaining = remaining.substring(0, unitMatcher.start()).trim();
        }

        Matcher floorMatcher = FLOOR.matcher(remaining);
        if (floorMatcher.find()) {
            String floorVal = floorMatcher.group(1) != null ? floorMatcher.group(1) : floorMatcher.group(2);
            tokens.add(token(TokenType.FLOOR, floorVal.toUpperCase()));
            remaining = (remaining.substring(0, floorMatcher.start()) + remaining.substring(floorMatcher.end()))
                    .trim().replaceAll("^[,\\s]+|[,\\s]+$", "").replaceAll("\\s{2,}", " ");
        }

        HouseNumberExtractor.Result houseResult = HouseNumberExtractor.extractLeading(remaining, HOUSE_NO);
        if (houseResult.houseNo() != null) {
            tokens.add(token(TokenType.HOUSE_NO, houseResult.houseNo()));
            remaining = houseResult.remaining();
        }

        Matcher streetTypeMatcher = STREET_TYPE.matcher(remaining);
        String streetType = null;
        int streetTypeStart = -1, streetTypeEnd = -1;
        while (streetTypeMatcher.find()) {
            streetType      = streetTypeMatcher.group(1);
            streetTypeStart = streetTypeMatcher.start();
            streetTypeEnd   = streetTypeMatcher.end();
        }
        String streetName = (streetTypeStart > 0)
            ? remaining.substring(0, streetTypeStart).trim()
            : remaining.trim();
        if (!streetName.isEmpty()) {
            tokens.add(token(TokenType.STREET_NAME, streetName));
        }
        if (streetTypeStart >= 0) {
            tokens.add(token(TokenType.STREET_TYPE, streetType));
        }

        return new ParsedAddress(raw, "US", tokens);
    }

    private AddressToken token(TokenType type, String value) {
        return new AddressToken(type, value);
    }
}
