# addresstokenizer-core

**Free tier** of the Address Tokenizer library.

Provides the `AddressParser` SPI, shared model types, SWIFT ASCII normalization, and country-specific parsers for the most common Western address formats. No database, no gazetteer, no JPA — a lightweight JAR you can drop into any Spring Boot project.

## API Stability

This library is at **v0.2.0** — early release.

The parsing engine is production-quality for the supported countries.
`ParsedAddress` now carries named fields (`streetName()`, `city()`, …) directly as
record components, plus an optional `diagnostics()` for Pro-tier enrichment — see
"Upgrading from Core to Pro" below. The shape may still evolve before v1.0.

For production deployments or enterprise licensing contact **dev@passioncore.io**.

## Core vs Pro

| Feature | Core (Apache-2.0) | Pro (Commercial) |
|---|---|---|
| Address tokenisation (US, UK, DE, FR, AU, CA, Quebec) | ✓ | ✓ |
| `AddressParsingService.parse(String)` / `parseLines(List<String>)` | ✓ | ✓ |
| Named field accessors (`city()`, `streetName()`, `postalCode()`, …) | ✓ | ✓ |
| Basic parse confidence (`parseConfidence`) | ✓ | ✓ |
| More country parsers (HK, SG, JP, BR) | — | ✓ |
| Gazetteer enrichment (IATA, GeoNames, OurAirports) | — | ✓ |
| Postal code → city lookup | — | ✓ |
| `diagnostics()` — weighted aggregate confidence | — | ✓ |
| `diagnostics()` — field-level confidence | — | ✓ |
| `diagnostics()` — trace logs | — | ✓ |
| `diagnostics()` — corrections map | — | ✓ |
| `diagnostics()` — ISO 20022 / pacs.008 structured output | — | ✓ |

Upgrading from Core to Pro requires only adding the Pro dependency and swapping which
`AddressParsingService` bean is injected — see "Upgrading from Core to Pro" below.

---

## Upgrading from Core to Pro

Both `AddressTokenizer` (Core) and `AddressEnrichmentService` (Pro) implement the same
`AddressParsingService` interface and return the same `ParsedAddress` type. Program
against the interface and the call site never changes:

```java
@Autowired
private AddressParsingService parsingService; // AddressTokenizer or AddressEnrichmentService

ParsedAddress result = parsingService.parse("350 Fifth Avenue, New York, NY 10118");
result.city();          // "NEW YORK" — same accessor, same value, in both tiers
result.diagnostics();   // null in Core; populated by Pro's gazetteer enrichment
```

Adding the Pro dependency and letting Spring wire `AddressEnrichmentService` in place of
`AddressTokenizer` is the entire upgrade — no code change, no return-type change. Pro's
`ParsedAddress` additionally corrects `city()`/`country()`/`postalCode()` via gazetteer
and postal-code lookups, and populates `diagnostics()` with weighted confidence, ISO
20022 structure, field-level confidence, corrections, and trace logs.

---

## What's included

| Component | Description |
|---|---|
| `AddressParsingService` | Public entry point interface — `parse()`/`parseLines()`; implemented by `AddressTokenizer` (Core) and `AddressEnrichmentService` (Pro) |
| `AddressParser` SPI | Interface every parser implements; extend it to add your own country |
| `AddressTokenizer` | Dispatcher: detects country, calls the right parser, rates confidence, maps tokens to named fields |
| `CountryDetector` | Regex + postal-code cascade; detects 10+ countries from a flat string |
| `UsAddressParser` | US USPS format — ZIP, state, unit, floor, street type |
| `UkAddressParser` | UK Royal Mail format — postcode at end, county, district |
| `DeAddressParser` | German format — house number **after** street name, `\d{5}` postcode |
| `FrAddressParser` | French format — street type before name (Rue, Avenue, Boulevard), CEDEX |
| `AuAddressParser` | Australian format — state abbreviation + 4-digit postcode |
| `CaAddressParser` | Canadian format — province code + alphanumeric postal code |
| `QuebecFrenchDetector` | Heuristic detection of Quebec French address patterns |
| `QuebecFrenchParser` | Quebec French address tokeniser (Rue, Boul, Ave in French) |
| `GenericAddressParser` | Positional fallback for unsupported countries (low confidence) |
| `model.*` | `AddressToken`, `TokenType`, `ParsedAddress`, `ParseDiagnostics` (Pro-populated, nullable in Core) |
| `utils.NormalizationUtil` | SWIFT Basic Latin charset conversion (ä→ae, é→e, ñ→n, …) |
| `dict.StreetTypeDictionary` | Curated street-type vocabulary for US/EN/DE/FR |

## Not included (pro only)

- HK, SG, JP, BR parsers
- ISO 20022 pacs.008 compliance layer
- GeoNames / OurAirports gazetteer
- IATA city-code enrichment
- FINTRAC PO Box validation
- Confidence scoring profiles (ISO20022, MARKETING)

