package com.squareup.javapoet.notation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

public class Text extends Notation {
  protected final String content;

  public Text(@NotNull String s) {
    super();
    this.content = s;
  }

  @Override
  public boolean isEmpty() {
    return content.isEmpty();
  }

  @Override
  public @NotNull Notation then(@NotNull Notation next) {
    if (next instanceof Text t) {
      return txt(content + t.content);
    }
    return super.then(next);
  }

  @Override
  public Notation toNotation() {
    return txt("\"" + content + "\"");
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) throws IOException {
    printer.append(content);
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    return flatVisitor.fitText(content);
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
    if (o instanceof Text text) {
      return Objects.equals(content, text.content);
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(content);
  }
}
