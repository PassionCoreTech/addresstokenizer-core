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

import io.passioncore.addresstokenizer.parser.support.PostalCodeStripper.StripResult;

class PostalCodeStripperTest {

    private static final Pattern ZIP = Pattern.compile("\\b(\\d{5}(?:-\\d{4})?)\\b");
    private static final Pattern UK_POSTCODE =
        Pattern.compile("\\b([A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2})\\b", Pattern.CASE_INSENSITIVE);

    @Test
    void postcodeAtEnd_typicalCase_remainingAfterIsEmpty() {
        StripResult r = PostalCodeStripper.stripLastMatch("10 Downing Street, London SW1A 2AA", UK_POSTCODE);

        assertThat(r.matchedValue()).isEqualTo("SW1A 2AA");
        assertThat(r.remainingBefore()).isEqualTo("10 Downing Street, London");
        assertThat(r.remainingAfter()).isEmpty();
    }

    @Test
    void postcodeMidString_remainingAfterCapturesTrailingContent() {
        // The docs/plans/040.05 shape: postcode is followed by more real content.
        StripResult r = PostalCodeStripper.stripLastMatch(
            "55 MARK LANE, THE CORN EXCHANGE, 6TH FLOOR, EC3R 7NE, LONDON, GB", UK_POSTCODE);

        assertThat(r.matchedValue()).isEqualTo("EC3R 7NE");
        assertThat(r.remainingBefore()).isEqualTo("55 MARK LANE, THE CORN EXCHANGE, 6TH FLOOR");
        assertThat(r.remainingAfter()).isEqualTo("LONDON, GB");
    }

    @Test
    void noMatch_returnsNullValueAndFullInputAsBefore() {
        StripResult r = PostalCodeStripper.stripLastMatch("No postcode here", UK_POSTCODE);

        assertThat(r.matchedValue()).isNull();
        assertThat(r.remainingBefore()).isEqualTo("No postcode here");
        assertThat(r.remainingAfter()).isEmpty();
    }

    @Test
    void multipleMatches_picksTheLast() {
        // Matches every existing parser's while(matcher.find())-keep-last loop.
        StripResult r = PostalCodeStripper.stripLastMatch("350 Fifth Avenue, New York, NY 10001-0001, Suite 90210", ZIP);

        assertThat(r.matchedValue()).isEqualTo("90210");
        assertThat(r.remainingBefore()).isEqualTo("350 Fifth Avenue, New York, NY 10001-0001, Suite");
    }
}
