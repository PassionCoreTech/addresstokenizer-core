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
public class UkAddressParser implements AddressParser {

    public static final Pattern POSTCODE =
        Pattern.compile("\\b([A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern UNIT =
        Pattern.compile("(?i)^(Flat|Apt|Apartment|Unit|Suite|Room|Floor)\\s+([\\w/-]+)\\s*,?\\s*");

    // NNth-Floor-shaped content (e.g. "6TH FLOOR", "18th Floor") -- unlike UNIT above, this can
    // appear anywhere in the street line, not just at the start.
    private static final Pattern ORDINAL_FLOOR =
        Pattern.compile("(?i)\\b(\\d+(?:ST|ND|RD|TH))\\s+(FLOOR|FL)\\b\\s*,?\\s*");

    private static final Pattern HOUSE_NO =
        Pattern.compile("^(\\d+[A-Za-z]?)\\s+");

    private static final Pattern STREET_TYPE =
        Pattern.compile("(?i)\\b(Street|St|Road|Rd|Avenue|Ave|Lane|Ln|Drive|Dr|" +
            "Close|Cl|Court|Ct|Place|Pl|Way|Grove|Rise|Mews|Terrace|Tce|" +
            "Crescent|Cres|Parade|Esplanade|Gardens|Park|Hill|Walk|Row|" +
            "Square|Sq|Circus|Gate|Green|Common|Broadway)\\b\\.?");

    // Redundant self-references to this parser's own country -- not real city names,
    // and would otherwise be mislabeled as CITY.
    private static final Set<String> SELF_REFERENCE =
        Set.of("UK", "GB", "UNITED KINGDOM", "GREAT BRITAIN");

    @Override public String postalCodePattern() { return POSTCODE.pattern(); }
    @Override public String countryCode() { return "GB"; }
    @Override public int detectionPriority() { return 10; }

    @Override
    public ParsedAddress parse(String raw, String country) {
        List<AddressToken> tokens = new ArrayList<>();
        String addr = raw.trim().replaceAll("\\s{2,}", " ");
        addr = LeadingNameNoiseStripper.strip(addr);

        PostalCodeStripper.StripResult pc = PostalCodeStripper.stripLastMatch(addr, POSTCODE);
        String remaining = pc.remainingBefore();
        if (pc.matchedValue() != null) {
            tokens.add(token(TokenType.POSTAL_CODE, normalizePostcode(pc.matchedValue())));
        }

        // A real bank-published address (ANZ's own ISO 20022 guide) puts
        // town/country content AFTER the postcode instead of the postcode being the final
        // element -- the pre-migration code took `remainingBefore` on faith and never looked at
        // `remainingAfter`, so that trailing content (here, the real city) was silently dropped
        // and the last segment BEFORE the postcode got misread as CITY instead. When
        // remainingAfter carries real content, prefer it as the city source.
        String cityFromAfter = extractCityFromTrailingText(pc.remainingAfter());

        List<String> partList = new ArrayList<>(List.of(NewlineFallbackSplitter.split(remaining, 0)));
        String streetLine = remaining;
        if (cityFromAfter != null) {
            tokens.add(token(TokenType.CITY, cityFromAfter));

            // CITY already came from remainingAfter, but `remaining` (everything before the
            // postcode) can still carry real NEIGHBORHOOD/building content (e.g. "THE CORN
            // EXCHANGE") -- don't just drop it. Set aside any trailing floor/unit-shaped segments
            // first (e.g. "6TH FLOOR") so they aren't mistaken for a neighborhood; they're folded
            // back into streetLine below so ORDINAL_FLOOR/UNIT still get a chance at them.
            List<String> beforeSegments = SelfReferenceStripper.strip(partList, SELF_REFERENCE);
            int contentEnd = beforeSegments.size();
            while (contentEnd > 0
                    && CommaSegmentCityExtractor.looksLikeFloorOrUnitMarker(beforeSegments.get(contentEnd - 1))) {
                contentEnd--;
            }
            List<String> content = beforeSegments.subList(0, contentEnd);
            List<String> setAside = beforeSegments.subList(contentEnd, beforeSegments.size());

            StringBuilder sb = new StringBuilder();
            if (content.size() >= 2) {
                String neighborhood = content.get(content.size() - 1).trim();
                if (!neighborhood.isEmpty()) {
                    tokens.add(token(TokenType.NEIGHBORHOOD, neighborhood));
                }
                for (int i = 0; i < content.size() - 1; i++) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(content.get(i).trim());
                }
            } else {
                for (String s : content) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s.trim());
                }
            }
            for (String s : setAside) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.trim());
            }
            streetLine = sb.toString();
        } else {
            List<String> segments = SelfReferenceStripper.strip(partList, SELF_REFERENCE);
            CommaSegmentCityExtractor.Result cityResult =
                CommaSegmentCityExtractor.extractLastAsCity(segments, true);
            if (cityResult.city() != null) {
                tokens.add(token(TokenType.CITY, cityResult.city()));
            }
            if (cityResult.neighborhood() != null) {
                tokens.add(token(TokenType.NEIGHBORHOOD, cityResult.neighborhood()));
            }
            streetLine = cityResult.streetLine();
        }

        Matcher unitMatcher = UNIT.matcher(streetLine);
        if (unitMatcher.find()) {
            String unitVal = unitMatcher.group(1) + " " + unitMatcher.group(2);
            tokens.add(token(TokenType.UNIT, unitVal));
            streetLine = streetLine.substring(unitMatcher.end()).trim();
        } else {
            Matcher ordinalFloorMatcher = ORDINAL_FLOOR.matcher(streetLine);
            if (ordinalFloorMatcher.find()) {
                String unitVal = (ordinalFloorMatcher.group(1) + " " + ordinalFloorMatcher.group(2)).toUpperCase();
                tokens.add(token(TokenType.UNIT, unitVal));
                streetLine = (streetLine.substring(0, ordinalFloorMatcher.start())
                        + streetLine.substring(ordinalFloorMatcher.end()))
                    .trim().replaceAll("^[,\\s]+|[,\\s]+$", "").replaceAll("\\s{2,}", " ");
            }
        }

        HouseNumberExtractor.Result houseResult = HouseNumberExtractor.extractLeading(streetLine, HOUSE_NO);
        if (houseResult.houseNo() != null) {
            tokens.add(token(TokenType.HOUSE_NO, houseResult.houseNo()));
            streetLine = houseResult.remaining();
        }

        Matcher stMatcher = STREET_TYPE.matcher(streetLine);
        int stStart = -1, stEnd = -1;
        String streetType = null;
        while (stMatcher.find()) {
            streetType = stMatcher.group(1);
            stStart    = stMatcher.start();
            stEnd      = stMatcher.end();
        }
        String streetName = (stStart > 0) ? streetLine.substring(0, stStart).trim() : streetLine.trim();
        if (!streetName.isEmpty()) {
            tokens.add(token(TokenType.STREET_NAME, streetName));
        }
        if (stStart >= 0) {
            tokens.add(token(TokenType.STREET_TYPE, streetType));
        }

        return new ParsedAddress(raw, "GB", tokens);
    }

    /** UK postcodes are stored space-separated before the final 3-character "inward" part
     *  (e.g. {@code "SW1A 2AA"}), regardless of how the input was spaced. */
    private static String normalizePostcode(String rawMatch) {
        String value = rawMatch.toUpperCase().replace(" ", "");
        if (value.length() > 3) {
            value = value.substring(0, value.length() - 3) + " " + value.substring(value.length() - 3);
        }
        return value;
    }

    /** Recovers CITY from text trailing the postcode (e.g. ", LONDON,
     *  GB") when present, instead of silently discarding it. Returns {@code null} when there's
     *  nothing usable, so the caller falls back to the classic "postcode at end" extraction. */
    private static String extractCityFromTrailingText(String remainingAfter) {
        if (remainingAfter == null || remainingAfter.isBlank()) {
            return null;
        }
        List<String> segments = SelfReferenceStripper.strip(
            new ArrayList<>(List.of(NewlineFallbackSplitter.split(remainingAfter, 0))), SELF_REFERENCE);
        if (segments.isEmpty()) {
            return null;
        }
        String candidate = segments.get(segments.size() - 1).trim();
        // SelfReferenceStripper only strips a self-reference when something more specific
        // precedes it -- a lone ", GB" with nothing else has no earlier
        // segment to strip down to, so it survives as the sole entry here. Reject it explicitly
        // rather than mistake the country code itself for a city.
        if (candidate.isEmpty() || SELF_REFERENCE.contains(candidate.toUpperCase())) {
            return null;
        }
        return candidate;
    }

    private AddressToken token(TokenType type, String value) {
        return new AddressToken(type, value);
    }
}
