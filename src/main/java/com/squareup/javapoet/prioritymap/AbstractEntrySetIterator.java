package com.squareup.javapoet.prioritymap;

import org.jetbrains.annotations.Contract;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

public class AbstractEntrySetIterator<K, V, D extends Deque<V>>
    implements Iterator<Map.Entry<K, V>> {
  private final Iterator<Map.Entry<K, D>> delegate;
  private transient Map.Entry<K, D> current;

  @Contract(pure = true)
  public AbstractEntrySetIterator(Iterator<Map.Entry<K, D>> iterator) {
    this.delegate = iterator;
  }

  @Contract(pure = true)
  @Override
  public boolean hasNext() {
    return delegate.hasNext();
  }

  @Override
  public Map.Entry<K, V> next() {
    current = delegate.next();
    return new PriorityEntry<>(current);
  }

  @Override
  public void remove() {
    var currentValue = current.getValue();
    currentValue.pop();
    if (currentValue.isEmpty()) {
      delegate.remove();
    }
  }
}
