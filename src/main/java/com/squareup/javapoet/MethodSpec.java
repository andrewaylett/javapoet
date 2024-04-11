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

import com.squareup.javapoet.notation.Notate;
import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.squareup.javapoet.CodeBlock.Builder.stripNL;
import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.Util.checkState;
import static com.squareup.javapoet.notation.Notation.asLines;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;

/**
 * A generated constructor or method declaration.
 */
public final class MethodSpec implements Emitable {
  static final String CONSTRUCTOR = "<init>";

  public final String name;
  public final CodeBlock javadoc;
  public final List<AnnotationSpec> annotations;
  public final Set<Modifier> modifiers;
  public final List<TypeVariableName> typeVariables;
  public final TypeName returnType;
  public final List<ParameterSpec> parameters;
  public final boolean varargs;
  public final List<TypeName> exceptions;
  public final CodeBlock code;
  public final CodeBlock defaultValue;

  private MethodSpec(Builder builder) {
    var code = builder.code.build();
    checkArgument(
        code.isEmpty() || !builder.modifiers.contains(Modifier.ABSTRACT),
        "abstract method %s cannot have code",
        builder.name
    );
    checkArgument(
        !builder.varargs || lastParameterIsArray(builder.parameters),
        "last parameter of varargs method %s must be an array",
        builder.name
    );

    this.name = checkNotNull(builder.name, "name == null");
    this.javadoc = builder.javadoc.build();
    this.annotations = Util.immutableList(builder.annotations);
    this.modifiers = Util.immutableSet(builder.modifiers);
    this.typeVariables = Util.immutableList(builder.typeVariables);
    this.returnType = builder.returnType;
    this.parameters = Util.immutableList(builder.parameters);
    this.varargs = builder.varargs;
    this.exceptions = Util.immutableList(builder.exceptions);
    this.defaultValue = builder.defaultValue;
    this.code = code;
  }

  public static Builder methodBuilder(String name) {
    return new Builder(name);
  }

  public static Builder constructorBuilder() {
    return new Builder(CONSTRUCTOR);
  }

  /**
   * Returns a new method spec builder that overrides {@code method}.
   *
   * <p>This will copy its visibility modifiers, type parameters, return type, name, parameters, and
   * throws declarations. An {@link Override} annotation will be added.
   *
   * <p>Note that in JavaPoet 1.2 through 1.7 this method retained annotations from the method and
   * parameters of the overridden method. Since JavaPoet 1.8 annotations must be added separately.
   */
  public static Builder overriding(ExecutableElement method) {
    checkNotNull(method, "method == null");

    var enclosingClass = method.getEnclosingElement();
    if (enclosingClass.getModifiers().contains(Modifier.FINAL)) {
      throw new IllegalArgumentException(
          "Cannot override method on final class " + enclosingClass);
    }

    var modifiers = method.getModifiers();
    if (modifiers.contains(Modifier.PRIVATE)
        || modifiers.contains(Modifier.FINAL)
        || modifiers.contains(Modifier.STATIC)) {
      throw new IllegalArgumentException(
          "cannot override method with modifiers: " + modifiers);
    }

    var methodName = method.getSimpleName().toString();
    var methodBuilder = MethodSpec.methodBuilder(methodName);

    methodBuilder.addAnnotation(Override.class);

    modifiers = new LinkedHashSet<>(modifiers);
    modifiers.remove(Modifier.ABSTRACT);
    modifiers.remove(Modifier.DEFAULT);
    methodBuilder.addModifiers(modifiers);

    for (var typeParameterElement : method.getTypeParameters()) {
      var var = (TypeVariable) typeParameterElement.asType();
      methodBuilder.addTypeVariable(TypeVariableName.get(var));
    }

    methodBuilder.returns(TypeName.get(method.getReturnType()));
    methodBuilder.addParameters(ParameterSpec.parametersOf(method));
    methodBuilder.varargs(method.isVarArgs());

    for (var thrownType : method.getThrownTypes()) {
      methodBuilder.addException(TypeName.get(thrownType));
    }

    return methodBuilder;
  }

