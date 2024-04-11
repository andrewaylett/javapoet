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

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.javapoet.CodeBlock.builder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class CodeBlockTest {
  @Test
  public void equalsAndHashCode() {
    var a = CodeBlock.builder().build();
    var b = CodeBlock.builder().build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    a = CodeBlock.builder().add("$L", "taco").build();
    b = CodeBlock.builder().add("$L", "taco").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  public void of() {
    var a = CodeBlock.of("$L taco", "delicious");
    assertThat(a.toString()).isEqualTo("delicious taco");
  }

  @Test
  public void isEmpty() {
    assertTrue(CodeBlock.builder().isEmpty());
    assertTrue(CodeBlock.builder().add("").isEmpty());
    assertFalse(CodeBlock.builder().add(" ").isEmpty());
  }

  @Test
  public void indentCannotBeIndexed() {
    var exp = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1>", "taco").build()
    );
    assertThat(exp)
        .hasMessageThat()
        .isEqualTo("$$, $>, $<, $[, $], $W, and $Z may not have an index");
  }

  @Test
  public void deindentCannotBeIndexed() {
    var exp = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1<", "taco").build()
    );
    assertThat(exp)
        .hasMessageThat()
        .isEqualTo("$$, $>, $<, $[, $], $W, and $Z may not have an index");
  }

  @Test
  public void dollarSignEscapeCannotBeIndexed() {
    var exp = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1$", "taco").build()
    );
    assertThat(exp)
        .hasMessageThat()
        .isEqualTo("$$, $>, $<, $[, $], $W, and $Z may not have an index");
  }

  @Test
  public void statementBeginningCannotBeIndexed() {
    var exp = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1[", "taco").build()
    );
    assertThat(exp)
        .hasMessageThat()
        .isEqualTo("$$, $>, $<, $[, $], $W, and $Z may not have an index");
  }

  @Test
  public void statementEndingCannotBeIndexed() {
    var exp = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1]", "taco").build()
    );
    assertThat(exp)
        .hasMessageThat()
        .isEqualTo("$$, $>, $<, $[, $], $W, and $Z may not have an index");
  }

  @Test
  public void nameFormatCanBeIndexed() {
    var block = CodeBlock.builder().add("$1N", "taco").build();
    assertThat(block.toString()).isEqualTo("taco");
  }

  @Test
  public void literalFormatCanBeIndexed() {
    var block = CodeBlock.builder().add("$1L", "taco").build();
    assertThat(block.toString()).isEqualTo("taco");
  }

  @Test
  public void stringFormatCanBeIndexed() {
    var block = CodeBlock.builder().add("$1S", "taco").build();
    assertThat(block.toString()).isEqualTo("\"taco\"");
  }

  @Test
  public void typeFormatCanBeIndexed() {
    var block = CodeBlock.builder().add("$1T", String.class).build();
    assertThat(block.toString()).isEqualTo("java.lang.String");
  }

  @Test
  public void simpleNamedArgument() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("text", "taco");
    var block = CodeBlock.builder().addNamed("$text:S", map).build();
    assertThat(block.toString()).isEqualTo("\"taco\"");
  }

  @Test
  public void repeatedNamedArgument() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("text", "tacos");
    var block = CodeBlock.builder()
        .addNamed(
            "\"I like \" + $text:S + \". Do you like \" + $text:S + \"?\"",
            map
        )
        .build();
    assertThat(block.toString()).isEqualTo(
        "\"I like \" + \"tacos\" + \". Do you like \" + \"tacos\" + \"?\"");
  }

  @Test
  public void namedAndNoArgFormat() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("text", "tacos");
    var block = CodeBlock.builder()
        .addNamed("$>$text:L for $$3.50", map).build();
    assertThat(block.toString()).isEqualTo("tacos for $3.50");
  }

  @Test
  public void missingNamedArgument() {
    var expected = assertThrows(IllegalArgumentException.class, () -> {
      Map<String, Object> map = new LinkedHashMap<>();
      builder().addNamed("$text:S", map).build();
    });
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("Missing named argument for $text");
  }

  @Test
  public void lowerCaseNamed() {
    var expected = assertThrows(IllegalArgumentException.class, () -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("Text", "tacos");
      builder().addNamed("$Text:S", map).build();
    });
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("argument 'Text' must start with a lowercase character");
  }

  @Test
  public void multipleNamedArguments() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("pipe", System.class);
    map.put("text", "tacos");

    var block = CodeBlock.builder()
        .addNamed("$pipe:T.out.println(\"Let's eat some $text:L\");", map)
        .build();

    assertThat(block.toString()).isEqualTo(
        "java.lang.System.out.println(\"Let's eat some tacos\");");
  }

  @Test
  public void namedNewline() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("clazz", Integer.class);
    var block = CodeBlock.builder().addNamed("$clazz:T\n", map).build();
    assertThat(block.toString()).isEqualTo("java.lang.Integer");
  }

  @Test
  public void danglingNamed() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("clazz", Integer.class);
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().addNamed("$clazz:T$", map).build()
    );
    assertThat(expected).hasMessageThat().isEqualTo("dangling $ at end");
  }

  @Test
  public void indexTooHigh() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$2T", String.class).build()
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("index 2 for '$2T' not in range (received 1 arguments)");
  }

  @Test
  public void indexIsZero() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$0T", String.class).build()
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("index 0 for '$0T' not in range (received 1 arguments)");
  }

  @Test
  public void indexIsNegative() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$-1T", String.class).build()
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("invalid format string: '$-1T'");
  }

  @Test
  public void indexWithoutFormatType() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1", String.class).build()
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("dangling format characters in '$1'");
  }

  @Test
  public void indexWithoutFormatTypeNotAtStringEnd() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1 taco", String.class).build()
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("invalid format string: '$1 taco'");
  }

  @Test
  public void indexButNoArguments() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$1T").build()
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("index 1 for '$1T' not in range (received 0 arguments)");
  }

  @Test
  public void formatIndicatorAlone() {
    var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder().add("$", String.class).build()
    );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("dangling format characters in '$'");
  }

  @Test
  public void formatIndicatorWithoutIndexOrFormatType() {
    var expected =
        assertThrows(
            IllegalArgumentException.class,
            () -> builder().add("$ tacoString", String.class).build()
        );
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("invalid format string: '$ tacoString'");
  }

  @Test
  public void sameIndexCanBeUsedWithDifferentFormats() {
    var block = CodeBlock.builder()
        .add("$1T.out.println($1S)", ClassName.get(System.class))
        .build();
    assertThat(block.toString()).isEqualTo(
        "java.lang.System.out.println(\"java.lang.System\")");
  }

  @Test
  public void tooManyStatementEntersInOneGo() {
    var expected = assertThrows(IllegalStateException.class, () -> builder().add("$[$[").build().toNotation());
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("statement enter $[ followed by statement enter $[");
  }

  @Test
  public void statementExitWithoutStatementEnter() {
    var expected = assertThrows(IllegalStateException.class, () -> {
      var _ignored = builder().add("$]").build().toNotation();
    });
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("statement exit $] but no statement found");
  }

  @Test
  public void join() {
    List<CodeBlock> codeBlocks = new ArrayList<>();
    codeBlocks.add(CodeBlock.of("$S", "hello"));
    codeBlocks.add(CodeBlock.of("$T", ClassName.get("world", "World")));
    codeBlocks.add(CodeBlock.of("need tacos"));

    var joined = CodeBlock.join(codeBlocks, " || ");
    assertThat(joined.toString()).isEqualTo(
        "\"hello\" || world.World || need tacos");
  }

  @Test
  public void joining() {
    List<CodeBlock> codeBlocks = new ArrayList<>();
    codeBlocks.add(CodeBlock.of("$S", "hello"));
    codeBlocks.add(CodeBlock.of("$T", ClassName.get("world", "World")));
    codeBlocks.add(CodeBlock.of("need tacos"));

    var joined = codeBlocks.stream().collect(CodeBlock.joining(" || "));
    assertThat(joined.toString()).isEqualTo(
        "\"hello\" || world.World || need tacos");
  }

  @Test
  public void joiningSingle() {
    List<CodeBlock> codeBlocks = new ArrayList<>();
    codeBlocks.add(CodeBlock.of("$S", "hello"));

    var joined = codeBlocks.stream().collect(CodeBlock.joining(" || "));
    assertThat(joined.toString()).isEqualTo("\"hello\"");
  }

  @Test
  public void joiningWithPrefixAndSuffix() {
    List<CodeBlock> codeBlocks = new ArrayList<>();
    codeBlocks.add(CodeBlock.of("$S", "hello"));
    codeBlocks.add(CodeBlock.of("$T", ClassName.get("world", "World")));
    codeBlocks.add(CodeBlock.of("need tacos"));

    var joined = codeBlocks
        .stream()
        .collect(CodeBlock.joining(" || ", "start {", "} end"));
    assertThat(joined.toString()).isEqualTo(
        "start {\"hello\" || world.World || need tacos} end");
  }

  @Test
  public void clear() {
    var block = CodeBlock.builder()
        .addStatement("$S", "Test string")
        .clear()
        .build();

    assertThat(block.toString()).isEmpty();
  }
}
