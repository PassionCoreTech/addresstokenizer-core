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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.NormalizationHints;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;

import lombok.extern.slf4j.Slf4j;

/**
 * Canadian address tokenizer following Canada Post formatting guidelines.
 *
 * <h3>Dispatch logic</h3>
 * <ol>
 *   <li>Scan {@link NormalizationHints} from the raw input.</li>
 *   <li>Run {@link QuebecFrenchDetector} — score-based language detection.</li>
 *   <li>If score ≥ 40 (FR_CA or FR_CA_PROBABLE) → delegate to {@link QuebecFrenchParser}.</li>
 *   <li>Otherwise (EN_CA) → apply standard English-Canadian parsing rules.</li>
 * </ol>
 */
@Slf4j
@Component
public class CaAddressParser implements AddressParser {

    public static final Pattern POSTAL_FULL =
        Pattern.compile("\\b([A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d)\\b");

    private static final Pattern POSTAL_FSA =
        Pattern.compile("\\b([A-Z]\\d[A-Z])(?!\\s?\\d[A-Z]\\d)\\b");

    public static final Pattern PROVINCE =
        Pattern.compile("(?i)\\b(AB|BC|MB|NB|NL|NS|NT|NU|ON|PE|QC|SK|YT|" +
            "ALBERTA|BRITISH COLUMBIA|MANITOBA|NEW BRUNSWICK|" +
            "NEWFOUNDLAND AND LABRADOR|NOVA SCOTIA|NORTHWEST TERRITORIES|" +
            "NUNAVUT|ONTARIO|PRINCE EDWARD ISLAND|QUEBEC|SASKATCHEWAN|YUKON)\\b");

    private static final Pattern STREET_LINE_UNIT =
        Pattern.compile("(?i)\\b(Unit|Level|Lvl|Suite|Ste|Shop|Apt|Flat)\\s+(\\d+[A-Z]?)\\b");
    private static final Pattern UNIT_CIVIC =
        Pattern.compile("^(#?\\d+[A-Z]?)\\s*-\\s*(\\d+[A-Z]?)\\s+");
    private static final Pattern HOUSE_NO =
        Pattern.compile("^(\\d+[A-Z]?)\\s+");
    private static final Pattern FLOOR =
        Pattern.compile(
            "(?i)\\b(?:Floor|Flr|FL|Level|Lvl)\\s*#?\\s*(\\d+[A-Z]?\\w*)" +
            "|\\b(\\d+(?:st|nd|rd|th)?)\\s+(?:Floor|Flr|Fl)\\b");

    private static final Pattern STREET_TYPE =
        Pattern.compile("(?i)\\b(Street|St|Road|Rd|Avenue|Ave|Lane|Ln|Drive|Dr|" +
            "Close|Cl|Court|Ct|Place|Pl|Way|Grove|Rise|Mews|Terrace|Tce|" +
            "Crescent|Cres|Parade|Esplanade|Gardens|Park|Hill|Walk|Row|" +
            "Square|Sq|Circus|Gate|Green|Common|Broadway|" +
            "Boulevard|Blvd|Chemin|Rang|Mont[eé]e|Route|Rte|All[eé]e|" +
            "Concession|Sideroad|Sdr|Townline|Private|Pvt)\\b");

    private final QuebecFrenchDetector qcDetector;
    private final QuebecFrenchParser   qcParser;

    @Autowired
    public CaAddressParser(QuebecFrenchDetector qcDetector,
                           QuebecFrenchParser   qcParser) {
        this.qcDetector = qcDetector;
        this.qcParser   = qcParser;
    }

    @Override public String postalCodePattern() { return POSTAL_FULL.pattern(); }
    @Override public String countryCode()        { return "CA"; }
    @Override public int    detectionPriority()  { return 20; }

    @Override
    public ParsedAddress parse(String raw, String country) {
        NormalizationHints hints = NormalizationHints.scan(raw);

        String upper = raw.trim().toUpperCase().replaceAll("\\s{2,}", " ");
        List<String> lines = List.of(upper.split("[,\\n\\r]+"));

        QuebecFrenchDetector.DetectionResult detection = qcDetector.detect(lines, hints);

        log.debug("CaAddressParser: lang={} score={} inputLen={}",
            detection.language(), detection.score(), raw == null ? 0 : raw.length());

        return switch (detection.language()) {
            case FR_CA, FR_CA_PROBABLE -> qcParser.parse(raw, detection);
            case EN_CA                 -> parseEnglishCa(raw);
        };
    }

    private static final Pattern UNIT_CIVIC_SOLO =
        Pattern.compile("^#(\\d+[A-Z]?)$");