  /**
   * Returns a new method spec builder that overrides {@code method} as a member of {@code
   * enclosing}. This will resolve type parameters: for example overriding {@link
   * Comparable#compareTo} in a type that implements {@code Comparable<Movie>}, the {@code T}
   * parameter will be resolved to {@code Movie}.
   *
   * <p>This will copy its visibility modifiers, type parameters, return type, name, parameters, and
   * throws declarations. An {@link Override} annotation will be added.
   *
   * <p>Note that in JavaPoet 1.2 through 1.7 this method retained annotations from the method and
   * parameters of the overridden method. Since JavaPoet 1.8 annotations must be added separately.
   */
  public static Builder overriding(
      ExecutableElement method, DeclaredType enclosing, Types types
  ) {
    var executableType = (ExecutableType) types.asMemberOf(enclosing, method);
    var resolvedParameterTypes = executableType.getParameterTypes();
    var resolvedThrownTypes = executableType.getThrownTypes();
    var resolvedReturnType = executableType.getReturnType();

    var builder = overriding(method);
    builder.returns(TypeName.get(resolvedReturnType));
    for (int i = 0, size = builder.parameters.size(); i < size; i++) {
      var parameter = builder.parameters.get(i);
      var type = TypeName.get(resolvedParameterTypes.get(i));
      builder.parameters.set(
          i,
          parameter.toBuilder(type, parameter.name).build()
      );
    }
    builder.exceptions.clear();
    for (var resolvedThrownType : resolvedThrownTypes) {
      builder.addException(TypeName.get(resolvedThrownType));
    }

    return builder;
  }

  private boolean lastParameterIsArray(List<ParameterSpec> parameters) {
    return !parameters.isEmpty()
        && TypeName.asArray(parameters.get(parameters.size() - 1).type) != null;
  }

  private Notation javadocWithParameters() {
    var methodJavadoc = javadoc.toNotation();
    var parameterJavadoc = parameters
        .stream()
        .filter(p -> !p.javadoc.isEmpty())
        .map(p -> txt(("@param " + p.name + " " + p.javadoc).strip()))
        .collect(asLines());

    return Stream
        .of(methodJavadoc, parameterJavadoc)
        .filter(n -> !n.isEmpty())
        .collect(join(txt("\n\n"))).suppressImports();
  }

  public boolean hasModifier(Modifier modifier) {
    return modifiers.contains(modifier);
  }

  public boolean isConstructor() {
    return name.equals(CONSTRUCTOR);
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof MethodSpec that) {
      return varargs == that.varargs && Objects.equals(name, that.name)
          && Objects.equals(javadoc, that.javadoc) && Objects.equals(
          annotations,
          that.annotations
      ) && Objects.equals(modifiers, that.modifiers) && Objects.equals(
          typeVariables,
          that.typeVariables
      ) && Objects.equals(returnType, that.returnType) && Objects.equals(
          parameters,
          that.parameters
      ) && Objects.equals(exceptions, that.exceptions) && Objects.equals(
          code,
          that.code
      ) && Objects.equals(defaultValue, that.defaultValue);
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(
        name,
        javadoc,
        annotations,
        modifiers,
        typeVariables,
        returnType,
        parameters,
        varargs,
        exceptions,
        code,
        defaultValue
    );
  }

  @Override
  public String toString() {
    return toNotation().then(nl()).toCode();
  }

  public Builder toBuilder() {
    var builder = new Builder(name);
    builder.javadoc.add(javadoc);
    builder.annotations.addAll(annotations);
    builder.modifiers.addAll(modifiers);
    builder.typeVariables.addAll(typeVariables);
    builder.returnType = returnType;
    builder.parameters.addAll(parameters);
    builder.exceptions.addAll(exceptions);
    builder.code.add(code);
    builder.varargs = varargs;
    builder.defaultValue = defaultValue;
    return builder;
  }

  @Override
  public Notation toNotation() {
    return toNotation("Constructor", Set.of());
  }

