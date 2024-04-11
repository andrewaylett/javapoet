/*
 * Copyright ©️ 2024 Andrew Aylett
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
