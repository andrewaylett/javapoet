package com.squareup.javapoet.notation;

import org.jetbrains.annotations.NotNull;

public class Flat extends Notation {
  private final Notation inner;

  public Flat(@NotNull Notation inner) {
    super();
    this.inner = inner;
  }

  @Override
  public void visit(@NotNull Printer.PrinterVisitor printer, @NotNull Chunk chunk) {
    printer.push(chunk.withNotation(inner).flat());
  }

  @Override
  public @NotNull Printer.FlatResponse visit(@NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk) {
    flatVisitor.push(chunk.withNotation(inner).flat());
    return Printer.FlatResponse.INCONCLUSIVE;
  }
}
