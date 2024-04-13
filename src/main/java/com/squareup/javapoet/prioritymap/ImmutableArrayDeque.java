package com.squareup.javapoet.prioritymap;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;

@Immutable(containerOf = "T")
public class ImmutableArrayDeque<T> implements Deque<T> {
  private final ImmutableList<T> delegate;

  @Contract(pure = true)
  public ImmutableArrayDeque(Collection<? extends T> collection) {
    delegate = ImmutableList.copyOf(collection);
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public void addFirst(@Nonnull T t) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public void addLast(@Nonnull T t) {
    throw new UnsupportedOperationException();
  }

  @Contract(pure = true)
  @Override
  public boolean offerFirst(T t) {
    return false;
  }

  @Contract(pure = true)
  @Override
  public boolean offerLast(T t) {
    return false;
  }

  @Contract(value = " -> fail", pure = true)
  @Override
  public T removeFirst() {
    throw new UnsupportedOperationException();
  }

  @Contract(value = " -> fail", pure = true)
  @Override
  public T removeLast() {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public boolean addAll(Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public boolean removeAll(@Nonnull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public boolean retainAll(@Nonnull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = " -> fail", pure = true)
  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public void push(T t) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = " -> fail", pure = true)
  @Override
  public T pop() {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Contract(pure = true)
  @Override
  public boolean containsAll(@Nonnull Collection<?> c) {
    return new HashSet<>(delegate).containsAll(c);
  }

  @Contract(pure = true)
  @Override
  public boolean contains(Object o) {
    return delegate.contains(o);
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

  @Contract(pure = true)
  @Override
  public @NotNull Iterator<T> iterator() {
    return delegate.iterator();
  }

  @Contract(pure = true)
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

  @Contract(pure = true)
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

  @Contract("-> fail")
  @Override
  public T pollFirst() {
    throw new UnsupportedOperationException();
  }

  @Contract("-> fail")
  @Override
  public T pollLast() {
    throw new UnsupportedOperationException();
  }

  @Contract(pure = true)
  @Override
  public T getFirst() {
    return delegate.get(0);
  }

  @Contract(pure = true)
  @Override
  public T getLast() {
    return delegate.get(delegate.size() - 1);
  }

  @Contract(pure = true)
  @Override
  public @Nullable T peekFirst() {
    return delegate.isEmpty() ? null : delegate.get(0);
  }

  @Contract(pure = true)
  @Override
  public @Nullable T peekLast() {
    return delegate.isEmpty() ? null : delegate.get(delegate.size() - 1);
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public boolean removeFirstOccurrence(Object o) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public boolean removeLastOccurrence(Object o) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public boolean add(T t) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = "_ -> fail", pure = true)
  @Override
  public boolean offer(T t) {
    throw new UnsupportedOperationException();
  }

  @Contract(value = " -> fail", pure = true)
  @Override
  public T remove() {
    throw new UnsupportedOperationException();
  }

  @Contract(value = " -> fail", pure = true)
  @Override
  public T poll() {
    throw new UnsupportedOperationException();
  }

  @Contract(pure = true)
  @Override
  public T element() {
    return getFirst();
  }

  @Contract(pure = true)
  @Override
  public @Nullable T peek() {
    return peekFirst();
  }
}
