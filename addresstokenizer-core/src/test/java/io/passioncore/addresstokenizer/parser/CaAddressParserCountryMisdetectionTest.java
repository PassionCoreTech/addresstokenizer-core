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

import java.util.List;

import org.junit.jupiter.api.Test;

import io.passioncore.addresstokenizer.AddressTokenizer;
import io.passioncore.addresstokenizer.detector.CountryDetector;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;
import io.passioncore.addresstokenizer.utils.NormalizationUtil;

/**
 * Documents a known, currently-uncorrected bug: {@link CountryDetector#detect(String)}
 * matches the literal country name {@code "CANADA"} before it ever considers whether
 * the rest of the address is US-shaped, so a US address that happens to end with the
 * word "CANADA" gets wrongly dispatched to {@link CaAddressParser} — which cannot parse
 * a US-format address and corrupts its output.
 *
 * <p>This test pins down exactly what survives that corruption, for
 * {@code docs/plans/028} (Decision 1): {@code POSTAL_CODE} does <b>not</b> survive.
 * {@link CaAddressParser}'s postal-code regexes only recognise the Canadian
 * {@code A1A 1A1}/{@code A1A} shapes, so a US 5-digit ZIP ("10118") never matches and no
 * {@code POSTAL_CODE} token is emitted at all. Worse, because {@code "NY"} is not a
 * recognised Canadian province abbreviation, the {@code STATE_CODE} step also fails to
 * match, and {@code CaAddressParser}'s step-3 city bucket (which unconditionally
 * overwrites {@code city} with each subsequent non-unit segment — see
 * {@code CaAddressParser.java} step 3) ends up swallowing the leftover
 * {@code "NY 10118"} segment whole into the {@code CITY} token, one segment after the
 * legitimate city ("NEW YORK") was already captured and then overwritten.</p>
 *
 * <p>This is a permanent regression pin, not a throwaway: if {@code CaAddressParser} or
 * {@code CountryDetector} is ever changed such that {@code POSTAL_CODE} starts surviving
 * (or the country-detection cascade is fixed so {@code CaAddressParser} no longer runs
 * on this input), this test's assertions will need deliberate updating — that's the
 * point, so the change is visible rather than silent.</p>
 */
class CaAddressParserCountryMisdetectionTest {

    @Test
    void usAddressDeclaringCanada_dispatchesToCaParser_andLosesPostalCode() {
        List<AddressParser> parsers = List.of(
            new UsAddressParser(),
            new UkAddressParser(),
            new DeAddressParser(),
            new FrAddressParser(),
            new AuAddressParser(),
            new CaAddressParser(new QuebecFrenchDetector(), new QuebecFrenchParser())
        );
        AddressTokenizer tokenizer = new AddressTokenizer(
            new CountryDetector(parsers),
            parsers,
            new GenericAddressParser(),
            new NormalizationUtil(),
            null   // no AddressHealer — Core-only mode
        );

        ParsedAddress p = tokenizer.parse("350 Fifth Avenue, New York, NY 10118, CANADA");

        // CountryDetector.detect() matches the literal "CANADA" name hint before any
        // US-shape check runs, so CaAddressParser (not UsAddressParser) handles this.
        assertThat(p.countryCode()).isEqualTo("CA");
        assertThat(p.country()).isEqualTo("CA");

        // POSTAL_CODE does NOT survive: CaAddressParser's postal regexes only match
        // Canadian A1A 1A1 / A1A shapes, never a bare 5-digit US ZIP.
        assertThat(p.get(TokenType.POSTAL_CODE)).isEmpty();
        assertThat(p.postalCode()).isNull();

        // STATE_CODE also does not survive: "NY" is not a recognised CA province code.
        assertThat(p.get(TokenType.STATE_CODE)).isEmpty();

        // CITY is corrupted: step 3's leftover-city bucket overwrites "NEW YORK" with
        // the next non-unit segment ("NY 10118") instead of accumulating/preserving it.
        assertThat(p.city()).isEqualTo("NY 10118");
    }
}
