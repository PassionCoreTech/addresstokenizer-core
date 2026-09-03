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

class NewlineFallbackSplitterTest {

    private static final Pattern POSTAL_CITY =
        Pattern.compile("(\\d{5})\\s+(.+?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STREET_TYPE =
        Pattern.compile("(?i)\\b(Rue|Avenue|Boulevard)\\b");

    @Test
    void split_fallsBackToNewline_whenNoCommaPresent() {
        String[] result = NewlineFallbackSplitter.split("line one\nline two\nline three", 2);

        assertThat(result).containsExactly("line one", "line two\nline three");
    }

    @Test
    void split_prefersComma_whenPresent() {
        String[] result = NewlineFallbackSplitter.split("a, b, c", 2);

        assertThat(result).containsExactly("a", " b, c");
    }

    @Test
    void split_returnsWholeStringUnsplit_whenNoCommaAndNoNewline() {
        String[] result = NewlineFallbackSplitter.split("single line only", 2);

        assertThat(result).containsExactly("single line only");
    }

    @Test
    void trySegmentByLine_findsStreetAndCityLines_ignoringNoiseLines() {
        NewlineFallbackSplitter.Segments seg = NewlineFallbackSplitter.trySegmentByLine(
            "FR783643860\n14 RUE DU PEUPLE\n13340 MARSEILLE FRANCE\nTEL: 067534012",
            POSTAL_CITY, STREET_TYPE);

        assertThat(seg).isNotNull();
        assertThat(seg.streetLine()).isEqualTo("14 RUE DU PEUPLE");
        assertThat(seg.cityLine()).isEqualTo("13340 MARSEILLE FRANCE");
    }

    @Test
    void trySegmentByLine_returnsNull_whenCommaAlreadyPresent() {
        NewlineFallbackSplitter.Segments seg = NewlineFallbackSplitter.trySegmentByLine(
            "14 Rue du Peuple, 13340 Marseille", POSTAL_CITY, STREET_TYPE);

        assertThat(seg).isNull();
    }

    @Test
    void trySegmentByLine_returnsNull_whenNoNewline() {
        NewlineFallbackSplitter.Segments seg = NewlineFallbackSplitter.trySegmentByLine(
            "14 Rue du Peuple 13340 Marseille", POSTAL_CITY, STREET_TYPE);

        assertThat(seg).isNull();
    }

    @Test
    void trySegmentByLine_returnsNull_whenNoLineMatchesCityPattern() {
        NewlineFallbackSplitter.Segments seg = NewlineFallbackSplitter.trySegmentByLine(
            "14 Rue du Peuple\nsome other line", POSTAL_CITY, STREET_TYPE);

        assertThat(seg).isNull();
    }
}
