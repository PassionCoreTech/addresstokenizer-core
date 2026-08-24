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

/**
 * An immutable token extracted from a parsed address string.
 *
 * @param type     the semantic category of this address fragment
 * @param value    the normalized text of the token
 * @param source   optional provenance tag for how this token's value was obtained when
 *                 it did not come directly from the raw input (e.g. {@code "postal derived"}
 *                 for a {@link TokenType#CITY} value looked up from the postal code because
 *                 no city was present in the raw address). {@code null} for the common case
 *                 of a value parsed directly from the raw input.
 * @param original the as-parsed value this token's {@code value} replaced when enrichment
 *                 corrected it (e.g. a typo'd city fixed via gazetteer lookup). {@code null}
 *                 when {@code value} is unchanged from what was parsed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressToken(
    TokenType type,
    String value,
    String source,
    String original
) {
    /** Convenience constructor for a token parsed directly from the raw input (no provenance tag). */
    public AddressToken(TokenType type, String value) {
        this(type, value, null, null);
    }

    /** Convenience constructor for a token with provenance but no correction. */
    public AddressToken(TokenType type, String value, String source) {
        this(type, value, source, null);
    }
}

