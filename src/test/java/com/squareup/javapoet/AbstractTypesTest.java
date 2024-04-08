/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.notation.Notation;
import org.junit.Test;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static org.junit.Assert.fail;

public abstract class AbstractTypesTest {
  protected abstract Elements getElements();

  protected abstract Types getTypes();

  private TypeElement getElement(Class<?> clazz) {
    return getElements().getTypeElement(clazz.getCanonicalName());
  }

  private TypeMirror getMirror(Class<?> clazz) {
    return getElement(clazz).asType();
  }

  @Test
  public void getBasicTypeMirror() {
    assertThat(TypeName.get(getMirror(Object.class)))
        .isEqualTo(ClassName.get(Object.class));
    assertThat(TypeName.get(getMirror(Charset.class)))
        .isEqualTo(ClassName.get(Charset.class));
    assertThat(TypeName.get(getMirror(AbstractTypesTest.class)))
        .isEqualTo(ClassName.get(AbstractTypesTest.class));
  }

  @Test
  public void getParameterizedTypeMirror() {
    var setType =
        getTypes().getDeclaredType(
            getElement(Set.class),
            getMirror(Object.class)
        );
    assertThat(TypeName.get(setType))
        .isEqualTo(ParameterizedTypeName.get(
            ClassName.get(Set.class),
            ClassName.OBJECT
        ));
  }

  @Test
  public void errorTypes() {
    var hasErrorTypes =
        JavaFileObjects.forSourceLines(
            "com.squareup.tacos.ErrorTypes",
            "package com.squareup.tacos;",
            "",
            "@SuppressWarnings(\"hook-into-compiler\")",
            "class ErrorTypes {",
            "  Tacos tacos;",
            "  Ingredients.Guacamole guacamole;",
            "}"
        );
    var compilation = javac().withProcessors(new AbstractProcessor() {
      @Override
      public boolean process(
          Set<? extends TypeElement> set,
          RoundEnvironment roundEnvironment
      ) {
        var classFile =
            processingEnv
                .getElementUtils()
                .getTypeElement("com.squareup.tacos.ErrorTypes");
        var fields = fieldsIn(classFile.getEnclosedElements());
        var topLevel = (ErrorType) fields.get(0).asType();
        var member = (ErrorType) fields.get(1).asType();

        assertThat(TypeName.get(topLevel)).isEqualTo(ClassName.get(
            "",
            "Tacos"
        ));
        assertThat(TypeName.get(member)).isEqualTo(ClassName.get(
            "Ingredients",
            "Guacamole"
        ));
        return false;
      }

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("*");
      }
    }).compile(hasErrorTypes);

