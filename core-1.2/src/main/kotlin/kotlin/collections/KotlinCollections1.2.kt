// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("unused", "MagicNumber", "INVISIBLE_MEMBER")

package kotlin.collections

inline fun <K, V> Iterable<K>.associateWith(valueSelector: (K) -> V): Map<K, V> {
    val result = LinkedHashMap<K, V>(mapCapacity(if (this is Collection<*>) size else 10).coerceAtLeast(16))
    return associateWithTo(result, valueSelector)
}

inline fun <K, V, M : MutableMap<in K, in V>> Iterable<K>.associateWithTo(destination: M, valueSelector: (K) -> V): M {
    for (element in this) {
        destination.put(element, valueSelector(element))
    }
    return destination
}

inline fun <T> Iterable<T>.sumOf(selector: (T) -> Int): Int {
    var sum = 0
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

inline fun <T, R : Comparable<R>> Iterable<T>.maxOf(selector: (T) -> R): R {
    val iterator = iterator()
    if (!iterator.hasNext()) throw NoSuchElementException()
    var maxValue = selector(iterator.next())
    while (iterator.hasNext()) {
        val v = selector(iterator.next())
        if (maxValue < v) {
            maxValue = v
        }
    }
    return maxValue
}
