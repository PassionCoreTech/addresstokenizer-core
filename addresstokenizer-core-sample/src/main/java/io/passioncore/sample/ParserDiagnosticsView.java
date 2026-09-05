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
package io.passioncore.sample;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.passioncore.addresstokenizer.model.AddressIso20022Result.AddressStructureType;
import io.passioncore.addresstokenizer.model.ConfidenceBreakdown;
import io.passioncore.addresstokenizer.model.CountryCodeStatus;
import io.passioncore.addresstokenizer.model.FieldConfidenceEntry;
import io.passioncore.addresstokenizer.model.FieldCorrection;
import io.passioncore.addresstokenizer.model.ParseDiagnostics;
import io.passioncore.addresstokenizer.model.TraceLog;

/**
 * Projection of {@link ParseDiagnostics} used by the shared {@code /parse} endpoint
 * contract. Never {@code null} in a {@code /parse} response, on
 * either tier — Core populates only {@link #confidence()}/{@link #needsReview()} via
 * {@link #fromCore}; every other field stays absent ({@code NON_NULL}) since it
 * requires Pro-only enrichment Core doesn't have.
 *
 * <p>This exact record is duplicated in {@code addresstokenizer-pro-sample} (see its
 * own {@code ParserDiagnosticsView}) rather than shared from a common module — Core's
 * and Pro's sample apps are independent Maven modules that don't depend on each other,
 * and this plan is scoped to not touch {@code addresstokenizer-core}/{@code -pro}
 * themselves, so there's no clean shared home for it without a new module. The type is
 * small enough that keeping both copies in sync by hand is an acceptable tradeoff.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParserDiagnosticsView(
    ConfidenceBreakdown confidence,
    boolean needsReview,
    CountryCodeStatus countryCodeStatus,
    AddressStructureType inputStructure,
    Boolean cbprStructured,
    Boolean fintracPoBoxInvalid,
    Map<String, FieldConfidenceEntry> fieldConfidences,
    Map<String, FieldCorrection> corrections,
    List<TraceLog> traceLogs
) {
    /**
     * Core-side Null Object fallback for when {@link io.passioncore.addresstokenizer.model.ParsedAddress#diagnostics()}
     * is {@code null} (Core-only parsing has no gazetteer/ISO 20022 tier to report).
     * {@code confidence}'s {@code parse}/{@code gazetteer}/{@code final} all collapse to
     * {@code parseConfidence} — there's nothing more specific to say without a gazetteer.
     */
    public static ParserDiagnosticsView fromCore(double parseConfidence, double threshold) {
        ConfidenceBreakdown confidence =
                new ConfidenceBreakdown(parseConfidence, parseConfidence, parseConfidence);
        return new ParserDiagnosticsView(
                confidence,
                parseConfidence < threshold,
                null, null, null, null, null, null, null);
    }
}
