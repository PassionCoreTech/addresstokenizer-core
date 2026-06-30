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
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * In-memory city-name → ISO 3166-1 alpha-2 country lookup.
 *
 * <p>Loaded from {@code city_countries.tsv} (bundled in the core JAR), which is derived
 * from GeoNames {@code cities500.txt} — one row per ASCII city name, keeping the country
 * with the highest population for that name. 171 k entries, ~2.4 MB on disk.</p>
 *
 * <p>Used by {@link CountryDetector} as the final fallback after all regex signals fail.</p>
 */
@Slf4j
@Component
public class CityCountryLookup {

    // Postal-code-like tokens to strip before city-name matching
    private static final Pattern NOISE = Pattern.compile(
        "\\b(\\d{4,6}(-\\d{3,4})?|[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2})\\b");

    @Value("${address.gazetteer.city-countries:classpath:data/city_countries.tsv}")
    private Resource resource;

    // lowercase ascii_name → country_code
    private Map<String, String> index;

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
        String lower = seg.toLowerCase();

        // full segment
        String cc = index.get(lower);
        if (cc != null) return cc;

        String[] words = seg.trim().split("\\s+");
        // sliding window: try longest phrases first
        for (int len = Math.min(3, words.length); len >= 1; len--) {
            for (int start = words.length - len; start >= 0; start--) {
                String phrase = joinLower(words, start, start + len);
                cc = index.get(phrase);
                if (cc != null) return cc;
            }
        }
        return null;
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
