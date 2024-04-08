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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.Util.checkState;
import static com.squareup.javapoet.Util.requireExactlyOneOf;
import static com.squareup.javapoet.notation.Notation.asLines;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.literal;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;
import static com.squareup.javapoet.notation.Notation.typeRef;

/**
 * A generated class, interface, or enum declaration.
 */
public final class TypeSpec {
  public final Kind kind;
  public final String name;
  public final CodeBlock anonymousTypeArguments;
  public final CodeBlock javadoc;
  public final List<AnnotationSpec> annotations;
  public final Set<Modifier> modifiers;
  public final List<TypeName> typeVariables;
  public final TypeName superclass;
  public final List<TypeName> superinterfaces;
  public final Map<String, TypeSpec> enumConstants;
  public final List<FieldSpec> fieldSpecs;
  public final CodeBlock staticBlock;
  public final CodeBlock initializerBlock;
  public final List<MethodSpec> methodSpecs;
  public final List<TypeSpec> typeSpecs;
  public final List<Element> originatingElements;
  public final Set<String> alwaysQualifiedNames;
  final Set<String> nestedTypesSimpleNames;

  private TypeSpec(@NotNull Builder builder) {
    this.kind = builder.kind;
    this.name = builder.name;
    this.anonymousTypeArguments = builder.anonymousTypeArguments;
    this.javadoc = builder.javadoc.build();
    this.annotations = Util.immutableList(builder.annotations);
    this.modifiers = Util.immutableSet(builder.modifiers);
    this.typeVariables = Util.immutableList(builder.typeVariables);
    this.superclass = builder.superclass;
    this.superinterfaces = Util.immutableList(builder.superinterfaces);
    this.enumConstants = Util.immutableMap(builder.enumConstants);
    this.fieldSpecs = Util.immutableList(builder.fieldSpecs);
    this.staticBlock = builder.staticBlock.build();
    this.initializerBlock = builder.initializerBlock.build();
    this.methodSpecs = Util.immutableList(builder.methodSpecs);
    this.typeSpecs = Util.immutableList(builder.typeSpecs);
    this.alwaysQualifiedNames = Util.immutableSet(builder.alwaysQualifiedNames);

    nestedTypesSimpleNames = new HashSet<>(builder.typeSpecs.size());
    List<Element> originatingElementsMutable =
        new ArrayList<>(builder.originatingElements);
    for (var typeSpec : builder.typeSpecs) {
      nestedTypesSimpleNames.add(typeSpec.name);
      originatingElementsMutable.addAll(typeSpec.originatingElements);
    }

    this.originatingElements = Util.immutableList(originatingElementsMutable);
  }

  /**
   * Creates a dummy type spec for type-resolution only (in CodeWriter)
   * while emitting the type declaration but before entering the type body.
   */
  private TypeSpec(@NotNull TypeSpec type) {
    assert type.anonymousTypeArguments == null;
    this.kind = type.kind;
    this.name = type.name;
    this.anonymousTypeArguments = null;
    this.javadoc = type.javadoc;
    this.annotations = Collections.emptyList();
    this.modifiers = Collections.emptySet();
    this.typeVariables = Collections.emptyList();
    this.superclass = null;
    this.superinterfaces = Collections.emptyList();
    this.enumConstants = Collections.emptyMap();
    this.fieldSpecs = Collections.emptyList();
    this.staticBlock = type.staticBlock;
    this.initializerBlock = type.initializerBlock;
    this.methodSpecs = Collections.emptyList();
    this.typeSpecs = Collections.emptyList();
    this.originatingElements = Collections.emptyList();
    this.nestedTypesSimpleNames = Collections.emptySet();
    this.alwaysQualifiedNames = Collections.emptySet();
  }

  @Contract("_ -> new")
  public static @NotNull Builder classBuilder(@NotNull String name) {
    return new Builder(Kind.CLASS, checkNotNull(name, "name == null"), null);
  }

  @Contract("_ -> new")
  public static @NotNull Builder classBuilder(@NotNull ClassName className) {
    return classBuilder(checkNotNull(
        className,
        "className == null"
    ).nameWhenImported());
  }

  @Contract("_ -> new")
  public static @NotNull Builder interfaceBuilder(@NotNull String name) {
    return new Builder(
        Kind.INTERFACE,
        checkNotNull(name, "name == null"),
        null
    );
  }

