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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.passioncore.addresstokenizer.parser.AddressParser;
import io.passioncore.addresstokenizer.parser.CaAddressParser;
import io.passioncore.addresstokenizer.parser.UsAddressParser;

/**
 * Regex-only country detector — the Core implementation of {@link CountryDetectorInterface}.
 *
 * <p>Detection runs a priority-ordered cascade: language/script signals, explicit
 * country names, vocabulary hints (street-type keywords), parser postal-code patterns,
 * and US full-state-name recognition. Returns {@code "UNKNOWN"} when none match.</p>
 *
 * <p>The Pro tier provides {@link CountryDetectorPro}, which extends this class and adds
 * a gazetteer database fallback for edge cases that regex cannot resolve.</p>
 */
@Component
public class CountryDetector implements CountryDetectorInterface {

    private static final Pattern IN_POSTAL = Pattern.compile("\\b\\d{6}\\b");

    // Used by detectDeclaredCountryCode: matches only when the trimmed last comma-segment
    // is exactly two letters -- nothing else (no digits, no extra words).
    private static final Pattern BARE_COUNTRY_CODE = Pattern.compile("^[A-Za-z]{2}$");

    // China (CN): a bare "CN" token, or a 6-digit postal code directly followed by "CN"
    // (Chinese mailing convention puts the postal code before the country marker, e.g.
    // "350503 CN FUJIAN SHENG..."). Must be checked before IN_POSTAL below -- China's own
    // 6-digit postal code would otherwise be misread as an Indian PIN code whenever no
    // literal "CHINA" word is present (COUNTRY_NAME_HINT above already covers that case).
    private static final Pattern CN_BARE_CODE = Pattern.compile("(?i)\\bCN\\b");
    private static final Pattern CN_POSTAL_CODE = Pattern.compile("\\b\\d{6}\\s*CN\\b");

    /**
     * Unambiguous US full state names used as a last-resort US signal when no postal
     * code matches. Multi-word names are listed first to prevent partial matches
     * (e.g. VIRGINIA must not shadow WEST VIRGINIA). GEORGIA is intentionally excluded:
     * it is also a country (ISO 3166-1 GE) and is too ambiguous to use unilaterally.
     */
    private static final Pattern US_FULL_STATE_NAME = Pattern.compile("(?i)\\b(" +
        "DISTRICT OF COLUMBIA|" +
        "NORTH CAROLINA|NORTH DAKOTA|SOUTH CAROLINA|SOUTH DAKOTA|" +
        "NEW HAMPSHIRE|NEW JERSEY|NEW MEXICO|NEW YORK|WEST VIRGINIA|RHODE ISLAND|" +
        "ALABAMA|ALASKA|ARIZONA|ARKANSAS|CALIFORNIA|COLORADO|CONNECTICUT|" +
        "DELAWARE|FLORIDA|HAWAII|IDAHO|ILLINOIS|INDIANA|IOWA|" +
        "KANSAS|KENTUCKY|LOUISIANA|MAINE|MARYLAND|MASSACHUSETTS|MICHIGAN|" +
        "MINNESOTA|MISSISSIPPI|MISSOURI|MONTANA|NEBRASKA|NEVADA|OHIO|" +
        "OKLAHOMA|OREGON|PENNSYLVANIA|TENNESSEE|TEXAS|UTAH|VERMONT|" +
        "VIRGINIA|WASHINGTON|WISCONSIN|WYOMING" +
        ")\\b");

    // Matched case-insensitively — input is SWIFT-uppercased before this check.
    // BD\.?(?=\s) catches the French "Bd" abbreviation for Boulevard (e.g. "Bd Saint-Germain").
    private static final Map<String, String> STREET_VOCAB_HINT = Map.of(
        "RUE|BOULEVARD|BD\\.?(?=\\s)|IMPASSE|ALLEE|ALL[EÉ]E|CEDEX|BOUL\\.?(?=\\s)", "FR",
        "STRASSE|STR\\.(?=\\s)|\\bWEG\\b|GASSE|PLATZ",                               "DE",
        "RUA|AVENIDA|ALAMEDA|TRAVESSA|CEP",                                            "BR",
        "BLK|BLOCK|#\\d{2}-\\d{2}",                                                   "SG",
        "丁目|番地|号|都|区|市",                                                          "JP",
        "室|樓|街|道|路|里|徑|磡仔|九龍|新界",                                             "HK"
    );

