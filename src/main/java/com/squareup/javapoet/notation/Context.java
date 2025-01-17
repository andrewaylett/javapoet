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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.Emitable;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.prioritymap.HashPriorityMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Immutable
public class Context extends Notation {
  public final ImmutableMap<Emitable, String> childNames;
  public final ImmutableSet<Context> innerContexts;
  public final Optional<String> simpleName;
  public final Notation inner;
  private final ImmutableSet<TypeName> typeVariableNames;

  @Contract(pure = true)
  public Context(
      Optional<String> name,
      Notation inner,
      Set<TypeName> typeVariableNames
  ) {
    super(
        Map.of(),
        inner.imports
            .stream()
            .filter(n -> !tvnMatches(typeVariableNames, n))
            .collect(Collectors.toSet())
    );

    this.childNames = inner.names;
    this.innerContexts = inner.childContexts;
    this.simpleName = name;
    this.inner = inner;
    this.typeVariableNames = ImmutableSet.copyOf(typeVariableNames);
  }

  @Contract(pure = true)
  static boolean tvnMatches(Set<TypeName> set, TypeName instance) {
    var tvn = instance.withoutAnnotations();
    return set.stream().anyMatch(n -> n.withoutAnnotations().equals(tvn));
  }

  @Contract(pure = true)
  @Override
  public Notation toNotation() {
    return Notate.fnLike("Context<" + simpleName + ">", List.of(inner));
  }

  @Contract(pure = true)
  @Override
  public boolean isEmpty() {
    return inner.isEmpty();
  }

  @Override
  public void visit(
      Printer.@NotNull PrinterVisitor printer, @NotNull Chunk chunk
  ) {
    chunk = apply(chunk);
    if (chunk.flat || printer.fits(chunk.withNotation(inner.flat()))) {
      printer.push(chunk.withNotation(inner.flat()));
    } else {
      printer.push(chunk.withNotation(inner));
    }
  }

  @Override
  public Printer.@NotNull FlatResponse visit(
      Printer.@NotNull FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    chunk = apply(chunk);
    if (chunk.flat) {
      flatVisitor.push(chunk.withNotation(inner.flat()));
    } else {
      flatVisitor.push(chunk.withNotation(inner));
    }
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  private Chunk apply(Chunk chunk) {
    var className = chunk.scopes.isEmpty()
        ? ClassName.get(
        chunk.packageName,
        simpleName.orElse("Object")
    ) : simpleName
        .map(n -> chunk.scopes
            .get(chunk.scopes.size() - 1)
            .className()
            .nestedClass(n))
        .orElseGet(() -> chunk.scopes
            .get(chunk.scopes.size() - 1)
            .className());

    var scope = new Chunk.Scope(this, className);
    var scopes =
        Stream.concat(chunk.scopes.stream(), Stream.of(scope)).toList();

    var newNames = new HashPriorityMap<>(chunk.names);
    var namesInScope = new HashMap<String, Object>();

    scopes
        .stream()
        .flatMap(s -> s
            .context()
            .immediateChildContextNames()
            .map(n -> s.className().nestedClass(n)))
        .forEach(c -> namesInScope.put(c.simpleName(), c));

    scopes
        .stream()
        .flatMap(s -> s
            .context()
            .typeVariableNames.stream())
        .map(TypeName::withoutAnnotations)
        .forEach(t -> namesInScope.put(t.canonicalName(), t));

    chunk.names
        .keySet()
        .stream()
        .flatMap(o -> o instanceof ClassName c ? Stream.of(c) : Stream.of())
        .filter(c -> c
            .canonicalName()
            .startsWith(className.topLevelClassName().canonicalName()))
        .map(n -> Map.<Emitable, String>entry(
            n,
            className.referenceTo(n, namesInScope)
        ))
        .forEach(e -> {
          newNames.values().remove(e.getValue());
          newNames.entrySet().add(e);
        });

    typeVariableNames.forEach(tvn -> {
      newNames.values().remove(tvn.canonicalName());
      newNames.put(tvn.withoutAnnotations(), tvn.canonicalName());
    });

    this.childNames.forEach((k, v) -> {
      while (newNames.containsValue(v)) {
        v = v + "_";
      }
      if (!newNames.containsKey(k)) {
        newNames.put(k, v);
      }
    });

    return chunk.names(newNames).inScope(scope);
  }

  @Contract(pure = true)
  public Stream<String> allContextNames() {
    var builder = Stream.<String>builder();
    immediateChildContextNames().forEach(builder);
    innerContexts.stream().flatMap(Context::allContextNames).forEach(builder);
    return builder.build();
  }

  @Contract(pure = true)
  public Stream<String> immediateChildContextNames() {
    return Stream.concat(
        typeVariableNames
            .stream()
            .map(TypeName::canonicalName),
        innerContexts
            .stream()
            .flatMap(c -> c.simpleName.stream())
    );
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
    if (o instanceof Context statement) {
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
