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
package io.passioncore.sample.plainjava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.model.ParsedAddress;

/**
 * Confirms {@link AddressParsingClient} works exactly as advertised: constructed with a plain
 * {@code new}, no Spring container running anywhere in this test.
 */
class AddressParsingClientTest {

    private final AddressParsingClient client = new AddressParsingClient();

    @Test
    void parse_usAddress_namedAccessors() {
        ParsedAddress p = client.parse("350 Fifth Avenue, New York, NY 10118");
        assertThat(p.countryCode()).isEqualTo("US");
        assertThat(p.city()).isEqualTo("NEW YORK");
        assertThat(p.parseConfidence()).isGreaterThan(0.80);
    }

    @Test
    void parseBatch_multipleAddresses_eachResolvesIndependently() {
        List<ParsedAddress> results = client.parseBatch(List.of(
            "10 Downing Street, London SW1A 2AA",
            "12 Rue de Rivoli, 75001 Paris, France"
        ));
        assertThat(results).hasSize(2);
        assertThat(results.get(0).countryCode()).isEqualTo("GB");
        assertThat(results.get(1).countryCode()).isEqualTo("FR");
    }

    @Test
    void parseLines_multiLineAddress() {
        ParsedAddress p = client.parseLines(List.of("350 Fifth Avenue", "New York, NY 10118"));
        assertThat(p.countryCode()).isEqualTo("US");
    }
}
