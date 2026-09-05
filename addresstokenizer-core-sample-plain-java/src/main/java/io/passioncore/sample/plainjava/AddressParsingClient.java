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
package io.passioncore.sample.plainjava;

import java.util.List;

import io.passioncore.addresstokenizer.AddressParsingService;
import io.passioncore.addresstokenizer.AddressTokenizer;
import io.passioncore.addresstokenizer.model.ParsedAddress;

/**
 * Reference wrapper for embedding Address Tokenizer Core into a project with no Spring
 * container — a CLI tool, a batch job, a servlet app, a Micronaut/Quarkus app, or anything
 * else. Copy this class into your own project as a starting point.
 *
 * <h3>Lifecycle</h3>
 * Construct <b>one</b> instance and reuse it for the life of your process (or request scope, if
 * your framework has one). {@link AddressTokenizer#createDefault()} rebuilds the whole parser
 * pipeline (parsers, country detector, city-country lookup) every time it's called — wasted
 * work if repeated per request. The resulting {@link AddressTokenizer} is stateless and
 * thread-safe once built, so one shared instance is safe under concurrent use.
 *
 * <h3>Upgrading to Pro</h3>
 * Swap {@link AddressTokenizer#createDefault()} for Pro's own
 * {@code ProAddressTokenizerFactory.createDefault()} to add gazetteer enrichment — no other
 * code in this class needs to change, since both return {@link AddressParsingService}. See
 * {@code addresstokenizer-pro-sample-plain-java} for the equivalent Pro-tier wrapper.
 */
public class AddressParsingClient {

    private final AddressParsingService service;

    public AddressParsingClient() {
        this(AddressTokenizer.createDefault());
    }

    /** Accepts any {@link AddressParsingService} — lets a test inject a stub, or lets a caller
     *  hand in a Pro-tier instance without changing the rest of this class. */
    public AddressParsingClient(AddressParsingService service) {
        this.service = service;
    }

    public ParsedAddress parse(String rawAddress) {
        return service.parse(rawAddress);
    }

    public ParsedAddress parseLines(List<String> lines) {
        return service.parseLines(lines);
    }

    public List<ParsedAddress> parseBatch(List<String> rawAddresses) {
        return service.parseBatch(rawAddresses);
    }
}
