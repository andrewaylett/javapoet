package com.squareup.javapoet.notation;

import com.squareup.javapoet.Notate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class Indent extends Notation {
  public final String indent;
  public final Notation inner;

  public Indent(String indent, Notation inner) {
    super(inner.names, inner.imports);
    this.indent = indent;
    this.inner = inner;
  }

  @Override
  public boolean isEmpty() {
    return inner.isEmpty();
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) {
    printer.push(chunk.withNotation(inner).indented(indent));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    flatVisitor.push(chunk.withNotation(inner).indented(indent));
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
    visitor.enter(this);
    inner.visit(visitor);
    visitor.exit(this);
  }

  @Override
  public Notation toNotation() {
    return Notate.fnLike("Indent", List.of(inner));
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Indent indent1) {
      return Objects.equals(indent, indent1.indent) && Objects.equals(
          inner,
          indent1.inner
      );
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(indent, inner);
  }
}
