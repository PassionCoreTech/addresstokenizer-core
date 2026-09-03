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
 * Regression pin for docs/plans/040.05 / 045.01: {@code UkAddressParser} used to discard any
 * address content trailing a matched postcode, so a real ANZ-published address ordering
 * (postcode, then town, then country) produced no {@code CITY} token at all -- the floor
 * descriptor before the postcode got misread as the city instead. Fixed as part of migrating the
 * parser onto the shared {@code parser.support} toolkit (docs/plans/045).
 */
class UkAddressParserTrailingContentTest {

    @Test
    void cityAfterPostcode_isRecovered_notTheFloorDescriptorBeforeIt() {
        ParsedAddress r = new UkAddressParser().parse(
            "55 MARK LANE, THE CORN EXCHANGE, 6TH FLOOR, EC3R 7NE, LONDON, GB", "GB");

        assertThat(r.get(TokenType.CITY)).hasValue("LONDON");
        assertThat(r.get(TokenType.POSTAL_CODE)).hasValue("EC3R 7NE");
        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("55");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("MARK");
        assertThat(r.get(TokenType.STREET_TYPE)).hasValue("LANE");
        // Must not be silently dropped just because the fix routes CITY through a different path.
        assertThat(r.get(TokenType.NEIGHBORHOOD)).hasValue("THE CORN EXCHANGE");
    }

    @Test
    void ordinalFloorDescriptor_capturedAsUnit_notLeakedIntoStreetName() {
        ParsedAddress r = new UkAddressParser().parse(
            "55 MARK LANE, THE CORN EXCHANGE, 6TH FLOOR, EC3R 7NE, LONDON, GB", "GB");

        assertThat(r.get(TokenType.UNIT)).hasValue("6TH FLOOR");
    }

    @Test
    void trailingSelfReferenceOnly_doesNotOverrideCity() {
        // ", GB" alone after the postcode, with no real town -- must not be treated as a city.
        ParsedAddress r = new UkAddressParser().parse(
            "10 Downing Street, SW1A 2AA, GB", "GB");

        assertThat(r.get(TokenType.CITY)).isEmpty();
    }

    @Test
    void normalPostcodeAtEndLayout_stillWorksUnchanged() {
        // The classic, overwhelmingly common shape -- nothing after the postcode.
        ParsedAddress r = new UkAddressParser().parse(
            "10 Downing Street, London SW1A 2AA", "GB");

        assertThat(r.get(TokenType.CITY)).hasValue("London");
        assertThat(r.get(TokenType.POSTAL_CODE)).hasValue("SW1A 2AA");
    }
}
