package com.squareup.javapoet.notation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PriorityMap<K, V> extends AbstractMap<K, V> {
  private final Map<K, Deque<V>> delegate;

  public PriorityMap() {
    delegate = new HashMap<>();
  }

  private PriorityMap(Map<K, Deque<V>> delegate) {
    this.delegate = delegate;
  }

  public PriorityMap(PriorityMap<K, V> toCopy) {
    delegate = new HashMap<>();
    toCopy.delegate.forEach((key, value) -> delegate.put(key, new ArrayDeque<>(value)));
  }

  public static <K, V> PriorityMap<K, V> from(Map<K, V> source) {
    var delegate = new HashMap<K, Deque<V>>();
    source.forEach((key, value) -> {
      var l = new ArrayDeque<V>();
      l.push(value);
      delegate.put(key, l);
    });
    return new PriorityMap<>(delegate);
  }

  public PriorityMap<K, V> immutableCopy() {
    var delegate = new HashMap<K, Deque<V>>();
    this.delegate.forEach((key, value) -> delegate.put(key, new ReadOnlyArrayDeque<>(value)));
    return new PriorityMap<>(Map.copyOf(delegate));
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

  private static class ReadOnlyArrayDeque<T> implements Deque<T> {
    private final List<T> delegate;
    public ReadOnlyArrayDeque(Collection<? extends T> collection) {
      delegate = List.copyOf(collection);
    }

    @Override
    public void addFirst(@Nonnull T t) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addLast(@Nonnull T t) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean offerFirst(T t) {
      return false;
    }

    @Override
    public boolean offerLast(T t) {
      return false;
    }

    @Override
    public T removeFirst() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T removeLast() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void push(T t) {
      throw new UnsupportedOperationException();
    }

    @Override
    public T pop() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
      return new HashSet<>(delegate).containsAll(c);
    }

    @Override
    public boolean contains(Object o) {
      return delegate.contains(o);
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
    public @NotNull Iterator<T> iterator() {
      return delegate.iterator();
    }

    @Nonnull
    @Override
    public Object[] toArray() {
      return delegate.toArray();
    }

    @Nonnull
    @Override
    public <T1> T1[] toArray(@Nonnull T1[] a) {
      return delegate.toArray(a);
    }

    @Nonnull
    @Override
    public Iterator<T> descendingIterator() {
      return new Iterator<>() {
        private int idx = delegate.size() - 1;
        @Override
        public boolean hasNext() {
          return idx >= 0;
        }

        @Override
        public T next() {
          return delegate.get(idx--);
        }
      };
    }

    @Override
    public T pollFirst() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T pollLast() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T getFirst() {
      return delegate.get(0);
    }

    @Override
    public T getLast() {
      return delegate.get(delegate.size() - 1);
    }

    @Override
    public @Nullable T peekFirst() {
      return delegate.isEmpty() ? null : delegate.get(0);
    }

    @Override
    public @Nullable T peekLast() {
      return delegate.isEmpty() ? null : delegate.get(delegate.size() - 1);
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T t) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(T t) {
      throw new UnsupportedOperationException();
    }

    @Override
    public T remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T poll() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T element() {
      return getFirst();
    }

    @Override
    public @Nullable T peek() {
      return peekFirst();
    }
  }
}
