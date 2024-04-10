package com.squareup.javapoet.notation;

import com.squareup.javapoet.ClassName;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class Chunk {
  public final boolean flat;
  public final String indent;
  public final Notation notation;
  public final PriorityMap<Object, String> names;
  public final Set<Context> currentContexts;
  public final String indentBy;
  public final Optional<ClassName> scope;
  public final String packageName;

  @Contract(pure = true)
  public Chunk(
      Notation notation,
      String indent,
      boolean flat,
      PriorityMap<Object, String> names,
      Set<Context> currentContexts,
      String indentBy,
      Optional<ClassName> scope,
      String packageName
  ) {
    this.notation = notation;
    this.indent = indent;
    this.flat = flat;
    this.names = names;
    this.currentContexts = currentContexts;
    this.indentBy = indentBy;
    this.scope = scope;
    this.packageName = packageName;
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk withNotation(Notation notation) {
    return new Chunk(notation,
        this.indent,
        this.flat,
        names,
        currentContexts,
        indentBy,
        scope,
        packageName
    );
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk indented(String indent) {
    return new Chunk(this.notation,
        this.indent + indent,
        this.flat,
        names,
        currentContexts,
        indentBy,
        scope,
        packageName
    );
  }

  @Contract(value = "-> new", pure = true)
  public Chunk flat() {
    return new Chunk(this.notation,
        this.indent,
        true,
        names,
        currentContexts,
        indentBy,
        scope,
        packageName
    );
  }

  @Contract(value = "_, _ -> new", pure = true)
  public Chunk inScope(Set<Context> contexts, ClassName scope) {
    return new Chunk(this.notation,
        this.indent,
        flat,
        names,
        contexts,
        indentBy,
        Optional.of(scope),
        packageName
    );
  }

  @Contract(value = "_ -> new", pure = true)
  public Chunk names(PriorityMap<Object, String> newNames) {
    return new Chunk(this.notation,
        this.indent,
        flat,
        newNames,
        currentContexts,
        indentBy,
        scope,
        packageName
    );
  }

  public void visit(Printer.PrinterVisitor printer) throws IOException {
    notation.visit(printer, this);
  }

  public Printer.FlatResponse visit(Printer.FlatVisitor flatVisitor) {
    return notation.visit(flatVisitor, this);
  }

  public @NotNull String getName(Object tag) {
    return requireNonNull(names.get(tag));
  }
}
