package com.squareup.javapoet.notation;

import com.squareup.javapoet.TypeName;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class StaticImportRef extends Notation {

  private final TypeName ref;
  private final String part;

  public StaticImportRef(TypeName ref, String part) {
    super(Map.of(), Set.of(), Set.of());
    this.ref = ref;
    this.part = part;
  }

  @Override
  public @NotNull Notation flat() {
    return this;
  }

  @Override
  public Notation toNotation() {
    return txt("StaticTypeRef(" + ref.nameWhenImported() + "." + part + ")");
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer, @NotNull Chunk chunk
  ) throws IOException {
    printer.append(part);
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    return flatVisitor.fitText(part);
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
    if (o instanceof StaticImportRef typeRef) {
      return Objects.equals(ref, typeRef.ref) && Objects.equals(part,
          typeRef.part
      );
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(ref, part);
  }
}
