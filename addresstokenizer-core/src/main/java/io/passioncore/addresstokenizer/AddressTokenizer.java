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

package io.passioncore.addresstokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.passioncore.addresstokenizer.detector.CountryDetectorInterface;
import io.passioncore.addresstokenizer.healer.AddressHealer;
import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.NormalizationResult;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;
import io.passioncore.addresstokenizer.parser.AddressParser;
import io.passioncore.addresstokenizer.parser.GenericAddressParser;
import io.passioncore.addresstokenizer.utils.NormalizationUtil;

/**
 * Core tokenisation pipeline.
 *
 * <p>Returns a {@link ParsedAddress} containing the detected country code,
 * a flat list of typed tokens ({@link AddressToken}), named fields derived from
 * those tokens ({@link #tokensToFields}), and a basic {@code parseConfidence}
 * score (0.0–1.0) derived from token coverage alone. Enhanced confidence with
 * gazetteer validation is a Pro-tier feature ({@link ParsedAddress#diagnostics()}).</p>
 *
 * <h3>Pipeline (parseLines)</h3>
 * <ol>
 *   <li>SWIFT-normalize all lines.</li>
 *   <li>PO Box detection — stripped, held as token, reattached after parsing.</li>
 *   <li>Country detection.</li>
 *   <li>Optional split healing (when {@link AddressHealer} bean is present — pro only).</li>
 *   <li>Country re-detect after healing.</li>
 *   <li>Country-specific parser dispatch.</li>
 *   <li>PO Box reattach.</li>
 *   <li>Named field mapping ({@link #tokensToFields}).</li>
 * </ol>
 */
@Component
public class AddressTokenizer implements AddressParsingService {

    private static final Set<TokenType> MANDATORY_TYPES = Set.of(
        TokenType.STREET_NAME, TokenType.CITY, TokenType.POSTAL_CODE);

    private static final Pattern PO_BOX = Pattern.compile(
        "(?i)\\b(?:" +
        "Post\\s+Office\\s+Box|" +
        "P\\.?\\s*O\\.?\\s*Box|" +
        "P\\.?O\\.?B\\.?|" +
        "Postfach|" +
        "Bo[iî]te\\s+Postale|" +
        "B\\.?P\\.?(?=\\s+\\d)|" +
        "Apartado(?:\\s+de\\s+Correos)?|" +
        "Postbus|" +
        "私書箱" +
        ")\\s*([A-Za-z0-9-]*)");

    private final CountryDetectorInterface   detector;
    private final Map<String, AddressParser> parsers;
    private final GenericAddressParser       fallback;
    private final NormalizationUtil          norm;
    private final AddressHealer              healer;

    @Autowired
    public AddressTokenizer(
            CountryDetectorInterface detector,
            List<AddressParser>  parserList,
            GenericAddressParser fallback,
            NormalizationUtil    norm,
            @Autowired(required = false) AddressHealer healer) {
        this.detector = detector;
        this.fallback = fallback;
        this.norm     = norm;
        this.healer   = healer;
        this.parsers  = parserList.stream()
                .collect(Collectors.toMap(AddressParser::countryCode, p -> p));
    }

    public ParsedAddress parseLines(List<String> rawLines) {
        NormalizationResult normalized = norm.toSwiftAsciiLines(rawLines);
        String work = normalized.value();

        if (work.isBlank()) {
            return new ParsedAddress(String.join("\n", rawLines), "UNKNOWN", List.of());
        }

        // PO Box detection
        AddressToken poBoxToken = null;
        String rawJoined = String.join("\n", rawLines);
        Matcher poMatcher = PO_BOX.matcher(work);
        if (poMatcher.find()) {
            String boxNum   = poMatcher.group(1);
            String tokenVal = boxNum.isBlank() ? poMatcher.group().trim() : boxNum;
            poBoxToken = new AddressToken(TokenType.PO_BOX, tokenVal);
            String before  = work.substring(0, poMatcher.start());
            String after   = work.substring(poMatcher.end());
            String stripped = (before + after).trim()
                    .replaceAll("^[,;\\s]+|[,;\\s]+$", "").replaceAll("\\s{2,}", " ");
            if (!stripped.isBlank()) work = stripped;
        }

        String flat = work.replace('\n', ' ').replaceAll("\\s{2,}", " ");
        String country = detector.detect(flat);

        // Optional healing (pro only — healer is null in core-only deployments)
        String healedFlat = healer != null ? healer.heal(flat, country) : flat;

        String finalCountry = healer != null ? detector.detect(healedFlat) : country;
        if ("UNKNOWN".equals(finalCountry)) finalCountry = country;

        AddressParser parser = parsers.getOrDefault(finalCountry, fallback);
        ParsedAddress parsed = withCountryCodeToken(parser.parse(work, finalCountry), finalCountry);

        if (healer != null) {
            parsed = healer.enrichCity(parsed, finalCountry);
        }

        double conf = computeParseConfidence(parsed.tokens(), parsed.countryCode());

        if (poBoxToken == null) {
            return tokensToFields(parsed.raw(), parsed.countryCode(), conf, parsed.tokens());
        }
        List<AddressToken> merged = new ArrayList<>(parsed.tokens().size() + 1);
        merged.add(poBoxToken);
        merged.addAll(parsed.tokens());
        return tokensToFields(rawJoined, parsed.countryCode(), conf, merged);
    }

