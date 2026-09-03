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

import java.util.List;

import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.parser.support.CommaSegmentCityExtractor.Result;

class CommaSegmentCityExtractorTest {

    @Test
    void twoSegments_lastIsCity_noNeighborhood() {
        Result r = CommaSegmentCityExtractor.extractLastAsCity(
            List.of("350 Fifth Avenue", "New York"), true);

        assertThat(r.city()).isEqualTo("New York");
        assertThat(r.neighborhood()).isNull();
        assertThat(r.streetLine()).isEqualTo("350 Fifth Avenue");
    }

    @Test
    void threeSegments_withNeighborhoodRequested_extractsBoth() {
        Result r = CommaSegmentCityExtractor.extractLastAsCity(
            List.of("55 Mark Lane", "The Corn Exchange", "London"), true);

        assertThat(r.city()).isEqualTo("London");
        assertThat(r.neighborhood()).isEqualTo("The Corn Exchange");
        assertThat(r.streetLine()).isEqualTo("55 Mark Lane");
    }

    @Test
    void threeSegments_withoutNeighborhoodRequested_onlyCityExtracted() {
        Result r = CommaSegmentCityExtractor.extractLastAsCity(
            List.of("350 Fifth Avenue", "Suite 100", "New York"), false);

        assertThat(r.city()).isEqualTo("New York");
        assertThat(r.neighborhood()).isNull();
        assertThat(r.streetLine()).isEqualTo("350 Fifth Avenue, Suite 100");
    }

    @Test
    void singleSegment_noCity() {
        Result r = CommaSegmentCityExtractor.extractLastAsCity(List.of("350 Fifth Avenue"), true);

        assertThat(r.city()).isNull();
        assertThat(r.neighborhood()).isNull();
        assertThat(r.streetLine()).isEqualTo("350 Fifth Avenue");
    }

    @Test
    void looksLikeFloorOrUnitMarker_recognisesOrdinalFloor() {
        assertThat(CommaSegmentCityExtractor.looksLikeFloorOrUnitMarker("6TH FLOOR")).isTrue();
        assertThat(CommaSegmentCityExtractor.looksLikeFloorOrUnitMarker("18th Floor")).isTrue();
        assertThat(CommaSegmentCityExtractor.looksLikeFloorOrUnitMarker("Suite 400")).isTrue();
        assertThat(CommaSegmentCityExtractor.looksLikeFloorOrUnitMarker("Unit 3B")).isTrue();
    }

    @Test
    void looksLikeFloorOrUnitMarker_falseForRealCityNames() {
        assertThat(CommaSegmentCityExtractor.looksLikeFloorOrUnitMarker("London")).isFalse();
        assertThat(CommaSegmentCityExtractor.looksLikeFloorOrUnitMarker("New York")).isFalse();
        assertThat(CommaSegmentCityExtractor.looksLikeFloorOrUnitMarker("Unit City")).isFalse();
    }
}
