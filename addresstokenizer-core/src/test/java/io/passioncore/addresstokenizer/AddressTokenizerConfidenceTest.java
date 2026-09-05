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
import io.passioncore.addresstokenizer.parser.CaAddressParser;
import io.passioncore.addresstokenizer.parser.GenericAddressParser;
import io.passioncore.addresstokenizer.parser.QuebecFrenchDetector;
import io.passioncore.addresstokenizer.parser.QuebecFrenchParser;
import io.passioncore.addresstokenizer.parser.UsAddressParser;
import io.passioncore.addresstokenizer.utils.NormalizationUtil;

/**
 * Covers the declared-vs-resolved country conflict penalty added to
 * {@code AddressTokenizer.computeParseConfidence()}: when the address's raw
 * text ends in a bare 2-letter code (its own comma segment) that disagrees with the
 * country the pipeline actually resolves, {@code parseConfidence} is penalized -0.10.
 */
class AddressTokenizerConfidenceTest {

    private AddressTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        List<AddressParser> parsers = List.of(
            new UsAddressParser(),
            new CaAddressParser(new QuebecFrenchDetector(), new QuebecFrenchParser())
        );
        NormalizationUtil norm = new NormalizationUtil();
        tokenizer = new AddressTokenizer(
            new CountryDetector(parsers),
            parsers,
            new GenericAddressParser(),
            norm,
            null   // no AddressHealer -- Core-only mode
        );
    }

    @Test
    void declaredCountryConflict_lowersParseConfidenceByExactly0_10() {
        // ZIP 10118 / city NEW YORK / state NY all resolve to US -- the literal trailing
        // "CA" is a conflicting declared-country signal.
        ParsedAddress conflict = tokenizer.parse("350 Fifth Avenue, New York, NY 10118, CA");
        ParsedAddress agree = tokenizer.parse("350 Fifth Avenue, New York, NY 10118, US");

        assertThat(conflict.countryCode()).isEqualTo("US");
        assertThat(agree.countryCode()).isEqualTo("US");

        assertThat(agree.parseConfidence() - conflict.parseConfidence())
            .as("declared 'CA' vs resolved 'US' should cost exactly the -0.10 conflict penalty")
            .isEqualTo(0.10, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void declaredCountryAgreement_appliesNoPenalty() {
        // Genuine Canadian address ending in a standalone ", CA" segment where the
        // resolved country actually IS CA -- must not be penalized.
        ParsedAddress agree = tokenizer.parse("100 Queen Street West, Toronto, ON M5H 2N2, CA");
        ParsedAddress conflict = tokenizer.parse("100 Queen Street West, Toronto, ON M5H 2N2, GB");

        assertThat(agree.countryCode()).isEqualTo("CA");
        assertThat(conflict.countryCode()).isEqualTo("CA");

        assertThat(agree.parseConfidence())
            .as("declared 'CA' agrees with resolved 'CA' -- no penalty")
            .isGreaterThan(conflict.parseConfidence());
        assertThat(agree.parseConfidence() - conflict.parseConfidence())
            .isEqualTo(0.10, org.assertj.core.data.Offset.offset(0.001));
    }
}
