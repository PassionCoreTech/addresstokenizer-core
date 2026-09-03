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

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.passioncore.addresstokenizer.AddressTokenizer;
import io.passioncore.addresstokenizer.model.AddressToken;
import io.passioncore.addresstokenizer.model.ParsedAddress;

@Tag(name = "Address Parsing", description = "Parse free-text postal addresses into structured tokens")
@RestController
public class AddressParseController {

    private final AddressTokenizer tokenizer;
    private final double confidenceThreshold;

    public AddressParseController(
            AddressTokenizer tokenizer,
            @Value("${address.tokenizer.confidence-threshold:0.75}") double confidenceThreshold) {
        this.tokenizer = tokenizer;
        this.confidenceThreshold = confidenceThreshold;
    }

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

    /** A small curated subset of SWIFT/bank-published ISO 20022 worked examples
     *  (docs/plans/040.04) — real addresses banks use in their own migration guidance,
     *  not synthetic test data. */
    private static final List<String> SWIFT_EXAMPLE_ADDRESSES = List.of(
        // Brussels â€” HSBC ISO 20022 guide worked example (BQA-002)
        "HOOGSTRAAT 6, 18TH FLOOR, 1000 BRUSSELS, BE",
        // London â€” ANZ ISO 20022 guide worked example (BQA-003)
        "55 MARK LANE, THE CORN EXCHANGE, 6TH FLOOR, EC3R 7NE, LONDON, GB",
        // New York â€” Citi sanctions false-positive example: CUBA in a street name
        // must not resolve country to CU (BQA-004)
        "JOHN SMITH, 126 CUBA AVE, NEW YORK CITY, NY 10306, UNITED STATES"
    );

    private record EdgeCase(String label, String why, String address) {}

    private record EdgeCaseResult(String label, String why, String input, ParseResponse result) {}

    /** docs/plans/040.03 / 040.06: CityCountryLookup (CountryDetector's last-resort fallback,
     *  reached only when no postal-code pattern or country-name-hint text matches at all) used
     *  to scan raw text for a known city name with no disambiguation, so an ordinary common word
     *  that coincidentally collided with an obscure real place name elsewhere in the world could
     *  silently misresolve the country. These two cases were verified end-to-end against this
     *  sample's own default tokenizer wiring, not assumed. */
    private static final List<EdgeCase> EDGE_CASES = List.of(

        new EdgeCase(
            "Common first name coincidentally a real (but tiny) place — no longer trusted",
            "\"Donald\" is a real town in both Victoria, Australia (pop. 1,469) and Oregon, US " +
            "(pop. 1,001) — before docs/plans/040.06's population floor, CityCountryLookup's " +
            "last-resort fallback picked the higher-population one (AU) with no other evidence " +
            "at all, so any text ending in this ordinary first name misresolved the country. " +
            "city_countries.tsv now drops any name whose population falls under 10,000, so " +
            "\"Donald\" isn't in the index any more — the address honestly resolves country=null " +
            "instead of a wrong-but-confident AU.",
            "MEET ME AT DONALD"
        ),

        new EdgeCase(
            "Short word correctly trusted once it's a genuinely significant place",
            "The flip side of the same fix: docs/plans/040.03 originally excluded every " +
            "single-word candidate under 4 characters, assuming a short word was too unreliable " +
            "to trust — but \"Aba\" is Nigeria's third-largest metro area at 1.16 million people, " +
            "excluded only because it's short. Replacing that length guard with the population " +
            "floor lets this resolve correctly instead of silently discarding real evidence.",
            "SOMETHING ABA"
        )
    );

    // â”€â”€ Endpoints â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Operation(
        summary = "Parse a single address",
        description = """
            Tokenises a free-text postal address into structured tokens.

            Returns {raw, tokens, diagnostics}. `diagnostics` is always present, but on
            this free tier only `confidence` (parse/gazetteer/final all equal — no
            gazetteer tier to report) and `needsReview` are populated; fields that
            require Pro-only enrichment (`countryCodeStatus`, `inputStructure`,
            `cbprStructured`, `fintracPoBoxInvalid`, `corrections`, `traceLogs`,
            `fieldConfidences`) are absent. See addresstokenizer-pro-sample's `/parse`
            for the fully populated shape.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Parsed successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or blank address parameter")
        }
    )
    @GetMapping("/parse")
    public ParseResponse parse(
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
    public List<ParseResponse> demo() {
        return DEMO_ADDRESSES.stream()
                .map(tokenizer::parse)
                .map(this::toResponse)
                .toList();
    }

    @Operation(
        summary = "Run built-in SWIFT/bank-published worked examples",
        description = """
            Parses 3 real addresses taken from public bank ISO 20022 migration guides
            (Brussels/HSBC, London/ANZ, and Citi's CUBA AVE sanctions-screening
            false-positive example — see docs/plans/040.04). Address Tokenizer is not
            SWIFT-certified or endorsed by these banks; this demonstrates parsing
            against publicly published examples, not compliance with any bank's or
            SWIFT's own validation rules.

            Brussels is Belgium, which has no dedicated country parser, so it's tokenized by
            the low-confidence generic fallback instead (needsReview=true) — a real, expected
            limitation, not a bug. All three examples resolve their fields correctly.
            """,
        responses = @ApiResponse(responseCode = "200", description = "Results for the 3 worked examples")
    )
    @GetMapping("/demo/swift-examples")
    public List<ParseResponse> swiftExamples() {
        return SWIFT_EXAMPLE_ADDRESSES.stream()
                .map(tokenizer::parse)
                .map(this::toResponse)
                .toList();
    }

    @Operation(
        summary = "Run edge-case addresses",
        description = """
            A curated set of addresses that exercise boundary conditions in
            CityCountryLookup, CountryDetector's last-resort country-detection
            fallback (docs/plans/040.03, 040.06) — reached only when no postal-code
            pattern or country-name-hint text matches at all.
            """,
        responses = @ApiResponse(responseCode = "200", description = "Edge-case results")
    )
    @GetMapping("/demo/edge-cases")
    public List<EdgeCaseResult> edgeCases() {
        return EDGE_CASES.stream()
                .map(ec -> new EdgeCaseResult(
                        ec.label(),
                        ec.why(),
                        ec.address(),
                        toResponse(tokenizer.parse(ec.address()))))
                .toList();
    }

    // ── Response builder ────────────────────────────────────────────────────

    /** Shared {@code /parse} response contract (docs/plans/032.03) — same shape Pro's
     *  {@code addresstokenizer-pro-sample} returns from its own {@code /parse}. */
    private record ParseResponse(String raw, List<AddressToken> tokens, ParserDiagnosticsView diagnostics) {}

    private ParseResponse toResponse(ParsedAddress parsed) {
        ParserDiagnosticsView diagnostics =
                ParserDiagnosticsView.fromCore(parsed.parseConfidence(), confidenceThreshold);
        return new ParseResponse(parsed.raw(), parsed.tokens(), diagnostics);
    }
}

