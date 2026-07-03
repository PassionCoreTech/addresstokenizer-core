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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Optional Pro-tier enrichment diagnostics attached to {@link ParsedAddress#diagnostics()}.
 *
 * <p>Core defines this type but never populates it — {@link ParsedAddress#diagnostics()}
 * is {@code null} for Core-only parsing. Pro's {@code AddressEnrichmentService} populates
 * it with gazetteer-validated confidence, ISO 20022 structure, field corrections, and
 * trace logs. Defining the type in Core keeps the shared {@link AddressParsingService}
 * interface free of a Pro dependency while still allowing Pro to attach its richer output
 * to the same {@link ParsedAddress} the Core-only caller already knows how to read.</p>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParseDiagnostics(
    /** Weighted aggregate confidence (0.0–1.0) — gazetteer validation plus penalties. */
    double confidence,

    /** {@code true} when {@link #confidence()} is below the review threshold. */
    boolean needsReview,

    /** ISO 3166-1 alpha-2 validation status of the resolved country code. */
    CountryCodeStatus countryCodeStatus,

    /** ISO 3166-2 normalised state/province, e.g. {@code "US-NY"} (opt-in, postal-code enrichment). */
    String subDivision,

    /** Community / suburb resolved from the postal-code dataset (opt-in). */
    String townLocation,

    /** Which pacs.008 XML structure this address produces. */
    AddressIso20022Result.AddressStructureType inputStructure,

    /** {@code true} when the address satisfies the CBPR+ November 2026 structured mandate. */
    boolean cbprStructured,

    /** {@code true} when a PO Box is present without a civic address (CA FINTRAC rule). */
    boolean fintracPoBoxInvalid,

    /** Per-field confidence scores (0.0–1.0), keyed by output field name; absent fields omitted. */
    Map<String, Double> fieldConfidences,

    /** Structured field corrections, present only for fields that were modified during enrichment. */
    Map<String, FieldCorrection> corrections,

    /** One entry per parser decision, explaining why each field was assigned its value. */
    List<TraceLog> traceLogs,

    /** Full ISO 20022 / pacs.008 structured output. */
    AddressIso20022Result iso20022Result
) {}
