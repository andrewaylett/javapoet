package com.squareup.javapoet.notation;

import org.jetbrains.annotations.Contract;

import java.io.IOException;

public class Chunk {
  private final Notation notation;
  protected final int indent;
  public final boolean flat;

  @Contract(pure = true)
  public Chunk(Notation notation, int indent, boolean flat) {
    this.notation = notation;
    this.indent = indent;
    this.flat = flat;
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk withNotation(Notation notation) {
    return new Chunk(notation, this.indent, this.flat);
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk indented(int indent) {
    return new Chunk(this.notation, this.indent + indent, this.flat);
  }

  @Contract(value = "-> new", pure = true)
  public Chunk flat() {
    return new Chunk(this.notation, this.indent, true);
  }

  public void visit(Printer.PrinterVisitor printer) throws IOException {
    notation.visit(printer, this);
  }

  public Printer.FlatResponse visit(Printer.FlatVisitor flatVisitor) {
    return notation.visit(flatVisitor, this);
  }
}