    private ParsedAddress parseEnglishCa(String raw) {
        List<AddressToken> tokens = new ArrayList<>();
        String addr = raw.trim().toUpperCase().replaceAll("\\s{2,}", " ");

        List<String> segs = new ArrayList<>();
        for (String s : addr.split("[,\\n\\r]+")) {
            String t = s.trim();
            if (!t.isEmpty()) segs.add(t);
        }
        if (segs.isEmpty()) return new ParsedAddress(raw, "CA", tokens);

        // Step 1 — postal code (right-to-left scan)
        for (int i = segs.size() - 1; i >= 0; i--) {
            Matcher m = POSTAL_FULL.matcher(segs.get(i));
            if (m.find()) {
                String pc = m.group(1).replace(" ", "");
                pc = pc.substring(0, 3) + " " + pc.substring(3);
                tokens.add(token(TokenType.POSTAL_CODE, pc));
                String rest = segs.get(i).substring(0, m.start()).trim();
                if (rest.isBlank()) segs.remove(i); else segs.set(i, rest);
                break;
            }
        }
        if (tokens.stream().noneMatch(t2 -> t2.type() == TokenType.POSTAL_CODE)) {
            for (int i = segs.size() - 1; i >= 0; i--) {
                Matcher m = POSTAL_FSA.matcher(segs.get(i));
                if (m.find()) {
                    tokens.add(token(TokenType.POSTAL_CODE, m.group(1)));
                    String rest = segs.get(i).substring(0, m.start()).trim();
                    if (rest.isBlank()) segs.remove(i); else segs.set(i, rest);
                    break;
                }
            }
        }

        // Step 2 — province (right-to-left scan)
        for (int i = segs.size() - 1; i >= 0; i--) {
            Matcher m = PROVINCE.matcher(segs.get(i));
            if (m.find()) {
                tokens.add(token(TokenType.STATE_CODE, m.group(1)));
                String rest = (segs.get(i).substring(0, m.start()) + " "
                              + segs.get(i).substring(m.end())).trim();
                if (rest.isBlank()) segs.remove(i); else segs.set(i, rest);
                break;
            }
        }

        if (segs.isEmpty()) return new ParsedAddress(raw, "CA", tokens);

        // Step 3 — unit keywords and city
        String streetLine = null;
        String city = "";
        for (int i = 0; i < segs.size(); i++) {
            String seg = segs.get(i);
            Matcher um = STREET_LINE_UNIT.matcher(seg);
            StringBuffer sb = new StringBuffer();
            while (um.find()) {
                String unitVal = um.group(1).toUpperCase() + " " + um.group(2);
                tokens.add(token(TokenType.UNIT, unitVal));
                um.appendReplacement(sb, " ");
            }
            um.appendTail(sb);
            String leftover = sb.toString().replaceAll("\\s{2,}", " ").trim();

            if (streetLine == null) {
                if (!leftover.isBlank()) streetLine = leftover;
            } else {
                if (!leftover.isBlank()) {
                    Matcher soloHash = UNIT_CIVIC_SOLO.matcher(leftover);
                    if (soloHash.matches()) {
                        tokens.add(token(TokenType.UNIT, soloHash.group(1)));
                    } else if (!"CANADA".equals(leftover)) {
                        city = leftover;
                    }
                }
            }
        }
        if (streetLine == null) streetLine = "";

        // Step 4 — unit-civic or house number
        Matcher ucMatcher = UNIT_CIVIC.matcher(streetLine);
        boolean hasUnitCivic = ucMatcher.find();
        if (hasUnitCivic) {
            String unitVal  = ucMatcher.group(1).replace("#", "");
            String civicVal = ucMatcher.group(2);
            tokens.add(token(TokenType.UNIT,     unitVal));
            tokens.add(token(TokenType.HOUSE_NO, civicVal));
            streetLine = streetLine.substring(ucMatcher.end()).trim();
        } else {
            Matcher houseMatcher = HOUSE_NO.matcher(streetLine);
            if (houseMatcher.find()) {
                String houseNo = houseMatcher.group(1);
                tokens.add(token(TokenType.HOUSE_NO, houseNo));
                streetLine = streetLine.substring(houseMatcher.end()).trim();
            }
        }

        // Step 5 — floor
        Matcher floorMatcher = FLOOR.matcher(streetLine);
        if (floorMatcher.find()) {
            String floorVal = floorMatcher.group(1) != null
                    ? floorMatcher.group(1) : floorMatcher.group(2);
            tokens.add(token(TokenType.FLOOR, floorVal.toUpperCase()));
            streetLine = (streetLine.substring(0, floorMatcher.start())
                    + streetLine.substring(floorMatcher.end()))
                    .trim().replaceAll("^[,\\s]+|[,\\s]+$", "").replaceAll("\\s{2,}", " ");
        }

        // Step 6 — street type and name
        Matcher stMatcher = STREET_TYPE.matcher(streetLine);
        int stStart = -1;
        String stType = null;
        while (stMatcher.find()) {
            stType  = stMatcher.group(1);
            stStart = stMatcher.start();
        }
        String streetName = stStart > 0
            ? streetLine.substring(0, stStart).trim()
            : streetLine.trim();
        if (!streetName.isBlank()) {
            tokens.add(token(TokenType.STREET_NAME, streetName));
        }
        if (stStart >= 0) {
            tokens.add(token(TokenType.STREET_TYPE, stType));
        }

        // Step 7 — city
        if (!city.isBlank()) {
            tokens.add(token(TokenType.CITY, city));
        }

        return new ParsedAddress(raw, "CA", tokens);
    }

    private static AddressToken token(TokenType type, String value) {
        return new AddressToken(type, value);
    }
}
