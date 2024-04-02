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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;

public final class ParameterizedTypeName extends ObjectTypeName {
  public final ClassName rawType;
  public final List<? extends TypeName> typeArguments;
  private final ParameterizedTypeName enclosingType;

  ParameterizedTypeName(@Nullable ParameterizedTypeName enclosingType, ClassName rawType,
                        List<? extends TypeName> typeArguments) {
    super();
    this.rawType = checkNotNull(rawType, "rawType == null");
    this.enclosingType = enclosingType;
    this.typeArguments = Util.immutableList(typeArguments);

    checkArgument(!this.typeArguments.isEmpty() || enclosingType != null,
            "no type arguments: %s", rawType);
    for (var typeArgument : this.typeArguments) {
      checkArgument(!(typeArgument instanceof PrimitiveType),
              "invalid type parameter: %s", typeArgument);
    }
  }

  /**
   * Returns a parameterized type, applying {@code typeArguments} to {@code rawType}.
   */
  public static ParameterizedTypeName get(ClassName rawType, TypeName... typeArguments) {
    return new ParameterizedTypeName(null, rawType, Arrays.asList(typeArguments));
  }

  /**
   * Returns a parameterized type, applying {@code typeArguments} to {@code rawType}.
   */
  public static ParameterizedTypeName get(Class<?> rawType, Type... typeArguments) {
    return new ParameterizedTypeName(null, ClassName.get(rawType), TypeName.list(typeArguments));
  }

  /**
   * Returns a parameterized type equivalent to {@code type}.
   */
  public static ParameterizedTypeName get(ParameterizedType type) {
    return get(type, new LinkedHashMap<>());
  }

  /**
   * Returns a parameterized type equivalent to {@code type}.
   */
  static ParameterizedTypeName get(ParameterizedType type, Map<Type, TypeVariableName> map) {
    var rawType = ClassName.get((Class<?>) type.getRawType());
    var typeArguments = TypeName.list(type.getActualTypeArguments(), map);
    if ((type.getOwnerType() instanceof ParameterizedType ownerType)
            && !Modifier.isStatic(((Class<?>) type.getRawType()).getModifiers())) {
      return get(ownerType, map).nestedClass(rawType.simpleName(), typeArguments);
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

  @Override
  public @NotNull CodeWriter emit(@NotNull CodeWriter out) throws IOException {
    if (enclosingType != null) {
      enclosingType.emit(out);
      out.emit(".");
      out.emit(rawType.simpleName());
    } else {
      rawType.emit(out);
    }
    if (!typeArguments.isEmpty()) {
      out.emitAndIndent("<");
      var firstParameter = true;
      for (var parameter : typeArguments) {
        if (!firstParameter) out.emitAndIndent(", ");
        parameter.emit(out);
        firstParameter = false;
      }
      out.emitAndIndent(">");
    }
    return out;
  }

  /**
   * Returns a new {@link ParameterizedTypeName} instance for the specified {@code name} as nested
   * inside this class.
   */
  @Override
  public ParameterizedTypeName nestedClass(String name) {
    checkNotNull(name, "name == null");
    return new ParameterizedTypeName(this, rawType.nestedClass(name), new ArrayList<>());
  }

  @Override
  public TypeName withBounds(List<? extends TypeName> bounds) {
    return new ParameterizedTypeName(null, rawType, bounds);
  }

  /**
   * Returns a new {@link ParameterizedTypeName} instance for the specified {@code name} as nested
   * inside this class, with the specified {@code typeArguments}.
   */
  public ParameterizedTypeName nestedClass(String name, List<TypeName> typeArguments) {
    checkNotNull(name, "name == null");
    return new ParameterizedTypeName(this, rawType.nestedClass(name), typeArguments);
  }
}
