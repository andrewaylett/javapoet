package com.squareup.javapoet.notation;

import com.squareup.javapoet.Notate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public class Empty extends Notation {
  public static Empty INSTANCE = new Empty();

  private Empty() {
  }

  @Override
  public @NotNull Notation then(@NotNull Notation next) {
    return next;
  }

  @Override
  public Notation toNotation() {
    return txt("Empty()");
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  @Contract(mutates = "param1")
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) throws IOException {
    // nothing
  }

  @Override
  @Contract(pure = true)
  public Printer.@NotNull FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    return Printer.FlatResponse.FITS;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Empty;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.getClass());
  }
}
