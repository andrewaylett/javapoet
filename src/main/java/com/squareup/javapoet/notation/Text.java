package com.squareup.javapoet.notation;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class Text extends Notation {
  private final String content;

  public Text(@NotNull String s) {
    super();
    this.content = s;
  }

  @Override
  public void visit(@NotNull Printer.PrinterVisitor printer, @NotNull Chunk chunk) throws IOException {
    printer.append(content);
  }

  @Override
  public @NotNull Printer.FlatResponse visit(@NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk) {
    return flatVisitor.fitText(content);
  }
}
