/*
 * Copyright (C) 2015 Square, Inc.
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

import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.txt;
import static java.lang.Character.isISOControl;

/**
 * Like Guava, but worse and standalone. This makes it easier to mix JavaPoet with libraries that
 * bring their own version of Guava.
 */
final class Util {
  private Util() {
  }

  static <K, V> Map<K, List<V>> immutableMultimap(Map<K, List<V>> multimap) {
    var result = new LinkedHashMap<K, List<V>>();
    for (var entry : multimap.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      result.put(entry.getKey(), immutableList(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  static <K, V> Map<K, V> immutableMap(Map<K, V> map) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(map));
  }

  static void checkArgument(
      boolean condition, @PrintFormat String format, Object... args
  ) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(format, args));
    }
  }

  @Contract("!null, _, _ -> param1; null, _, _ -> fail")
  static <T> @NotNull T checkNotNull(
      T reference, @PrintFormat String format, Object... args
  ) {
    if (reference == null) {
      throw new NullPointerException(String.format(format, args));
    }
    return reference;
  }

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

  static <T> Set<T> union(Set<T> a, Set<T> b) {
    Set<T> result = new LinkedHashSet<>();
    result.addAll(a);
    result.addAll(b);
    return result;
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
    switch (c) {
      case '\b':
        return "\\b"; /* \\u0008: backspace (BS) */
      case '\t':
        return "\\t"; /* \\u0009: horizontal tab (HT) */
      case '\n':
        return "\\n"; /* \\u000a: linefeed (LF) */
      case '\f':
        return "\\f"; /* \\u000c: form feed (FF) */
      case '\r':
        return "\\r"; /* \\u000d: carriage return (CR) */
      case '\"':
        return "\"";  /* \\u0022: double quote (") */
      case '\'':
        return "\\'"; /* \\u0027: single quote (') */
      case '\\':
        return "\\\\";  /* \\u005c: backslash (\) */
      default:
        return isISOControl(c)
            ? String.format("\\u%04x", (int) c)
            : Character.toString(c);
    }
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
      // default case: just let character literal do its work
      result.append(characterLiteralWithoutSingleQuotes(c));
      // need to append indent after linefeed?
      if (c == '\n' && i + 1 < value.length()) {
        lines.add(txt(result.toString()));
        result.setLength(0);
      }
    }
    lines.add(txt(result.toString()));
    if (lines.size() == 1) {
      return txt("\"").then(lines.get(0)).then(txt("\""));
    }
    return Stream.of(
        txt("\"").then(lines.get(0)),
        lines
            .subList(1, lines.size())
            .stream()
            .collect(join(txt("\"\n+ \"")))
            .indent()
            .indent()
    ).collect(join(txt("\"\n+ \""))).then(txt("\""));
  }
}
