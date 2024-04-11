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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.squareup.javapoet.Util.checkNotNull;

public final class ArrayTypeName extends ObjectTypeName {
  public final TypeName componentType;

  public ArrayTypeName(TypeName componentType) {
    super();
    this.componentType = checkNotNull(componentType, "rawType == null");
  }

  /**
   * Returns an array type whose elements are all instances of {@code componentType}.
   */
  public static ArrayTypeName of(TypeName componentType) {
    return new ArrayTypeName(componentType);
  }

  /**
   * Returns an array type whose elements are all instances of {@code componentType}.
   */
  public static ArrayTypeName of(Type componentType) {
    return of(TypeName.get(componentType));
  }

  /**
   * Returns an array type equivalent to {@code mirror}.
   */
  public static ArrayTypeName get(ArrayType mirror) {
    return get(mirror, new LinkedHashMap<>());
  }

  static ArrayTypeName get(
      ArrayType mirror,
      Map<TypeParameterElement, TypeVariableName> typeVariables
  ) {
    return new ArrayTypeName(TypeName.get(
        mirror.getComponentType(),
        typeVariables
    ));
  }

  /**
   * Returns an array type equivalent to {@code type}.
   */
  public static ArrayTypeName get(GenericArrayType type) {
    return get(type, new LinkedHashMap<>());
  }

  static ArrayTypeName get(
      GenericArrayType type,
      Map<Type, TypeVariableName> map
  ) {
    return ArrayTypeName.of(TypeName.get(type.getGenericComponentType(), map));
  }

  @Override
  public boolean isBoxedPrimitive() {
    return false;
  }

  @Override
  public @NotNull PrimitiveType unbox() {
    throw new UnsupportedOperationException("Cannot unbox an array");
  }

  @Override
  public @NotNull TypeName nestedClass(@NotNull String name) {
    throw new UnsupportedOperationException("Cannot nest class inside array");
  }

  @Override
  public @NotNull TypeName nestedClass(
      @NotNull String name,
      @NotNull List<TypeName> typeArguments
  ) {
    throw new UnsupportedOperationException("Cannot nest class inside array");
  }

  @Override
  public TypeName withBounds(List<? extends TypeName> bounds) {
    throw new UnsupportedOperationException("Cannot add bounds to array type");
  }

  @Override
  public @NotNull String nameWhenImported() {
    return componentType.nameWhenImported() + "[]";
  }

  @Override
  public @NotNull String canonicalName() {
    return componentType.canonicalName() + "[]";
  }

  @Override
  public @NotNull ClassName topLevelClassName() {
    return componentType.topLevelClassName();
  }

  @Override
  public @NotNull String reflectionName() {
    return componentType.reflectionName() + "[]";
  }

  @Override
  public @Nullable TypeName enclosingClassName() {
    return componentType.enclosingClassName();
  }

  @Override
  public List<String> simpleNames() {
    return componentType.simpleNames();
  }

  @Override
  public @NotNull String simpleName() {
    return componentType.simpleName();
  }

  @Override
  public @NotNull Notation toNotation() {
    return componentType.toNotation().then(Notation.txt("[]"));
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
    var that = (ArrayTypeName) o;
    return Objects.equals(componentType, that.componentType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(componentType);
  }
}
