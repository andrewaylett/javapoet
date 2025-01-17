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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.Emitable;
import org.jetbrains.annotations.Contract;

import java.util.List;
import java.util.stream.Stream;

import static com.squareup.javapoet.notation.Notation.commaSeparated;
import static com.squareup.javapoet.notation.Notation.empty;
import static com.squareup.javapoet.notation.Notation.join;
import static com.squareup.javapoet.notation.Notation.nl;
import static com.squareup.javapoet.notation.Notation.txt;

public class Notate {
  @Contract(pure = true)
  public static Notation javadoc(Notation javadoc) {
    if (javadoc.isEmpty()) {
      return Notation.empty();
    }

    return Stream
        .of(txt("/**"), javadoc.indent(" * "), txt(" */"))
        .collect(Notation.asLines());
  }

  @Contract(pure = true)
  public static Notation annotations(
      List<AnnotationSpec> annotations, boolean inline
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

  @Contract(pure = true)
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

  @Contract(pure = true)
  public static Notation wrapAndIndent(
      Notation before, Notation wrapped, Notation after
  ) {
    if (wrapped.isEmpty()) {
      return before.then(after);
    }
    return before
        .then(wrapped)
        .then(after)
        .flat()
        .or(before
            .then(nl().then(wrapped).indent())
            .then(after.isEmpty() ? empty() : nl().then(after)));
  }

  @Contract(pure = true)
  public static Notation wrapAndIndentUnlessEmpty(
      Notation before, Notation wrapped, Notation after
  ) {
    if (wrapped.isEmpty()) {
      return before.then(after);
    }
    return before
        .then(nl().then(wrapped).indent())
        .then(after.isEmpty() ? empty() : nl().then(after));
  }

  public static Notation spacesOrWrapAndIndent(
      Notation before, Notation wrapped, Notation after
  ) {
    if (wrapped.isEmpty()) {
      return before.then(after);
    }
    return before
        .then(txt(" "))
        .then(wrapped)
        .then(after.isEmpty() ? empty() : txt(" ").then(after))
        .flat()
        .or(before
            .then(nl().then(wrapped).indent())
            .then(after.isEmpty() ? empty() : nl().then(after)));
  }

  @Contract(pure = true)
  public static Notation fnLike(String name, Iterable<Notation> content) {
    var builder = Stream.<Notation>builder();
    content.forEach(builder);
    return wrapAndIndent(
        txt(name + "("),
        builder.build().map(Notation::toNotation).collect(commaSeparated()),
        txt(")")
    );
  }
}
