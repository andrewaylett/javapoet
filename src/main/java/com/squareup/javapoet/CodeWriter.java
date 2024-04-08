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

import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.Contract;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.Util.checkState;

/**
 * Converts a {@link JavaFile} to a string suitable to both human- and javac-consumption. This
 * honors imports, indentation, and deferred variable names.
 */
public final class CodeWriter {
  private static final Pattern LINE_BREAKING_PATTERN = Pattern.compile("\\R");

  private final Deque<Notation> notation;
  private final List<TypeSpec> typeSpecStack = new ArrayList<>();
  private final Map<String, ClassName> importedTypes;
  private final Multiset<String> currentTypeVariables = new Multiset<>();
  /**
   * When emitting a statement, this is the line of the statement currently being written. The first
   * line of a statement is indented normally and subsequent wrapped lines are double-indented. This
   * is -1 when the currently-written line isn't part of a statement.
   */
  int statementLine = -1;
  private int indentLevel;
  private Optional<String> packageName = Optional.empty();

  CodeWriter(
      String indent,
      Map<String, ClassName> importedTypes,
      Set<String> staticImports,
      Set<String> alwaysQualify
  ) {
    this.notation = new ArrayDeque<>();
    notation.push(Notation.empty());
    String indent1 = checkNotNull(indent, "indent == null");
    this.importedTypes = checkNotNull(importedTypes, "importedTypes == null");
    Set<String> staticImports1 =
        checkNotNull(staticImports, "staticImports == null");
    Set<String> alwaysQualify1 =
        checkNotNull(alwaysQualify, "alwaysQualify == null");
    Set<String> staticImportClassNames = new LinkedHashSet<>();
    for (var signature : staticImports) {
      staticImportClassNames.add(signature.substring(
          0,
          signature.lastIndexOf('.')
      ));
    }
  }

  public Map<String, ClassName> importedTypes() {
    return importedTypes;
  }

  @Contract("-> this")
  public CodeWriter indent() {
    return indent(1);
  }

  @Contract("_ -> this")
  public CodeWriter indent(int levels) {
    indentLevel += levels;
    return this;
  }

  @Contract("-> this")
  public CodeWriter unindent() {
    return unindent(1);
  }

  @Contract("_ -> this")
  public CodeWriter unindent(int levels) {
    checkArgument(
        indentLevel - levels >= 0,
        "cannot unindent %s from %s",
        levels,
        indentLevel
    );
    indentLevel -= levels;
    return this;
  }

  @Contract("_ -> this")
  public CodeWriter pushPackage(String packageName) {
    checkState(
        this.packageName.isEmpty(),
        "package already set: %s",
        this.packageName
    );
    this.packageName = Optional.of(packageName);
    return this;
  }

  @Contract("-> this")
  public CodeWriter popPackage() {
    checkState(this.packageName.isPresent(), "package not set");
    this.packageName = Optional.empty();
    return this;
  }

  @Contract("_ -> this")
  public CodeWriter pushType(TypeSpec type) {
    this.typeSpecStack.add(type);
    return this;
  }

  @Contract("-> this")
  public CodeWriter popType() {
    this.typeSpecStack.remove(typeSpecStack.size() - 1);
    return this;
  }

  public void emitComment(CodeBlock codeBlock) throws IOException {
    boolean trailingNewline = true; // Force the '//' prefix for the comment.
    boolean comment = true;
    try {
      emit(codeBlock);
      emit("\n");
    } finally {
      comment = false;
    }
  }

  public void emitJavadoc(CodeBlock javadocCodeBlock) throws IOException {
    if (javadocCodeBlock.isEmpty()) {
      return;
    }

    emit("/**\n");
    boolean javadoc = true;
    try {
      emit(javadocCodeBlock, true);
    } finally {
      javadoc = false;
    }
    emit(" */\n");
  }

  public void emitAnnotations(List<AnnotationSpec> annotations, boolean inline)
      throws IOException {
    for (var annotationSpec : annotations) {
      annotationSpec.emit(this, inline);
      emit(inline ? " " : "\n");
    }
  }

