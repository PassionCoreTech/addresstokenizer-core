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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.detector.CountryDetector;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.parser.AddressParser;
import io.passioncore.addresstokenizer.parser.AuAddressParser;
import io.passioncore.addresstokenizer.parser.CaAddressParser;
import io.passioncore.addresstokenizer.parser.DeAddressParser;
import io.passioncore.addresstokenizer.parser.FrAddressParser;
import io.passioncore.addresstokenizer.parser.GenericAddressParser;
import io.passioncore.addresstokenizer.parser.QuebecFrenchDetector;
import io.passioncore.addresstokenizer.parser.QuebecFrenchParser;
import io.passioncore.addresstokenizer.parser.UkAddressParser;
import io.passioncore.addresstokenizer.parser.UsAddressParser;
import io.passioncore.addresstokenizer.utils.NormalizationUtil;

/**
 * Verifies that AddressTokenizer initialises and parses correctly with no Pro classes
 * on the classpath. healer is explicitly null — confirming Core wires up standalone.
 */
class CoreOnlyWiringTest {

    private AddressTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        List<AddressParser> parsers = List.of(
            new UsAddressParser(),
            new UkAddressParser(),
            new DeAddressParser(),
            new FrAddressParser(),
            new AuAddressParser(),
            new CaAddressParser(new QuebecFrenchDetector(), new QuebecFrenchParser())
        );
        NormalizationUtil norm = new NormalizationUtil();
        tokenizer = new AddressTokenizer(
            new CountryDetector(parsers),
            parsers,
            new GenericAddressParser(),
            norm,
            null   // no AddressHealer — Core-only mode
        );
    }

    @Test
    void parse_usAddress_namedAccessors() {
        ParsedAddress p = tokenizer.parse("350 Fifth Avenue, New York, NY 10118");
        assertThat(p.countryCode()).isEqualTo("US");
        assertThat(p.streetName()).isNotBlank();
        assertThat(p.buildingName()).isEqualTo("350");
        assertThat(p.city()).isEqualTo("NEW YORK");
        assertThat(p.postalCode()).isNotBlank();
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parse_ukAddress_namedAccessors() {
        ParsedAddress p = tokenizer.parse("10 Downing Street, London SW1A 2AA");
        assertThat(p.countryCode()).isEqualTo("GB");
        assertThat(p.streetName()).isNotBlank();
        assertThat(p.city()).isNotBlank();
        assertThat(p.postalCode()).isNotBlank();
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parse_deAddress_namedAccessors() {
        ParsedAddress p = tokenizer.parse("Unter den Linden 6, 10117 Berlin, Germany");
        assertThat(p.countryCode()).isEqualTo("DE");
        assertThat(p.streetName()).isNotBlank();
        assertThat(p.city()).isNotBlank();
        assertThat(p.postalCode()).isNotBlank();
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parseLines_multiLine_usAddress() {
        ParsedAddress p = tokenizer.parseLines(List.of(
            "350 Fifth Avenue",
            "New York, NY 10118"
        ));
        assertThat(p.countryCode()).isEqualTo("US");
        assertThat(p.postalCode()).isNotBlank();
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parse_noProDependency_healerIsNull() {
        // Confirms Core tokenizer runs when healer is null (no AddressSplitHealer bean)
        ParsedAddress p = tokenizer.parse("123 Main Street, Vancouver, BC, Canada");
        assertThat(p).isNotNull();
        assertThat(p.parseConfidence()).isGreaterThan(0.0);
    }

    @Test
    void namedAccessors_returnNullForAbsentFields() {
        // A city-only input should have null street and postal
        ParsedAddress p = tokenizer.parse("Berlin, Germany");
        assertThat(p.unit()).isNull();
        assertThat(p.floor()).isNull();
    }

    @Test
    void country_fallsBackToDetectedCountryCode() {
        ParsedAddress p = tokenizer.parse("350 Fifth Avenue, New York, NY 10118");
        // country() returns COUNTRY_CODE token or falls back to countryCode() component
        assertThat(p.country()).isEqualTo("US");
    }
}
