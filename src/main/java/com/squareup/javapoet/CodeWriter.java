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

import javax.lang.model.SourceVersion;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.Util.checkState;
import static java.lang.String.join;

/**
 * Converts a {@link JavaFile} to a string suitable to both human- and javac-consumption. This
 * honors imports, indentation, and deferred variable names.
 */
public final class CodeWriter {
  private static final Pattern LINE_BREAKING_PATTERN = Pattern.compile("\\R");

  private final String indent;
  private final Deque<Notation> notation;
  private final List<TypeSpec> typeSpecStack = new ArrayList<>();
  private final Set<String> staticImportClassNames;
  private final Set<String> staticImports;
  private final Set<String> alwaysQualify;
  private final Map<String, ClassName> importedTypes;
  private final Map<String, ClassName> importableTypes = new LinkedHashMap<>();
  private final Set<String> referencedNames = new LinkedHashSet<>();
  private final Multiset<String> currentTypeVariables = new Multiset<>();
  /**
   * When emitting a statement, this is the line of the statement currently being written. The first
   * line of a statement is indented normally and subsequent wrapped lines are double-indented. This
   * is -1 when the currently-written line isn't part of a statement.
   */
  int statementLine = -1;
  private int indentLevel;
  private boolean javadoc = false;
  private boolean comment = false;
  private Optional<String> packageName = Optional.empty();
  private boolean trailingNewline;

  CodeWriter() {
    this("  ", Collections.emptySet(), Collections.emptySet());
  }

  CodeWriter(
      String indent,
      Set<String> staticImports,
      Set<String> alwaysQualify
  ) {
    this(indent, Collections.emptyMap(), staticImports, alwaysQualify);
  }

  CodeWriter(
      String indent,
      Map<String, ClassName> importedTypes,
      Set<String> staticImports,
      Set<String> alwaysQualify
  ) {
    this.notation = new ArrayDeque<>();
    notation.push(Notation.empty());
    this.indent = checkNotNull(indent, "indent == null");
    this.importedTypes = checkNotNull(importedTypes, "importedTypes == null");
    this.staticImports = checkNotNull(staticImports, "staticImports == null");
    this.alwaysQualify = checkNotNull(alwaysQualify, "alwaysQualify == null");
    this.staticImportClassNames = new LinkedHashSet<>();
    for (var signature : staticImports) {
      staticImportClassNames.add(signature.substring(
          0,
          signature.lastIndexOf('.')
      ));
    }
  }

