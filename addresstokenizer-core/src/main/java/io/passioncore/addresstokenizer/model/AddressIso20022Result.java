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
 * ISO 20022 / pacs.008 CBPR+ structured-address output (Pro only).
 *
 * <p>Contains the discrete pacs.008 {@code <PstlAdr>} element fields, any
 * compliance violation messages, and per-field confidence scores keyed by
 * ISO 20022 XML tag name (e.g. {@code "StrtNm"}, {@code "TwnNm"}).</p>
 *
 * <p>Top-level compliance flags ({@link ParseDiagnostics#inputStructure()},
 * {@link ParseDiagnostics#cbprStructured()}, {@link ParseDiagnostics#fintracPoBoxInvalid()})
 * live on {@link ParseDiagnostics} for direct access without navigating into this
 * nested object.</p>
 *
 * <h3>SWIFT field-length constraints (enforced at assembly time)</h3>
 * <pre>
 *  StrtNm       max 70 chars
 *  BldgNb       max 16 chars
 *  BldgNm       max 35 chars
 *  PstCd        max 16 chars
 *  TwnNm        max 35 chars  – MANDATORY from November 2026
 *  Ctry         exactly 2 uppercase alpha – MANDATORY
 *  CtrySubDvsn  max 35 chars
 *  Dstrct       max 35 chars
 *  TwnLctnNm    max 35 chars
 *  AdrLine      max 70 chars each; max 2 lines per pacs.008 cardinality
 * </pre>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressIso20022Result(

    // ── pacs.008 structured address fields ────────────────────────────────────────────────────────

    /** {@code <StrtNm>} – SWIFT-safe uppercase, max 70 chars. */
    String strtNm,

    /** {@code <BldgNb>} – house/unit number, max 16 chars. */
    String bldgNb,

    /** {@code <BldgNm>} – unit/suite designator or named building, max 35 chars. */
    String bldgNm,

    /** {@code <PstCd>} – postal / zip code, max 16 chars. */
    String pstCd,

    /** {@code <TwnNm>} – MANDATORY from November 2026; gazetteer-validated, max 35 chars. */
    String twnNm,

    /** {@code <Ctry>} – MANDATORY; ISO 3166-1 alpha-2 e.g. "GB". */
    String ctry,

    /** {@code <CtrySubDvsn>} – state / province / region e.g. "CA-ON", max 35 chars. */
    String ctrySubDvsn,

    /** {@code <Dstrct>} – district / county, max 35 chars. */
    String dstrct,

    /** {@code <TwnLctnNm>} – community / suburb, max 35 chars. */
    String twnLctnNm,

    /**
     * {@code <Flr>} – floor identifier (e.g. "3", "G/F", "12/F"), max 70 chars.
     * Populated when a floor designation is parsed from the address.
     */
    String flr,

    /**
     * {@code <Room>} – room identifier, max 70 chars.
     * Reserved for future extraction; currently always null.
     */
    String room,

    /**
     * {@code <AdrLine>} – residual unstructured overflow lines.
     * Present only in HYBRID mode; max 2 elements, each max 70 chars.
     */
    List<String> adrLine,

    // ── Compliance violations ─────────────────────────────────────────────────────────────────────

    /**
     * Human-readable descriptions of each CBPR+ compliance violation.
     * Null when {@link ParseDiagnostics#cbprStructured()} is {@code true}.
     */
    List<String> violations,

    // ── Per-field confidence ──────────────────────────────────────────────────────────────────────

    /**
     * Per-field confidence using ISO 20022 XML tag names as keys and numeric
     * 0.0–1.0 scores as values (e.g. {@code {"StrtNm": 0.88, "TwnNm": 0.97}}).
     * Only fields that were parsed or enriched are present.
     */
    Map<String, Double> fieldConfidence

) {

    // ── Nested enum ───────────────────────────────────────────────────────────────────────────────

    /**
     * Classifies which pacs.008 XML structure this address produces.
     * Surfaced at {@link ParseDiagnostics#inputStructure()}.
     *
     * <ul>
     *   <li>{@code STRUCTURED}   – all mandatory fields as discrete elements; fully compliant</li>
     *   <li>{@code HYBRID}       – TwnNm+Ctry present as structured; overflow in AdrLine</li>
     *   <li>{@code UNSTRUCTURED} – only AdrLine elements; NON-COMPLIANT after Nov 2026</li>
     * </ul>
     */
    public enum AddressStructureType {
        STRUCTURED,
        HYBRID,
        UNSTRUCTURED
    }
}
