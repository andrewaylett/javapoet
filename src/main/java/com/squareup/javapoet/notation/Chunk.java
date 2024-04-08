package com.squareup.javapoet.notation;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.util.Map;

public class Chunk {
  public final boolean flat;
  protected final String indent;
  private final Notation notation;
  private final Map<Object, String> names;

  @Contract(pure = true)
  public Chunk(
      Notation notation, String indent, boolean flat, Map<Object, String> names
  ) {
    this.notation = notation;
    this.indent = indent;
    this.flat = flat;
    this.names = names;
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk withNotation(Notation notation) {
    return new Chunk(notation, this.indent, this.flat, names);
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk indented(String indent) {
    return new Chunk(this.notation, this.indent + indent, this.flat, names);
  }

  @Contract(value = "-> new", pure = true)
  public Chunk flat() {
    return new Chunk(this.notation, this.indent, true, names);
  }

  public void visit(Printer.PrinterVisitor printer) throws IOException {
    notation.visit(printer, this);
  }

  public Printer.FlatResponse visit(Printer.FlatVisitor flatVisitor) {
    return notation.visit(flatVisitor, this);
  }

  public String getName(Object tag) {
    return names.get(tag);
  }
}
