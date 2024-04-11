/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.javapoet;

import com.google.common.base.Splitter;
import com.squareup.javapoet.notation.Concat;
import com.squareup.javapoet.notation.NewLine;
import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkState;
import static com.squareup.javapoet.Util.stringLiteralWithDoubleQuotes;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.hoistChoice;
import static com.squareup.javapoet.notation.Notation.literal;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.staticImport;
import static com.squareup.javapoet.notation.Notation.txt;
import static com.squareup.javapoet.notation.Notation.typeRef;

/**
 * A fragment of a .java file, potentially containing declarations, statements, and documentation.
 * Code blocks are not necessarily well-formed Java code, and are not validated. This class assumes
 * javac will check correctness later!
 *
 * <p>Code blocks support placeholders like {@link java.text.Format}. Where {@link String#format}
 * uses percent {@code %} to reference target values, this class uses dollar sign {@code $} and has
 * its own set of permitted placeholders:
 *
 * <ul>
 *   <li>{@code $L} emits a <em>literal</em> value with no escaping. Arguments for literals may be
 *       strings, primitives, {@linkplain TypeSpec type declarations}, {@linkplain AnnotationSpec
 *       annotations} and even other code blocks.
 *   <li>{@code $N} emits a <em>name</em>, using name collision avoidance where necessary. Arguments
 *       for names may be strings (actually any {@linkplain CharSequence character sequence}),
 *       {@linkplain ParameterSpec parameters}, {@linkplain FieldSpec fields}, {@linkplain
 *       MethodSpec methods}, and {@linkplain TypeSpec types}.
 *   <li>{@code $S} escapes the value as a <em>string</em>, wraps it with double quotes, and emits
 *       that. For example, {@code 6" sandwich} is emitted {@code "6\" sandwich"}.
 *   <li>{@code $T} emits a <em>type</em> reference. Types will be imported if possible. Arguments
 *       for types may be {@linkplain Class classes}, {@linkplain javax.lang.model.type.TypeMirror
 * ,*       type mirrors}, and {@linkplain javax.lang.model.element.Element elements}.
 *   <li>{@code $$} emits a dollar sign.
 *   <li>{@code $W} emits a space or a newline, depending on its position on the line. This prefers
 *       to wrap lines before 100 columns.
 *   <li>{@code $Z} acts as a zero-width space. This prefers to wrap lines before 100 columns.
 *   <li>{@code $>} increases the indentation level.
 *   <li>{@code $<} decreases the indentation level.
 *   <li>{@code $[} begins a statement. For multiline statements, every line after the first line
 *       is double-indented.
 *   <li>{@code $]} ends a statement.
 * </ul>
 */
public final class CodeBlock implements Emitable {
  private static final Pattern NAMED_ARGUMENT =
      Pattern.compile("\\$(?<argumentName>[\\w_]+):(?<typeChar>\\w).*");
  private static final Pattern LOWERCASE = Pattern.compile("[a-z]+[\\w_]*");

  /**
   * A heterogeneous list containing string literals and value placeholders.
   */
  final List<String> formatParts;
  final List<Object> args;

  private CodeBlock(Builder builder) {
    this.formatParts = Util.immutableList(builder.formatParts);
    this.args = Util.immutableList(builder.args);
  }

  public static CodeBlock of(String format, Object... args) {
    return new Builder().add(format, args).build();
  }

  /**
   * Joins {@code codeBlocks} into a single {@link CodeBlock}, each separated by {@code separator}.
   * For example, joining {@code String s}, {@code Object o} and {@code int i} using {@code ", "}
   * would produce {@code String s, Object o, int i}.
   */
  public static CodeBlock join(
      Iterable<CodeBlock> codeBlocks, String separator
  ) {
    return StreamSupport
        .stream(codeBlocks.spliterator(), false)
        .collect(joining(separator));
  }

  /**
   * A {@link Collector} implementation that joins {@link CodeBlock} instances together into one
   * separated by {@code separator}. For example, joining {@code String s}, {@code Object o} and
   * {@code int i} using {@code ", "} would produce {@code String s, Object o, int i}.
   */
  public static Collector<CodeBlock, ?, CodeBlock> joining(String separator) {
    return Collector.of(() -> new CodeBlockJoiner(separator, builder()),
        CodeBlockJoiner::add,
        CodeBlockJoiner::merge,
        CodeBlockJoiner::join
    );
  }

