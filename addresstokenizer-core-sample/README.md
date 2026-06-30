/*
 * Copyright (c) 2026 PassionCore Technologies Inc. (dev@passioncore.io)
 *
 * This file is part of Address Tokenizer Core Sample.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Commercial
 */
# addresstokenizer-core-sample

A runnable Spring Boot application that shows how to integrate **addresstokenizer-core** (the free tier) into your own project.

---

## What this sample covers

| Feature | Where to look |
|---|---|
| Autowire `AddressTokenizer` into a Spring bean | `AddressParseController` |
| Parse a single address string | `/parse?address=â€¦` endpoint |
| Run built-in country examples | `/demo` endpoint |
| Batch parsing (`parseBatch`) | `AddressTokenizerUsageTest` |
| Multi-line MT103 input (`parseLines`) | `AddressTokenizerUsageTest` |
| Access individual token types | `AddressTokenizerUsageTest` |
| Override confidence thresholds | `application.properties` |

---

## Build and run

```bash
# 1. Build and install the library from the project root
cd ..
mvn install -DskipTests

# 2. Run the sample app
cd addresstokenizer-core-sample
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## REST endpoints

### `GET /parse?address=â€¦`

Parse any free-text address string.

```bash
curl "http://localhost:8080/parse?address=10%20Downing%20Street%2C%20London%20SW1A%202AA"
```

```json
{
  "raw": "10 Downing Street, London SW1A 2AA",
  "country": "UK",
  "confidence": "94%",
  "needsReview": false,
  "tokens": [
    { "type": "HOUSE_NO",    "value": "10",         "confidence": "0.99" },
    { "type": "STREET_NAME", "value": "DOWNING ST",  "confidence": "0.93" },
    { "type": "CITY",        "value": "LONDON",      "confidence": "0.91" },
    { "type": "POSTAL_CODE", "value": "SW1A 2AA",    "confidence": "0.99" }
  ]
}
```

### `GET /demo`

Runs a set of built-in examples covering all countries in the free tier:

| # | Country | Example address |
|---|---|---|
| 1 | US | 350 Fifth Avenue, Suite 6402, New York, NY 10118 |
| 2 | UK | 10 Downing Street, London SW1A 2AA |
| 3 | DE | Unter den Linden 6, 10117 Berlin |
| 4 | FR | 75 Rue de Rivoli, 75001 Paris |
| 5 | AU | Level 3/80 Pacific Highway, North Sydney NSW 2060 |
| 6 | CA | 120 Adelaide Street West, Suite 2500, Toronto, ON M5H 1T1 |
| 7 | CA/QC | 1000 rue de la GauchetiÃ¨re Ouest, Bureau 2500, MontrÃ©al, QC H3B 4W5 |
| 8 | CA (PO Box) | PO Box 9000, Victoria, BC V8W 9V6 |

```bash
curl http://localhost:8080/demo | python -m json.tool
```

---

## Using `AddressTokenizer` directly

You do not need a REST layer. Autowire `AddressTokenizer` anywhere in your Spring app:

```java
@Service
@RequiredArgsConstructor
public class MyService {

    private final AddressTokenizer tokenizer;

    public void process(String rawAddress) {
        ParsedAddress result = tokenizer.parse(rawAddress);

        // Access the detected country
        String country = result.countryCode();          // "US", "UK", "DE" â€¦

        // Access a specific token
        Optional<String> postCode = result.get(TokenType.POSTAL_CODE);

        // Check overall confidence
        boolean needsManualReview = result.confidence() < 0.75;

        // Get all tokens as a Map<String, String>
        Map<String, String> map = result.toMap();

        // Parse multi-line MT103 input
        ParsedAddress mt = tokenizer.parseLines(List.of("10 Downing Street", "London", "SW1A 2AA"));

        // Batch (uses parallel streams internally)
        List<ParsedAddress> batch = tokenizer.parseBatch(List.of(addr1, addr2, addr3));
    }
}
```

See `AddressTokenizerUsageTest` for working examples of every pattern above.

---

## Configuration

All properties below can be added to your `application.properties`. The defaults are set for pacs.008 / ISO 20022 compliance.

```properties
# Confidence score below which needsReview = true  (default: 0.75)
address.tokenizer.confidence-threshold=0.75

# Scoring profile: ISO20022 (default) | MARKETING | CUSTOM
address.confidence.profile=ISO20022

# Custom weights (only active when profile=CUSTOM)
# address.confidence.weights.city=0.35
# address.confidence.weights.country=0.35
# address.confidence.weights.postalCode=0.15
# address.confidence.weights.streetName=0.10
# address.confidence.weights.buildingName=0.05
```

---

## Supported countries (free tier)

| Country | Parser class |
|---|---|
| US | `UsAddressParser` |
| UK | `UkAddressParser` |
| DE | `DeAddressParser` |
| FR | `FrAddressParser` |
| AU | `AuAddressParser` |
| CA | `CaAddressParser` + `QuebecFrenchParser` |
| Any | `GenericAddressParser` (fallback) |

For HK, SG, JP, ISO 20022 enrichment, and gazetteer-backed city validation, see **addresstokenizer-pro**.

---

## Token types

| Token | Meaning |
|---|---|
| `HOUSE_NO` | Building or street number |
| `STREET_NAME` | Street or road name |
| `STREET_TYPE` | St, Ave, Blvd, Rue â€¦ |
| `UNIT` | Suite, Apt, Unit |
| `FLOOR` | Level, Floor |
| `CITY` | City or town name |
| `STATE` / `STATE_CODE` | Province/state full name or abbreviation |
| `POSTAL_CODE` | Post/ZIP code |
| `COUNTRY` / `COUNTRY_CODE` | Country name or ISO alpha-2 |
| `PO_BOX` | Post Office Box number |
| `BUILDING_NAME` | Named building |
| `DIRECTION` | N, S, E, W, NW â€¦ |

---

## Running the tests

```bash
mvn test
```

Two test classes are included:

| Class | What it tests |
|---|---|
| `AddressParseControllerTest` | All REST endpoints via Spring MockMvc |
| `AddressTokenizerUsageTest` | Direct `AddressTokenizer` API â€” good copy-paste reference |

## License

Address Tokenizer Core Sample is sample code for Address Tokenizer Core.

Unless a separate written commercial license applies, this sample project is licensed under the GNU Affero General Public License v3.0 or later.

SPDX expression:

AGPL-3.0-or-later OR LicenseRef-Commercial

