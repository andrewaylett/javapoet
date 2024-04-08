package com.squareup.javapoet.notation;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class NotationTest {
  @Test
  public void txt() {
    assertThat(Notation.txt("string"), equalTo(new Text("string")));
  }

  @Test
  public void emptyTxt() {
    assertThat(Notation.txt(""), equalTo(Empty.INSTANCE));
  }

  @Test
  public void nlTxt() {
    assertThat(Notation.txt("\n"), equalTo(Notation.nl()));
  }
}
