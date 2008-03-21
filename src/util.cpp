/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "util.h"

using namespace vm;

namespace vm {

object
hashMapFindNode(Thread* t, object map, object key,
                uint32_t (*hash)(Thread*, object),
                bool (*equal)(Thread*, object, object))
{
  bool weak = objectClass(t, map)
    == arrayBody(t, t->m->types, Machine::WeakHashMapType);

  object array = hashMapArray(t, map);
  if (array) {
    unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
    for (object n = arrayBody(t, array, index); n; n = tripleThird(t, n)) {
      object k = tripleFirst(t, n);
      if (weak) {
        k = jreferenceTarget(t, k);
        if (k == 0) {
          continue;
        }
      }

      if (equal(t, key, k)) {
        return n;
      }
    }
  }
  return 0;
}

void
hashMapResize(Thread* t, object map, uint32_t (*hash)(Thread*, object),
              unsigned size)
{
  PROTECT(t, map);

  object newArray = 0;

  if (size) {
    object oldArray = hashMapArray(t, map);
    PROTECT(t, oldArray);

    unsigned newLength = nextPowerOfTwo(size);
    if (oldArray and arrayLength(t, oldArray) == newLength) {
      return;
    }

    newArray = makeArray(t, newLength, true);

    if (oldArray) {
      bool weak = objectClass(t, map)
        == arrayBody(t, t->m->types, Machine::WeakHashMapType);

      for (unsigned i = 0; i < arrayLength(t, oldArray); ++i) {
        object next;
        for (object p = arrayBody(t, oldArray, i); p; p = next) {
          next = tripleThird(t, p);

          object k = tripleFirst(t, p);
          if (weak) {
            k = jreferenceTarget(t, k);
            if (k == 0) {
              continue;
            }
          }

          unsigned index = hash(t, k) & (newLength - 1);

          set(t, p, TripleThird, arrayBody(t, newArray, index));
          set(t, newArray, ArrayBody + (index * BytesPerWord), p);
        }
      }
    }
  }
  
  set(t, map, HashMapArray, newArray);
}

void
hashMapInsert(Thread* t, object map, object key, object value,
               uint32_t (*hash)(Thread*, object))
{
  // note that we reinitialize the array and index variables whenever
  // an allocation (and thus possibly a collection) occurs, in case
  // the array changes due to a table resize.

  PROTECT(t, map);

  uint32_t h = hash(t, key);

  bool weak = objectClass(t, map)
    == arrayBody(t, t->m->types, Machine::WeakHashMapType);

  object array = hashMapArray(t, map);

  ++ hashMapSize(t, map);

  if (array == 0 or hashMapSize(t, map) >= arrayLength(t, array) * 2) { 
    PROTECT(t, key);
    PROTECT(t, value);

    hashMapResize(t, map, hash, array ? arrayLength(t, array) * 2 : 16);

    array = hashMapArray(t, map);
  }

  object k = key;

  if (weak) {
    PROTECT(t, key);
    PROTECT(t, value);

    object r = makeWeakReference(t, 0, 0, 0, 0);
    jreferenceTarget(t, r) = key;
    jreferenceVmNext(t, r) = t->m->weakReferences;
    k = t->m->weakReferences = r;

    array = hashMapArray(t, map);
  }

  unsigned index = h & (arrayLength(t, array) - 1);

  object n = makeTriple(t, k, value, arrayBody(t, array, index));

  array = hashMapArray(t, map);
  index = h & (arrayLength(t, array) - 1);

  set(t, array, ArrayBody + (index * BytesPerWord), n);
}

object
hashMapRemoveNode(Thread* t, object map, unsigned index, object p, object n)
{
  if (p) {
    set(t, p, TripleThird, tripleThird(t, n));
  } else {
    set(t, hashMapArray(t, map), ArrayBody + (index * BytesPerWord),
        tripleThird(t, n));
  }
  -- hashMapSize(t, map);
  return n;
}

object
hashMapRemove(Thread* t, object map, object key,
              uint32_t (*hash)(Thread*, object),
              bool (*equal)(Thread*, object, object))
{
  bool weak = objectClass(t, map)
    == arrayBody(t, t->m->types, Machine::WeakHashMapType);

  object array = hashMapArray(t, map);
  object o = 0;
  if (array) {
    unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
    object p = 0;
    for (object n = arrayBody(t, array, index); n;) {
      object k = tripleFirst(t, n);
      if (weak) {
        k = jreferenceTarget(t, k);
        if (k == 0) {
          n = tripleThird(t, hashMapRemoveNode(t, map, index, p, n));
          continue;
        }
      }

      if (equal(t, key, k)) {
        o = tripleSecond(t, hashMapRemoveNode(t, map, index, p, n));
        break;
      } else {
        p = n;
        n = tripleThird(t, n);
      }
    }

    if (hashMapSize(t, map) <= arrayLength(t, array) / 3) { 
      PROTECT(t, o);
      hashMapResize(t, map, hash, arrayLength(t, array) / 2);
    }
  }

  return o;
}

object
hashMapIterator(Thread* t, object map)
{
  object array = hashMapArray(t, map);
  if (array) {
    for (unsigned i = 0; i < arrayLength(t, array); ++i) {
      if (arrayBody(t, array, i)) {
        return makeHashMapIterator(t, map, arrayBody(t, array, i), i + 1);
      }
    }
  }
  return 0;
}

object
hashMapIteratorNext(Thread* t, object it)
{
  object map = hashMapIteratorMap(t, it);
  object node = hashMapIteratorNode(t, it);
  unsigned index = hashMapIteratorIndex(t, it);

  if (tripleThird(t, node)) {
    return makeHashMapIterator(t, map, tripleThird(t, node), index);
  } else {
    object array = hashMapArray(t, map);
    for (unsigned i = index; i < arrayLength(t, array); ++i) {
      if (arrayBody(t, array, i)) {
        return makeHashMapIterator(t, map, arrayBody(t, array, i), i + 1);
      }
    }
    return 0;
  }  
}

void
listAppend(Thread* t, object list, object value)
{
  PROTECT(t, list);

  ++ listSize(t, list);
  
  object p = makePair(t, value, 0);
  if (listFront(t, list)) {
    set(t, listRear(t, list), PairSecond, p);
  } else {
    set(t, list, ListFront, p);
  }
  set(t, list, ListRear, p);
}

object
vectorAppend(Thread* t, object vector, object value)
{
  if (vectorLength(t, vector) == vectorSize(t, vector)) {
    PROTECT(t, vector);
    PROTECT(t, value);

    object newVector = makeVector
      (t, vectorSize(t, vector), max(16, vectorSize(t, vector) * 2), false);

    if (vectorSize(t, vector)) {
      memcpy(&vectorBody(t, newVector, 0),
             &vectorBody(t, vector, 0),
             vectorSize(t, vector) * BytesPerWord);
    }

    memset(&vectorBody(t, newVector, vectorSize(t, vector) + 1),
           0,
           (vectorLength(t, newVector) - vectorSize(t, vector) - 1)
           * BytesPerWord);

    vector = newVector;
  }

  set(t, vector, VectorBody + (vectorSize(t, vector) * BytesPerWord), value);
  ++ vectorSize(t, vector);
  return vector;
}

} // namespace vm
