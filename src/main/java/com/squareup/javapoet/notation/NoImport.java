package com.squareup.javapoet.notation;

import com.squareup.javapoet.Notate;
import com.squareup.javapoet.TypeName;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NoImport extends Notation {
  private final Notation inner;

  public NoImport(@NotNull Notation inner) {
    this(inner, inner.imports.stream().map(TypeName::canonicalName).collect(
        Collectors.toSet()));
  }

  public NoImport(@NotNull Notation inner, @NotNull Set<String> suppress) {
    super(
        Stream
            .concat(
                inner.names.entrySet().stream(),
                inner.imports
                    .stream()
                    .filter(t -> suppress.contains(t.canonicalName()) || suppress.contains(t.nameWhenImported()))
                    .map(t -> Map.entry(t, t.canonicalName()))
            )
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                )),
        inner.imports
            .stream()
            .filter(t -> !(suppress.contains(t.canonicalName()) || suppress.contains(t.nameWhenImported())))
            .collect(
                Collectors.toSet())
    );
    this.inner = inner;
  }

  @Override
  public boolean isEmpty() {
    return inner.isEmpty();
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) {
    printer.push(chunk.withNotation(inner));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    flatVisitor.push(chunk.withNotation(inner));
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
  public Notation toNotation() {
    return Notate.fnLike("NoImport", List.of(inner));
  }

  @Override
  @Contract(value = "null -> false", pure = true)
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof NoImport flat) {
      return Objects.equals(inner, flat.inner);
    }
    return false;
  }

  @Override
  @Contract(pure = true)
  public int hashCode() {
    return Objects.hash(inner);
  }
}