  @Contract("_ -> new")
  public static @NotNull Builder interfaceBuilder(
      @NotNull ClassName className
  ) {
    return interfaceBuilder(checkNotNull(
        className,
        "className == null"
    ).nameWhenImported());
  }

  @Contract("_ -> new")
  public static @NotNull Builder enumBuilder(@NotNull String name) {
    return new Builder(Kind.ENUM, checkNotNull(name, "name == null"), null);
  }

  @Contract("_ -> new")
  public static @NotNull Builder enumBuilder(@NotNull ClassName className) {
    return enumBuilder(checkNotNull(
        className,
        "className == null"
    ).nameWhenImported());
  }

  @Contract("_, _ -> new")
  public static @NotNull Builder anonymousClassBuilder(
      @NotNull String typeArgumentsFormat, @NotNull Object... args
  ) {
    return anonymousClassBuilder(CodeBlock.of(typeArgumentsFormat, args));
  }

  @Contract("_ -> new")
  public static @NotNull Builder anonymousClassBuilder(
      @NotNull CodeBlock typeArguments
  ) {
    return new Builder(Kind.CLASS, null, typeArguments);
  }

  @Contract("_ -> new")
  public static @NotNull Builder annotationBuilder(@NotNull String name) {
    return new Builder(
        Kind.ANNOTATION,
        checkNotNull(name, "name == null"),
        null
    );
  }

  @Contract("_ -> new")
  public static @NotNull Builder annotationBuilder(
      @NotNull ClassName className
  ) {
    return annotationBuilder(checkNotNull(
        className,
        "className == null"
    ).nameWhenImported());
  }

  @Contract(pure = true)
  public boolean hasModifier(@NotNull Modifier modifier) {
    return modifiers.contains(modifier);
  }

  @Contract("-> new")
  public @NotNull Builder toBuilder() {
    var builder = new Builder(kind, name, anonymousTypeArguments);
    builder.javadoc.add(javadoc);
    builder.annotations.addAll(annotations);
    builder.modifiers.addAll(modifiers);
    builder.typeVariables.addAll(typeVariables);
    builder.superclass = superclass;
    builder.superinterfaces.addAll(superinterfaces);
    builder.enumConstants.putAll(enumConstants);
    builder.fieldSpecs.addAll(fieldSpecs);
    builder.methodSpecs.addAll(methodSpecs);
    builder.typeSpecs.addAll(typeSpecs);
    builder.initializerBlock.add(initializerBlock);
    builder.staticBlock.add(staticBlock);
    builder.originatingElements.addAll(originatingElements);
    builder.alwaysQualifiedNames.addAll(alwaysQualifiedNames);
    return builder;
  }

  public Notation toNotation() {
    return toNotation(Set.of());
  }

  public Notation toNotation(Set<Modifier> implicitModifiers) {
    Notation preamble;
    if (anonymousTypeArguments != null) {
      var supertype =
          !superinterfaces.isEmpty() ? superinterfaces.get(0) : superclass;
      preamble = Notate.wrapAndIndent(
          txt("new ").then(typeRef(supertype)).then(txt("(")),
          anonymousTypeArguments.toNotation(),
          txt(")")
      );
    } else {
      var lines = Stream.<Notation>builder();
      lines.add(Notate.javadoc(javadoc.toNotation().suppressImports()));
      annotations.stream().map(Emitable::toNotation).forEach(lines);
      var declaration = Stream.<Notation>builder();
      modifiers.stream()
          .filter(m -> !implicitModifiers.contains(m))
          .map(String::valueOf)
          .map(Notation::txt)
          .forEach(declaration);
      if (kind == Kind.ANNOTATION) {
        declaration.add(txt("@interface"));
      } else {
        declaration.add(txt(kind.name().toLowerCase(Locale.US)));
      }
      if (typeVariables.isEmpty()) {
        declaration.add(txt(name));
      } else {
        declaration.add(Notate.wrapAndIndent(
            txt(name + "<"),
            typeVariables
                .stream()
                .map(Emitable::toDeclaration)
                .collect(join(txt(", ").or(txt(",\n")))),
            txt(">")
        ));
      }

      List<TypeName> extendsTypes;
      List<TypeName> implementsTypes;
      if (kind == Kind.INTERFACE) {
        extendsTypes = superinterfaces;
        implementsTypes = Collections.emptyList();
      } else {
        extendsTypes = superclass.equals(ClassName.OBJECT)
            ? Collections.emptyList()
            : Collections.singletonList(superclass);
        implementsTypes = superinterfaces;
      }

      if (!extendsTypes.isEmpty()) {
        declaration.add(Notate.spacesOrWrapAndIndent(
            txt("extends"),
            extendsTypes
                .stream()
                .map(Notation::typeRef)
                .collect(join(txt(", ").or(txt(",\n")))),
            empty()
        ));
      }

      if (!implementsTypes.isEmpty()) {
        declaration.add(Notate.spacesOrWrapAndIndent(
            txt("implements"),
            implementsTypes
                .stream()
                .map(Notation::typeRef)
                .collect(join(txt(", ").or(txt(",\n")))),
            empty()
        ));
      }

      declaration.add(txt("{"));
      lines.add(declaration.build().collect(join(txt(" ").or(nl()))));
      preamble = lines.build().filter(n -> !n.isEmpty()).collect(asLines());
    }
    return fromPreamble(preamble);
  }

