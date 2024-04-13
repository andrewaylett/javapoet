package com.squareup.javapoet.prioritymap;

import java.util.Map;

/**
 * A priority map holds a stack of values for each key.
 * <p>
 * Removing a key from the map pops the stack for that key, removing a value
 * from the map removes every instance of that value -- and any keys that had
 * been pointing at the removed value will then point to the next value in the
 * stack.
 * @param <K> Map key
 * @param <V> Map value
 */
public interface PriorityMap<K, V> extends Map<K, V> {
  /**
   * Make an immutable copy of this priority map.
   * <p>
   *   If the map is already immutable, the copy is a reference copy.  Otherwise
   *   we build new immutable components matching the mutable values.
   * </p>
   * <p>
   *   If the parameterised typs are known by ErrorProne to be immutable, the
   *   new PriorityMap will also be known to be immutable.
   * </p>
   * @return A map with the same content as this map, but immutable.
   */
  ImmutablePriorityMap<K, V> immutableCopy();
}