## Installation

```xml
<dependency>
    <groupId>io.passioncore</groupId>
    <artifactId>addresstokenizer-core</artifactId>
    <version>0.2.0</version>
</dependency>
```

No extra configuration needed — Spring Boot picks up `AddressTokenizerAutoConfiguration` automatically via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## Usage

### Spring Boot (recommended)

Inject `AddressParsingService` rather than `AddressTokenizer` directly if you want the
code to keep working unchanged after upgrading to Pro (see "Upgrading from Core to Pro").

```java
@Autowired
private AddressTokenizer tokenizer; // or AddressParsingService for a Pro-portable call site

ParsedAddress result = tokenizer.parse("350 5th Ave, New York, NY 10118");

result.get(TokenType.HOUSE_NO)     // "350"
result.get(TokenType.STREET_NAME)  // "5TH AVE"
result.get(TokenType.CITY)         // "NEW YORK"
result.get(TokenType.STATE_CODE)   // "NY"
result.get(TokenType.POSTAL_CODE)  // "10118"
result.countryCode()               // "US"
result.parseConfidence()           // 0.92

// Named accessors (equivalent to get(TokenType.*))
result.city()          // "NEW YORK"
result.streetName()    // "5TH AVE"
result.buildingName()  // "350"
result.postalCode()    // "10118"
result.state()         // "NY"
```

Multi-line MT103 input (preserves line boundaries for better parsing):

```java
List<String> lines = List.of(
    "123 SHERBROOKE ST WEST",
    "MONTREAL QC H3A 1B1"
);
ParsedAddress result = tokenizer.parseLines(lines);
```

Batch parsing (uses `parallelStream` internally):

```java
List<ParsedAddress> results = tokenizer.parseBatch(addresses);
```

### Without Spring

```java
List<AddressParser> parsers = List.of(
    new UsAddressParser(),
    new UkAddressParser(),
    new DeAddressParser(),
    new FrAddressParser(),
    new AuAddressParser(),
    new CaAddressParser(new QuebecFrenchDetector(), new QuebecFrenchParser())
);
CountryDetector detector = new CountryDetector(parsers);
GenericAddressParser fallback = new GenericAddressParser();
NormalizationUtil norm = new NormalizationUtil();

AddressTokenizer tokenizer = new AddressTokenizer(
    detector, parsers, fallback, norm, null // healer — null in Core-only mode
);
```

## Output model

`ParsedAddress` fields are accessed by `TokenType`:

| `TokenType` | pacs.008 element | Description |
|---|---|---|
| `HOUSE_NO` | `<BldgNb>` | House or building number |
| `STREET_NAME` | `<StrtNm>` (body) | Street name without type suffix |
| `STREET_TYPE` | `<StrtNm>` (suffix) | St, Rd, Ave, Blvd, … |
| `UNIT` | `<BldgNb>` / `<BldgNm>` | Apt, Suite, Unit |
| `FLOOR` | `<Flr>` | Floor designation |
| `CITY` | `<TwnNm>` | City / municipality |
| `STATE_CODE` | `<CtrySubDvsn>` | State or province abbreviation |
| `POSTAL_CODE` | `<PstCd>` | Postal or ZIP code |
| `PO_BOX` | `<PstBx>` | PO Box number |
| `COUNTRY` | — | Full country name |
| `COUNTRY_CODE` | `<Ctry>` | ISO 3166-1 alpha-2 |

`ParsedAddress` also exposes:
- `parseConfidence()` — basic parse confidence in `[0.0, 1.0]` based on token coverage
- Named fields, populated by `AddressTokenizer` from the token list: `streetName()`, `buildingName()`, `unit()`, `floor()`, `city()`, `district()`, `state()`, `postalCode()`
- `country()` — resolved country, derived from tokens with a fallback to `countryCode()`
- `diagnostics()` — `null` in Core; populated by Pro with weighted confidence, ISO 20022 structure, field confidence, corrections, and trace logs
- `toMap()` — flat `Map<String, String>` keyed by `TokenType.name()`

## Extending with a custom parser

Implement `AddressParser` and register it as a Spring bean:

```java
@Component
public class MxAddressParser implements AddressParser {

    @Override public String countryCode() { return "MX"; }

    @Override
    public ParsedAddress parse(String raw, String country, ParseTrace trace) {
        // extract tokens, log to trace, return ParsedAddress
    }
}
```

Spring auto-wires it into `AddressTokenizer` — no further configuration needed.

## Build

```bash
# Build core alone
mvn verify -pl addresstokenizer-core

# Install to local Maven repo
mvn install -DskipTests -pl addresstokenizer-core
```

## License

Address Tokenizer Core is licensed under the **Apache License, Version 2.0**.

```text
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at:

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

SPDX: `Apache-2.0`

For commercial Pro licensing (gazetteer enrichment, ISO 20022 output, SLA support), contact:

```text
dev@passioncore.io
```
