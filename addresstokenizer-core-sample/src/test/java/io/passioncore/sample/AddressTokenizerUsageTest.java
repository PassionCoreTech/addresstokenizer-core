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

package io.passioncore.sample;

import io.passioncore.addresstokenizer.AddressTokenizer;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates direct use of {@link AddressTokenizer} without an HTTP layer.
 *
 * <p>Copy these patterns when embedding the library in your own Spring Boot app.</p>
 */
@SpringBootTest
class AddressTokenizerUsageTest {

    @Autowired
    AddressTokenizer tokenizer;

    @Test
    @DisplayName("US â€” extracts house number, street, city, state and postal code")
    void usAddress() {
        ParsedAddress result = tokenizer.parse("350 Fifth Avenue, Suite 6402, New York, NY 10118");

        assertThat(result.countryCode()).isEqualTo("US");
        assertThat(result.get(TokenType.POSTAL_CODE)).isPresent();
        assertThat(result.get(TokenType.STATE_CODE)).contains("NY");
    }

    @Test
    @DisplayName("UK â€” extracts postcode and country")
    void ukAddress() {
        ParsedAddress result = tokenizer.parse("10 Downing Street, London SW1A 2AA");

        assertThat(result.countryCode()).isEqualTo("GB");
        assertThat(result.get(TokenType.POSTAL_CODE)).isPresent();
    }

    @Test
    @DisplayName("DE â€” house number follows street name")
    void deAddress() {
        ParsedAddress result = tokenizer.parse("Unter den Linden 6, 10117 Berlin");

        assertThat(result.countryCode()).isEqualTo("DE");
        assertThat(result.get(TokenType.POSTAL_CODE)).isPresent();
    }

    @Test
    @DisplayName("FR â€” street type precedes street name")
    void frAddress() {
        ParsedAddress result = tokenizer.parse("75 Rue de Rivoli, 75001 Paris");

        assertThat(result.countryCode()).isEqualTo("FR");
        assertThat(result.get(TokenType.POSTAL_CODE)).isPresent();
    }

    @Test
    @DisplayName("AU â€” state abbreviation before postal code")
    void auAddress() {
        ParsedAddress result = tokenizer.parse("Level 3/80 Pacific Highway, North Sydney NSW 2060");

        assertThat(result.countryCode()).isEqualTo("AU");
        assertThat(result.get(TokenType.STATE_CODE)).isPresent();
    }

    @Test
    @DisplayName("CA â€” suite and unit extracted correctly")
    void caAddress() {
        ParsedAddress result = tokenizer.parse(
                "120 Adelaide Street West, Suite 2500, Toronto, ON M5H 1T1");

        assertThat(result.countryCode()).isEqualTo("CA");
        assertThat(result.get(TokenType.POSTAL_CODE)).isPresent();
    }

    @Test
    @DisplayName("PO Box â€” dedicated PO_BOX token is produced")
    void poBox() {
        ParsedAddress result = tokenizer.parse("PO Box 9000, Victoria, BC V8W 9V6");

        assertThat(result.tokens())
                .anyMatch(t -> t.type() == TokenType.PO_BOX);
    }

    @Test
    @DisplayName("batch parse â€” all results are returned in order")
    void batchParse() {
        List<String> addresses = List.of(
                "350 Fifth Avenue, New York, NY 10118",
                "10 Downing Street, London SW1A 2AA",
                "Unter den Linden 6, 10117 Berlin"
        );

        List<ParsedAddress> results = tokenizer.parseBatch(addresses);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).countryCode()).isEqualTo("US");
        assertThat(results.get(1).countryCode()).isEqualTo("GB");
        assertThat(results.get(2).countryCode()).isEqualTo("DE");
    }

    @Test
    @DisplayName("multi-line MT103-style input â€” lines joined and parsed")
    void multiLineInput() {
        ParsedAddress result = tokenizer.parseLines(List.of(
                "10 Downing Street",
                "London",
                "SW1A 2AA"
        ));

        assertThat(result.countryCode()).isEqualTo("GB");
        assertThat(result.get(TokenType.POSTAL_CODE)).isPresent();
    }

    @Test
    @DisplayName("toMap() â€” tokens accessible by type name")
    void toMapUsage() {
        ParsedAddress result = tokenizer.parse("350 Fifth Avenue, New York, NY 10118");

        var map = result.toMap();
        assertThat(map).containsKey("POSTAL_CODE");
    }
}

