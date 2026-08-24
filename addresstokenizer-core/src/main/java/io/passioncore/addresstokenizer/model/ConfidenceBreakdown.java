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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * The three confidence numbers for a parsed address, side by side, so a caller doesn't
 * have to cross-reference {@link ParsedAddress#general()} and {@link ParseDiagnostics}
 * separately to see the whole story.
 *
 * <ul>
 *   <li>{@code parse} — Core token-coverage confidence, no gazetteer lookup involved.
 *       Duplicates {@code general.parseConfidence}'s value intentionally.</li>
 *   <li>{@code gazetteer} — the pre-penalty gazetteer match tier
 *       ({@code EnrichmentResult.confidence()} from {@code GazeteerEnricher}'s P1–P8
 *       priority table), before any whole-address penalty is applied.</li>
 *   <li>{@code final_} (serialized as {@code "final"}) — {@code gazetteer} minus any
 *       whole-address penalties (typo correction, subdivision mismatch, country
 *       conflict, etc.). This is the value that drives {@link ParseDiagnostics#needsReview()}.</li>
 * </ul>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConfidenceBreakdown(
    double parse,
    double gazetteer,

    @JsonProperty("final")
    double final_
) {}
