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
package io.passioncore.addresstokenizer.detector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.parser.AddressParser;
import io.passioncore.addresstokenizer.parser.CaAddressParser;
import io.passioncore.addresstokenizer.parser.QuebecFrenchDetector;
import io.passioncore.addresstokenizer.parser.QuebecFrenchParser;
import io.passioncore.addresstokenizer.parser.UsAddressParser;

/**
 * Core-only tests for {@link CountryDetector}, covering {@link #detectDeclaredCountryCode}
 * (plan 026) and the equal-length {@code COUNTRY_NAME_HINT} tie-break (plan 036). Broader
 * {@code detect()}/{@code detectInTail()} coverage lives in {@code addresstokenizer-pro}'s
 * {@code CountryDetectorTest} (which exercises the full parser list); this class carries
 * only what needs standalone coverage in this module.
 */
class CountryDetectorTest {

    private final List<AddressParser> parsers = List.of(
        new UsAddressParser(),
        new CaAddressParser(new QuebecFrenchDetector(), new QuebecFrenchParser())
    );

    private final CountryDetector detector = new CountryDetector(parsers);

    @Test
    void trailingCountryWinsOverEarlierEqualLengthNameCollision() {
        // "HONG KONG" (9 chars) appears twice as unrelated proper nouns (the sending
        // organization's name, the building name); "AUSTRALIA" (9 chars) appears once,
        // correctly, as the trailing country declaration. Real-world reported case
        // (plan 036) that previously resolved to HK at 0.97 confidence. No AU parser is
        // registered here -- detect() resolves this via the COUNTRY_NAME_HINT tie-break
        // alone, before any postal-code pattern is even consulted.
        assertThat(detector.detect(
            "Hong Kong Tourism Board - Sydney Office Level 4 Hong Kong House "
            + "80 Druitt Street SYDNEY NSW 2000 ,NEW SOUTH WALES, AUSTRALIA"))
            .isEqualTo("AU");
    }

    @Nested
    class DetectDeclaredCountryCode {

        @Test
        void matchesBareCodeAloneInLastSegment() {
            assertThat(detector.detectDeclaredCountryCode("350 Fifth Avenue, New York, NY 10118, CA"))
                .contains("CA");
        }

        @Test
        void matchesAndUppercasesLowercaseBareCode() {
            assertThat(detector.detectDeclaredCountryCode("350 Fifth Avenue, New York, NY 10118, ca"))
                .contains("CA");
        }

        @Test
        void doesNotMatchStateAndZipSharingOneSegment() {
            // "NY 10118" is the last comma segment -- two tokens, one numeric, never qualifies.
            assertThat(detector.detectDeclaredCountryCode("350 Fifth Avenue, New York, NY 10118"))
                .isEqualTo(Optional.empty());
        }

        @Test
        void doesNotMatchWhenAddressHasNoComma() {
            assertThat(detector.detectDeclaredCountryCode("350 Fifth Avenue New York NY 10118"))
                .isEqualTo(Optional.empty());
        }

        @Test
        void returnsEmptyForBlankOrNullInput() {
            assertThat(detector.detectDeclaredCountryCode("")).isEqualTo(Optional.empty());
            assertThat(detector.detectDeclaredCountryCode(null)).isEqualTo(Optional.empty());
        }
    }
}
