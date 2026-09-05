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

import java.io.IOException;

/**
 * Launches {@link AddressParsingHttpServer} — an entry point for the optional web-layer
 * add-on, not the reusable artifact itself. If you're copying something into your own
 * project, that's {@link AddressParsingClient}; this class just gives the web layer something
 * runnable to demonstrate it end-to-end.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        AddressParsingHttpServer server = new AddressParsingHttpServer(new AddressParsingClient(), port);
        server.start();
        System.out.println("Address Tokenizer Core (plain Java, no Spring container) listening on "
            + "http://localhost:" + server.port() + "/parse?address=...");
    }
}
