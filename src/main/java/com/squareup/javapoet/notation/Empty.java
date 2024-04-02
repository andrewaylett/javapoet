package com.squareup.javapoet.notation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

@SuppressWarnings("UnstableApiUsage")
public class Empty extends Notation {
  public static Empty INSTANCE = new Empty();

  private Empty() {}

  @Override
  public @NotNull Notation then(@NotNull Notation next) {
    return next;
  }

  @Override
  @Contract(mutates = "param1")
  public void visit(@NotNull Printer.PrinterVisitor printer, @NotNull Chunk chunk) throws IOException {
    // nothing
  }

  @Override
  @Contract(pure = true)
  public Printer.@NotNull FlatResponse visit(@NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk) {
    return Printer.FlatResponse.FITS;
  }
}
