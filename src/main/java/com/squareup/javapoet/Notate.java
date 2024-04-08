package com.squareup.javapoet;

import com.squareup.javapoet.notation.Notation;

import java.util.List;
import java.util.stream.Stream;

import static com.squareup.javapoet.notation.Notation.commaSeparated;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;

public class Notate {
  public static Notation javadoc(Notation javadoc) {
    if (javadoc.isEmpty()) {
      return Notation.empty();
    }

    return Stream
        .of(txt("/**"), javadoc.indent(" * "), txt(" */"))
        .collect(Notation.asLines());
  }

  public static Notation annotations(
      List<AnnotationSpec> annotations,
      boolean inline
  ) {
    if (inline) {
      return annotations
          .stream()
          .map(Emitable::toNotation)
          .collect(join(txt(" ").or(nl())));
    } else {
      return annotations
          .stream()
          .map(Emitable::toNotation)
          .collect(Notation.asLines());
    }
  }

  public static Notation oneOrArray(List<CodeBlock> value) {
    if (value.size() == 1) {
      return value.get(0).toNotation();
    }
    var inner = value
        .stream()
        .map(CodeBlock::toNotation)
        .collect(join(txt(", ").or(txt(",\n"))));
    return spacesOrWrapAndIndent(txt("{"), inner, txt("}"));
  }

  public static Notation wrapAndIndent(
      Notation before,
      Notation wrapped,
      Notation after
  ) {
    if (wrapped.isEmpty()) {
      return before.then(after);
    }
    return before.flat()
        .then(wrapped.flat())
        .then(after.flat())
        .or(before
            .then(nl().then(wrapped).indent())
            .then(after.isEmpty() ? empty() : nl().then(after)));
  }

  public static Notation wrapAndIndentUnlessEmpty(
      Notation before,
      Notation wrapped,
      Notation after
  ) {
    if (wrapped.isEmpty()) {
      return before.then(after);
    }
    return before
        .then(nl().then(wrapped).indent())
        .then(after.isEmpty() ? empty() : nl().then(after));
  }

  public static Notation spacesOrWrapAndIndent(
      Notation before,
      Notation wrapped,
      Notation after
  ) {
    if (wrapped.isEmpty()) {
      return before.then(after);
    }
    return before
        .then(txt(" "))
        .then(wrapped.flat())
        .then(after.isEmpty() ? empty() : txt(" ").then(after))
        .or(before
            .then(nl().then(wrapped).indent())
            .then(after.isEmpty() ? empty() : nl().then(after)));
  }

  public static Notation fnLike(String name, Iterable<Notation> content) {
    var builder = Stream.<Notation>builder();
    content.forEach(builder);
    return wrapAndIndent(txt(name + "("), builder.build().map(Notation::toNotation).collect(commaSeparated()), txt(")"));
  }
}
