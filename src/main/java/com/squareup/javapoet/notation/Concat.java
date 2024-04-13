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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.Immutable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Immutable
public final class Concat extends Notation {
  public final @NotNull ImmutableList<Notation> content;

  @Contract(pure = true)
  public Concat(@NotNull List<Notation> content) {
    super(
        content
            .stream()
            .unordered()
            .flatMap(n -> n.names.entrySet().stream().unordered())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> {
                  if (Objects.equals(a, b)) {
                    return a;
                  }
                  throw new IllegalStateException(
                      "Cannot have the same tag pointing to both "
                          + a + " and " + b);
                }
            )),
        content
            .stream()
            .unordered()
            .flatMap(n -> n.imports.stream().unordered())
            .collect(Collectors.toSet()),
        content
            .stream()
            .unordered()
            .flatMap(n -> n.childContexts.stream().unordered())
            .collect(Collectors.toSet())
    );
    this.content = ImmutableList.copyOf(content);
  }

  @Contract(pure = true)
  @Override
  public Notation toNotation() {
    return Notate.fnLike("Concat", content);
  }

  @Contract(pure = true)
  @Override
  public boolean isEmpty() {
    return content.stream().allMatch(Notation::isEmpty);
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) {
    Lists.reverse(content).forEach(n -> printer.push(chunk.withNotation(n)));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    Lists.reverse(content).forEach(n ->
        flatVisitor.push(chunk.withNotation(n)));
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
    visitor.enter(this);
    content.forEach(n -> n.visit(visitor));
    visitor.exit(this);
  }

  @Contract(value = "null -> false", pure = true)
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Concat concat) {
      return Objects.equals(content, concat.content);
    }
    return false;
  }

  @Contract(pure = true)
  @Override
  public int hashCode() {
    return Objects.hash(content);
  }
}
