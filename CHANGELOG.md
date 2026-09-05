# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
(pre-1.0: minor versions may contain breaking changes, as noted in the README's
API Stability section).

## [0.6.0] - 2026-09-05

### Added

- `addresstokenizer-core-sample-plain-java` — new module, first publish. Non-Spring
  reference: a reusable `AddressParsingClient` wrapper class meant to be copied into your
  own project, plus an optional thin `java.net.HttpServer` layer on top proving it composes
  into a plain HTTP stack. Uses `AddressTokenizer.createDefault()`, no Spring container.
- `TokenType.PHASE` — a new token type alongside the existing `TokenType.BLOCK`, for
  addressing schemes that distinguish a "Phase N" designator from a block/tower number.
- `DISCLAIMER.md` at the repository root, packaged alongside `LICENSE` and `NOTICE.md` into
  `addresstokenizer-core`'s built JAR under `META-INF/`.

### Fixed

- `GenericAddressParser` (the low-confidence fallback used for any country with no dedicated
  parser) labeled every non-first comma/newline segment `CITY`, producing multiple `CITY`
  tokens for one address. Now only the last remaining segment is trusted as `CITY`; segments
  in between (floor, building name, street, district) fall back to `STREET_NAME`.

### Changed

- README's "Disclaimer" section replaced with the fuller "Important use limitation" text
  (deterministic-parsing caveats, no compliance/certification guarantee), linking to the new
  `DISCLAIMER.md`. The same section was added to `addresstokenizer-core`'s own README, which
  didn't have one before.

## [0.5.0] - 2026-09-02

### Added

- `AddressTokenizer.createDefault()` — construct a fully-wired tokenizer without a Spring
  container, for callers who don't use Spring Boot.
- `addresstokenizer-core-sample`: `GET /demo/swift-examples` — parses 3 real addresses taken
  from public bank ISO 20022 migration guides (Brussels/HSBC, London/ANZ, Citi's CUBA AVE
  sanctions-screening false-positive example).
- `addresstokenizer-core-sample`: `GET /demo/edge-cases` — demonstrates two
  `CityCountryLookup` boundary cases (see Fixed below).

### Fixed

- `GenericAddressParser` (the low-confidence fallback used for any country with no dedicated
  parser) discarded the real city whenever it shared a comma-segment with the postal code
  (e.g. "1000 BRUSSELS" kept only the postal code, dropping "BRUSSELS"), while blindly
  labeling every other non-first segment `CITY` — including floor/unit markers and the bare
  trailing country code. Now keeps the postal segment's remaining text as `CITY`, routes
  floor/unit-shaped segments to `UNIT`, and no longer duplicates the country code as `CITY`.
