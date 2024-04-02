package com.squareup.javapoet.notation;

import org.jetbrains.annotations.NotNull;

public class Indent extends Notation {
  private final int indent;
  private final Notation inner;

  public Indent(int indent, Notation inner) {
    super();
    this.indent = indent;
    this.inner = inner;
  }

  @Override
  public void visit(@NotNull Printer.PrinterVisitor printer, @NotNull Chunk chunk) {
    printer.push(chunk.withNotation(inner).indented(indent));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(@NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk) {
    flatVisitor.push(chunk.withNotation(inner).indented(indent));
    return Printer.FlatResponse.INCONCLUSIVE;
  }
}
