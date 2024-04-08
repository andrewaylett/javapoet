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

import javax.lang.model.element.TypeParameterElement;
import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.notation.Notation.typeRef;

public final class WildcardTypeName extends ObjectTypeName {
  public final List<TypeName> upperBounds;
  public final List<TypeName> lowerBounds;

  private WildcardTypeName(
      List<TypeName> upperBounds,
      List<TypeName> lowerBounds
  ) {
    super();
    this.upperBounds = Util.immutableList(upperBounds);
    this.lowerBounds = Util.immutableList(lowerBounds);

    checkArgument(
        this.upperBounds.size() == 1,
        "unexpected extends bounds: %s",
        upperBounds
    );
    for (var upperBound : this.upperBounds) {
      checkArgument(!upperBound.isPrimitive() && upperBound != PrimitiveType.Void,
          "invalid upper bound: %s",
          upperBound
      );
    }
    for (var lowerBound : this.lowerBounds) {
      checkArgument(!lowerBound.isPrimitive() && lowerBound != PrimitiveType.Void,
          "invalid lower bound: %s",
          lowerBound
      );
    }
  }

  /**
   * Returns a type that represents an unknown type that extends {@code bound}. For example, if
   * {@code bound} is {@code CharSequence.class}, this returns {@code ? extends CharSequence}. If
   * {@code bound} is {@code Object.class}, this returns {@code ?}, which is shorthand for {@code
   * ? extends Object}.
   */
  public static WildcardTypeName subtypeOf(TypeName upperBound) {
    return new WildcardTypeName(
        Collections.singletonList(upperBound),
        Collections.emptyList()
    );
  }

  public static WildcardTypeName subtypeOf(Type upperBound) {
    return subtypeOf(TypeName.get(upperBound));
  }

  /**
   * Returns a type that represents an unknown supertype of {@code bound}. For example, if {@code
   * bound} is {@code String.class}, this returns {@code ? super String}.
   */
  public static WildcardTypeName supertypeOf(TypeName lowerBound) {
    return new WildcardTypeName(
        Collections.singletonList(ClassName.OBJECT),
        Collections.singletonList(lowerBound)
    );
  }

  public static WildcardTypeName supertypeOf(Type lowerBound) {
    return supertypeOf(TypeName.get(lowerBound));
  }

  public static WildcardTypeName get(javax.lang.model.type.WildcardType mirror) {
    return get(mirror, new LinkedHashMap<>());
  }

  static WildcardTypeName get(
      javax.lang.model.type.WildcardType mirror,
      Map<TypeParameterElement, TypeVariableName> typeVariables
  ) {
    var extendsBound = mirror.getExtendsBound();
    if (extendsBound == null) {
      var superBound = mirror.getSuperBound();
      if (superBound == null) {
        return subtypeOf(Object.class);
      } else {
        return supertypeOf(TypeName.get(superBound, typeVariables));
      }
    } else {
      return subtypeOf(TypeName.get(extendsBound, typeVariables));
    }
  }

  public static WildcardTypeName get(WildcardType wildcardName) {
    return get(wildcardName, new LinkedHashMap<>());
  }

  static WildcardTypeName get(
      WildcardType wildcardName,
      Map<Type, TypeVariableName> map
  ) {
    return new WildcardTypeName(
        TypeName.list(wildcardName.getUpperBounds(), map),
        TypeName.list(wildcardName.getLowerBounds(), map)
    );
  }

  @Override
  public @NotNull WildcardTypeName withoutAnnotations() {
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
  public @NotNull TypeName nestedClass(@NotNull String name) {
    throw new UnsupportedOperationException(
        "Cannot nest class inside type bound");
  }

  @Override
  public @NotNull TypeName nestedClass(
      @NotNull String name,
      @NotNull List<TypeName> typeArguments
  ) {
    throw new UnsupportedOperationException(
        "Cannot nest class inside type bound");
  }

  @Override
  public TypeName withBounds(List<? extends TypeName> bounds) {
    return new WildcardTypeName(Stream
        .concat(upperBounds.stream(), bounds.stream())
        .toList(), lowerBounds);
  }

  @Override
  public @NotNull String nameWhenImported() {
    if (lowerBounds.size() == 1) {
      return "? super " + lowerBounds.get(0).nameWhenImported();
    }
    return upperBounds.get(0).equals(ClassName.OBJECT)
        ? "?"
        : "? extends " + upperBounds.get(0).nameWhenImported();
  }

  @Override
  public @NotNull String canonicalName() {
    if (lowerBounds.size() == 1) {
      return "? super " + lowerBounds.get(0).canonicalName();
    }
    return upperBounds.get(0).equals(ClassName.OBJECT)
        ? "?"
        : "? extends " + upperBounds.get(0).canonicalName();
  }

  @Override
  public @NotNull ClassName topLevelClassName() {
    throw new UnsupportedOperationException(
        "Not a sensible concept for a wildcard");
  }

  @Override
  public @NotNull String reflectionName() {
    return canonicalName();
  }

  @Override
  public @Nullable TypeName enclosingClassName() {
    return null;
  }

  @Override
  public List<String> simpleNames() {
    throw new UnsupportedOperationException("A wildcard has no names");
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
    var that = (WildcardTypeName) o;
    return Objects.equals(upperBounds, that.upperBounds) && Objects.equals(
        lowerBounds,
        that.lowerBounds
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(upperBounds, lowerBounds);
  }

  @Override
  public Notation toNotation() {
    if (lowerBounds.size() == 1) {
      return Notation.txt("? super ").then(lowerBounds.get(0).toNotation());
    }
    return upperBounds.get(0).equals(ClassName.OBJECT)
        ? Notation.txt("?")
        : Notation.txt("? extends ").then(upperBounds.get(0).toNotation());
  }
}
