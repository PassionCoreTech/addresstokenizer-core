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
 * Strips a leading recipient-name segment (e.g. {@code "JOHN SMITH, 126 CUBA AVE..."} or
 * {@code "MR. JOHN SMITH, ..."}) before it can leak into {@code STREET_NAME} or block a
 * {@code ^}-anchored {@code HOUSE_NO} pattern from matching (docs/plans/045.02).
 *
 * <p>Two tiers, deliberately not a single comma/whitespace rule (a comma-only rule breaks when
 * there's no comma, or when a name itself contains one; a whitespace-only rule can't work at all
 * since names are multi-word):
 * <ol>
 *   <li><b>Salutation-led</b> — {@code Mr|Mrs|Ms|Miss|Dr|Mdm|Prof} is a strong enough signal on
 *       its own to strip unconditionally, matching {@code SgAddressParser}/{@code HkAddressParser}
 *       (plans 041/042).</li>
 *   <li><b>Bare name</b> (no salutation) — only stripped when corroborated by the very next
 *       character after the comma being a digit, i.e. a house number leads immediately
 *       (covers US/UK/AU/CA's house-number-before-street convention, and FR's, where the number
 *       also leads within the following segment). Deliberately narrow: checking for a digit
 *       <em>anywhere</em> in the next segment was tried and reverted — it wrongly stripped real,
 *       common addresses with no house number at all (e.g. {@code "Times Square, New York NY
 *       10036"}, {@code "House of Commons, London SW1A 0AA"}), where the only digit is a
 *       postcode/zip further along, not a house number right after the comma.</li>
 * </ol>
 *
 * <p>Countries where the house number doesn't lead the very next segment even when real (DE's
 * {@code "Hauptstrasse 42"} — number trails the street name in the same segment) don't get the
 * bare-name tier's benefit; only {@link #stripSalutationLed} coverage applies to them. That's a
 * deliberate, documented gap, not an oversight — there's no safe positional signal to lean on
 * there. Countries whose own convention places a real, un-prefixed house number as its own bare
 * comma-segment (BR: {@code "Rua das Flores, 123, ..."}) must use {@link #stripSalutationLed}
 * only — for those, the bare-name tier is structurally indistinguishable from real content and
 * was found to cause exactly that false-positive during testing.
 */
public final class LeadingNameNoiseStripper {

    private LeadingNameNoiseStripper() {}

    private static final Pattern SALUTATION_LEAD =
        Pattern.compile("(?i)^(?:Mr|Mrs|Ms|Miss|Dr|Mdm|Prof)\\.?\\s+");

    // Up to 4 capitalized-looking words, no digits, no comma -- a plausible bare name.
    private static final Pattern BARE_NAME_SHAPE =
        Pattern.compile("^[A-Za-z][A-Za-z.'-]*(?:\\s+[A-Za-z][A-Za-z.'-]*){0,3}$");

    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern LEADING_DIGIT = Pattern.compile("^\\s*\\d");

    /** Two-tier stripping: salutation-led (unconditional) or bare-name-led (only when a digit
     *  immediately follows the comma). Not safe for parsers whose own convention places a real,
     *  bare house-number segment right after the first comma (see class docs) — use
     *  {@link #stripSalutationLed} there instead. */
    public static String strip(String addr) {
        String afterSalutation = stripSalutationLed(addr);
        if (!afterSalutation.equals(addr)) {
            return afterSalutation;
        }
        int comma = addr.indexOf(',');
        if (comma < 0) {
            return addr;
        }
        String before = addr.substring(0, comma).trim();
        String after = addr.substring(comma + 1);
        if (before.isEmpty() || DIGIT.matcher(before).find()) {
            return addr;
        }
        if (BARE_NAME_SHAPE.matcher(before).matches() && LEADING_DIGIT.matcher(after).find()) {
            return after.trim();
        }
        return addr;
    }

    /** Salutation-led tier only (no bare-name tier) — safe for every parser regardless of its own
     *  house-number placement convention, since it never relies on positional corroboration. */
    public static String stripSalutationLed(String addr) {
        if (addr == null) {
            return null;
        }
        int comma = addr.indexOf(',');
        if (comma < 0) {
            return addr;
        }
        String before = addr.substring(0, comma).trim();
        String after = addr.substring(comma + 1);
        if (before.isEmpty() || DIGIT.matcher(before).find()) {
            return addr;
        }
        if (SALUTATION_LEAD.matcher(before).find()) {
            return after.trim();
        }
        return addr;
    }
}
