package com.squareup.javapoet.prioritymap;

import org.jetbrains.annotations.Contract;

import java.util.Deque;
import java.util.Map;

@SuppressWarnings("DataFlowIssue")
public class PriorityEntry<K, V, D extends Deque<V>> implements Map.Entry<K, V> {
  private final Map.Entry<K, D> delegate;

  @Contract(pure = true)
  public PriorityEntry(Map.Entry<K, D> current) {
    this.delegate = current;
  }

  @Contract(pure = true)
  @Override
  public K getKey() {
    return delegate.getKey();
  }

  @Contract(pure = true)
  @Override
  public V getValue() {
    return delegate.getValue().peek();
  }

  @Override
  public V setValue(V value) {
    var delegateValue = delegate.getValue();
    try {
      return delegateValue.peek();
    } finally {
      delegateValue.push(value);
    }
  }
}
