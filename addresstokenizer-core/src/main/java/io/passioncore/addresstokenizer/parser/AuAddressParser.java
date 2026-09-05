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
import io.passioncore.addresstokenizer.parser.support.LeadingNameNoiseStripper;
import io.passioncore.addresstokenizer.parser.support.NewlineFallbackSplitter;

/**
 * Australian address tokenizer.
 *
 * Typical formats:
 *   123 Collins Street, Melbourne VIC 3000
 *   Unit 5/42 George Street, Sydney NSW 2000
 */
@Component
public class AuAddressParser implements AddressParser {

    public static final Pattern STATE_POSTCODE =
        Pattern.compile("\\b(NSW|VIC|QLD|SA|WA|TAS|ACT|NT|" +
            "(?i:New South Wales|Victoria|Queensland|South Australia|" +
            "Western Australia|Tasmania|Australian Capital Territory|" +
            "Northern Territory))\\s+(\\d{4})\\b");

    private static final Pattern UNIT_SLASH =
        Pattern.compile("^(\\d+)/(\\d+[A-Za-z]?)\\s+");

    private static final Pattern UNIT_WORD =
        // [\w./-]+ (not just [\w/-]+) -- AU floor.unit-style suite numbering (e.g.
        // "Suite 14.02" = Suite 02 on Level 14) uses a literal dot inside the value; without
        // it the match stops at the dot and leaks the remainder ("02") into the street name.
        Pattern.compile("(?i)^(Unit|Level|Lvl|Suite|Ste|Shop|Apt|Flat)\\s+([\\w./-]+)\\s*,?\\s*");

    private static final Pattern HOUSE_NO =
        Pattern.compile("^(\\d+[A-Za-z]?)\\s+");

    private static final Pattern STREET_TYPE =
        Pattern.compile("(?i)\\b(Street|St|Road|Rd|Avenue|Ave|Drive|Dr|Lane|Ln|" +
            "Court|Ct|Place|Pl|Way|Close|Cl|Crescent|Cres|Terrace|Tce|" +
            "Parade|Pde|Circuit|Cct|Boulevard|Blvd|Highway|Hwy|Grove|Gve|" +
            "Rise|Row|Walk|Loop|Link|Esplanade|Esp|Quay|Mall)\\b\\.?");

    // Redundant self-references to this parser's own country -- not real city names,
    // and would otherwise be mislabeled as CITY.
    private static final Set<String> SELF_REFERENCE = Set.of("AU", "AUSTRALIA");

    @Override public String postalCodePattern() { return STATE_POSTCODE.pattern(); }
    @Override public String countryCode() { return "AU"; }
    @Override public int detectionPriority() { return 60; }

    @Override
    public ParsedAddress parse(String raw, String country) {
        List<AddressToken> tokens = new ArrayList<>();
        String addr = raw.trim().replaceAll("\\s{2,}", " ");
        addr = LeadingNameNoiseStripper.strip(addr);

        String remaining = addr;
        Matcher spMatcher = STATE_POSTCODE.matcher(addr);
        int spStart = -1;
        String state = null, postcode = null;
        while (spMatcher.find()) {
            state    = spMatcher.group(1);
            postcode = spMatcher.group(2);
            spStart  = spMatcher.start();
        }
        if (spStart >= 0 && state != null && postcode != null) {
            tokens.add(token(TokenType.STATE_CODE, state));
            tokens.add(token(TokenType.POSTAL_CODE, postcode));
            remaining = addr.substring(0, spStart).trim().replaceAll("[,\\s]+$", "");
        }

        List<String> partList = new ArrayList<>(List.of(NewlineFallbackSplitter.split(remaining, 0)));
        while (partList.size() >= 2 && SELF_REFERENCE.contains(partList.get(partList.size() - 1).trim().toUpperCase())) {
            partList.remove(partList.size() - 1);
        }
        int len = partList.size();
        String streetLine = remaining;
        boolean cityFromComma = len >= 2;
        if (cityFromComma) {
            String city = partList.get(len - 1).trim();
            tokens.add(token(TokenType.CITY, city));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len - 1; i++) {
                if (i > 0) sb.append(", ");
                sb.append(partList.get(i).trim());
            }
            streetLine = sb.toString();
        }

        Matcher slashMatcher = UNIT_SLASH.matcher(streetLine);
        if (slashMatcher.find()) {
            String unitVal = slashMatcher.group(1);
            tokens.add(token(TokenType.UNIT, unitVal));
            streetLine = slashMatcher.group(2) + " " + streetLine.substring(slashMatcher.end()).trim();
        } else {
            // AU addresses commonly stack multiple unit-like prefixes (e.g. "Suite 14.02,
            // Level 14, ..." -- the suite's own floor.unit numbering restated explicitly as
            // a separate "Level" segment). Consume every leading UNIT_WORD match, not just
            // the first, so the whole stack becomes one UNIT value instead of the second
            // prefix ("Level 14") leaking into the street name.
            List<String> unitParts = new ArrayList<>();
            Matcher unitMatcher = UNIT_WORD.matcher(streetLine);
            while (unitMatcher.find() && unitMatcher.start() == 0) {
                unitParts.add(unitMatcher.group(1) + " " + unitMatcher.group(2));
                streetLine = streetLine.substring(unitMatcher.end()).trim();
                unitMatcher = UNIT_WORD.matcher(streetLine);
            }
            if (!unitParts.isEmpty()) {
                tokens.add(token(TokenType.UNIT, String.join(", ", unitParts)));
            }
        }

        Matcher houseMatcher = HOUSE_NO.matcher(streetLine);
        if (houseMatcher.find()) {
            String houseNo = houseMatcher.group(1);
            tokens.add(token(TokenType.HOUSE_NO, houseNo));
            streetLine = streetLine.substring(houseMatcher.end()).trim();
        }

        Matcher stMatcher = STREET_TYPE.matcher(streetLine);
        int stStart = -1, stEnd = -1;
        String stType = null;
        while (stMatcher.find()) {
            stType  = stMatcher.group(1);
            stStart = stMatcher.start();
            stEnd   = stMatcher.end();
        }
        String streetName = (stStart > 0) ? streetLine.substring(0, stStart).trim() : streetLine.trim();
        if (!streetName.isEmpty()) {
            tokens.add(token(TokenType.STREET_NAME, streetName));
        }
        if (stStart >= 0) {
            tokens.add(token(TokenType.STREET_TYPE, stType));
        }

        // No comma separated the suburb from the street (e.g. "80 Druitt Street SYDNEY
        // NSW 2000") -- the AU convention places the suburb right after the street type,
        // before state+postcode, so whatever remains there is the suburb.
        if (!cityFromComma && stEnd >= 0) {
            String cityCandidate = streetLine.substring(stEnd).trim();
            if (!cityCandidate.isEmpty()) {
                tokens.add(token(TokenType.CITY, cityCandidate));
            }
        }

        return new ParsedAddress(raw, "AU", tokens);
    }

    private AddressToken token(TokenType type, String value) {
        return new AddressToken(type, value);
    }
}
