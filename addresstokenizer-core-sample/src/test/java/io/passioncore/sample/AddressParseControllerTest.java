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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AddressParseControllerTest {

    @Autowired
    WebApplicationContext wac;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // ── /parse endpoint ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /parse")
    class ParseEndpoint {

        @ParameterizedTest(name = "{0} → country={1}")
        @CsvSource({
            "'350 Fifth Avenue, New York, NY 10118',                            US",
            "'10 Downing Street, London SW1A 2AA',                              GB",
            "'Unter den Linden 6, 10117 Berlin',                                DE",
            "'75 Rue de Rivoli, 75001 Paris',                                   FR",
            "'Level 3/80 Pacific Highway, North Sydney NSW 2060',               AU",
            "'120 Adelaide Street West, Suite 2500, Toronto, ON M5H 1T1',       CA",
        })
        @DisplayName("detects country correctly")
        void detectsCountry(String address, String expectedCountry) throws Exception {
            mockMvc.perform(get("/parse").param("address", address))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.country").value(expectedCountry));
        }

        @Test
        @DisplayName("response includes required fields")
        void responseShape() throws Exception {
            mockMvc.perform(get("/parse").param("address", "10 Downing Street, London SW1A 2AA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.raw").isNotEmpty())
                    .andExpect(jsonPath("$.country").isNotEmpty())
                    .andExpect(jsonPath("$.tokens").isArray());
        }

        @Test
        @DisplayName("token objects contain type, value ")
        void tokenShape() throws Exception {
            mockMvc.perform(get("/parse").param("address", "350 Fifth Avenue, New York, NY 10118"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokens[0].type").isNotEmpty())
                    .andExpect(jsonPath("$.tokens[0].value").isNotEmpty());
        }

        @Test
        @DisplayName("blank address returns UNKNOWN country")
        void blankAddress() throws Exception {
            mockMvc.perform(get("/parse").param("address", "   "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.country").value("UNKNOWN"));
        }

        @Test
        @DisplayName("Quebec French address is parsed as CA")
        void quebecFrenchAddress() throws Exception {
            mockMvc.perform(get("/parse")
                            .param("address", "1000 rue de la Gauchetiere Ouest, Montreal, QC H3B 4W5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.country").value("CA"));
        }

        @Test
        @DisplayName("PO Box address produces a PO_BOX token")
        void poBoxToken() throws Exception {
            mockMvc.perform(get("/parse").param("address", "PO Box 9000, Victoria, BC V8W 9V6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokens[?(@.type == 'PO_BOX')]").isNotEmpty());
        }
    }

    // ── /demo endpoint ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /demo")
    class DemoEndpoint {

        @Test
        @DisplayName("returns an array")
        void returnsList() throws Exception {
            mockMvc.perform(get("/demo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("every result has a non-empty country")
        void everyResultHasCountry() throws Exception {
            mockMvc.perform(get("/demo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].country").isNotEmpty());
        }

        @Test
        @DisplayName("every result has at least one token")
        void everyResultHasTokens() throws Exception {
            mockMvc.perform(get("/demo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].tokens[0]").isNotEmpty());
        }
    }
}