- `CountryDetector` misread a Chinese address as India whenever its 6-digit postal code
  appeared with a `CN` marker but no literal word "CHINA" (China's own postal-code shape
  collided with `IN_POSTAL`'s generic 6-digit fallback). Now correctly detected as `CN`.
- `CityCountryLookup`'s last-resort country-detection fallback (used only when no postal
  code or country-name text matches at all) could let an ordinary word shadow an unrelated
  real city elsewhere in the world — e.g. "Donald" coincidentally matching a small town in
  Victoria, Australia. The underlying `city_countries.tsv` is regenerated with a population
  floor (place names with population ≥ 10,000 only; 171,750 → 41,317 entries), replacing the
  previous length/word-list heuristics, which also incorrectly excluded some legitimately
  large cities that happened to have short names (e.g. "Aba", Nigeria's third-largest city).
- `UkAddressParser` discarded address content that came after a matched postcode, so a city
  name following the postcode (e.g. "...EC3R 7NE, London, GB") was silently dropped instead
  of becoming the `CITY` token.
- `QuebecFrenchParser` could misdetect a non-numeric reference-number line preceding the real
  address (e.g. "CA783643864230") as the street line, and didn't strip a trailing
  self-referential province/country mention from the city value.
- `CaAddressParser` misfiled the city as the street name for an address consisting only of a
  PO Box and a city (e.g. "PO Box 9000, Victoria, BC V8W 9V6").
- `FrAddressParser`, `DeAddressParser`, `UsAddressParser`, `UkAddressParser`, and
  `AuAddressParser` failed to split street from city on addresses with no comma separators
  (common in multi-line pasted addresses); all five now fall back to a newline-aware split.
- `AddressTokenizer.parseLines()`'s PO Box branch now returns the same SWIFT-normalized `raw`
  value as every other address (previously returned the un-normalized original text).

## [0.4.0] - 2026-08-24

### Changed — **breaking**

- `ParsedAddress`'s JSON output is restructured. The flat fields (`streetName`,
  `buildingName`, `unit`, `floor`, `city`, `district`, `state`, `postalCode`), `tokens`,
  and the derived `country` are no longer serialized at the top level — they move into
  a new nested `general` object (`general.countryCode`, `general.streetName`, etc.),
  built by the new `general()` accessor. `iso20022Result` is promoted from nested inside
  `diagnostics` to its own top-level JSON section. Code that reads `ParsedAddress` as a
  Java object is unaffected (the record's accessor methods are unchanged); code that
  serializes it to JSON directly (rather than through `addresstokenizer-core-sample`'s
  `/parse` endpoint, which is unaffected — see below) will see a different shape.
- `ParseDiagnostics.confidence` changes type from `double` to a new `ConfidenceBreakdown`
  record (`{parse, gazetteer, final}`), so a caller can see the token-coverage confidence,
  the pre-penalty gazetteer match tier, and the post-penalty final value side by side
  instead of only the final aggregate.
- `ParseDiagnostics.fieldConfidences` and `AddressIso20022Result.fieldConfidence` change
  value type from a plain `Double` to a new `FieldConfidenceEntry` record
  (`{confidence, penalty}`, `penalty` omitted when none applied) — merges what were two
  parallel confidence/penalty maps into one.
- `ParseDiagnostics.subDivision` and `townLocation` are removed — both were pure
  duplicates of `iso20022Result.ctrySubDvsn`/`twnLctnNm`; read those instead.
- `TokenType.CORRECTED_CITY` is removed. Enrichment-corrected values (e.g. a typo'd city
  fixed via gazetteer lookup) now replace the token's `value` in place, with the
  as-parsed value preserved on the same token's new `original` field, rather than
  appearing as a second, separately-typed token.
- `addresstokenizer-core-sample`'s `GET /parse` response reshapes from
  `{raw, country, tokens: [{type, value}]}` to `{raw, tokens, diagnostics}`. `diagnostics`
  is always present (never `null`); on this free tier only `confidence` (`parse`/
  `gazetteer`/`final` all equal — no gazetteer tier to report) and `needsReview` are
  populated, matching the shape Pro's `/parse` returns with those two fields always
  populated the same way.

### Added

- `AddressToken` gains an `original` field carrying the as-parsed value when enrichment
  replaced it (see the `CORRECTED_CITY` removal above); `null` when the token's `value`
  is unchanged from parsing. A new 4-argument constructor is available; the existing
  2- and 3-argument constructors are unchanged.
- `ParsedAddress.general()` and `ParsedAddress.iso20022Result()` accessor methods, and
  three new model types backing them: `GeneralView`, `ConfidenceBreakdown`,
  `FieldConfidenceEntry`.

### Fixed

- `ParsedAddress.countryName()` threw `IllformedLocaleException` for any address that
  resolves country to a non-ISO value (e.g. the `"UNKNOWN"` detection-failure sentinel)
  — now returns `null` instead.
- `AddressTokenizer.parse()`'s parse confidence could exceed 1.0 for an address with a
  corrected city (the confidence calculation counted mandatory-type token occurrences
  rather than distinct types present, so a second same-type token could inflate the
  score) — now counts distinct types.
- `CountryDetector.detect()` could resolve the wrong country at high confidence when two
  country names of equal length both appeared in the address text (e.g. an address
  mentioning "Hong Kong" twice as unrelated proper nouns, with "Australia" appearing once
  as the actual trailing country declaration, previously resolved to `HK` instead of
  `AU`). The equal-length tie-break now prefers whichever name appears closer to the end
  of the string, matching mailing-address convention; the existing longer-name-wins rule
  is unchanged. Same fix applied to `detectInTail()`.
- `AuAddressParser` dropped the suburb entirely when no comma separated it from the street
  (e.g. `"... 80 Druitt Street SYDNEY NSW 2000 ..."`, a common valid AU layout) — `CITY`
  is now also read from the text immediately following the street type when no
  comma-delimited city segment was found.
- `AuAddressParser`'s unit/suite value truncated at the first `.` (e.g. `"Suite 14.02"`,
  AU's floor.unit-style suite numbering, came out as `"Suite 14"` with `".02"` leaking into
  the street name) — the value now retains dots.
