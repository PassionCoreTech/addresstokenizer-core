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
 * Regression pins: a leading non-numeric reference/tracking-number line (e.g.
 * a bank/SWIFT-style ID) confused {@code QuebecFrenchParser}'s street/city line-boundary
 * detection, and the municipality line never stripped a trailing self-referential country name.
 * Root cause and investigation trail recorded in the plan itself.
 */
class QuebecFrenchParserReferenceNumberTest {

    private CaAddressParser parser() {
        return new CaAddressParser(new QuebecFrenchDetector(), new QuebecFrenchParser());
    }

    @Test
    void referenceNumberPrefix_doesNotCorruptStreetOrCity() {
        ParsedAddress r = parser().parse(
            "CA783643864230, 14 RUE DE TROIS-RIVIERES, MONTREAL CANADA", "CA");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("14");
        assertThat(r.get(TokenType.STREET_TYPE)).hasValue("RUE");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("DE TROIS-RIVIERES");
        assertThat(r.get(TokenType.CITY)).hasValue("MONTREAL");
    }

    @Test
    void sameAddressWithoutReferenceNumber_isUnchanged() {
        // Regression guard: the fix must not alter the already-correct baseline case.
        ParsedAddress r = parser().parse(
            "14 RUE DE TROIS-RIVIERES, MONTREAL CANADA", "CA");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("14");
        assertThat(r.get(TokenType.STREET_TYPE)).hasValue("RUE");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("DE TROIS-RIVIERES");
        assertThat(r.get(TokenType.CITY)).hasValue("MONTREAL");
    }

    @Test
    void trailingSelfReference_doesNotOverwriteCity() {
        // Same bug class already fixed in five other parsers, applied here.
        ParsedAddress r = parser().parse(
            "123 RUE SAINT-DENIS, MONTREAL CANADA", "CA");

        assertThat(r.get(TokenType.CITY)).hasValue("MONTREAL");
    }

    @Test
    void trailingCountryCodeCA_doesNotOverwriteCity() {
        ParsedAddress r = parser().parse(
            "123 RUE SAINT-DENIS, MONTREAL CA", "CA");

        assertThat(r.get(TokenType.CITY)).hasValue("MONTREAL");
    }

    @Test
    void bareSelfReference_withNoRealCity_isLeftAlone() {
        // Never strip "CANADA"/"CA" down to nothing when there's no real content before it.
        ParsedAddress r = parser().parse("123 RUE SAINT-DENIS, CANADA", "CA");

        assertThat(r.get(TokenType.CITY)).hasValue("CANADA");
    }

    @Test
    void existingTwoLineShape_unaffectedByLineBoundaryChange() {
        // Regression guard against the line-boundary fix above: the common
        // "<digit+type+name>\n<city province postal>" shape (already covered elsewhere, e.g.
        // CaAddressParserEdgeCaseTest's CA_EDGE_006) must still resolve identically.
        ParsedAddress r = parser().parse("123 RUE SAINT-DENIS OUEST\nMONTREAL QC H2X 3K1", "CA");

        assertThat(r.get(TokenType.STREET_TYPE)).hasValue("RUE");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("SAINT-DENIS");
        assertThat(r.get(TokenType.CITY)).hasValue("MONTREAL");
        assertThat(r.get(TokenType.STATE_CODE)).hasValue("QC");
        assertThat(r.get(TokenType.POSTAL_CODE)).hasValue("H2X 3K1");
    }
}
