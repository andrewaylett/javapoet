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

import com.google.errorprone.annotations.Immutable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@Immutable
public class Statement extends Notation {
  public final Notation inner;

  public Statement(Notation inner) {
    super(inner.names, inner.imports, inner.childContexts);

    inner.visit(new Visitor() {
      int contextNestLevel = 0;
      @Override
      public void accept(Notation notation) {
        if (contextNestLevel == 0 && notation instanceof Statement) {
          throw new IllegalStateException(
              "statement enter $[ followed by statement enter $[");
        }
      }

      @Override
      public void enter(Notation n) {
        if (n instanceof Context) {
          contextNestLevel++;
        }
      }

      @Override
      public void exit(Notation n) {
        if (n instanceof Context) {
          contextNestLevel--;
        }
      }
    });

    this.inner = inner;
  }

  @Contract(pure = true)
  @Override
  public Notation toNotation() {
    return Notate.fnLike("Statement", List.of(inner));
  }

  @Contract(pure = true)
  @Override
  public boolean isEmpty() {
    return inner.isEmpty();
  }

  @Override
  public void visit(
      Printer.@NotNull PrinterVisitor printer,
      @NotNull Chunk chunk
  ) {
    if (chunk.flat || printer.fits(chunk.withNotation(inner.flat()))) {
      printer.push(chunk.withNotation(inner.flat()));
    } else {
      printer.push(chunk.withNotation(inner));
    }
  }

  @Override
  public Printer.@NotNull FlatResponse visit(
      Printer.@NotNull FlatVisitor flatVisitor,
      @NotNull Chunk chunk
  ) {
    if (chunk.flat) {
      flatVisitor.push(chunk.withNotation(inner.flat()));
    } else {
      flatVisitor.push(chunk.withNotation(inner));
    }
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
    visitor.enter(this);
    inner.visit(visitor);
    visitor.exit(this);
  }

  @Contract(value = "null -> false", pure = true)
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Statement statement) {
      return Objects.equals(inner, statement.inner);
    }
    return false;
  }

  @Contract(pure = true)
  @Override
  public int hashCode() {
    return Objects.hash(inner);
  }
}