  private Notation fromPreamble(Notation preamble) {
    var body = Stream.<Notation>builder();
    var needsSeparator = kind == Kind.ENUM
        && (!fieldSpecs.isEmpty() || !methodSpecs.isEmpty()
        || !typeSpecs.isEmpty());

    var constants =
        enumConstants
            .entrySet()
            .stream()
            .map(e -> e.getValue().notationForEnumConstant(e.getKey()))
            .toList();
    body.add(constants
        .stream()
        .collect(join(txt(", ")))
        .then(needsSeparator ? txt(";") : empty())
        .or(constants
            .stream()
            .collect(join(txt(",\n")))
            .then(needsSeparator ? txt("\n;") : empty())));

    // Static fields.
    var staticFields = fieldSpecs
        .stream()
        .filter(f -> f.hasModifier(Modifier.STATIC))
        .map(f -> f.toNotation(kind.implicitFieldModifiers))
        .collect(asLines());
    body.add(staticFields);

    body.add(staticBlock.toNotation());

    // Non-static fields.
    var nonStaticFields = fieldSpecs
        .stream()
        .filter(f -> !f.hasModifier(Modifier.STATIC))
        .map(f -> f.toNotation(kind.implicitFieldModifiers))
        .collect(asLines());
    body.add(nonStaticFields);

    // Initializer block.
    body.add(initializerBlock.toNotation());

    // Constructors.
    var constructors = methodSpecs
        .stream()
        .filter(MethodSpec::isConstructor)
        .map(m -> m.toNotation(name, kind.implicitMethodModifiers))
        .collect(join(txt("\n\n")));
    body.add(constructors);

    // Methods (static and non-static).
    var methods = methodSpecs
        .stream()
        .filter(m -> !m.isConstructor())
        .map(m -> m.toNotation(name, kind.implicitMethodModifiers))
        .collect(join(txt("\n\n")));
    body.add(methods);

    // Types.
    var types = typeSpecs
        .stream()
        .map(m -> m.toNotation(kind.implicitTypeModifiers))
        .collect(join(txt("\n\n")));
    body.add(types);

    var spec = Notate.wrapAndIndentUnlessEmpty(
        preamble,
        body.build().filter(n -> !n.isEmpty()).collect(join(nl().then(nl()))),
        txt("}")
    );

    return spec.suppressImports(alwaysQualifiedNames);
  }

  private Notation notationForEnumConstant(String name) {
    var preamble = Stream.<Notation>builder();
    preamble.add(javadoc.toNotation());
    annotations.stream().map(Emitable::toNotation).forEach(preamble);
    var emumNotation = literal(name);
    if (!anonymousTypeArguments.notation.isEmpty()) {
      emumNotation = Notate.wrapAndIndent(emumNotation.then(txt("(")),
          anonymousTypeArguments.toNotation(),
          txt(")")
      );
    }
    preamble.add(emumNotation);
    var collected =
        preamble.build().filter(n -> !n.isEmpty()).collect(join(nl()));
    if (fieldSpecs.isEmpty() && methodSpecs.isEmpty() && typeSpecs.isEmpty()) {
      return collected; // Avoid unnecessary braces "{}".
    }
    return fromPreamble(collected.then(txt(" {")));
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
    return toNotation().then(nl()).toCode();
  }

  public enum Kind {
    CLASS(
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet()
    ),