  public Notation toNotation(
      String enclosingName, Set<Modifier> implicitModifiers
  ) {
    var components = new ArrayList<Notation>();
    var javadocNotation = javadocWithParameters();
    components.add(Notate.javadoc(javadocNotation));
    components.addAll(annotations.stream().map(Emitable::toNotation).toList());

    var declaration = new ArrayList<>(modifiers.stream()
        .filter(m -> !implicitModifiers.contains(m))
        .map(m -> txt(m.toString()))
        .toList());

    if (!typeVariables.isEmpty()) {
      declaration.add(Notate.wrapAndIndent(
          txt("<"),
          typeVariables
              .stream()
              .map(Emitable::toDeclaration)
              .collect(join(txt(", ").or(txt(",\n")))),
          txt(">")
      ));
    }

    if (isConstructor()) {
      declaration.add(Notation.literal(enclosingName));
    } else {
      declaration.add(returnType.toNotation());
      declaration.add(Notation.literal(name));
    }

    var partOne = declaration.stream().collect(join(txt(" ").or(nl())));

    var params = new ArrayList<Notation>();

    for (var i = parameters.iterator(); i.hasNext(); ) {
      var parameter = i.next();
      params.add(parameter.toNotation(!i.hasNext() && varargs));
    }

    var partTwo =
        params.stream().collect(join(txt(", ").or(txt(",\n"))));

    var extras = new ArrayList<Notation>();

    if (defaultValue != null && !defaultValue.isEmpty()) {
      extras.add(txt("default"));
      extras.add(defaultValue.toNotation());
    }

    if (!exceptions.isEmpty()) {
      extras.add(txt("throws"));
      extras.add(exceptions
          .stream()
          .map(Notation::typeRef)
          .collect(join(txt(", ").or(txt(",\n")))));
    }

    Notation partThree;
    if (extras.isEmpty()) {
      partThree = empty();
    } else {
      partThree = txt(" ").then(extras.stream().collect(join(txt(" "))).flat()
          .or(
              extras.stream().collect(join(txt(" "))).or(
                  extras.stream().collect(join(nl()))
              )
          ));
    }

    var method = partOne
        .then(Notate.wrapAndIndent(txt("("), partTwo, txt(")")))
        .then(partThree);

    if (hasModifier(Modifier.ABSTRACT)) {
      components.add(method.then(txt(";")));
    } else if (hasModifier(Modifier.NATIVE)) {
      // Code is allowed to support stuff like GWT JSNI.
      components.add(
          method.then(
              code.toNotation(true)).then(
              txt(";"))
      );
    } else {
      components.add(method.then(Notate.wrapAndIndentUnlessEmpty(
          txt(" {"),
          code.toNotation(true),
          txt("}")
      )));
    }
    return components
        .stream()
        .filter(n -> !n.isEmpty())
        .collect(join(nl()))
        .inContext(typeVariables);
  }

  public static final class Builder {
    public final List<TypeVariableName> typeVariables = new ArrayList<>();
    public final List<AnnotationSpec> annotations = new ArrayList<>();
    public final List<Modifier> modifiers = new ArrayList<>();
    public final List<ParameterSpec> parameters = new ArrayList<>();
    private final CodeBlock.Builder javadoc = CodeBlock.builder();
    private final Set<TypeName> exceptions = new LinkedHashSet<>();
    private final CodeBlock.Builder code = CodeBlock.builder();
    private String name;
    private @Nullable TypeName returnType;
    private boolean varargs;
    private CodeBlock defaultValue;

    private Builder(String name) {
      setName(name);
    }

    public Builder setName(String name) {
      checkNotNull(name, "name == null");
      checkArgument(
          name.equals(CONSTRUCTOR) || SourceVersion.isName(name),
          "not a valid name: %s",
          name
      );
      this.name = name;
      this.returnType = name.equals(CONSTRUCTOR) ? null : PrimitiveType.Void;
      return this;
    }

    public Builder addJavadoc(String format, Object... args) {
      javadoc.add(format.strip(), args);
      return this;
    }

    public Builder addJavadoc(CodeBlock block) {
      javadoc.add(block);
      return this;
    }

    public Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
      checkArgument(annotationSpecs != null, "annotationSpecs == null");
      for (var annotationSpec : annotationSpecs) {
        this.annotations.add(annotationSpec);
      }
      return this;
    }

    public Builder addAnnotation(AnnotationSpec annotationSpec) {
      this.annotations.add(annotationSpec);
      return this;
    }

    public Builder addAnnotation(ClassName annotation) {
      this.annotations.add(AnnotationSpec.builder(annotation).build());
      return this;
    }

    public Builder addAnnotation(Class<?> annotation) {
      return addAnnotation(ClassName.get(annotation));
    }

    public Builder addModifiers(Modifier... modifiers) {
      checkNotNull(modifiers, "modifiers == null");
      Collections.addAll(this.modifiers, modifiers);
      return this;
    }

    public Builder addModifiers(Iterable<Modifier> modifiers) {
      checkNotNull(modifiers, "modifiers == null");
      for (var modifier : modifiers) {
        this.modifiers.add(modifier);
      }
      return this;
    }

    public Builder addTypeVariables(Iterable<TypeVariableName> typeVariables) {
      checkArgument(typeVariables != null, "typeVariables == null");
      for (var typeVariable : typeVariables) {
        this.typeVariables.add(typeVariable);
      }
      return this;
    }

