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

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Immutable
@SuppressWarnings("UnstableApiUsage")
public class NewLine extends Notation {
  public static final NewLine INSTANCE = new NewLine();

  @Contract(pure = true)
  private NewLine() {
    super(Map.of(), Set.of(), Set.of());
  }

  @Contract(pure = true)
  @Override
  public Notation toNotation() {
    return txt("\\n");
  }

  @Contract(pure = true)
  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  @Contract(mutates = "param1")
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) throws IOException {
    printer.newLine();
    printer.append(chunk.indent);
  }

  @Override
  @Contract(pure = true)
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    if (chunk.flat) {
      return Printer.FlatResponse.TOO_LONG;
    }
    return Printer.FlatResponse.FITS;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
  }

  @Contract(value = "null -> false", pure = true)
  @Override
  public boolean equals(Object o) {
    return o instanceof NewLine;
  }

  @Contract(pure = true)
  @Override
  public int hashCode() {
    return Objects.hashCode(this.getClass());
  }
}
