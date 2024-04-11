/*
 * Copyright © 2015 Square, Inc.
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

import com.google.common.testing.EqualsTester;
import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AnnotatedTypeNameTest {

  private final static String NN = NeverNull.class.getCanonicalName();
  private final static String TUA = TypeUseAnnotation.class.getCanonicalName();
  private final AnnotationSpec NEVER_NULL =
      AnnotationSpec.builder(NeverNull.class).build();
  private final AnnotationSpec TYPE_USE_ANNOTATION =
      AnnotationSpec.builder(TypeUseAnnotation.class).build();

  @Test(expected = IllegalArgumentException.class)
  public void nullAnnotationArray() {
    PrimitiveType.Boolean.annotated((AnnotationSpec[]) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullAnnotationList() {
    PrimitiveType.Double.annotated((List<AnnotationSpec>) null);
  }

  @Test
  public void annotated() {
    var simpleString = TypeName.get(String.class);
    assertFalse(simpleString.isAnnotated());
    assertEquals(simpleString, TypeName.get(String.class));

    TypeName annotated = simpleString.annotated(NEVER_NULL);
    assertTrue(annotated.isAnnotated());
    assertEquals(annotated, annotated.annotated());
  }

  @Test
  public void annotatedType() {
    var type = TypeName.get(String.class);
    TypeName actual = type.annotated(TYPE_USE_ANNOTATION);
    assertThat(actual.toString()).isEqualTo("@" + TUA + " java.lang.String");
  }

  @Test
  public void annotatedTwice() {
    var type = TypeName.get(String.class);
    TypeName actual =
        type.annotated(NEVER_NULL)
            .annotated(TYPE_USE_ANNOTATION);
    assertThat(actual.toString())
        .isEqualTo("@" + NN + "\n@" + TUA + "\njava.lang.String");
  }

  @Test
  public void annotatedParameterizedType() {
    TypeName type = ParameterizedTypeName.get(List.class, String.class);
    TypeName actual = type.annotated(TYPE_USE_ANNOTATION);
    assertThat(actual.toString()).isEqualTo(
        "@" + TUA + " java.util.List<java.lang.String>");
  }

  @Test
  public void annotatedArgumentOfParameterizedType() {
    TypeName type = TypeName.get(String.class).annotated(TYPE_USE_ANNOTATION);
    TypeName actual =
        ParameterizedTypeName.get(ClassName.get(List.class), type);
    assertThat(actual.toString()).isEqualTo(
        "java.util.List<@" + TUA + " java.lang.String>");
  }

  @Test
  public void annotatedWildcardTypeNameWithSuper() {
    TypeName type = TypeName.get(String.class).annotated(TYPE_USE_ANNOTATION);
    TypeName actual = WildcardTypeName.supertypeOf(type);
    assertThat(actual.toString()).isEqualTo(
        "? super @" + TUA + " java.lang.String");
  }

  @Test
  public void annotatedWildcardTypeNameWithExtends() {
    TypeName type = TypeName.get(String.class).annotated(TYPE_USE_ANNOTATION);
    TypeName actual = WildcardTypeName.subtypeOf(type);
    assertThat(actual.toString()).isEqualTo(
        "? extends @" + TUA + " java.lang.String");
  }

  @Test
  public void annotatedEquivalence() {
    annotatedEquivalence(PrimitiveType.Void);
    annotatedEquivalence(TypeName.get(Object[].class));
    annotatedEquivalence(ClassName.get(Object.class));
    annotatedEquivalence(ParameterizedTypeName.get(List.class, Object.class));
    annotatedEquivalence(TypeName.get(Object.class));
  }

  private void annotatedEquivalence(TypeName type) {
    assertFalse(type.isAnnotated());
    new EqualsTester()
        .addEqualityGroup(type)
        .addEqualityGroup(type.annotated(TYPE_USE_ANNOTATION))
        .testEquals();
  }

  // https://github.com/square/javapoet/issues/431
  @Test
  public void annotatedNestedType() {
    TypeName type =
        TypeName.get(Map.Entry.class).annotated(TYPE_USE_ANNOTATION);
    assertThat(type.toString()).isEqualTo("@" + TUA + " java.util.Map.Entry");
  }

  @Test
  public void annotatedEnclosingAndNestedType() {
    TypeName type = TypeName.get(Map.class).annotated(TYPE_USE_ANNOTATION)
        .nestedClass("Entry").annotated(TYPE_USE_ANNOTATION);
    assertThat(type.toString()).isEqualTo(
        "@" + TUA + "\n@" + TUA + "\njava.util.Map.Entry");
  }

  // https://github.com/square/javapoet/issues/431
  @Test
  public void annotatedNestedParameterizedType() {
    TypeName type =
        ParameterizedTypeName.get(Map.Entry.class, Byte.class, Byte.class)
            .annotated(TYPE_USE_ANNOTATION);
    assertThat(type.toString())
        .isEqualTo(
            "@" + TUA + "\njava.util.Map.Entry<java.lang.Byte, java.lang.Byte>");
  }

  @Test
  public void withoutAnnotationsOnAnnotatedEnclosingAndNestedType() {
    TypeName type = TypeName.get(Map.class).annotated(TYPE_USE_ANNOTATION)
        .nestedClass("Entry").annotated(TYPE_USE_ANNOTATION);
    assertThat(type.isAnnotated()).isTrue();
    assertThat(type.withoutAnnotations()).isEqualTo(TypeName.get(Map.Entry.class));
  }

  @Test
  public void withoutAnnotationsOnAnnotatedEnclosingType() {
    var type = TypeName.get(Map.class).annotated(TYPE_USE_ANNOTATION)
        .nestedClass("Entry");
    assertThat(type.isAnnotated()).isTrue();
    assertThat(type.withoutAnnotations()).isEqualTo(TypeName.get(Map.Entry.class));
  }

  @Test
  public void withoutAnnotationsOnAnnotatedNestedType() {
    var type = ((ClassName) TypeName.get(Map.class))
        .nestedClass("Entry").annotated(TYPE_USE_ANNOTATION);
    assertThat(type.isAnnotated()).isTrue();
    assertThat(type.withoutAnnotations()).isEqualTo(TypeName.get(Map.Entry.class));
  }

  // https://github.com/square/javapoet/issues/614
  @Test
  public void annotatedArrayType() {
    var type = ArrayTypeName
        .of(ClassName.get(Object.class))
        .annotated(TYPE_USE_ANNOTATION);
    assertThat(type.toString()).isEqualTo("@" + TUA + " java.lang.Object[]");
  }

  @Test
  public void annotatedArrayElementType() {
    var type = ArrayTypeName.of(ClassName
        .get(Object.class)
        .annotated(TYPE_USE_ANNOTATION));
    assertThat(type.toString()).isEqualTo("@" + TUA + " java.lang.Object[]");
  }

  // https://github.com/square/javapoet/issues/614
  @Test
  public void annotatedOuterMultidimensionalArrayType() {
    TypeName type =
        ArrayTypeName.of(ArrayTypeName.of(ClassName.get(Object.class)))
            .annotated(TYPE_USE_ANNOTATION);
    assertThat(type.toString()).isEqualTo("@" + TUA + " java.lang.Object[][]");
  }

  // https://github.com/square/javapoet/issues/614
  @Test
  public void annotatedInnerMultidimensionalArrayType() {
    var type = ArrayTypeName.of(ArrayTypeName.of(ClassName.get(Object.class))
        .annotated(TYPE_USE_ANNOTATION));
    assertThat(type.toString()).isEqualTo("@" + TUA + " java.lang.Object[][]");
  }

  // https://github.com/square/javapoet/issues/614
  @Test
  public void annotatedArrayTypeVarargsParameter() {
    var type = ArrayTypeName.of(ArrayTypeName.of(ClassName.get(Object.class)))
        .annotated(TYPE_USE_ANNOTATION);
    var varargsMethod = MethodSpec.methodBuilder("m")
        .addParameter(
            ParameterSpec.builder(type, "p")
                .build())
        .varargs()
        .build();
    assertThat(varargsMethod.toString()).isEqualTo(
        "void m(@" + TUA + " java.lang.Object[]... p) {}\n");
  }

  // https://github.com/square/javapoet/issues/614
  @Test
  public void annotatedArrayTypeInVarargsParameter() {
    var type = ArrayTypeName.of(ArrayTypeName.of(ClassName.get(Object.class))
        .annotated(TYPE_USE_ANNOTATION));
    var varargsMethod = MethodSpec.methodBuilder("m")
        .addParameter(
            ParameterSpec.builder(type, "p")
                .build())
        .varargs()
        .build();
    assertThat(varargsMethod.toString()).isEqualTo(
        "void m(@" + TUA + " java.lang.Object[]... p) {}\n");
  }

  @Target(ElementType.TYPE_USE)
  public @interface NeverNull {
  }

  @Target(ElementType.TYPE_USE)
  public @interface TypeUseAnnotation {
  }
}
