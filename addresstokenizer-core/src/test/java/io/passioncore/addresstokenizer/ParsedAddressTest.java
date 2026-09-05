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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;

/**
 * {@link ParsedAddress#countryName()} must not throw for the {@code "UNKNOWN"}
 * country-detection-failure sentinel (or any other non-ISO country value) — it isn't a
 * valid {@code Locale} region, so {@code new Locale.Builder().setRegion(code)} throws
 * {@code IllformedLocaleException}. {@code AddressEnrichmentService.countryDisplayName()}
 * (Pro) already guards against this for token construction; this test locks in the
 * equivalent guard on {@code countryName()} itself, which {@code general()}
 * JSON view calls directly — a real 500 (HttpMessageNotWritableException) on {@code GET
 * /enrich} was reproduced for an input that resolves country to "UNKNOWN" before this fix.
 */
class ParsedAddressTest {

    @Test
    void countryName_returnsNullInsteadOfThrowing_forUnknownSentinel() {
        ParsedAddress address = new ParsedAddress(
            "1", "UNKNOWN", 0.0,
            List.of(new AddressToken(TokenType.COUNTRY_CODE, "UNKNOWN")));

        assertThatCode(address::countryName).doesNotThrowAnyException();
        assertThat(address.countryName()).isNull();
    }

    @Test
    void countryName_returnsDisplayName_forValidIsoCode() {
        ParsedAddress address = new ParsedAddress(
            "10 Downing Street", "GB", 0.0,
            List.of(new AddressToken(TokenType.COUNTRY_CODE, "GB")));

        assertThat(address.countryName()).isEqualTo("United Kingdom");
    }
}
