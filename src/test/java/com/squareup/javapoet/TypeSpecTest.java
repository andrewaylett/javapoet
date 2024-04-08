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

import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.CompilationRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventListener;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.javapoet.CodeBlock.builder;
import static com.squareup.javapoet.TypeName.get;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

@SuppressWarnings("DataFlowIssue")
@RunWith(JUnit4.class)
public final class TypeSpecTest {
  private static final String donutsPackage = "com.squareup.donuts";
  @Rule
  public final CompilationRule compilation = new CompilationRule();
  private final String tacosPackage = "com.squareup.tacos";

  private @Nullable TypeElement getElement(@NotNull Class<?> clazz) {
    return compilation.getElements().getTypeElement(clazz.getCanonicalName());
  }

  @Test
  public void basic() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(String.class)
            .addCode("return $S;\n", "taco")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;
        import java.lang.String;

        class Taco {
          @Override
          public final String toString() {
            return "taco";
          }
        }
        """);
    assertEquals(
        472949424,
        taco.hashCode()
    ); // update expected number if source changes
  }

  @Test
  public void interestingTypes() {
    ObjectTypeName listOfAny =
        ParameterizedTypeName.get(
            ClassName.get(List.class),
            WildcardTypeName.subtypeOf(Object.class)
        );
    ObjectTypeName listOfExtends =
        ParameterizedTypeName.get(
            ClassName.get(List.class),
            WildcardTypeName.subtypeOf(Serializable.class)
        );
    ObjectTypeName listOfSuper =
        ParameterizedTypeName.get(
            ClassName.get(List.class),
            WildcardTypeName.supertypeOf(String.class)
        );
    var taco = TypeSpec
        .classBuilder("Taco")
        .addField(listOfAny, "extendsObject")
        .addField(listOfExtends, "extendsSerializable")
        .addField(listOfSuper, "superString")
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.io.Serializable;
        import java.lang.String;
        import java.util.List;

        class Taco {
          List<?> extendsObject;

          List<? extends Serializable> extendsSerializable;

          List<? super String> superString;
        }
        """);
  }

  @Test
  public void anonymousInnerClass() {
    var foo = ClassName.get(tacosPackage, "Foo");
    var bar = ClassName.get(tacosPackage, "Bar");
    var thingThang = ClassName.get(tacosPackage, "Thing", "Thang");
    ObjectTypeName thingThangOfFooBar =
        ParameterizedTypeName.get(thingThang, foo, bar);
    var thung = ClassName.get(tacosPackage, "Thung");
    var simpleThung = ClassName.get(tacosPackage, "SimpleThung");
    ObjectTypeName thungOfSuperBar =
        ParameterizedTypeName.get(thung, WildcardTypeName.supertypeOf(bar));
    ObjectTypeName thungOfSuperFoo =
        ParameterizedTypeName.get(thung, WildcardTypeName.supertypeOf(foo));
    ObjectTypeName simpleThungOfBar =
        ParameterizedTypeName.get(simpleThung, bar);

    var thungParameter = ParameterSpec
        .builder(thungOfSuperFoo, "thung")
        .addModifiers(Modifier.FINAL)
        .build();
    var aSimpleThung = TypeSpec
        .anonymousClassBuilder(CodeBlock.of("$N", thungParameter))
        .superclass(simpleThungOfBar)
        .addMethod(MethodSpec
            .methodBuilder("doSomething")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(bar, "bar")
            .addCode("/* code snippets */\n")
            .build())
        .build();
    var aThingThang = TypeSpec
        .anonymousClassBuilder("")
        .superclass(thingThangOfFooBar)
        .addMethod(MethodSpec
            .methodBuilder("call")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(thungOfSuperBar)
            .addParameter(thungParameter)
            .addCode("return $L;\n", aSimpleThung)
            .build())
        .build();
    var taco = TypeSpec
        .classBuilder("Taco")
        .addField(FieldSpec
            .builder(thingThangOfFooBar, "NAME")
            .addModifiers(Modifier.STATIC, Modifier.FINAL, Modifier.FINAL)
            .initializer("$L", aThingThang)
            .build())
        .build();

    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;

        class Taco {
          static final Thing.Thang<Foo, Bar> NAME = new Thing.Thang<Foo, Bar>() {
            @Override
            public Thung<? super Bar> call(final Thung<? super Foo> thung) {
              return new SimpleThung<Bar>(thung) {
                @Override
                public void doSomething(Bar bar) {
                  /* code snippets */
                }
              };
            }
          };
        }
        """);
  }

  @Test
  public void annotatedParameters() {
    var service = TypeSpec
        .classBuilder("Foo")
        .addMethod(MethodSpec
            .constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(long.class, "id")
            .addParameter(ParameterSpec
                .builder(String.class, "one")
                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                .build())
            .addParameter(ParameterSpec
                .builder(String.class, "two")
                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                .build())
            .addParameter(ParameterSpec
                .builder(String.class, "three")
                .addAnnotation(AnnotationSpec
                    .builder(ClassName.get(tacosPackage, "Pong"))
                    .addMember("value", "$S", "pong")
                    .build())
                .build())
            .addParameter(ParameterSpec
                .builder(String.class, "four")
                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                .build())
            .addCode("/* code snippets */\n")
            .build())
        .build();

    assertThat(toString(service)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        class Foo {
          public Foo(long id, @Ping String one, @Ping String two, @Pong("pong") String three,
              @Ping String four) {
            /* code snippets */
          }
        }
        """);
  }

  /**
   * We had a bug where annotations were preventing us from doing the right thing when resolving
   * imports. <a href="https://github.com/square/javapoet/issues/422">...</a>
   */
  @Test
  public void annotationsAndJavaLangTypes() {
    var freeRange = ClassName.get("javax.annotation", "FreeRange");
    var taco = TypeSpec
        .classBuilder("EthicalTaco")
        .addField(ClassName
            .get(String.class)
            .annotated(AnnotationSpec.builder(freeRange).build()), "meat")
        .build();

    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;
        import javax.annotation.FreeRange;

        class EthicalTaco {
          @FreeRange String meat;
        }
        """);
  }

  @Test
  public void retrofitStyleInterface() {
    var observable = ClassName.get(tacosPackage, "Observable");
    var fooBar = ClassName.get(tacosPackage, "FooBar");
    var thing = ClassName.get(tacosPackage, "Thing");
    var things = ClassName.get(tacosPackage, "Things");
    var map = ClassName.get("java.util", "Map");
    var string = ClassName.get("java.lang", "String");
    var headers = ClassName.get(tacosPackage, "Headers");
    var post = ClassName.get(tacosPackage, "POST");
    var body = ClassName.get(tacosPackage, "Body");
    var queryMap = ClassName.get(tacosPackage, "QueryMap");
    var header = ClassName.get(tacosPackage, "Header");
    var service = TypeSpec
        .interfaceBuilder("Service")
        .addMethod(MethodSpec
            .methodBuilder("fooBar")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationSpec
                .builder(headers)
                .addMember("value", "$S", "Accept: application/json")
                .addMember("value", "$S", "User-Agent: foobar")
                .build())
            .addAnnotation(AnnotationSpec
                .builder(post)
                .addMember("value", "$S", "/foo/bar")
                .build())
            .returns(ParameterizedTypeName.get(observable, fooBar))
            .addParameter(ParameterSpec
                .builder(ParameterizedTypeName.get(things, thing), "things")
                .addAnnotation(body)
                .build())
            .addParameter(ParameterSpec
                .builder(
                    ParameterizedTypeName.get(map, string, string),
                    "query"
                )
                .addAnnotation(AnnotationSpec
                    .builder(queryMap)
                    .addMember("encodeValues", "false")
                    .build())
                .build())
            .addParameter(ParameterSpec
                .builder(string, "authorization")
                .addAnnotation(AnnotationSpec
                    .builder(header)
                    .addMember("value", "$S", "Authorization")
                    .build())
                .build())
            .build())
        .build();

    assertThat(toString(service)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;
        import java.util.Map;

        interface Service {
          @Headers({
              "Accept: application/json",
              "User-Agent: foobar"
          })
          @POST("/foo/bar")
          Observable<FooBar> fooBar(@Body Things<Thing> things,
              @QueryMap(encodeValues = false) Map<String, String> query,
              @Header("Authorization") String authorization);
        }
        """);
  }

  @Test
  public void annotatedField() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addField(FieldSpec
            .builder(String.class, "thing", Modifier.PRIVATE, Modifier.FINAL)
            .addAnnotation(AnnotationSpec
                .builder(ClassName.get(tacosPackage, "JsonAdapter"))
                .addMember(
                    "value",
                    "$T.class",
                    ClassName.get(tacosPackage, "Foo")
                )
                .build())
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        class Taco {
          @JsonAdapter(Foo.class)
          private final String thing;
        }
        """);
  }

  @Test
  public void annotatedClass() {
    var someType = ClassName.get(tacosPackage, "SomeType");
    var taco = TypeSpec
        .classBuilder("Foo")
        .addAnnotation(AnnotationSpec
            .builder(ClassName.get(tacosPackage, "Something"))
            .addMember("hi", "$T.$N", someType, "FIELD")
            .addMember("hey", "$L", 12)
            .addMember("hello", "$S", "goodbye")
            .build())
        .addModifiers(Modifier.PUBLIC)
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        @Something(
            hi = SomeType.FIELD,
            hey = 12,
            hello = "goodbye"
        )
        public class Foo {
        }
        """);
  }

  @SuppressWarnings("DataFlowIssue")
  @Test
  public void addAnnotationDisallowsNull() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Foo").addAnnotation((AnnotationSpec) null)
    );
    assertThat(expected).hasMessageThat().contains("annotationSpec");
    expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Foo").addAnnotation((ClassName) null)
    );
    assertThat(expected).hasMessageThat().contains("type");
    expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Foo").addAnnotation((Class<?>) null)
    );
    assertThat(expected).hasMessageThat().contains("clazz");
  }

  @Test
  public void enumWithSubclassing() {
    var roshambo = TypeSpec
        .enumBuilder("Roshambo")
        .addModifiers(Modifier.PUBLIC)
        .addEnumConstant(
            "ROCK",
            TypeSpec
                .anonymousClassBuilder("")
                .addJavadoc("Avalanche!\n")
                .build()
        )
        .addEnumConstant(
            "PAPER",
            TypeSpec
                .anonymousClassBuilder("$S", "flat")
                .addMethod(MethodSpec
                    .methodBuilder("toString")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(String.class)
                    .addCode("return $S;\n", "paper airplane!")
                    .build())
                .build()
        )
        .addEnumConstant(
            "SCISSORS",
            TypeSpec.anonymousClassBuilder("$S", "peace sign").build()
        )
        .addField(
            String.class,
            "handPosition",
            Modifier.PRIVATE,
            Modifier.FINAL
        )
        .addMethod(MethodSpec
            .constructorBuilder()
            .addParameter(String.class, "handPosition")
            .addCode("this.handPosition = handPosition;\n")
            .build())
        .addMethod(MethodSpec
            .constructorBuilder()
            .addCode("this($S);\n", "fist")
            .build())
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;
        import java.lang.String;

        public enum Roshambo {
          /**
           * Avalanche!
           */
          ROCK,

          PAPER("flat") {
            @Override
            public String toString() {
              return "paper airplane!";
            }
          },

          SCISSORS("peace sign");

          private final String handPosition;

          Roshambo(String handPosition) {
            this.handPosition = handPosition;
          }

          Roshambo() {
            this("fist");
          }
        }
        """);
  }

  /**
   * <a href="https://github.com/square/javapoet/issues/193">...</a>
   */
  @Test
  public void enumsMayDefineAbstractMethods() {
    var roshambo = TypeSpec
        .enumBuilder("Tortilla")
        .addModifiers(Modifier.PUBLIC)
        .addEnumConstant(
            "CORN",
            TypeSpec
                .anonymousClassBuilder("")
                .addMethod(MethodSpec
                    .methodBuilder("fold")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .build())
                .build()
        )
        .addMethod(MethodSpec
            .methodBuilder("fold")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build())
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;

        public enum Tortilla {
          CORN {
            @Override
            public void fold() {
            }
          };

          public abstract void fold();
        }
        """);
  }

  @Test
  public void noEnumConstants() {
    var roshambo = TypeSpec
        .enumBuilder("Roshambo")
        .addField(String.class, "NO_ENUM", Modifier.STATIC)
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        enum Roshambo {
          ;
          static String NO_ENUM;
        }
        """);
  }

  @Test
  public void onlyEnumsMayHaveEnumConstants() {
    try {
      TypeSpec.classBuilder("Roshambo").addEnumConstant("ROCK").build();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void enumWithMembersButNoConstructorCall() {
    var roshambo = TypeSpec
        .enumBuilder("Roshambo")
        .addEnumConstant(
            "SPOCK",
            TypeSpec
                .anonymousClassBuilder("")
                .addMethod(MethodSpec
                    .methodBuilder("toString")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(String.class)
                    .addCode("return $S;\n", "west side")
                    .build())
                .build()
        )
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;
        import java.lang.String;

        enum Roshambo {
          SPOCK {
            @Override
            public String toString() {
              return "west side";
            }
          }
        }
        """);
  }

  /**
   * <a href="https://github.com/square/javapoet/issues/253">...</a>
   */
  @Test
  public void enumWithAnnotatedValues() {
    var roshambo = TypeSpec
        .enumBuilder("Roshambo")
        .addModifiers(Modifier.PUBLIC)
        .addEnumConstant(
            "ROCK",
            TypeSpec
                .anonymousClassBuilder("")
                .addAnnotation(Deprecated.class)
                .build()
        )
        .addEnumConstant("PAPER")
        .addEnumConstant("SCISSORS")
        .build();
    assertThat(toString(roshambo)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Deprecated;

        public enum Roshambo {
          @Deprecated
          ROCK,

          PAPER,

          SCISSORS
        }
        """);
  }

  @Test
  public void methodThrows() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addModifiers(Modifier.ABSTRACT)
        .addMethod(MethodSpec
            .methodBuilder("throwOne")
            .addException(IOException.class)
            .build())
        .addMethod(MethodSpec
            .methodBuilder("throwTwo")
            .addException(IOException.class)
            .addException(ClassName.get(tacosPackage, "SourCreamException"))
            .build())
        .addMethod(MethodSpec
            .methodBuilder("abstractThrow")
            .addModifiers(Modifier.ABSTRACT)
            .addException(IOException.class)
            .build())
        .addMethod(MethodSpec
            .methodBuilder("nativeThrow")
            .addModifiers(Modifier.NATIVE)
            .addException(IOException.class)
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.io.IOException;

        abstract class Taco {
          void throwOne() throws IOException {
          }

          void throwTwo() throws IOException, SourCreamException {
          }

          abstract void abstractThrow() throws IOException;

          native void nativeThrow() throws IOException;
        }
        """);
  }

  @Test
  public void typeVariables() {
    var t = TypeVariableName.get("T");
    var p = TypeVariableName.get("P", Number.class);
    var location = ClassName.get(tacosPackage, "Location");
    var typeSpec = TypeSpec
        .classBuilder("Location")
        .addTypeVariable(t)
        .addTypeVariable(p)
        .addSuperinterface(ParameterizedTypeName.get(
            ClassName.get(Comparable.class),
            p
        ))
        .addField(t, "label")
        .addField(p, "x")
        .addField(p, "y")
        .addMethod(MethodSpec
            .methodBuilder("compareTo")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(int.class)
            .addParameter(p, "p")
            .addCode("return 0;\n")
            .build())
        .addMethod(MethodSpec
            .methodBuilder("of")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariable(t)
            .addTypeVariable(p)
            .returns(ParameterizedTypeName.get(location, t, p))
            .addParameter(t, "label")
            .addParameter(p, "x")
            .addParameter(p, "y")
            .addCode(
                "throw new $T($S);\n",
                UnsupportedOperationException.class,
                "TODO"
            )
            .build())
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Comparable;
        import java.lang.Number;
        import java.lang.Override;
        import java.lang.UnsupportedOperationException;

        class Location<T, P extends Number> implements Comparable<P> {
          T label;

          P x;

          P y;

          @Override
          public int compareTo(P p) {
            return 0;
          }

          public static <T, P extends Number> Location<T, P> of(T label, P x, P y) {
            throw new UnsupportedOperationException("TODO");
          }
        }
        """);
  }

  @Test
  public void typeVariableWithBounds() {
    var a = AnnotationSpec
        .builder(ClassName.get("com.squareup.tacos", "A"))
        .build();
    var p = TypeVariableName.get("P", Number.class);
    var q = TypeVariableName.get("Q", Number.class).annotated(a);
    var typeSpec = TypeSpec
        .classBuilder("Location")
        .addTypeVariable(p.withBounds(Comparable.class))
        .addTypeVariable(q.withBounds(Comparable.class))
        .addField(p, "x")
        .addField(q, "y")
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Comparable;
        import java.lang.Number;

        class Location<P extends Number & Comparable, @A Q extends Number & Comparable> {
          P x;

          @A Q y;
        }
        """);
  }

  @Test
  public void classImplementsExtends() {
    var taco = ClassName.get(tacosPackage, "Taco");
    var food = ClassName.get("com.squareup.tacos", "Food");
    var typeSpec = TypeSpec
        .classBuilder("Taco")
        .addModifiers(Modifier.ABSTRACT)
        .superclass(ParameterizedTypeName.get(
            ClassName.get(AbstractSet.class),
            food
        ))
        .addSuperinterface(Serializable.class)
        .addSuperinterface(ParameterizedTypeName.get(
            ClassName.get(Comparable.class),
            taco
        ))
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
        package com.squareup.tacos;

        import java.io.Serializable;
        import java.lang.Comparable;
        import java.util.AbstractSet;

        abstract class Taco extends AbstractSet<Food> implements Serializable, Comparable<Taco> {
        }
        """);
  }

  @Test
  public void classImplementsNestedClass() {
    var outer = ClassName.get(tacosPackage, "Outer");
    var inner = outer.nestedClass("Inner");
    var callable = ClassName.get(Callable.class);
    var typeSpec = TypeSpec
        .classBuilder("Outer")
        .superclass(ParameterizedTypeName.get(callable, inner))
        .addType(TypeSpec
            .classBuilder("Inner")
            .addModifiers(Modifier.STATIC)
            .build())
        .build();

    assertThat(toString(typeSpec)).isEqualTo("""
        package com.squareup.tacos;

        import java.util.concurrent.Callable;

        class Outer extends Callable<Outer.Inner> {
          static class Inner {
          }
        }
        """);
  }

  @Test
  public void enumImplements() {
    var typeSpec = TypeSpec
        .enumBuilder("Food")
        .addSuperinterface(Serializable.class)
        .addSuperinterface(Cloneable.class)
        .addEnumConstant("LEAN_GROUND_BEEF")
        .addEnumConstant("SHREDDED_CHEESE")
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
        package com.squareup.tacos;

        import java.io.Serializable;
        import java.lang.Cloneable;

        enum Food implements Serializable, Cloneable {
          LEAN_GROUND_BEEF,

          SHREDDED_CHEESE
        }
        """);
  }

  @Test
  public void interfaceExtends() {
    var taco = ClassName.get(tacosPackage, "Taco");
    var typeSpec = TypeSpec
        .interfaceBuilder("Taco")
        .addSuperinterface(Serializable.class)
        .addSuperinterface(ParameterizedTypeName.get(
            ClassName.get(Comparable.class),
            taco
        ))
        .build();
    assertThat(toString(typeSpec)).isEqualTo("""
        package com.squareup.tacos;

        import java.io.Serializable;
        import java.lang.Comparable;

        interface Taco extends Serializable, Comparable<Taco> {
        }
        """);
  }

  @Test
  public void nestedClasses() {
    var taco = ClassName.get(tacosPackage, "Combo", "Taco");
    var topping = ClassName.get(tacosPackage, "Combo", "Taco", "Topping");
    var chips = ClassName.get(tacosPackage, "Combo", "Chips");
    var sauce = ClassName.get(tacosPackage, "Combo", "Sauce");
    var typeSpec = TypeSpec
        .classBuilder("Combo")
        .addField(taco, "taco")
        .addField(chips, "chips")
        .addType(TypeSpec
            .classBuilder(taco.nameWhenImported())
            .addModifiers(Modifier.STATIC)
            .addField(ParameterizedTypeName.get(
                ClassName.get(List.class),
                topping
            ), "toppings")
            .addField(sauce, "sauce")
            .addType(TypeSpec
                .enumBuilder(topping.nameWhenImported())
                .addEnumConstant("SHREDDED_CHEESE")
                .addEnumConstant("LEAN_GROUND_BEEF")
                .build())
            .build())
        .addType(TypeSpec
            .classBuilder(chips.nameWhenImported())
            .addModifiers(Modifier.STATIC)
            .addField(topping, "topping")
            .addField(sauce, "dippingSauce")
            .build())
        .addType(TypeSpec
            .enumBuilder(sauce.nameWhenImported())
            .addEnumConstant("SOUR_CREAM")
            .addEnumConstant("SALSA")
            .addEnumConstant("QUESO")
            .addEnumConstant("MILD")
            .addEnumConstant("FIRE")
            .build())
        .build();

    assertThat(toString(typeSpec)).isEqualTo("""
        package com.squareup.tacos;

        import java.util.List;

        class Combo {
          Taco taco;

          Chips chips;

          static class Taco {
            List<Topping> toppings;

            Sauce sauce;

            enum Topping {
              SHREDDED_CHEESE,

              LEAN_GROUND_BEEF
            }
          }

          static class Chips {
            Taco.Topping topping;

            Sauce dippingSauce;
          }

          enum Sauce {
            SOUR_CREAM,

            SALSA,

            QUESO,

            MILD,

            FIRE
          }
        }
        """);
  }

  @Test
  public void annotation() throws Exception {
    var annotation = TypeSpec
        .annotationBuilder("MyAnnotation")
        .addModifiers(Modifier.PUBLIC)
        .addMethod(MethodSpec
            .methodBuilder("test")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .defaultValue("$L", 0)
            .returns(int.class)
            .build())
        .build();

    assertThat(toString(annotation)).isEqualTo("""
        package com.squareup.tacos;

        public @interface MyAnnotation {
          int test() default 0;
        }
        """);
  }

  @Test
  public void innerAnnotationInAnnotationDeclaration() {
    var bar = TypeSpec
        .annotationBuilder("Bar")
        .addMethod(MethodSpec
            .methodBuilder("value")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .defaultValue("@$T", Deprecated.class)
            .returns(Deprecated.class)
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Deprecated;

        @interface Bar {
          Deprecated value() default @Deprecated;
        }
        """);
  }

  @Test
  public void annotationWithFields() {
    var field = FieldSpec
        .builder(int.class, "FOO")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .initializer("$L", 101)
        .build();

    var anno = TypeSpec.annotationBuilder("Anno").addField(field).build();

    assertThat(toString(anno)).isEqualTo("""
        package com.squareup.tacos;

        @interface Anno {
          int FOO = 101;
        }
        """);
  }

  @Test
  public void classCannotHaveDefaultValueForMethod() {
    try {
      TypeSpec
          .classBuilder("Tacos")
          .addMethod(MethodSpec
              .methodBuilder("test")
              .addModifiers(Modifier.PUBLIC)
              .defaultValue("0")
              .returns(int.class)
              .build())
          .build();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void classCannotHaveDefaultMethods() {
    try {
      TypeSpec
          .classBuilder("Tacos")
          .addMethod(MethodSpec
              .methodBuilder("test")
              .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
              .returns(int.class)
              .addCode(CodeBlock.builder().addStatement("return 0").build())
              .build())
          .build();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void interfaceStaticMethods() {
    var bar = TypeSpec
        .interfaceBuilder("Tacos")
        .addMethod(MethodSpec
            .methodBuilder("test")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
        package com.squareup.tacos;

        interface Tacos {
          static int test() {
            return 0;
          }
        }
        """);
  }

  @Test
  public void interfaceDefaultMethods() {
    var bar = TypeSpec
        .interfaceBuilder("Tacos")
        .addMethod(MethodSpec
            .methodBuilder("test")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
        package com.squareup.tacos;

        interface Tacos {
          default int test() {
            return 0;
          }
        }
        """);
  }

  @Test
  public void invalidInterfacePrivateMethods() {
    try {
      TypeSpec
          .interfaceBuilder("Tacos")
          .addMethod(MethodSpec
              .methodBuilder("test")
              .addModifiers(Modifier.PRIVATE, Modifier.DEFAULT)
              .returns(int.class)
              .addCode(CodeBlock.builder().addStatement("return 0").build())
              .build())
          .build();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      TypeSpec
          .interfaceBuilder("Tacos")
          .addMethod(MethodSpec
              .methodBuilder("test")
              .addModifiers(Modifier.PRIVATE, Modifier.ABSTRACT)
              .returns(int.class)
              .build())
          .build();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      TypeSpec
          .interfaceBuilder("Tacos")
          .addMethod(MethodSpec
              .methodBuilder("test")
              .addModifiers(Modifier.PRIVATE, Modifier.PUBLIC)
              .returns(int.class)
              .addCode(CodeBlock.builder().addStatement("return 0").build())
              .build())
          .build();
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void interfacePrivateMethods() {
    var bar = TypeSpec
        .interfaceBuilder("Tacos")
        .addMethod(MethodSpec
            .methodBuilder("test")
            .addModifiers(Modifier.PRIVATE)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
        package com.squareup.tacos;

        interface Tacos {
          private int test() {
            return 0;
          }
        }
        """);

    bar = TypeSpec
        .interfaceBuilder("Tacos")
        .addMethod(MethodSpec
            .methodBuilder("test")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(int.class)
            .addCode(CodeBlock.builder().addStatement("return 0").build())
            .build())
        .build();

    assertThat(toString(bar)).isEqualTo("""
        package com.squareup.tacos;

        interface Tacos {
          private static int test() {
            return 0;
          }
        }
        """);
  }

  @Test
  public void referencedAndDeclaredSimpleNamesConflict() {
    var internalTop = FieldSpec
        .builder(ClassName.get(tacosPackage, "Top"), "internalTop")
        .build();
    var internalBottom =
        FieldSpec
            .builder(
                ClassName.get(tacosPackage, "Top", "Middle", "Bottom"),
                "internalBottom"
            )
            .build();
    var externalTop = FieldSpec
        .builder(ClassName.get(donutsPackage, "Top"), "externalTop")
        .build();
    var externalBottom = FieldSpec
        .builder(ClassName.get(donutsPackage, "Bottom"), "externalBottom")
        .build();
    var top = TypeSpec
        .classBuilder("Top")
        .addField(internalTop)
        .addField(internalBottom)
        .addField(externalTop)
        .addField(externalBottom)
        .addType(TypeSpec
            .classBuilder("Middle")
            .addField(internalTop)
            .addField(internalBottom)
            .addField(externalTop)
            .addField(externalBottom)
            .addType(TypeSpec
                .classBuilder("Bottom")
                .addField(internalTop)
                .addField(internalBottom)
                .addField(externalTop)
                .addField(externalBottom)
                .build())
            .build())
        .build();
    assertThat(toString(top)).isEqualTo("""
        package com.squareup.tacos;

        import com.squareup.donuts.Bottom;

        class Top {
          Top internalTop;

          Middle.Bottom internalBottom;

          com.squareup.donuts.Top externalTop;

          Bottom externalBottom;

          class Middle {
            Top internalTop;

            Bottom internalBottom;

            com.squareup.donuts.Top externalTop;

            com.squareup.donuts.Bottom externalBottom;

            class Bottom {
              Top internalTop;

              Bottom internalBottom;

              com.squareup.donuts.Top externalTop;

              com.squareup.donuts.Bottom externalBottom;
            }
          }
        }
        """);
  }

  @Test
  public void simpleNamesConflictInThisAndOtherPackage() {
    var internalOther = FieldSpec
        .builder(ClassName.get(tacosPackage, "Other"), "internalOther")
        .build();
    var externalOther = FieldSpec
        .builder(ClassName.get(donutsPackage, "Other"), "externalOther")
        .build();
    var gen = TypeSpec
        .classBuilder("Gen")
        .addField(internalOther)
        .addField(externalOther)
        .build();
    assertThat(toString(gen)).isEqualTo("""
        package com.squareup.tacos;

        class Gen {
          Other internalOther;

          com.squareup.donuts.Other externalOther;
        }
        """);
  }

  @Test
  public void simpleNameConflictsWithTypeVariable() {
    var inPackage = ClassName.get("com.squareup.tacos", "InPackage");
    var otherType = ClassName.get("com.other", "OtherType");
    var methodInPackage =
        ClassName.get("com.squareup.tacos", "MethodInPackage");
    var methodOtherType = ClassName.get("com.other", "MethodOtherType");
    var gen = TypeSpec
        .classBuilder("Gen")
        .addTypeVariable(TypeVariableName.get("InPackage"))
        .addTypeVariable(TypeVariableName.get("OtherType"))
        .addField(FieldSpec.builder(inPackage, "inPackage").build())
        .addField(FieldSpec.builder(otherType, "otherType").build())
        .addMethod(MethodSpec
            .methodBuilder("withTypeVariables")
            .addTypeVariable(TypeVariableName.get("MethodInPackage"))
            .addTypeVariable(TypeVariableName.get("MethodOtherType"))
            .addStatement("$T inPackage = null", methodInPackage)
            .addStatement("$T otherType = null", methodOtherType)
            .build())
        .addMethod(MethodSpec
            .methodBuilder("withoutTypeVariables")
            .addStatement("$T inPackage = null", methodInPackage)
            .addStatement("$T otherType = null", methodOtherType)
            .build())
        .addMethod(MethodSpec
            .methodBuilder("againWithTypeVariables")
            .addTypeVariable(TypeVariableName.get("MethodInPackage"))
            .addTypeVariable(TypeVariableName.get("MethodOtherType"))
            .addStatement("$T inPackage = null", methodInPackage)
            .addStatement("$T otherType = null", methodOtherType)
            .build())
        // https://github.com/square/javapoet/pull/657#discussion_r205514292
        .addMethod(MethodSpec
            .methodBuilder("masksEnclosingTypeVariable")
            .addTypeVariable(TypeVariableName.get("InPackage"))
            .build())
        .addMethod(MethodSpec
            .methodBuilder("hasSimpleNameThatWasPreviouslyMasked")
            .addStatement("$T inPackage = null", inPackage)
            .build())
        .build();
    assertThat(toString(gen)).isEqualTo("""
        package com.squareup.tacos;

        import com.other.MethodOtherType;

        class Gen<InPackage, OtherType> {
          com.squareup.tacos.InPackage inPackage;

          com.other.OtherType otherType;

          <MethodInPackage, MethodOtherType> void withTypeVariables() {
            com.squareup.tacos.MethodInPackage inPackage = null;
            com.other.MethodOtherType otherType = null;
          }

          void withoutTypeVariables() {
            MethodInPackage inPackage = null;
            MethodOtherType otherType = null;
          }

          <MethodInPackage, MethodOtherType> void againWithTypeVariables() {
            com.squareup.tacos.MethodInPackage inPackage = null;
            com.other.MethodOtherType otherType = null;
          }

          <InPackage> void masksEnclosingTypeVariable() {
          }

          void hasSimpleNameThatWasPreviouslyMasked() {
            com.squareup.tacos.InPackage inPackage = null;
          }
        }
        """);
  }

  @Test
  public void originatingElementsIncludesThoseOfNestedTypes() {
    var outerElement = Mockito.mock(Element.class);
    var innerElement = Mockito.mock(Element.class);
    var outer = TypeSpec
        .classBuilder("Outer")
        .addOriginatingElement(outerElement)
        .addType(TypeSpec
            .classBuilder("Inner")
            .addOriginatingElement(innerElement)
            .build())
        .build();
    assertThat(outer.originatingElements).containsExactly(
        outerElement,
        innerElement
    );
  }

  @Test
  public void intersectionType() {
    var typeVariable =
        TypeVariableName.get("T", Comparator.class, Serializable.class);
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("getComparator")
            .addTypeVariable(typeVariable)
            .returns(typeVariable)
            .addCode("return null;\n")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.io.Serializable;
        import java.util.Comparator;

        class Taco {
          <T extends Comparator & Serializable> T getComparator() {
            return null;
          }
        }
        """);
  }

  @Test
  public void arrayType() {
    var taco =
        TypeSpec.classBuilder("Taco").addField(int[].class, "ints").build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          int[] ints;
        }
        """);
  }

  @Test
  public void javadoc() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addJavadoc(
            "A hard or soft tortilla, loosely folded and filled with whatever {@link \n")
        .addJavadoc(
            "{@link $T random} tex-mex stuff we could find in the pantry\n",
            Random.class
        )
        .addJavadoc(CodeBlock.of("and some {@link $T} cheese.\n", String.class))
        .addField(FieldSpec
            .builder(boolean.class, "soft")
            .addJavadoc(
                "True for a soft flour tortilla; false for a crunchy corn tortilla.\n")
            .build())
        .addMethod(MethodSpec.methodBuilder("refold").addJavadoc("""
            Folds the back of this taco to reduce sauce leakage.

            <p>For {@link $T#KOREAN}, the front may also be folded.
            """, Locale.class).addParameter(Locale.class, "locale").build())
        .build();
    // Mentioning a type in Javadoc will not cause an import to be added (java.util.Random here),
    // but the short name will be used if it's already imported (java.util.Locale here).
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.util.Locale;

        /**
         * A hard or soft tortilla, loosely folded and filled with whatever {@link\s
         * {@link java.util.Random random} tex-mex stuff we could find in the pantry
         * and some {@link java.lang.String} cheese.
         */
        class Taco {
          /**
           * True for a soft flour tortilla; false for a crunchy corn tortilla.
           */
          boolean soft;

          /**
           * Folds the back of this taco to reduce sauce leakage.
           *
           * <p>For {@link Locale#KOREAN}, the front may also be folded.
           */
          void refold(Locale locale) {
          }
        }
        """);
  }

  @Test
  public void annotationsInAnnotations() {
    var beef = ClassName.get(tacosPackage, "Beef");
    var chicken = ClassName.get(tacosPackage, "Chicken");
    var option = ClassName.get(tacosPackage, "Option");
    var mealDeal = ClassName.get(tacosPackage, "MealDeal");
    var menu = TypeSpec
        .classBuilder("Menu")
        .addAnnotation(AnnotationSpec
            .builder(mealDeal)
            .addMember("price", "$L", 500)
            .addMember(
                "options",
                "$L",
                AnnotationSpec
                    .builder(option)
                    .addMember("name", "$S", "taco")
                    .addMember("meat", "$T.class", beef)
                    .build()
            )
            .addMember(
                "options",
                "$L",
                AnnotationSpec
                    .builder(option)
                    .addMember("name", "$S", "quesadilla")
                    .addMember("meat", "$T.class", chicken)
                    .build()
            )
            .build())
        .build();
    assertThat(toString(menu)).isEqualTo("""
        package com.squareup.tacos;

        @MealDeal(
            price = 500,
            options = {
                @Option(name = "taco", meat = Beef.class),
                @Option(name = "quesadilla", meat = Chicken.class)
            }
        )
        class Menu {
        }
        """);
  }

  @Test
  public void varargs() {
    var taqueria = TypeSpec
        .classBuilder("Taqueria")
        .addMethod(MethodSpec
            .methodBuilder("prepare")
            .addParameter(int.class, "workers")
            .addParameter(Runnable[].class, "jobs")
            .varargs()
            .build())
        .build();
    assertThat(toString(taqueria)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Runnable;

        class Taqueria {
          void prepare(int workers, Runnable... jobs) {
          }
        }
        """);
  }

  @Test
  public void codeBlocks() {
    var ifBlock =
        CodeBlock
            .builder()
            .beginControlFlow("if (!a.equals(b))")
            .addStatement("return i")
            .endControlFlow()
            .build();
    var methodBody = CodeBlock
        .builder()
        .addStatement(
            "$T size = $T.min(listA.size(), listB.size())",
            int.class,
            Math.class
        )
        .beginControlFlow("for ($T i = 0; i < size; i++)", int.class)
        .addStatement("$T $N = $N.get(i)", String.class, "a", "listA")
        .addStatement("$T $N = $N.get(i)", String.class, "b", "listB")
        .add("$L", ifBlock)
        .endControlFlow()
        .addStatement("return size")
        .build();
    var fieldBlock = CodeBlock
        .builder()
        .add("$>$>")
        .add(
            "\n$T.<$T, $T>builder()$>$>",
            ImmutableMap.class,
            String.class,
            String.class
        )
        .add("\n.add($S, $S)", '\'', "&#39;")
        .add("\n.add($S, $S)", '&', "&amp;")
        .add("\n.add($S, $S)", '<', "&lt;")
        .add("\n.add($S, $S)", '>', "&gt;")
        .add("\n.build()$<$<")
        .add("$<$<")
        .build();
    var escapeHtml = FieldSpec
        .builder(ParameterizedTypeName.get(
            Map.class,
            String.class,
            String.class
        ), "ESCAPE_HTML")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .initializer(fieldBlock)
        .build();
    var util = TypeSpec
        .classBuilder("Util")
        .addField(escapeHtml)
        .addMethod(MethodSpec
            .methodBuilder("commonPrefixLength")
            .returns(int.class)
            .addParameter(
                ParameterizedTypeName.get(List.class, String.class),
                "listA"
            )
            .addParameter(
                ParameterizedTypeName.get(List.class, String.class),
                "listB"
            )
            .addCode(methodBody)
            .build())
        .build();
    assertThat(toString(util)).isEqualTo("""
        package com.squareup.tacos;

        import com.google.common.collect.ImmutableMap;
        import java.lang.Math;
        import java.lang.String;
        import java.util.List;
        import java.util.Map;

        class Util {
          private static final Map<String, String> ESCAPE_HTML =\s
              ImmutableMap.<String, String>builder()
                  .add("'", "&#39;")
                  .add("&", "&amp;")
                  .add("<", "&lt;")
                  .add(">", "&gt;")
                  .build();

          int commonPrefixLength(List<String> listA, List<String> listB) {
            int size = Math.min(listA.size(), listB.size());
            for (int i = 0; i < size; i++) {
              String a = listA.get(i);
              String b = listB.get(i);
              if (!a.equals(b)) {
                return i;
              }
            }
            return size;
          }
        }
        """);
  }

  @Test
  public void indexedElseIf() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("choices")
            .beginControlFlow(
                "if ($1L != null || $1L == $2L)",
                "taco",
                "otherTaco"
            )
            .addStatement(
                "$T.out.println($S)",
                System.class,
                "only one taco? NOO!"
            )
            .nextControlFlow(
                "else if ($1L.$3L && $2L.$3L)",
                "taco",
                "otherTaco",
                "isSupreme()"
            )
            .addStatement("$T.out.println($S)", System.class, "taco heaven")
            .endControlFlow()
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.System;

        class Taco {
          void choices() {
            if (taco != null || taco == otherTaco) {
              System.out.println("only one taco? NOO!");
            } else if (taco.isSupreme() && otherTaco.isSupreme()) {
              System.out.println("taco heaven");
            }
          }
        }
        """);
  }

  @Test
  public void elseIf() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("choices")
            .beginControlFlow("if (5 < 4) ")
            .addStatement("$T.out.println($S)", System.class, "wat")
            .nextControlFlow("else if (5 < 6)")
            .addStatement("$T.out.println($S)", System.class, "hello")
            .endControlFlow()
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.System;

        class Taco {
          void choices() {
            if (5 < 4)  {
              System.out.println("wat");
            } else if (5 < 6) {
              System.out.println("hello");
            }
          }
        }
        """);
  }

  @Test
  public void doWhile() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("loopForever")
            .beginControlFlow("do")
            .addStatement("$T.out.println($S)", System.class, "hello")
            .endControlFlow("while (5 < 6)")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.System;

        class Taco {
          void loopForever() {
            do {
              System.out.println("hello");
            } while (5 < 6);
          }
        }
        """);
  }

  @Test
  public void inlineIndent() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("inlineIndent")
            .addCode(
                "if (3 < 4) {\n$>$T.out.println($S);\n$<}\n",
                System.class,
                "hello"
            )
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.System;

        class Taco {
          void inlineIndent() {
            if (3 < 4) {
              System.out.println("hello");
            }
          }
        }
        """);
  }

  @Test
  public void defaultModifiersForInterfaceMembers() {
    var taco = TypeSpec
        .interfaceBuilder("Taco")
        .addField(FieldSpec
            .builder(String.class, "SHELL")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", "crunchy corn")
            .build())
        .addMethod(MethodSpec
            .methodBuilder("fold")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build())
        .addType(TypeSpec
            .classBuilder("Topping")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        interface Taco {
          String SHELL = "crunchy corn";

          void fold();

          class Topping {
          }
        }
        """);
  }

  @Test
  public void defaultModifiersForMemberInterfacesAndEnums() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addType(TypeSpec
            .classBuilder("Meat")
            .addModifiers(Modifier.STATIC)
            .build())
        .addType(TypeSpec
            .interfaceBuilder("Tortilla")
            .addModifiers(Modifier.STATIC)
            .build())
        .addType(TypeSpec
            .enumBuilder("Topping")
            .addModifiers(Modifier.STATIC)
            .addEnumConstant("SALSA")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          static class Meat {
          }

          interface Tortilla {
          }

          enum Topping {
            SALSA
          }
        }
        """);
  }

  @Test
  public void membersOrdering() {
    // Hand out names in reverse-alphabetical order to defend against unexpected sorting.
    var taco = TypeSpec
        .classBuilder("Members")
        .addType(TypeSpec.classBuilder("Z").build())
        .addType(TypeSpec.classBuilder("Y").build())
        .addField(String.class, "X", Modifier.STATIC)
        .addField(String.class, "W")
        .addField(String.class, "V", Modifier.STATIC)
        .addField(String.class, "U")
        .addMethod(MethodSpec
            .methodBuilder("T")
            .addModifiers(Modifier.STATIC)
            .build())
        .addMethod(MethodSpec.methodBuilder("S").build())
        .addMethod(MethodSpec
            .methodBuilder("R")
            .addModifiers(Modifier.STATIC)
            .build())
        .addMethod(MethodSpec.methodBuilder("Q").build())
        .addMethod(MethodSpec
            .constructorBuilder()
            .addParameter(int.class, "p")
            .build())
        .addMethod(MethodSpec
            .constructorBuilder()
            .addParameter(long.class, "o")
            .build())
        .build();
    // Static fields, instance fields, constructors, methods, classes.
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        class Members {
          static String X;

          static String V;

          String W;

          String U;

          Members(int p) {
          }

          Members(long o) {
          }

          static void T() {
          }

          void S() {
          }

          static void R() {
          }

          void Q() {
          }

          class Z {
          }

          class Y {
          }
        }
        """);
  }

  @Test
  public void nativeMethods() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("nativeInt")
            .addModifiers(Modifier.NATIVE)
            .returns(int.class)
            .build())
        // GWT JSNI
        .addMethod(MethodSpec
            .methodBuilder("alert")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.NATIVE)
            .addParameter(String.class, "msg")
            .addCode(CodeBlock
                .builder()
                .add(" /*-{\n")
                .indent()
                .addStatement("$$wnd.alert(msg)")
                .unindent()
                .add("}-*/")
                .build())
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        class Taco {
          native int nativeInt();

          public static native void alert(String msg) /*-{
            $wnd.alert(msg);
          }-*/;
        }
        """);
  }

  @Test
  public void nullStringLiteral() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addField(FieldSpec
            .builder(String.class, "NULL")
            .initializer("$S", (Object) null)
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        class Taco {
          String NULL = null;
        }
        """);
  }

  @Test
  public void annotationToString() {
    var annotation = AnnotationSpec
        .builder(SuppressWarnings.class)
        .addMember("value", "$S", "unused")
        .build();
    assertThat(annotation.toString()).isEqualTo(
        "@java.lang.SuppressWarnings(\"unused\")");
  }

  @Test
  public void codeBlockToString() {
    var codeBlock = CodeBlock
        .builder()
        .addStatement("$T $N = $S.substring(0, 3)", String.class, "s", "taco")
        .build();
    assertThat(codeBlock.toString()).isEqualTo(
        "java.lang.String s = \"taco\".substring(0, 3);");
  }

  @Test
  public void codeBlockAddStatementOfCodeBlockToString() {
    var contents =
        CodeBlock.of("$T $N = $S.substring(0, 3)", String.class, "s", "taco");
    var statement = CodeBlock.builder().addStatement(contents).build();
    assertThat(statement.toString()).isEqualTo(
        "java.lang.String s = \"taco\".substring(0, 3);");
  }

  @Test
  public void fieldToString() {
    var field = FieldSpec
        .builder(String.class, "s", Modifier.FINAL)
        .initializer("$S.substring(0, 3)", "taco")
        .build();
    assertThat(field.toString()).isEqualTo(
        "final java.lang.String s = \"taco\".substring(0, 3);");
  }

  @Test
  public void methodToString() {
    var method = MethodSpec
        .methodBuilder("toString")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(String.class)
        .addStatement("return $S", "taco")
        .build();
    assertThat(method.toString()).isEqualTo("""
        @java.lang.Override
        public java.lang.String toString() {
          return "taco";
        }
        """);
  }

  @Test
  public void constructorToString() {
    var constructor = MethodSpec
        .constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ClassName.get(tacosPackage, "Taco"), "taco")
        .addStatement("this.$N = $N", "taco", "taco")
        .build();
    assertThat(constructor.toString()).isEqualTo("""
        public Constructor(com.squareup.tacos.Taco taco) {
          this.taco = taco;
        }
        """);
  }

  @Test
  public void parameterToString() {
    var parameter = ParameterSpec
        .builder(ClassName.get(tacosPackage, "Taco"), "taco")
        .addModifiers(Modifier.FINAL)
        .addAnnotation(ClassName.get("javax.annotation", "Nullable"))
        .build();
    assertThat(parameter.toString()).isEqualTo(
        "@javax.annotation.Nullable final com.squareup.tacos.Taco taco");
  }

  @Test
  public void classToString() {
    var type = TypeSpec.classBuilder("Taco").build();
    assertThat(type.toString()).isEqualTo("""
        class Taco {
        }
        """);
  }

  @Test
  public void anonymousClassToString() {
    var type = TypeSpec
        .anonymousClassBuilder("")
        .addSuperinterface(Runnable.class)
        .addMethod(MethodSpec
            .methodBuilder("run")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .build())
        .build();
    assertThat(type.toString()).isEqualTo("""
        new java.lang.Runnable() {
          @java.lang.Override
          public void run() {
          }
        }""");
  }

  @Test
  public void interfaceClassToString() {
    var type = TypeSpec.interfaceBuilder("Taco").build();
    assertThat(type.toString()).isEqualTo("""
        interface Taco {
        }
        """);
  }

  @Test
  public void annotationDeclarationToString() {
    var type = TypeSpec.annotationBuilder("Taco").build();
    assertThat(type.toString()).isEqualTo("""
        @interface Taco {
        }
        """);
  }

  private String toString(TypeSpec typeSpec) {
    return JavaFile.builder(tacosPackage, typeSpec).build().toString();
  }

  @Test
  public void multilineStatement() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement(
                "return $S\n+ $S\n+ $S\n+ $S\n+ $S",
                "Taco(",
                "beef,",
                "lettuce,",
                "cheese",
                ")"
            )
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;
        import java.lang.String;

        class Taco {
          @Override
          public String toString() {
            return "Taco("
                + "beef,"
                + "lettuce,"
                + "cheese"
                + ")";
          }
        }
        """);
  }

  @Test
  public void multilineStatementWithAnonymousClass() {
    ObjectTypeName stringComparator =
        ParameterizedTypeName.get(Comparator.class, String.class);
    ObjectTypeName listOfString =
        ParameterizedTypeName.get(List.class, String.class);
    var prefixComparator = TypeSpec
        .anonymousClassBuilder("")
        .addSuperinterface(stringComparator)
        .addMethod(MethodSpec
            .methodBuilder("compare")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(int.class)
            .addParameter(String.class, "a")
            .addParameter(String.class, "b")
            .addStatement("return a.substring(0, length)\n"
                + ".compareTo(b.substring(0, length))")
            .build())
        .build();
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("comparePrefix")
            .returns(stringComparator)
            .addParameter(int.class, "length", Modifier.FINAL)
            .addStatement("return $L", prefixComparator)
            .build())
        .addMethod(MethodSpec
            .methodBuilder("sortPrefix")
            .addParameter(listOfString, "list")
            .addParameter(int.class, "length", Modifier.FINAL)
            .addStatement(
                "$T.sort(\nlist,\n$L)",
                Collections.class,
                prefixComparator
            )
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;
        import java.lang.String;
        import java.util.Collections;
        import java.util.Comparator;
        import java.util.List;

        class Taco {
          Comparator<String> comparePrefix(final int length) {
            return new Comparator<String>() {
              @Override
              public int compare(String a, String b) {
                return a.substring(0, length)
                    .compareTo(b.substring(0, length));
              }
            };
          }

          void sortPrefix(List<String> list, final int length) {
            Collections.sort(
                list,
                new Comparator<String>() {
                  @Override
                  public int compare(String a, String b) {
                    return a.substring(0, length)
                        .compareTo(b.substring(0, length));
                  }
                });
          }
        }
        """);
  }

  @Test
  public void multilineStrings() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addField(FieldSpec
            .builder(String.class, "toppings")
            .initializer("$S", "shell\nbeef\nlettuce\ncheese\n")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        class Taco {
          String toppings = "shell\\n"
              + "beef\\n"
              + "lettuce\\n"
              + "cheese\\n";
        }
        """);
  }

  @Test
  public void doubleFieldInitialization() {
    try {
      FieldSpec
          .builder(String.class, "listA")
          .initializer("foo")
          .initializer("bar")
          .build();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      FieldSpec
          .builder(String.class, "listA")
          .initializer(CodeBlock.builder().add("foo").build())
          .initializer(CodeBlock.builder().add("bar").build())
          .build();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void nullAnnotationsAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addAnnotations(null)
    );
    assertThat(expected.getMessage()).contains("annotationSpecs");
  }

  @Test
  public void multipleAnnotationAddition() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addAnnotations(Arrays.asList(AnnotationSpec
            .builder(SuppressWarnings.class)
            .addMember("value", "$S", "unchecked")
            .build(), AnnotationSpec.builder(Deprecated.class).build()))
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Deprecated;
        import java.lang.SuppressWarnings;

        @SuppressWarnings("unchecked")
        @Deprecated
        class Taco {
        }
        """);
  }

  @Test
  public void nullFieldsAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addFields(null)
    );
    assertThat(expected.getMessage()).contains("fieldSpecs");
  }

  @Test
  public void multipleFieldAddition() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addFields(Arrays.asList(
            FieldSpec
                .builder(int.class, "ANSWER", Modifier.STATIC, Modifier.FINAL)
                .build(),
            FieldSpec
                .builder(BigDecimal.class, "price", Modifier.PRIVATE)
                .build()
        ))
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.math.BigDecimal;

        class Taco {
          static final int ANSWER;

          private BigDecimal price;
        }
        """);
  }

  @Test
  public void nullMethodsAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addMethods(null)
    );
    assertThat(expected).hasMessageThat().contains("methodSpecs");
  }

  @Test
  public void multipleMethodAddition() {
    var taco = TypeSpec
        .classBuilder("Taco").addMethods(Arrays.asList(
            MethodSpec
                .methodBuilder("getAnswer")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addStatement("return $L", 42)
                .build(),
            MethodSpec
                .methodBuilder("getRandomQuantity")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addJavadoc("chosen by fair dice roll ;)")
                .addStatement("return $L", 4)
                .build()
        ))
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          public static int getAnswer() {
            return 42;
          }

          /**
           * chosen by fair dice roll ;)
           */
          public int getRandomQuantity() {
            return 4;
          }
        }
        """);
  }

  @Test
  public void nullSuperinterfacesAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addSuperinterfaces(null)
    );
    assertThat(expected.getMessage()).contains("superinterfaces");
  }

  @Test
  public void nullSingleSuperinterfaceAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addSuperinterface((ObjectTypeName) null)
    );
    assertThat(expected.getMessage()).contains("superinterface");
  }

  @Test
  public void nullInSuperinterfaceIterableAddition() {
    List<TypeName> superinterfaces = new ArrayList<>();
    superinterfaces.add(get(List.class));
    superinterfaces.add(null);

    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addSuperinterfaces(superinterfaces)
    );
    assertThat(expected.getMessage()).contains("superinterface");
  }

  @Test
  public void multipleSuperinterfaceAddition() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addSuperinterfaces(Arrays.asList(
            TypeName.get(Serializable.class),
            TypeName.get(EventListener.class)
        ))
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.io.Serializable;
        import java.util.EventListener;

        class Taco implements Serializable, EventListener {
        }
        """);
  }

  @Test
  public void nullModifiersAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addModifiers((Modifier) null).build()
    );
    assertThat(expected.getMessage()).isEqualTo("modifiers contain null");
  }

  @Test
  public void nullTypeVariablesAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addTypeVariables(null)
    );
    assertThat(expected.getMessage()).contains("typeVariables");
  }

  @Test
  public void multipleTypeVariableAddition() {
    var location = TypeSpec
        .classBuilder("Location")
        .addTypeVariables(Arrays.asList(
            TypeVariableName.get("T"),
            TypeVariableName.get("P", Number.class)
        ))
        .build();
    assertThat(toString(location)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Number;

        class Location<T, P extends Number> {
        }
        """);
  }

  @Test
  public void nullTypesAddition() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> classBuilder("Taco").addTypes(null)
    );
    assertThat(expected).hasMessageThat().contains("typeSpecs");
  }

  @Test
  public void multipleTypeAddition() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addTypes(
            Arrays.asList(
                TypeSpec.classBuilder("Topping").build(),
                TypeSpec.classBuilder("Sauce").build()
            ))
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          class Topping {
          }

          class Sauce {
          }
        }
        """);
  }

  @Test
  public void tryCatch() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("addTopping")
            .addParameter(
                ClassName.get("com.squareup.tacos", "Topping"),
                "topping"
            )
            .beginControlFlow("try")
            .addCode("/* do something tricky with the topping */\n")
            .nextControlFlow(
                "catch ($T e)",
                ClassName.get("com.squareup.tacos", "IllegalToppingException")
            )
            .endControlFlow()
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          void addTopping(Topping topping) {
            try {
              /* do something tricky with the topping */
            } catch (IllegalToppingException e) {
            }
          }
        }
        """);
  }

  @Test
  public void ifElse() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec
            .methodBuilder("isDelicious")
            .addParameter(PrimitiveType.Integer, "count")
            .returns(PrimitiveType.Boolean)
            .beginControlFlow("if (count > 0)")
            .addStatement("return true")
            .nextControlFlow("else")
            .addStatement("return false")
            .endControlFlow()
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          boolean isDelicious(int count) {
            if (count > 0) {
              return true;
            } else {
              return false;
            }
          }
        }
        """);
  }

  @Test
  public void literalFromAnything() {
    var value = new Object() {
      @Override
      public String toString() {
        return "foo";
      }
    };
    assertThat(CodeBlock.of("$L", value).toString()).isEqualTo("foo");
  }

  @Test
  public void nameFromCharSequence() {
    assertThat(CodeBlock.of("$N", "text").toString()).isEqualTo("text");
  }

  @Test
  public void nameFromField() {
    var field = FieldSpec.builder(String.class, "field").build();
    assertThat(CodeBlock.of("$N", field).toString()).isEqualTo("field");
  }

  @Test
  public void nameFromParameter() {
    var parameter = ParameterSpec.builder(String.class, "parameter").build();
    assertThat(CodeBlock.of("$N", parameter).toString()).isEqualTo("parameter");
  }

  @Test
  public void nameFromMethod() {
    var method = MethodSpec
        .methodBuilder("method")
        .addModifiers(Modifier.ABSTRACT)
        .returns(String.class)
        .build();
    assertThat(CodeBlock.of("$N", method).toString()).isEqualTo("method");
  }

  @Test
  public void nameFromType() {
    var type = TypeSpec.classBuilder("Type").build();
    assertThat(CodeBlock.of("$N", type).toString()).isEqualTo("Type");
  }

  @Test
  public void nameFromUnsupportedType() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$N", String.class)
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("expected name but was " + String.class);
  }

  @Test
  public void stringFromAnything() {
    var value = new Object() {
      @Override
      public String toString() {
        return "foo";
      }
    };
    assertThat(CodeBlock.of("$S", value).toString()).isEqualTo("\"foo\"");
  }

  @Test
  public void stringFromNull() {
    assertThat(CodeBlock.of("$S", new Object[]{null}).toString()).isEqualTo(
        "null");
  }

  @Test
  public void typeFromTypeName() {
    var typeName = TypeName.get(String.class);
    assertThat(CodeBlock.of("$T", typeName).toString()).isEqualTo(
        "java.lang.String");
  }

  @Test
  public void typeFromTypeMirror() {
    var mirror = getElement(String.class).asType();
    assertThat(CodeBlock.of("$T", mirror).toString()).isEqualTo(
        "java.lang.String");
  }

  @Test
  public void typeFromTypeElement() {
    var element = getElement(String.class);
    assertThat(CodeBlock.of("$T", element).toString()).isEqualTo(
        "java.lang.String");
  }

  @Test
  public void typeFromReflectType() {
    assertThat(CodeBlock.of("$T", String.class).toString()).isEqualTo(
        "java.lang.String");
  }

  @Test
  public void typeFromUnsupportedType() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$T", "java.lang.String")
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("expected type but was a class java.lang.String");
  }

  @Test
  public void tooFewArguments() {
    var expected =
        assertThrows(IllegalArgumentException.class, () -> builder().add("$S"));
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("index 1 for '$S' not in range (received 0 arguments)");
  }

  @Test
  public void unusedArgumentsRelative() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$L $L", "a", "b", "c")
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("unused arguments: expected 2, received 3");
  }

  @Test
  public void unusedArgumentsIndexed() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1L $2L", "a", "b", "c")
    );
    assertThat(expected).hasMessageThat().isEqualTo("unused argument: $3");
    expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1L $1L $1L", "a", "b", "c")
    );
    assertThat(expected).hasMessageThat().isEqualTo("unused arguments: $2, $3");
    expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$3L $1L $3L $1L $3L", "a", "b", "c", "d")
    );
    assertThat(expected).hasMessageThat().isEqualTo("unused arguments: $2, $4");
  }

  @Test
  public void superClassOnlyValidForClasses() {
    try {
      TypeSpec.annotationBuilder("A").superclass(ClassName.get(Object.class));
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      TypeSpec.enumBuilder("E").superclass(ClassName.get(Object.class));
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      TypeSpec.interfaceBuilder("I").superclass(ClassName.get(Object.class));
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void invalidSuperClass() {
    try {
      TypeSpec
          .classBuilder("foo")
          .superclass(ClassName.get(List.class))
          .superclass(ClassName.get(Map.class));
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      TypeSpec.classBuilder("foo").superclass(PrimitiveType.Integer);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void staticCodeBlock() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addField(String.class, "foo", Modifier.PRIVATE)
        .addField(
            String.class,
            "FOO",
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL
        )
        .addStaticBlock(CodeBlock
            .builder()
            .addStatement("FOO = $S", "FOO")
            .build())
        .addMethod(MethodSpec
            .methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addCode("return FOO;\n")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;
        import java.lang.String;

        class Taco {
          private static final String FOO;

          static {
            FOO = "FOO";
          }

          private String foo;

          @Override
          public String toString() {
            return FOO;
          }
        }
        """);
  }

  @Test
  public void initializerBlockInRightPlace() {
    var taco = TypeSpec
        .classBuilder("Taco")
        .addField(String.class, "foo", Modifier.PRIVATE)
        .addField(
            String.class,
            "FOO",
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL
        )
        .addStaticBlock(CodeBlock
            .builder()
            .addStatement("FOO = $S", "FOO")
            .build())
        .addMethod(MethodSpec.constructorBuilder().build())
        .addMethod(MethodSpec
            .methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addCode("return FOO;\n")
            .build())
        .addInitializerBlock(CodeBlock
            .builder()
            .addStatement("foo = $S", "FOO")
            .build())
        .build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;
        import java.lang.String;

        class Taco {
          private static final String FOO;

          static {
            FOO = "FOO";
          }

          private String foo;

          {
            foo = "FOO";
          }

          Taco() {
          }

          @Override
          public String toString() {
            return FOO;
          }
        }
        """);
  }

  @Test
  public void initializersToBuilder() {
    // Tests if toBuilder() contains correct static and instance initializers
    Element originatingElement = getElement(TypeSpecTest.class);
    var taco = TypeSpec
        .classBuilder("Taco")
        .addField(String.class, "foo", Modifier.PRIVATE)
        .addField(
            String.class,
            "FOO",
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL
        )
        .addStaticBlock(CodeBlock
            .builder()
            .addStatement("FOO = $S", "FOO")
            .build())
        .addMethod(MethodSpec.constructorBuilder().build())
        .addMethod(MethodSpec
            .methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addCode("return FOO;\n")
            .build())
        .addInitializerBlock(CodeBlock
            .builder()
            .addStatement("foo = $S", "FOO")
            .build())
        .addOriginatingElement(originatingElement)
        .alwaysQualify("com.example.AlwaysQualified")
        .build();

    var recreatedTaco = taco.toBuilder().build();
    assertThat(toString(taco)).isEqualTo(toString(recreatedTaco));
    assertThat(taco.originatingElements).containsExactlyElementsIn(recreatedTaco.originatingElements);
    assertThat(taco.alwaysQualifiedNames).containsExactlyElementsIn(
        recreatedTaco.alwaysQualifiedNames);

    var initializersAdded = taco
        .toBuilder()
        .addInitializerBlock(CodeBlock
            .builder()
            .addStatement("foo = $S", "instanceFoo")
            .build())
        .addStaticBlock(CodeBlock
            .builder()
            .addStatement("FOO = $S", "staticFoo")
            .build())
        .build();

    assertThat(toString(initializersAdded)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Override;
        import java.lang.String;

        class Taco {
          private static final String FOO;

          static {
            FOO = "FOO";
          }
          static {
            FOO = "staticFoo";
          }

          private String foo;

          {
            foo = "FOO";
          }
          {
            foo = "instanceFoo";
          }

          Taco() {
          }

          @Override
          public String toString() {
            return FOO;
          }
        }
        """);
  }

  @Test
  public void initializerBlockUnsupportedExceptionOnInterface() {
    var interfaceBuilder = TypeSpec.interfaceBuilder("Taco");
    assertThrows(
        UnsupportedOperationException.class,
        () -> interfaceBuilder.addInitializerBlock(CodeBlock.builder().build())
    );
  }

  @Test
  public void initializerBlockUnsupportedExceptionOnAnnotation() {
    var annotationBuilder = TypeSpec.annotationBuilder("Taco");
    assertThrows(
        UnsupportedOperationException.class,
        () -> annotationBuilder.addInitializerBlock(CodeBlock.builder().build())
    );
  }

  @Test
  public void lineWrapping() {
    var methodBuilder = MethodSpec.methodBuilder("call");
    methodBuilder.addCode("$[call(");
    for (var i = 0; i < 32; i++) {
      methodBuilder.addParameter(String.class, "s" + i);
      methodBuilder.addCode(i > 0 ? ",$W$S" : "$S", i);
    }
    methodBuilder.addCode(");$]\n");

    var taco =
        TypeSpec.classBuilder("Taco").addMethod(methodBuilder.build()).build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.String;

        class Taco {
          void call(String s0, String s1, String s2, String s3, String s4, String s5, String s6, String s7,
              String s8, String s9, String s10, String s11, String s12, String s13, String s14, String s15,
              String s16, String s17, String s18, String s19, String s20, String s21, String s22,
              String s23, String s24, String s25, String s26, String s27, String s28, String s29,
              String s30, String s31) {
            call("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31");
          }
        }
        """);
  }

  @Test
  public void lineWrappingWithZeroWidthSpace() {
    var method = MethodSpec
        .methodBuilder("call")
        .addCode("$[iAmSickOfWaitingInLine($Z")
        .addCode(
            "it, has, been, far, too, long, of, a, wait, and, i, would, like, to, eat, ")
        .addCode("this, is, a, run, on, sentence")
        .addCode(");$]\n")
        .build();

    var taco = TypeSpec.classBuilder("Taco").addMethod(method).build();
    assertThat(toString(taco)).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          void call() {
            iAmSickOfWaitingInLine(
                it, has, been, far, too, long, of, a, wait, and, i, would, like, to, eat, this, is, a, run, on, sentence);
          }
        }
        """);
  }

  @Test
  public void equalsAndHashCode() {
    var a = TypeSpec.interfaceBuilder("taco").build();
    var b = TypeSpec.interfaceBuilder("taco").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    a = TypeSpec.classBuilder("taco").build();
    b = TypeSpec.classBuilder("taco").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    a = TypeSpec.enumBuilder("taco").addEnumConstant("SALSA").build();
    b = TypeSpec.enumBuilder("taco").addEnumConstant("SALSA").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    a = TypeSpec.annotationBuilder("taco").build();
    b = TypeSpec.annotationBuilder("taco").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  public void classNameFactories() {
    var className = ClassName.get("com.example", "Example");
    assertThat(TypeSpec.classBuilder(className).build().name).isEqualTo(
        "Example");
    assertThat(TypeSpec.interfaceBuilder(className).build().name).isEqualTo(
        "Example");
    assertThat(TypeSpec
        .enumBuilder(className)
        .addEnumConstant("A")
        .build().name).isEqualTo("Example");
    assertThat(TypeSpec.annotationBuilder(className).build().name).isEqualTo(
        "Example");
  }

  @Test
  public void modifyAnnotations() {
    var builder = TypeSpec
        .classBuilder("Taco")
        .addAnnotation(Override.class)
        .addAnnotation(SuppressWarnings.class);

    builder.annotations.remove(1);
    assertThat(builder.build().annotations).hasSize(1);
  }

  @Test
  public void modifyModifiers() {
    var builder = TypeSpec
        .classBuilder("Taco")
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    builder.modifiers.remove(1);
    assertThat(builder.build().modifiers).containsExactly(Modifier.PUBLIC);
  }

  @Test
  public void modifyFields() {
    var builder = TypeSpec.classBuilder("Taco").addField(int.class, "source");

    builder.fieldSpecs.remove(0);
    assertThat(builder.build().fieldSpecs).isEmpty();
  }

  @Test
  public void modifyTypeVariables() {
    var t = TypeVariableName.get("T");
    var builder = TypeSpec
        .classBuilder("Taco")
        .addTypeVariable(t)
        .addTypeVariable(TypeVariableName.get("V"));

    builder.typeVariables.remove(1);
    assertThat(builder.build().typeVariables).containsExactly(t);
  }

  @Test
  public void modifySuperinterfaces() {
    var builder = TypeSpec.classBuilder("Taco").addSuperinterface(File.class);

    builder.superinterfaces.clear();
    assertThat(builder.build().superinterfaces).isEmpty();
  }

  @Test
  public void modifyMethods() {
    var builder = TypeSpec
        .classBuilder("Taco")
        .addMethod(MethodSpec.methodBuilder("bell").build());

    builder.methodSpecs.clear();
    assertThat(builder.build().methodSpecs).isEmpty();
  }

  @Test
  public void modifyTypes() {
    var builder = TypeSpec
        .classBuilder("Taco")
        .addType(TypeSpec.classBuilder("Bell").build());

    builder.typeSpecs.clear();
    assertThat(builder.build().typeSpecs).isEmpty();
  }

  @Test
  public void modifyEnumConstants() {
    var constantType = TypeSpec.anonymousClassBuilder("").build();
    var builder = TypeSpec
        .enumBuilder("Taco")
        .addEnumConstant("BELL", constantType)
        .addEnumConstant("WUT", TypeSpec.anonymousClassBuilder("").build());

    builder.enumConstants.remove("WUT");
    assertThat(builder.build().enumConstants).containsExactly(
        "BELL",
        constantType
    );
  }

  @Test
  public void modifyOriginatingElements() {
    var builder = TypeSpec
        .classBuilder("Taco")
        .addOriginatingElement(Mockito.mock(Element.class));

    builder.originatingElements.clear();
    assertThat(builder.build().originatingElements).isEmpty();
  }

  @Test
  public void javadocWithTrailingLineDoesNotAddAnother() {
    var spec = TypeSpec
        .classBuilder("Taco")
        .addJavadoc("Some doc with a newline\n")
        .build();

    assertThat(toString(spec)).isEqualTo("""
        package com.squareup.tacos;

        /**
         * Some doc with a newline
         */
        class Taco {
        }
        """);
  }

  @Test
  public void javadocEnsuresTrailingLine() {
    var spec = TypeSpec
        .classBuilder("Taco")
        .addJavadoc("Some doc with a newline")
        .build();

    assertThat(toString(spec)).isEqualTo("""
        package com.squareup.tacos;

        /**
         * Some doc with a newline
         */
        class Taco {
        }
        """);
  }
}
