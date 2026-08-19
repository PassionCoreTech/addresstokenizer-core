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
import io.passioncore.addresstokenizer.utils.NormalizationUtil;

/**
 * Regression pin for a real user-reported bug: a bare {@code "CA"} segment trailing the
 * city (a redundant, non-standard country-code declaration -- "CA" is not a valid
 * Canadian province abbreviation, see {@link CaAddressParser#PROVINCE}) survived Step 2's
 * province strip and then clobbered the already-correctly-captured city in Step 3's
 * unconditional {@code city = leftover} assignment, because only the literal word
 * {@code "CANADA"} was excluded there, not the ISO code {@code "CA"} itself.
 *
 * <p>Before the fix: {@code CITY} came out as {@code "CA"} instead of {@code "RICHMOND"},
 * which downstream (Pro's {@code GazeteerEnricher}) could substring-match an unrelated
 * country entirely (see the P5/P6 fix in the same investigation).</p>
 */
class CaAddressParserCountryCodeSuffixTest {

    @Test
    void bareCaSuffixDoesNotOverwriteCity() {
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
            null   // no AddressHealer -- Core-only mode
        );

        ParsedAddress p = tokenizer.parse("350 No. 1 Road, Richmond , CA, V6Y 3W9");

        assertThat(p.countryCode()).isEqualTo("CA");
        assertThat(p.city()).isEqualTo("RICHMOND");
        assertThat(p.postalCode()).isEqualTo("V6Y 3W9");
    }
}
