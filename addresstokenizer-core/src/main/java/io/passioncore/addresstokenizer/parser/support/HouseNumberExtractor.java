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
 * Extracts a house/building number and splices it out of the remaining text. House-number
 * *position* is genuinely country-specific grammar (US: leading, before the street name; DE:
 * trailing, after the street name) — this class doesn't unify that, only the shared
 * find-and-splice mechanics each parser previously rewrote by hand.
 */
public final class HouseNumberExtractor {

    private HouseNumberExtractor() {}

    public record Result(String houseNo, String remaining) {}

    /** For a house number that comes BEFORE the rest of the text (e.g. US's {@code ^(\d+[A-Z]?)\s+}).
     *  {@code leadingPattern} must have group(1) as the value; matched text is removed from the
     *  front. */
    public static Result extractLeading(String text, Pattern leadingPattern) {
        Matcher m = leadingPattern.matcher(text);
        if (m.find()) {
            return new Result(m.group(1), text.substring(m.end()).trim());
        }
        return new Result(null, text);
    }

    /** For a house number that comes AFTER the rest of the text (e.g. DE's
     *  {@code \s+(\d+\s*[a-zA-Z]?)$}). {@code trailingPattern} must have group(1) as the value;
     *  matched text is removed from the end. */
    public static Result extractTrailing(String text, Pattern trailingPattern) {
        Matcher m = trailingPattern.matcher(text);
        if (m.find()) {
            return new Result(m.group(1).trim(), text.substring(0, m.start()).trim());
        }
        return new Result(null, text);
    }
}
