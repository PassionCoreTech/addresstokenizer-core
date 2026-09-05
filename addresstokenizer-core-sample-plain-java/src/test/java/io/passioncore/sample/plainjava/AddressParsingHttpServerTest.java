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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end proof that {@link AddressParsingHttpServer} composes into a real HTTP request,
 *  using only the JDK's {@code java.net.http.HttpClient} — no test framework's mock server. */
class AddressParsingHttpServerTest {

    private AddressParsingHttpServer server;
    private HttpClient http;

    @BeforeEach
    void setUp() throws IOException {
        server = new AddressParsingHttpServer(new AddressParsingClient(), 0); // 0 = ephemeral port
        server.start();
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void parseEndpoint_returnsJson() throws Exception {
        HttpResponse<String> resp = get("/parse?address="
            + URLEncoder.encode("350 Fifth Avenue, New York, NY 10118", StandardCharsets.UTF_8));

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("\"countryCode\":\"US\"");
        assertThat(resp.body()).contains("\"city\":\"NEW YORK\"");
    }

    @Test
    void parseEndpoint_missingAddressParam_returns400() throws Exception {
        HttpResponse<String> resp = get("/parse");

        assertThat(resp.statusCode()).isEqualTo(400);
        assertThat(resp.body()).contains("error");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
            .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
