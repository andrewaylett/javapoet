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

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Choice extends Notation {
  public final Notation left;
  public final Notation right;

  public Choice(Notation left, Notation right) {
    super(
        Stream
            .concat(
                left.names.entrySet().stream(),
                right.names.entrySet().stream()
            )
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> {
                  if (Objects.equals(a, b)) {
                    return a;
                  }
                  throw new IllegalStateException(
                      "Cannot have the same tag pointing to both " + a + " and "
                          + b);
                }
            )),
        Stream
            .concat(left.imports.stream(), right.imports.stream())
            .collect(Collectors.toSet()),
        Stream
            .concat(left.childContexts.stream(), right.childContexts.stream())
            .collect(Collectors.toSet())
    );
    this.left = left;
    this.right = right;
  }

  @Override
  public Notation toNotation() {
    if (left instanceof Flat f && f.inner.equals(right)) {
      return Notate.fnLike("FlatChoice", List.of(right));
    }
    return Notate.fnLike("Choice", List.of(left, right));
  }

  @Override
  public boolean isEmpty() {
    return left.isEmpty() && right.isEmpty();
  }

  @Override
  public void visit(
      Printer.@NotNull PrinterVisitor printer, @NotNull Chunk chunk
  ) {
    if (chunk.flat || printer.fits(chunk.withNotation(left))) {
      printer.push(chunk.withNotation(left));
    } else {
      printer.push(chunk.withNotation(right));
    }
  }

  @Override
  public Printer.@NotNull FlatResponse visit(
      Printer.@NotNull FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    if (chunk.flat) {
      flatVisitor.push(chunk.withNotation(left));
    } else {
      flatVisitor.push(chunk.withNotation(right));
    }
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
    visitor.enter(this);
    left.visit(visitor);
    right.visit(visitor);
    visitor.exit(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Choice choice) {
      return Objects.equals(left, choice.left) && Objects.equals(
          right,
          choice.right
      );
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(left, right);
  }
}
