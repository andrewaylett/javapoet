/*
 * Copyright (C) 2015 Square, Inc.
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

import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;

/**
 * A generated parameter declaration.
 */
public final class ParameterSpec implements Emitable {
  public final String name;
  public final List<AnnotationSpec> annotations;
  public final Set<Modifier> modifiers;
  public final TypeName type;
  public final CodeBlock javadoc;

  private ParameterSpec(Builder builder) {
    this.name = checkNotNull(builder.name, "name == null");
    this.annotations = Util.immutableList(builder.annotations);
    this.modifiers = Util.immutableSet(builder.modifiers);
    this.type = checkNotNull(builder.type, "type == null");
    this.javadoc = builder.javadoc.build();
  }

  public static ParameterSpec get(VariableElement element) {
    checkArgument(
        element.getKind().equals(ElementKind.PARAMETER),
        "element is not a parameter"
    );

    var type = TypeName.get(element.asType());
    var name = element.getSimpleName().toString();
    // Copying parameter annotations can be incorrect so we're deliberately not including them.
    // See https://github.com/square/javapoet/issues/482.
    return ParameterSpec
        .builder(type, name)
        .addModifiers(element.getModifiers())
        .build();
  }

  static List<ParameterSpec> parametersOf(ExecutableElement method) {
    List<ParameterSpec> result = new ArrayList<>();
    for (var parameter : method.getParameters()) {
      result.add(ParameterSpec.get(parameter));
    }
    return result;
  }

  private static boolean isValidParameterName(String name) {
    // Allow "this" for explicit receiver parameters
    // See https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.4.1.
    if (name.endsWith(".this")) {
      return SourceVersion.isIdentifier(name.substring(0,
          name.length() - ".this".length()
      ));
    }
    return name.equals("this") || SourceVersion.isName(name);
  }

  public static Builder builder(
      TypeName type, String name, Modifier... modifiers
  ) {
    checkNotNull(type, "type == null");
    checkArgument(isValidParameterName(name), "not a valid name: %s", name);
    return new Builder(type, name).addModifiers(modifiers);
  }

  public static Builder builder(Type type, String name, Modifier... modifiers) {
    return builder(TypeName.get(type), name, modifiers);
  }

  public boolean hasModifier(Modifier modifier) {
    return modifiers.contains(modifier);
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof ParameterSpec that) {
      return Objects.equals(name, that.name) && Objects.equals(annotations,
          that.annotations
      ) && Objects.equals(modifiers, that.modifiers) && Objects.equals(
          type,
          that.type
      ) && Objects.equals(javadoc, that.javadoc);
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(name, annotations, modifiers, type, javadoc);
  }

  @Override
  public String toString() {
    return toNotation().toCode();
  }

  public Builder toBuilder() {
    return toBuilder(type, name);
  }

  Builder toBuilder(TypeName type, String name) {
    var builder = new Builder(type, name);
    builder.annotations.addAll(annotations);
    builder.modifiers.addAll(modifiers);
    return builder;
  }

  @Override
  public Notation toNotation() {
    var builder = Stream.<Notation>builder();
    annotations.stream().map(Emitable::toNotation).forEach(builder);
    modifiers
        .stream()
        .map(Notation::literal)
        .forEach(builder);
    Stream.of(type.toNotation(), Notation.name(this, name)).forEach(builder);

    return builder
        .build()
        .collect(Notation.join(Notation.txt(" ").or(Notation.nl())));
  }

  public Notation toNotation(boolean varargs) {
    if (!varargs) {
      return toNotation();
    }
    var componentType = TypeName.arrayComponent(type);
    if (componentType == null) {
      throw new IllegalStateException("Varargs but not an array type");
    }
    var builder = Stream.<Notation>builder();
    annotations.stream().map(Emitable::toNotation).forEach(builder);
    modifiers
        .stream()
        .map(Notation::literal)
        .forEach(builder);
    Stream.of(componentType.toNotation().then(Notation.txt("...")),
        Notation.name(this, name)
    ).forEach(builder);

    return builder
        .build()
        .collect(Notation.join(Notation.txt(" ").or(Notation.nl())));
  }

  public static final class Builder {
    public final List<AnnotationSpec> annotations = new ArrayList<>();
    public final List<Modifier> modifiers = new ArrayList<>();
    private final TypeName type;
    private final String name;
    private final CodeBlock.Builder javadoc = CodeBlock.builder();

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

    public Builder addModifiers(Iterable<Modifier> modifiers) {
      checkNotNull(modifiers, "modifiers == null");
      for (var modifier : modifiers) {
        if (!modifier.equals(Modifier.FINAL)) {
          throw new IllegalStateException(
              "unexpected parameter modifier: " + modifier);
        }
        this.modifiers.add(modifier);
      }
      return this;
    }

    public ParameterSpec build() {
      return new ParameterSpec(this);
    }
  }
}
