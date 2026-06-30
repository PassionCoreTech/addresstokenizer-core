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

import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Quebec French postal addresses (Canada Post / Poste Canada rules).
 *
 * <p>This class is intentionally <em>not</em> an {@link AddressParser} bean
 * in the main parser map — it is invoked exclusively via {@link CaAddressParser}
 * after {@link QuebecFrenchDetector} has confirmed the address is French.</p>
 */
@Slf4j
@Component
public class QuebecFrenchParser {

    private static final Pattern FR_STREET = Pattern.compile(
        "(?i)^(\\d+[A-Z]?)"
        + "\\s+(RUE|BOUL(?:EVARD)?|BLVD|AV(?:E(?:NUE)?)?|CHEMIN|ROUTE"
        + "|RANG|MONTEE|COTE|RUELLE|IMPASSE|PLACE|CROISSANT|CARRE)"
        + "\\s+(.+?)"
        + "(?:\\s+(OUEST|EST|NORD|SUD|O\\.?|N\\.?|S\\.?))?$"
    );

    private static final Pattern UNIT_SUFFIX = Pattern.compile(
        "(?i)\\b(BUREAU|SUITE|APP(?:ARTEMENT)?\\.?|LOCAL)\\s*(\\d+[A-Z]?)"
    );

    private static final Pattern POSTAL_FULL =
        Pattern.compile("\\b([A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d)\\b");

    private static final Pattern POSTAL_FSA =
        Pattern.compile("\\b([A-Z]\\d[A-Z])(?!\\s?\\d[A-Z]\\d)\\b");

    private static final Pattern PROVINCE =
        Pattern.compile("(?i)\\b(QC|PQ|AB|BC|MB|NB|NL|NS|NT|NU|ON|PE|SK|YT)\\b");

    public ParsedAddress parse(String raw, QuebecFrenchDetector.DetectionResult detection) {
        return doParse(raw);
    }

    public ParsedAddress parse(String raw, String country) {
        return doParse(raw);
    }

    private ParsedAddress doParse(String raw) {
        List<AddressToken> tokens = new ArrayList<>();
        String addr = raw.trim().toUpperCase().replaceAll("\\s{2,}", " ");

        String[] lines = addr.split("[,\\n\\r]");
        String streetLine;
        String municipalityLine;
        if (lines.length > 1 && lines[0].trim().matches("\\d+[A-Za-z]?")) {
            streetLine       = lines[0].trim() + " " + lines[1].trim();
            municipalityLine = lines.length > 2 ? joinFrom(lines, 2).trim() : "";
        } else {
            streetLine       = lines[0].trim();
            municipalityLine = lines.length > 1 ? joinFrom(lines, 1).trim() : "";
        }

        String remaining = municipalityLine;

        String postalCode = null;
        int    postalStart = -1;
        Matcher fullPc = POSTAL_FULL.matcher(remaining);
        while (fullPc.find()) {
            postalCode = fullPc.group(1).replace(" ", "");
            postalCode = postalCode.substring(0, 3) + " " + postalCode.substring(3);
            postalStart = fullPc.start();
        }
        if (postalStart >= 0) {
            tokens.add(tok(TokenType.POSTAL_CODE, postalCode));
            remaining = remaining.substring(0, postalStart).trim();
        } else {
            Matcher fsaPc = POSTAL_FSA.matcher(remaining);
            if (fsaPc.find()) {
                tokens.add(tok(TokenType.POSTAL_CODE, fsaPc.group(1)));
                remaining = remaining.substring(0, fsaPc.start()).trim();
            }
        }

        Matcher provMatcher = PROVINCE.matcher(remaining);
        if (provMatcher.find()) {
            tokens.add(tok(TokenType.STATE_CODE, provMatcher.group(1)));
            remaining = remaining.substring(0, provMatcher.start()).trim();
        }

        if (!remaining.isBlank()) {
            tokens.add(tok(TokenType.CITY, remaining.trim()));
        }

        Matcher unitMatcher = UNIT_SUFFIX.matcher(streetLine);
        if (unitMatcher.find()) {
            String unitVal = unitMatcher.group(2);
            tokens.add(tok(TokenType.UNIT, unitVal));
            streetLine = (streetLine.substring(0, unitMatcher.start())
                    + streetLine.substring(unitMatcher.end())).trim();
        }

        Matcher frMatcher = FR_STREET.matcher(streetLine);
        if (frMatcher.find()) {
            tokens.add(tok(TokenType.HOUSE_NO,    frMatcher.group(1).toUpperCase()));
            tokens.add(tok(TokenType.STREET_TYPE, frMatcher.group(2).toUpperCase()));
            tokens.add(tok(TokenType.STREET_NAME, frMatcher.group(3).toUpperCase()));
            if (frMatcher.group(4) != null) {
                tokens.add(tok(TokenType.DIRECTION, frMatcher.group(4).toUpperCase()));
            }
        } else {
            log.debug("QuebecFrenchParser: FR_STREET not matched for [{}] — using fallback", raw);
            Pattern houseOnly = Pattern.compile("^(\\d+[A-Z]?)\\s+");
            Matcher houseMatcher = houseOnly.matcher(streetLine);
            if (houseMatcher.find()) {
                tokens.add(tok(TokenType.HOUSE_NO, houseMatcher.group(1)));
                String rest = streetLine.substring(houseMatcher.end()).trim();
                if (!rest.isBlank()) {
                    tokens.add(tok(TokenType.STREET_NAME, rest));
                }
            } else if (!streetLine.isBlank()) {
                tokens.add(tok(TokenType.STREET_NAME, streetLine));
            }
        }

        return new ParsedAddress(raw, "CA", tokens);
    }

    private static AddressToken tok(TokenType type, String value) {
        return new AddressToken(type, value);
    }

    private static String joinFrom(String[] arr, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < arr.length; i++) {
            String part = arr[i].trim();
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
