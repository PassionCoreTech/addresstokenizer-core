# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
(pre-1.0: minor versions may contain breaking changes, as noted in the README's
API Stability section).

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

[0.4.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.4.0
[0.3.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.3.0
[0.2.1]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.2.1
[0.2.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.2.0
[0.1.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.1.0
