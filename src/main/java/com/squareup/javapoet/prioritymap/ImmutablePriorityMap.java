package com.squareup.javapoet.prioritymap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import org.jetbrains.annotations.Contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("Immutable") // AbstractMap has interior mutability as an optimisation
@Immutable(containerOf = {"K", "V"})
public class ImmutablePriorityMap<K, V> extends AbstractPriorityMap<K, V, ImmutableArrayDeque<V>> {
  private final ImmutableMap<K, ImmutableArrayDeque<V>> delegate;

  @Contract(pure = true)
  public ImmutablePriorityMap() {
    delegate = ImmutableMap.of();
  }

  @Contract(pure = true)
  protected ImmutablePriorityMap(ImmutableMap<K, ImmutableArrayDeque<V>> delegate) {
    this.delegate = delegate;
  }

  @Contract(pure = true)
  public ImmutablePriorityMap(ImmutablePriorityMap<K, V> toCopy) {
    var delegate = new HashMap<K, ImmutableArrayDeque<V>>();
    toCopy.getDelegate().forEach((key, value) -> delegate.put(key, new ImmutableArrayDeque<>(value)));
    this.delegate = ImmutableMap.copyOf(delegate);
  }

  @Contract(pure = true)
  public static <K, V> ImmutablePriorityMap<K, V> from(Map<K, V> source) {
    var delegate = new HashMap<K, ImmutableArrayDeque<V>>();
    source.forEach((key, value) -> {
      delegate.put(key, new ImmutableArrayDeque<>(List.of(value)));
    });
    return new ImmutablePriorityMap<>(ImmutableMap.copyOf(delegate));
  }

  @Contract(pure = true)
  @Override
  public ImmutablePriorityMap<K, V> immutableCopy() {
    var delegate = new HashMap<K, ImmutableArrayDeque<V>>();
    this.getDelegate().forEach((key, value) -> delegate.put(key, new ImmutableArrayDeque<>(value)));
    return new ImmutablePriorityMap<>(ImmutableMap.copyOf(delegate));
  }
  @Override
  protected Map<K, ImmutableArrayDeque<V>> getDelegate() {
    return delegate;
  }

  @Override
  protected ImmutableArrayDeque<V> newDeque() {
    return new ImmutableArrayDeque<>(ImmutableList.of());
  }
}
