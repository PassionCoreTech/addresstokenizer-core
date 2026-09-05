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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.passioncore.addresstokenizer.model.GeneralView;
import io.passioncore.addresstokenizer.model.ParsedAddress;

/**
 * Minimal, non-Spring HTTP layer over {@link AddressParsingClient} — proof that the wrapper
 * composes into a plain web stack with zero extra dependencies beyond the JDK's own
 * {@link HttpServer} ({@code jdk.httpserver}, not a third-party library). Deliberately not
 * production-grade (no TLS, no request-size limits, no routing framework) — kept small enough
 * to read end-to-end. For a production-shaped Spring Boot REST alternative, see
 * {@code addresstokenizer-core-sample}'s {@code AddressParseController}.
 */
public class AddressParsingHttpServer {

    private final AddressParsingClient client;
    private final HttpServer server;
    private final ExecutorService executor;

    public AddressParsingHttpServer(AddressParsingClient client, int port) throws IOException {
        this.client = client;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newFixedThreadPool(8);
        server.createContext("/parse", this::handleParse);
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdown();
    }

    /** Actual bound port — differs from the constructor argument when constructed with port
     *  {@code 0} (an ephemeral port), e.g. in tests. */
    public int port() {
        return server.getAddress().getPort();
    }

    private void handleParse(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"only GET is supported\"}");
                return;
            }
            String address = queryParam(exchange.getRequestURI().getRawQuery(), "address");
            if (address == null || address.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"missing required 'address' query parameter\"}");
                return;
            }
            ParsedAddress parsed = client.parse(address);
            sendJson(exchange, 200, toJson(parsed.general()));
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":" + jsonString(e.getMessage()) + "}");
        }
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** Hand-rolled, not Jackson — this module deliberately carries no JSON library dependency
     *  (addresstokenizer-core only pulls in jackson-annotations transitively, not
     *  jackson-databind/ObjectMapper). {@link GeneralView} is the same plain-name view both
     *  Core and Pro populate, so this method needs no tier-specific branching — Pro's version
     *  of this class serializes it identically, the only difference being that
     *  {@code fieldConfidences} is non-null. */
    private static String toJson(GeneralView g) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"countryCode\":").append(jsonString(g.countryCode())).append(',');
        sb.append("\"countryName\":").append(jsonString(g.countryName())).append(',');
        sb.append("\"streetName\":").append(jsonString(g.streetName())).append(',');
        sb.append("\"buildingName\":").append(jsonString(g.buildingName())).append(',');
        sb.append("\"city\":").append(jsonString(g.city())).append(',');
        sb.append("\"state\":").append(jsonString(g.state())).append(',');
        sb.append("\"postalCode\":").append(jsonString(g.postalCode())).append(',');
        sb.append("\"parseConfidence\":").append(g.parseConfidence());
        if (g.fieldConfidences() != null) {
            // Pro only -- Core's fieldConfidences is always null. Full per-field detail is
            // available via ParsedAddress.diagnostics(); left out of this minimal example.
            sb.append(",\"fieldConfidenceCount\":").append(g.fieldConfidences().size());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
