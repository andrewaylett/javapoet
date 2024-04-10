package com.squareup.javapoet.notation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;

public class PriorityMap<K, V> extends AbstractMap<K, V> {
  private final Map<K, Deque<V>> delegate;

  public PriorityMap() {
    delegate = new HashMap<>();
  }

  public PriorityMap(PriorityMap<K, V> toCopy) {
    delegate = new HashMap<>(toCopy.delegate);
  }

  public static <K, V> PriorityMap<K, V> from(Map<K, V> source) {
    var priorityMap = new PriorityMap<K, V>();
    source.forEach((key, value) -> {
      var l = new ArrayDeque<V>();
      l.push(value);
      priorityMap.delegate.put(key, l);
    });
    return priorityMap;
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean containsValue(Object value) {
    return delegate
        .values()
        .stream()
        .anyMatch(v -> Objects.equals(v.peek(), value));
  }

  @Override
  public boolean containsKey(Object key) {
    return delegate.containsKey(key);
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public @Nullable V get(Object key) {
    return delegate.getOrDefault(key, new ArrayDeque<>()).peek();
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Nonnull
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return new PriorityEntrySet<>(delegate);
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public boolean remove(Object key, Object value) {
    var values = delegate.get(key);
    if (values == null) {
      return false;
    }
    try {
      return values.removeAll(List.of(value));
    } finally {
      if (values.isEmpty()) {
        delegate.remove(key);
      }
    }
  }

  @Override
  public V put(K key, V value) {
    return delegate.compute(key, (k, old) -> {
      if (old == null) {
        old = new ArrayDeque<>();
      }
      old.push(value);
      return old;
    }).peek();
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public @Nullable V remove(Object key) {
    var deque = delegate.get(key);
    if (deque == null) {
      return null;
    }
    try {
      return deque.pop();
    } finally {
      if (deque.isEmpty()) {
        delegate.remove(key);
      }
    }
  }

  public static class PriorityEntrySet<K, V>
      extends AbstractSet<Map.Entry<K, V>> {
    private final Map<K, Deque<V>> delegate;

    public PriorityEntrySet(Map<K, Deque<V>> delegate) {
      this.delegate = delegate;
    }

    @Override
    public @NotNull Iterator<Map.Entry<K, V>> iterator() {
      return new AbstractEntrySetIterator<>(delegate.entrySet().iterator());
    }

    @Override
    public Spliterator<Entry<K, V>> spliterator() {
      return super.spliterator();
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public boolean add(Entry<K, V> kvEntry) {
      delegate.compute(kvEntry.getKey(), (k, old) -> {
        if (old == null) {
          old = new ArrayDeque<>();
        }
        old.push(kvEntry.getValue());
        return old;
      });
      return true;
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public boolean remove(Object o) {
      if (o instanceof Entry<?, ?> e) {
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

  public static class AbstractEntrySetIterator<K, V>
      implements Iterator<Map.Entry<K, V>> {
    private final Iterator<Entry<K, Deque<V>>> delegate;
    private transient Entry<K, Deque<V>> current;

    public AbstractEntrySetIterator(Iterator<Entry<K, Deque<V>>> iterator) {
      this.delegate = iterator;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public Entry<K, V> next() {
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

  @SuppressWarnings("DataFlowIssue")
  public static class PriorityEntry<K, V> implements Map.Entry<K, V> {
    private final Entry<K, Deque<V>> delegate;

    public PriorityEntry(Entry<K, Deque<V>> current) {
      this.delegate = current;
    }

    @Override
    public K getKey() {
      return delegate.getKey();
    }

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
}