    /**
     * Central mapping step: derives {@link ParsedAddress} named fields from the token
     * list. Assembles {@code streetName} from {@code STREET_NAME}/{@code STREET_TYPE}/
     * {@code DIRECTION} tokens (French layout: type precedes name; English: name precedes
     * type), and applies the same SWIFT field-length caps used for pacs.008 output.
     * Called once, after all parsing/healing/PO-Box steps are complete.
     */
    private static ParsedAddress tokensToFields(
            String raw, String countryCode, double parseConfidence, List<AddressToken> tokens) {
        String streetName   = cap(assembleStreetName(tokens), 70);
        String buildingName = cap(firstValue(tokens, TokenType.HOUSE_NO, TokenType.BUILDING_NAME), 16);
        String unit         = firstValue(tokens, TokenType.UNIT);
        String floor        = cap(firstValue(tokens, TokenType.FLOOR), 70);
        String city         = cap(firstValue(tokens, TokenType.CITY), 35);
        String district     = cap(firstValue(tokens, TokenType.DISTRICT), 35);
        String state        = firstValue(tokens, TokenType.STATE_CODE, TokenType.STATE);
        String postalCode   = cap(firstValue(tokens, TokenType.POSTAL_CODE), 16);

        return new ParsedAddress(raw, countryCode, parseConfidence,
            streetName, buildingName, unit, floor, city, district, state, postalCode,
            tokens, null);
    }

    /**
     * Assembles the street name from token order: French layout has
     * {@code STREET_TYPE} before {@code STREET_NAME} (e.g. {@code RUE SAINT-DENIS});
     * English layout has {@code STREET_NAME} before {@code STREET_TYPE}
     * (e.g. {@code MAIN ST}).
     */
    private static String assembleStreetName(List<AddressToken> tokens) {
        String name = firstValue(tokens, TokenType.STREET_NAME);
        String type = firstValue(tokens, TokenType.STREET_TYPE);
        String dir  = firstValue(tokens, TokenType.DIRECTION);
        name = name == null ? "" : name;
        type = type == null ? "" : type;
        dir  = dir  == null ? "" : dir;

        if (name.isBlank() && type.isBlank()) return null;

        String s;
        if (!type.isBlank() && !name.isBlank()) {
            int typeIdx = tokenIndex(tokens, TokenType.STREET_TYPE);
            int nameIdx = tokenIndex(tokens, TokenType.STREET_NAME);
            if (typeIdx >= 0 && nameIdx >= 0 && typeIdx < nameIdx) {
                s = type + " " + name + (dir.isBlank() ? "" : " " + dir);
            } else {
                s = (name + " " + type).trim();
            }
        } else {
            s = (name + " " + type).trim();
        }
        return s.isBlank() ? null : s;
    }

    /** Returns the value of the first token matching any of {@code types}, in priority order. */
    private static String firstValue(List<AddressToken> tokens, TokenType... types) {
        for (TokenType type : types) {
            for (AddressToken t : tokens) {
                if (t.type() == type) return t.value();
            }
        }
        return null;
    }

    private static int tokenIndex(List<AddressToken> tokens, TokenType type) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type() == type) return i;
        }
        return -1;
    }

    private static String cap(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    private static double computeParseConfidence(List<AddressToken> tokens, String countryCode) {
        long mandatory = tokens.stream()
                .filter(t -> MANDATORY_TYPES.contains(t.type())).count();
        double base = 0.50 + (mandatory / 3.0) * 0.40;

        boolean hasHouse = tokens.stream()
                .anyMatch(t -> t.type() == TokenType.HOUSE_NO || t.type() == TokenType.BUILDING_NAME);
        double bonus = hasHouse ? 0.05 : 0.0;

        long unknownCount = tokens.stream()
                .filter(t -> t.type() == TokenType.UNKNOWN).count();
        double unknownPenalty = Math.min(unknownCount * 0.05, 0.20);

        double countryPenalty = "UNKNOWN".equals(countryCode) ? 0.10 : 0.0;

        double raw = base + bonus - unknownPenalty - countryPenalty;
        return Math.round(Math.max(0.0, Math.min(1.0, raw)) * 100.0) / 100.0;
    }

    private ParsedAddress withCountryCodeToken(ParsedAddress parsed, String country) {
        if (country == null || country.isBlank() || "UNKNOWN".equals(country)) {
            return parsed;
        }
        boolean hasCountry = parsed.tokens().stream()
                .anyMatch(t -> t.type() == TokenType.COUNTRY || t.type() == TokenType.COUNTRY_CODE);
        if (hasCountry) {
            return parsed;
        }
        List<AddressToken> tokens = new ArrayList<>(parsed.tokens().size() + 1);
        tokens.addAll(parsed.tokens());
        tokens.add(new AddressToken(TokenType.COUNTRY_CODE, country));
        return new ParsedAddress(parsed.raw(), parsed.countryCode(), parsed.parseConfidence(), tokens);
    }

    public ParsedAddress parse(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return new ParsedAddress(rawAddress, "UNKNOWN", List.of());
        }
        List<String> lines = Arrays.stream(rawAddress.split("\\r?\\n"))
                .map(norm::preprocess)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        if (lines.isEmpty()) lines = List.of(norm.preprocess(rawAddress));
        return parseLines(lines);
    }

    public List<ParsedAddress> parseBatch(List<String> addresses) {
        return addresses.parallelStream()
                .map(this::parse)
                .collect(Collectors.toList());
    }

    public List<ParsedAddress> parseBatchLines(List<List<String>> lineGroups) {
        return lineGroups.parallelStream()
                .map(this::parseLines)
                .collect(Collectors.toList());
    }
}
