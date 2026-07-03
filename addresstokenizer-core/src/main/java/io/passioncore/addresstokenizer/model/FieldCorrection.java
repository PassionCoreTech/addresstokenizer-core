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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Structured record of a field correction applied during enrichment (Pro only).
 *
 * <p>Reason codes:</p>
 * <ul>
 *   <li>{@code TYPO} — parsed city not found in gazetteer; corrected via postal lookup</li>
 *   <li>{@code SUB_DISTRICT_MISMATCH} — postal code resolves to a sub-district of the parsed city</li>
 *   <li>{@code CROSS_CITY_MISMATCH} — postal code resolves to a different municipality</li>
 *   <li>{@code COUNTRY_INFERRED} — country missing or unrecognised; resolved from postal code</li>
 *   <li>{@code COUNTRY_MISMATCH} — postal code belongs to a different country than resolved</li>
 * </ul>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldCorrection(
    String original,   // value extracted verbatim from input
    String corrected,  // value used in output after postal/gazetteer resolution
    String reason      // short code: TYPO, SUB_DISTRICT_MISMATCH, CROSS_CITY_MISMATCH, COUNTRY_INFERRED, COUNTRY_MISMATCH
) {}
