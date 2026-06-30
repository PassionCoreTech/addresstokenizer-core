# Third-Party Notices — Address Tokenizer Core

Address Tokenizer Core
Copyright (c) 2026 PassionCore Technologies Inc. (dev@passioncore.io)

This document lists all third-party software and data dependencies used by
Address Tokenizer Core. It must be reviewed and kept up to date before each
public or commercial release.



---

## Java Runtime Dependencies

| Artifact | Version | License | Purpose |
|---|---|---|---|
| `spring-boot-starter` | 4.0.5 | Apache-2.0 | Spring Boot auto-configuration infrastructure |
| `spring-boot-starter-log4j2` | 4.0.5 | Apache-2.0 | Logging via Log4j2 |
| `spring-framework` (transitive) | 7.0.6 | Apache-2.0 | Core Spring container, beans, context |
| `log4j-core` (transitive) | 2.25.3 | Apache-2.0 | Log4j2 implementation |
| `slf4j-api` (transitive) | managed by Spring Boot BOM | MIT | Logging facade |
| `snakeyaml` (transitive) | managed by Spring Boot BOM | Apache-2.0 | YAML configuration parsing |
| `spring-jdbc` | managed by Spring Boot BOM | Apache-2.0 | Optional JDBC — `CountryDetector` and `TokenConfidenceRater` accept a nullable `JdbcTemplate`; Core functions without a database |
| `lombok` | 1.18.44 | MIT | Compile-time code generation — **not bundled in the released JAR** |

## Test-Only Dependencies (not included in released JAR)

| Artifact | Version | License | Purpose |
|---|---|---|---|
| `spring-boot-starter-test` | 4.0.5 | Apache-2.0 | JUnit 5, Mockito, AssertJ |
| `junit-jupiter` (transitive) | 6.0.3 | EPL-2.0 | JUnit 5 test framework |
| `mockito-core` (transitive) | 5.20.0 | MIT | Mocking framework |
| `assertj-core` (transitive) | 3.27.7 | Apache-2.0 | Fluent assertion library |

---

## License Notices

### Apache Software Foundation (Spring Boot, Spring Framework, Log4j2, SnakeYAML, AssertJ)

> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at:
> https://www.apache.org/licenses/LICENSE-2.0

### Lombok

> Copyright (C) 2009-2024 The Project Lombok Authors.
> Licensed under the MIT License.
> Source: https://projectlombok.org

### SLF4J

> Copyright (c) 2004-2024 QOS.ch. Licensed under the MIT License.
> Source: https://www.slf4j.org

### Mockito (test only)

> Copyright (c) 2007-2024 Mockito contributors. Licensed under the MIT License.
> Source: https://site.mockito.org

### JUnit 5 (test only)

> Copyright (c) 2015-2024 the JUnit team.
> Licensed under the Eclipse Public License 2.0.
> Source: https://junit.org/junit5/

---

## Bundled Data — GeoNames city_countries.tsv

| Field | Detail |
|---|---|
| **File bundled** | `src/main/resources/data/city_countries.tsv` (included in the released JAR) |
| **Derived from** | GeoNames `cities500.txt` — one row per ASCII city name, highest-population country retained per name; ~171k entries, ~2.4 MB |
| **Source** | https://download.geonames.org/export/dump/cities500.zip |
| **License** | Creative Commons Attribution 4.0 International (CC BY 4.0) |
| **License text** | https://creativecommons.org/licenses/by/4.0/ |
| **Purpose** | Final-fallback city-name → ISO 3166-1 country lookup in `CountryDetector` when all regex signals fail |

**Required CC BY 4.0 attribution:**

> This product includes data derived from GeoNames (https://www.geonames.org),
> available under the Creative Commons Attribution 4.0 International License
> (https://creativecommons.org/licenses/by/4.0/).

---

