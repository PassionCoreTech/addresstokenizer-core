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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** Raw pre-enrichment detected country code. Not serialized directly — see
     *  {@link #general()}, which uses the resolved {@link #country()} value instead;
     *  this raw value can diverge from it (e.g. cross-border correction). */
    @JsonIgnore
    String countryCode,

    /** Basic parse confidence (0.0–1.0) computed from token coverage.
     *  Scores presence of STREET_NAME, CITY, POSTAL_CODE, and HOUSE_NO/BUILDING_NAME;
     *  applies penalties for UNKNOWN tokens and undetected country.
     *  Pro provides enhanced confidence with gazetteer validation — see
     *  {@link ParseDiagnostics#confidence()}. Not serialized directly — see
     *  {@link #general()}. */
    @JsonIgnore
    double parseConfidence,

    // Named fields — populated by AddressTokenizer.tokensToFields() after parsing.
    // Null until that mapping step runs (e.g. on a parser's raw, intermediate return value).
    // Not serialized directly — see {@link #general()}.
    @JsonIgnore String streetName,
    @JsonIgnore String buildingName,
    @JsonIgnore String unit,
    @JsonIgnore String floor,
    @JsonIgnore String city,
    @JsonIgnore String district,
    @JsonIgnore String state,
    @JsonIgnore String postalCode,

    /** Full token list — retained for internal use ({@link #get(TokenType)} lookups) and
     *  for the shared {@code /parse} endpoint contract, which serializes it explicitly in
     *  its own response type. Not serialized directly here — the enriched {@code /enrich}
     *  view is {@code {raw, diagnostics, general, iso20022Result}}. */
    @JsonIgnore
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
     *  otherwise falls back to the detected {@link #countryCode()}. Not serialized
     *  directly — see {@link #general()}, which exposes this value as
     *  {@code general.countryCode}. */
    @JsonIgnore
    public String country() {
        return get(TokenType.COUNTRY_CODE).or(() -> get(TokenType.COUNTRY)).orElse(countryCode());
    }

    /** Full country display name, derived from {@link #country()} — no gazetteer/DB
     *  dependency, so this works identically in Core and Pro. Not serialized directly —
     *  see {@link #general()}. */
    @JsonIgnore
    public String countryName() {
        String code = country();
        if (code == null || code.isBlank()) return null;
        try {
            return new Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH);
        } catch (java.util.IllformedLocaleException e) {
            return null;
        }
    }

    /**
     * Plain-name view of this address — the {@code "general"} section of the JSON
     * output. Groups the flat fields (hidden from top-level JSON via {@code @JsonIgnore})
     * together with the resolved country and, when {@link #diagnostics} is present,
     * Pro's per-field confidence map.
     */
    @JsonProperty("general")
    public GeneralView general() {
        Map<String, FieldConfidenceEntry> fieldConfidences =
                diagnostics != null ? diagnostics.fieldConfidences() : null;
        String townLocation = diagnostics != null && diagnostics.iso20022Result() != null
                ? diagnostics.iso20022Result().twnLctnNm() : null;

        return GeneralView.builder()
                .countryCode(country())
                .countryName(countryName())
                .streetName(streetName)
                .buildingName(buildingName)
                .unit(unit)
                .floor(floor)
                .city(city)
                .district(district)
                .townLocation(townLocation)
                .state(state)
                .postalCode(postalCode)
                .parseConfidence(parseConfidence)
                .fieldConfidences(fieldConfidences)
                .build();
    }

    /**
     * ISO 20022 / pacs.008 structured result — promoted here from
     * {@link ParseDiagnostics#iso20022Result()} so it's a top-level JSON section rather
     * than buried inside Pro-only diagnostics. {@code null} for Core-only parsing (same
     * as {@link #diagnostics}).
     */
    @JsonProperty("iso20022Result")
    public AddressIso20022Result iso20022Result() {
        return diagnostics != null ? diagnostics.iso20022Result() : null;
    }
}