    public Builder addTypeVariable(TypeVariableName typeVariable) {
      typeVariables.add(typeVariable);
      return this;
    }

    public Builder returns(TypeName returnType) {
      checkState(
          !name.equals(CONSTRUCTOR),
          "constructor cannot have return type."
      );
      this.returnType = returnType;
      return this;
    }

    public Builder returns(Type returnType) {
      return returns(TypeName.get(returnType));
    }

    public Builder addParameters(Iterable<ParameterSpec> parameterSpecs) {
      checkArgument(parameterSpecs != null, "parameterSpecs == null");
      for (var parameterSpec : parameterSpecs) {
        this.parameters.add(parameterSpec);
      }
      return this;
    }

    public Builder addParameter(ParameterSpec parameterSpec) {
      this.parameters.add(parameterSpec);
      return this;
    }

    public Builder addParameter(
        TypeName type, String name, Modifier... modifiers
    ) {
      return addParameter(ParameterSpec.builder(type, name, modifiers).build());
    }

    public Builder addParameter(Type type, String name, Modifier... modifiers) {
      return addParameter(TypeName.get(type), name, modifiers);
    }

    public Builder varargs() {
      return varargs(true);
    }

    public Builder varargs(boolean varargs) {
      this.varargs = varargs;
      return this;
    }

    public Builder addExceptions(Iterable<? extends ObjectTypeName> exceptions) {
      checkArgument(exceptions != null, "exceptions == null");
      for (var exception : exceptions) {
        this.exceptions.add(exception);
      }
      return this;
    }

    public Builder addException(TypeName exception) {
      this.exceptions.add(exception);
      return this;
    }

    public Builder addException(Type exception) {
      return addException(TypeName.get(exception));
    }

    public Builder addCode(String format, Object... args) {
      code.add(stripNL(format), args);
      return this;
    }

    public Builder addNamedCode(String format, Map<String, ?> args) {
      code.addNamed(stripNL(format), args);
      return this;
    }

    public Builder addCode(CodeBlock codeBlock) {
      code.add(codeBlock);
      return this;
    }

    public Builder addComment(String format, Object... args) {
      code.add("\n// " + format.strip(), args);
      return this;
    }

    public Builder defaultValue(String format, Object... args) {
      return defaultValue(CodeBlock.of(format.strip(), args));
    }

    public Builder defaultValue(CodeBlock codeBlock) {
      checkState(this.defaultValue == null, "defaultValue was already set");
      this.defaultValue = checkNotNull(codeBlock, "codeBlock == null");
      return this;
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "if (foo == 5)".
     *                    Shouldn't contain braces or newline characters.
     */
    public Builder beginControlFlow(String controlFlow, Object... args) {
      code.beginControlFlow(controlFlow, args);
      return this;
    }

    /**
     * @param codeBlock the control flow construct and its code, such as "if (foo == 5)".
     *                  Shouldn't contain braces or newline characters.
     */
    public Builder beginControlFlow(CodeBlock codeBlock) {
      return beginControlFlow("$L", codeBlock);
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "else if (foo == 10)".
     *                    Shouldn't contain braces or newline characters.
     */
    public Builder nextControlFlow(String controlFlow, Object... args) {
      code.nextControlFlow(controlFlow, args);
      return this;
    }

    /**
     * @param codeBlock the control flow construct and its code, such as "else if (foo == 10)".
     *                  Shouldn't contain braces or newline characters.
     */
    public Builder nextControlFlow(CodeBlock codeBlock) {
      return nextControlFlow("$L", codeBlock);
    }

    public Builder endControlFlow() {
      code.endControlFlow();
      return this;
    }

    /**
     * @param controlFlow the optional control flow construct and its code, such as
     *                    "while(foo == 20)". Only used for "do/while" control flows.
     */
    public Builder endControlFlow(String controlFlow, Object... args) {
      code.endControlFlow(controlFlow, args);
      return this;
    }

    /**
     * @param codeBlock the optional control flow construct and its code, such as
     *                  "while(foo == 20)". Only used for "do/while" control flows.
     */
    public Builder endControlFlow(CodeBlock codeBlock) {
      return endControlFlow("$L", codeBlock);
    }

    public Builder addStatement(String format, Object... args) {
      code.addStatement(stripNL(format), args);
      return this;
    }

    public Builder addStatement(CodeBlock codeBlock) {
      code.addStatement(codeBlock);
      return this;
    }

    public MethodSpec build() {
      return new MethodSpec(this);
    }
  }
}
