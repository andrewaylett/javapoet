/*
 * Copyright © 2024 Andrew Aylett
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.javapoet.notation;

import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.Emitable;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.prioritymap.HashPriorityMap;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Stream;

@Immutable
public abstract class Notation {
  // From java.util.Formatter
  // %[argument_index$][flags][width][.precision][t]conversion
  private static final String formatSpecifier
      = "%(?:\\d+\\$)?(?:[-#+ 0,(<]*)?(?:\\d+)?(?:\\.\\d+)?[tT]?[a-zA-Z%]";
  private static final Pattern fsPattern = Pattern.compile(formatSpecifier);
  public final ImmutableMap<Emitable, String> names;
  public final ImmutableSet<TypeName> imports;
  public final ImmutableSet<Context> childContexts;

  @Contract(pure = true)
  public Notation(Map<Emitable, String> names, Set<TypeName> imports, Set<Context> childContexts) {
    this.names = ImmutableMap.copyOf(names);
    this.imports = ImmutableSet.copyOf(imports);
    this.childContexts = ImmutableSet.copyOf(childContexts);
  }

  @Contract(pure = true)
  public Notation(Map<ParameterSpec, String> names, Set<TypeName> imports) {
    this.names = ImmutableMap.copyOf(names);
    this.imports = ImmutableSet.copyOf(imports);
    if (this instanceof Context c) {
      this.childContexts = ImmutableSet.of(c);
    } else {
      throw new IllegalArgumentException("Called two-param constructor of Notation when not a Context");
    }
  }

  @Contract(pure = true)
  public static @NotNull Notation empty() {
    return Empty.INSTANCE;
  }

  @Contract(pure = true)
  public static @NotNull Notation nl() {
    return NewLine.INSTANCE;
  }

  private static final LoadingCache<String, Notation>
      txtCache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
    @Override
    public @NotNull Notation load(@NotNull String key) {
      var pieces = Splitter.on('\n').splitToStream(key);

      return pieces
          .map(t -> t.isEmpty() ? empty() : new Text(t))
          .collect(asLines());
    }
  });

  @Contract(pure = true)
  public static @NotNull Notation txt(@NotNull String s) {
    try {
      return txtCache.get(s);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Contract(pure = true)
  public static @NotNull Notation name(
      @NotNull ParameterSpec tag,
      @NotNull String suggestion
  ) {
    return new Name(tag, suggestion);
  }

  @Contract(pure = true)
  public static @NotNull Notation typeRef(@NotNull TypeName ref) {
    return new TypeRef(ref);
  }

  @Contract(pure = true)
  public static @NotNull Notation staticImport(@NotNull TypeName ref, @NotNull String name) {
    return new StaticImportRef(ref, name);
  }

  @Contract(pure = true)
  public static @NotNull Notation literal(@NotNull Emitable ref) {
    if (ref instanceof CodeBlock c) {
      return new Literal(c.toNotation(true));
    }
    return new Literal(ref.toNotation());
  }

  @Contract(pure = true)
  public static @NotNull Notation literal(@NotNull Modifier ref) {
    return new Literal(txt(ref.toString()));
  }

  @Contract(pure = true)
  public static @NotNull Notation literal(@NotNull String ref) {
    return new Literal(txt(ref));
  }

  @Contract(pure = true)
  public static @NotNull Notation statement(@NotNull Notation ref) {
    return new Statement(ref);
  }

  @Contract(pure = true)
  public static @NotNull Notation format(
      @PrintFormat String fmt,
      @NotNull Notation... args
  ) {
    var match = fsPattern.split(fmt);
    var accum = Notation.empty();
    for (var i = 0; i < Math.max(match.length, args.length); i++) {
      if (match.length > i) {
        accum = accum.then(Notation.txt(match[i]));
      }
      if (args.length > i) {
        accum = accum.then(args[i]);
      }
    }
    return accum;
  }

  @Contract(value = "-> new", pure = true)
  public static @NotNull Collector<Notation, List<Notation>, ? extends Notation> asLines() {
    return join(nl());
  }

  @Contract(value = "-> new", pure = true)
  public static @NotNull Collector<Notation, List<Notation>, ? extends Notation> commaSeparated() {
    return join(txt(", ").or(txt(",\n")));
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull Collector<Notation, List<Notation>, ? extends Notation> join(
      Notation joiner
  ) {
    if (joiner instanceof Choice choice) {
      return join(choice);
    }
    return Collector.of(
        ArrayList::new,
        List::add,
        (List<Notation> arr, List<Notation> arr2) -> Stream
            .concat(arr.stream(), arr2.stream())
            .toList(),
        (List<Notation> arr) -> {
          if (arr.isEmpty()) {
            return Notation.empty();
          }
          var res = arr.get(0);
          if (arr.size() > 1) {
            for (var next : arr.subList(1, arr.size())) {
              if (next instanceof Indent indented) {
                res = res.then(joiner
                    .then(indented.inner)
                    .indent(indented.indent));
              } else {
                res = res.then(joiner).then(next);
              }
            }
          }
          return res;
        }
    );
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull Collector<Notation, List<Notation>, Choice> join(Choice joiner) {
    return Collector.of(
        ArrayList::new,
        List::add,
        (List<Notation> arr, List<Notation> arr2) -> Stream
            .concat(arr.stream(), arr2.stream())
            .toList(),
        (List<Notation> arr) -> arr
            .stream()
            .collect(join(joiner.left))
            .flat()
            .or(arr.stream().collect(join(joiner.right)))
    );
  }

  @Contract(pure = true)
  public static @NotNull Collector<Notation, List<Notation>, Notation> hoistChoice() {
    return Collector.of(
        ArrayList::new,
        List::add,
        (List<Notation> arr, List<Notation> arr2) -> Stream
            .concat(arr.stream(), arr2.stream())
            .toList(),
        (List<Notation> arr) -> {
          if (arr.isEmpty()) {
            return Notation.empty();
          }
          var foundChoice = false;
          var left = empty();
          var right = empty();
          for (var next : arr) {
            Function<Notation, Notation> rewrap = (x) -> x;
            while (next instanceof Indent || next instanceof Statement) {
              if (next instanceof Indent indent) {
                next = indent.inner;
                var prev = rewrap;
                rewrap = n -> n.indent(indent.indent);
                rewrap = rewrap.andThen(prev);
              } else {
                var statement = (Statement) next;
                next = statement.inner;
                var prev = rewrap;
                rewrap = Notation::statement;
                rewrap = rewrap.andThen(prev);
              }
            }

            if (next instanceof Choice choice) {
              foundChoice = true;
              left = left.then(rewrap.apply(choice.left));
              right = right.then(rewrap.apply(choice.right));
            } else {
              left = left.then(rewrap.apply(next));
              right = right.then(rewrap.apply(next));
            }
          }
          if (foundChoice) {
            return left.flat().or(right);
          }
          return right;
        }
    );
  }

  @Contract(value = "-> new", pure = true)
  public @NotNull Notation flat() {
    return new Flat(this);
  }

  @Contract(value = "_ -> new", pure = true)
  public @NotNull Notation indent(String indent) {
    return new Indent(Optional.of(indent), this);
  }

  @Contract(value = "_ -> new", pure = true)
  public @NotNull Notation indent(Optional<String> indent) {
    return new Indent(indent, this);
  }

  @Contract(value = "-> new", pure = true)
  public @NotNull Notation indent() {
    return new Indent(Optional.empty(), this);
  }

  @Contract(value = "-> new", pure = true)
  public @NotNull Notation suppressImports() {
    return new NoImport(this);
  }

  @Contract(value = "_ -> new", pure = true)
  public @NotNull Notation suppressImports(Set<String> suppressImports) {
    if (suppressImports.isEmpty()) {
      return this;
    }
    return new NoImport(this, suppressImports);
  }

  @Contract(pure = true)
  public @NotNull Notation then(@NotNull Notation next) {
    if (next instanceof Empty) {
      return this;
    }
    var content = new ArrayList<Notation>();
    if (this instanceof Concat concat) {
      content.addAll(concat.content);
    } else {
      content.add(this);
    }
    if (next instanceof Concat concat) {
      content.addAll(concat.content);
    } else {
      content.add(next);
    }
    return new Concat(content);
  }

  @Contract(value = "_ -> new", pure = true)
  public @NotNull Choice or(@NotNull Notation choice) {
    return new Choice(this, choice);
  }

  @Contract(value = "_, _ -> new", pure = true)
  public @NotNull Context inContext(@NotNull String name, Collection<? extends TypeName> typeVariableNames) {
    return new Context(Optional.of(name), this, Set.copyOf(typeVariableNames));
  }

  @Contract(value = "_ -> new", pure = true)
  public @NotNull Context inContext(Collection<? extends TypeName> typeVariableNames) {
    return new Context(Optional.empty(), this, Set.copyOf(typeVariableNames));
  }

  @Contract(pure = true)
  @Override
  public String toString() {
    var out = new StringWriter();
    try {
      var names = HashPriorityMap.from(this.names);
      for (var ty : this.imports) {
        names.put(ty, ty.canonicalName());
      }
      var printer = new Printer(toNotation(), 80, names, "| ", "");
      printer.print(out);
      return out.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  @Contract(pure = true)
  public abstract Notation toNotation();

  @Contract(pure = true)
  public String toCode() {
    var out = new StringWriter();
    try {
      var names = HashPriorityMap.from(this.names);
      for (var ty : this.imports) {
        names.put(ty, ty.canonicalName());
      }
      var printer = new Printer(this, 100, names, "  ", "");
      printer.print(out);
      return out.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  @Contract(pure = true)
  public abstract boolean isEmpty();

  public abstract void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) throws IOException;

  public abstract @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  );

  public abstract void visit(@NotNull Visitor visitor);

  @Contract(value = "null -> false", pure = true)
  @Override
  public abstract boolean equals(Object o);

  @Contract(pure = true)
  @Override
  public abstract int hashCode();

  public interface Visitor extends Consumer<Notation> {
    default void enter(Notation n) {}
    default void exit(Notation n) {}
  }
}
