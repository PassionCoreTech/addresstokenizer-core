# addresstokenizer-core

[![CI](https://github.com/PassionCoreTech/addresstokenizer-core/actions/workflows/ci.yml/badge.svg)](https://github.com/PassionCoreTech/addresstokenizer-core/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.passioncore/addresstokenizer-core)](https://central.sonatype.com/artifact/io.passioncore/addresstokenizer-core)

**Free, open-source address tokeniser** for postal address parsing in Spring Boot and plain Java applications.

Parses free-text postal addresses from 7 countries/locales into structured tokens — no database,
no REST layer, no third-party data subscriptions. SWIFT Basic Latin charset normalisation included
for [pacs.008 CBPR+](https://www.swift.com/standards/iso-20022/cbpr-plus) field formatting.

## API Stability

This library is at **v0.2.0** — early release.

The parsing engine is production-quality for the supported countries.
v0.2.0 lands the unified API refactor: Core and Pro now share the
`AddressParsingService` interface and a single `ParsedAddress` return type
(a **breaking change** from v0.1.0 — see CHANGELOG). The shape may still
evolve before v1.0.

For production deployments or enterprise licensing contact **dev@passioncore.io**.

## Core vs Pro

| Feature | Core (Apache-2.0) | Pro (Commercial) |
|---|---|---|
| Address tokenisation (US, UK, DE, FR, AU, CA, Quebec) | ✓ | ✓ |
| `parse(String)` / `parseLines(List<String>)` | ✓ | ✓ |
| Named field accessors (`city()`, `streetName()`, `postalCode()`, …) | ✓ | ✓ |
| Basic parse confidence (`parseConfidence`) | ✓ | ✓ |
| More country parsers (HK, SG, JP, BR) | — | ✓ |
| Gazetteer enrichment (IATA, GeoNames, OurAirports) | — | ✓ |
| Postal code → city lookup | — | ✓ |
| Weighted aggregate confidence | — | ✓ |
| Field-level confidence | — | ✓ |
| Trace logs | — | ✓ |
| Corrections map | — | ✓ |
| ISO 20022 / pacs.008 structured output | — | ✓ |

Upgrading from Core to Pro requires only adding the Pro dependency.
Application code that calls `AddressTokenizer.parse()` continues to work unchanged.

---

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

```java
@Autowired
private AddressTokenizer tokenizer;

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

Multi-line MT103 input:

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
- Named field accessors: `city()`, `streetName()`, `buildingName()`, `unit()`, `floor()`, `state()`, `postalCode()`, `country()`
- `toMap()` — flat `Map<String, String>` keyed by `TokenType.name()`

## What's included

| Component | Description |
|---|---|
| `AddressParser` SPI | Interface every parser implements; extend it to add your own country |
| `AddressTokenizer` | Dispatcher: detects country, calls the right parser, rates confidence |
| `CountryDetector` | Regex + postal-code cascade; detects 10+ countries from a flat string |
| `UsAddressParser` | US USPS format — ZIP, state, unit, floor, street type |
| `UkAddressParser` | UK Royal Mail format — postcode at end, county, district |
| `DeAddressParser` | German format — house number **after** street name, 5-digit postcode |
| `FrAddressParser` | French format — street type before name (Rue, Avenue, Boulevard), CEDEX |
| `AuAddressParser` | Australian format — state abbreviation + 4-digit postcode |
| `CaAddressParser` | Canadian format — province code + alphanumeric postal code |
| `QuebecFrenchDetector` | Heuristic detection of Quebec French address patterns |
| `QuebecFrenchParser` | Quebec French address tokeniser (Rue, Boul, Ave in French) |
| `GenericAddressParser` | Positional fallback for unsupported countries (low confidence) |
| `model.*` | `AddressToken`, `TokenType`, `ParsedAddress`, `ParseTrace` |
| `utils.NormalizationUtil` | SWIFT Basic Latin charset conversion (ä→ae, é→e, ñ→n, …) |
| `dict.StreetTypeDictionary` | Curated street-type vocabulary for US/EN/DE/FR |

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
# Build and test
mvn verify

# Install to local Maven repo
mvn install -DskipTests
```

## Modules

| Module | Description |
|---|---|
| `addresstokenizer-core` | The library JAR — add this as a dependency |
| `addresstokenizer-core-sample` | Runnable Spring Boot app showing usage via REST endpoint |

## License

Address Tokenizer Core is licensed under the **Apache License, Version 2.0**.

```text
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at:

    https://www.apache.org/licenses/LICENSE-2.0
```

SPDX: `Apache-2.0`

For commercial Pro licensing (gazetteer enrichment, ISO 20022 output, SLA support) contact:

```text
dev@passioncore.io
```
