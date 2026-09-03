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

import java.util.regex.Pattern;

/**
 * Falls back to newline-based segmentation when a country parser's usual comma-delimited split
 * finds no comma at all. Real bank/SWIFT-derived address blocks are often one field per physical
 * line (tax-ID line, street line, postal+city line, phone line) with no comma anywhere in the
 * whole block -- {@code AddressTokenizer.parseLines()} only flattens embedded newlines to spaces
 * for its own country-*detection* step, never for the string it hands to the country-specific
 * *parser* (docs/plans/049), so every parser that segments its input on comma alone is equally
 * exposed to this shape.
 */
public final class NewlineFallbackSplitter {

    private NewlineFallbackSplitter() {}

    /** Drop-in replacement for {@code addr.split(",", limit)}: falls back to splitting on
     *  {@code \n} (with the same {@code limit} semantics) only when {@code addr} has no comma
     *  at all but does contain an embedded newline. Comma-bearing input -- every address shape
     *  this already worked for -- is split exactly as before. */
    public static String[] split(String addr, int limit) {
        if (addr != null && addr.indexOf(',') < 0 && addr.indexOf('\n') >= 0) {
            return addr.split("\n", limit);
        }
        return addr.split(",", limit);
    }

    /** Street/city line pair recovered from a comma-less, multi-line block. */
    public record Segments(String streetLine, String cityLine) {}

    /** For parsers whose street segment must be a single physical line (e.g. FR/DE's
     *  "street type before/after name" logic, which can't safely span a joined multi-line
     *  blob): scans each non-blank line of {@code addr} for one matching
     *  {@code cityLinePattern} (the parser's own postal-code/city pattern -- the last matching
     *  line wins) and one matching {@code streetLinePattern} (the parser's own street-type
     *  pattern -- the first matching line wins), and returns just those two lines. Any other
     *  line (a tax/VAT ID, a phone number) is treated as noise and dropped rather than folded
     *  into either field.
     *
     *  <p>Returns {@code null} when {@code addr} already has a comma, has no embedded newline,
     *  or the two patterns don't resolve to two distinct lines -- callers should fall through to
     *  their normal comma-split logic (typically via {@link #split}) in every such case. */
    public static Segments trySegmentByLine(
            String addr, Pattern cityLinePattern, Pattern streetLinePattern) {
        if (addr == null || addr.indexOf(',') >= 0 || addr.indexOf('\n') < 0) {
            return null;
        }
        String[] rawLines = addr.split("\n");
        String[] lines = new String[rawLines.length];
        int n = 0;
        int cityIdx = -1;
        int streetIdx = -1;
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            lines[n] = line;
            if (cityLinePattern.matcher(line).find()) {
                cityIdx = n;
            }
            if (streetIdx < 0 && streetLinePattern.matcher(line).find()) {
                streetIdx = n;
            }
            n++;
        }
        if (cityIdx < 0 || streetIdx < 0 || cityIdx == streetIdx) {
            return null;
        }
        return new Segments(lines[streetIdx], lines[cityIdx]);
    }
}
