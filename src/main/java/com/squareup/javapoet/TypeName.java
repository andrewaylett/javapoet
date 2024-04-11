package com.squareup.javapoet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Any type in Java's type system, plus {@code void}. This class is an identifier for primitive
 * types like {@code int} and raw reference types like {@code String} and {@code List}. It also
 * identifies composite types like {@code char[]} and {@code Set<Long>}.
 *
 * <p>Type names are dumb identifiers only and do not model the values they name. For example, the
 * type name for {@code java.util.List} doesn't know about the {@code size()} method, the fact that
 * lists are collections, or even that it accepts a single type parameter.
 *
 * <p>Instances of this class are immutable value objects that implement {@code equals()} and {@code
 * hashCode()} properly.
 *
 * <h3>Referencing existing types</h3>
 *
 * <p>PrimitiveType and void are constants that you can reference directly: see {@link PrimitiveType#Integer}, {@link
 * PrimitiveType#Double}, and {@link PrimitiveType#Void}.
 *
 * <p>In an annotation processor you can get a type name instance for a type mirror by calling
 * {@link #get(TypeMirror)}. In reflection code, you can use {@link #get(Type)}.
 *
 * <h3>Defining new types</h3>
 *
 * <p>Create new reference types like {@code com.example.HelloWorld} with {@link
 * ClassName#get(String, String, String...)}. To build composite types like {@code char[]} and
 * {@code Set<Long>}, use the factory methods on {@link ArrayTypeName}, {@link
 * ParameterizedTypeName}, {@link TypeVariableName}, and {@link WildcardTypeName}.
 */
public sealed interface TypeName extends Emitable
    permits ObjectTypeName, PrimitiveType, AnnotatedTypeName {
  /**
   * Returns a type name equivalent to {@code mirror}.
   */
  static TypeName get(TypeMirror mirror) {
    return get(mirror, new LinkedHashMap<>());
  }

  static TypeName get(
      TypeMirror mirror,
      final Map<TypeParameterElement, TypeVariableName> typeVariables
  ) {
    return mirror.accept(new SimpleTypeVisitor8<TypeName, Void>() {
      @Override
      public TypeName visitPrimitive(
          javax.lang.model.type.PrimitiveType t,
          Void p
      ) {
        return switch (t.getKind()) {
          case BOOLEAN -> PrimitiveType.Boolean;
          case BYTE -> PrimitiveType.Byte;
          case SHORT -> PrimitiveType.Short;
          case INT -> PrimitiveType.Integer;
          case LONG -> PrimitiveType.Long;
          case CHAR -> PrimitiveType.Character;
          case FLOAT -> PrimitiveType.Float;
          case DOUBLE -> PrimitiveType.Double;
          default -> throw new AssertionError();
        };
      }

      @Override
      public TypeName visitDeclared(DeclaredType t, Void p) {
        var rawType = ClassName.get((TypeElement) t.asElement());
        var enclosingType = t.getEnclosingType();
        var enclosing =
            (enclosingType.getKind() != TypeKind.NONE)
                && !t.asElement().getModifiers().contains(Modifier.STATIC)
                ? enclosingType.accept(this, null)
                : null;
        if (t.getTypeArguments().isEmpty()
            && !(enclosing instanceof ParameterizedTypeName)) {
          return rawType;
        }

        List<TypeName> typeArgumentNames = new ArrayList<>();
        for (var mirror : t.getTypeArguments()) {
          typeArgumentNames.add(get(mirror, typeVariables));
        }
        return enclosing instanceof ParameterizedTypeName
            ? enclosing.nestedClass(
            rawType.nameWhenImported(), typeArgumentNames)
            : new ParameterizedTypeName(null, rawType, typeArgumentNames);
      }

      @Override
      public TypeName visitError(ErrorType t, Void p) {
        return visitDeclared(t, p);
      }

      @Override
      public ArrayTypeName visitArray(ArrayType t, Void p) {
        return ArrayTypeName.get(t, typeVariables);
      }

      @Override
      public TypeName visitTypeVariable(
          javax.lang.model.type.TypeVariable t,
          Void p
      ) {
        return TypeVariableName.get(t, typeVariables);
      }

      @Override
      public TypeName visitWildcard(
          javax.lang.model.type.WildcardType t,
          Void p
      ) {
        return WildcardTypeName.get(t, typeVariables);
      }

      @Override
      public TypeName visitNoType(NoType t, Void p) {
        if (t.getKind() == TypeKind.VOID) {
          return PrimitiveType.Void;
        }
        return super.visitUnknown(t, p);
      }

      @Override
      protected ObjectTypeName defaultAction(TypeMirror e, Void p) {
        throw new IllegalArgumentException("Unexpected type mirror: " + e);
      }
    }, null);
  }

  /**
   * Returns a type name equivalent to {@code type}.
   */
  static TypeName get(Type type) {
    return get(type, new LinkedHashMap<>());
  }

  static TypeName get(Type type, Map<Type, TypeVariableName> map) {
    if (type instanceof Class<?> classType) {
      if (type == void.class) {
        return PrimitiveType.Void;
      }
      if (type == boolean.class) {
        return PrimitiveType.Boolean;
      }
      if (type == byte.class) {
        return PrimitiveType.Byte;
      }
      if (type == short.class) {
        return PrimitiveType.Short;
      }
      if (type == int.class) {
        return PrimitiveType.Integer;
      }
      if (type == long.class) {
        return PrimitiveType.Long;
      }
      if (type == char.class) {
        return PrimitiveType.Character;
      }
      if (type == float.class) {
        return PrimitiveType.Float;
      }
      if (type == double.class) {
        return PrimitiveType.Double;
      }
      if (classType.isArray()) {
        return ArrayTypeName.of(get(classType.getComponentType(), map));
      }
      return ClassName.get(classType);

    } else if (type instanceof ParameterizedType) {
      return ParameterizedTypeName.get((ParameterizedType) type, map);

    } else if (type instanceof java.lang.reflect.WildcardType) {
      return WildcardTypeName.get((java.lang.reflect.WildcardType) type, map);

    } else if (type instanceof java.lang.reflect.TypeVariable<?>) {
      return TypeVariableName.get(
          (java.lang.reflect.TypeVariable<?>) type,
          map
      );

    } else if (type instanceof GenericArrayType) {
      return ArrayTypeName.get((GenericArrayType) type, map);

    } else {
      throw new IllegalArgumentException("unexpected type: " + type);
    }
  }

  /**
   * Converts an array of types to a list of type names.
   */
  static List<TypeName> list(Type[] types) {
    return list(types, new LinkedHashMap<>());
  }

  static List<TypeName> list(Type[] types, Map<Type, TypeVariableName> map) {
    List<TypeName> result = new ArrayList<>(types.length);
    for (var type : types) {
      result.add(get(type, map));
    }
    return result;
  }

  /**
   * Returns the array component of {@code type}, or null if {@code type} is not an array.
   */
  static @Nullable TypeName arrayComponent(TypeName type) {
    return type instanceof ArrayTypeName atn
        ? atn.componentType
        : type instanceof AnnotatedTypeName annotated
            && annotated.inner instanceof ArrayTypeName inner
            ? inner.componentType.annotated(annotated.annotations)
            : null;
  }

  /**
   * Returns {@code type} as an array, or null if {@code type} is not an array.
   */
  static @Nullable TypeName asArray(TypeName type) {
    return type instanceof ArrayTypeName atn
        ? atn
        : type instanceof AnnotatedTypeName annotated
            && annotated.inner instanceof ArrayTypeName
            ? annotated
            : null;
  }

  default @NotNull AnnotatedTypeName annotated(
      @NotNull Collection<AnnotationSpec> annotations
  ) {
    return new AnnotatedTypeName(this, annotations);
  }

  default @NotNull AnnotatedTypeName annotated(
      @NotNull AnnotationSpec... annotations
  ) {
    return annotated(Arrays.asList(annotations));
  }

  @NotNull
  TypeName withoutAnnotations();

  /**
   * Returns true if this is a primitive type like {@code int}.
   * Returns false for all other types including boxed primitives and {@code void}.
   */
  boolean isPrimitive();

  /**
   * Returns true if this is a boxed primitive type like {@code Integer}.
   * Returns false for all other types, including unboxed primitives and {@code java.lang.Void}.
   */
  boolean isBoxedPrimitive();

  /**
   * Returns a boxed type if this is a primitive type (like {@code Integer} for {@code int}) or
   * {@code void}.
   * Returns this type if boxing doesn't apply.
   */
  TypeName box();

  /**
   * Returns an unboxed type if this is a boxed primitive type (like {@code int} for {@code
   * Integer}) or {@code Void}.
   * Returns this type if it is already unboxed.
   *
   * @throws UnsupportedOperationException if this type isn't eligible for unboxing.
   */
  @NotNull
  TypeName unbox();

  @NotNull
  TypeName nestedClass(@NotNull String name);

  @NotNull
  TypeName nestedClass(
      @NotNull String name,
      @NotNull List<TypeName> typeArguments
  );

  default boolean isAnnotated() {
    return this instanceof AnnotatedTypeName;
  }

  default TypeName withBounds(Type... bounds) {
    return withBounds(TypeName.list(bounds));
  }

  default TypeName withBounds(TypeName... bounds) {
    return withBounds(Arrays.asList(bounds));
  }

  TypeName withBounds(List<? extends TypeName> bounds);

  @NotNull
  String nameWhenImported();

  @NotNull
  String canonicalName();

  @NotNull
  ClassName topLevelClassName();

  @NotNull
  String reflectionName();

  @Nullable
  TypeName enclosingClassName();

  List<String> simpleNames();

  String simpleName();
}
