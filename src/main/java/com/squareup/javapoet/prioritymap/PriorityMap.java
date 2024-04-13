package com.squareup.javapoet.prioritymap;

import java.util.Map;

public interface PriorityMap<K, V> extends Map<K, V> {
  ImmutablePriorityMap<K, V> immutableCopy();
}
