package com.squareup.javapoet;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.squareup.javapoet.Util.checkNotNull;

public final class AnnotatedTypeName implements TypeName {
  final @NotNull TypeName inner;
  final @NotNull List<AnnotationSpec> annotations;


  public AnnotatedTypeName(@NotNull TypeName inner, @NotNull Collection<AnnotationSpec> annotations) {
    checkNotNull(annotations, "annotations");
    if (inner instanceof AnnotatedTypeName annotated) {
      this.inner = annotated.inner;
      this.annotations = annotated.concatAnnotations(annotations);
    } else {
      this.inner = inner;
      this.annotations = List.copyOf(annotations);
    }
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (getClass() != o.getClass()) return false;
    return toString().equals(o.toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public @NotNull String toString() {
    try {
      var resultBuilder = new StringBuilder();
      var codeWriter = new CodeWriter(resultBuilder);
      emit(codeWriter);
      return resultBuilder.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  private @NotNull List<AnnotationSpec> concatAnnotations(@NotNull Collection<AnnotationSpec> annotations) {
    List<AnnotationSpec> allAnnotations = new ArrayList<>(this.annotations);
    allAnnotations.addAll(annotations);
    return allAnnotations;
  }

  @Contract(pure = true)
  @Override
  public @NotNull TypeName withoutAnnotations() {
    return inner;
  }

  @Override
  public boolean isPrimitive() {
    return inner.isPrimitive();
  }

  @Override
  public boolean isBoxedPrimitive() {
    return inner.isBoxedPrimitive();
  }

  @Override
  public AnnotatedTypeName box() {
    return new AnnotatedTypeName(inner.box(), annotations);
  }

  @Override
  public @NotNull AnnotatedTypeName unbox() {
    return new AnnotatedTypeName(inner.unbox(), annotations);
  }

  @Override
  public @NotNull CodeWriter emit(@NotNull CodeWriter out) throws IOException {
    return emit(out, false);
  }

  @Override
  public @NotNull CodeWriter emit(@NotNull CodeWriter out, boolean varargs) throws IOException {
    for (var annotation : annotations) {
      annotation.emit(out, true);
      out.emit(" ");
    }
    return inner.emit(out, varargs);
  }

  @Override
  public @NotNull AnnotatedTypeName nestedClass(@NotNull String name) {
    return new AnnotatedTypeName(inner.nestedClass(name), annotations);
  }

  @Override
  public @NotNull TypeName withBounds(@NotNull List<? extends TypeName> bounds) {
    return new AnnotatedTypeName(inner.withBounds(bounds), annotations);
  }
}
