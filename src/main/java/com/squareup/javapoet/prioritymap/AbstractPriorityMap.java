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
package com.squareup.javapoet.prioritymap;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.AbstractMap;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractPriorityMap<K, V, D extends Deque<V>> extends AbstractMap<K, V> implements PriorityMap<K, V> {
  @Contract(pure = true)
  @Override
  public int size() {
    return getDelegate().size();
  }

  @Contract(pure = true)
  @Override
  public boolean isEmpty() {
    return getDelegate().isEmpty();
  }

  @Contract(pure = true)
  @Override
  public boolean containsValue(Object value) {
    return getDelegate()
        .values()
        .stream()
        .anyMatch(v -> Objects.equals(v.peek(), value));
  }

  @Contract(pure = true)
  @Override
  public boolean containsKey(Object key) {
    return getDelegate().containsKey(key);
  }

  @Contract(pure = true)
  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public @Nullable V get(Object key) {
    return getDelegate().getOrDefault(key, newDeque()).peek();
  }

  @Override
  public void clear() {
    getDelegate().clear();
  }

  @Contract(pure = true)
  @Nonnull
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return new PriorityEntrySet<>(this);
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public boolean remove(Object key, Object value) {
    var values = getDelegate().get(key);
    if (values == null) {
      return false;
    }
    try {
      return values.removeAll(List.of(value));
    } finally {
      if (values.isEmpty()) {
        getDelegate().remove(key);
      }
    }
  }

  @Override
  public V put(K key, V value) {
    return getDelegate().compute(key, (k, old) -> {
      if (old == null) {
        old = newDeque();
      }
      old.push(value);
      return old;
    }).peek();
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public @Nullable V remove(Object key) {
    var deque = getDelegate().get(key);
    if (deque == null) {
      return null;
    }
    try {
      return deque.pop();
    } finally {
      if (deque.isEmpty()) {
        getDelegate().remove(key);
      }
    }
  }

  protected abstract Map<K, D> getDelegate();
  protected abstract D newDeque();
}
