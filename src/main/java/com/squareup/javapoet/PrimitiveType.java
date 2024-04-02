package com.squareup.javapoet;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public enum PrimitiveType implements TypeName {
  Void("void") {
    @Override
    public boolean isPrimitive() {
      return false;
    }
  }, Boolean("boolean"), Byte("byte"), Short("short"), Integer("int"), Long("long"), Character("char"), Float("float"), Double("double"),
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
  public @NotNull CodeWriter emit(@NotNull CodeWriter out) throws IOException {
    out.emit(keyword);
    return out;
  }

  @Override
  public TypeName nestedClass(String name) {
    throw new UnsupportedOperationException("Cannot nest class inside primitive");
  }

  @Override
  public TypeName withBounds(List<? extends TypeName> bounds) {
    throw new UnsupportedOperationException("Cannot set bounds on primitive");
  }
}
