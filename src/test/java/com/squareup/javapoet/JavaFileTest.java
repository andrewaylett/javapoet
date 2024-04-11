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

import com.google.testing.compile.CompilationRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public final class JavaFileTest {

  @Rule public final CompilationRule compilation = new CompilationRule();

  private TypeElement getElement(Class<?> clazz) {
    return compilation.getElements().getTypeElement(clazz.getCanonicalName());
  }

  @Test
  public void importStaticReadmeExample() {
    var hoverboard = ClassName.get("com.mattel", "Hoverboard");
    var namedBoards = ClassName.get("com.mattel", "Hoverboard", "Boards");
    var list = ClassName.get("java.util", "List");
    var arrayList = ClassName.get("java.util", "ArrayList");
    ObjectTypeName listOfHoverboards =
        ParameterizedTypeName.get(list, hoverboard);
    var beyond = MethodSpec.methodBuilder("beyond")
        .returns(listOfHoverboards)
        .addStatement("$T result = new $T<>()", listOfHoverboards, arrayList)
        .addStatement("result.add($T.createNimbus(2000))", hoverboard)
        .addStatement("result.add($T.createNimbus(\"2001\"))", hoverboard)
        .addStatement(
            "result.add($T.createNimbus($T.THUNDERBOLT))",
            hoverboard,
            namedBoards
        )
        .addStatement("$T.sort(result)", Collections.class)
        .addStatement(
            "return result.isEmpty() ? $T.emptyList() : result",
            Collections.class
        )
        .build();
    var hello = TypeSpec.classBuilder("HelloWorld")
        .addMethod(beyond)
        .build();
    var example = JavaFile.builder("com.example.helloworld", hello)
        .addStaticImport(hoverboard, "createNimbus")
        .addStaticImport(namedBoards, "*")
        .addStaticImport(Collections.class, "*")
        .build();
    assertThat(example.toString()).isEqualTo("""
        package com.example.helloworld;

        import com.mattel.Hoverboard;
        import java.util.ArrayList;
        import java.util.List;

        import static com.mattel.Hoverboard.Boards.*;
        import static com.mattel.Hoverboard.createNimbus;
        import static java.util.Collections.*;

        class HelloWorld {
          List<Hoverboard> beyond() {
            List<Hoverboard> result = new ArrayList<>();
            result.add(createNimbus(2000));
            result.add(createNimbus("2001"));
            result.add(createNimbus(THUNDERBOLT));
            sort(result);
            return result.isEmpty() ? emptyList() : result;
          }
        }
        """);
  }

  @Test
  public void importStaticForCrazyFormatsWorks() {
    var method = MethodSpec.methodBuilder("method").build();
    var _ignored = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addStaticBlock(CodeBlock.builder()
                    .addStatement("$T", Runtime.class)
                    .addStatement("$T.a()", Runtime.class)
                    .addStatement("$T.X", Runtime.class)
                    .addStatement("$T$T", Runtime.class, Runtime.class)
                    .addStatement("$T.$T", Runtime.class, Runtime.class)
                    .addStatement("$1T$1T", Runtime.class)
                    .addStatement("$1T$2L$1T", Runtime.class, "?")
                    .addStatement("$1T$2L$2S$1T", Runtime.class, "?")
                    .addStatement("$1T$2L$2S$1T$3N$1T", Runtime.class, "?", method)
                    .addStatement("$T$L", Runtime.class, "?")
                    .addStatement("$T$S", Runtime.class, "?")
                    .addStatement("$T$N", Runtime.class, method)
                    .build())
                .build()
        )
        .addStaticImport(Runtime.class, "*")
        .build()
        .toString(); // don't look at the generated code...
  }

  @Test
  public void importStaticMixed() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addStaticBlock(CodeBlock.builder()
                    .addStatement(
                        "assert $1T.valueOf(\"BLOCKED\") == $1T.BLOCKED",
                        Thread.State.class
                    )
                    .addStatement("$T.gc()", System.class)
                    .addStatement("$1T.out.println($1T.nanoTime())", System.class)
                    .build())
                .addMethod(MethodSpec.constructorBuilder()
                    .addParameter(Thread.State[].class, "states")
                    .varargs(true)
                    .build())
                .build()
        )
        .addStaticImport(Thread.State.BLOCKED)
        .addStaticImport(System.class, "*")
        .addStaticImport(Thread.State.class, "valueOf")
        .build();
    assertThat(source.toString()).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Thread;

        import static java.lang.System.*;
        import static java.lang.Thread.State.BLOCKED;
        import static java.lang.Thread.State.valueOf;

        class Taco {
          static {
            assert valueOf("BLOCKED") == BLOCKED;
            gc();
            out.println(nanoTime());
          }

          Taco(Thread.State... states) {}
        }
        """);
  }

  @Ignore("addStaticImport doesn't support members with $L")
  @Test
  public void importStaticDynamic() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("main")
                    .addStatement("$T.$L.println($S)", System.class, "out", "hello")
                    .build())
                .build()
        )
        .addStaticImport(System.class, "out")
        .build();
    assertThat(source.toString()).isEqualTo("""
        package com.squareup.tacos;

        import static java.lang.System.out;

        class Taco {
          void main() {
            out.println("hello");
          }
        }
        """);
  }

  @Test
  public void importStaticNone() {
    assertThat(JavaFile.builder("readme", importStaticTypeSpec("Util"))
        .build().toString()).isEqualTo("""
        package readme;

        import java.lang.System;
        import java.util.concurrent.TimeUnit;

        class Util {
          public static long minutesToSeconds(long minutes) {
            System.gc();
            return TimeUnit.SECONDS.convert(minutes, TimeUnit.MINUTES);
          }
        }
        """);
  }

  @Test
  public void importStaticOnce() {
    assertThat(JavaFile.builder("readme", importStaticTypeSpec("Util"))
        .addStaticImport(TimeUnit.SECONDS)
        .build().toString()).isEqualTo("""
        package readme;

        import java.lang.System;
        import java.util.concurrent.TimeUnit;

        import static java.util.concurrent.TimeUnit.SECONDS;

        class Util {
          public static long minutesToSeconds(long minutes) {
            System.gc();
            return SECONDS.convert(minutes, TimeUnit.MINUTES);
          }
        }
        """);
  }

  @Test
  public void importStaticTwice() {
    assertThat(JavaFile.builder("readme", importStaticTypeSpec("Util"))
        .addStaticImport(TimeUnit.SECONDS)
        .addStaticImport(TimeUnit.MINUTES)
        .build().toString()).isEqualTo("""
        package readme;

        import java.lang.System;

        import static java.util.concurrent.TimeUnit.MINUTES;
        import static java.util.concurrent.TimeUnit.SECONDS;

        class Util {
          public static long minutesToSeconds(long minutes) {
            System.gc();
            return SECONDS.convert(minutes, MINUTES);
          }
        }
        """);
  }

  @Test
  public void importStaticUsingWildcards() {
    assertThat(JavaFile.builder("readme", importStaticTypeSpec("Util"))
        .addStaticImport(TimeUnit.class, "*")
        .addStaticImport(System.class, "*")
        .build().toString()).isEqualTo("""
        package readme;

        import static java.lang.System.*;
        import static java.util.concurrent.TimeUnit.*;

        class Util {
          public static long minutesToSeconds(long minutes) {
            gc();
            return SECONDS.convert(minutes, MINUTES);
          }
        }
        """);
  }

  private TypeSpec importStaticTypeSpec(String name) {
    var method = MethodSpec.methodBuilder("minutesToSeconds")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(long.class)
        .addParameter(long.class, "minutes")
        .addStatement("$T.gc()", System.class)
        .addStatement(
            "return $1T.SECONDS.convert(minutes, $1T.MINUTES)",
            TimeUnit.class
        )
        .build();
    return TypeSpec.classBuilder(name).addMethod(method).build();

  }

  @Test
  public void noImports() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco").build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class Taco {}
        """);
  }

  @Test
  public void singleImport() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addField(Date.class, "madeFreshDate")
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import java.util.Date;

        class Taco {
          Date madeFreshDate;
        }
        """);
  }

  @Test
  public void conflictingImports() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addField(Date.class, "madeFreshDate")
                .addField(
                    ClassName.get("java.sql", "Date"),
                    "madeFreshDatabaseDate"
                )
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import java.sql.Date;

        class Taco {
          java.util.Date madeFreshDate;
          Date madeFreshDatabaseDate;
        }
        """);
  }

  @Test
  public void annotatedTypeParam() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addField(ParameterizedTypeName.get(
                    ClassName.get(List.class),
                    ClassName.get("com.squareup.meat", "Chorizo")
                        .annotated(AnnotationSpec
                            .builder(ClassName.get("com.squareup.tacos", "Spicy"))
                            .build())
                ), "chorizo")
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import com.squareup.meat.Chorizo;
        import java.util.List;

        class Taco {
          List<@Spicy Chorizo> chorizo;
        }
        """);
  }

  @Test
  public void skipJavaLangImportsWithConflictingClassLast() {
    // Whatever is used first wins! In this case the Float in java.lang is imported.
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addField(ClassName.get("java.lang", "Float"), "litres")
                .addField(ClassName.get("org.squareup.soda", "Float"), "beverage")
                .build()
        )
        .skipJavaLangImports(true)
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          Float litres;
          org.squareup.soda.Float beverage;
        }
        """);
  }

  @Test
  public void skipJavaLangImportsWithConflictingClassFirst() {
    // Whatever is used first wins! In this case the Float in com.squareup.soda is imported.
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addField(ClassName.get("com.squareup.soda", "Float"), "beverage")
                .addField(ClassName.get("java.lang", "Float"), "litres")
                .build()
        )
        .skipJavaLangImports(true)
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import com.squareup.soda.Float;

        class Taco {
          Float beverage;
          java.lang.Float litres;
        }
        """);
  }

  @Test
  public void conflictingParentName() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("A")
                .addType(TypeSpec.classBuilder("B")
                    .addType(TypeSpec.classBuilder("Twin").build())
                    .addType(TypeSpec.classBuilder("C")
                        .addField(ClassName.get(
                            "com.squareup.tacos",
                            "A",
                            "Twin",
                            "D"
                        ), "d")
                        .build())
                    .build())
                .addType(TypeSpec.classBuilder("Twin")
                    .addType(TypeSpec.classBuilder("D")
                        .build())
                    .build())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class A {
          class B {
            class Twin {}

            class C {
              A.Twin.D d;
            }
          }

          class Twin {
            class D {}
          }
        }
        """);
  }

  @Test
  public void conflictingChildName() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("A")
                .addType(TypeSpec.classBuilder("B")
                    .addType(TypeSpec.classBuilder("C")
                        .addField(ClassName.get(
                            "com.squareup.tacos",
                            "A",
                            "Twin",
                            "D"
                        ), "d")
                        .addType(TypeSpec.classBuilder("Twin").build())
                        .build())
                    .build())
                .addType(TypeSpec.classBuilder("Twin")
                    .addType(TypeSpec.classBuilder("D")
                        .build())
                    .build())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class A {
          class B {
            class C {
              A.Twin.D d;

              class Twin {}
            }
          }

          class Twin {
            class D {}
          }
        }
        """);
  }

  @Test
  public void conflictingNameOutOfScope() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("A")
                .addType(TypeSpec.classBuilder("B")
                    .addType(TypeSpec.classBuilder("C")
                        .addField(ClassName.get(
                            "com.squareup.tacos",
                            "A",
                            "Twin",
                            "D"
                        ), "d")
                        .addType(TypeSpec.classBuilder("Nested")
                            .addType(TypeSpec.classBuilder("Twin").build())
                            .build())
                        .build())
                    .build())
                .addType(TypeSpec.classBuilder("Twin")
                    .addType(TypeSpec.classBuilder("D")
                        .build())
                    .build())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class A {
          class B {
            class C {
              Twin.D d;

              class Nested {
                class Twin {}
              }
            }
          }

          class Twin {
            class D {}
          }
        }
        """);
  }

  @Test
  public void nestedClassAndSuperclassShareName() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .superclass(ClassName.get("com.squareup.wire", "Message"))
                .addType(TypeSpec.classBuilder("Builder")
                    .superclass(ClassName.get(
                        "com.squareup.wire",
                        "Message",
                        "Builder"
                    ))
                    .build())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import com.squareup.wire.Message;

        class Taco extends Message {
          class Builder extends Message.Builder {}
        }
        """);
  }

  @Test
  public void classAndSuperclassShareName() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .superclass(ClassName.get("com.taco.bell", "Taco"))
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class Taco extends com.taco.bell.Taco {}
        """);
  }

  @Test
  public void conflictingAnnotation() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addAnnotation(ClassName.get("com.taco.bell", "Taco"))
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        @com.taco.bell.Taco
        class Taco {}
        """);
  }

  @Test
  public void conflictingAnnotationReferencedClass() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addAnnotation(AnnotationSpec
                    .builder(ClassName.get("com.squareup.tacos", "MyAnno"))
                    .addMember(
                        "value",
                        "$T.class",
                        ClassName.get("com.taco.bell", "Taco")
                    )
                    .build())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        @MyAnno(com.taco.bell.Taco.class)
        class Taco {}
        """);
  }

  @Test
  public void conflictingTypeVariableBound() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addTypeVariable(
                    TypeVariableName.get(
                        "T",
                        ClassName.get("com.taco.bell", "Taco")
                    ))
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class Taco<T extends com.taco.bell.Taco> {}
        """);
  }

  @Test
  public void superclassReferencesSelf() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .superclass(ParameterizedTypeName.get(
                    ClassName.get(Comparable.class),
                    ClassName.get("com.squareup.tacos", "Taco")
                ))
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import java.lang.Comparable;

        class Taco extends Comparable<Taco> {}
        """);
  }

  /**
   * <a href="https://github.com/square/javapoet/issues/366">https://github.com/square/javapoet/issues/366</a>
   */
  @Test
  public void annotationIsNestedClass() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("TestComponent")
                .addAnnotation(ClassName.get("dagger", "Component"))
                .addType(TypeSpec.classBuilder("Builder")
                    .addAnnotation(ClassName.get("dagger", "Component", "Builder"))
                    .build())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import dagger.Component;

        @Component
        class TestComponent {
          @Component.Builder
          class Builder {}
        }
        """);
  }

  @Test
  public void defaultPackage() {
    var source = JavaFile.builder(
            "",
            TypeSpec.classBuilder("HelloWorld")
                .addMethod(MethodSpec.methodBuilder("main")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(String[].class, "args")
                    .addCode("$T.out.println($S);\n", System.class, "Hello World!")
                    .build())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        import java.lang.String;
        import java.lang.System;

        class HelloWorld {
          public static void main(String[] args) {
            System.out.println("Hello World!");
          }
        }
        """);
  }

  @Test
  public void defaultPackageTypesAreNotImported() {
    var source = JavaFile.builder(
            "hello",
            TypeSpec
                .classBuilder("World")
                .addSuperinterface(ClassName.get("", "Test"))
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package hello;

        class World implements Test {}
        """);
  }

  @Test
  public void topOfFileComment() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco").build()
        )
        .addFileComment("Generated $L by JavaPoet. DO NOT EDIT!", "2015-01-13")
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        // Generated 2015-01-13 by JavaPoet. DO NOT EDIT!
        package com.squareup.tacos;

        class Taco {}
        """);
  }

  @Test
  public void emptyLinesInTopOfFileComment() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco").build()
        )
        .addFileComment("\nGENERATED FILE:\n\nDO NOT EDIT!\n")
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        //
        // GENERATED FILE:
        //
        // DO NOT EDIT!
        //
        package com.squareup.tacos;

        class Taco {}
        """);
  }

  @Test
  public void packageClassConflictsWithNestedClass() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addField(ClassName.get("com.squareup.tacos", "A"), "a")
                .addType(TypeSpec.classBuilder("A").build())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          com.squareup.tacos.A a;

          class A {}
        }
        """);
  }

  @Test
  public void packageClassConflictsWithSuperlass() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .superclass(ClassName.get("com.taco.bell", "A"))
                .addField(ClassName.get("com.squareup.tacos", "A"), "a")
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class Taco extends com.taco.bell.A {
          A a;
        }
        """);
  }

  @Test
  public void modifyStaticImports() {
    var builder = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .build()
        )
        .addStaticImport(File.class, "separator");

    builder.staticImports.clear();
    builder.staticImports.add(File.class.getCanonicalName() + ".separatorChar");

    var source = builder.build().toString();

    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import static java.io.File.separatorChar;

        class Taco {}
        """);
  }

  @Test
  public void alwaysQualifySimple() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addField(Thread.class, "thread")
                .alwaysQualify("Thread")
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          java.lang.Thread thread;
        }
        """);
  }

  @Test
  public void alwaysQualifySupersedesJavaLangImports() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                .addField(Thread.class, "thread")
                .alwaysQualify("Thread")
                .build()
        )
        .skipJavaLangImports(true)
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        class Taco {
          java.lang.Thread thread;
        }
        """);
  }

  @Test
  public void avoidClashesWithNestedClasses_viaClass() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                // These two should get qualified
                .addField(ClassName.get("other", "NestedTypeA"), "nestedA")
                .addField(ClassName.get("other", "NestedTypeB"), "nestedB")
                // This one shouldn't since it's not a nested type of Foo
                .addField(ClassName.get("other", "NestedTypeC"), "nestedC")
                // This one shouldn't since we only look at nested types
                .addField(ClassName.get("other", "Foo"), "foo")
                .avoidClashesWithNestedClasses(Foo.class)
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import other.Foo;
        import other.NestedTypeC;

        class Taco {
          other.NestedTypeA nestedA;
          other.NestedTypeB nestedB;
          NestedTypeC nestedC;
          Foo foo;
        }
        """);
  }

  @Test
  public void avoidClashesWithNestedClasses_viaTypeElement() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                // These two should get qualified
                .addField(ClassName.get("other", "NestedTypeA"), "nestedA")
                .addField(ClassName.get("other", "NestedTypeB"), "nestedB")
                // This one shouldn't since it's not a nested type of Foo
                .addField(ClassName.get("other", "NestedTypeC"), "nestedC")
                // This one shouldn't since we only look at nested types
                .addField(ClassName.get("other", "Foo"), "foo")
                .avoidClashesWithNestedClasses(getElement(Foo.class))
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import other.Foo;
        import other.NestedTypeC;

        class Taco {
          other.NestedTypeA nestedA;
          other.NestedTypeB nestedB;
          NestedTypeC nestedC;
          Foo foo;
        }
        """);
  }

  @Test
  public void avoidClashesWithNestedClasses_viaSuperinterfaceType() {
    var source = JavaFile.builder(
            "com.squareup.tacos",
            TypeSpec.classBuilder("Taco")
                // These two should get qualified
                .addField(ClassName.get("other", "NestedTypeA"), "nestedA")
                .addField(ClassName.get("other", "NestedTypeB"), "nestedB")
                // This one shouldn't since it's not a nested type of Foo
                .addField(ClassName.get("other", "NestedTypeC"), "nestedC")
                // This one shouldn't since we only look at nested types
                .addField(ClassName.get("other", "Foo"), "foo")
                .addType(TypeSpec.classBuilder("NestedTypeA").build())
                .addType(TypeSpec.classBuilder("NestedTypeB").build())
                .addSuperinterface(FooInterface.class)
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.tacos;

        import com.squareup.javapoet.JavaFileTest;
        import other.Foo;
        import other.NestedTypeC;

        class Taco implements JavaFileTest.FooInterface {
          other.NestedTypeA nestedA;
          other.NestedTypeB nestedB;
          NestedTypeC nestedC;
          Foo foo;

          class NestedTypeA {}

          class NestedTypeB {}
        }
        """);
  }

  private TypeSpec.Builder childTypeBuilder() {
    return TypeSpec.classBuilder("Child")
        .addMethod(MethodSpec.methodBuilder("optionalString")
            .returns(ParameterizedTypeName.get(Optional.class, String.class))
            .addStatement("return $T.empty()", Optional.class)
            .build())
        .addMethod(MethodSpec.methodBuilder("pattern")
            .returns(Pattern.class)
            .addStatement("return null")
            .build());
  }

  @Test
  public void avoidClashes_parentChild_superclass_type() {
    var source = JavaFile.builder(
            "com.squareup.javapoet",
            childTypeBuilder().superclass(Parent.class).build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.javapoet;

        import java.lang.String;

        class Child extends JavaFileTest.Parent {
          java.util.Optional<String> optionalString() {
            return java.util.Optional.empty();
          }

          java.util.regex.Pattern pattern() {
            return null;
          }
        }
        """);
  }

  @Test
  public void avoidClashes_parentChild_superclass_typeMirror() {
    var source = JavaFile.builder(
            "com.squareup.javapoet",
            childTypeBuilder().superclass(getElement(Parent.class).asType()).build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.javapoet;

        import java.lang.String;

        class Child extends JavaFileTest.Parent {
          java.util.Optional<String> optionalString() {
            return java.util.Optional.empty();
          }

          java.util.regex.Pattern pattern() {
            return null;
          }
        }
        """);
  }

  @Test
  public void avoidClashes_parentChild_superinterface_type() {
    var source = JavaFile.builder(
            "com.squareup.javapoet",
            childTypeBuilder().addSuperinterface(ParentInterface.class).build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.javapoet;

        import java.lang.String;
        import java.util.regex.Pattern;

        class Child implements JavaFileTest.ParentInterface {
          java.util.Optional<String> optionalString() {
            return java.util.Optional.empty();
          }

          Pattern pattern() {
            return null;
          }
        }
        """);
  }

  @Test
  public void avoidClashes_parentChild_superinterface_typeMirror() {
    var source = JavaFile.builder(
            "com.squareup.javapoet",
            childTypeBuilder()
                .addSuperinterface(getElement(ParentInterface.class).asType())
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.javapoet;

        import java.lang.String;
        import java.util.regex.Pattern;

        class Child implements JavaFileTest.ParentInterface {
          java.util.Optional<String> optionalString() {
            return java.util.Optional.empty();
          }

          Pattern pattern() {
            return null;
          }
        }
        """);
  }

  // Regression test for case raised here: https://github.com/square/javapoet/issues/77#issuecomment-519972404
  @Test
  public void avoidClashes_mapEntry() {
    var source = JavaFile.builder(
            "com.squareup.javapoet",
            TypeSpec.classBuilder("MapType")
                .addMethod(MethodSpec.methodBuilder("optionalString")
                    .returns(ClassName.get("com.foo", "Entry"))
                    .addStatement("return null")
                    .build())
                .addSuperinterface(Map.class)
                .build()
        )
        .build()
        .toString();
    assertThat(source).isEqualTo("""
        package com.squareup.javapoet;

        import java.util.Map;

        class MapType implements Map {
          com.foo.Entry optionalString() {
            return null;
          }
        }
        """);
  }

  @SuppressWarnings("unused")
  interface FooInterface {
    class NestedTypeA {

    }

    class NestedTypeB {

    }
  }

  @SuppressWarnings("unused")
  interface ParentInterface {
    class Optional {

    }
  }

  @SuppressWarnings("unused")
  static class Foo {
    static class NestedTypeA {

    }

    static class NestedTypeB {

    }
  }

  // Regression test for https://github.com/square/javapoet/issues/77
  // This covers class and inheritance
  @SuppressWarnings("unused")
  static class Parent implements ParentInterface {
    static class Pattern {

    }
  }
}