  private static String extractMemberName(String part) {
    checkArgument(
        Character.isJavaIdentifierStart(part.charAt(0)),
        "not an identifier: %s",
        part
    );
    for (var i = 1; i <= part.length(); i++) {
      if (!SourceVersion.isIdentifier(part.substring(0, i))) {
        return part.substring(0, i - 1);
      }
    }
    return part;
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
    trailingNewline = true; // Force the '//' prefix for the comment.
    comment = true;
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
    javadoc = true;
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

  private boolean emitStaticImportMember(String canonical, String part)
      throws IOException {
    var partWithoutLeadingDot = part.substring(1);
    if (partWithoutLeadingDot.isEmpty()) {
      return false;
    }
    var first = partWithoutLeadingDot.charAt(0);
    if (!Character.isJavaIdentifierStart(first)) {
      return false;
    }
    var explicit = canonical + "." + extractMemberName(partWithoutLeadingDot);
    var wildcard = canonical + ".*";
    if (staticImports.contains(explicit) || staticImports.contains(wildcard)) {
      emitAndIndent(partWithoutLeadingDot);
      return true;
    }
    return false;
  }

  private void emitLiteral(Object o) throws IOException {
    if (o instanceof TypeSpec typeSpec) {
      typeSpec.emit(this, null, Collections.emptySet());
    } else if (o instanceof AnnotationSpec annotationSpec) {
      annotationSpec.emit(this, true);
    } else if (o instanceof CodeBlock codeBlock) {
      emit(codeBlock);
    } else {
      emitAndIndent(String.valueOf(o));
    }
  }

  /**
   * Returns the best name to identify {@code className} with in the current context. This uses the
   * available imports and the current scope to find the shortest name available. It does not honor
   * names visible due to inheritance.
   */
  String lookupName(ClassName className) {
    // If the top level simple name is masked by a current type variable, use the canonical name.
    var topLevelSimpleName = className.topLevelClassName().nameWhenImported();
    if (currentTypeVariables.contains(topLevelSimpleName)) {
      return className.canonicalName;
    }

    // Find the shortest suffix of className that resolves to className. This uses both local type
    // names (so `Entry` in `Map` refers to `Map.Entry`). Also uses imports.
    var nameResolved = false;
    for (TypeName c = className; c != null; c = c.enclosingClassName()) {
      var resolved = resolve(c.nameWhenImported());
      nameResolved = resolved != null;

      if (resolved != null && Objects.equals(
          resolved.canonicalName,
          c.canonicalName()
      )) {
        var suffixOffset = c.simpleNames().size() - 1;
        return join(".", className.simpleNames().subList(
            suffixOffset, className.simpleNames().size()));
      }
    }

    // If the name resolved but wasn't a match, we're stuck with the fully qualified name.
    if (nameResolved) {
      return className.canonicalName;
    }

    // If the class is in the same package, we're done.
    if (Objects.equals(packageName.orElse(""), className.packageName())) {
      referencedNames.add(topLevelSimpleName);
      return join(".", className.simpleNames());
    }

    // We'll have to use the fully-qualified name. Mark the type as importable for a future pass.
    if (!javadoc) {
      importableType(className);
    }

    return className.canonicalName;
  }

  private void importableType(ClassName className) {
    if (className.packageName().isEmpty()) {
      return;
    } else if (alwaysQualify.contains(className.simpleName)) {
      // TODO what about nested types like java.util.Map.Entry?
      return;
    }
    var topLevelClassName = className.topLevelClassName();
    var simpleName = topLevelClassName.nameWhenImported();
//    var replaced = importableTypes.put(simpleName, topLevelClassName);
//    if (replaced != null) {
//      importableTypes.put(simpleName, replaced); // On collision, prefer the first inserted.
//    }
  }

  /**
   * Returns the class referenced by {@code simpleName}, using the current nesting context and
   * imports.
   */
  // TODO(jwilson): also honor superclass members when resolving names.
  private ClassName resolve(String simpleName) {
    // Match a child of the current (potentially nested) class.
    for (var i = typeSpecStack.size() - 1; i >= 0; i--) {
      var typeSpec = typeSpecStack.get(i);
      if (typeSpec.nestedTypesSimpleNames.contains(simpleName)) {
        return stackClassName(i, simpleName);
      }
    }

    // Match the top-level class.
    if (!typeSpecStack.isEmpty() && Objects.equals(
        typeSpecStack.get(0).name,
        simpleName
    )) {
      return ClassName.get(packageName.orElse(""), simpleName);
    }

    // Match an imported type.
    return importedTypes.get(simpleName);
  }

  /**
   * Returns the class named {@code simpleName} when nested in the class at {@code stackDepth}.
   */
  private ClassName stackClassName(int stackDepth, String simpleName) {
    var className =
        ClassName.get(packageName.orElse(""), typeSpecStack.get(0).name);
    for (var i = 1; i <= stackDepth; i++) {
      className = className.nestedClass(typeSpecStack.get(i).name);
    }
    return className.nestedClass(simpleName);
  }

  CodeWriter emitAndIndent(String s) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the types that should have been imported for this code. If there were any simple name
   * collisions, that type's first use is imported.
   */
  Map<String, ClassName> suggestedImports() {
    Map<String, ClassName> result = new LinkedHashMap<>(importableTypes);
    result.keySet().removeAll(referencedNames);
    return result;
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

    boolean contains(T t) {
      return map.getOrDefault(t, 0) > 0;
    }
  }
}
