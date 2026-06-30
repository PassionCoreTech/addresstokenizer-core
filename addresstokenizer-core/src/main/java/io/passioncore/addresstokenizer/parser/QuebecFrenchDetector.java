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

import org.springframework.stereotype.Component;

import io.passioncore.addresstokenizer.model.NormalizationHints;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects whether MT103 address lines are Quebec French or English Canadian.
 *
 * <h3>Signal tiers</h3>
 * <pre>
 *  +100  QC postal prefix H / J / G
 *  + 80  Province marker QC or legacy PQ
 *  + 40  French street type token (RUE, BOUL, CHEMIN, RANG …)
 *  + 30  hadCedilla — ç/Ç in original input
 *  + 20  French direction suffix (OUEST, NORD, SUD)
 *  + 15  SAINT-/SAINTE-/STE- municipality prefix
 *  + 10  hadAcuteAccent — Romance accent in original
 *  - 10  hadUmlaut — Germanic signal
 *  -  5  hadAeLigature — Nordic signal
 * </pre>
 *
 * <h3>Thresholds → Language</h3>
 * <pre>
 *  >= 100  FR_CA           apply QuebecFrenchParser
 *  >=  40  FR_CA_PROBABLE  apply QuebecFrenchParser, −0.05 confidence penalty
 *  <   40  EN_CA           apply standard English-CA parsing
 * </pre>
 */
@Component
public class QuebecFrenchDetector {

    public enum Language { FR_CA, FR_CA_PROBABLE, EN_CA }

    private static final Pattern QC_POSTAL   = Pattern.compile("\\b[HJG]\\d[A-Z]\\s?\\d[A-Z]\\d\\b");
    private static final Pattern QC_PROVINCE = Pattern.compile("\\bQ\\.?C\\.?\\b|\\bP\\.?Q\\.?\\b");
    private static final Pattern SAINT_PFX   = Pattern.compile("\\bSAINTE?-|\\bSTE?-");

    private static final Set<String> FR_STREET_TYPES = Set.of(
            "RUE", "BOUL", "BOULEVARD", "CHEMIN", "ROUTE",
            "RANG", "MONTEE", "COTE", "RUELLE", "IMPASSE", "PLACE", "CROISSANT", "CARRE"
    );

    private static final Set<String> FR_DIRECTIONS = Set.of("OUEST", "NORD", "SUD");

    public DetectionResult detect(List<String> lines, NormalizationHints hints) {
        String combined = String.join(" ", lines).toUpperCase();
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (QC_POSTAL.matcher(combined).find()) {
            score += 100;
            reasons.add("QC postal prefix (H/J/G)");
        }
        if (QC_PROVINCE.matcher(combined).find()) {
            score += 80;
            reasons.add("Province marker QC/PQ");
        }
        for (String tok : combined.split("\\s+")) {
            if (FR_STREET_TYPES.contains(tok)) {
                score += 40;
                reasons.add("French street type: " + tok);
                break;
            }
        }
        if (hints.hadCedilla()) {
            score += 30;
            reasons.add("hadCedilla=true");
        }
        for (String dir : FR_DIRECTIONS) {
            if (combined.contains(" " + dir + " ") || combined.endsWith(" " + dir)) {
                score += 20;
                reasons.add("French direction: " + dir);
                break;
            }
        }
        if (SAINT_PFX.matcher(combined).find()) {
            score += 15;
            reasons.add("Saint-/Ste- municipality prefix");
        }
        if (hints.hadAcuteAccent()) { score += 10; reasons.add("hadAcuteAccent=true"); }
        if (hints.hadUmlaut())      { score -= 10; reasons.add("hadUmlaut=true"); }
        if (hints.hadAeLigature())  { score -= 5;  reasons.add("hadAeLigature=true"); }

        Language lang = score >= 100 ? Language.FR_CA
                      : score >= 40  ? Language.FR_CA_PROBABLE
                      : Language.EN_CA;

        return new DetectionResult(lang, score, List.copyOf(reasons));
    }

    public DetectionResult detect(List<String> lines) {
        return detect(lines, NormalizationHints.none());
    }

    public record DetectionResult(Language language, int score, List<String> reasons) {
        public double confidencePenalty() {
            return language == Language.FR_CA_PROBABLE ? 0.05 : 0.0;
        }
        public boolean needsReview() {
            return language == Language.FR_CA_PROBABLE;
        }
    }
}
