package com.squareup.javapoet.notation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

@SuppressWarnings("UnstableApiUsage")
public class NewLine extends Notation {
  public static NewLine INSTANCE = new NewLine();

  private NewLine() {}

  @Override
  @Contract(mutates = "param1")
  public void visit(@NotNull Printer.PrinterVisitor printer, @NotNull Chunk chunk) throws IOException {
    printer.newLine();
    char [] spaces = new char[chunk.indent];
    Arrays.fill(spaces, ' ');
    printer.append(new String(spaces));
  }

  @Override
  @Contract(pure = true)
  public Printer.@NotNull FlatResponse visit(@NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk) {
    return Printer.FlatResponse.FITS;
  }
}
