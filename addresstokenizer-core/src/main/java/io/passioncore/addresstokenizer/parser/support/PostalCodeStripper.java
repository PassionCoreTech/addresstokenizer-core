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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the last match of a postal-code {@link Pattern} in an address string and splits the
 * string around it — without ever silently discarding the part after the match.
 *
 * <p>Motivation: several country parsers each hand-rolled a "find the last
 * regex match, keep only what's before it" loop and threw away everything after the match.
 * That was the root cause of a real bug — a real bank-published UK address
 * has town/country content trailing the postcode, and the old inline code never looked at it.
 * A caller of this class gets that content back explicitly, in {@link StripResult#remainingAfter()},
 * and has to consciously decide what to do with it.
 */
public final class PostalCodeStripper {

    private PostalCodeStripper() {}

    /**
     * @param matchedValue   the postal-code {@code Pattern}'s group(1), from the last match found,
     *                       or {@code null} if the pattern never matched
     * @param remainingBefore the input text before the last match (trimmed, trailing comma/space
     *                       stripped), or the full input if there was no match
     * @param remainingAfter  the input text after the last match (trimmed, leading comma/space
     *                       stripped) — empty string if there was no match or nothing follows it
     */
    public record StripResult(String matchedValue, String remainingBefore, String remainingAfter) {}

    /** Finds the LAST match of {@code pattern} (which must have a capturing group 1) in
     *  {@code addr} and splits around it. Matches every existing parser's {@code while
     *  (matcher.find())}-keep-last loop behaviour when a postal code could appear more than once. */
    public static StripResult stripLastMatch(String addr, Pattern pattern) {
        Matcher matcher = pattern.matcher(addr);
        String matchedValue = null;
        int start = -1, end = -1;
        while (matcher.find()) {
            matchedValue = matcher.group(1);
            start = matcher.start();
            end = matcher.end();
        }
        if (matchedValue == null) {
            return new StripResult(null, addr, "");
        }
        String before = addr.substring(0, start).trim().replaceAll("[,\\s]+$", "");
        String after = addr.substring(end).trim().replaceAll("^[,\\s]+", "");
        return new StripResult(matchedValue, before, after);
    }
}
