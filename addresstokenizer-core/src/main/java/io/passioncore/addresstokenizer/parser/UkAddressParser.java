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

@Component
public class UkAddressParser implements AddressParser {

    public static final Pattern POSTCODE =
        Pattern.compile("\\b([A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern UNIT =
        Pattern.compile("(?i)^(Flat|Apt|Apartment|Unit|Suite|Room|Floor)\\s+([\\w/-]+)\\s*,?\\s*");

    private static final Pattern HOUSE_NO =
        Pattern.compile("^(\\d+[A-Za-z]?)\\s+");

    private static final Pattern STREET_TYPE =
        Pattern.compile("(?i)\\b(Street|St|Road|Rd|Avenue|Ave|Lane|Ln|Drive|Dr|" +
            "Close|Cl|Court|Ct|Place|Pl|Way|Grove|Rise|Mews|Terrace|Tce|" +
            "Crescent|Cres|Parade|Esplanade|Gardens|Park|Hill|Walk|Row|" +
            "Square|Sq|Circus|Gate|Green|Common|Broadway)\\b\\.?");

    @Override public String postalCodePattern() { return POSTCODE.pattern(); }
    @Override public String countryCode() { return "GB"; }
    @Override public int detectionPriority() { return 10; }

    @Override
    public ParsedAddress parse(String raw, String country) {
        List<AddressToken> tokens = new ArrayList<>();
        String addr = raw.trim().replaceAll("\\s{2,}", " ");

        String remaining = addr;
        Matcher pcMatcher = POSTCODE.matcher(addr);
        String lastPostcode = null;
        int lastStart = -1, lastEnd = -1;
        while (pcMatcher.find()) {
            lastPostcode = pcMatcher.group(1).toUpperCase().replace(" ", "");
            if (lastPostcode.length() > 3) {
                lastPostcode = lastPostcode.substring(0, lastPostcode.length() - 3)
                    + " " + lastPostcode.substring(lastPostcode.length() - 3);
            }
            lastStart = pcMatcher.start();
            lastEnd   = pcMatcher.end();
        }
        if (lastPostcode != null) {
            tokens.add(token(TokenType.POSTAL_CODE, lastPostcode));
            remaining = addr.substring(0, lastStart).trim().replaceAll("[,\\s]+$", "");
        }

        String[] parts = remaining.split(",");
        int len = parts.length;
        String streetLine = remaining;
        if (len >= 2) {
            String city = parts[len - 1].trim();
            tokens.add(token(TokenType.CITY, city));
            if (len >= 3) {
                String nbhd = parts[len - 2].trim();
                if (!nbhd.isEmpty()) {
                    tokens.add(token(TokenType.NEIGHBORHOOD, nbhd));
                }
            }
            StringBuilder sb = new StringBuilder();
            int endIdx = len >= 3 ? len - 2 : len - 1;
            for (int i = 0; i < endIdx; i++) {
                if (i > 0) sb.append(", ");
                sb.append(parts[i].trim());
            }
            streetLine = sb.toString();
        }

        Matcher unitMatcher = UNIT.matcher(streetLine);
        if (unitMatcher.find()) {
            String unitVal = unitMatcher.group(1) + " " + unitMatcher.group(2);
            tokens.add(token(TokenType.UNIT, unitVal));
            streetLine = streetLine.substring(unitMatcher.end()).trim();
        }

        Matcher houseMatcher = HOUSE_NO.matcher(streetLine);
        if (houseMatcher.find()) {
            String houseNo = houseMatcher.group(1);
            tokens.add(token(TokenType.HOUSE_NO, houseNo));
            streetLine = streetLine.substring(houseMatcher.end()).trim();
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

    private AddressToken token(TokenType type, String value) {
        return new AddressToken(type, value);
    }
}
