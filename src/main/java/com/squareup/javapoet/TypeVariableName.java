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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeVariable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.txt;
import static com.squareup.javapoet.notation.Notation.typeRef;

public final class TypeVariableName extends ObjectTypeName {
  private static final ThreadLocal<Boolean> guard =
      ThreadLocal.withInitial(() -> Boolean.FALSE);
  public final String name;
  public final List<TypeName> bounds;

  private TypeVariableName(String name, List<TypeName> bounds) {
    super();
    this.name = checkNotNull(name, "name == null");
    this.bounds = bounds;

    for (var bound : this.bounds) {
      checkArgument(
          !bound.isPrimitive() && bound != PrimitiveType.Void,
          "invalid bound: %s",
          bound
      );
    }
  }

  private static TypeVariableName of(String name, List<TypeName> bounds) {
    // Strip java.lang.Object from bounds if it is present.
    List<TypeName> boundsNoObject = new ArrayList<>(bounds);
    boundsNoObject.remove(ClassName.OBJECT);
    return new TypeVariableName(
        name,
        Collections.unmodifiableList(boundsNoObject)
    );
  }

  /**
   * Returns type variable named {@code name} without bounds.
   */
  public static TypeVariableName get(String name) {
    return TypeVariableName.of(name, Collections.emptyList());
  }

  /**
   * Returns type variable named {@code name} with {@code bounds}.
   */
  public static TypeVariableName get(String name, ObjectTypeName... bounds) {
    return TypeVariableName.of(name, Arrays.asList(bounds));
  }

  /**
   * Returns type variable named {@code name} with {@code bounds}.
   */
  public static TypeVariableName get(String name, Type... bounds) {
    return TypeVariableName.of(name, TypeName.list(bounds));
  }

  /**
   * Returns type variable equivalent to {@code mirror}.
   */
  public static TypeVariableName get(TypeVariable mirror) {
    return get((TypeParameterElement) mirror.asElement());
  }

  /**
   * Make a TypeVariableName for the given TypeMirror. This form is used internally to avoid
   * infinite recursion in cases like {@code Enum<E extends Enum<E>>}. When we encounter such a
   * thing, we will make a TypeVariableName without bounds and add that to the {@code typeVariables}
   * map before looking up the bounds. Then if we encounter this TypeVariable again while
   * constructing the bounds, we can just return it from the map. And, the code that put the entry
   * in {@code variables} will make sure that the bounds are filled in before returning.
   */
  static TypeVariableName get(
      TypeVariable mirror,
      Map<TypeParameterElement, TypeVariableName> typeVariables
  ) {
    var element = (TypeParameterElement) mirror.asElement();
    var typeVariableName = typeVariables.get(element);
    if (typeVariableName == null) {
      // Since the bounds field is public, we need to make it an unmodifiableList. But we control
      // the List that that wraps, which means we can change it before returning.
      List<TypeName> bounds = new ArrayList<>();
      var visibleBounds = Collections.unmodifiableList(bounds);
      typeVariableName = new TypeVariableName(
          element.getSimpleName().toString(),
          visibleBounds
      );
      typeVariables.put(element, typeVariableName);
      for (var typeMirror : element.getBounds()) {
        bounds.add(TypeName.get(typeMirror, typeVariables));
      }
      bounds.remove(ClassName.OBJECT);
    }
    return typeVariableName;
  }

  /**
   * Returns type variable equivalent to {@code element}.
   */
  public static TypeVariableName get(TypeParameterElement element) {
    var name = element.getSimpleName().toString();
    var boundsMirrors = element.getBounds();

    List<TypeName> boundsTypeNames = new ArrayList<>();
    for (var typeMirror : boundsMirrors) {
      boundsTypeNames.add(TypeName.get(typeMirror));
    }

    return TypeVariableName.of(name, boundsTypeNames);
  }

  /**
   * Returns type variable equivalent to {@code type}.
   */
  public static TypeVariableName get(java.lang.reflect.TypeVariable<?> type) {
    return get(type, new LinkedHashMap<>());
  }

  /**
   * See {@link #get(java.lang.reflect.TypeVariable)}.
   */
  static TypeVariableName get(
      java.lang.reflect.TypeVariable<?> type,
      Map<Type, TypeVariableName> map
  ) {
    var result = map.get(type);
    if (result == null) {
      List<TypeName> bounds = new ArrayList<>();
      var visibleBounds = Collections.unmodifiableList(bounds);
      result = new TypeVariableName(type.getName(), visibleBounds);
      map.put(type, result);
      for (var bound : type.getBounds()) {
        bounds.add(TypeName.get(bound, map));
      }
      bounds.remove(ClassName.OBJECT);
    }
    return result;
  }

  @Override
  public @NotNull TypeVariableName withoutAnnotations() {
    return this;
  }

  @Override
  public boolean isBoxedPrimitive() {
    return false;
  }

  @Override
  public @NotNull TypeName unbox() {
    throw new UnsupportedOperationException("Cannot unbox " + this);
  }

  @Override
  public TypeVariableName withBounds(Type... bounds) {
    return withBounds(TypeName.list(bounds));
  }

  @Override
  public TypeVariableName withBounds(TypeName... bounds) {
    return withBounds(Arrays.asList(bounds));
  }

  @Override
  public TypeVariableName withBounds(List<? extends TypeName> bounds) {
    var newBounds = new ArrayList<TypeName>();
    newBounds.addAll(this.bounds);
    newBounds.addAll(bounds);
    return new TypeVariableName(name, newBounds);
  }

  @Nonnull
  @Override
  public @NotNull String nameWhenImported() {
    return name;
  }

  @Override
  public @NotNull String canonicalName() {
    return name;
  }

  @Override
  public @NotNull ClassName topLevelClassName() {
    throw new UnsupportedOperationException(
        "Does not make sense for type variable");
  }

  @Override
  public @NotNull String reflectionName() {
    throw new UnsupportedOperationException(
        "Does not make sense for type variable");
  }

  @Override
  public @Nullable TypeName enclosingClassName() {
    throw new UnsupportedOperationException(
        "Does not make sense for type variable");
  }

  @Override
  public List<String> simpleNames() {
    throw new UnsupportedOperationException(
        "Does not make sense for type variable");
  }

  @Override
  public @NotNull TypeName nestedClass(@NotNull String name) {
    throw new UnsupportedOperationException(
        "Cannot nest class inside type variable");
  }

  @Override
  public @NotNull TypeName nestedClass(
      @NotNull String name,
      @NotNull List<TypeName> typeArguments
  ) {
    throw new UnsupportedOperationException(
        "Cannot nest class inside type variable");
  }

  @Override
  public @NotNull CodeWriter emit(@NotNull CodeWriter out) throws IOException {
//    emitAnnotations(out);
    return out.emitAndIndent(name);
  }

  @Override
  public String toString() {
    return toNotation().toCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TypeVariableName that = (TypeVariableName) o;
    return Objects.equals(name, that.name) && Objects.equals(
        bounds,
        that.bounds
    );
  }

  @Override
  public int hashCode() {
    if (guard.get()) {
      return Objects.hash(name);
    }
    try {
      guard.set(Boolean.TRUE);
      return Objects.hash(name, bounds);
    } finally {
      guard.set(Boolean.FALSE);
    }
  }

  @Override
  public Notation toNotation() {
    var builder = Stream.<Notation>builder();
    builder.add(Notation.typeRef(this));
    builder.add(bounds
        .stream()
        .map(b -> txt("extends ").then(typeRef(b)))
        .collect(join(txt(" & "))));
    return builder.build().filter(n -> !n.isEmpty()).collect(join(txt(" ")));
  }
}
