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

package io.passioncore.addresstokenizer.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.ParsedAddress;
import io.passioncore.addresstokenizer.model.TokenType;

/**
 * Fallback address tokenizer used when no country-specific parser is registered.
 * All confidence scores are low (0.35–0.60) to signal human review is recommended.
 */
@Component
public class GenericAddressParser implements AddressParser {

    private static final Pattern POSTAL_GENERIC =
        Pattern.compile("\\b(\\d{4,6}(?:-\\d{3,4})?)\\b");

    @Override public String postalCodePattern() { return POSTAL_GENERIC.pattern(); }
    @Override public String countryCode() { return "UNKNOWN"; }

    @Override
    public ParsedAddress parse(String raw, String country) {
        List<AddressToken> tokens = new ArrayList<>();
        String[] parts = raw.split("[,\\n]+");
        int len = parts.length;

        for (int i = 0; i < len; i++) {
            String part = parts[i].trim();
            if (part.isBlank()) continue;

            Matcher postalMatcher = POSTAL_GENERIC.matcher(part);
            if (postalMatcher.find()) {
                String postal = postalMatcher.group(1);
                tokens.add(new AddressToken(TokenType.POSTAL_CODE, postal));
            } else if (i == 0) {
                tokens.add(new AddressToken(TokenType.STREET_NAME, part));
            } else {
                tokens.add(new AddressToken(TokenType.CITY, part));
            }
        }

        return new ParsedAddress(raw, country, tokens);
    }
}
