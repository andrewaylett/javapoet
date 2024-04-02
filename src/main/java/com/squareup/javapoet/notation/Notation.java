package com.squareup.javapoet.notation;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;

public abstract class Notation {
  @Contract(pure = true)
  public static @NotNull Notation empty() {
    return Empty.INSTANCE;
  }

  @Contract(pure = true)
  public static @NotNull Notation nl() {
    return NewLine.INSTANCE;
  }

  @Contract(pure = true)
  public static @NotNull Notation txt(@NotNull String s) {
    if (s.isEmpty()) {
      return Empty.INSTANCE;
    }
    return new Text(s);
  }

  // From java.util.Formatter
  // %[argument_index$][flags][width][.precision][t]conversion
  private static final String formatSpecifier
          = "%(?:\\d+\\$)?(?:[-#+ 0,(<]*)?(?:\\d+)?(?:\\.\\d+)?[tT]?[a-zA-Z%]";
  private static final Pattern fsPattern = Pattern.compile(formatSpecifier);

  @Contract(pure = true)
  public static @NotNull Notation format(@PrintFormat String fmt, @NotNull Notation... args) {
    String[] match = fsPattern.split(fmt);
    Notation accum = Notation.empty();
    for (int i = 0; i < Math.max(match.length, args.length); i++) {
      if (match.length > i) {
        accum = accum.then(Notation.txt(match[i]));
      }
      if (args.length > i) {
        accum = accum.then(args[i]);
      }
    }
    return accum;
  }

  public static Collector<Notation, Notation[], Notation> asLines() {
    return Collector.of(
            () -> new Notation[] {Notation.empty()},
            (Notation[] arr, Notation n) -> arr[0] = arr[0].then(n).then(Notation.nl()),
            (Notation[] arr, Notation[] arr2) -> new Notation[] {arr[0].then(arr2[0])},
            (Notation[] arr) -> arr[0]);
  }

  @Contract(value = "-> new", pure = true)
  public @NotNull Notation flat() {
    return new Flat(this);
  }

  @Contract(value = "_ -> new", pure = true)
  public @NotNull Notation indent(int indent) {
    return new Indent(indent, this);
  }

  @Contract(pure = true)
  public @NotNull Notation then(@NotNull Notation next) {
    if (next instanceof Empty) {
      return this;
    }
    return new Concat(this, next);
  }

  @Contract(value = "_ -> new", pure = true)
  public @NotNull Notation or(@NotNull Notation choice) {
    return new Choice(this, choice);
  }

  public abstract void visit(@NotNull Printer.PrinterVisitor printer, @NotNull Chunk chunk) throws IOException;

  public abstract @NotNull Printer.FlatResponse visit(@NotNull Printer.FlatVisitor flatVisitor, @NotNull Chunk chunk);
}
