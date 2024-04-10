package com.squareup.javapoet.notation;

import com.squareup.javapoet.Notate;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class Statement extends Notation {
  public final Notation inner;

  public Statement(Notation inner) {
    super(inner.names, inner.imports, inner.childContexts);

    inner.visit((Notation notation) -> {
      if (notation instanceof Statement) {
        throw new IllegalStateException(
            "statement enter $[ followed by statement enter $[");
      }
    });

    this.inner = inner;
  }

  @Override
  public Notation toNotation() {
    return Notate.fnLike("Statement", List.of(inner));
  }

  @Override
  public boolean isEmpty() {
    return inner.isEmpty();
  }

  @Override
  public void visit(
      Printer.@NotNull PrinterVisitor printer,
      @NotNull Chunk chunk
  ) {
    if (chunk.flat || printer.fits(chunk.withNotation(inner.flat()))) {
      printer.push(chunk.withNotation(inner.flat()));
    } else {
      printer.push(chunk.withNotation(inner));
    }
  }

  @Override
  public Printer.@NotNull FlatResponse visit(
      Printer.@NotNull FlatVisitor flatVisitor,
      @NotNull Chunk chunk
  ) {
    if (chunk.flat) {
      flatVisitor.push(chunk.withNotation(inner.flat()));
    } else {
      flatVisitor.push(chunk.withNotation(inner));
    }
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
    visitor.enter(this);
    inner.visit(visitor);
    visitor.exit(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Statement statement) {
      return Objects.equals(inner, statement.inner);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(inner);
  }
}
