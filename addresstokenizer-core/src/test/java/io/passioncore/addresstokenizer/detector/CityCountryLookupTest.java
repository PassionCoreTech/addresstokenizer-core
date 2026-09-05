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
package io.passioncore.addresstokenizer.detector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CityCountryLookup#lookupInAddress} scans trailing word-windows and
 * returns on the first hit — verifying it against real SWIFT gauntlet inputs found it returning
 * the wrong country when a trailing word (typically the stated country name itself) coincides
 * with an obscure, unrelated real place name elsewhere in the world.
 */
class CityCountryLookupTest {

    private final CityCountryLookup lookup = CityCountryLookup.createDefault();

    @Test
    void doesNotLetTheCountryNameCanadaShadowTheRealCityMontreal() {
        // Verified failure before the fix: "Canada" is also a real, obscure town in Mexico,
        // and was matched (returning MX) before "Montreal" (correctly CA) was ever tried.
        String address = "CA783643864230\n14 STREET OF HEROES\nN2L 3G1 MONTREAL CANADA\nTEL: 067534012";

        assertThat(lookup.lookupInAddress(address)).contains("CA");
    }

    @Test
    void doesNotLetTheWordAmericaShadowARealCityWhenNoneIsPresent() {
        // Verified failure before the fix: "America" is a real village in Limburg, Netherlands,
        // and was matched (returning NL) before ever considering "Donaldville" (fictional).
        // Tested on the specific segment that carried the bug -- see the residual-limitation
        // test below for why the FULL gauntlet address still resolves wrong, for an entirely
        // different, out-of-scope reason.
        String segment = "DONALDVILLE UNITED STATES OF AMERICA";

        assertThat(lookup.lookupInAddress(segment)).isEmpty();
    }

    @Test
    void doesNotLetTheFirstNameDonaldShadowAnUnrelatedFictionalAddress() {
        // Documented residual limitation: "Donald" (as in "Donald Duck")
        // was itself a real, obscure town in Victoria, Australia (population 1,469) and
        // Oregon/US (1,001) -- an ordinary first name, not a country name or street-type word,
        // so the exclusion sets alone couldn't catch it. This was closed not by adding
        // "donald" to a word list (there's no way to enumerate every common word that might
        // coincidentally be a place name) but by regenerating city_countries.tsv with a
        // 10,000-population floor -- both "Donald" rows fall well under it, so the name is
        // gone from the index entirely and this now correctly resolves nothing.
        String fullAddress = "/89007\nDONALD DUCK\n100 GOLDENPARIS STREET APPARTMENT 14\n"
            + "DONALDVILLE UNITED STATES OF AMERICA";

        assertThat(lookup.lookupInAddress(fullAddress)).isEmpty();
    }

    @Test
    void stillResolvesAGenuineCityWhenTheCountryNameIsAlsoPresent() {
        // Non-regression: excluding the country name must not block the real city that
        // precedes it in the same segment.
        assertThat(lookup.lookupInAddress("PAUL DOMBAIS\nPARIS FRANCE")).contains("FR");
    }

    @Test
    void shortWordIsTrustedWhenItsRealPlaceIsGenuinelySignificant() {
        // This lookup originally excluded every single-word candidate under 4 characters,
        // reasoning "Aba" was an obscure Nigerian town not worth trusting on its own. That
        // premise was wrong -- verified directly against GeoNames population data: "Aba" is
        // Nigeria's third-largest metro area at 1.16 million people, incorrectly excluded only
        // because it's short. The length guard was replaced with a population floor on
        // city_countries.tsv itself (10,000) -- "Aba" clears it easily and correctly resolves.
        assertThat(lookup.lookupInAddress("SOMETHING ABA")).contains("NG");
    }

    @Test
    void doesNotLetTheStopWordOverShadowAnUnrelatedSentenceEnding() {
        // Fold-in fix: "Over" is a real place in England, and unlike "Canada"/"America" it's
        // not a country name -- it's an ordinary English preposition, 4 characters long, so it
        // was NOT caught by the length guard alone. A systematic scan of common English stop
        // words against city_countries.tsv found this and 3 similar collisions (most/same/than).
        assertThat(lookup.lookupInAddress("PACKAGE HANDED OVER")).isEmpty();
    }

    @Test
    void doesNotLetTheStopWordMostShadowAnUnrelatedSentenceEnding() {
        // Second confirmed stop-word collision past the length guard: "Most" is a real place in
        // the Czech Republic, population well above 040.06's 10,000 floor -- the name-based
        // STOP_WORDS exclusion is the only thing still catching this one.
        assertThat(lookup.lookupInAddress("PLEASE DELIVER MOST")).isEmpty();
    }

    @Test
    void doesNotLetTheStopWordOfShadowAnUnrelatedPreposition() {
        // "Of" (Turkey, population 31,951) is exactly the case this population floor's own analysis was
        // built around: it comfortably clears the 10,000 population floor on its own, so only
        // the STOP_WORDS name-based exclusion prevents it from resolving here.
        assertThat(lookup.lookupInAddress("A SLICE OF CAKE")).isEmpty();
    }
}
