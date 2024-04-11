/*
 * Copyright © 2024 Andrew Aylett
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
package com.squareup.javapoet.notation;

import com.squareup.javapoet.TypeName;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NoImport extends Notation {
  private final Notation inner;
  @SuppressWarnings("FieldCanBeLocal") // Useful for debugging
  private final Set<String> suppressed;

  public NoImport(@NotNull Notation inner) {
    this(inner, inner.imports.stream().map(TypeName::canonicalName).collect(
        Collectors.toSet()));
  }

  public NoImport(@NotNull Notation inner, @NotNull Set<String> suppress) {
    super(
        Stream
            .concat(
                inner.names.entrySet().stream(),
                inner.imports
                    .stream()
                    .filter(t -> suppress.contains(t.canonicalName()) || suppress.contains(t.nameWhenImported()))
                    .map(t -> Map.entry(t, t.canonicalName()))
            )
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                )),
        inner.imports
            .stream()
            .filter(t -> !(suppress.contains(t.canonicalName()) || suppress.contains(t.nameWhenImported())))
            .collect(
                Collectors.toSet()),
        inner.childContexts
    );
    this.inner = inner;
    this.suppressed = suppress;
  }

  @Override
  public boolean isEmpty() {
    return inner.isEmpty();
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) {
    printer.push(chunk.withNotation(inner));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    flatVisitor.push(chunk.withNotation(inner));
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
    visitor.enter(this);
    inner.visit(visitor);
    visitor.exit(this);
  }

  @Override
  public Notation toNotation() {
    return Notate.fnLike("NoImport", List.of(inner));
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof NoImport flat) {
      return Objects.equals(inner, flat.inner);
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(inner);
  }
}
