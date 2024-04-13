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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeVariable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.txt;

@Immutable
public sealed abstract class TypeVariableName extends ObjectTypeName {
  private final static LoadingCache<TypeParameterElement, TypeVariableName> mirrors =
      CacheBuilder.newBuilder().build(new CacheLoader<>() {
        @Override
        public @NotNull TypeVariableName load(@NotNull TypeParameterElement key) throws Exception {
          var bounds = key.getBounds();
          try {
            return new LiteralTypeVariableName(
                key.getSimpleName().toString(),
                bounds.stream().map(b -> TypeName.get(b, new HashMap<>())).filter(tn -> !tn.equals(ClassName.OBJECT)).toList()
            );
          } catch (IllegalStateException ignored) {
            return new MirrorTypeVariableName(key);
          }
        }
      });

  @Immutable
  private static final class LiteralTypeVariableName extends TypeVariableName {
    public final String name;
    public final ImmutableList<TypeName> bounds;

    private LiteralTypeVariableName(String name, List<TypeName> bounds) {
      super();
      this.name = checkNotNull(name, "name == null");
      this.bounds = ImmutableList.copyOf(bounds);

      for (var bound : this.bounds) {
        checkArgument(
            !bound.isPrimitive() && bound != PrimitiveType.Void,
            "invalid bound: %s",
            bound
        );
      }
    }

    @Override
    protected String getName() {
      return name;
    }

    @Override
    protected List<TypeName> getBounds() {
      return bounds;
    }
  }

  @Immutable
  private static final class MirrorTypeVariableName extends TypeVariableName {
    @SuppressWarnings("Immutable")
    public final TypeParameterElement typeMirror;

    private MirrorTypeVariableName(TypeParameterElement typeMirror) {
      this.typeMirror = typeMirror;
    }

    @Override
    protected String getName() {
      return typeMirror.getSimpleName().toString();
    }

    @Override
    protected List<TypeName> getBounds() {
      return typeMirror.getBounds().stream().map(m -> TypeName.get(m, mirrors.asMap())).toList();
    }
  }

  private static TypeVariableName of(String name, List<TypeName> bounds) {
    // Strip java.lang.Object from bounds if it is present.
    List<TypeName> boundsNoObject = new ArrayList<>(bounds);
    boundsNoObject.remove(ClassName.OBJECT);
    return new LiteralTypeVariableName(
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
      Map<TypeParameterElement, TypeVariableName> ignored
  ) {
    var element = (TypeParameterElement) mirror.asElement();
    return mirrors.getUnchecked(element);
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
      result = new LiteralTypeVariableName(type.getName(), visibleBounds);
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
    newBounds.addAll(this.getBounds());
    newBounds.addAll(bounds);
    return new LiteralTypeVariableName(getName(), newBounds);
  }

  @Nonnull
  @Override
  public @NotNull String nameWhenImported() {
    return getName();
  }

  @Override
  public @NotNull String canonicalName() {
    return getName();
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
  public @NotNull String simpleName() {
    return getName();
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
  public String toString() {
    return toNotation().toCode();
  }
  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof TypeVariableName that) {

      return Objects.equals(getName(), that.getName()) && Objects.equals(
          getBounds(),
          that.getBounds()
      );
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(getName());
  }

  @Override
  public @NotNull Notation toNotation() {
    return Notation.typeRef(this);
  }

  @Override
  public @NotNull Notation toDeclaration() {
    var builder = Stream.<Notation>builder();
    builder.add(Notation.typeRef(this));
    if (!getBounds().isEmpty()) {
      builder.add(txt("extends"));
      builder.add(getBounds()
          .stream()
          .map(Notation::typeRef)
          .collect(join(txt(" & "))));
    }
    return builder.build().filter(n -> !n.isEmpty()).collect(join(txt(" ")));
  }

  protected abstract String getName();
  protected abstract List<TypeName> getBounds();
}
