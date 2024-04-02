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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public sealed abstract class ObjectTypeName implements TypeName permits ArrayTypeName, ClassName, ParameterizedTypeName, TypeVariableName, WildcardTypeName {

  /**
   * Lazily-initialized toString of this type name.
   */
  private String cachedString;


  public ObjectTypeName() {
  }

  @Override
  public @NotNull ObjectTypeName withoutAnnotations() {
    return this;
  }

  @Override
  public boolean isAnnotated() {
    return false;
  }

  /**
   * Returns true if this is a primitive type like {@code int}. Returns false for all other types
   * types including boxed primitives and {@code void}.
   */
  @Override
  public boolean isPrimitive() {
    return false;
  }

  /**
   * Returns a boxed type if this is a primitive type (like {@code Integer} for {@code int}) or
   * {@code void}. Returns this type if boxing doesn't apply.
   */
  @Override
  public ObjectTypeName box() {
    return this; // Doesn't need boxing.
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (getClass() != o.getClass()) return false;
    return toString().equals(o.toString());
  }

  @Override
  public final int hashCode() {
    return toString().hashCode();
  }

  @Override
  public final String toString() {
    var result = cachedString;
    if (result == null) {
      try {
        var resultBuilder = new StringBuilder();
        var codeWriter = new CodeWriter(resultBuilder);
        emit(codeWriter);
        result = resultBuilder.toString();
        cachedString = result;
      } catch (IOException e) {
        throw new AssertionError();
      }
    }
    return result;
  }
}
