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

package io.passioncore.addresstokenizer.parser.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.parser.support.HouseNumberExtractor.Result;

class HouseNumberExtractorTest {

    private static final Pattern US_LEADING = Pattern.compile("^(\\d+[A-Za-z]?)\\s+");
    private static final Pattern DE_TRAILING = Pattern.compile("\\s+(\\d+\\s*[a-zA-Z]?)$");

    @Test
    void leading_usStyle_houseNumberBeforeStreet() {
        Result r = HouseNumberExtractor.extractLeading("350 Fifth Avenue", US_LEADING);

        assertThat(r.houseNo()).isEqualTo("350");
        assertThat(r.remaining()).isEqualTo("Fifth Avenue");
    }

    @Test
    void leading_noMatch_returnsNullAndUnchangedText() {
        Result r = HouseNumberExtractor.extractLeading("Fifth Avenue", US_LEADING);

        assertThat(r.houseNo()).isNull();
        assertThat(r.remaining()).isEqualTo("Fifth Avenue");
    }

    @Test
    void trailing_deStyle_houseNumberAfterStreet() {
        Result r = HouseNumberExtractor.extractTrailing("Hauptstrasse 42", DE_TRAILING);

        assertThat(r.houseNo()).isEqualTo("42");
        assertThat(r.remaining()).isEqualTo("Hauptstrasse");
    }

    @Test
    void trailing_withLetterSuffix() {
        Result r = HouseNumberExtractor.extractTrailing("Hauptstrasse 42a", DE_TRAILING);

        assertThat(r.houseNo()).isEqualTo("42a");
        assertThat(r.remaining()).isEqualTo("Hauptstrasse");
    }
}
