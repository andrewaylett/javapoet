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

import com.squareup.javapoet.Emitable;
import com.squareup.javapoet.prioritymap.AbstractPriorityMap;
import com.squareup.javapoet.prioritymap.PriorityMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class Printer {
  private final int width;
  private final Deque<Chunk> chunks = new ArrayDeque<>();
  private int col = 0;

  @Contract(pure = true)
  public Printer(
      @NotNull Notation notation,
      int width,
      PriorityMap<Emitable, String> names,
      String indent,
      String packageName
  ) {
    this.width = width;
    var chunk = new Chunk(
        notation,
        "",
        false,
        names,
        indent,
        List.of(),
        packageName
    );
    chunks.push(chunk);
  }

  @Contract(pure = true)
  public void print(@NotNull Appendable inner) throws IOException {
    var out = new NoTrailingSpaceAppendable(inner);
    while (!chunks.isEmpty()) {
      var chunk = chunks.pop();
      chunk.visit(new PrinterVisitor() {
        @Override
        public void append(@NotNull String s) throws IOException {
          out.append(s);
          col += s.length();
        }

        @Override
        public void newLine() throws IOException {
          out.append('\n');
          col = 0;
        }

        @Override
        public void push(@NotNull Chunk chunk) {
          chunks.push(chunk);
        }

        @Override
        public boolean fits(@NotNull Chunk chunk) {
          if (col > width) {
            return false;
          }

          Deque<Chunk> stack = new ArrayDeque<>(chunks);
          stack.push(chunk);

          var visitor = new FlatVisitor() {
            int remaining = width - col;

            @Override
            public @NotNull FlatResponse fitText(@NotNull String content) {
              if (content.length() > remaining) {
                return FlatResponse.TOO_LONG;
              }
              remaining -= content.length();
              return FlatResponse.INCONCLUSIVE;
            }

            @Override
            public void push(@NotNull Chunk flat) {
              stack.push(flat);
            }
          };

          while (true) {
            Chunk current;
            if (stack.isEmpty()) {
              return true;
            } else {
              current = stack.pop();
            }

            switch (current.visit(visitor)) {
              case INCONCLUSIVE:
                break;
              case FITS:
                return true;
              case TOO_LONG:
                return false;
            }
          }
        }
      });
    }
  }

  public enum FlatResponse {
    INCONCLUSIVE, FITS, TOO_LONG,
  }

  public interface PrinterVisitor {
    void append(@NotNull String s) throws IOException;

    void newLine() throws IOException;

    void push(@NotNull Chunk chunk);

    boolean fits(@NotNull Chunk chunk);
  }

  public interface FlatVisitor {
    @NotNull
    FlatResponse fitText(@NotNull String content);

    void push(Chunk flat);
  }

  private static class NoTrailingSpaceAppendable implements Appendable {
    private final Appendable inner;
    final StringBuilder whitespace = new StringBuilder();

    NoTrailingSpaceAppendable(Appendable inner) {
      this.inner = inner;
    }

    @Override
    public Appendable append(CharSequence csq) throws IOException {
      if (csq == null) {
        return append('n').append('u').append('l').append('l');
      }
      for (var i = 0; i < csq.length(); i++) {
        append(csq.charAt(i));
      }
      return this;
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end)
        throws IOException {
      return append(csq.subSequence(start, end));
    }

    @Override
    public Appendable append(char c) throws IOException {
      if (c == '\n') {
        inner.append(c);
        whitespace.setLength(0);
      } else if (Character.isWhitespace(c)) {
        whitespace.append(c);
      } else {
        inner.append(whitespace);
        inner.append(c);
        whitespace.setLength(0);
      }
      return this;
    }
  }
}
