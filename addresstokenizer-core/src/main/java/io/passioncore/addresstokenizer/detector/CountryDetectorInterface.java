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

package io.passioncore.addresstokenizer.detector;

import java.util.Optional;

/**
 * Contract for country detection from a free-text address string.
 *
 * <p>Core provides {@link CountryDetector} (regex-only).
 * Pro provides {@link CountryDetectorPro} which extends it with a gazetteer
 * database fallback and is registered as {@code @Primary}.</p>
 */
public interface CountryDetectorInterface {

    /**
     * Detects the ISO 3166-1 alpha-2 country code from a free-text address.
     *
     * @param address raw address string (SWIFT-normalized or original)
     * @return ISO 3166-1 alpha-2 code (e.g. {@code "GB"}, {@code "US"})
     *         or {@code "UNKNOWN"} if detection fails
     */
    String detect(String address);

    /**
     * Secondary, positional signal: checks whether a recognized country name appears
     * within the trailing {@code tailTokenWindow} tokens of the address. Used only to
     * corroborate/conflict-check the primary {@link #detect} result — never a
     * replacement for it.
     *
     * @param address raw address string
     * @param tailTokenWindow number of trailing whitespace/comma-delimited tokens to scan
     * @return ISO 3166-1 alpha-2 code of the recognized country name found in the tail,
     *         or empty if none matched
     */
    Optional<String> detectInTail(String address, int tailTokenWindow);

    /**
     * Returns the bare 2-letter code (uppercased) when the address's last comma-delimited
     * segment consists solely of that code — a strong positional signal of an explicit
     * country declaration (e.g. "..., CA"), distinct from {@link #detectInTail} which scans
     * a fuzzy trailing window for full country names only. Returns empty when the last
     * segment has any other content (e.g. "NY 10118" — state+ZIP share one segment in
     * standard mailing-address format and never qualify), is missing, or is blank.
     *
     * @param address raw address string (original, not tokenizer-normalized)
     * @return bare uppercased 2-letter code, or empty if the last segment isn't a lone code
     */
    Optional<String> detectDeclaredCountryCode(String address);
}