  /**
   * Emits {@code modifiers} in the standard order. Modifiers in {@code implicitModifiers} will not
   * be emitted.
   */
  public void emitModifiers(
      Set<Modifier> modifiers,
      Set<Modifier> implicitModifiers
  )
      throws IOException {
    if (modifiers.isEmpty()) {
      return;
    }
    for (var modifier : EnumSet.copyOf(modifiers)) {
      if (implicitModifiers.contains(modifier)) {
        continue;
      }
      emitAndIndent(modifier.name().toLowerCase(Locale.US));
      emitAndIndent(" ");
    }
  }

  public void emitModifiers(Set<Modifier> modifiers) throws IOException {
    emitModifiers(modifiers, Collections.emptySet());
  }

  /**
   * Emit type variables with their bounds. This should only be used when declaring type variables;
   * everywhere else bounds are omitted.
   */
  public void emitTypeVariables(List<? extends TypeName> typeVariables)
      throws IOException {
    if (typeVariables.isEmpty()) {
      return;
    }

    typeVariables.forEach(type -> {
      if (type instanceof TypeVariableName typeVariable) {
        currentTypeVariables.add(typeVariable.name);
      } else if (type instanceof AnnotatedTypeName annotated) {
        if (annotated.inner instanceof TypeVariableName typeVariable) {
          currentTypeVariables.add(typeVariable.name);
        }
      } else {
        throw new UnsupportedOperationException(
            "Expected type variable, got " + type + " of class "
                + type.getClass());
      }
    });

    emit("<");
    var firstTypeVariable = true;
    for (var type : typeVariables) {
      TypeVariableName typeVariable;
      if (!firstTypeVariable) {
        emit(", ");
      }
      if (type instanceof AnnotatedTypeName annotatedTypeName) {
        emitAnnotations(annotatedTypeName.annotations, true);
        typeVariable = (TypeVariableName) annotatedTypeName.inner;
      } else {
        // Would be picked up by the forEach if not the case
        typeVariable = (TypeVariableName) type;
      }
      emit("$L", typeVariable.name);
      var firstBound = true;
      for (var bound : typeVariable.bounds) {
        emit(firstBound ? " extends $T" : " & $T", bound);
        firstBound = false;
      }
      firstTypeVariable = false;
    }
    emit(">");
  }

  public void popTypeVariables(List<? extends TypeName> typeVariables) {
    typeVariables.forEach(type -> {
      if (type instanceof TypeVariableName typeVariable) {
        currentTypeVariables.remove(typeVariable.name);
      } else if (type instanceof AnnotatedTypeName annotated) {
        if (annotated.inner instanceof TypeVariableName typeVariable) {
          currentTypeVariables.remove(typeVariable.name);
        }
      } else {
        throw new UnsupportedOperationException(
            "Expected type variable, got " + type);
      }
    });
  }

  @Contract("_ -> this")
  public CodeWriter emit(String s) throws IOException {
    return emitAndIndent(s);
  }

  @Contract("_, _ -> fail")
  public CodeWriter emit(String format, Object... args) throws IOException {
    return emit(CodeBlock.of(format, args));
  }

  @Contract("_ -> fail")
  public CodeWriter emit(CodeBlock codeBlock) throws IOException {
    return emit(codeBlock, false);
  }

  @Contract("_, _ -> fail")
  public CodeWriter emit(CodeBlock codeBlock, boolean ensureTrailingNewline)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  @Contract("-> this")
  public CodeWriter emitWrappingSpace() {
    notation.push(notation.pop().then(Notation.nl()));
    return this;
  }

  CodeWriter emitAndIndent(String s) throws IOException {
    throw new UnsupportedOperationException();
  }

  // A makeshift multi-set implementation
  private static final class Multiset<T> {
    private final Map<T, Integer> map = new LinkedHashMap<>();

    void add(T t) {
      int count = map.getOrDefault(t, 0);
      map.put(t, count + 1);
    }

    void remove(T t) {
      int count = map.getOrDefault(t, 0);
      if (count == 0) {
        throw new IllegalStateException(t + " is not in the multiset");
      }
      map.put(t, count - 1);
    }

  }
}
