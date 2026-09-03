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

import org.junit.jupiter.api.Test;

class LeadingNameNoiseStripperTest {

    @Test
    void bareNameFollowedByDigitSegment_isStripped() {
        String result = LeadingNameNoiseStripper.strip(
            "JOHN SMITH, 126 CUBA AVE, NEW YORK CITY, NY 10306, UNITED STATES");

        assertThat(result).isEqualTo("126 CUBA AVE, NEW YORK CITY, NY 10306, UNITED STATES");
    }

    @Test
    void salutationLed_isStrippedEvenWithoutADigitAfter() {
        String result = LeadingNameNoiseStripper.strip("Mr. John Smith, Main Street, Springfield");

        assertThat(result).isEqualTo("Main Street, Springfield");
    }

    @Test
    void noComma_returnsUnchanged() {
        String input = "126 Cuba Ave";

        assertThat(LeadingNameNoiseStripper.strip(input)).isEqualTo(input);
    }

    @Test
    void leadingSegmentAlreadyHasDigits_notTreatedAsName() {
        // "126 Cuba Ave" itself contains a digit, so it must never be mistaken for a name.
        String input = "126 Cuba Ave, New York City, NY 10306";

        assertThat(LeadingNameNoiseStripper.strip(input)).isEqualTo(input);
    }

    @Test
    void bareNameWithoutDigitFollowing_isLeftAlone() {
        // No corroborating digit right after the comma -- too risky to strip without a
        // salutation, since this could be real address content (e.g. "Springfield, IL").
        String input = "Springfield, IL";

        assertThat(LeadingNameNoiseStripper.strip(input)).isEqualTo(input);
    }

    @Test
    void tooManyWordsBeforeComma_notTreatedAsBareName() {
        String input = "This Is Way Too Many Words Before The Comma, 5 Main St";

        assertThat(LeadingNameNoiseStripper.strip(input)).isEqualTo(input);
    }
}
