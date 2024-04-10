package com.squareup.javapoet;

import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public enum PrimitiveType implements TypeName {
  Void("void") {
    @Override
    public boolean isPrimitive() {
      return false;
    }
  },
  Boolean("boolean"),
  Byte("byte"),
  Short("short"),
  Integer("int"),
  Long("long"),
  Character("char"),
  Float("float"),
  Double("double"),
  ;
  public final String keyword;

  PrimitiveType(String keyword) {
    this.keyword = keyword;
  }

  @Override
  public ClassName box() {
    return ClassName.get("java.lang", name());
  }

  @Override
  public @NotNull PrimitiveType unbox() {
    return this;
  }


  @Override
  public @NotNull TypeName withoutAnnotations() {
    return this;
  }

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public boolean isBoxedPrimitive() {
    return false;
  }

  @Override
  public @NotNull TypeName nestedClass(@NotNull String name) {
    throw new UnsupportedOperationException("Cannot nest class inside primitive");
  }

  @Override
  public @NotNull TypeName nestedClass(
      @NotNull String name,
      @NotNull List<TypeName> typeArguments
  ) {
    throw new UnsupportedOperationException("Cannot nest class inside primitive");
  }

  @Override
  public TypeName withBounds(List<? extends TypeName> bounds) {
    throw new UnsupportedOperationException("Cannot set bounds on primitive");
  }

  @Override
  public @NotNull String nameWhenImported() {
    return keyword;
  }

  @Override
  public @NotNull String canonicalName() {
    return keyword;
  }

  @Override
  public @NotNull ClassName topLevelClassName() {
    throw new UnsupportedOperationException(
        "Not a sensible concept for a primitive");
  }

  @Override
  public @NotNull String reflectionName() {
    return keyword;
  }

  @Override
  public TypeName enclosingClassName() {
    return null;
  }

  @Override
  public List<String> simpleNames() {
    return List.of(keyword);
  }

  @Override
  public @NotNull String simpleName() {
    return keyword;
  }

  @Override
  public Notation toNotation() {
    return Notation.typeRef(this);
  }
}
