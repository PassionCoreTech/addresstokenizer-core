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

package io.passioncore.sample;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.passioncore.addresstokenizer.AddressTokenizer;
import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.ParsedAddress;

import lombok.RequiredArgsConstructor;

@Tag(name = "Address Parsing", description = "Parse free-text postal addresses into structured tokens")
@RestController
@RequiredArgsConstructor
public class AddressParseController {

    private final AddressTokenizer tokenizer;

    /** Sample addresses for every country supported by the free tier. */
    private static final List<String> DEMO_ADDRESSES = List.of(
        // United States
        "350 Fifth Avenue, Suite 6402, New York, NY 10118",
        // United Kingdom
        "10 Downing Street, London SW1A 2AA",
        // Germany
        "Unter den Linden 6, 10117 Berlin",
        // France
        "75 Rue de Rivoli, 75001 Paris",
        // Australia
        "Level 3/80 Pacific Highway, North Sydney NSW 2060",
        // Canada â€” English
        "120 Adelaide Street West, Suite 2500, Toronto, ON M5H 1T1",
        // Canada â€” QuÃ©bec French
        "1000 rue de la GauchetiÃ¨re Ouest, Bureau 2500, MontrÃ©al, QC H3B 4W5",
        // Canada â€” English with PO Box
        "PO Box 9000, Victoria, BC V8W 9V6"
    );

    // â”€â”€ Endpoints â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Operation(
        summary = "Parse a single address",
        description = "Tokenises a free-text postal address and returns the detected tokens with confidence scores.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Parsed successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or blank address parameter")
        }
    )
    @GetMapping("/parse")
    public Map<String, Object> parse(
            @Parameter(description = "Free-text postal address to parse", example = "10 Downing Street, London SW1A 2AA")
            @RequestParam String address) {
        ParsedAddress result = tokenizer.parse(address);
        return toResponse(result);
    }

    @Operation(
        summary = "Run built-in demo addresses",
        description = "Parses a pre-loaded set of sample addresses covering US, UK, DE, FR, AU, and CA.",
        responses = @ApiResponse(responseCode = "200", description = "Demo results")
    )
    @GetMapping("/demo")
    public List<Map<String, Object>> demo() {
        return DEMO_ADDRESSES.stream()
                .map(tokenizer::parse)
                .map(this::toResponse)
                .toList();
    }

    // â”€â”€ Response builder â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Map<String, Object> toResponse(ParsedAddress parsed) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("raw",        parsed.raw());
        out.put("country",    parsed.countryCode());
        List<Map<String, Object>> tokens = parsed.tokens().stream()
                .map(t -> tokenView(t))
                .toList();
        out.put("tokens", tokens);
        return out;
    }

    private Map<String, Object> tokenView(AddressToken t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type",  t.type().name());
        m.put("value", t.value());
        return m;
    }
}

