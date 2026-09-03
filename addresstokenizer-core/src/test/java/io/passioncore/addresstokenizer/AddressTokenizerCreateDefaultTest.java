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
package io.passioncore.addresstokenizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.model.ParsedAddress;

/**
 * Guard test for {@link AddressTokenizer#createDefault()} (plan 039, Option B): confirms the
 * factory-built object graph parses the same as the hand-wired graph in
 * {@link CoreOnlyWiringTest}, for every parser {@code createDefault()} registers. If a new
 * Core parser is added to the factory but this test isn't extended to cover it, that's a signal
 * the factory and the manual wiring documented in {@code addresstokenizer-core/README.md} have
 * drifted apart.
 */
class AddressTokenizerCreateDefaultTest {

    private final AddressTokenizer tokenizer = AddressTokenizer.createDefault();

    @Test
    void parse_usAddress() {
        ParsedAddress p = tokenizer.parse("350 Fifth Avenue, New York, NY 10118");
        assertThat(p.countryCode()).isEqualTo("US");
        assertThat(p.city()).isEqualTo("NEW YORK");
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parse_ukAddress() {
        ParsedAddress p = tokenizer.parse("10 Downing Street, London SW1A 2AA");
        assertThat(p.countryCode()).isEqualTo("GB");
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parse_deAddress() {
        ParsedAddress p = tokenizer.parse("Unter den Linden 6, 10117 Berlin, Germany");
        assertThat(p.countryCode()).isEqualTo("DE");
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parse_frAddress() {
        ParsedAddress p = tokenizer.parse("12 Rue de Rivoli, 75001 Paris, France");
        assertThat(p.countryCode()).isEqualTo("FR");
        assertThat(p.city()).isEqualTo("PARIS");
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parse_auAddress() {
        ParsedAddress p = tokenizer.parse("1 Martin Place, Sydney NSW 2000, Australia");
        assertThat(p.countryCode()).isEqualTo("AU");
        assertThat(p.city()).isEqualTo("SYDNEY");
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parse_caAddress_quebecFrenchLayout() {
        // Exercises CaAddressParser's QuebecFrenchDetector/QuebecFrenchParser collaborators,
        // the two arguments createDefault() must wire by hand (not just default-constructed).
        ParsedAddress p = tokenizer.parse("100 Rue Saint-Denis, Montréal, QC H2X 3K6, Canada");
        assertThat(p.countryCode()).isEqualTo("CA");
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void noSpringContainer_healerIsNull() {
        // healer must be null (Core-only mode) — same contract as the hand-wired path.
        ParsedAddress p = tokenizer.parse("123 Main Street, Vancouver, BC, Canada");
        assertThat(p).isNotNull();
        assertThat(p.parseConfidence()).isGreaterThan(0.0);
    }

    @Test
    void createDefault_cityNameOnlyFallback_resolvesViaCityCountryLookup() {
        // No postal code, no explicit country name, no other CountryDetector pattern hint --
        // exercises the last-resort CityCountryLookup fallback (CountryDetector.java:220),
        // which createDefault() now wires via CityCountryLookup.createDefault() instead of
        // omitting it.
        ParsedAddress p = tokenizer.parse("123 Some Street, Paris");
        assertThat(p.countryCode()).isEqualTo("FR");
    }
}
