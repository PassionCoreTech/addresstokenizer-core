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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Strips trailing comma-segments that are just a redundant self-reference to the parser's own
 * country (e.g. a UK address ending in {@code ", UK"} with no real city after it).
 * {@code UsAddressParser} and
 * {@code UkAddressParser} each hand-rolled an identical while-loop for this; this replaces both.
 */
public final class SelfReferenceStripper {

    private SelfReferenceStripper() {}

    /** Returns a new list with trailing segments removed while at least 2 segments remain and the
     *  last one (trimmed, uppercased) is in {@code selfReferenceValues}. Never strips down to a
     *  single segment — a self-reference is only redundant when something more specific precedes
     *  it. */
    public static List<String> strip(List<String> segments, Set<String> selfReferenceValues) {
        List<String> result = new ArrayList<>(segments);
        while (result.size() >= 2 && selfReferenceValues.contains(result.get(result.size() - 1).trim().toUpperCase())) {
            result.remove(result.size() - 1);
        }
        return result;
    }
}
