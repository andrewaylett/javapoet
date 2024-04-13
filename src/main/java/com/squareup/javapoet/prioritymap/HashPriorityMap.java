package com.squareup.javapoet.prioritymap;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Contract;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class HashPriorityMap<K, V> extends AbstractPriorityMap<K, V, Deque<V>> {
  private final Map<K, Deque<V>> delegate;

  @Contract(pure = true)
  public HashPriorityMap() {
    delegate = new HashMap<>();
  }

  @Contract(pure = true)
  private HashPriorityMap(Map<K, Deque<V>> delegate) {
    this.delegate = delegate;
  }

  @Contract(pure = true)
  public HashPriorityMap(AbstractPriorityMap<K, V, ?> toCopy) {
    delegate = new HashMap<>();
    toCopy.getDelegate().forEach((key, value) -> getDelegate().put(key, new ArrayDeque<>(value)));
  }

  @Contract(pure = true)
  public static <K, V> HashPriorityMap<K, V> from(Map<K, V> source) {
    var delegate = new HashMap<K, Deque<V>>();
    source.forEach((key, value) -> {
      var l = new ArrayDeque<V>();
      l.push(value);
      delegate.put(key, l);
    });
    return new HashPriorityMap<>(delegate);
  }

  @Contract(pure = true)
  @Override
  public ImmutablePriorityMap<K, V> immutableCopy() {
    var delegate = new HashMap<K, ImmutableArrayDeque<V>>();
    this.getDelegate().forEach((key, value) -> delegate.put(key, new ImmutableArrayDeque<>(value)));
    return new ImmutablePriorityMap<>(ImmutableMap.copyOf(delegate));
  }
  @Override
  protected Map<K, Deque<V>> getDelegate() {
    return delegate;
  }

  @Override
  protected Deque<V> newDeque() {
    return new ArrayDeque<>();
  }
}