    assertThat(compilation).failed();
  }

  @Test
  public void getTypeVariableTypeMirror() {
    var typeVariables =
        getElement(Parameterized.class).getTypeParameters();

    // Members of converted types use ClassName and not Class<?>.
    var number = ClassName.get(Number.class);
    var runnable = ClassName.get(Runnable.class);
    var serializable = ClassName.get(Serializable.class);

    assertThat(TypeName.get(typeVariables.get(0).asType()))
        .isEqualTo(TypeVariableName.get("Simple"));
    assertThat(TypeName.get(typeVariables.get(1).asType()))
        .isEqualTo(TypeVariableName.get("ExtendsClass", number));
    assertThat(TypeName.get(typeVariables.get(2).asType()))
        .isEqualTo(TypeVariableName.get("ExtendsInterface", runnable));
    assertThat(TypeName.get(typeVariables.get(3).asType()))
        .isEqualTo(TypeVariableName.get(
            "ExtendsTypeVariable",
            TypeVariableName.get("Simple")
        ));
    assertThat(TypeName.get(typeVariables.get(4).asType()))
        .isEqualTo(TypeVariableName.get("Intersection", number, runnable));
    assertThat(TypeName.get(typeVariables.get(5).asType()))
        .isEqualTo(TypeVariableName.get(
            "IntersectionOfInterfaces",
            runnable,
            serializable
        ));
    assertThat(((TypeVariableName) TypeName.get(typeVariables
        .get(4)
        .asType())).bounds)
        .containsExactly(number, runnable);
  }

  @Test
  public void getTypeVariableTypeMirrorRecursive() {
    var typeMirror = getElement(Recursive.class).asType();
    var typeName = (ParameterizedTypeName) TypeName.get(typeMirror);
    var className = Recursive.class.getCanonicalName();
    assertThat(typeName.toString()).isEqualTo(className + "<T>");

    var typeVariableName = (TypeVariableName) typeName.typeArguments.get(0);

    try {
      typeVariableName.bounds.set(0, null);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
    }

    assertThat(Notation.typeRef(typeVariableName).toCode()).isEqualTo("T");
    assertThat(typeVariableName.bounds.toString())
        .isEqualTo("[java.util.Map<java.util.List<T>, java.util.Set<T[]>>]");
  }

  @Test
  public void getPrimitiveTypeMirror() {
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.BOOLEAN)))
        .isEqualTo(PrimitiveType.Boolean);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.BYTE)))
        .isEqualTo(PrimitiveType.Byte);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.SHORT)))
        .isEqualTo(PrimitiveType.Short);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.INT)))
        .isEqualTo(PrimitiveType.Integer);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.LONG)))
        .isEqualTo(PrimitiveType.Long);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.CHAR)))
        .isEqualTo(PrimitiveType.Character);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.FLOAT)))
        .isEqualTo(PrimitiveType.Float);
    assertThat(TypeName.get(getTypes().getPrimitiveType(TypeKind.DOUBLE)))
        .isEqualTo(PrimitiveType.Double);
  }

  @Test
  public void getArrayTypeMirror() {
    assertThat(TypeName.get(getTypes().getArrayType(getMirror(Object.class))))
        .isEqualTo(ArrayTypeName.of(ClassName.OBJECT));
  }

  @Test
  public void getVoidTypeMirror() {
    assertThat(TypeName.get(getTypes().getNoType(TypeKind.VOID)))
        .isEqualTo(PrimitiveType.Void);
  }

  @Test
  public void getNullTypeMirror() {
    try {
      TypeName.get(getTypes().getNullType());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void parameterizedType() throws Exception {
    var type = ParameterizedTypeName.get(Map.class, String.class, Long.class);
    assertThat(type.toString()).isEqualTo(
        "java.util.Map<java.lang.String, java.lang.Long>");
  }

  @Test
  public void arrayType() throws Exception {
    var type = ArrayTypeName.of(String.class);
    assertThat(type.toString()).isEqualTo("java.lang.String[]");
  }

  @Test
  public void wildcardExtendsType() throws Exception {
    var type = WildcardTypeName.subtypeOf(CharSequence.class);
    assertThat(type.toString()).isEqualTo("? extends java.lang.CharSequence");
  }

  @Test
  public void wildcardExtendsObject() throws Exception {
    var type = WildcardTypeName.subtypeOf(Object.class);
    assertThat(type.toString()).isEqualTo("?");
  }

  @Test
  public void wildcardSuperType() throws Exception {
    var type = WildcardTypeName.supertypeOf(String.class);
    assertThat(type.toString()).isEqualTo("? super java.lang.String");
  }

  @Test
  public void wildcardMirrorNoBounds() throws Exception {
    var wildcard = getTypes().getWildcardType(null, null);
    var type = TypeName.get(wildcard);
    assertThat(type.toString()).isEqualTo("?");
  }

  @Test
  public void wildcardMirrorExtendsType() throws Exception {
    var types = getTypes();
    var elements = getElements();
    var charSequence =
        elements.getTypeElement(CharSequence.class.getName()).asType();
    var wildcard = types.getWildcardType(charSequence, null);
    var type = TypeName.get(wildcard);
    assertThat(type.toString()).isEqualTo("? extends java.lang.CharSequence");
  }

  @Test
  public void wildcardMirrorSuperType() throws Exception {
    var types = getTypes();
    var elements = getElements();
    var string = elements.getTypeElement(String.class.getName()).asType();
    var wildcard = types.getWildcardType(null, string);
    var type = TypeName.get(wildcard);
    assertThat(type.toString()).isEqualTo("? super java.lang.String");
  }

  @Test
  public void typeVariable() throws Exception {
    var type = TypeVariableName.get("T", CharSequence.class);
    assertThat(Notation.typeRef(type).toCode()).isEqualTo("T"); // (Bounds are only emitted in declaration.)
  }

  @Test
  public void box() throws Exception {
    assertThat(PrimitiveType.Integer.box()).isEqualTo(ClassName.get(Integer.class));
    assertThat(PrimitiveType.Void.box()).isEqualTo(ClassName.get(Void.class));
    assertThat(ClassName.get(Integer.class).box()).isEqualTo(ClassName.get(
        Integer.class));
    assertThat(ClassName
        .get(Void.class)
        .box()).isEqualTo(ClassName.get(Void.class));
    assertThat(ClassName.OBJECT.box()).isEqualTo(ClassName.OBJECT);
    assertThat(ClassName
        .get(String.class)
        .box()).isEqualTo(ClassName.get(String.class));
  }

  @Test
  public void unbox() throws Exception {
    assertThat(PrimitiveType.Integer).isEqualTo(PrimitiveType.Integer.unbox());
    assertThat(PrimitiveType.Void).isEqualTo(PrimitiveType.Void.unbox());
    assertThat(ClassName
        .get(Integer.class)
        .unbox()).isEqualTo(PrimitiveType.Integer.unbox());
    assertThat(ClassName
        .get(Void.class)
        .unbox()).isEqualTo(PrimitiveType.Void.unbox());
    try {
      ClassName.OBJECT.unbox();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      ClassName.get(String.class).unbox();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @SuppressWarnings("unused")
  static class Parameterized<
      Simple,
      ExtendsClass extends Number,
      ExtendsInterface extends Runnable,
      ExtendsTypeVariable extends Simple,
      Intersection extends Number & Runnable,
      IntersectionOfInterfaces extends Runnable & Serializable> {
  }

  @SuppressWarnings("unused")
  static class Recursive<T extends Map<List<T>, Set<T[]>>> {
  }
}
