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

package io.passioncore.addresstokenizer.parser.support;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Extracts CITY (and optionally NEIGHBORHOOD) from the last comma-segment(s) of an address —
 * the shape both {@code UsAddressParser} and {@code UkAddressParser} hand-rolled independently.
 * Pass segments that have already been through {@link SelfReferenceStripper#strip}.
 */
public final class CommaSegmentCityExtractor {

    private CommaSegmentCityExtractor() {}

    /** @param city         last segment, or {@code null} if fewer than 2 segments were given
     *  @param neighborhood second-to-last segment (when requested and present/non-blank),
     *                      otherwise {@code null}
     *  @param streetLine   every segment before whichever of city/neighborhood was consumed,
     *                      rejoined with {@code ", "} */
    public record Result(String city, String neighborhood, String streetLine) {}

    /** Extracts CITY from the last segment, NEIGHBORHOOD from the second-to-last when
     *  {@code withNeighborhood} is true and a third segment exists. With fewer than 2 segments,
     *  no city is extracted and the whole input becomes {@code streetLine}. */
    public static Result extractLastAsCity(List<String> segments, boolean withNeighborhood) {
        return extractLastAsCity(segments, withNeighborhood, false);
    }

    /** Same as {@link #extractLastAsCity(List, boolean)}, but when {@code segments} has exactly
     *  one entry <em>and</em> {@code singleSegmentIsCity} is {@code true}, that entry is
     *  extracted as CITY instead of falling through to {@code streetLine}. Pass {@code true} only
     *  when the caller already stripped a confirmed trailing self-reference (via
     *  {@link SelfReferenceStripper#strip}) down to this one segment -- a bare single-segment
     *  input that was <em>never</em> preceded by anything (e.g. a lone street name with no city
     *  or country at all) is genuinely ambiguous and must keep resolving to {@code streetLine},
     *  which is why this is opt-in rather than the default. */
    public static Result extractLastAsCity(
            List<String> segments, boolean withNeighborhood, boolean singleSegmentIsCity) {
        int len = segments.size();
        if (len == 1 && singleSegmentIsCity) {
            String city = segments.get(0).trim();
            if (!city.isEmpty()) {
                return new Result(city, null, "");
            }
        }
        if (len < 2) {
            return new Result(null, null, String.join(", ", segments).trim());
        }
        String city = segments.get(len - 1).trim();
        String neighborhood = null;
        int streetEnd = len - 1;
        if (withNeighborhood && len >= 3) {
            String candidate = segments.get(len - 2).trim();
            if (!candidate.isEmpty()) {
                neighborhood = candidate;
                streetEnd = len - 2;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < streetEnd; i++) {
            if (i > 0) sb.append(", ");
            sb.append(segments.get(i).trim());
        }
        return new Result(city, neighborhood, sb.toString());
    }

    /** True if {@code segment} looks like a floor/unit marker (e.g. {@code "6TH FLOOR"},
     *  {@code "SUITE 400"}) rather than a plausible city name — a defensive check a parser can
     *  run before trusting a naive last-comma-segment pick as CITY. Kept as reusable
     *  defense-in-depth; not the mechanism that recovers the actual city when it trails the
     *  postcode instead, outside this segment list entirely — see
     *  {@code UkAddressParser}'s use of {@link PostalCodeStripper.StripResult#remainingAfter()}). */
    public static boolean looksLikeFloorOrUnitMarker(String segment) {
        return NON_CITY_SHAPE.matcher(segment.trim()).find();
    }

    private static final Pattern NON_CITY_SHAPE = Pattern.compile(
        "(?i)^(?:\\d+(?:ST|ND|RD|TH)?\\s+)?"
            + "(?:FLOOR|FL|LEVEL|LVL|SUITE|STE|UNIT|APT|APARTMENT|ROOM|FLAT)\\b"
            + "(?:\\s+\\d+[A-Za-z]?)?$");
}
