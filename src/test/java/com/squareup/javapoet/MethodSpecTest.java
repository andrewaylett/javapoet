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

import com.google.testing.compile.CompilationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.MethodSpec.overriding;
import static com.squareup.javapoet.TestUtil.findFirst;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

public final class MethodSpecTest {
  @Rule public final CompilationRule compilation = new CompilationRule();

  private Elements elements;
  private Types types;

  private static CodeBlock named(String format, Map<String, ?> args) {
    return CodeBlock.builder().addNamed(format, args).build();
  }

  @Before
  public void setUp() {
    elements = compilation.getElements();
    types = compilation.getTypes();
  }

  private TypeElement getElement(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }

  @Test
  public void nullAnnotationsAddition() {
    var expected =
        assertThrows(
            IllegalArgumentException.class,
            () -> methodBuilder("doSomething").addAnnotations(null)
        );
    assertThat(expected).hasMessageThat().isEqualTo("annotationSpecs == null");
  }

  @Test
  public void nullTypeVariablesAddition() {
    var expected =
        assertThrows(
            IllegalArgumentException.class,
            () -> methodBuilder("doSomething").addTypeVariables(null)
        );
    assertThat(expected).hasMessageThat().isEqualTo("typeVariables == null");
  }

  @Test
  public void nullParametersAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> methodBuilder("doSomething").addParameters(null)
    );
    assertThat(expected).hasMessageThat().isEqualTo("parameterSpecs == null");
  }

  @Test
  public void nullExceptionsAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> methodBuilder("doSomething").addExceptions(null)
    );
    assertThat(expected).hasMessageThat().isEqualTo("exceptions == null");
  }

  @Test
  public void overrideEverything() {
    var classElement = getElement(Everything.class);
    var methodElement =
        getOnlyElement(methodsIn(classElement.getEnclosedElements()));
    var method = MethodSpec.overriding(methodElement).build();
    assertThat(method.toString()).isEqualTo("""
        @java.lang.Override
        protected <T extends java.lang.Runnable & java.io.Closeable> java.lang.Runnable everything(
          java.lang.String arg0, java.util.List<? extends T> arg1
        ) throws java.io.IOException, java.lang.SecurityException {}
        """);
  }

  @Test
  public void overrideGenerics() {
    var classElement = getElement(Generics.class);
    var methodElement =
        getOnlyElement(methodsIn(classElement.getEnclosedElements()));
    var method = MethodSpec.overriding(methodElement)
        .addStatement("return null")
        .build();
    assertThat(method.toString()).isEqualTo("""
        @java.lang.Override
        <T, R, V extends java.lang.Throwable> T run(R param) throws V {
          return null;
        }
        """);
  }

  @Test
  public void overrideDoesNotCopyOverrideAnnotation() {
    var classElement = getElement(HasAnnotation.class);
    var exec = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
    var method = MethodSpec.overriding(exec).build();
    assertThat(method.toString()).isEqualTo("""
        @java.lang.Override
        public java.lang.String toString() {}
        """);
  }

  @Test
  public void overrideDoesNotCopyDefaultModifier() {
    var classElement = getElement(ExtendsIterableWithDefaultMethods.class);
    var classType = (DeclaredType) classElement.asType();
    var methods = methodsIn(elements.getAllMembers(classElement));
    var exec = findFirst(methods, "spliterator");
    var method = MethodSpec.overriding(exec, classType, types).build();
    assertThat(method.toString()).isEqualTo("""
        @java.lang.Override
        public java.util.Spliterator<java.lang.Object> spliterator() {}
        """);
  }

  @Test
  public void overrideExtendsOthersWorksWithActualTypeParameters() {
    var classElement = getElement(ExtendsOthers.class);
    var classType = (DeclaredType) classElement.asType();
    var methods = methodsIn(elements.getAllMembers(classElement));
    var exec = findFirst(methods, "call");
    var method = MethodSpec.overriding(exec, classType, types).build();
    assertThat(method.toString()).isEqualTo("""
        @java.lang.Override
        public java.lang.Integer call() throws java.lang.Exception {}
        """);
    exec = findFirst(methods, "compareTo");
    method = MethodSpec.overriding(exec, classType, types).build();
    assertThat(method.toString()).isEqualTo("@java.lang.Override\n"
        + "public int compareTo(" + ExtendsOthers.class.getCanonicalName()
        + " arg0) {}\n");
    exec = findFirst(methods, "fail");
    method = MethodSpec.overriding(exec, classType, types).build();
    assertThat(method.toString()).isEqualTo("""
        @java.lang.Override
        public void fail() throws java.lang.IllegalStateException {}
        """);
  }

  @Test
  public void overrideFinalClassMethod() {
    var classElement = getElement(FinalClass.class);
    var methods = methodsIn(elements.getAllMembers(classElement));
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> overriding(findFirst(methods, "method"))
    );
    assertThat(expected).hasMessageThat().isEqualTo(
        "Cannot override method on final class com.squareup.javapoet.MethodSpecTest.FinalClass");
  }

  @Test
  public void overrideInvalidModifiers() {
    var classElement = getElement(InvalidOverrideMethods.class);
    var methods = methodsIn(elements.getAllMembers(classElement));
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> overriding(findFirst(methods, "finalMethod"))
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("cannot override method with modifiers: [final]");
    expected = assertThrows(
        IllegalArgumentException.class,
        () -> overriding(findFirst(methods, "privateMethod"))
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("cannot override method with modifiers: [private]");
    expected = assertThrows(
        IllegalArgumentException.class,
        () -> overriding(findFirst(methods, "staticMethod"))
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("cannot override method with modifiers: [static]");
  }

  @Test
  public void overrideDoesNotCopyParameterAnnotations() {
    var abstractTypeElement =
        getElement(AbstractClassWithPrivateAnnotation.class);
    var fooElement = ElementFilter
        .methodsIn(abstractTypeElement.getEnclosedElements())
        .get(0);
    var implClassName = ClassName.get("com.squareup.javapoet", "Impl");
    var type = TypeSpec.classBuilder(implClassName)
        .superclass(abstractTypeElement.asType())
        .addMethod(MethodSpec.overriding(fooElement).build())
        .build();
    var jfo = JavaFile
        .builder(implClassName.packageName, type)
        .build()
        .toJavaFileObject();
    var compilation = javac().compile(jfo);
    assertThat(compilation).succeeded();
  }

  @Test
  public void equalsAndHashCode() {
    var a = MethodSpec.constructorBuilder().build();
    var b = MethodSpec.constructorBuilder().build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    a = MethodSpec.methodBuilder("taco").build();
    b = MethodSpec.methodBuilder("taco").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    var classElement = getElement(Everything.class);
    var methodElement =
        getOnlyElement(methodsIn(classElement.getEnclosedElements()));
    a = MethodSpec.overriding(methodElement).build();
    b = MethodSpec.overriding(methodElement).build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  public void withoutParameterJavaDoc() {
    var methodSpec = MethodSpec.methodBuilder("getTaco")
        .addModifiers(Modifier.PRIVATE)
        .addParameter(PrimitiveType.Double, "money")
        .addJavadoc("Gets the best Taco\n")
        .build();
    assertThat(methodSpec.toString()).isEqualTo("""
        /**
         * Gets the best Taco
         */
        private void getTaco(double money) {}
        """);
  }

  @Test
  public void withParameterJavaDoc() {
    var methodSpec = MethodSpec.methodBuilder("getTaco")
        .addParameter(ParameterSpec.builder(PrimitiveType.Double, "money")
            .addJavadoc("the amount required to buy the taco.\n")
            .build())
        .addParameter(ParameterSpec.builder(PrimitiveType.Integer, "count")
            .addJavadoc("the number of Tacos to buy.\n")
            .build())
        .addJavadoc("Gets the best Taco money can buy.")
        .build();
    assertThat(methodSpec.toString()).isEqualTo("""
        /**
         * Gets the best Taco money can buy.
         *
         * @param money the amount required to buy the taco.
         * @param count the number of Tacos to buy.
         */
        void getTaco(double money, int count) {}
        """);
  }

  @Test
  public void withParameterJavaDocAndWithoutMethodJavadoc() {
    var methodSpec = MethodSpec.methodBuilder("getTaco")
        .addParameter(ParameterSpec.builder(PrimitiveType.Double, "money")
            .addJavadoc("the amount required to buy the taco.\n")
            .build())
        .addParameter(ParameterSpec.builder(PrimitiveType.Integer, "count")
            .addJavadoc("the number of Tacos to buy.\n")
            .build())
        .build();
    assertThat(methodSpec.toString()).isEqualTo("""
        /**
         * @param money the amount required to buy the taco.
         * @param count the number of Tacos to buy.
         */
        void getTaco(double money, int count) {}
        """);
  }

  @Test
  public void duplicateExceptionsIgnored() {
    var ioException = ClassName.get(IOException.class);
    var timeoutException = ClassName.get(TimeoutException.class);
    var methodSpec = MethodSpec.methodBuilder("duplicateExceptions")
        .addException(ioException)
        .addException(timeoutException)
        .addException(timeoutException)
        .addException(ioException)
        .build();
    assertThat(methodSpec.exceptions).isEqualTo(Arrays.asList(
        ioException,
        timeoutException
    ));
    assertThat(methodSpec
        .toBuilder()
        .addException(ioException)
        .build().exceptions)
        .isEqualTo(Arrays.asList(ioException, timeoutException));
  }

  @Test
  public void nullIsNotAValidMethodName() {
    try {
      MethodSpec.methodBuilder(null);
      fail("NullPointerException expected");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isEqualTo("name == null");
    }
  }

  @Test
  public void addModifiersVarargsShouldNotBeNull() {
    try {
      MethodSpec.methodBuilder("taco")
          .addModifiers((Modifier[]) null);
      fail("NullPointerException expected");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isEqualTo("modifiers == null");
    }
  }

  @Test
  public void modifyMethodName() {
    var methodSpec = MethodSpec.methodBuilder("initialMethod")
        .build()
        .toBuilder()
        .setName("revisedMethod")
        .build();

    assertThat(methodSpec.toString()).isEqualTo("void revisedMethod() {}\n");
  }

  @Test
  public void modifyAnnotations() {
    var builder = MethodSpec.methodBuilder("foo")
        .addAnnotation(Override.class)
        .addAnnotation(SuppressWarnings.class);

    builder.annotations.remove(1);
    assertThat(builder.build().annotations).hasSize(1);
  }

  @Test
  public void modifyModifiers() {
    var builder = MethodSpec.methodBuilder("foo")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    builder.modifiers.remove(1);
    assertThat(builder.build().modifiers).containsExactly(Modifier.PUBLIC);
  }

  @Test
  public void modifyParameters() {
    var builder = MethodSpec.methodBuilder("foo")
        .addParameter(int.class, "source");

    builder.parameters.remove(0);
    assertThat(builder.build().parameters).isEmpty();
  }

  @Test
  public void modifyTypeVariables() {
    var t = TypeVariableName.get("T");
    var builder = MethodSpec.methodBuilder("foo")
        .addTypeVariable(t)
        .addTypeVariable(TypeVariableName.get("V"));

    builder.typeVariables.remove(1);
    assertThat(builder.build().typeVariables).containsExactly(t);
  }

  @Test
  public void ensureTrailingNewline() {
    var methodSpec = MethodSpec.methodBuilder("method")
        .addCode("codeWithNoNewline();")
        .build();

    assertThat(methodSpec.toString()).isEqualTo("""
        void method() {
          codeWithNoNewline();
        }
        """);
  }

  /**
   * Ensures that we don't add a duplicate newline if one is already present.
   */
  @Test
  public void ensureTrailingNewlineWithExistingNewline() {
    var methodSpec = MethodSpec.methodBuilder("method")
        .addCode("codeWithNoNewline();\n") // Have a newline already, so ensure we're not adding one
        .build();

    assertThat(methodSpec.toString()).isEqualTo("""
        void method() {
          codeWithNoNewline();
        }
        """);
  }

  @Test
  public void controlFlowWithNamedCodeBlocks() {
    Map<String, Object> m = new HashMap<>();
    m.put("field", "valueField");
    m.put("threshold", "5");

    var methodSpec = MethodSpec.methodBuilder("method")
        .beginControlFlow(named("if ($field:N > $threshold:L)", m))
        .nextControlFlow(named("else if ($field:N == $threshold:L)", m))
        .endControlFlow()
        .build();

    assertThat(methodSpec.toString()).isEqualTo("""
        void method() {
          if (valueField > 5) {} else if (valueField == 5) {}
        }
        """);
  }

  @Test
  public void doWhileWithNamedCodeBlocks() {
    Map<String, Object> m = new HashMap<>();
    m.put("field", "valueField");
    m.put("threshold", "5");

    var methodSpec = MethodSpec.methodBuilder("method")
        .beginControlFlow("do")
        .addStatement(named("$field:N--", m))
        .endControlFlow(named("while ($field:N > $threshold:L)", m))
        .build();

    assertThat(methodSpec.toString()).isEqualTo("""
        void method() {
          do {
            valueField--;
          } while (valueField > 5);
        }
        """);
  }

  @Target(ElementType.PARAMETER)
  @interface Nullable {
  }

  @SuppressWarnings("unused")
  interface Throws<R extends RuntimeException> {
    void fail() throws R;
  }

  interface ExtendsOthers extends Callable<Integer>, Comparable<ExtendsOthers>,
      Throws<IllegalStateException> {
  }

  interface ExtendsIterableWithDefaultMethods extends Iterable<Object> {
  }

  abstract static class Everything {
    @Deprecated
    protected abstract <T extends Runnable & Closeable> Runnable everything(
        @Nullable String thing, List<? extends T> things
    ) throws IOException, SecurityException;
  }

  @SuppressWarnings({"unused", "DataFlowIssue", "RedundantThrows"})
  abstract static class Generics {
    <T, R, V extends Throwable> T run(R param) throws V {
      return null;
    }
  }

  abstract static class HasAnnotation {
    @Override
    public abstract String toString();
  }

  @SuppressWarnings("unused")
  abstract static class InvalidOverrideMethods {
    static void staticMethod() {
    }

    final void finalMethod() {
    }

    private void privateMethod() {
    }
  }

  @SuppressWarnings("unused")
  abstract static class AbstractClassWithPrivateAnnotation {

    abstract void foo(@PrivateAnnotation final String bar);

    private @interface PrivateAnnotation {
    }
  }

  @SuppressWarnings({"unused", "InnerClassMayBeStatic"})
  final class FinalClass {
    void method() {
    }
  }

}
