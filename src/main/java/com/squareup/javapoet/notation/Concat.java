package com.squareup.javapoet.notation;

import com.google.common.collect.Lists;
import com.squareup.javapoet.Notate;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Concat extends Notation {
  public final @NotNull List<Notation> content;

  public Concat(@NotNull List<Notation> content) {
    super(
        content.stream().unordered().flatMap(n -> n.names.entrySet().stream().unordered())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> {
                  if (Objects.equals(a, b)) {
                    return a;
                  }
                  throw new IllegalStateException(
                      "Cannot have the same tag pointing to both "
                          + a + " and " + b);
                }
            )),
        content.stream().unordered().flatMap(n -> n.imports.stream().unordered())
            .collect(Collectors.toSet()),
        content.stream().unordered().flatMap(n -> n.childContexts.stream().unordered())
            .collect(Collectors.toSet())
    );
    this.content = List.copyOf(content);
  }

  @Override
  public Notation toNotation() {
    return Notate.fnLike("Concat", content);
  }

  @Override
  public boolean isEmpty() {
    return content.stream().allMatch(Notation::isEmpty);
  }

  @Override
  public void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) {
    Lists.reverse(content).forEach(n -> printer.push(chunk.withNotation(n)));
  }

  @Override
  public @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    Lists.reverse(content).forEach(n ->
        flatVisitor.push(chunk.withNotation(n)));
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  @Override
  public void visit(@NotNull Visitor visitor) {
    visitor.accept(this);
    visitor.enter(this);
    content.forEach(n -> n.visit(visitor));
    visitor.exit(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Concat concat) {
      return Objects.equals(content, concat.content);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(content);
  }
}
