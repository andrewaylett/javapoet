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

import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;

public final class ParameterizedTypeName extends ObjectTypeName {
  public final ClassName rawType;
  public final List<? extends TypeName> typeArguments;
  private final TypeName enclosingType;

  ParameterizedTypeName(
      @Nullable TypeName enclosingType, ClassName rawType,
      List<? extends TypeName> typeArguments
  ) {
    super();
    this.rawType = checkNotNull(rawType, "rawType == null");
    this.enclosingType = enclosingType;
    this.typeArguments = Util.immutableList(typeArguments);

    checkArgument(!this.typeArguments.isEmpty() || enclosingType != null,
        "no type arguments: %s", rawType
    );
    for (var typeArgument : this.typeArguments) {
      checkArgument(!(typeArgument instanceof PrimitiveType),
          "invalid type parameter: %s", typeArgument
      );
    }
  }

  /**
   * Returns a parameterized type, applying {@code typeArguments} to {@code rawType}.
   */
  public static ParameterizedTypeName get(
      ClassName rawType,
      TypeName... typeArguments
  ) {
    return new ParameterizedTypeName(
        null,
        rawType,
        Arrays.asList(typeArguments)
    );
  }

  /**
   * Returns a parameterized type, applying {@code typeArguments} to {@code rawType}.
   */
  public static ParameterizedTypeName get(
      Class<?> rawType,
      Type... typeArguments
  ) {
    return new ParameterizedTypeName(
        null,
        ClassName.get(rawType),
        TypeName.list(typeArguments)
    );
  }

  /**
   * Returns a parameterized type equivalent to {@code type}.
   */
  public static TypeName get(ParameterizedType type) {
    return get(type, new LinkedHashMap<>());
  }

  /**
   * Returns a parameterized type equivalent to {@code type}.
   */
  public static TypeName get(
      ParameterizedType type,
      Map<Type, TypeVariableName> map
  ) {
    var rawType = ClassName.get((Class<?>) type.getRawType());
    var typeArguments = TypeName.list(type.getActualTypeArguments(), map);
    if ((type.getOwnerType() instanceof ParameterizedType ownerType)
        && !Modifier.isStatic(((Class<?>) type.getRawType()).getModifiers())) {
      return get(ownerType, map).nestedClass(rawType.simpleName, typeArguments);
    } else {
      return new ParameterizedTypeName(null, rawType, typeArguments);
    }
  }

  @Contract(value = " -> this", pure = true)
  @Override
  public @NotNull ParameterizedTypeName withoutAnnotations() {
    return this;
  }

  @Contract(pure = true)
  @Override
  public boolean isBoxedPrimitive() {
    return false;
  }

  @Override
  public @NotNull TypeName unbox() {
    throw new UnsupportedOperationException("Cannot unbox " + this);
  }

  /**
   * Returns a new {@link ParameterizedTypeName} instance for the specified {@code name} as nested
   * inside this class.
   */
  @Override
  public @NotNull ClassName nestedClass(@NotNull String name) {
    checkNotNull(name, "name == null");
    return new ClassName(rawType.packageName, this, name);
  }

  @Override
  public TypeName withBounds(List<? extends TypeName> bounds) {
    return new ParameterizedTypeName(null, rawType, bounds);
  }

  @Override
  public @NotNull String nameWhenImported() {
    return rawType.nameWhenImported() + "<" + typeArguments
        .stream()
        .map(TypeName::nameWhenImported)
        .collect(Collectors.joining(", ")) + ">";
  }

  @Override
  public @NotNull String canonicalName() {
    return rawType.canonicalName() + "<" + typeArguments
        .stream()
        .map(TypeName::canonicalName)
        .collect(Collectors.joining(", ")) + ">";
  }

  @Override
  public @NotNull ClassName topLevelClassName() {
    return rawType.topLevelClassName();
  }

  @Override
  public @NotNull String reflectionName() {
    return rawType.reflectionName();
  }

  @Override
  public @Nullable TypeName enclosingClassName() {
    return enclosingType;
  }

  @Override
  public List<String> simpleNames() {
    return rawType.simpleNames();
  }

  @Override
  public @NotNull String simpleName() {
    return rawType.simpleName();
  }

  /**
   * Returns a new {@link ParameterizedTypeName} instance for the specified {@code name} as nested
   * inside this class, with the specified {@code typeArguments}.
   */
  public @NotNull TypeName nestedClass(
      @NotNull String name,
      @NotNull List<TypeName> typeArguments
  ) {
    checkNotNull(name, "name == null");
    if (typeArguments.isEmpty()) {
      return nestedClass(name);
    }
    return new ParameterizedTypeName(this, nestedClass(name), typeArguments);
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
    var that = (ParameterizedTypeName) o;
    return Objects.equals(rawType, that.rawType)
        && Objects.equals(typeArguments, that.typeArguments) && Objects.equals(
        enclosingType,
        that.enclosingType
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(rawType, typeArguments, enclosingType);
  }

  @Override
  public Notation toNotation() {
    return Stream.of(
        Notation.typeRef(this.rawType).then(txt("<")),
        typeArguments
            .stream()
            .map(Emitable::toNotation)
            .collect(join(txt(", ").or(txt(",\n")))),
        Notation.txt(">")
    ).collect(join(empty().or(nl())));
  }
}
