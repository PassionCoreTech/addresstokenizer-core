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

package io.passioncore.addresstokenizer.healer;

import io.passioncore.addresstokenizer.model.ParsedAddress;

/**
 * Strategy for pre-parse address healing (e.g., merging split city fragments).
 *
 * <p>Core defines this interface. Pro's {@code AddressSplitHealer} implements it
 * using the gazetteer H2 tables. When no healer is on the classpath the
 * {@link AddressTokenizer} passes the input through unchanged.</p>
 */
public interface AddressHealer {

    /**
     * Attempt to repair a flat (comma/space separated) address string
     * before it is sent to the country-specific parser.
     *
     * @param flat        the address as a single line (newlines replaced with spaces)
     * @param countryHint ISO 3166-1 alpha-2 code or "UNKNOWN"
     * @return the healed address, or {@code flat} unchanged if no repair was needed
     */
    String heal(String flat, String countryHint);

    /**
     * Post-parse enrichment: use the postal code token to confirm or correct
     * the city name against {@code gn_postal_codes}, and stamp a
     * {@code COUNTRY_CODE} token onto the result.
     *
     * <p>The default no-op implementation is used by core-only deployments
     * where no gazetteer database is present.</p>
     *
     * @param parsed      the token list produced by the country-specific parser
     * @param countryCode ISO 3166-1 alpha-2 code determined by the detector
     * @return a new {@link ParsedAddress} (possibly with corrected city / added country token),
     *         or {@code parsed} unchanged if no enrichment was possible
     */
    default ParsedAddress enrichCity(ParsedAddress parsed, String countryCode) {
        return parsed;
    }
}

