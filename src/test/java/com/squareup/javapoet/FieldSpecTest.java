/*
 * Copyright © 2015 Square, Inc.
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

import javax.lang.model.element.Modifier;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.javapoet.FieldSpec.builder;
import static org.junit.Assert.assertThrows;

public class FieldSpecTest {
  @Test
  public void equalsAndHashCode() {
    var a = FieldSpec.builder(int.class, "foo").build();
    var b = FieldSpec.builder(int.class, "foo").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());
    a = FieldSpec
        .builder(int.class, "FOO", Modifier.PUBLIC, Modifier.STATIC)
        .build();
    b = FieldSpec
        .builder(int.class, "FOO", Modifier.PUBLIC, Modifier.STATIC)
        .build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());
  }

  @Test
  public void nullAnnotationsAddition() {
    @SuppressWarnings("DataFlowIssue") var expected = assertThrows(
        IllegalArgumentException.class,
        () -> builder(int.class, "foo").addAnnotations(null)
    );
    assertThat(expected.getMessage())
        .isEqualTo("annotationSpecs == null");
  }

  @Test
  public void modifyAnnotations() {
    var builder = FieldSpec.builder(int.class, "foo")
        .addAnnotation(Override.class)
        .addAnnotation(SuppressWarnings.class);

    builder.annotations.remove(1);
    assertThat(builder.build().annotations).hasSize(1);
  }

  @Test
  public void modifyModifiers() {
    var builder = FieldSpec.builder(int.class, "foo")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    builder.modifiers.remove(1);
    assertThat(builder.build().modifiers).containsExactly(Modifier.PUBLIC);
  }
}
