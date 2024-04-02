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

import com.google.testing.compile.CompilationRule;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public final class ClassNameTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  @Test public void bestGuessForString_simpleClass() {
    assertThat(ClassName.bestGuess(String.class.getName()))
        .isEqualTo(ClassName.get("java.lang", "String"));
  }

  @Test public void bestGuessNonAscii() {
    var className = ClassName.bestGuess(
        "com.\ud835\udc1andro\ud835\udc22d.\ud835\udc00ctiv\ud835\udc22ty");
    assertEquals("com.\ud835\udc1andro\ud835\udc22d", className.packageName());
    assertEquals("\ud835\udc00ctiv\ud835\udc22ty", className.simpleName());
  }

  static class OuterClass {
    static class InnerClass {}
  }

  @Test public void bestGuessForString_nestedClass() {
    assertThat(ClassName.bestGuess(Map.Entry.class.getCanonicalName()))
        .isEqualTo(ClassName.get("java.util", "Map", "Entry"));
    assertThat(ClassName.bestGuess(OuterClass.InnerClass.class.getCanonicalName()))
        .isEqualTo(ClassName.get("com.squareup.javapoet",
            "ClassNameTest", "OuterClass", "InnerClass"));
  }

  @Test public void bestGuessForString_defaultPackage() {
    assertThat(ClassName.bestGuess("SomeClass"))
        .isEqualTo(ClassName.get("", "SomeClass"));
    assertThat(ClassName.bestGuess("SomeClass.Nested"))
        .isEqualTo(ClassName.get("", "SomeClass", "Nested"));
    assertThat(ClassName.bestGuess("SomeClass.Nested.EvenMore"))
        .isEqualTo(ClassName.get("", "SomeClass", "Nested", "EvenMore"));
  }

  @Test public void bestGuessForString_confusingInput() {
    assertBestGuessThrows("");
    assertBestGuessThrows(".");
    assertBestGuessThrows(".Map");
    assertBestGuessThrows("java");
    assertBestGuessThrows("java.util");
    assertBestGuessThrows("java.util.");
    assertBestGuessThrows("java..util.Map.Entry");
    assertBestGuessThrows("java.util..Map.Entry");
    assertBestGuessThrows("java.util.Map..Entry");
    assertBestGuessThrows("com.test.$");
    assertBestGuessThrows("com.test.LooksLikeAClass.pkg");
    assertBestGuessThrows("!@#$gibberish%^&*");
  }

  private void assertBestGuessThrows(String s) {
    try {
      ClassName.bestGuess(s);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void createNestedClass() {
    var foo = ClassName.get("com.example", "Foo");
    var bar = foo.nestedClass("Bar");
    assertThat(bar).isEqualTo(ClassName.get("com.example", "Foo", "Bar"));
    var baz = bar.nestedClass("Baz");
    assertThat(baz).isEqualTo(ClassName.get("com.example", "Foo", "Bar", "Baz"));
  }

  static class $Outer {
    static class $Inner {}
  }

  @Test public void classNameFromTypeElement() {
    var elements = compilationRule.getElements();
    var object = elements.getTypeElement(Object.class.getCanonicalName());
    assertThat(ClassName.get(object).toString()).isEqualTo("java.lang.Object");
    var outer = elements.getTypeElement($Outer.class.getCanonicalName());
    assertThat(ClassName.get(outer).toString()).isEqualTo("com.squareup.javapoet.ClassNameTest.$Outer");
    var inner = elements.getTypeElement($Outer.$Inner.class.getCanonicalName());
    assertThat(ClassName.get(inner).toString()).isEqualTo("com.squareup.javapoet.ClassNameTest.$Outer.$Inner");
  }

  /**
   * Buck builds with "source-based ABI generation" and those builds don't support
   * {@link TypeElement#getKind()}. Test to confirm that we don't use that API.
   */
  @Test public void classNameFromTypeElementDoesntUseGetKind() {
    var elements = compilationRule.getElements();
    var object = elements.getTypeElement(Object.class.getCanonicalName());
    var element = preventGetKind(object);
    assertThat(ClassName.get(element).toString())
        .isEqualTo("java.lang.Object");
    var outer = elements.getTypeElement($Outer.class.getCanonicalName());
    assertThat(ClassName.get(preventGetKind(outer)).toString())
        .isEqualTo("com.squareup.javapoet.ClassNameTest.$Outer");
    var inner = elements.getTypeElement($Outer.$Inner.class.getCanonicalName());
    assertThat(ClassName.get(preventGetKind(inner)).toString())
        .isEqualTo("com.squareup.javapoet.ClassNameTest.$Outer.$Inner");
  }

  /** Returns a new instance like {@code object} that throws on {@code getKind()}. */
  private @NotNull TypeElement preventGetKind(@NotNull TypeElement object) {
    return new TypeElement() {
      @Override
      public TypeMirror asType() {
        return object.asType();
      }

      @Override
      public List<? extends Element> getEnclosedElements() {
        return object.getEnclosedElements();
      }

      @Override
      public NestingKind getNestingKind() {
        return object.getNestingKind();
      }

      @Override
      public Name getQualifiedName() {
        return object.getQualifiedName();
      }

      @Override
      public Name getSimpleName() {
        return object.getSimpleName();
      }

      @Override
      public TypeMirror getSuperclass() {
        return object.getSuperclass();
      }

      @Override
      public List<? extends TypeMirror> getInterfaces() {
        return object.getInterfaces();
      }

      @Override
      public List<? extends TypeParameterElement> getTypeParameters() {
        return object.getTypeParameters();
      }

      @Override
      public Element getEnclosingElement() {
        var enclosingElement = object.getEnclosingElement();
        if (enclosingElement instanceof TypeElement typeElement) {
          return preventGetKind(typeElement);
        }
        return enclosingElement;
      }

      @Override
      public ElementKind getKind() {
        throw new RuntimeException("Should not have called getKind");
      }

      @Override
      public Set<Modifier> getModifiers() {
        return object.getModifiers();
      }

      @Override
      public List<? extends AnnotationMirror> getAnnotationMirrors() {
        return object.getAnnotationMirrors();
      }

      @Override
      public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return object.getAnnotation(annotationType);
      }

      @Override
      public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        return object.getAnnotationsByType(annotationType);
      }

      @Override
      public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        return object.accept(v, p);
      }
    };
  }

  @Test public void classNameFromClass() {
    assertThat(ClassName.get(Object.class).toString())
        .isEqualTo("java.lang.Object");
    assertThat(ClassName.get(OuterClass.InnerClass.class).toString())
        .isEqualTo("com.squareup.javapoet.ClassNameTest.OuterClass.InnerClass");
    assertThat((ClassName.get(new Object() {}.getClass())).toString())
        .isEqualTo("com.squareup.javapoet.ClassNameTest$2");
    assertThat((ClassName.get(new Object() { final Object inner = new Object() {}; }.inner.getClass())).toString())
        .isEqualTo("com.squareup.javapoet.ClassNameTest$3$1");
    assertThat((ClassName.get($Outer.class)).toString())
        .isEqualTo("com.squareup.javapoet.ClassNameTest.$Outer");
    assertThat((ClassName.get($Outer.$Inner.class)).toString())
        .isEqualTo("com.squareup.javapoet.ClassNameTest.$Outer.$Inner");
  }

  @Test public void peerClass() {
    assertThat(ClassName.get(Double.class).peerClass("Short"))
        .isEqualTo(ClassName.get(Short.class));
    assertThat(ClassName.get("", "Double").peerClass("Short"))
        .isEqualTo(ClassName.get("", "Short"));
    assertThat(ClassName.get("a.b", "Combo", "Taco").peerClass("Burrito"))
        .isEqualTo(ClassName.get("a.b", "Combo", "Burrito"));
  }

  @Test public void fromClassRejectionTypes() {
    try {
      ClassName.get(int.class);
      fail();
    } catch (IllegalArgumentException ignored) {
    }
    try {
      ClassName.get(void.class);
      fail();
    } catch (IllegalArgumentException ignored) {
    }
    try {
      ClassName.get(Object[].class);
      fail();
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void reflectionName() {
    assertEquals("java.lang.Object", ClassName.OBJECT.reflectionName());
    assertEquals("java.lang.Thread$State", ClassName.get(Thread.State.class).reflectionName());
    assertEquals("java.util.Map$Entry", ClassName.get(Map.Entry.class).reflectionName());
    assertEquals("Foo", ClassName.get("", "Foo").reflectionName());
    assertEquals("Foo$Bar$Baz", ClassName.get("", "Foo", "Bar", "Baz").reflectionName());
    assertEquals("a.b.c.Foo$Bar$Baz", ClassName.get("a.b.c", "Foo", "Bar", "Baz").reflectionName());
  }

  @Test
  public void canonicalName() {
    assertEquals("java.lang.Object", ClassName.OBJECT.canonicalName());
    assertEquals("java.lang.Thread.State", ClassName.get(Thread.State.class).canonicalName());
    assertEquals("java.util.Map.Entry", ClassName.get(Map.Entry.class).canonicalName());
    assertEquals("Foo", ClassName.get("", "Foo").canonicalName());
    assertEquals("Foo.Bar.Baz", ClassName.get("", "Foo", "Bar", "Baz").canonicalName());
    assertEquals("a.b.c.Foo.Bar.Baz", ClassName.get("a.b.c", "Foo", "Bar", "Baz").canonicalName());
  }
}
