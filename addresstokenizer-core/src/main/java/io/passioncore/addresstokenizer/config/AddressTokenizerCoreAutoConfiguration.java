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

package io.passioncore.addresstokenizer.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

/**
 * Spring Boot auto-configuration for the addresstokenizer-core library.
 *
 * <p>Consuming applications do NOT need to add {@code @ComponentScan} or
 * {@code @Import}. Add the JAR to the classpath â€” Spring Boot picks this
 * class up via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.</p>
 *
 * <p>{@code @PropertySource} registers the library's own
 * {@code addresstokenizer-core-defaults.properties} as a named property source
 * so it is always present regardless of classpath ordering and is still
 * overridable by the consuming application's {@code application.properties}.</p>
 */
@AutoConfiguration
@ComponentScan(basePackages = "io.passioncore.addresstokenizer")
@PropertySource(
    value = "classpath:addresstokenizer-core-defaults.properties",
    ignoreResourceNotFound = false
)
public class AddressTokenizerCoreAutoConfiguration {
    // All beans declared via @Component / @Service.
}

