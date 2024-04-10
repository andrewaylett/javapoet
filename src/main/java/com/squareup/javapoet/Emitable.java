package com.squareup.javapoet;

import com.squareup.javapoet.notation.Notation;

public interface Emitable {
  Notation toNotation();

  default Notation toDeclaration() {
    return toNotation();
  }
}