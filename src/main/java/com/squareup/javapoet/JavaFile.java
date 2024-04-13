/*
 * Copyright © 2015 Square, Inc., 2024 Andrew Aylett
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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.notation.Context;
import com.squareup.javapoet.notation.Notation;
import com.squareup.javapoet.notation.Printer;
import com.squareup.javapoet.prioritymap.HashPriorityMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A Java file containing a single top level class.
 */
public final class JavaFile implements Emitable {
  public static final ThreadLocal<Set<String>> CURRENT_STATIC_IMPORTS =
      ThreadLocal.withInitial(HashSet::new);

  public final CodeBlock fileComment;
  public final String packageName;
  public final TypeSpec typeSpec;
  public final boolean skipJavaLangImports;
  private final ImmutableSet<String> staticImports;
  private final ImmutableSet<String> alwaysQualify;
  private final String indent;

  private JavaFile(Builder builder) {
    this.fileComment = builder.fileComment.build();
    this.packageName = builder.packageName;
    this.typeSpec = builder.typeSpec;
    this.skipJavaLangImports = builder.skipJavaLangImports;
    this.staticImports = ImmutableSet.copyOf(builder.staticImports);
    this.indent = builder.indent;

    Set<String> alwaysQualifiedNames = new LinkedHashSet<>();
    fillAlwaysQualifiedNames(builder.typeSpec, alwaysQualifiedNames);
    this.alwaysQualify = ImmutableSet.copyOf(alwaysQualifiedNames);
  }

  public static Builder builder(String packageName, TypeSpec typeSpec) {
    checkNotNull(packageName, "packageName == null");
    checkNotNull(typeSpec, "typeSpec == null");
    return new Builder(packageName, typeSpec);
  }

  private void fillAlwaysQualifiedNames(
      TypeSpec spec, Set<String> alwaysQualifiedNames
  ) {
    alwaysQualifiedNames.addAll(spec.alwaysQualifiedNames);
    for (var nested : spec.typeSpecs) {
      fillAlwaysQualifiedNames(nested, alwaysQualifiedNames);
    }
  }

