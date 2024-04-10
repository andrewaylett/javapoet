package com.squareup.javapoet.notation;

import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.squareup.javapoet.Emitable;
import com.squareup.javapoet.TypeName;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Stream;

public abstract class Notation {
  // From java.util.Formatter
  // %[argument_index$][flags][width][.precision][t]conversion
  private static final String formatSpecifier
      = "%(?:\\d+\\$)?(?:[-#+ 0,(<]*)?(?:\\d+)?(?:\\.\\d+)?[tT]?[a-zA-Z%]";
  private static final Pattern fsPattern = Pattern.compile(formatSpecifier);
  public final Map<Object, String> names;
  public final Set<TypeName> imports;
  public final Set<Context> childContexts;

  public Notation(Map<Object, String> names, Set<TypeName> imports, Set<Context> childContexts) {
    this.names = Map.copyOf(names);
    this.imports = Set.copyOf(imports);
    this.childContexts = Set.copyOf(childContexts);
  }

  public Notation(Map<Object, String> names, Set<TypeName> imports) {
    this.names = Map.copyOf(names);
    this.imports = Set.copyOf(imports);
    if (this instanceof Context c) {
      this.childContexts = Set.of(c);
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

  public static @NotNull Notation name(
      @NotNull Object tag,
      @NotNull String suggestion
  ) {
    return new Name(tag, suggestion);
  }

  public static @NotNull Notation typeRef(@NotNull TypeName ref) {
    return new TypeRef(ref);
  }

  public static @NotNull Notation staticImport(@NotNull TypeName ref, @NotNull String name) {
    return new StaticImportRef(ref, name);
  }

  public static <T> @NotNull Notation literal(@NotNull T ref) {
    if (ref instanceof Emitable e) {
      return e.toNotation();
    }
    return new Literal<>(ref);
  }

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
            if (next instanceof Choice choice) {
              foundChoice = true;
              left = left.then(choice.left);
              right = right.then(choice.right);
            } else {
              left = left.then(next.flat());
              right = right.then(next);
            }
          }
          if (foundChoice) {
            return left.or(right);
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

  @Contract(value = "_ -> new", pure = true)
  public @NotNull Context inContext(@NotNull String name) {
    return new Context(name, this);
  }

  @Override
  public String toString() {
    var out = new StringWriter();
    try {
      var names = PriorityMap.from(this.names);
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

  public abstract Notation toNotation();

  public String toCode() {
    var out = new StringWriter();
    try {
      var names = PriorityMap.from(this.names);
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

  public abstract boolean isEmpty();

  public abstract void visit(
      @NotNull Printer.PrinterVisitor printer,
      @NotNull Chunk chunk
  ) throws IOException;

  public abstract @NotNull Printer.FlatResponse visit(
      @NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk
  );

  public abstract void visit(@NotNull Visitor visitor);

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  public interface Visitor extends Consumer<Notation> {
    default void enter(Notation n) {}
    default void exit(Notation n) {}
  }
}
