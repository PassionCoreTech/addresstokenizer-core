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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * In-memory city-name → ISO 3166-1 alpha-2 country lookup.
 *
 * <p>Loaded from {@code city_countries.tsv} (bundled in the core JAR), which is derived
 * from GeoNames {@code cities500.txt} — one row per ASCII city name, keeping the country
 * with the highest population for that name, and dropping any name whose winning
 * population falls below a 10,000 floor (docs/plans/040.06 — regenerate via
 * {@code scripts/regenerate-city-countries-data.sh}). ~41 k entries, ~540 KB on disk.</p>
 *
 * <p>Used by {@link CountryDetector} as the final fallback after all regex signals fail.</p>
 *
 * <p>Spring wires this as a {@code @Component} — the no-arg constructor plus {@code @Value}
 * field injection plus {@code @PostConstruct} {@link #load()}. Outside a Spring container, use
 * {@link #createDefault()} or {@link #CityCountryLookup(Resource)} instead, which load
 * immediately since {@code @Value} injection never happens without a container.</p>
 */
@Slf4j
@Component
public class CityCountryLookup {

    // Postal-code-like tokens to strip before city-name matching
    private static final Pattern NOISE = Pattern.compile(
        "\\b(\\d{4,6}(-\\d{3,4})?|[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2})\\b");

    private static final String DEFAULT_RESOURCE_PATH = "data/city_countries.tsv";

    /**
     * Candidate words/phrases excluded from city-lookup matching (plan 040.03). Verified via
     * two real, wrong-country lookups: {@code lookupInAddress("...MONTREAL CANADA")} returned
     * {@code MX} because {@code "Canada"} is also a real, obscure town in Mexico and is tried
     * (and matched) before {@code "Montreal"}; {@code lookupInAddress("...UNITED STATES OF
     * AMERICA")} returned {@code NL} because {@code "America"} is a real village in Limburg,
     * Netherlands. Both are common trailing words in addresses that state a country name, and
     * this lookup is only reached as {@link CountryDetector}'s last-resort fallback (i.e. every
     * higher-priority signal — including {@link CountryDetector#COUNTRY_NAME_HINT} itself —
     * already failed to recognize the country), so treating one of these words as city evidence
     * is far more likely to be an unrelated coincidence than a real signal.
     *
     * <p>Includes every {@link CountryDetector#COUNTRY_NAME_HINT} key plus {@code "america"},
     * which isn't itself a hint key (only the two-word {@code "UNITED STATES"} is), plus a small
     * set of common English street-type words — regression-testing this fix found a *third*,
     * more consequential collision: {@code "Street"} alone is a real place name (Somerset,
     * England) in {@code city_countries.tsv}, and unlike a country name, a street-type word can
     * appear as an isolated trailing segment word in a huge fraction of ordinary addresses
     * (anything ending in "...GOLDENPARIS STREET", split away from its house number). This list
     * is necessarily incomplete — it covers verified failures, not every possible coincidental
     * collision. The complementary, more general defense is {@code city_countries.tsv}'s own
     * 10,000-population floor (docs/plans/040.06) — a former length-based guard here was removed
     * once that floor shipped, since it was found to reject some genuinely significant places for
     * the wrong reason (e.g. {@code "Aba"}, Nigeria's third-largest city at 1.16M people, purely
     * because it's 3 characters).</p>
     */
    private static final Set<String> STREET_TYPE_WORDS = Set.of(
        "street", "avenue", "road", "drive", "boulevard", "lane", "way", "court",
        "place", "crescent", "terrace", "square", "close", "highway", "parkway", "alley");

    /**
     * Common English function words (articles, conjunctions, prepositions, auxiliary verbs)
     * excluded from city-lookup matching (plan 040.03 fold-in). A systematic scan of
     * {@code city_countries.tsv} against a standard English stop-word list found 16 real
     * collisions — e.g. {@code "of"} → TR (population 31,951, so it survives 040.06's population
     * floor and needs this name-based exclusion specifically), {@code "over"} → GB,
     * {@code "most"} → CZ, {@code "same"} → TZ, {@code "than"} → IN. Listed in full (not just the
     * ones that currently survive the population floor) so this protection doesn't silently
     * depend on where that floor happens to be set.
     */
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "and", "or", "but", "nor", "so", "yet",
        "of", "in", "on", "at", "to", "for", "from", "with", "into", "near",
        "over", "under", "through", "during", "before", "after", "above", "below", "between", "among",
        "it", "he", "she", "we", "they", "this", "that", "these", "those",
        "is", "are", "was", "were", "be", "been", "being", "has", "have", "had",
        "do", "does", "did", "will", "would", "can", "could", "should", "must", "may", "might",
        "not", "no", "then", "than", "both", "each", "few", "more", "most", "other",
        "some", "such", "only", "own", "same", "too", "very", "just",
        "us", "van", "by", "as", "if", "up", "out", "off", "all", "any", "new", "old");

    private static final Set<String> EXCLUDED_CANDIDATES = buildExcludedCandidates();

    private static Set<String> buildExcludedCandidates() {
        Set<String> excluded = new HashSet<>();
        for (String name : CountryDetector.COUNTRY_NAME_HINT.keySet()) {
            excluded.add(name.toLowerCase());
        }
        excluded.add("america");
        excluded.addAll(STREET_TYPE_WORDS);
        excluded.addAll(STOP_WORDS);
        return excluded;
    }

    @Value("${address.gazetteer.city-countries:classpath:data/city_countries.tsv}")
    private Resource resource;

    // lowercase ascii_name → country_code
    private Map<String, String> index;

    /** Spring path: {@code resource} is populated by {@code @Value} field injection after
     *  this constructor runs, then {@link #load()} fires via {@code @PostConstruct}. */
    public CityCountryLookup() {
    }

    /** Non-Spring path: loads {@code resource} immediately — no container, no
     *  {@code @PostConstruct}, needed since {@code @Value} field injection never happens
     *  outside a Spring {@code ApplicationContext}. */
    public CityCountryLookup(Resource resource) {
        this.resource = resource;
        load();
    }

    /** Convenience for the non-Spring path: loads from the same bundled classpath TSV the
     *  Spring-wired {@code @Value} default already points to. */
    public static CityCountryLookup createDefault() {
        return new CityCountryLookup(new ClassPathResource(DEFAULT_RESOURCE_PATH));
    }

    @PostConstruct
    void load() {
        Map<String, String> map = new HashMap<>(200_000);
        if (!resource.exists()) {
            log.warn("city_countries.tsv not found — city-based country fallback disabled");
            index = map;
            return;
        }
        long t0 = System.currentTimeMillis();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 1) continue;
                map.put(line.substring(0, tab).toLowerCase(), line.substring(tab + 1).trim());
            }
        } catch (Exception ex) {
            log.error("Failed to load city_countries.tsv — city fallback disabled", ex);
        }
        index = map;
        log.debug("City-country index loaded in {}ms — {} entries",
            System.currentTimeMillis() - t0, index.size());
    }

    /**
     * Scans {@code address} for a recognisable city name and returns its country code.
     *
     * <p>Strategy: split on commas/newlines, examine each segment from the back (city and
     * country tend to appear at the end), try progressively shorter phrase windows (3→2→1
     * words) within each segment. Returns the first unambiguous match.</p>
     */
    public Optional<String> lookupInAddress(String address) {
        if (index == null || index.isEmpty() || address == null) return Optional.empty();

        String[] segments = address.split("[,\n\r;]+");
        // scan from last segment (most likely to contain city/country) toward first
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = NOISE.matcher(segments[i].trim()).replaceAll(" ").trim();
            if (seg.isBlank()) continue;

            String cc = matchSegment(seg);
            if (cc != null) return Optional.of(cc);
        }
        return Optional.empty();
    }

    /** Try full segment, then 3-word, 2-word, 1-word trailing windows. */
    private String matchSegment(String seg) {
        String[] words = seg.trim().split("\\s+");

        // full segment
        String cc = lookupCandidate(seg.toLowerCase());
        if (cc != null) return cc;

        // sliding window: try longest phrases first
        for (int len = Math.min(3, words.length); len >= 1; len--) {
            for (int start = words.length - len; start >= 0; start--) {
                String phrase = joinLower(words, start, start + len);
                cc = lookupCandidate(phrase);
                if (cc != null) return cc;
            }
        }
        return null;
    }

    /** Looks up {@code phrase} in the city index unless it's an excluded/unreliable candidate —
     *  see {@link #EXCLUDED_CANDIDATES}. Every remaining entry already cleared
     *  {@code city_countries.tsv}'s own population floor (docs/plans/040.06), so no separate
     *  short-word guard is needed here any more. */
    private String lookupCandidate(String phrase) {
        String lower = phrase.toLowerCase();
        if (EXCLUDED_CANDIDATES.contains(lower)) return null;
        return index.get(lower);
    }

    private String joinLower(String[] words, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) sb.append(' ');
            sb.append(words[i].toLowerCase());
        }
        return sb.toString();
    }
}
