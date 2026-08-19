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
package io.passioncore.addresstokenizer.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;

/**
 * Regression pins for docs/plans/029: {@code UsAddressParser}, {@code UkAddressParser},
 * and {@code AuAddressParser} each naively took the last comma-segment as {@code CITY}
 * when no postal/state token had already been matched and stripped, so a trailing,
 * redundant self-reference to the parser's own country (no state/ZIP/postcode present to
 * absorb it first) got misread as the city. Same bug class as the {@code CaAddressParser}
 * "CA" fix (see {@code CaAddressParserCountryCodeSuffixTest}), applied to three more
 * parsers.
 */
class CountrySelfReferenceSuffixTest {

    @Test
    void usParser_trailingCountryName_doesNotOverwriteCity() {
        ParsedAddress r = new UsAddressParser()
            .parse("1600 Pennsylvania Ave, Washington, United States", "US");

        assertThat(r.get(TokenType.CITY)).hasValue("WASHINGTON");
    }

    @Test
    void ukParser_trailingCountryCode_doesNotOverwriteCity() {
        ParsedAddress r = new UkAddressParser()
            .parse("10 Downing Street, London, UK", "GB");

        assertThat(r.get(TokenType.CITY)).hasValue("London");
    }

    @Test
    void auParser_trailingCountryName_doesNotOverwriteCity() {
        ParsedAddress r = new AuAddressParser()
            .parse("123 Collins Street, Melbourne, Australia", "AU");

        assertThat(r.get(TokenType.CITY)).hasValue("Melbourne");
    }
}
