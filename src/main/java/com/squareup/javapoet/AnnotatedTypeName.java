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
package com.squareup.javapoet;

import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;

public final class AnnotatedTypeName implements TypeName {
  final @NotNull TypeName inner;
  final @NotNull List<AnnotationSpec> annotations;


  public AnnotatedTypeName(
      @NotNull TypeName inner,
      @NotNull Collection<AnnotationSpec> annotations
  ) {
    checkNotNull(annotations, "annotations");
    if (inner instanceof AnnotatedTypeName annotated) {
      this.inner = annotated.inner;
      this.annotations = annotated.concatAnnotations(annotations);
    } else {
      this.inner = inner;
      this.annotations = List.copyOf(annotations);
    }
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    if (getClass() != o.getClass()) {
      return false;
    }
    return toString().equals(o.toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public String toString() {
    return toNotation().toCode();
  }

  private @NotNull List<AnnotationSpec> concatAnnotations(
      @NotNull Collection<AnnotationSpec> annotations
  ) {
    List<AnnotationSpec> allAnnotations = new ArrayList<>(this.annotations);
    allAnnotations.addAll(annotations);
    return allAnnotations;
  }

  @Contract(pure = true)
  @Override
  public @NotNull TypeName withoutAnnotations() {
    return inner;
  }

  @Override
  public boolean isPrimitive() {
    return inner.isPrimitive();
  }

  @Override
  public boolean isBoxedPrimitive() {
    return inner.isBoxedPrimitive();
  }

  @Override
  public AnnotatedTypeName box() {
    return new AnnotatedTypeName(inner.box(), annotations);
  }

  @Override
  public @NotNull AnnotatedTypeName unbox() {
    return new AnnotatedTypeName(inner.unbox(), annotations);
  }

  @Override
  public @NotNull Notation toNotation() {
    return Stream.concat(
        annotations.stream().map(Emitable::toNotation),
        Stream.of(inner.toNotation())
    ).collect(join(txt(" ").or(nl())));
  }

  @Override
  public @NotNull Notation toDeclaration() {
    return Stream.concat(
        annotations.stream().map(Emitable::toDeclaration),
        Stream.of(inner.toDeclaration())
    ).collect(join(txt(" ").or(nl())));
  }

  @Override
  public @NotNull ClassName topLevelClassName() {
    return inner.topLevelClassName();
  }

  @Override
  public @NotNull String reflectionName() {
    return inner.reflectionName();
  }

  @Override
  public @Nullable TypeName enclosingClassName() {
    return inner.enclosingClassName();
  }

  @Override
  public List<String> simpleNames() {
    return inner.simpleNames();
  }

  @Override
  public @NotNull AnnotatedTypeName nestedClass(@NotNull String name) {
    return new AnnotatedTypeName(inner.nestedClass(name), annotations);
  }

  @Override
  public @NotNull TypeName nestedClass(
      @NotNull String name,
      @NotNull List<TypeName> typeArguments
  ) {
    return new AnnotatedTypeName(
        inner.nestedClass(name, typeArguments),
        annotations
    );
  }

  @Override
  public @NotNull TypeName withBounds(
      @NotNull List<? extends TypeName> bounds
  ) {
    return new AnnotatedTypeName(inner.withBounds(bounds), annotations);
  }

  @Nonnull
  @Override
  public @NotNull String nameWhenImported() {
    return inner.nameWhenImported();
  }

  @Override
  public @NotNull String canonicalName() {
    return inner.canonicalName();
  }

  @Override
  public @NotNull String simpleName() {
    return inner.simpleName();
  }
}
