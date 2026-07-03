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

/**
 * ISO 3166-1 alpha-2 validation status of the {@code <Ctry>} field (Pro only).
 *
 * <p>Set by {@code AddressEnrichmentService} after checking the detected country
 * code against the {@code oa_countries} gazetteer table. Exposed on
 * {@link ParseDiagnostics#countryCodeStatus()}.</p>
 */
public enum CountryCodeStatus {

    /** Code is present in the ISO 3166-1 alpha-2 registry and has a specialized parser. */
    VALID_ISO3166,

    /**
     * Country was not supplied or could not be detected from the address text, but was
     * inferred from a postal-code or city-name gazetteer lookup.
     * Callers should treat the resolved country as best-effort and set {@code needsReview}.
     */
    KNOWN_ALIAS_NORMALIZED,

    /** No country could be determined from the address text or any fallback source. */
    UNKNOWN,

    /**
     * The supplied or detected code does not conform to the ISO 3166-1 alpha-2 format
     * (exactly two uppercase ASCII letters), or it matches the format but is not present
     * in the ISO 3166-1 registry (e.g. {@code "XX"}, {@code "ZZ"}).
     */
    INVALID_FORMAT,

    /**
     * The code is a valid ISO 3166-1 alpha-2 country code but there is no specialized
     * address parser for it; results use the generic fallback parser and may have lower
     * structural accuracy.
     */
    UNSUPPORTED
}
