/*
 * Copyright © 2014 Square, Inc.
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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.javapoet.JavaFile.builder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createFile;
import static org.junit.Assert.assertThrows;

@RunWith(JUnit4.class)
public final class FileWritingTest {
  // Used for testing java.io File behavior.
  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  // Used for testing java.nio.file Path behavior.
  private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
  private final Path fsRoot = fs.getRootDirectories().iterator().next();

  // Used for testing annotation processor Filer behavior.
  private final TestFiler filer = new TestFiler(fs, fsRoot);

  @Test
  public void pathNotDirectory() throws IOException {
    var type = classBuilder("Test").build();
    var javaFile = builder("example", type).build();
    var path = fs.getPath("/foo/bar");
    createDirectories(path.getParent());
    createFile(path);
    var e = assertThrows(
        IllegalArgumentException.class,
        () -> javaFile.writeTo(path)
    );
    assertThat(e.getMessage()).isEqualTo(
        "path /foo/bar exists but is not a directory.");
  }

  @Test
  public void fileNotDirectory() throws IOException {
    var type = classBuilder("Test").build();
    var javaFile = builder("example", type).build();
    var file = new File(tmp.newFolder("foo"), "bar");
    file.createNewFile();
    var e = assertThrows(
        IllegalArgumentException.class,
        () -> javaFile.writeTo(file)
    );
    assertThat(e.getMessage()).isEqualTo(
        "path " + file.getPath() + " exists but is not a directory.");
  }

  @Test
  public void pathDefaultPackage() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("", type).build().writeTo(fsRoot);

    var testPath = fsRoot.resolve("Test.java");
    assertThat(Files.exists(testPath)).isTrue();
  }

  @Test
  public void fileDefaultPackage() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("", type).build().writeTo(tmp.getRoot());

    var testFile = new File(tmp.getRoot(), "Test.java");
    assertThat(testFile.exists()).isTrue();
  }

  @Test
  public void filerDefaultPackage() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("", type).build().writeTo(filer);

    var testPath = fsRoot.resolve("Test.java");
    assertThat(Files.exists(testPath)).isTrue();
  }

  @Test
  public void pathNestedClasses() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("foo", type).build().writeTo(fsRoot);
    JavaFile.builder("foo.bar", type).build().writeTo(fsRoot);
    JavaFile.builder("foo.bar.baz", type).build().writeTo(fsRoot);

    var fooPath = fsRoot.resolve(fs.getPath("foo", "Test.java"));
    var barPath = fsRoot.resolve(fs.getPath("foo", "bar", "Test.java"));
    var bazPath = fsRoot.resolve(fs.getPath("foo", "bar", "baz", "Test.java"));
    assertThat(Files.exists(fooPath)).isTrue();
    assertThat(Files.exists(barPath)).isTrue();
    assertThat(Files.exists(bazPath)).isTrue();
  }

  @Test
  public void fileNestedClasses() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("foo", type).build().writeTo(tmp.getRoot());
    JavaFile.builder("foo.bar", type).build().writeTo(tmp.getRoot());
    JavaFile.builder("foo.bar.baz", type).build().writeTo(tmp.getRoot());

    var fooDir = new File(tmp.getRoot(), "foo");
    var fooFile = new File(fooDir, "Test.java");
    var barDir = new File(fooDir, "bar");
    var barFile = new File(barDir, "Test.java");
    var bazDir = new File(barDir, "baz");
    var bazFile = new File(bazDir, "Test.java");
    assertThat(fooFile.exists()).isTrue();
    assertThat(barFile.exists()).isTrue();
    assertThat(bazFile.exists()).isTrue();
  }

  @Test
  public void filerNestedClasses() throws IOException {
    var type = TypeSpec.classBuilder("Test").build();
    JavaFile.builder("foo", type).build().writeTo(filer);
    JavaFile.builder("foo.bar", type).build().writeTo(filer);
    JavaFile.builder("foo.bar.baz", type).build().writeTo(filer);

    var fooPath = fsRoot.resolve(fs.getPath("foo", "Test.java"));
    var barPath = fsRoot.resolve(fs.getPath("foo", "bar", "Test.java"));
    var bazPath = fsRoot.resolve(fs.getPath("foo", "bar", "baz", "Test.java"));
    assertThat(Files.exists(fooPath)).isTrue();
    assertThat(Files.exists(barPath)).isTrue();
    assertThat(Files.exists(bazPath)).isTrue();
  }

  @Test
  public void filerPassesOriginatingElements() throws IOException {
    var element1_1 = Mockito.mock(Element.class);
    var test1 = TypeSpec.classBuilder("Test1")
        .addOriginatingElement(element1_1)
        .build();

    var element2_1 = Mockito.mock(Element.class);
    var element2_2 = Mockito.mock(Element.class);
    var test2 = TypeSpec.classBuilder("Test2")
        .addOriginatingElement(element2_1)
        .addOriginatingElement(element2_2)
        .build();

    JavaFile.builder("example", test1).build().writeTo(filer);
    JavaFile.builder("example", test2).build().writeTo(filer);

    var testPath1 = fsRoot.resolve(fs.getPath("example", "Test1.java"));
    assertThat(filer.getOriginatingElements(testPath1)).containsExactly(
        element1_1);
    var testPath2 = fsRoot.resolve(fs.getPath("example", "Test2.java"));
    assertThat(filer.getOriginatingElements(testPath2)).containsExactly(
        element2_1,
        element2_2
    );
  }

  @Test
  public void filerClassesWithTabIndent() throws IOException {
    var test = TypeSpec.classBuilder("Test")
        .addField(Date.class, "madeFreshDate")
        .addMethod(MethodSpec.methodBuilder("main")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(String[].class, "args")
            .addCode("$T.out.println($S);\n", System.class, "Hello World!")
            .build())
        .build();
    JavaFile.builder("foo", test).indent("\t").build().writeTo(filer);

    var fooPath = fsRoot.resolve(fs.getPath("foo", "Test.java"));
    assertThat(Files.exists(fooPath)).isTrue();
    var source = new String(Files.readAllBytes(fooPath));

    assertThat(source).isEqualTo("""
        package foo;

        import java.lang.String;
        import java.lang.System;
        import java.util.Date;

        class Test {
        \tDate madeFreshDate;

        \tpublic static void main(String[] args) {
        \t\tSystem.out.println("Hello World!");
        \t}
        }
        """);
  }

  /**
   * This test confirms that JavaPoet ignores the host charset and always uses UTF-8. The host
   * charset is customized with {@code -Dfile.encoding=ISO-8859-1}.
   */
  @Test
  public void fileIsUtf8() throws IOException {
    var javaFile =
        JavaFile.builder("foo", TypeSpec.classBuilder("Taco").build())
            .addFileComment("Piñata¡")
            .build();
    javaFile.writeTo(fsRoot);

    var fooPath = fsRoot.resolve(fs.getPath("foo", "Taco.java"));
    assertThat(Files.readString(fooPath)).isEqualTo(
        """
            // Piñata¡
            package foo;

            class Taco {}
            """);
  }

  @Test
  public void writeToPathReturnsPath() throws IOException {
    var javaFile =
        JavaFile.builder("foo", TypeSpec.classBuilder("Taco").build()).build();
    var filePath = javaFile.writeToPath(fsRoot);
    // Cast to avoid ambiguity between assertThat(Path) and assertThat(Iterable<?>)
    assertThat((Iterable<?>) filePath).isEqualTo(fsRoot.resolve(fs.getPath(
        "foo",
        "Taco.java"
    )));
  }
}