    // Explicit country names checked before postal-code patterns.
    // Ordered: longest/most-specific names first to prevent substring shadowing.
    // Package-private (not private): CityCountryLookup also reads this to exclude country
    // names from city-lookup candidacy -- see plan 040.03.
    static final Map<String, String> COUNTRY_NAME_HINT = Map.ofEntries(
        Map.entry("UNITED KINGDOM",    "GB"),
        Map.entry("GREAT BRITAIN",     "GB"),
        Map.entry("UNITED STATES",     "US"),
        Map.entry("SOUTH KOREA",       "KR"),
        Map.entry("NEW ZEALAND",       "NZ"),
        Map.entry("SAUDI ARABIA",      "SA"),
        Map.entry("SOUTH AFRICA",      "ZA"),
        Map.entry("NETHERLANDS",       "NL"),
        Map.entry("SWITZERLAND",       "CH"),
        Map.entry("AUSTRALIA",         "AU"),
        Map.entry("SINGAPORE",         "SG"),
        Map.entry("GERMANY",           "DE"),
        Map.entry("DEUTSCHLAND",       "DE"),
        Map.entry("FRANCE",            "FR"),
        Map.entry("GREECE",            "GR"),
        Map.entry("HELLAS",            "GR"),
        Map.entry("BRAZIL",            "BR"),
        Map.entry("BRASIL",            "BR"),
        Map.entry("CANADA",            "CA"),
        Map.entry("INDIA",             "IN"),
        Map.entry("CHINA",             "CN"),
        Map.entry("JAPAN",             "JP"),
        Map.entry("ITALY",             "IT"),
        Map.entry("ITALIA",            "IT"),
        Map.entry("SPAIN",             "ES"),
        Map.entry("ESPANA",            "ES"),
        Map.entry("PORTUGAL",          "PT"),
        Map.entry("SWEDEN",            "SE"),
        Map.entry("NORWAY",            "NO"),
        Map.entry("DENMARK",           "DK"),
        Map.entry("FINLAND",           "FI"),
        Map.entry("AUSTRIA",           "AT"),
        Map.entry("BELGIUM",           "BE"),
        Map.entry("POLAND",            "PL"),
        Map.entry("MEXICO",            "MX"),
        Map.entry("ARGENTINA",         "AR"),
        Map.entry("THAILAND",          "TH"),
        Map.entry("INDONESIA",         "ID"),
        Map.entry("MALAYSIA",          "MY"),
        Map.entry("PHILIPPINES",       "PH"),
        Map.entry("VIETNAM",           "VN"),
        Map.entry("TURKEY",            "TR"),
        Map.entry("ISRAEL",            "IL"),
        Map.entry("EGYPT",             "EG"),
        Map.entry("NIGERIA",           "NG"),
        Map.entry("KENYA",             "KE"),
        Map.entry("IRELAND",           "IE"),
        Map.entry("HONG KONG",         "HK")
    );

    private final List<CountryPattern> patterns;
    private final CityCountryLookup cityLookup;

    public CountryDetector(List<AddressParser> parsers) {
        this(parsers, null);
    }

    @Autowired
    public CountryDetector(List<AddressParser> parsers, CityCountryLookup cityLookup) {
        this.cityLookup = cityLookup;
        List<CountryPattern> built = parsers.stream()
            .filter(p -> p.detectionPriority() < Integer.MAX_VALUE)
            .sorted((a, b) -> Integer.compare(a.detectionPriority(), b.detectionPriority()))
            .map(p -> new CountryPattern(p.countryCode(), Pattern.compile(p.postalCodePattern())))
            .collect(Collectors.toCollection(ArrayList::new));
        built.add(new CountryPattern("IN", IN_POSTAL));
        this.patterns = Collections.unmodifiableList(built);
    }

