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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Plain-name view of a parsed address — the {@code "general"} section of
 * {@link ParsedAddress}'s JSON output.
 *
 * <p>Built on demand by {@link ParsedAddress#general()} from the record's own flat
 * fields (Core, always available) plus {@link ParseDiagnostics#fieldConfidences()}
 * (Pro-only, absent when {@link ParsedAddress#diagnostics()} is {@code null}). Mirrors
 * {@link AddressIso20022Result} field-for-field under plain Java names instead of ISO
 * 20022 tag names — {@code countryCode} here is {@code ctry} there, {@code fieldConfidences}
 * here is {@code fieldConfidence} there, etc. — for callers who don't need SWIFT tag
 * names.</p>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeneralView(
    /** Resolved ISO 3166-1 alpha-2 country code — same source as {@code iso20022Result.ctry}. */
    String countryCode,

    /** Full country display name, derived from {@link #countryCode} via {@code java.util.Locale}. */
    String countryName,

    String streetName,
    String buildingName,
    String unit,
    String floor,
    String city,
    String district,
    String townLocation,
    String state,
    String postalCode,

    /** Core token-coverage confidence (0.0-1.0) — always present, no gazetteer lookup required. */
    double parseConfidence,

    /** Per-field confidence, keyed by Java field name. {@code null} for Core-only parsing. */
    Map<String, FieldConfidenceEntry> fieldConfidences
) {}
