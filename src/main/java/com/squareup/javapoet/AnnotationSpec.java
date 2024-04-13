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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.squareup.javapoet.notation.Notate;
import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.squareup.javapoet.Util.characterLiteralWithoutSingleQuotes;
import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.txt;

/**
 * A generated annotation on a declaration.
 */
@Immutable
public final class AnnotationSpec implements Emitable {
  public static final String VALUE = "value";

  public final TypeName type;
  public final ImmutableMap<String, ImmutableList<CodeBlock>> members;

  private AnnotationSpec(Builder builder) {
    this.type = builder.type;
    this.members = Util.immutableMultimap(builder.members);
  }

  public static AnnotationSpec get(Annotation annotation) {
    return get(annotation, false);
  }

  public static AnnotationSpec get(
      Annotation annotation,
      boolean includeDefaultValues
  ) {
    var builder = builder(annotation.annotationType());
    try {
      var methods = annotation.annotationType().getDeclaredMethods();
      Arrays.sort(methods, Comparator.comparing(Method::getName));
      for (var method : methods) {
        var value = method.invoke(annotation);
        if (!includeDefaultValues) {
          if (Objects.deepEquals(value, method.getDefaultValue())) {
            continue;
          }
        }
        if (value.getClass().isArray()) {
          for (var i = 0; i < Array.getLength(value); i++) {
            builder.addMemberForValue(method.getName(), Array.get(value, i));
          }
          continue;
        }
        if (value instanceof Annotation) {
          builder.addMember(method.getName(), "$L", get((Annotation) value));
          continue;
        }
        builder.addMemberForValue(method.getName(), value);
      }
    } catch (Exception e) {
      throw new RuntimeException("Reflecting " + annotation + " failed!", e);
    }
    return builder.build();
  }

  public static AnnotationSpec get(AnnotationMirror annotation) {
    var element = (TypeElement) annotation.getAnnotationType().asElement();
    var builder = AnnotationSpec.builder(ClassName.get(element));
    var visitor = new Visitor(builder);
    for (var executableElement : annotation.getElementValues().keySet()) {
      var name = executableElement.getSimpleName().toString();
      var value = annotation.getElementValues().get(executableElement);
      value.accept(visitor, name);
    }
    return builder.build();
  }

  public static Builder builder(ClassName type) {
    checkNotNull(type, "type == null");
    return new Builder(type);
  }

  public static Builder builder(Class<?> type) {
    return builder(ClassName.get(type));
  }

  @Override
  public @NotNull Notation toNotation() {
    var ref = txt("@").then(Notation.typeRef(type));
    if (members.isEmpty()) {
      return ref;
    }
    if (members.size() == 1 && members.containsKey("value")) {
      return Notate.wrapAndIndent(
          ref.then(txt("(")),
          Notate.oneOrArray(members.get("value")),
          txt(")")
      );
    }
    var choice = txt(", ").or(txt(",\n"));
    var m = members
        .entrySet()
        .stream()
        .map(e -> txt(e.getKey())
            .then(txt(" = "))
            .then(Notate.oneOrArray(e.getValue())))
        .collect(join(choice));
    return Notate.wrapAndIndent(ref.then(txt("(")), m, txt(")"));
  }

  public Builder toBuilder() {
    var builder = new Builder(type);
    for (Map.Entry<String, ImmutableList<CodeBlock>> entry : members.entrySet()) {
      builder.members.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    return builder;
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
    return toNotation().toCode();
  }

  public static final class Builder {
    public final Map<String, List<CodeBlock>> members = new LinkedHashMap<>();
    private final TypeName type;

    private Builder(TypeName type) {
      this.type = type;
    }

    public Builder addMember(String name, String format, Object... args) {
      return addMember(name, CodeBlock.of(format, args));
    }

    public Builder addMember(String name, CodeBlock codeBlock) {
      var values = members.computeIfAbsent(name, k -> new ArrayList<>());
      values.add(codeBlock);
      return this;
    }

    /**
     * Delegates to {@link #addMember(String, String, Object...)}, with parameter {@code format}
     * depending on the given {@code value} object. Falls back to {@code "$L"} literal format if
     * the class of the given {@code value} object is not supported.
     */
    Builder addMemberForValue(String memberName, Object value) {
      checkNotNull(memberName, "memberName == null");
      checkNotNull(
          value,
          "value == null, constant non-null value expected for %s",
          memberName
      );
      checkArgument(
          SourceVersion.isName(memberName),
          "not a valid name: %s",
          memberName
      );
      if (value instanceof Class<?>) {
        return addMember(memberName, "$T.class", value);
      }
      if (value instanceof Enum) {
        return addMember(
            memberName,
            "$T.$L",
            value.getClass(),
            ((Enum<?>) value).name()
        );
      }
      if (value instanceof String) {
        return addMember(memberName, "$S", value);
      }
      if (value instanceof Float) {
        return addMember(memberName, "$Lf", value);
      }
      if (value instanceof Long) {
        return addMember(memberName, "$LL", value);
      }
      if (value instanceof Character) {
        return addMember(
            memberName,
            "'$L'",
            characterLiteralWithoutSingleQuotes((char) value)
        );
      }
      return addMember(memberName, "$L", value);
    }

    public AnnotationSpec build() {
      for (var name : members.keySet()) {
        checkNotNull(name, "name == null");
        checkArgument(SourceVersion.isName(name), "not a valid name: %s", name);
      }
      return new AnnotationSpec(this);
    }
  }

  /**
   * Annotation value visitor adding members to the given builder instance.
   */
  private static class Visitor
      extends SimpleAnnotationValueVisitor8<Builder, String> {
    final Builder builder;

    Visitor(Builder builder) {
      super(builder);
      this.builder = builder;
    }

    @Override
    protected Builder defaultAction(Object o, String name) {
      return builder.addMemberForValue(name, o);
    }

    @Override
    public Builder visitAnnotation(AnnotationMirror a, String name) {
      return builder.addMember(name, "$L", get(a));
    }

    @Override
    public Builder visitEnumConstant(VariableElement c, String name) {
      return builder.addMember(name, "$T.$L", c.asType(), c.getSimpleName());
    }

    @Override
    public Builder visitType(TypeMirror t, String name) {
      return builder.addMember(name, "$T.class", t);
    }

    @Override
    public Builder visitArray(
        List<? extends AnnotationValue> values,
        String name
    ) {
      for (var value : values) {
        value.accept(this, name);
      }
      return builder;
    }
  }
}
