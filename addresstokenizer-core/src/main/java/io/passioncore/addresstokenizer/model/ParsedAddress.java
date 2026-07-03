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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable output of address parsing — the return type of
 * {@link io.passioncore.addresstokenizer.AddressParsingService}.
 *
 * <p>Named fields ({@link #streetName()}, {@link #city()}, etc.) are populated by
 * {@link io.passioncore.addresstokenizer.AddressTokenizer} from the token list in one
 * central mapping step; the token list ({@link #tokens()}) is retained for diagnostics
 * and backward compatibility with {@link #get(TokenType)}. {@code parseConfidence}
 * (0.0–1.0) is computed from token coverage alone — no gazetteer lookup required.</p>
 *
 * <p>{@link #diagnostics()} is {@code null} for Core-only parsing. Pro's
 * {@code AddressEnrichmentService} returns a {@link ParsedAddress} with the same named
 * fields (potentially corrected via gazetteer/postal-code enrichment) plus a populated
 * {@link ParseDiagnostics} for weighted confidence, ISO 20022 structure, and trace logs.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParsedAddress(
    String raw,
    String countryCode,
    /** Basic parse confidence (0.0–1.0) computed from token coverage.
     *  Scores presence of STREET_NAME, CITY, POSTAL_CODE, and HOUSE_NO/BUILDING_NAME;
     *  applies penalties for UNKNOWN tokens and undetected country.
     *  Pro provides enhanced confidence with gazetteer validation — see
     *  {@link ParseDiagnostics#confidence()}. */
    double parseConfidence,

    // Named fields — populated by AddressTokenizer.tokensToFields() after parsing.
    // Null until that mapping step runs (e.g. on a parser's raw, intermediate return value).
    String streetName,
    String buildingName,
    String unit,
    String floor,
    String city,
    String district,
    String state,
    String postalCode,

    /** Full token list — retained for diagnostics and {@link #get(TokenType)} lookups. */
    List<AddressToken> tokens,

    /** Pro-tier enrichment diagnostics. {@code null} for Core-only parsing. */
    ParseDiagnostics diagnostics
) {
    /** Convenience constructor for parsers — {@code parseConfidence} defaults to 0.0 and is
     *  injected by {@code AddressTokenizer} after all token enrichment is complete. */
    public ParsedAddress(String raw, String countryCode, List<AddressToken> tokens) {
        this(raw, countryCode, 0.0, tokens);
    }

    /** Convenience constructor for parsers — named fields are populated later by
     *  {@code AddressTokenizer.tokensToFields()}; {@code diagnostics} is Pro-only. */
    public ParsedAddress(String raw, String countryCode, double parseConfidence, List<AddressToken> tokens) {
        this(raw, countryCode, parseConfidence,
            null, null, null, null, null, null, null, null,
            tokens, null);
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

    /** Resolved country — the {@code COUNTRY_CODE}/{@code COUNTRY} token if present,
     *  otherwise falls back to the detected {@link #countryCode()}.
     *  Annotated explicitly since it is a derived method, not a canonical record
     *  component — without {@code @JsonProperty}, Jackson would omit it from JSON. */
    @JsonProperty("country")
    public String country() {
        return get(TokenType.COUNTRY_CODE).or(() -> get(TokenType.COUNTRY)).orElse(countryCode());
    }
}
