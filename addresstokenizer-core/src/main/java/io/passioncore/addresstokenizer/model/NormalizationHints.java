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

package io.passioncore.addresstokenizer.model;

/**
 * Pre-normalization flags captured from the raw input <em>before</em>
 * {@link io.passioncore.addresstokenizer.utils.NormalizationUtil#toSwiftAscii}
 * strips accents and converts to SWIFT Basic Latin.
 *
 * <p>Once normalization runs, all accent information is destroyed.
 * Language detectors -- especially
 * {@link io.passioncore.addresstokenizer.parser.QuebecFrenchDetector}
 * -- need these signals to distinguish Quebec French from English Canadian
 * addresses when the postal code alone is ambiguous.</p>
 */
public record NormalizationHints(
        boolean hadCedilla,
        boolean hadAcuteAccent,
        boolean hadUmlaut,
        boolean hadAeLigature,
        boolean hadNonAsciiInput
) {

    public static NormalizationHints none() {
        return new NormalizationHints(false, false, false, false, false);
    }

    public static NormalizationHints scan(String raw) {
        if (raw == null || raw.isEmpty()) return none();

        // ç = c-cedilla (c,), Ç = C-cedilla (C,)
        boolean cedilla    = raw.indexOf('ç') >= 0 || raw.indexOf('Ç') >= 0;
        // French acute/grave/circumflex vowels (lower + upper)
        boolean acute      = containsAny(raw,
                "éèêë"   // e variants
              + "àâ"               // a variants
              + "ôîï"         // o, i variants
              + "ùû"               // u variants
              + "ÉÈÊË"   // E variants
              + "ÀÂÔ"         // A, O variants
              + "ÎÏÙÛ"); // I, U variants
        // German umlauts and sharp-s
        boolean umlaut     = containsAny(raw, "äöüßÄÖÜ");
        // æ = ae-ligature, Æ = AE-ligature
        boolean aeLigature = raw.indexOf('æ') >= 0 || raw.indexOf('Æ') >= 0;
        boolean nonAscii   = raw.chars().anyMatch(c -> c > 127);

        return new NormalizationHints(cedilla, acute, umlaut, aeLigature, nonAscii);
    }

    public static NormalizationHints scanAll(Iterable<String> raws) {
        boolean cedilla = false, acute = false, umlaut = false,
                aeLig = false, nonAscii = false;
        for (String s : raws) {
            NormalizationHints h = scan(s);
            cedilla  |= h.hadCedilla();
            acute    |= h.hadAcuteAccent();
            umlaut   |= h.hadUmlaut();
            aeLig    |= h.hadAeLigature();
            nonAscii |= h.hadNonAsciiInput();
        }
        return new NormalizationHints(cedilla, acute, umlaut, aeLig, nonAscii);
    }

    private static boolean containsAny(String s, String chars) {
        for (int i = 0; i < chars.length(); i++) {
            if (s.indexOf(chars.charAt(i)) >= 0) return true;
        }
        return false;
    }
}
