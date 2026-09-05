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

import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;

class GenericAddressParserTest {

    private final GenericAddressParser parser = new GenericAddressParser();

    @Test
    void belgiumWorkedExample_cityIsBrussels_notFloorOrCountryCode() {
        // Regression: a postal-code segment used to keep only the digits and silently drop
        // the real city sharing that segment ("1000 BRUSSELS" -> POSTAL_CODE=1000, city lost),
        // while every other non-first segment -- including a floor marker and the bare
        // trailing country code -- was blindly labeled CITY.
        ParsedAddress result = parser.parse("HOOGSTRAAT 6, 18TH FLOOR, 1000 BRUSSELS, BE", "BE");

        assertThat(result.tokens()).containsExactly(
            new AddressToken(TokenType.STREET_NAME, "HOOGSTRAAT 6"),
            new AddressToken(TokenType.UNIT, "18TH FLOOR"),
            new AddressToken(TokenType.POSTAL_CODE, "1000"),
            new AddressToken(TokenType.CITY, "BRUSSELS")
        );
    }

    @Test
    void postalCodeSegmentWithNoOtherText_noSpuriousCityToken() {
        ParsedAddress result = parser.parse("123 Main St, 12345", "ZZ");

        assertThat(result.tokens()).containsExactly(
            new AddressToken(TokenType.STREET_NAME, "123 Main St"),
            new AddressToken(TokenType.POSTAL_CODE, "12345")
        );
    }

    @Test
    void plainTwoSegmentAddress_lastSegmentIsCity() {
        ParsedAddress result = parser.parse("123 Main St, Springfield", "ZZ");

        assertThat(result.tokens()).containsExactly(
            new AddressToken(TokenType.STREET_NAME, "123 Main St"),
            new AddressToken(TokenType.CITY, "Springfield")
        );
    }

    @Test
    void multiSegmentAddress_onlyLastSegmentIsCity() {
        // Regression: every non-first segment (floor, building name, street, district) was
        // blindly labeled CITY, producing multiple CITY tokens for one address -- an address
        // has exactly one city. Only the final segment should be trusted as CITY.
        ParsedAddress result = parser.parse(
                "FLAT 25, 12/F, ACACIA BUILDING, 150 KENNEDY ROAD, WAN CHAI, HONG KONG", "HK");

        assertThat(result.tokens()).containsExactly(
            new AddressToken(TokenType.STREET_NAME, "FLAT 25"),
            new AddressToken(TokenType.STREET_NAME, "12/F"),
            new AddressToken(TokenType.STREET_NAME, "ACACIA BUILDING"),
            new AddressToken(TokenType.STREET_NAME, "150 KENNEDY ROAD"),
            new AddressToken(TokenType.STREET_NAME, "WAN CHAI"),
            new AddressToken(TokenType.CITY, "HONG KONG")
        );
    }

    @Test
    void bareTrailingCountryCode_notLabeledAsCity() {
        // AddressTokenizer already adds a COUNTRY_CODE token separately (withCountryCodeToken);
        // the generic fallback must not also emit the same value mislabeled as CITY.
        ParsedAddress result = parser.parse("1 Foo St, Nowhereville, ZZ", "ZZ");

        assertThat(result.tokens()).containsExactly(
            new AddressToken(TokenType.STREET_NAME, "1 Foo St"),
            new AddressToken(TokenType.CITY, "Nowhereville")
        );
    }
}
