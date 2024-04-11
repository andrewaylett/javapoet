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

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotEquals;

public class TypeNameTest {

  private static final AnnotationSpec ANNOTATION_SPEC =
      AnnotationSpec.builder(ClassName.OBJECT).build();

  @SuppressWarnings({"ClassEscapesDefinedScope", "DataFlowIssue"})
  protected static TestGeneric<String>.Inner testGenericStringInner() {
    return null;
  }

  @SuppressWarnings({"ClassEscapesDefinedScope", "DataFlowIssue"})
  protected static TestGeneric<Integer>.Inner testGenericIntInner() {
    return null;
  }

  @SuppressWarnings({"ClassEscapesDefinedScope", "DataFlowIssue"})
  protected static TestGeneric<Short>.InnerGeneric<Long> testGenericInnerLong() {
    return null;
  }

  @SuppressWarnings({"ClassEscapesDefinedScope", "DataFlowIssue"})
  protected static TestGeneric<Short>.InnerGeneric<Integer> testGenericInnerInt() {
    return null;
  }

  @SuppressWarnings({"ClassEscapesDefinedScope", "DataFlowIssue"})
  protected static TestGeneric.NestedNonGeneric testNestedNonGeneric() {
    return null;
  }

  protected <E extends Enum<E>> E generic(E[] values) {
    return values[0];
  }

  @Test
  public void genericType() throws Exception {
    var recursiveEnum = getClass().getDeclaredMethod("generic", Enum[].class);
    TypeName.get(recursiveEnum.getReturnType());
    TypeName.get(recursiveEnum.getGenericReturnType());
    var genericTypeName = TypeName.get(recursiveEnum.getParameterTypes()[0]);
    TypeName.get(recursiveEnum.getGenericParameterTypes()[0]);

    // Make sure the generic argument is present
    assertThat(genericTypeName.toString()).contains("Enum");
  }

  @Test
  public void innerClassInGenericType() throws Exception {
    var genericStringInner =
        getClass().getDeclaredMethod("testGenericStringInner");
    TypeName.get(genericStringInner.getReturnType());
    var genericTypeName =
        TypeName.get(genericStringInner.getGenericReturnType());
    assertNotEquals(
        TypeName.get(genericStringInner.getGenericReturnType()),
        TypeName.get(getClass()
            .getDeclaredMethod("testGenericIntInner")
            .getGenericReturnType())
    );

    // Make sure the generic argument is present
    assertThat(genericTypeName.toString()).isEqualTo(
        TestGeneric.class.getCanonicalName() + "<java.lang.String>.Inner");
  }

  @Test
  public void innerGenericInGenericType() throws Exception {
    var genericStringInner =
        getClass().getDeclaredMethod("testGenericInnerLong");
    TypeName.get(genericStringInner.getReturnType());
    var genericTypeName =
        TypeName.get(genericStringInner.getGenericReturnType());
    assertNotEquals(
        TypeName.get(genericStringInner.getGenericReturnType()),
        TypeName.get(getClass()
            .getDeclaredMethod("testGenericInnerInt")
            .getGenericReturnType())
    );

    // Make sure the generic argument is present
    assertThat(genericTypeName.toString()).isEqualTo(
        TestGeneric.class.getCanonicalName()
            + "<java.lang.Short>.InnerGeneric<java.lang.Long>");
  }

  @Test
  public void innerStaticInGenericType() throws Exception {
    var staticInGeneric = getClass().getDeclaredMethod("testNestedNonGeneric");
    TypeName.get(staticInGeneric.getReturnType());
    var typeName = TypeName.get(staticInGeneric.getGenericReturnType());

    // Make sure there are no generic arguments
    assertThat(typeName.toString()).isEqualTo(
        TestGeneric.class.getCanonicalName() + ".NestedNonGeneric");
  }

