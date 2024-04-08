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

import com.squareup.javapoet.notation.Notation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.StreamSupport;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.notation.Notation.asLines;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;

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
  final Notation notation;

  private CodeBlock(Notation notation) {
    this.notation = notation;
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
      Iterable<CodeBlock> codeBlocks,
      String separator
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
    return Collector.of(
        () -> new CodeBlockJoiner(separator, builder()),
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
    return Collector.of(
        () -> new CodeBlockJoiner(separator, builder),
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
    return notation.isEmpty();
  }

  @Override
  public Notation toNotation() {
    return notation;
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
    return toNotation().toCode();
  }

  public Builder toBuilder() {
    var builder = new Builder();
    builder.add(notation);
    return builder;
  }

  public static final class Builder {
    final ArrayList<Notation> lines;
    final Deque<Notation> notation;
    boolean inStatement = false;

    private Builder() {
      notation = new ArrayDeque<>();
      lines = new ArrayList<>();
      notation.push(Notation.empty());
    }

    public boolean isEmpty() {
      return notation.stream().allMatch(Notation::isEmpty);
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
        checkArgument(LOWERCASE.matcher(argument).matches(),
            "argument '%s' must start with a lowercase character", argument
        );
      }

      while (p < format.length()) {
        var nextP = format.indexOf("$", p);
        if (nextP == -1) {
          add(format.substring(p));
          break;
        }

        if (p != nextP) {
          add(format.substring(p, nextP));
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
          checkArgument(arguments.containsKey(argumentName),
              "Missing named argument for $%s",
              argumentName
          );
          var formatChar = matcher.group("typeChar").charAt(0);
          var prev = notation.pop();
          var next =
              addArgument(format, formatChar, arguments.get(argumentName));
          notation.push(prev.then(next));
          p += matcher.regionEnd();
        } else {
          checkArgument(p < format.length() - 1, "dangling $ at end");
          checkArgument(isNoArgPlaceholder(format.charAt(p + 1)),
              "unknown format $%s at %s in '%s'",
              format.charAt(p + 1),
              p + 1,
              format
          );
          add(format.substring(p, p + 2));
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
      format = format.replace("\n", "$W");

      for (var p = 0; p < format.length(); ) {
        if (format.charAt(p) != '$') {
          var nextP = format.indexOf('$', p + 1);
          if (nextP == -1) {
            nextP = format.length();
          }
          var prev = notation.pop();
          var next = txt(format.substring(p, nextP));
          notation.push(prev.then(next));
          p = nextP;
          continue;
        }

        p++; // '$'.

        // Consume zero or more digits, leaving 'c' as the first non-digit char after the '$'.
        var indexStart = p;
        char c;
        do {
          checkArgument(
              p < format.length(),
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
          switch (c) {
            case '$':
              notation.push(notation.pop().then(txt("$")));
              break;
            case '>':
              indent();
              break;
            case '<':
              unindent("", "");
              break;
            case '[':
              statement();
              break;
            case ']':
              unstatement();
              break;
            case 'W':
              lines.add(notation.pop().then(txt(" ")));
              notation.push(empty());
              break;
            case 'Z':
              lines.add(notation.pop());
              notation.push(empty());
              break;
            default:
              throw new IllegalArgumentException(
                  "Can't use '" + c + "' as a no arg placeholder");
          }
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

        checkArgument(index >= 0 && index < args.length,
            "index %d for '%s' not in range (received %s arguments)",
            index + 1,
            format.substring(indexStart - 1, indexEnd + 1),
            args.length
        );
        checkArgument(
            !hasIndexed || !hasRelative,
            "cannot mix indexed and positional parameters"
        );

        var prev = notation.pop();
        var next = addArgument(format, c, args[index]);
        notation.push(prev.then(next));
      }

      if (hasRelative) {
        checkArgument(relativeParameterCount >= args.length,
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
        checkArgument(
            unused.isEmpty(),
            "unused argument%s: %s",
            s,
            String.join(", ", unused)
        );
      }
      return this;
    }

    public Builder add(Notation n) {
      notation.push(notation.pop().then(n));
      return this;
    }

    private boolean isNoArgPlaceholder(char c) {
      return c == '$' || c == '>' || c == '<' || c == '[' || c == ']'
          || c == 'W' || c == 'Z';
    }

    private Notation addArgument(String format, char c, Object arg) {
      return switch (c) {
        case 'N' -> txt(argToName(arg));
        case 'L' -> Notation.literal(argToLiteral(arg));
        case 'S' -> txt(argToString(arg));
        case 'T' -> Notation.typeRef(argToType(arg));
        default -> throw new IllegalArgumentException(
            String.format("invalid format string: '%s'", format));
      };
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

    @Contract(pure = true)
    private String argToString(Object o) {
      return o != null ? "\"" + o + "\"" : "null";
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
      add(controlFlow + " ", args);
      indent();
      return this;
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "else if (foo == 10)".
     *                    Shouldn't contain braces or newline characters.
     */
    public Builder nextControlFlow(String controlFlow, Object... args) {
      unindent("{", "}");
      add(" " + controlFlow + " ", args);
      indent();
      return this;
    }

    public Builder endControlFlow() {
      unindent("{", "}");
      return this;
    }

    /**
     * @param controlFlow the optional control flow construct and its code, such as
     *                    "while(foo == 20)". Only used for "do/while" control flows.
     */
    public Builder endControlFlow(String controlFlow, Object... args) {
      unindent("{", "}");
      add(" " + controlFlow + ";", args);
      return this;
    }

    public Builder addStatement(String format, Object... args) {
      add("$[");
      add(format, args);
      add(";$]");
      return this;
    }

    public Builder addStatement(CodeBlock codeBlock) {
      return addStatement("$L", codeBlock);
    }

    public Builder add(CodeBlock codeBlock) {
      add(codeBlock.notation);
      return this;
    }

    public Builder gatherLines() {
      notation.push(lines.stream().collect(asLines()).then(notation.pop()));
      lines.clear();
      return this;
    }

    public Builder indent() {
      notation.push(Notation.empty());
      return this;
    }

    public Builder unindent(String before, String after) {
      gatherLines();
      var indented = notation.pop();
      var surrounding = notation.pop();
      notation.push(surrounding.then(Notate.wrapAndIndentUnlessEmpty(txt(before), indented, txt(after))));
      return this;
    }

    public Builder statement() {
      if (inStatement) {
        throw new IllegalStateException(
            "statement enter $[ followed by statement enter $[");
      }
      notation.push(Notation.empty());
      inStatement = true;
      return this;
    }

    public Builder unstatement() {
      if (!inStatement) {
        throw new IllegalStateException(
            "statement exit $] has no matching statement enter $[");
      }
      inStatement = false;
      gatherLines();
      var statement = notation.pop();
      var surrounding = notation.pop();
      if (surrounding.isEmpty()) {
        notation.push(Notation.statement(statement));
      } else {
        notation.push(surrounding
            .then(nl())
            .then(Notation.statement(statement)));
      }
      return this;
    }

    public Builder clear() {
      notation.clear();
      inStatement = false;
      notation.push(Notation.empty());
      return this;
    }

    public CodeBlock build() {
      if (inStatement) {
        unstatement();
      }
      gatherLines();
      while (this.notation.size() > 1) {
        unindent("", "");
      }
      return new CodeBlock(this.notation.pop());
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
