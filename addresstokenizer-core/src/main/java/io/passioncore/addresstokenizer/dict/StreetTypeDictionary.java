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

package io.passioncore.addresstokenizer.dict;

import java.util.Set;

/**
 * Canonical street-type abbreviations per country.
 * Used by parsers to identify and split street components.
 */
public final class StreetTypeDictionary {

    private StreetTypeDictionary() {}

    public static final Set<String> EN = Set.of(
        "ST", "AVE", "BLVD", "DR", "RD", "LN", "CT", "PL", "WAY",
        "CIR", "CIRCLE", "TRL", "TRAIL", "PKWY", "HWY", "FWY", "EXPY",
        "GROVE", "RISE", "CLOSE", "MEWS", "TERRACE", "TCE", "CRES", "CRESCENT",
        "PARADE", "PDE", "ESPLANADE", "ESP"
    );

    public static final Set<String> DE = Set.of(
        "STRASSE", "STR", "WEG", "ALLEE", "GASSE", "PLATZ", "RING",
        "DAMM", "CHAUSSEE", "UFER", "STEIG", "STIEG", "GRABEN"
    );

    public static final Set<String> FR = Set.of(
        "RUE", "AVENUE", "AVE", "BOULEVARD", "BD", "BLVD", "IMPASSE", "IMP",
        "ALLEE", "PLACE", "PL", "CHEMIN", "ROUTE", "PASSAGE", "VILLA",
        "SQUARE", "CITE", "QUAI", "VOIE", "ALLÃ‰E", "RUELLE"
    );

    public static final Set<String> BR = Set.of(
        "RUA", "AVENIDA", "AV", "ALAMEDA", "AL", "TRAVESSA", "TV",
        "RODOVIA", "ESTRADA", "EST", "PRACA", "PC", "LARGO", "LG",
        "VIELA", "BECO", "ACESSO"
    );

    public static boolean isAny(String token) {
        return EN.contains(token) || DE.contains(token)
            || FR.contains(token) || BR.contains(token);
    }
}