    @Override
    public String detect(String address) {
        if (address == null || address.isBlank()) return "UNKNOWN";
        String upper = address.toUpperCase();

        // Explicit country names — longest match wins so "UNITED KINGDOM" beats "CHINA"
        // when both appear in the same address string. Map iteration order is not
        // guaranteed, so we must scan all entries and pick the longest key that matches.
        // Secondary tie-break when two names are the same length (e.g. "HONG KONG" and
        // "AUSTRALIA", both 9 chars): prefer whichever name's LAST occurrence is closer
        // to the end of the string. International mailing convention states the
        // destination country last; a same-length name earlier in the text is more
        // likely an organization/building name coincidence (e.g. "Hong Kong Tourism
        // Board", "Hong Kong House") than the actual country declaration.
        Map.Entry<String, String> bestName = COUNTRY_NAME_HINT.entrySet().stream()
            .filter(e -> upper.contains(e.getKey()))
            .max(Comparator
                .<Map.Entry<String, String>>comparingInt(e -> e.getKey().length())
                .thenComparingInt(e -> upper.lastIndexOf(e.getKey())))
            .orElse(null);
        if (bestName != null) return bestName.getValue();

        // SG postal patten 
        if (upper.matches(".*\\bS\\d{6}\\b.*"))
            return "SG";

        if (upper.contains("KOWLOON")
                || upper.contains("HKG") || upper.contains("NEW TERRITORIES")
                || upper.contains("HKSAR")
                || upper.contains("HK CN") || upper.contains("CN HK")
                || address.matches(".*[乂新香龍港磡].*"))
            return "HK";

        // China (CN): bare "CN" token or "<6-digit postal> CN" -- must run before IN_POSTAL's
        // generic 6-digit fallback below so a Chinese postal code (e.g. "350503") is not
        // misread as an Indian PIN code when no literal "CHINA" word is present.
        if (CN_POSTAL_CODE.matcher(upper).find() || CN_BARE_CODE.matcher(upper).find())
            return "CN";

        if (CaAddressParser.POSTAL_FULL.matcher(address).find()
                || (CaAddressParser.PROVINCE.matcher(address).find()
                    && address.matches(".*\\b[A-Z]\\d[A-Z]\\b.*"))) {
            if (UsAddressParser.STATE.matcher(address).find()
                    && UsAddressParser.ZIP.matcher(address).find())
                return "US";
            return "CA";
        }

        // Vocabulary hints checked before postal-code patterns — intercepts 5-digit ZIPs
        // shared by US, DE, and FR, preventing false US detection for European addresses.
        for (Map.Entry<String, String> e : STREET_VOCAB_HINT.entrySet()) {
            if (Pattern.compile(e.getKey()).matcher(upper).find()) {
                return e.getValue();
            }
        }



        for (CountryPattern cp : patterns) {
            if (cp.pattern().matcher(address).find()) {
                return cp.country();
            }
        }

        // Full US state name without any postal code — must come before city-name lookup
        // so that "COLUMBIA" in "District of Columbia" does not match Colombia.
        if (US_FULL_STATE_NAME.matcher(upper).find()) return "US";

        // City-name fallback: scan address for a known city from cities500 dataset
        return cityLookup != null ? cityLookup.lookupInAddress(address).orElse("UNKNOWN") : "UNKNOWN";
    }

    /**
     * Secondary, positional signal — checks whether a recognized country name from
     * {@link #COUNTRY_NAME_HINT} appears within the trailing {@code tailTokenWindow}
     * tokens of the address. Reuses the same longest-match-wins vocabulary as
     * {@link #detect}'s explicit-name check, restricted to the tail so it can be used
     * to corroborate/conflict-check the primary {@link #detect} result rather than
     * duplicate it. Bare 2-letter ISO codes are intentionally not matched here — too
     * easily confused with a trailing state/province abbreviation to be a safe signal.
     */
    @Override
    public Optional<String> detectInTail(String address, int tailTokenWindow) {
        if (address == null || address.isBlank() || tailTokenWindow <= 0) return Optional.empty();

        String[] rawTokens = address.trim().split("[\\s,]+");
        int from = Math.max(0, rawTokens.length - tailTokenWindow);
        String tail = String.join(" ", Arrays.copyOfRange(rawTokens, from, rawTokens.length)).toUpperCase();

        return COUNTRY_NAME_HINT.entrySet().stream()
            .filter(e -> tail.contains(e.getKey()))
            .max(Comparator
                .<Map.Entry<String, String>>comparingInt(e -> e.getKey().length())
                .thenComparingInt(e -> tail.lastIndexOf(e.getKey())))
            .map(Map.Entry::getValue);
    }

    /**
     * Positional signal for an explicit country declaration: matches only when the
     * address's last comma-delimited segment is, once trimmed, exactly a bare 2-letter
     * code and nothing else. Standard mailing-address format puts state and postal code
     * together in one segment ("NY 10118"), so that shape never qualifies -- only a code
     * standing alone in its own final segment does. Format-only check: does not validate
     * the code against a canonical ISO-3166 list.
     */
    @Override
    public Optional<String> detectDeclaredCountryCode(String address) {
        if (address == null || address.isBlank()) return Optional.empty();

        String[] segments = address.split(",", -1);
        if (segments.length < 2) return Optional.empty();

        String lastSegment = segments[segments.length - 1].trim();
        return BARE_COUNTRY_CODE.matcher(lastSegment).matches()
            ? Optional.of(lastSegment.toUpperCase())
            : Optional.empty();
    }

    private record CountryPattern(String country, Pattern pattern) {}
}
