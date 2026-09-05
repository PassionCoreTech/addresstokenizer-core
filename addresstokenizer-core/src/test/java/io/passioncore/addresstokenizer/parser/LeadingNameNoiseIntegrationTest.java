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
 * Regression pins: a leading recipient-name segment (with or without a
 * salutation) used to leak into {@code STREET_NAME} or block a {@code ^}-anchored
 * {@code HOUSE_NO} pattern entirely, across every Core country parser. Fixed via the shared
 * {@code LeadingNameNoiseStripper} (see its own unit tests for the stripping-rule coverage) — this
 * file confirms each parser actually calls it and produces the expected downstream tokens.
 */
class LeadingNameNoiseIntegrationTest {

    @Test
    void us_bareNameBeforeHouseNumber_isStripped() {
        ParsedAddress r = new UsAddressParser().parse(
            "JOHN SMITH, 126 CUBA AVE, NEW YORK CITY, NY 10306, UNITED STATES", "US");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("126");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("CUBA");
    }

    @Test
    void uk_salutationLedName_isStripped() {
        ParsedAddress r = new UkAddressParser().parse(
            "Mr. John Smith, 10 Downing Street, London SW1A 2AA", "GB");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("10");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("Downing");
    }

    @Test
    void de_salutationLedName_isStripped() {
        // DE's own convention (house number trails the street name, e.g. "Unter den Linden 6")
        // means a bare name with no salutation has no safe leading-digit signal to corroborate on
        // -- only the salutation-led tier applies here, deliberately (see
        // LeadingNameNoiseStripper's class docs).
        ParsedAddress r = new DeAddressParser().parse(
            "Mr. John Smith, Unter den Linden 6, 10117 Berlin", "DE");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("6");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("Unter den Linden");
    }

    @Test
    void fr_bareNameBeforeHouseNumber_isStripped() {
        ParsedAddress r = new FrAddressParser().parse(
            "Jean Dupont, 15 Rue de la Paix, 75001 Paris", "FR");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("15");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("de la Paix");
    }

    @Test
    void au_bareNameBeforeHouseNumber_isStripped() {
        ParsedAddress r = new AuAddressParser().parse(
            "John Smith, 123 Collins Street, Melbourne VIC 3000", "AU");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("123");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("Collins");
    }

    @Test
    void ca_bareNameBeforeHouseNumber_isStripped() {
        ParsedAddress r = new CaAddressParser(new QuebecFrenchDetector(), new QuebecFrenchParser())
            .parse("John Smith, 120 Adelaide Street West, Toronto, ON M5H 1T1", "CA");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("120");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("ADELAIDE");
    }

    @Test
    void us_legitimateAddressWithoutLeadingName_isUnaffected() {
        // Regression guard: no false-positive stripping of a normal address.
        ParsedAddress r = new UsAddressParser().parse(
            "350 Fifth Avenue, New York, NY 10118", "US");

        assertThat(r.get(TokenType.HOUSE_NO)).hasValue("350");
        assertThat(r.get(TokenType.STREET_NAME)).hasValue("Fifth");
    }

    @Test
    void us_placeNameOnlyAddress_notMistakenForARecipientName() {
        // Regression guard: a real no-house-number address ("Times Square, New York NY 10036")
        // was briefly, wrongly stripped by an earlier, looser version of this fix -- the postcode
        // digit further along satisfied too-loose corroboration. STREET_NAME must stay intact.
        ParsedAddress r = new UsAddressParser().parse(
            "Times Square, New York NY 10036", "US");

        assertThat(r.get(TokenType.STREET_NAME)).hasValue("Times Square");
    }

    @Test
    void uk_placeNameOnlyAddress_notMistakenForARecipientName() {
        // Same class of regression as the US case above, for UK's "House of Commons, London
        // SW1A 0AA" (no house number, real building name only).
        ParsedAddress r = new UkAddressParser().parse(
            "House of Commons, London SW1A 0AA", "GB");

        assertThat(r.get(TokenType.STREET_NAME)).hasValue("House of Commons");
    }

}
