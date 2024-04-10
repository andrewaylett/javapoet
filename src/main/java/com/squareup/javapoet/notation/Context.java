package com.squareup.javapoet.notation;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.Notate;
import com.squareup.javapoet.TypeName;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
    var scope = chunk.scope
        .map(t -> t.nestedClass(simpleName))
        .orElseGet(() -> ClassName.get(chunk.packageName, simpleName));

    var newNames = new PriorityMap<>(chunk.names);
    var enclosedNames = allContextNames().collect(Collectors.toSet());
    chunk.names
        .keySet()
        .stream()
        .flatMap(o -> o instanceof ClassName c ? Stream.of(c) : Stream.of())
        .filter(c -> c
            .canonicalName()
            .startsWith(scope.topLevelClassName().canonicalName()))
        .map(n -> Map.<Object, String>entry(n, scope.referenceTo(n,
            enclosedNames
        )))
        .forEach(newNames.entrySet()::add);

    this.childNames.forEach((k, v) -> {
      while (newNames.containsValue(v)) {
        v = v + "_";
      }
      if (!newNames.containsKey(k)) {
        newNames.put(k, v);
      }
    });

    return chunk.names(newNames).inScope(innerContexts, scope);
  }

  public Stream<String> allContextNames() {
    var builder = Stream.<String>builder();
    builder.add(simpleName);
    innerContexts
        .stream()
        .flatMap(Context::allContextNames)
        .forEach(builder);
    return builder.build();
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
