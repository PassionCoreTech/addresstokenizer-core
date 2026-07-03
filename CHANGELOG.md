# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
(pre-1.0: minor versions may contain breaking changes, as noted in the README's
API Stability section).

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

[0.2.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.2.0
[0.1.0]: https://github.com/PassionCoreTech/addresstokenizer-core/releases/tag/v0.1.0
