package com.squareup.javapoet.notation;

import com.squareup.javapoet.Notate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class Flat extends Notation {
  public final Notation inner;

  public Flat(@NotNull Notation inner) {
    super(inner.names, inner.imports, inner.childContexts);
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
    printer.push(chunk.withNotation(inner).flat());
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    flatVisitor.push(chunk.withNotation(inner).flat());
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
  public @NotNull Notation flat() {
    return inner.flat();
  }

  @Override
  public Notation toNotation() {
    return Notate.fnLike("Flat", List.of(inner));
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Flat flat) {
      return Objects.equals(inner, flat.inner);
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(inner);
  }
}