    INTERFACE(
        Util.immutableSet(Arrays.asList(
            Modifier.PUBLIC,
            Modifier.STATIC,
            Modifier.FINAL
        )),
        Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.ABSTRACT)),
        Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC)),
        Util.immutableSet(Collections.singletonList(Modifier.STATIC))
    ),

    ENUM(
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.singleton(Modifier.STATIC)
    ),

    ANNOTATION(
        Util.immutableSet(Arrays.asList(
            Modifier.PUBLIC,
            Modifier.STATIC,
            Modifier.FINAL
        )),
        Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.ABSTRACT)),
        Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC)),
        Util.immutableSet(Collections.singletonList(Modifier.STATIC))
    );

    private final Set<Modifier> implicitFieldModifiers;
    private final Set<Modifier> implicitMethodModifiers;
    private final Set<Modifier> implicitTypeModifiers;
    private final Set<Modifier> asMemberModifiers;

    Kind(
        Set<Modifier> implicitFieldModifiers,
        Set<Modifier> implicitMethodModifiers,
        Set<Modifier> implicitTypeModifiers,
        Set<Modifier> asMemberModifiers
    ) {
      this.implicitFieldModifiers = Set.copyOf(implicitFieldModifiers);
      this.implicitMethodModifiers = Set.copyOf(implicitMethodModifiers);
      this.implicitTypeModifiers = Set.copyOf(implicitTypeModifiers);
      this.asMemberModifiers = Set.copyOf(asMemberModifiers);
    }
  }

  public static final class Builder {
    public final Map<String, TypeSpec> enumConstants = new LinkedHashMap<>();
    public final List<AnnotationSpec> annotations = new ArrayList<>();
    public final List<Modifier> modifiers = new ArrayList<>();
    public final List<TypeName> typeVariables = new ArrayList<>();
    public final List<TypeName> superinterfaces = new ArrayList<>();
    public final List<FieldSpec> fieldSpecs = new ArrayList<>();
    public final List<MethodSpec> methodSpecs = new ArrayList<>();
    public final List<TypeSpec> typeSpecs = new ArrayList<>();
    public final List<Element> originatingElements = new ArrayList<>();
    public final Set<String> alwaysQualifiedNames = new LinkedHashSet<>();
    private final Kind kind;
    private final String name;
    private final CodeBlock anonymousTypeArguments;
    private final CodeBlock.Builder javadoc = CodeBlock.builder();
    private final CodeBlock.Builder staticBlock = CodeBlock.builder();
    private final CodeBlock.Builder initializerBlock = CodeBlock.builder();
    private TypeName superclass = ClassName.OBJECT;

    private Builder(
        @NotNull Kind kind, @Nullable String name,
        @Nullable CodeBlock anonymousTypeArguments
    ) {
      checkArgument(
          name == null || SourceVersion.isName(name),
          "not a valid name: %s",
          name
      );
      this.kind = kind;
      this.name = name;
      this.anonymousTypeArguments = anonymousTypeArguments;
    }

    @Contract("_, _ -> this")
    public @NotNull Builder addJavadoc(
        @NotNull String format,
        @NotNull Object... args
    ) {
      javadoc.add(format, args);
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addJavadoc(@NotNull CodeBlock block) {
      javadoc.add(block);
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addAnnotations(
        @NotNull Iterable<AnnotationSpec> annotationSpecs
    ) {
      for (var annotationSpec : annotationSpecs) {
        this.annotations.add(annotationSpec);
      }
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addAnnotation(
        @NotNull AnnotationSpec annotationSpec
    ) {
      this.annotations.add(annotationSpec);
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addAnnotation(@NotNull ClassName type) {
      return addAnnotation(AnnotationSpec.builder(type).build());
    }

    public @NotNull Builder addAnnotation(@NotNull Class<?> clazz) {
      return addAnnotation(ClassName.get(clazz));
    }

    @Contract("_ -> this")
    public @NotNull Builder addModifiers(@NotNull Modifier... modifiers) {
      Collections.addAll(this.modifiers, modifiers);
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addTypeVariables(
        @NotNull Iterable<TypeVariableName> typeVariables
    ) {
      for (var typeVariable : typeVariables) {
        this.typeVariables.add(typeVariable);
      }
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addTypeVariable(@NotNull TypeName typeVariable) {
      typeVariables.add(typeVariable);
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder superclass(@NotNull TypeName superclass) {
      checkState(
          this.kind == Kind.CLASS,
          "only classes have super classes, not " + this.kind
      );
      checkState(
          this.superclass == ClassName.OBJECT,
          "superclass already set to " + this.superclass
      );
      checkArgument(
          !superclass.isPrimitive(),
          "superclass may not be a primitive"
      );
      this.superclass = superclass;
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder superclass(@NotNull Type superclass) {
      return superclass(superclass, true);
    }

    @Contract("_, _ -> this")
    public @NotNull Builder superclass(
        @NotNull Type superclass,
        boolean avoidNestedTypeNameClashes
    ) {
      superclass(TypeName.get(superclass));
      if (avoidNestedTypeNameClashes) {
        var clazz = getRawType(superclass);
        if (clazz != null) {
          avoidClashesWithNestedClasses(clazz);
        }
      }
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder superclass(@NotNull TypeMirror superclass) {
      return superclass(superclass, true);
    }

    @Contract("_, _ -> this")
    public @NotNull Builder superclass(
        @NotNull TypeMirror superclass,
        boolean avoidNestedTypeNameClashes
    ) {
      superclass(TypeName.get(superclass));
      if (avoidNestedTypeNameClashes && superclass instanceof DeclaredType) {
        var superInterfaceElement =
            (TypeElement) ((DeclaredType) superclass).asElement();
        avoidClashesWithNestedClasses(superInterfaceElement);
      }
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addSuperinterfaces(
        @NotNull Iterable<? extends TypeName> superinterfaces
    ) {
      for (var superinterface : superinterfaces) {
        addSuperinterface(superinterface);
      }
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addSuperinterface(
        @NotNull TypeName superinterface
    ) {
      this.superinterfaces.add(superinterface);
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addSuperinterface(@NotNull Type superinterface) {
      return addSuperinterface(superinterface, true);
    }

    @Contract("_, _ -> this")
    public @NotNull Builder addSuperinterface(
        @NotNull Type superinterface,
        boolean avoidNestedTypeNameClashes
    ) {
      addSuperinterface(TypeName.get(superinterface));
      if (avoidNestedTypeNameClashes) {
        var clazz = getRawType(superinterface);
        if (clazz != null) {
          avoidClashesWithNestedClasses(clazz);
        }
      }
      return this;
    }

    private @Nullable Class<?> getRawType(@NotNull Type type) {
      if (type instanceof Class<?>) {
        return (Class<?>) type;
      } else if (type instanceof ParameterizedType) {
        return getRawType(((ParameterizedType) type).getRawType());
      } else {
        return null;
      }
    }

    @Contract("_ -> this")
    public @NotNull Builder addSuperinterface(
        @NotNull TypeMirror superinterface
    ) {
      return addSuperinterface(superinterface, true);
    }

    @Contract("_, _ -> this")
    public @NotNull Builder addSuperinterface(
        @NotNull TypeMirror superinterface,
        boolean avoidNestedTypeNameClashes
    ) {
      addSuperinterface(TypeName.get(superinterface));
      if (avoidNestedTypeNameClashes
          && superinterface instanceof DeclaredType declaredType) {
        var superInterfaceElement =
            (TypeElement) declaredType.asElement();
        avoidClashesWithNestedClasses(superInterfaceElement);
      }
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addEnumConstant(@NotNull String name) {
      return addEnumConstant(name, anonymousClassBuilder("").build());
    }

    @Contract("_, _ -> this")
    public @NotNull Builder addEnumConstant(
        @NotNull String name,
        @NotNull TypeSpec typeSpec
    ) {
      enumConstants.put(name, typeSpec);
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addFields(@NotNull Iterable<FieldSpec> fieldSpecs) {
      for (var fieldSpec : fieldSpecs) {
        addField(fieldSpec);
      }
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addField(@NotNull FieldSpec fieldSpec) {
      fieldSpecs.add(fieldSpec);
      return this;
    }

    public Builder addField(TypeName type, String name, Modifier... modifiers) {
      return addField(FieldSpec.builder(type, name, modifiers).build());
    }

    public Builder addField(Type type, String name, Modifier... modifiers) {
      return addField(TypeName.get(type), name, modifiers);
    }

    public Builder addStaticBlock(CodeBlock block) {
      staticBlock.beginControlFlow("static").add(block).endControlFlow();
      return this;
    }

    public Builder addInitializerBlock(CodeBlock block) {
      if ((kind != Kind.CLASS && kind != Kind.ENUM)) {
        throw new UnsupportedOperationException(
            kind + " can't have initializer blocks");
      }
      initializerBlock
          .indent()
          .add(block)
          .unindent("{", "}");
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addMethods(
        @NotNull Iterable<MethodSpec> methodSpecs
    ) {
      for (var methodSpec : methodSpecs) {
        addMethod(methodSpec);
      }
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addMethod(@NotNull MethodSpec methodSpec) {
      methodSpecs.add(methodSpec);
      return this;
    }

    @Contract("_ -> this")
    public @NotNull Builder addTypes(@NotNull Iterable<TypeSpec> typeSpecs) {
      for (var typeSpec : typeSpecs) {
        addType(typeSpec);
      }
      return this;
    }

    public Builder addType(TypeSpec typeSpec) {
      typeSpecs.add(typeSpec);
      return this;
    }

    public Builder addOriginatingElement(Element originatingElement) {
      originatingElements.add(originatingElement);
      return this;
    }

    public Builder alwaysQualify(String... simpleNames) {
      checkArgument(simpleNames != null, "simpleNames == null");
      for (var name : simpleNames) {
        checkArgument(
            name != null,
            "null entry in simpleNames array: %s",
            Arrays.toString(simpleNames)
        );
        alwaysQualifiedNames.add(name);
      }
      return this;
    }

    /**
     * Call this to always fully qualify any types that would conflict with possibly nested types of
     * this {@code typeElement}. For example - if the following type was passed in as the
     * typeElement:
     *
     * <pre><code>
     *   class Foo {
     *     class NestedTypeA {
     *
     *     }
     *     class NestedTypeB {
     *
     *     }
     *   }
     * </code></pre>
     *
     * <p>
     * Then this would add {@code "NestedTypeA"} and {@code "NestedTypeB"} as names that should
     * always be qualified via {@link #alwaysQualify(String...)}. This way they would avoid
     * possible import conflicts when this JavaFile is written.
     *
     * @param typeElement the {@link TypeElement} with nested types to avoid clashes with.
     * @return this builder instance.
     */
    public Builder avoidClashesWithNestedClasses(TypeElement typeElement) {
      checkArgument(typeElement != null, "typeElement == null");
      for (var nestedType : ElementFilter.typesIn(typeElement.getEnclosedElements())) {
        alwaysQualify(nestedType.getSimpleName().toString());
      }
      var superclass = typeElement.getSuperclass();
      if (!(superclass instanceof NoType)
          && superclass instanceof DeclaredType) {
        var superclassElement =
            (TypeElement) ((DeclaredType) superclass).asElement();
        avoidClashesWithNestedClasses(superclassElement);
      }
      for (var superinterface : typeElement.getInterfaces()) {
        if (superinterface instanceof DeclaredType) {
          var superinterfaceElement
              = (TypeElement) ((DeclaredType) superinterface).asElement();
          avoidClashesWithNestedClasses(superinterfaceElement);
        }
      }
      return this;
    }

    /**
     * Call this to always fully qualify any types that would conflict with possibly nested types of
     * this {@code typeElement}. For example - if the following type was passed in as the
     * typeElement:
     *
     * <pre><code>
     *   class Foo {
     *     class NestedTypeA {
     *
     *     }
     *     class NestedTypeB {
     *
     *     }
     *   }
     * </code></pre>
     *
     * <p>
     * Then this would add {@code "NestedTypeA"} and {@code "NestedTypeB"} as names that should
     * always be qualified via {@link #alwaysQualify(String...)}. This way they would avoid
     * possible import conflicts when this JavaFile is written.
     *
     * @param clazz the {@link Class} with nested types to avoid clashes with.
     * @return this builder instance.
     */
    public Builder avoidClashesWithNestedClasses(Class<?> clazz) {
      checkArgument(clazz != null, "clazz == null");
      for (var nestedType : clazz.getDeclaredClasses()) {
        alwaysQualify(nestedType.getSimpleName());
      }
      var superclass = clazz.getSuperclass();
      if (superclass != null && !Object.class.equals(superclass)) {
        avoidClashesWithNestedClasses(superclass);
      }
      for (var superinterface : clazz.getInterfaces()) {
        avoidClashesWithNestedClasses(superinterface);
      }
      return this;
    }

    public TypeSpec build() {
      for (var annotationSpec : annotations) {
        checkNotNull(annotationSpec, "annotationSpec == null");
      }

      if (!modifiers.isEmpty()) {
        checkState(
            anonymousTypeArguments == null,
            "forbidden on anonymous types."
        );
        for (var modifier : modifiers) {
          checkArgument(modifier != null, "modifiers contain null");
        }
      }

      for (var superinterface : superinterfaces) {
        checkArgument(superinterface != null, "superinterfaces contains null");
      }

      if (!typeVariables.isEmpty()) {
        checkState(
            anonymousTypeArguments == null,
            "typevariables are forbidden on anonymous types."
        );
        for (var typeVariableName : typeVariables) {
          checkArgument(typeVariableName != null, "typeVariables contain null");
        }
      }

      for (var enumConstant : enumConstants.entrySet()) {
        checkState(kind == Kind.ENUM, "%s is not enum", this.name);
        checkArgument(
            enumConstant.getValue().anonymousTypeArguments != null,
            "enum constants must have anonymous type arguments"
        );
        checkArgument(
            SourceVersion.isName(name),
            "not a valid enum constant: %s",
            name
        );
      }

      for (var fieldSpec : fieldSpecs) {
        if (kind == Kind.INTERFACE || kind == Kind.ANNOTATION) {
          requireExactlyOneOf(
              fieldSpec.modifiers,
              Modifier.PUBLIC,
              Modifier.PRIVATE
          );
          Set<Modifier> check = EnumSet.of(Modifier.STATIC, Modifier.FINAL);
          checkState(fieldSpec.modifiers.containsAll(check),
              "%s %s.%s requires modifiers %s",
              kind,
              name,
              fieldSpec.name,
              check
          );
        }
      }

      for (var methodSpec : methodSpecs) {
        if (kind == Kind.INTERFACE) {
          requireExactlyOneOf(
              methodSpec.modifiers,
              Modifier.PUBLIC,
              Modifier.PRIVATE
          );
          if (methodSpec.modifiers.contains(Modifier.PRIVATE)) {
            checkState(!methodSpec.hasModifier(Modifier.DEFAULT),
                "%s %s.%s cannot be private and default",
                kind,
                name,
                methodSpec.name
            );
            checkState(!methodSpec.hasModifier(Modifier.ABSTRACT),
                "%s %s.%s cannot be private and abstract",
                kind,
                name,
                methodSpec.name
            );
          } else {
            requireExactlyOneOf(methodSpec.modifiers,
                Modifier.ABSTRACT,
                Modifier.STATIC,
                Modifier.DEFAULT
            );
          }
        } else if (kind == Kind.ANNOTATION) {
          checkState(methodSpec.modifiers.equals(kind.implicitMethodModifiers),
              "%s %s.%s requires modifiers %s",
              kind, name, methodSpec.name, kind.implicitMethodModifiers
          );
        }
        if (kind != Kind.ANNOTATION) {
          checkState(methodSpec.defaultValue == null,
              "%s %s.%s cannot have a default value",
              kind,
              name,
              methodSpec.name
          );
        }
        if (kind != Kind.INTERFACE) {
          checkState(!methodSpec.hasModifier(Modifier.DEFAULT),
              "%s %s.%s cannot be default",
              kind,
              name,
              methodSpec.name
          );
        }
      }

      for (var typeSpec : typeSpecs) {
        checkArgument(typeSpec.modifiers.containsAll(kind.implicitTypeModifiers),
            "%s %s.%s requires modifiers %s", kind, name, typeSpec.name,
            kind.implicitTypeModifiers
        );
      }

      var isAbstract =
          modifiers.contains(Modifier.ABSTRACT) || kind != Kind.CLASS;
      for (var methodSpec : methodSpecs) {
        checkArgument(isAbstract || !methodSpec.hasModifier(Modifier.ABSTRACT),
            "non-abstract type %s cannot declare abstract method %s",
            name,
            methodSpec.name
        );
      }

      var superclassIsObject = superclass.equals(ClassName.OBJECT);
      var interestingSupertypeCount =
          (superclassIsObject ? 0 : 1) + superinterfaces.size();
      checkArgument(
          anonymousTypeArguments == null || interestingSupertypeCount <= 1,
          "anonymous type has too many supertypes"
      );

      return new TypeSpec(this);
    }
  }
}
