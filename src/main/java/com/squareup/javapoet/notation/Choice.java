package com.squareup.javapoet.notation;

import org.jetbrains.annotations.NotNull;

public class Choice extends Notation {
  private final Notation left;
  private final Notation right;

  public Choice(Notation left, Notation right) {
    super();
    this.left = left;
    this.right = right;
  }

  @Override
  public void visit(Printer.@NotNull PrinterVisitor printer, @NotNull Chunk chunk) {
    if (chunk.flat || printer.fits(chunk.withNotation(left))) {
      printer.push(chunk.withNotation(left));
    } else {
      printer.push(chunk.withNotation(right));
    }
  }

  @Override
  public Printer.@NotNull FlatResponse visit(Printer.@NotNull FlatVisitor flatVisitor, @NotNull Chunk chunk) {
    if (chunk.flat) {
      flatVisitor.push(chunk.withNotation(left));
    } else {
      flatVisitor.push(chunk.withNotation(right));
    }
    return Printer.FlatResponse.INCONCLUSIVE;
  }
}
