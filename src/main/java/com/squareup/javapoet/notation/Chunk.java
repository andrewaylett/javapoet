package com.squareup.javapoet.notation;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

public class Chunk {
  public final boolean flat;
  protected final String indent;
  private final Notation notation;
  private final Map<Object, String> names;
  private final String indentBy;

  @Contract(pure = true)
  public Chunk(
      Notation notation, String indent, boolean flat, Map<Object, String> names, String indentBy
  ) {
    this.notation = notation;
    this.indent = indent;
    this.flat = flat;
    this.names = names;
    this.indentBy = indentBy;
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk withNotation(Notation notation) {
    return new Chunk(notation, this.indent, this.flat, names, indentBy);
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk indented(String indent) {
    return new Chunk(this.notation, this.indent + indent, this.flat, names, indentBy);
  }

  @Contract(value = "-> new", pure = true)
  public Chunk flat() {
    return new Chunk(this.notation, this.indent, true, names, indentBy);
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

  public String indentBy() {
    return indentBy;
  }
}
