package com.squareup.javapoet.notation;

import com.squareup.javapoet.Notate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class Literal<T> extends Notation {

  private final T value;

  public Literal(T value) {
    super();
    this.value = value;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) throws IOException {
    printer.append(String.valueOf(value));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    return flatVisitor.fitText(String.valueOf(value));
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
  }

  @Override
  public Notation toNotation() {
    return Notate.wrapAndIndent(txt("Literal("), txt(String.valueOf(value)), txt(")"));
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Literal<?> literal) {
      return Objects.equals(value, literal.value);
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(value);
  }
}
