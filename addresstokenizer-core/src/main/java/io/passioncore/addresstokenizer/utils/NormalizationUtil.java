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

package io.passioncore.addresstokenizer.utils;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.passioncore.addresstokenizer.model.NormalizationHints;
import io.passioncore.addresstokenizer.model.NormalizationResult;

/**
 * SWIFT Basic Latin normalization utilities.
 *
 * <h3>Critical ordering inside {@link #toSwiftAscii}</h3>
 * <ol>
 *   <li>Word-level substitutions first (München&#x2192;Munich,
 *       São Paulo&#x2192;Sao Paulo)</li>
 *   <li>Char-level substitutions (ä&#x2192;ae, ß&#x2192;ss,
 *       ç&#x2192;c, æ&#x2192;ae)</li>
 *   <li>NFD decompose + strip combining marks</li>
 *   <li>Strip SWIFT-unsafe characters</li>
 *   <li>Collapse whitespace + uppercase</li>
 * </ol>
 * {@code ä&#x2192;ae} <strong>must</strong> run before NFD stripping.
 */
@Component
public class NormalizationUtil {

    private static final Pattern SWIFT_UNSAFE =
        Pattern.compile("[^A-Za-z0-9 /\\-?:()\\.,'\\+\\r\\n]");

    private static final Map<String, String> SUB = new LinkedHashMap<>();
    static {
        for (String[] e : new String[][]{
            // City-level word substitutions
            {"München",   "Munich"},
            {"Zürich",    "Zurich"},
            {"Köln",      "Koeln"},
            {"São Paulo", "Sao Paulo"},
            {"Genève",    "Geneva"},
            {"Genf",           "Geneva"},
            // German umlauts (Ä Ö Ü ä ö ü ß)
            {"Ä", "Ae"}, {"ä", "ae"},
            {"Ö", "Oe"}, {"ö", "oe"},
            {"Ü", "Ue"}, {"ü", "ue"},
            {"ß", "ss"},
            // A-variants (À Á Â Ã Å / à á â ã å)
            {"À", "A"}, {"Á", "A"}, {"Â", "A"},
            {"Ã", "A"}, {"Å", "A"},
            {"à", "a"}, {"á", "a"}, {"â", "a"},
            {"ã", "a"}, {"å", "a"},
            // E-variants (È É Ê Ë / è é ê ë)
            {"È", "E"}, {"É", "E"}, {"Ê", "E"}, {"Ë", "E"},
            {"è", "e"}, {"é", "e"}, {"ê", "e"}, {"ë", "e"},
            // I-variants (Î Ï Í / î ï í)
            {"Î", "I"}, {"Ï", "I"}, {"Í", "I"},
            {"î", "i"}, {"ï", "i"}, {"í", "i"},
            // O-variants (Ó Õ Ø Ő / ô õ ø ő)
            {"Ó", "O"}, {"Õ", "O"}, {"Ø", "O"}, {"Ő", "Oe"},
            {"ô", "o"}, {"õ", "o"}, {"ø", "o"}, {"ő", "oe"},
            // U-variants (Ù Ú Û / ù ú û)
            {"Ù", "U"}, {"Ú", "U"}, {"Û", "U"},
            {"ù", "u"}, {"ú", "u"}, {"û", "u"},
            // N-tilde (Ñ / ñ)
            {"Ñ", "N"}, {"ñ", "n"},
            // C-cedilla (Ç / ç)
            {"Ç", "C"}, {"ç", "c"},
            // Y-acute (Ý / ý)
            {"Ý", "Y"}, {"ý", "y"},
            // AE-ligature (Æ / æ)
            {"Æ", "Ae"}, {"æ", "ae"},
            // S-caron (Š / š)
            {"Š", "S"}, {"š", "s"},
            // Z-caron (Ž / ž)
            {"Ž", "Z"}, {"ž", "z"},
            // C-caron (Č / č)
            {"Č", "C"}, {"č", "c"},
            // G-breve (Ğ / ğ)
            {"Ğ", "G"}, {"ğ", "g"},
            // I-dotted (İ) / dotless-i (ı)
            {"İ", "I"}, {"ı", "i"},
            // Thorn (Þ / þ)
            {"Þ", "Th"}, {"þ", "th"},
            // Eth (Ð / ð)
            {"Ð", "D"}, {"ð", "d"},
        }) SUB.put(e[0], e[1]);
    }

    public String preprocess(String s) {
        if (s == null) return "";
        s = s.trim();
        // Normalize various Unicode space characters to ASCII space
        s = s.replace(' ', ' ')  // NO-BREAK SPACE
             .replace(' ', ' ')  // FIGURE SPACE
             .replace(' ', ' ')  // NARROW NO-BREAK SPACE
             .replace('　', ' '); // IDEOGRAPHIC SPACE
        // Normalize Unicode dashes to ASCII hyphen
        s = s.replace('‐', '-')  // HYPHEN
             .replace('–', '-')  // EN DASH
             .replace('—', '-')  // EM DASH
             .replace('―', '-'); // HORIZONTAL BAR
        // Normalize Unicode quotation marks to ASCII equivalents
        s = s.replace('‘', '\'') // LEFT SINGLE QUOTATION MARK
             .replace('’', '\'') // RIGHT SINGLE QUOTATION MARK
             .replace('“', '"')  // LEFT DOUBLE QUOTATION MARK
             .replace('”', '"'); // RIGHT DOUBLE QUOTATION MARK
        // HTML entities
        s = s.replace("&amp;",  "&").replace("&nbsp;", " ")
             .replace("&lt;",   "<").replace("&gt;",   ">");
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
    }

    public String toSwiftAscii(String s) {
        if (s == null) return "";
        s = s.trim();
        for (var e : SUB.entrySet()) s = s.replace(e.getKey(), e.getValue());
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        s = SWIFT_UNSAFE.matcher(s).replaceAll(" ");
        return s.trim().replaceAll("\\s{2,}", " ").toUpperCase();
    }

    public NormalizationResult toSwiftAsciiWithHints(String raw) {
        NormalizationHints hints = NormalizationHints.scan(raw);
        return new NormalizationResult(toSwiftAscii(raw), hints);
    }

    public NormalizationResult toSwiftAsciiLines(List<String> rawLines) {
        NormalizationHints hints = NormalizationHints.scanAll(rawLines);
        List<String> normalized = rawLines.stream()
                .map(this::toSwiftAscii)
                .filter(s -> !s.isBlank())
                .toList();
        String joined = String.join("\n", normalized);
        return new NormalizationResult(joined, hints);
    }

    public String toAsciiSafe(String s) {
        if (s == null) return "";
        s = s.trim();
        for (var e : SUB.entrySet()) s = s.replace(e.getKey(), e.getValue());
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim();
    }
}
