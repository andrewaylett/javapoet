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
import com.google.errorprone.annotations.Immutable;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.Emitable;
import com.squareup.javapoet.prioritymap.HashPriorityMap;
import com.squareup.javapoet.prioritymap.ImmutablePriorityMap;
import com.squareup.javapoet.prioritymap.PriorityMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@Immutable
public class Chunk {
  public final boolean flat;
  public final String indent;
  public final Notation notation;
  public final ImmutablePriorityMap<Emitable, String> names;
  public final String indentBy;
  public final ImmutableList<Scope> scopes;
  public final String packageName;

  @Contract(pure = true)
  public Chunk(
      Notation notation,
      String indent,
      boolean flat,
      PriorityMap<Emitable, String> names,
      String indentBy,
      List<Scope> scopes,
      String packageName
  ) {
    this.notation = notation;
    this.indent = indent;
    this.flat = flat;
    this.names = names.immutableCopy();
    this.indentBy = indentBy;
    this.scopes = ImmutableList.copyOf(scopes);
    this.packageName = packageName;
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk withNotation(Notation notation) {
    return new Chunk(notation,
        this.indent,
        this.flat,
        names,
        indentBy,
        scopes,
        packageName
    );
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk indented(String indent) {
    return new Chunk(this.notation,
        this.indent + indent,
        this.flat,
        names,
        indentBy,
        scopes,
        packageName
    );
  }

  @Contract(value = "-> new", pure = true)
  public Chunk flat() {
    return new Chunk(this.notation,
        this.indent,
        true,
        names,
        indentBy,
        scopes,
        packageName
    );
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk inScope(Scope scope) {
    return new Chunk(this.notation,
        this.indent,
        flat,
        names,
        indentBy,
        Stream.concat(scopes.stream(), Stream.of(scope)).toList(),
        packageName
    );
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk names(HashPriorityMap<Emitable, String> newNames) {
    return new Chunk(this.notation,
        this.indent,
        flat,
        newNames,
        indentBy,
        scopes,
        packageName
    );
  }

  public void visit(Printer.PrinterVisitor printer) throws IOException {
    notation.visit(printer, this);
  }

  public Printer.FlatResponse visit(Printer.FlatVisitor flatVisitor) {
    return notation.visit(flatVisitor, this);
  }

  @Contract(pure = true)
  public @NotNull String getName(Emitable tag) {
    return requireNonNull(names.get(tag));
  }

  public @NotNull Set<Emitable> getTags() {
    return names.keySet();
  }

  @Immutable
  public record Scope(Context context, ClassName className) {}
}
