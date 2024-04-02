package com.squareup.javapoet.notation;

import org.jetbrains.annotations.NotNull;

public class Concat extends Notation {
  private final @NotNull Notation left;
  private final @NotNull Notation right;

  public Concat(@NotNull Notation left, @NotNull Notation right) {
    super();
    this.left = left;
    this.right = right;
  }

  @Override
  public void visit(@NotNull Printer.PrinterVisitor printer, @NotNull Chunk chunk) {
    printer.push(chunk.withNotation(left));
    printer.push(chunk.withNotation(right));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(@NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk) {
    flatVisitor.push(chunk.withNotation(left));
    flatVisitor.push(chunk.withNotation(right));
    return Printer.FlatResponse.INCONCLUSIVE;
  }
}