  @Test
  public void equalsAndHashCodePrimitive() {
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Boolean, PrimitiveType.Boolean)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Boolean).toString(),
            ((TypeName) PrimitiveType.Boolean).toString()
        )
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Byte, PrimitiveType.Byte)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Byte).toString(),
            ((TypeName) PrimitiveType.Byte).toString()
        )
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Character, PrimitiveType.Character)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Character).toString(),
            ((TypeName) PrimitiveType.Character).toString()
        )
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Double, PrimitiveType.Double)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Double).toString(),
            ((TypeName) PrimitiveType.Double).toString()
        )
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Float, PrimitiveType.Float)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Float).toString(),
            ((TypeName) PrimitiveType.Float).toString()
        )
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Integer, PrimitiveType.Integer)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Integer).toString(),
            ((TypeName) PrimitiveType.Integer).toString()
        )
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Long, PrimitiveType.Long)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Long).toString(),
            ((TypeName) PrimitiveType.Long).toString()
        )
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Short, PrimitiveType.Short)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Short).toString(),
            ((TypeName) PrimitiveType.Short).toString()
        )
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(PrimitiveType.Void, PrimitiveType.Void)
        .addEqualityGroup(
            ((TypeName) PrimitiveType.Void).toString(),
            ((TypeName) PrimitiveType.Void).toString()
        )
        .testEquals();
  }

  @Test
  public void equalsAndHashCodeArrayTypeName() {
    TypeName a1 = ArrayTypeName.of(Object.class);
    TypeName b1 = ArrayTypeName.of(Object.class);
    new EqualsTester()
        .addEqualityGroup(a1, b1)
        .addEqualityGroup(a1.toString(), b1.toString())
        .testEquals();
    var a = TypeName.get(Object[].class);
    TypeName b = ArrayTypeName.of(Object.class);
    new EqualsTester()
        .addEqualityGroup(a, b)
        .addEqualityGroup(a.toString(), b.toString())
        .testEquals();
  }

  @Test
  public void equalsAndHashCodeClassName() {
    TypeName a2 = ClassName.get(Object.class);
    TypeName b2 = ClassName.get(Object.class);
    new EqualsTester()
        .addEqualityGroup(a2, b2)
        .addEqualityGroup(a2.toString(), b2.toString())
        .testEquals();
    var a1 = TypeName.get(Object.class);
    TypeName b1 = ClassName.get(Object.class);
    new EqualsTester()
        .addEqualityGroup(a1, b1)
        .addEqualityGroup(a1.toString(), b1.toString())
        .testEquals();
    TypeName a = ClassName.bestGuess("java.lang.Object");
    TypeName b = ClassName.get(Object.class);
    new EqualsTester()
        .addEqualityGroup(a, b)
        .addEqualityGroup(a.toString(), b.toString())
        .testEquals();
  }

  @Test
  public void equalsAndHashCodeParameterizedTypeName() {
    TypeName a = ParameterizedTypeName.get(Set.class, UUID.class);
    TypeName b = ParameterizedTypeName.get(Set.class, UUID.class);
    TypeName a1 = ClassName.get(Object.class);
    TypeName b1 = ClassName.get(Object.class);
    new EqualsTester()
        .addEqualityGroup(a1, b1)
        .addEqualityGroup(a1.toString(), b1.toString())
        .addEqualityGroup(a, b)
        .addEqualityGroup(a.toString(), b.toString())
        .addEqualityGroup(ClassName.get(List.class))
        .addEqualityGroup(ParameterizedTypeName.get(List.class, String.class))
        .testEquals();
  }

  @Test
  public void equalsAndHashCodeTypeVariableName() {
    var a = TypeName.get(Object.class);
    var b = TypeName.get(Object.class);
    new EqualsTester()
        .addEqualityGroup(a, b)
        .addEqualityGroup(a.toString(), b.toString())
        .testEquals();
    var typeVar1 =
        TypeVariableName.get("T", Comparator.class, Serializable.class);
    var typeVar2 =
        TypeVariableName.get("T", Comparator.class, Serializable.class);
    new EqualsTester()
        .addEqualityGroup(typeVar1, typeVar2)
        .addEqualityGroup(
            ((TypeName) typeVar1).toString(),
            ((TypeName) typeVar2).toString()
        )
        .testEquals();
  }

  @Test
  public void equalsAndHashCodeWildcardTypeName() {
    TypeName a2 = WildcardTypeName.subtypeOf(Object.class);
    TypeName b2 = WildcardTypeName.subtypeOf(Object.class);
    new EqualsTester()
        .addEqualityGroup(a2, b2)
        .addEqualityGroup(a2.toString(), b2.toString())
        .testEquals();
    TypeName a1 = WildcardTypeName.subtypeOf(Serializable.class);
    TypeName b1 = WildcardTypeName.subtypeOf(Serializable.class);
    new EqualsTester()
        .addEqualityGroup(a1, b1)
        .addEqualityGroup(a1.toString(), b1.toString())
        .testEquals();
    TypeName a = WildcardTypeName.supertypeOf(String.class);
    TypeName b = WildcardTypeName.supertypeOf(String.class);
    new EqualsTester()
        .addEqualityGroup(a, b)
        .addEqualityGroup(a.toString(), b.toString())
        .testEquals();
  }

  @Test
  public void isPrimitive() {
    assertThat(PrimitiveType.Integer.isPrimitive()).isTrue();
    assertThat(ClassName.get("java.lang", "Integer").isPrimitive()).isFalse();
    assertThat(ClassName.get("java.lang", "String").isPrimitive()).isFalse();
    assertThat(PrimitiveType.Void.isPrimitive()).isFalse();
    assertThat(ClassName.get("java.lang", "Void").isPrimitive()).isFalse();
  }

  @Test
  public void isBoxedPrimitive() {
    assertThat(PrimitiveType.Integer.isBoxedPrimitive()).isFalse();
    assertThat(ClassName
        .get("java.lang", "Integer")
        .isBoxedPrimitive()).isTrue();
    assertThat(ClassName
        .get("java.lang", "String")
        .isBoxedPrimitive()).isFalse();
    assertThat(PrimitiveType.Void.isBoxedPrimitive()).isFalse();
    assertThat(ClassName.get("java.lang", "Void").isBoxedPrimitive()).isFalse();
    assertThat(ClassName.get("java.lang", "Integer")
        .annotated(ANNOTATION_SPEC).isBoxedPrimitive()).isTrue();
  }

  @Test
  public void canBoxAnnotatedPrimitive() {
    assertThat(PrimitiveType.Boolean
        .annotated(ANNOTATION_SPEC)
        .box()).isEqualTo(
        ClassName.get("java.lang", "Boolean").annotated(ANNOTATION_SPEC));
  }

  @Test
  public void canUnboxAnnotatedPrimitive() {
    assertThat(ClassName.get("java.lang", "Boolean").annotated(ANNOTATION_SPEC)
        .unbox()).isEqualTo(PrimitiveType.Boolean.annotated(ANNOTATION_SPEC));
  }

  @SuppressWarnings("InnerClassMayBeStatic")
  protected static class TestGeneric<T> {
    static class NestedNonGeneric {
    }

    class Inner {
    }

    class InnerGeneric<T2> {
    }
  }

}
