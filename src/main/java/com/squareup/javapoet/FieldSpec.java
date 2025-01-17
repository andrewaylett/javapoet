/*
 * Copyright © 2015 Square, Inc., 2024 Andrew Aylett
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.squareup.javapoet.notation.Notate;
import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.Util.checkState;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;

/**
 * A generated field declaration.
 */
@Immutable
public final class FieldSpec implements Emitable {
  public final TypeName type;
  public final String name;
  public final CodeBlock javadoc;
  public final ImmutableList<AnnotationSpec> annotations;
  public final ImmutableSet<Modifier> modifiers;
  public final CodeBlock initializer;

  private FieldSpec(Builder builder) {
    this.type = checkNotNull(builder.type, "type == null");
    this.name = checkNotNull(builder.name, "name == null");
    this.javadoc = builder.javadoc.build();
    this.annotations = ImmutableList.copyOf(builder.annotations);
    this.modifiers = ImmutableSet.copyOf(builder.modifiers);
    this.initializer = (builder.initializer == null)
        ? CodeBlock.builder().build()
        : builder.initializer;
  }

  public static Builder builder(
      TypeName type,
      String name,
      Modifier... modifiers
  ) {
    checkNotNull(type, "type == null");
    checkArgument(SourceVersion.isName(name), "not a valid name: %s", name);
    return new Builder(type, name).addModifiers(modifiers);
  }

  public static Builder builder(Type type, String name, Modifier... modifiers) {
    return builder(TypeName.get(type), name, modifiers);
  }

  public boolean hasModifier(Modifier modifier) {
    return modifiers.contains(modifier);
  }

  @Override
  public @NotNull Notation toNotation() {
    return toNotation(Set.of());
  }

  public Notation toNotation(Set<Modifier> implicitModifiers) {
    var context = Stream.<Notation>builder();
    context.add(Notate.javadoc(javadoc.toNotation().suppressImports()));
    annotations.stream().map(Emitable::toNotation).forEach(context);

    var decl = Stream.<Notation>builder();
    decl.add(modifiers.stream()
        .filter(m -> !implicitModifiers.contains(m))
        .map(Notation::literal)
        .collect(join(txt(" "))));
    decl.add(type.toNotation());
    decl.add(Notation
        .literal(name)
        .then(initializer.isEmpty() ? txt(";") : empty()));

    Notation declaration;
    if (!initializer.isEmpty()) {
      decl.add(txt("= "));
      declaration = decl.build().filter(d -> !d.isEmpty()).collect(join(txt(" ").or(nl()))).indent().then(
          initializer.toNotation(true).then(txt(";")));
    } else {
      declaration = decl.build().filter(d -> !d.isEmpty()).collect(join(txt(" ").or(nl())));
    }
    context.add(declaration);

    return context
        .build()
        .filter(n -> !n.isEmpty())
        .collect(Notation.asLines());
  }

  @Override
  public boolean equals(Object o) {
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

  public Builder toBuilder() {
    var builder = new Builder(type, name);
    builder.javadoc.add(javadoc);
    builder.annotations.addAll(annotations);
    builder.modifiers.addAll(modifiers);
    builder.initializer = initializer.isEmpty() ? null : initializer;
    return builder;
  }

  public static final class Builder {
    public final List<AnnotationSpec> annotations = new ArrayList<>();
    public final List<Modifier> modifiers = new ArrayList<>();
    private final TypeName type;
    private final String name;
    private final CodeBlock.Builder javadoc = CodeBlock.builder();
    private @Nullable CodeBlock initializer = null;

    private Builder(TypeName type, String name) {
      this.type = type;
      this.name = name;
    }

    public Builder addJavadoc(String format, Object... args) {
      javadoc.add(format, args);
      return this;
    }

    public Builder addJavadoc(CodeBlock block) {
      javadoc.add(block);
      return this;
    }

    public Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
      checkArgument(annotationSpecs != null, "annotationSpecs == null");
      for (var annotationSpec : annotationSpecs) {
        this.annotations.add(annotationSpec);
      }
      return this;
    }

    public Builder addAnnotation(AnnotationSpec annotationSpec) {
      this.annotations.add(annotationSpec);
      return this;
    }

    public Builder addAnnotation(ClassName annotation) {
      this.annotations.add(AnnotationSpec.builder(annotation).build());
      return this;
    }

    public Builder addAnnotation(Class<?> annotation) {
      return addAnnotation(ClassName.get(annotation));
    }

    public Builder addModifiers(Modifier... modifiers) {
      Collections.addAll(this.modifiers, modifiers);
      return this;
    }

    public Builder initializer(String format, Object... args) {
      return initializer(CodeBlock.of(format, args));
    }

    public Builder initializer(CodeBlock codeBlock) {
      checkState(this.initializer == null, "initializer was already set");
      this.initializer = checkNotNull(codeBlock, "codeBlock == null");
      return this;
    }

    public FieldSpec build() {
      return new FieldSpec(this);
    }
  }
}