  /**
   * A {@link Collector} implementation that joins {@link CodeBlock} instances together into one
   * separated by {@code separator}. For example, joining {@code String s}, {@code Object o} and
   * {@code int i} using {@code ", "} would produce {@code String s, Object o, int i}.
   */
  public static Collector<CodeBlock, ?, CodeBlock> joining(
      String separator, String prefix, String suffix
  ) {
    var builder = builder().add("$N", prefix);
    return Collector.of(() -> new CodeBlockJoiner(separator, builder),
        CodeBlockJoiner::add,
        CodeBlockJoiner::merge,
        joiner -> {
          builder.add(CodeBlock.of("$N", suffix));
          return joiner.join();
        }
    );
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEmpty() {
    return formatParts.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    if (getClass() != o.getClass()) {
      return false;
    }
    return toString().equals(o.toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public String toString() {
    return toNotation(true).toCode();
  }

  public Builder toBuilder() {
    var builder = new Builder();
    builder.formatParts.addAll(formatParts);
    builder.args.addAll(args);
    return builder;
  }

  @Override
  public Notation toNotation() {
    return toNotation(false);
  }

  public Notation toNotation(boolean stripResult) {
    return toNotation(stripResult, JavaFile.CURRENT_STATIC_IMPORTS.get());
  }

  public Notation toNotation(boolean stripResult, Set<String> staticImports) {
    if (this.isEmpty()) {
      return empty();
    }
    var staticImportClassNames = staticImports
        .stream()
        .flatMap(im -> Stream.iterate(im,
            i -> !i.isEmpty(),
            i -> i.lastIndexOf('.') >= 0 ? i.substring(0, i.lastIndexOf('.')) : ""
        ))
        .collect(Collectors.toSet());
    var a = 0;
    var partIterator = this.formatParts.listIterator();
    var stack = new ArrayDeque<ArrayList<Notation>>();
    var builder = new ArrayList<Notation>();
    @Nullable ClassName deferredTypeName = null;
    while (partIterator.hasNext()) {
      var part = partIterator.next();
      switch (part) {
        case "$L", "$N":
          var literal = literal(this.args.get(a++));
          if (literal instanceof Concat concat) {
            builder.addAll(concat.content);
          } else {
            builder.add(literal);
          }
          break;

        case "$S":
          var string = (String) this.args.get(a++);
          // Emit null as a literal null: no quotes.
          builder.add(string != null
              ? stringLiteralWithDoubleQuotes(string)
              : txt("null"));
          break;

        case "$T": {
          var typeName = (TypeName) this.args.get(a++);
          // defer "typeName.emit(this)" if next format part will be handled by the default case
          if (typeName instanceof ClassName candidate
              && partIterator.hasNext()) {
            if (!this.formatParts
                .get(partIterator.nextIndex())
                .startsWith("$")) {
              if (staticImportClassNames.contains(candidate.canonicalName)) {
                checkState(deferredTypeName == null,
                    "pending type for static import?!"
                );
                deferredTypeName = candidate;
                break;
              }
            }
          }
          builder.add(typeRef(typeName));
          break;
        }

        case "$$":
          builder.add(txt("$"));
          break;

        case "$[": {
          stack.push(builder);
          builder = new ArrayList<>();
        }
        break;
        case "$>": {
          var addNL = false;
          if (!builder.isEmpty() && builder.get(
              builder.size() - 1) instanceof NewLine) {
            addNL = true;
            builder.remove(builder.size() - 1);
          }
          stack.push(builder);
          builder = new ArrayList<>();
          if (addNL) {
            builder.add(nl());
          }
        }
        break;

        case "$<": {
          builder = unindent(stack, builder);
          break;
        }

        case "$]": {
          builder = popStatement(builder, stack);
          break;
        }

        case "$W":
          builder = processTokenAfterNewline(partIterator, stack, builder);
          builder.add(txt(" ").or(nl()));
          break;

        case "$Z":
          builder = processTokenAfterNewline(partIterator, stack, builder);
          builder.add(empty().or(nl()));
          break;

        default:
          // handle deferred type
          if (deferredTypeName != null) {
            if (part.startsWith(".")) {
              emitStaticImportMember(staticImports,
                  deferredTypeName,
                  part
              ).forEach(builder::add);
              // okay, static import hit and all was emitted,
              // so clean-up and jump to next part
              deferredTypeName = null;
              break;
            }
            builder.add(typeRef(deferredTypeName));
            deferredTypeName = null;
          }

          splitAtNewLines(part).forEach(builder::add);
          break;
      }
    }
    while (!stack.isEmpty()) {
      builder = unindent(stack, builder);
    }
    if (stripResult) {
      stripBuilder(builder);
    }
    return builder.stream().collect(Notation.hoistChoice());
  }

  private ArrayList<Notation> processTokenAfterNewline(
      ListIterator<String> partIterator,
      ArrayDeque<ArrayList<Notation>> stack,
      ArrayList<Notation> builder
  ) {
    if (partIterator.hasNext()) {
      var next = partIterator.next();
      switch (next) {
        case "$>", "$[":
          stack.push(builder);
          builder = new ArrayList<>();
          break;
        case "$]": {
          builder = popStatement(builder, stack);
          break;
        }
        case "$<": {
          builder = unindent(stack, builder);
          break;
        }
        default:
          // move back again
          partIterator.previous();
          break;
      }
    }
    return builder;
  }

  private static @Nonnull ArrayList<Notation> popStatement(
      ArrayList<Notation> builder,
      ArrayDeque<ArrayList<Notation>> stack
  ) {
    var statementBuilder = builder;
    try {
      builder = stack.pop();
    } catch (NoSuchElementException e) {
      throw new IllegalStateException("statement exit $] has no matching statement enter $[",
          e
      );
    }
    if (!statementBuilder.isEmpty()) {
      stripBuilder(statementBuilder);
      builder.add(Notation.statement(statementBuilder
          .stream()
          .collect(hoistChoice()).indent().indent()));
      builder.add(nl());
    }
    return builder;
  }

  private static void stripBuilder(ArrayList<Notation> builder) {
    while (!builder.isEmpty() && builder.get(0) instanceof NewLine) {
      builder.remove(0);
    }
    while (!builder.isEmpty() && builder.get(
        builder.size() - 1) instanceof NewLine) {
      builder.remove(builder.size() - 1);
    }
  }

  @NotNull
  private ArrayList<Notation> unindent(
      ArrayDeque<ArrayList<Notation>> stack,
      ArrayList<Notation> builder
  ) {
    var addNL = false;
    while (!builder.isEmpty() && builder.get(
        builder.size() - 1) instanceof NewLine) {
      builder.remove(builder.size() - 1);
      addNL = true;
    }
    var indented = builder.stream().collect(hoistChoice());
    builder = stack.pop();
    builder.add(indented.indent());
    if (addNL) {
      builder.add(nl());
    }
    return builder;
  }

  private Stream<Notation> splitAtNewLines(String part) {
    var pieces = Splitter.on('\n').splitToStream(part);

    return pieces
        .flatMap(t -> t.isEmpty() ? Stream.of(nl()) : Stream.of(nl(), txt(t)))
        .skip(1);
  }

  private Stream<Notation> emitStaticImportMember(
      Set<String> staticImports, TypeName enclosing, String part
  ) {
    var builder = Stream.<Notation>builder();
    builder.add(typeRef(enclosing));
    splitAtNewLines(part).forEach(builder);
    var partWithoutLeadingDot = part.substring(1);
    if (partWithoutLeadingDot.isEmpty()) {
      return builder.build();
    }
    var first = partWithoutLeadingDot.charAt(0);
    if (!Character.isJavaIdentifierStart(first)) {
      return builder.build();
    }
    var memberName = extractMemberName(partWithoutLeadingDot);
    var explicit = enclosing.canonicalName() + "." + memberName;
    var wildcard = enclosing.canonicalName() + ".*";
    if (staticImports.contains(explicit) || staticImports.contains(wildcard)) {
      var staticBuilder = Stream.<Notation>builder();
      staticBuilder.add(staticImport(enclosing, memberName));
      splitAtNewLines(part.substring(memberName.length() + 1)).forEach(staticBuilder);
      return staticBuilder.build();
    }
    return builder.build();
  }

  private static String extractMemberName(String part) {
    checkArgument(Character.isJavaIdentifierStart(part.charAt(0)),
        "not an identifier: %s",
        part
    );
    for (var i = 1; i <= part.length(); i++) {
      if (!SourceVersion.isIdentifier(part.substring(0, i))) {
        return part.substring(0, i - 1);
      }
    }
    return part;
  }

  public static final class Builder {
    final List<String> formatParts = new ArrayList<>();
    final List<Object> args = new ArrayList<>();

    private Builder() {
    }

    public boolean isEmpty() {
      return formatParts.isEmpty();
    }

    /**
     * Adds code using named arguments.
     *
     * <p>Named arguments specify their name after the '$' followed by : and the corresponding type
     * character. Argument names consist of characters in {@code a-z, A-Z, 0-9, and _} and must
     * start with a lowercase character.
     *
     * <p>For example, to refer to the type {@link java.lang.Integer} with the argument name {@code
     * clazz} use a format string containing {@code $clazz:T} and include the key {@code clazz} with
     * value {@code java.lang.Integer.class} in the argument map.
     */
    public Builder addNamed(String format, Map<String, ?> arguments) {
      var p = 0;

      for (var argument : arguments.keySet()) {
        checkArgument(
            LOWERCASE.matcher(argument).matches(),
            "argument '%s' must start with a lowercase character",
            argument
        );
      }

      while (p < format.length()) {
        var nextP = format.indexOf("$", p);
        if (nextP == -1) {
          formatParts.add(format.substring(p));
          break;
        }

        if (p != nextP) {
          formatParts.add(format.substring(p, nextP));
          p = nextP;
        }

        Matcher matcher = null;
        var colon = format.indexOf(':', p);
        if (colon != -1) {
          var endIndex = Math.min(colon + 2, format.length());
          matcher = NAMED_ARGUMENT.matcher(format.substring(p, endIndex));
        }
        if (matcher != null && matcher.lookingAt()) {
          var argumentName = matcher.group("argumentName");
          checkArgument(
              arguments.containsKey(argumentName),
              "Missing named argument for $%s",
              argumentName
          );
          var formatChar = matcher.group("typeChar").charAt(0);
          addArgument(format, formatChar, arguments.get(argumentName));
          formatParts.add("$" + formatChar);
          p += matcher.regionEnd();
        } else {
          checkArgument(p < format.length() - 1, "dangling $ at end");
          checkArgument(
              isNoArgPlaceholder(format.charAt(p + 1)),
              "unknown format $%s at %s in '%s'",
              format.charAt(p + 1),
              p + 1,
              format
          );
          formatParts.add(format.substring(p, p + 2));
          p += 2;
        }
      }

      return this;
    }

    /**
     * Add code with positional or relative arguments.
     *
     * <p>Relative arguments map 1:1 with the placeholders in the format string.
     *
     * <p>Positional arguments use an index after the placeholder to identify which argument index
     * to use. For example, for a literal to reference the 3rd argument: "$3L" (1 based index)
     *
     * <p>Mixing relative and positional arguments in a call to add is invalid and will result in an
     * error.
     */
    public Builder add(String format, Object... args) {
      var hasRelative = false;
      var hasIndexed = false;

      var relativeParameterCount = 0;
      var indexedParameterCount = new int[args.length];

      for (var p = 0; p < format.length(); ) {
        if (format.charAt(p) != '$') {
          var nextP = format.indexOf('$', p + 1);
          if (nextP == -1) {
            nextP = format.length();
          }
          formatParts.add(format.substring(p, nextP));
          p = nextP;
          continue;
        }

        p++; // '$'.

        // Consume zero or more digits, leaving 'c' as the first non-digit char after the '$'.
        var indexStart = p;
        char c;
        do {
          checkArgument(p < format.length(),
              "dangling format characters in '%s'",
              format
          );
          c = format.charAt(p++);
        } while (c >= '0' && c <= '9');
        var indexEnd = p - 1;

        // If 'c' doesn't take an argument, we're done.
        if (isNoArgPlaceholder(c)) {
          checkArgument(
              indexStart == indexEnd,
              "$$, $>, $<, $[, $], $W, and $Z may not have an index"
          );
          formatParts.add("$" + c);
          continue;
        }

        // Find either the indexed argument, or the relative argument. (0-based).
        int index;
        if (indexStart < indexEnd) {
          index = Integer.parseInt(format.substring(indexStart, indexEnd)) - 1;
          hasIndexed = true;
          if (args.length > 0) {
            indexedParameterCount[index
                % args.length]++; // modulo is needed, checked below anyway
          }
        } else {
          index = relativeParameterCount;
          hasRelative = true;
          relativeParameterCount++;
        }

        checkArgument(
            index >= 0 && index < args.length,
            "index %d for '%s' not in range (received %s arguments)",
            index + 1,
            format.substring(indexStart - 1, indexEnd + 1),
            args.length
        );
        checkArgument(
            !hasIndexed || !hasRelative,
            "cannot mix indexed and positional parameters"
        );

        addArgument(format, c, args[index]);

        formatParts.add("$" + c);
      }

      if (hasRelative) {
        checkArgument(
            relativeParameterCount >= args.length,
            "unused arguments: expected %s, received %s",
            relativeParameterCount,
            args.length
        );
      }
      if (hasIndexed) {
        List<String> unused = new ArrayList<>();
        for (var i = 0; i < args.length; i++) {
          if (indexedParameterCount[i] == 0) {
            unused.add("$" + (i + 1));
          }
        }
        var s = unused.size() == 1 ? "" : "s";
        checkArgument(unused.isEmpty(),
            "unused argument%s: %s",
            s,
            String.join(", ", unused)
        );
      }
      return this;
    }

    private boolean isNoArgPlaceholder(char c) {
      return c == '$' || c == '>' || c == '<' || c == '[' || c == ']'
          || c == 'W' || c == 'Z';
    }

    private void addArgument(String format, char c, Object arg) {
      switch (c) {
        case 'N':
          this.args.add(argToName(arg));
          break;
        case 'L':
          this.args.add(argToLiteral(arg));
          break;
        case 'S':
          this.args.add(argToString(arg));
          break;
        case 'T':
          this.args.add(argToType(arg));
          break;
        default:
          throw new IllegalArgumentException(String.format("invalid format string: '%s'",
              format
          ));
      }
    }

    private String argToName(Object o) {
      if (o instanceof CharSequence) {
        return o.toString();
      }
      if (o instanceof ParameterSpec) {
        return ((ParameterSpec) o).name;
      }
      if (o instanceof FieldSpec) {
        return ((FieldSpec) o).name;
      }
      if (o instanceof MethodSpec) {
        return ((MethodSpec) o).name;
      }
      if (o instanceof TypeSpec) {
        return ((TypeSpec) o).name;
      }
      throw new IllegalArgumentException("expected name but was " + o);
    }

    private Object argToLiteral(Object o) {
      return o;
    }

    @Contract(value = "null -> null", pure = true)
    private @Nullable String argToString(Object o) {
      return o != null ? String.valueOf(o) : null;
    }

    @Contract("null -> fail")
    private TypeName argToType(Object o) {
      if (o instanceof TypeName) {
        return (TypeName) o;
      }
      if (o instanceof TypeMirror) {
        return TypeName.get((TypeMirror) o);
      }
      if (o instanceof Element) {
        return TypeName.get(((Element) o).asType());
      }
      if (o instanceof Type) {
        return TypeName.get((Type) o);
      }
      throw new IllegalArgumentException(
          "expected type but was a " + o.getClass());
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "if (foo == 5)".
     *                    Shouldn't contain braces or newline characters.
     */
    public Builder beginControlFlow(String controlFlow, Object... args) {
      add(controlFlow + " {\n", args);
      indent();
      return this;
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "else if (foo == 10)".
     *                    Shouldn't contain braces or newline characters.
     */
    public Builder nextControlFlow(String controlFlow, Object... args) {
      unindent();
      add("} " + controlFlow + " {\n", args);
      indent();
      return this;
    }

    public Builder endControlFlow() {
      unindent();
      add("}\n");
      return this;
    }

    /**
     * @param controlFlow the optional control flow construct and its code, such as
     *                    "while(foo == 20)". Only used for "do/while" control flows.
     */
    public Builder endControlFlow(String controlFlow, Object... args) {
      unindent();
      add("} " + controlFlow + ";\n", args);
      return this;
    }

    public Builder addStatement(String format, Object... args) {
      add("$[");
      add(format, args);
      add(";\n$]");
      return this;
    }

    public Builder addStatement(CodeBlock codeBlock) {
      return addStatement("$L", codeBlock);
    }

    public Builder add(CodeBlock codeBlock) {
      formatParts.addAll(codeBlock.formatParts);
      args.addAll(codeBlock.args);
      return this;
    }

    public Builder indent() {
      this.formatParts.add("$>");
      return this;
    }

    public Builder unindent() {
      this.formatParts.add("$<");
      return this;
    }

    public Builder clear() {
      formatParts.clear();
      args.clear();
      return this;
    }

    public CodeBlock build() {
      return new CodeBlock(this);
    }
  }

  private static final class CodeBlockJoiner {
    private final String delimiter;
    private final Builder builder;
    private boolean first = true;

    CodeBlockJoiner(String delimiter, Builder builder) {
      this.delimiter = delimiter;
      this.builder = builder;
    }

    CodeBlockJoiner add(CodeBlock codeBlock) {
      if (!first) {
        builder.add(delimiter);
      }
      first = false;

      builder.add(codeBlock);
      return this;
    }

    CodeBlockJoiner merge(CodeBlockJoiner other) {
      var otherBlock = other.builder.build();
      if (!otherBlock.isEmpty()) {
        add(otherBlock);
      }
      return this;
    }

    CodeBlock join() {
      return builder.build();
    }
  }
}
