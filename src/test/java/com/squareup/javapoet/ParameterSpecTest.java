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
import com.google.testing.compile.CompilationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.javapoet.ParameterSpec.builder;
import static com.squareup.javapoet.ParameterSpec.get;
import static com.squareup.javapoet.TestUtil.findFirst;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.junit.Assert.assertThrows;

public class ParameterSpecTest {
  @Rule public final CompilationRule compilation = new CompilationRule();

  private Elements elements;

  @Before
  public void setUp() {
    elements = compilation.getElements();
  }

  private TypeElement getElement(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }

  @Test
  public void equalsAndHashCode() {
    var a = ParameterSpec.builder(int.class, "foo").build();
    var b = ParameterSpec.builder(int.class, "foo").build();
    new EqualsTester().addEqualityGroup(a, b).testEquals();
    assertThat(a.toString()).isEqualTo(b.toString());
    a = ParameterSpec
        .builder(int.class, "i")
        .addModifiers(Modifier.STATIC)
        .build();
    b = ParameterSpec
        .builder(int.class, "i")
        .addModifiers(Modifier.STATIC)
        .build();
    new EqualsTester().addEqualityGroup(a, b).testEquals();
    assertThat(a.toString()).isEqualTo(b.toString());
  }

  @Test
  public void receiverParameterInstanceMethod() {
    var builder = ParameterSpec.builder(int.class, "this");
    assertThat(builder.build().name).isEqualTo("this");
  }

  @Test
  public void receiverParameterNestedClass() {
    var builder = ParameterSpec.builder(int.class, "Foo.this");
    assertThat(builder.build().name).isEqualTo("Foo.this");
  }

  @Test
  public void keywordName() {
    var e = assertThrows(Exception.class, () -> builder(int.class, "super"));
    assertThat(e.getMessage()).isEqualTo("not a valid name: super");
  }

  @Test
  public void nullAnnotationsAddition() {
    var e = assertThrows(
        Exception.class,
        () -> builder(int.class, "foo").addAnnotations(null)
    );
    assertThat(e.getMessage())
        .isEqualTo("annotationSpecs == null");
  }

  @Test
  public void fieldVariableElement() {
    var classElement = getElement(VariableElementFieldClass.class);
    var methods = fieldsIn(elements.getAllMembers(classElement));
    var element = findFirst(methods, "name");

    var exception =
        assertThrows(IllegalArgumentException.class, () -> get(element));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("element is not a parameter");
  }

  @Test
  public void parameterVariableElement() {
    var classElement = getElement(VariableElementParameterClass.class);
    var methods = methodsIn(elements.getAllMembers(classElement));
    var element = findFirst(methods, "foo");
    var parameterElement = element.getParameters().get(0);

    assertThat(ParameterSpec.get(parameterElement).toString())
        .isEqualTo("java.lang.String bar");
  }

  @Test
  public void addNonFinalModifier() {
    List<Modifier> modifiers = new ArrayList<>();
    modifiers.add(FINAL);
    modifiers.add(PUBLIC);

    var e = assertThrows(Exception.class, () -> builder(int.class, "foo")
        .addModifiers(modifiers));
    assertThat(e.getMessage()).isEqualTo("unexpected parameter modifier: public");
  }

  @Test
  public void modifyAnnotations() {
    var builder = ParameterSpec.builder(int.class, "foo")
        .addAnnotation(Override.class)
        .addAnnotation(SuppressWarnings.class);

    builder.annotations.remove(1);
    assertThat(builder.build().annotations).hasSize(1);
  }

  @Test
  public void modifyModifiers() {
    var builder = ParameterSpec.builder(int.class, "foo")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    builder.modifiers.remove(1);
    assertThat(builder.build().modifiers).containsExactly(Modifier.PUBLIC);
  }

  @SuppressWarnings({"unused", "InnerClassMayBeStatic"})
  final class VariableElementFieldClass {
    String name;
  }

  @SuppressWarnings({"unused", "InnerClassMayBeStatic"})
  final class VariableElementParameterClass {
    public void foo(@Nullable final String bar) {
    }
  }
}
