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

package io.passioncore.addresstokenizer.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable output of the core tokenisation pipeline.
 *
 * <p>Contains the detected country code, a flat list of typed tokens, and
 * a {@code parseConfidence} score (0.0–1.0) computed from the token set alone —
 * no gazetteer lookup required. Full confidence with gazetteer validation is a
 * Pro-tier feature ({@code AddressResult.confidence()}).</p>
 */
public record ParsedAddress(
    String raw,
    String countryCode,
    /** Basic parse confidence (0.0–1.0) computed from token coverage.
     *  Scores presence of STREET_NAME, CITY, POSTAL_CODE, and HOUSE_NO/BUILDING_NAME;
     *  applies penalties for UNKNOWN tokens and undetected country.
     *  Pro provides enhanced confidence with gazetteer validation and field-level breakdown. */
    double parseConfidence,
    List<AddressToken> tokens
) {
    /** Convenience constructor for parsers — {@code parseConfidence} defaults to 0.0 and is
     *  injected by {@code AddressTokenizer} after all token enrichment is complete. */
    public ParsedAddress(String raw, String countryCode, List<AddressToken> tokens) {
        this(raw, countryCode, 0.0, tokens);
    }

    public Optional<String> get(TokenType type) {
        return tokens.stream()
            .filter(t -> t.type() == type)
            .map(AddressToken::value)
            .findFirst();
    }

    public Map<String, String> toMap() {
        return tokens.stream()
            .collect(Collectors.toMap(
                t -> t.type().name(),
                AddressToken::value,
                (a, b) -> a
            ));
    }

    // Named convenience accessors — thin projections over the token list.
    // These are the stable public API; callers should prefer these over get(TokenType).

    public String streetName()   { return get(TokenType.STREET_NAME).orElse(null); }
    public String buildingName() {
        return get(TokenType.HOUSE_NO).or(() -> get(TokenType.BUILDING_NAME)).orElse(null);
    }
    public String unit()         { return get(TokenType.UNIT).orElse(null); }
    public String floor()        { return get(TokenType.FLOOR).orElse(null); }
    public String city()         { return get(TokenType.CITY).orElse(null); }
    public String district()     { return get(TokenType.DISTRICT).orElse(null); }
    public String state() {
        return get(TokenType.STATE_CODE).or(() -> get(TokenType.STATE)).orElse(null);
    }
    public String postalCode()   { return get(TokenType.POSTAL_CODE).orElse(null); }
    public String country() {
        return get(TokenType.COUNTRY_CODE).or(() -> get(TokenType.COUNTRY)).orElse(countryCode());
    }
}
