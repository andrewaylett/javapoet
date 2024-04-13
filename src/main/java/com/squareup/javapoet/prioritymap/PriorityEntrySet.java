package com.squareup.javapoet.prioritymap;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractSet;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PriorityEntrySet<K, V, D extends Deque<V>>
    extends AbstractSet<Map.Entry<K, V>> {
  private final Map<K, D> delegate;
  private final Supplier<D> newDeque;

  @Contract(pure = true)
  public PriorityEntrySet(AbstractPriorityMap<K, V, D> delegate) {
    this.delegate = delegate.getDelegate();
    this.newDeque = delegate::newDeque;
  }

  @Contract(pure = true)
  @Override
  public @NotNull Iterator<Map.Entry<K, V>> iterator() {
    return new AbstractEntrySetIterator<>(delegate
        .entrySet()
        .iterator());
  }

  @Contract(pure = true)
  @Override
  public int size() {
    return delegate.size();
  }

  @Contract(pure = true)
  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean add(Map.Entry<K, V> kvEntry) {
    delegate.compute(kvEntry.getKey(), (k, old) -> {
      if (old == null) {
        old = newDeque.get();
      }
      old.push(kvEntry.getValue());
      return old;
    });
    return true;
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public boolean remove(Object o) {
    if (o instanceof Map.Entry<?, ?> e) {
      var deque = delegate.get(e.getKey());
      if (deque == null) {
        return false;
      }
      try {
        var value = e.getValue();
        return deque.removeAll(List.of(value));
      } finally {
        if (deque.isEmpty()) {
          delegate.remove(e.getKey());
        }
      }
    } else {
      return false;
    }
  }
}
