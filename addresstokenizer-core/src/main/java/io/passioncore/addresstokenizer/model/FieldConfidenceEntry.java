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
 * One field's confidence score, plus the whole-address penalty (if any) that was
 * triggered by a correction to this specific field.
 *
 * <p>Replaces the plain {@code double} previously used in {@code fieldConfidences}/
 * {@code fieldConfidence} maps — merges what would otherwise be two parallel maps
 * (confidence + penalty) into one entry per field. {@code penalty} is omitted when no
 * whole-address penalty was triggered by this field (the common case).</p>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldConfidenceEntry(
    double confidence,
    Double penalty
) {
    public static FieldConfidenceEntry of(double confidence) {
        return new FieldConfidenceEntry(confidence, null);
    }

    public static FieldConfidenceEntry of(double confidence, double penalty) {
        return new FieldConfidenceEntry(confidence, penalty);
    }
}
