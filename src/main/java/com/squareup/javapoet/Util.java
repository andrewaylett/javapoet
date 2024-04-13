/*
 * Copyright © 2015 Square, Inc., 2024 Andrew Aylett
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.javapoet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.FormatMethod;
import com.squareup.javapoet.notation.Notation;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.squareup.javapoet.notation.Notation.asLines;
import static com.squareup.javapoet.notation.Notation.txt;
import static java.lang.Character.isISOControl;

/**
 * Like Guava, but worse and standalone. This makes it easier to mix JavaPoet with libraries that
 * bring their own version of Guava.
 */
public final class Util {
  private Util() {
  }

  static <K, V> ImmutableMap<K, ImmutableList<V>> immutableMultimap(Map<K, List<V>> multimap) {
    var result = new LinkedHashMap<K, ImmutableList<V>>();
    for (var entry : multimap.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      result.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
    }
    return ImmutableMap.copyOf(result);
  }

  @FormatMethod
  static void checkArgument(
      boolean condition, @PrintFormat String format, Object... args
  ) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(format, args));
    }
  }

  @Contract("!null, _, _ -> param1; null, _, _ -> fail")
  @FormatMethod
  static <T> @NotNull T checkNotNull(
      T reference, @PrintFormat String format, Object... args
  ) {
    if (reference == null) {
      throw new NullPointerException(String.format(format, args));
    }
    return reference;
  }

  @FormatMethod
  static void checkState(
      boolean condition, @PrintFormat String format, Object... args
  ) {
    if (!condition) {
      throw new IllegalStateException(String.format(format, args));
    }
  }

  static <T> List<T> immutableList(Collection<T> collection) {
    //noinspection Java9CollectionFactory
    return Collections.unmodifiableList(new ArrayList<>(collection));
  }

  static <T> Set<T> immutableSet(Collection<T> set) {
    return Collections.unmodifiableSet(new LinkedHashSet<>(set));
  }

  static void requireExactlyOneOf(
      Set<Modifier> modifiers, Modifier... mutuallyExclusive
  ) {
    var count = 0;
    for (var modifier : mutuallyExclusive) {
      if (modifiers.contains(modifier)) {
        count++;
      }
    }
    checkArgument(
        count == 1,
        "modifiers %s must contain one of %s",
        modifiers,
        Arrays.toString(mutuallyExclusive)
    );
  }

  static String characterLiteralWithoutSingleQuotes(char c) {
    // see https://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10.6
    return switch (c) {
      case '\b' -> "\\b"; /* \\u0008: backspace (BS) */
      case '\t' -> "\\t"; /* \\u0009: horizontal tab (HT) */
      case '\n' -> "\\n"; /* \\u000a: linefeed (LF) */
      case '\f' -> "\\f"; /* \\u000c: form feed (FF) */
      case '\r' -> "\\r"; /* \\u000d: carriage return (CR) */
      case '\"' -> "\"";  /* \\u0022: double quote (") */
      case '\'' -> "\\'"; /* \\u0027: single quote (') */
      case '\\' -> "\\\\";  /* \\u005c: backslash (\) */
      default -> isISOControl(c)
          ? String.format("\\u%04x", (int) c)
          : Character.toString(c);
    };
  }

  /**
   * Returns the notation representing {@code value},
   * including wrapping double quotes.
   */
  static Notation stringLiteralWithDoubleQuotes(String value) {
    var lines = new ArrayList<Notation>();
    var result = new StringBuilder(value.length() + 2);

    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      // trivial case: single quote must not be escaped
      if (c == '\'') {
        result.append("'");
        continue;
      }
      // trivial case: double quotes must be escaped
      if (c == '\"') {
        result.append("\\\"");
        continue;
      }
      // need to append indent after linefeed?
      if (c == '\n') {
        if (i + 1 < value.length()) {
          lines.add(txt(result.toString()));
          result.setLength(0);
        }
        continue;
      }
      // default case: just let character literal do its work
      result.append(characterLiteralWithoutSingleQuotes(c));
    }
    lines.add(txt(result.toString()));
    if (lines.size() == 1) {
      return txt("\"").then(lines.get(0)).then(txt("\""));
    }
    var builder = Stream.<Notation>builder();
    builder.add(txt("\"\"\""));
    lines.forEach(builder);
    builder.add(txt("\"\"\""));
    return builder.build().collect(asLines()).indent().indent();
  }
}
