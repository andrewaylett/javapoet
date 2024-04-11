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

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TypeRef extends Notation {

  private final TypeName ref;

  public TypeRef(TypeName ref) {
    super(Map.of(), Set.of(ref), Set.of());
    this.ref = ref;
  }

  @Override
  public Notation toNotation() {
    return txt("TypeRef(" + ref.nameWhenImported() + ")");
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) throws IOException {
    printer.append(chunk.getName(ref));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    return flatVisitor.fitText(chunk.getName(ref));
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof TypeRef typeRef) {
      return Objects.equals(ref, typeRef.ref) && Objects.equals(
          imports,
          typeRef.imports
      );
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(ref, imports);
  }
}
