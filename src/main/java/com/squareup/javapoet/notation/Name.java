package com.squareup.javapoet.notation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Name extends Notation {

  private final Object tag;

  public Name(Object tag, String suggestion) {
    super(Map.of(tag, suggestion), Set.of(), Set.of());
    this.tag = tag;
  }

  @Override
  public @NotNull Notation flat() {
    return this;
  }

  @Override
  public Notation toNotation() {
    return txt("Name(" + tag + ")");
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
    printer.append(chunk.getName(tag));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    return flatVisitor.fitText(chunk.getName(tag));
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Name name) {
      return Objects.equals(tag, name.tag) && Objects.equals(names, name.names);
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(tag, names);
  }
}