- `AuAddressParser` only ever captured the first unit-like prefix (`Unit`/`Level`/`Suite`/
  etc.) — a stacked second prefix (e.g. `"Suite 14.02, Level 14, ..."`, restating the
  level already encoded in the suite number) leaked into the street name instead of being
  part of `UNIT`. `UNIT` now collects every stacked prefix into one value
  (`"Suite 14.02, Level 14"`).

## [0.3.0] - 2026-08-18

### Added

- `CountryDetectorInterface` gains `detectInTail()` — a fuzzy scan of an address's
  trailing tokens for a recognized full country name — and `detectDeclaredCountryCode()`
  — matches a bare 2-letter country code standing alone in the address's final
  comma-delimited segment. Both are used to reconcile a raw address's literal country
  declaration against the resolved `countryCode`.
- `AddressToken` gains an optional `source()` field recording provenance for values not
  parsed directly from the raw input (e.g. `"postal derived"` for a Pro-tier city value
  looked up from the postal code). The existing two-argument constructor is preserved,
  so no source changes are required for direct construction.

### Changed

- `AddressTokenizer.parse()`'s parse confidence now applies a small penalty when an
  address's literal declared country conflicts with the resolved `countryCode` (e.g.
  `"350 Fifth Avenue, New York, NY 10118, CA"` declares `CA` but resolves to `US`) —
  previously this conflict was silently discarded and had no effect on confidence.

### Fixed

- US, UK, AU, and CA address parsers no longer misread a redundant trailing
  self-reference to their own country (e.g. `"...London, UK"`, `"...Melbourne,
  Australia"`) as the `CITY` token.
- `addresstokenizer-core-sample`: the README's license section incorrectly stated
  AGPL-3.0-or-later; corrected to Apache-2.0, matching the module's actual license.

## [0.2.1] - 2026-08-16

### Fixed

- Canadian address parsing now supports unit values written as `Suite #123` in
  addition to `Suite 123`.

### Added

- Core-only tests covering named field accessors for FR, AU, and CA addresses.

### Changed

- Clarified Core parsing limitations and ISO 20022/CBPR+ disclaimer text in the
  public API documentation.

## [0.2.0] — 2026-07-03

### Changed — **breaking**

- **`ParsedAddress` reshaped.** Parsed fields (`streetName`, `city`, `postalCode`,
  `country`, …) are now first-class record components with named accessors, replacing
  the previous token-map-centric shape. Code that read tokens positionally must switch
  to the named accessors.
- **New `AddressParsingService` interface.** `AddressTokenizer` now implements
  `io.passioncore.addresstokenizer.AddressParsingService` (`parse(String)` /
  `parseLines(List<String>)`). This is the same interface implemented by the
  commercial Pro tier's `AddressEnrichmentService`, so upgrading from Core to Pro
  is a bean swap with no application-code changes.

### Added

- `ParseDiagnostics` on `ParsedAddress.diagnostics()` — populated by the Pro tier
  (weighted aggregate confidence, field-level confidence, trace logs, corrections
  map); always present with basic parse confidence in Core.
- Supporting model types: `AddressIso20022Result`, `CountryCodeStatus`,
  `FieldCorrection`, `TraceLog`.
- `jackson-annotations` dependency (annotations only, no databind) so JSON
  serialization of `ParseDiagnostics` drops null/absent fields.

## [0.1.0] — 2026-06-30

- Initial public release: `AddressParser` SPI, SWIFT Basic Latin normalisation,
  country parsers for US, UK, DE, FR, AU, CA (including Quebec French),
  Spring Boot auto-configuration, `addresstokenizer-core-sample` app.

[0.6.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.6.0
[0.5.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.5.0
[0.4.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.4.0
[0.3.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.3.0
[0.2.1]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.2.1
[0.2.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.2.0
[0.1.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.1.0
