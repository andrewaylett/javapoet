package com.squareup.javapoet.notation;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.Notate;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class Context extends Notation {
  public final Map<Object, String> childNames;
  public final Set<Context> innerContexts;
  public final String simpleName;
  public final Notation inner;

  public Context(String name, Notation inner) {
    super(Map.of(), inner.imports);

    this.childNames = inner.names;
    this.innerContexts = inner.childContexts;
    this.simpleName = name;
    this.inner = inner;
  }

  @Override
  public Notation toNotation() {
    return Notate.fnLike("Context<" + simpleName + ">", List.of(inner));
  }

  @Override
  public boolean isEmpty() {
    return inner.isEmpty();
  }

  @Override
  public void visit(
      Printer.@NotNull PrinterVisitor printer, @NotNull Chunk chunk
  ) {
    chunk = apply(chunk);
    if (chunk.flat || printer.fits(chunk.withNotation(inner.flat()))) {
      printer.push(chunk.withNotation(inner.flat()));
    } else {
      printer.push(chunk.withNotation(inner));
    }
  }

  @Override
  public Printer.@NotNull FlatResponse visit(
      Printer.@NotNull FlatVisitor flatVisitor, @NotNull Chunk chunk
  ) {
    chunk = apply(chunk);
    if (chunk.flat) {
      flatVisitor.push(chunk.withNotation(inner.flat()));
    } else {
      flatVisitor.push(chunk.withNotation(inner));
    }
    return Printer.FlatResponse.INCONCLUSIVE;
  }

  private Chunk apply(Chunk chunk) {
    var className = chunk.scopes.isEmpty()
        ? ClassName.get(chunk.packageName, simpleName)
        : chunk.scopes
            .get(chunk.scopes.size() - 1)
            .className()
            .nestedClass(simpleName);

    var scope = new Chunk.Scope(this, className);
    var scopes =
        Stream.concat(chunk.scopes.stream(), Stream.of(scope)).toList();

    var newNames = new PriorityMap<>(chunk.names);
    var namesInScope = new HashMap<String, ClassName>();

    scopes
        .stream()
        .flatMap(s -> s
            .context()
            .immediateChildContextNames()
            .map(n -> s.className().nestedClass(n)))
        .forEach(c -> namesInScope.put(c.simpleName(), c));

    chunk.names
        .keySet()
        .stream()
        .flatMap(o -> o instanceof ClassName c ? Stream.of(c) : Stream.of())
        .filter(c -> c
            .canonicalName()
            .startsWith(className.topLevelClassName().canonicalName()))
        .map(n -> Map.<Object, String>entry(n,
            className.referenceTo(n, namesInScope)
        ))
        .forEach(newNames.entrySet()::add);

    this.childNames.forEach((k, v) -> {
      while (newNames.containsValue(v)) {
        v = v + "_";
      }
      if (!newNames.containsKey(k)) {
        newNames.put(k, v);
      }
    });

    return chunk.names(newNames).inScope(scope);
  }

  public Stream<String> allContextNames() {
    var builder = Stream.<String>builder();
    immediateChildContextNames().forEach(builder);
    innerContexts.stream().flatMap(Context::allContextNames).forEach(builder);
    return builder.build();
  }

  public Stream<String> immediateChildContextNames() {
    return innerContexts.stream().map(c -> c.simpleName);
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
    if (o instanceof Context statement) {
      return Objects.equals(inner, statement.inner);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(inner);
  }
}