  public void writeTo(Appendable out) throws IOException {
    CURRENT_STATIC_IMPORTS.set(staticImports);
    try {
      // First pass: emit the entire class, just to collect the types we'll need to import.
      var notation = toNotation();

      var suggestedImports = notation.imports
          .stream()
          .filter(t -> !(t.withoutAnnotations() instanceof TypeVariableName))
          .sorted(ClassName.PACKAGE_COMPARATOR)
          .toList();
      var actualImports = new HashSet<ClassName>();
      var names = HashPriorityMap.from(notation.names);
      var localNames = notation.childContexts
          .stream()
          .flatMap(Context::immediateChildContextNames)
          .collect(Collectors.toSet());

      notation.childContexts.forEach(c -> c.simpleName.ifPresent(
          s -> names.put(ClassName.get(packageName, s), s)));

      var inverseNames = names
          .entrySet()
          .stream()
          .collect(Collectors.toMap(Map.Entry::getValue,
              e -> Set.of(e.getKey()),
              (a, b) -> Stream
                  .concat(a.stream(), b.stream())
                  .collect(Collectors.toSet())
          ));

      // Find package local names first
      for (var typeName : suggestedImports) {
        // All classes may be referenced by their canonical name
        names.put(typeName, typeName.canonicalName());
        inverseNames.put(typeName.canonicalName(), Set.of(typeName));
        var nameWhenImported = typeName.nameWhenImported();
        var keys = inverseNames.get(nameWhenImported);
        if ((keys == null || keys.equals(Set.of(typeName))) && typeName
            .canonicalName()
            .equals(packageName + "." + nameWhenImported)) {
          if (!localNames.contains(nameWhenImported)) {
            names.put(typeName, nameWhenImported);
            inverseNames.put(nameWhenImported, Set.of(typeName));
          }
        }
      }

      for (var typeName : suggestedImports) {
        var nameWhenImported = typeName.nameWhenImported();
        if (!alwaysQualify.contains(nameWhenImported)
            && !inverseNames.containsKey(nameWhenImported)
            && !localNames.contains(nameWhenImported)) {
          names.put(typeName, nameWhenImported);
          inverseNames.put(nameWhenImported, Set.of(typeName));
          try {
            var className = typeName.topLevelClassName();
            if (!className.packageName.isEmpty()
                && !className.packageName.equals(packageName)) {
              actualImports.add(className);
            }
          } catch (UnsupportedOperationException ignored) {
          }
        } else if (!names.containsKey(typeName)) {
          names.put(typeName, typeName.canonicalName());
          inverseNames.put(typeName.canonicalName(), Set.of(typeName));
        }
      }

      var comment = fileComment.isEmpty()
          ? empty()
          : txt("// ").then(fileComment.toNotation().indent("// "));
      var pkg =
          packageName.isEmpty() ? empty() : txt("package " + packageName + ";");

      var imports = actualImports
          .stream()
          .filter(imp -> !skipJavaLangImports || !imp.packageName.equals(
              "java.lang"))
          .sorted(ClassName.PACKAGE_COMPARATOR)
          .map(c -> txt("import " + c.canonicalName() + ";"))
          .collect(Notation.asLines());

      var statics = staticImports
          .stream()
          .sorted(ClassName.STRING_PACKAGE_COMPARATOR)
          .map(c -> txt("import static " + c + ";"))
          .collect(Notation.asLines());

      var top = Stream
          .of(comment, pkg)
          .filter(n -> !n.isEmpty())
          .collect(join(txt("\n")));

      var everything = Stream
          .of(top, imports, statics, notation)
          .filter(n -> !n.isEmpty())
          .collect(join(txt("\n\n")))
          .then(nl());

      var printer = new Printer(everything, 100, names, indent, packageName);
      printer.print(out);
    } finally {
      CURRENT_STATIC_IMPORTS.remove();
    }
  }

  /**
   * Writes this to {@code directory} as UTF-8 using the standard directory structure.
   */
  public void writeTo(Path directory) throws IOException {
    writeToPath(directory);
  }

  /**
   * Writes this to {@code directory} with the provided {@code charset} using the standard directory
   * structure.
   */
  public void writeTo(Path directory, Charset charset) throws IOException {
    writeToPath(directory, charset);
  }

  /**
   * Writes this to {@code directory} as UTF-8 using the standard directory structure.
   * Returns the {@link Path} instance to which source is actually written.
   */
  public Path writeToPath(Path directory) throws IOException {
    return writeToPath(directory, UTF_8);
  }

  /**
   * Writes this to {@code directory} with the provided {@code charset} using the standard directory
   * structure.
   * Returns the {@link Path} instance to which source is actually written.
   */
  public Path writeToPath(Path directory, Charset charset) throws IOException {
    checkArgument(
        Files.notExists(directory) || Files.isDirectory(directory),
        "path %s exists but is not a directory.",
        directory
    );
    var outputDirectory = directory;
    if (!packageName.isEmpty()) {
      for (var packageComponent : Splitter.on('.').split(packageName)) {
        outputDirectory = outputDirectory.resolve(packageComponent);
      }
      Files.createDirectories(outputDirectory);
    }

    var outputPath = outputDirectory.resolve(typeSpec.name + ".java");
    try (Writer writer = new OutputStreamWriter(Files.newOutputStream(outputPath),
        charset
    )) {
      writeTo(writer);
    }

    return outputPath;
  }

  /**
   * Writes this to {@code directory} as UTF-8 using the standard directory structure.
   */
  public void writeTo(File directory) throws IOException {
    writeTo(directory.toPath());
  }

  /**
   * Writes this to {@code directory} as UTF-8 using the standard directory structure.
   * Returns the {@link File} instance to which source is actually written.
   */
  public File writeToFile(File directory) throws IOException {
    final var outputPath = writeToPath(directory.toPath());
    return outputPath.toFile();
  }

