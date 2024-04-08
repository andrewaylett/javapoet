package com.squareup.javapoet.notation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public class NewLine extends Notation {
  public static NewLine INSTANCE = new NewLine();

  private NewLine() {
  }

  @Override
  public Notation toNotation() {
    return txt("\\n");
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  @Contract(mutates = "param1")
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) throws IOException {
    printer.newLine();
    printer.append(chunk.indent);
  }

  @Override
  @Contract(pure = true)
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    if (chunk.flat) {
      return Printer.FlatResponse.TOO_LONG;
    }
    return Printer.FlatResponse.FITS;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof NewLine;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.getClass());
  }
}
