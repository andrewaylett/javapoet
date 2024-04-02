package com.squareup.javapoet.notation;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;

public class Printer {
  private final int width;
  private int col = 0;
  private final Deque<Chunk> chunks = new ArrayDeque<>();

  public Printer(@NotNull Notation notation, int width) {
    this.width = width;
    Chunk chunk = new Chunk(notation, 0, false);
    chunks.push(chunk);
  }

  public void print(@NotNull Writer out) throws IOException {
    while (!chunks.isEmpty()) {
      Chunk chunk = chunks.pop();
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

          final int[] remaining = {width - col};
          Deque<Chunk> stack = new ArrayDeque<>();

          while (true) {
            Chunk current;
            if (stack.isEmpty()) {
              if (chunks.isEmpty()) {
                return true;
              }
              current = chunks.pop();
            } else {
              current = stack.pop();
            }

            switch (current.visit(new FlatVisitor() {
              @Override
              public @NotNull FlatResponse fitText(@NotNull String content) {
                if (content.length() > remaining[0]) {
                  return FlatResponse.TOO_LONG;
                }
                remaining[0] -= content.length();
                return FlatResponse.INCONCLUSIVE;
              }

              @Override
              public void push(@NotNull Chunk flat) {
                stack.push(flat);
              }
            })) {
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

  public interface PrinterVisitor {
    void append(@NotNull String s) throws IOException;

    void newLine() throws IOException;

    void push(@NotNull Chunk chunk);

    boolean fits(@NotNull Chunk chunk);
  }

  public enum FlatResponse {
    INCONCLUSIVE, FITS, TOO_LONG,
  }
  public interface FlatVisitor {
    @NotNull FlatResponse fitText(@NotNull String content);

    void push(Chunk flat);
  }
}