  /**
   * Writes this to {@code filer}.
   */
  public void writeTo(Filer filer) throws IOException {
    var fileName = packageName.isEmpty()
        ? typeSpec.name
        : packageName + "." + typeSpec.name;
    var originatingElements = typeSpec.originatingElements;
    var filerSourceFile = filer.createSourceFile(fileName,
        originatingElements.toArray(new Element[0])
    );
    try (var writer = filerSourceFile.openWriter()) {
      writeTo(writer);
    } catch (Exception e) {
      try {
        filerSourceFile.delete();
      } catch (Exception suppressed) {
        e.addSuppressed(suppressed);
      }
      throw e;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    if (getClass() != o.getClass()) {
      return false;
    }
    return toString().equals(o.toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public String toString() {
    try {
      var result = new StringBuilder();
      writeTo(result);
      return result.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  public JavaFileObject toJavaFileObject() {
    var uri = URI.create((packageName.isEmpty()
        ? typeSpec.name
        : packageName.replace('.', '/') + '/' + typeSpec.name)
        + Kind.SOURCE.extension);
    return new SimpleJavaFileObject(uri, Kind.SOURCE) {
      private final long lastModified = System.currentTimeMillis();

      @Override
      public String getCharContent(boolean ignoreEncodingErrors) {
        return JavaFile.this.toString();
      }

      @Override
      public InputStream openInputStream() {
        return new ByteArrayInputStream(getCharContent(true).getBytes(UTF_8));
      }

      @Override
      public long getLastModified() {
        return lastModified;
      }
    };
  }

  public Builder toBuilder() {
    var builder = new Builder(packageName, typeSpec);
    builder.fileComment.add(fileComment);
    builder.skipJavaLangImports = skipJavaLangImports;
    builder.indent = indent;
    return builder;
  }

  @Override
  public @NotNull Notation toNotation() {
    return typeSpec.toNotation();
  }

  public static final class Builder {
    public final Set<String> staticImports = new TreeSet<>();
    private final String packageName;
    private final TypeSpec typeSpec;
    private final CodeBlock.Builder fileComment = CodeBlock.builder();
    private boolean skipJavaLangImports;
    private String indent = "  ";

    private Builder(String packageName, TypeSpec typeSpec) {
      this.packageName = packageName;
      this.typeSpec = typeSpec;
    }

    public Builder addFileComment(String format, Object... args) {
      this.fileComment.add(format, args);
      return this;
    }

    public Builder addStaticImport(Enum<?> constant) {
      return addStaticImport(ClassName.get(constant.getDeclaringClass()),
          constant.name()
      );
    }

    public Builder addStaticImport(Class<?> clazz, String... names) {
      return addStaticImport(ClassName.get(clazz), names);
    }

    public Builder addStaticImport(ClassName className, String... names) {
      checkArgument(className != null, "className == null");
      checkArgument(names != null, "names == null");
      checkArgument(names.length > 0, "names array is empty");
      for (var name : names) {
        checkArgument(name != null,
            "null entry in names array: %s",
            Arrays.toString(names)
        );
        staticImports.add(className.canonicalName + "." + name);
      }
      return this;
    }

    /**
     * Call this to omit imports for classes in {@code java.lang}, such as {@code java.lang.String}.
     *
     * <p>By default, JavaPoet explicitly imports types in {@code java.lang} to defend against
     * naming conflicts. Suppose an (ill-advised) class is named {@code com.example.String}. When
     * {@code java.lang} imports are skipped, generated code in {@code com.example} that references
     * {@code java.lang.String} will get {@code com.example.String} instead.
     */
    public Builder skipJavaLangImports(boolean skipJavaLangImports) {
      this.skipJavaLangImports = skipJavaLangImports;
      return this;
    }

    public Builder indent(String indent) {
      this.indent = indent;
      return this;
    }

    public JavaFile build() {
      return new JavaFile(this);
    }
  }
}
