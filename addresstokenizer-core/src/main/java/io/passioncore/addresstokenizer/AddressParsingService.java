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

package io.passioncore.addresstokenizer;

import java.util.List;

import io.passioncore.addresstokenizer.model.ParsedAddress;

/**
 * Public entry point for address parsing.
 *
 * <p>Implemented by {@link AddressTokenizer} (Core) and by
 * {@code AddressEnrichmentService} (Pro). Both return the same
 * {@link ParsedAddress} type — Pro populates {@link ParsedAddress#diagnostics()}
 * with gazetteer-backed enrichment; Core leaves it {@code null}. Callers can
 * upgrade from Core to Pro by swapping the injected bean, with no code change
 * at the call site.</p>
 */
public interface AddressParsingService {

    ParsedAddress parse(String address);

    ParsedAddress parseLines(List<String> lines);

    default List<ParsedAddress> parseBatch(List<String> addresses) {
        return addresses.parallelStream().map(this::parse).toList();
    }
}
