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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;

/**
 * German address tokenizer. House number comes AFTER street name.
 *
 * Typical format:
 *   Hauptstraße 42, 10115 Berlin
 */
@Component
public class DeAddressParser implements AddressParser {

    private static final Pattern POSTAL_CITY =
        Pattern.compile("(\\d{5})\\s+(.+)$");

    private static final Pattern HOUSE_NO_SUFFIX =
        Pattern.compile("\\s+(\\d+\\s*[a-zA-Z]?)$");

    private static final Pattern STREET_TYPE =
        Pattern.compile("(?i)(Straße|Strasse|Str\\.?|Weg|Allee|Gasse|Platz|" +
            "Ring|Damm|Chaussee|Ufer|Steig|Stieg|Graben|Pfad|Berg|Dorf)$");

    @Override public String postalCodePattern() { return POSTAL_CITY.pattern(); }
    @Override public String countryCode() { return "DE"; }
    @Override public int detectionPriority() { return 20; }

    @Override
    public ParsedAddress parse(String raw, String country) {
        List<AddressToken> tokens = new ArrayList<>();
        String addr = raw.trim().replaceAll("\\s+", " ");

        String[] parts = addr.split(",", 2);
        String streetPart = parts[0].trim();
        String cityPart   = parts.length > 1 ? parts[1].trim() : "";

        if (cityPart.isEmpty()) {
            Matcher pcMatcher = POSTAL_CITY.matcher(streetPart);
            if (pcMatcher.find()) {
                cityPart   = pcMatcher.group(0).trim();
                streetPart = streetPart.substring(0, pcMatcher.start()).trim();
            }
        }

        if (!cityPart.isEmpty()) {
            int cityComma = cityPart.indexOf(',');
            if (cityComma >= 0) cityPart = cityPart.substring(0, cityComma).trim();

            Matcher pcMatcher = POSTAL_CITY.matcher(cityPart);
            if (pcMatcher.find()) {
                String postal = pcMatcher.group(1);
                String city   = pcMatcher.group(2).trim();
                tokens.add(token(TokenType.POSTAL_CODE, postal));
                tokens.add(token(TokenType.CITY, city));
            } else {
                tokens.add(token(TokenType.CITY, cityPart));
            }
        }

        Matcher houseMatcher = HOUSE_NO_SUFFIX.matcher(streetPart);
        if (houseMatcher.find()) {
            String houseNo = houseMatcher.group(1).trim();
            tokens.add(token(TokenType.HOUSE_NO, houseNo));
            streetPart = streetPart.substring(0, houseMatcher.start()).trim();
        }

        Matcher stMatcher = STREET_TYPE.matcher(streetPart);
        if (stMatcher.find()) {
            String stType = stMatcher.group(1);
            String streetName = streetPart.substring(0, stMatcher.start()).trim();
            if (!streetName.isEmpty()) {
                tokens.add(token(TokenType.STREET_NAME, streetName));
            } else {
                tokens.add(token(TokenType.STREET_NAME, streetPart));
            }
            tokens.add(token(TokenType.STREET_TYPE, stType));
        } else if (!streetPart.isEmpty()) {
            tokens.add(token(TokenType.STREET_NAME, streetPart));
        }

        return new ParsedAddress(raw, "DE", tokens);
    }

    private AddressToken token(TokenType type, String value) {
        return new AddressToken(type, value);
    }
}
