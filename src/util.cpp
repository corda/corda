#include "util.h"

using namespace vm;

namespace {

object
clone(Thread* t, object o)
{
  object class_ = objectClass(t, o);
  unsigned size = baseSize(t, o, class_) * BytesPerWord;

  object clone = make(t, class_);
  memcpy(reinterpret_cast<void**>(clone) + 1,
         reinterpret_cast<void**>(o) + 1,
         size - BytesPerWord);

  return clone;
}

object
treeFind(Thread* t, object old, object node, object sentinal)
{
  PROTECT(t, old);
  PROTECT(t, node);
  PROTECT(t, sentinal);

  object newRoot = clone(t, old);
  PROTECT(t, newRoot);

  object new_ = newRoot;
  PROTECT(t, new_);

  object ancestors = 0;
  PROTECT(t, ancestors);

  while (old != sentinal) {
    ancestors = makePair(t, new_, ancestors);

    intptr_t difference = treeNodeKey(t, node) - treeNodeKey(t, old);
    if (difference < 0) {
      old = treeNodeLeft(t, old);
      object n = clone(t, old);
      set(t, new_, TreeNodeLeft, n);
      new_ = n;
    } else if (difference > 0) {
      old = treeNodeRight(t, old);
      object n = clone(t, old);
      set(t, new_, TreeNodeRight, n);
      new_ = n;
    } else {
      return makeTreePath(t, false, new_, newRoot, pairSecond(t, ancestors));
    }
  }

  object class_ = objectClass(t, node);
  unsigned size = baseSize(t, node, class_) * BytesPerWord;
  unsigned treeNodeSize = classFixedSize
    (t, arrayBody(t, t->m->types, Machine::TreeNodeType)) * BytesPerWord;
  unsigned diff = treeNodeSize - size;
  
  if (diff) {
    memcpy(reinterpret_cast<uint8_t*>(new_) + treeNodeSize,
           reinterpret_cast<uint8_t*>(node) + treeNodeSize,
           diff);
  }

  return makeTreePath(t, true, new_, newRoot, ancestors);
}

object
leftRotate(Thread* t, object n)
{
  object child = clone(t, treeNodeRight(t, n));
  set(t, n, TreeNodeRight, treeNodeLeft(t, child));
  set(t, child, TreeNodeLeft, n);
  return child;
}

object
rightRotate(Thread* t, object n)
{
  object child = clone(t, treeNodeLeft(t, n));
  set(t, n, TreeNodeLeft, treeNodeRight(t, child));
  set(t, child, TreeNodeRight, n);
  return child;
}

object
treeAdd(Thread* t, object path)
{
  object new_ = treePathNode(t, path);
  PROTECT(t, new_);

  object newRoot = treePathRoot(t, path);
  PROTECT(t, newRoot);

  object ancestors = treePathAncestors(t, path);
  PROTECT(t, ancestors);

  // rebalance
  treeNodeRed(t, new_) = true;
  while (ancestors != 0 and treeNodeRed(t, pairFirst(t, ancestors))) {
    if (pairFirst(t, ancestors)
        == treeNodeLeft(t, pairFirst(t, pairSecond(t, ancestors))))
    {
      if (treeNodeRed
          (t, treeNodeRight(t, pairFirst(t, pairSecond(t, ancestors)))))
      {
        treeNodeRed(t, pairFirst(t, ancestors)) = true;

        object n = clone
          (t, treeNodeRight(t, pairFirst(t, pairSecond(t, ancestors))));

        set(t, pairFirst(t, pairSecond(t, ancestors)), TreeNodeRight, n);

        treeNodeRed
          (t, treeNodeRight
           (t, pairFirst(t, pairSecond(t, ancestors)))) = false;

        treeNodeRed(t, pairFirst(t, pairSecond(t, ancestors))) = false;

        new_ = pairFirst(t, pairSecond(t, ancestors));
        ancestors = pairSecond(t, pairSecond(t, ancestors));
      } else {
        if (new_ == treeNodeRight(t, pairFirst(t, ancestors))) {
          new_ = pairFirst(t, ancestors);
          ancestors = pairSecond(t, ancestors);

          object n = leftRotate(t, new_);

          if (new_ == treeNodeRight(t, pairFirst(t, ancestors))) {
            set(t, pairFirst(t, ancestors), TreeNodeRight, n);
          } else {
            set(t, pairFirst(t, ancestors), TreeNodeLeft, n);
          }
          ancestors = makePair(t, n, ancestors);
        }
        treeNodeRed(t, pairFirst(t, ancestors)) = false;
        treeNodeRed(t, pairFirst(t, pairSecond(t, ancestors))) = true;

        object n = rightRotate(t, pairFirst(t, pairSecond(t, ancestors)));
        if (pairSecond(t, pairSecond(t, ancestors)) == 0) {
          newRoot = n;
        } else if (treeNodeRight
                   (t, pairFirst(t, pairSecond(t, pairSecond(t, ancestors))))
                   == pairFirst(t, pairSecond(t, ancestors)))
        {
          set(t, pairFirst(t, pairSecond(t, pairSecond(t, ancestors))),
              TreeNodeRight, n);
        } else {
          set(t, pairFirst(t, pairSecond(t, pairSecond(t, ancestors))),
              TreeNodeLeft, n);
        }
        // done
      }
    } else { // this is just the reverse of the code above (right and
             // left swapped):
      if (treeNodeRed
          (t, treeNodeLeft(t, pairFirst(t, pairSecond(t, ancestors)))))
      {
        treeNodeRed(t, pairFirst(t, ancestors)) = true;

        object n = clone
          (t, treeNodeLeft(t, pairFirst(t, pairSecond(t, ancestors))));

        set(t, pairFirst(t, pairSecond(t, ancestors)), TreeNodeLeft, n);

        treeNodeRed
          (t, treeNodeLeft
           (t, pairFirst(t, pairSecond(t, ancestors)))) = false;

        treeNodeRed(t, pairFirst(t, pairSecond(t, ancestors))) = false;

        new_ = pairFirst(t, pairSecond(t, ancestors));
        ancestors = pairSecond(t, pairSecond(t, ancestors));
      } else {
        if (new_ == treeNodeLeft(t, pairFirst(t, ancestors))) {
          new_ = pairFirst(t, ancestors);
          ancestors = pairSecond(t, ancestors);

          object n = rightRotate(t, new_);

          if (new_ == treeNodeLeft(t, pairFirst(t, ancestors))) {
            set(t, pairFirst(t, ancestors), TreeNodeLeft, n);
          } else {
            set(t, pairFirst(t, ancestors), TreeNodeRight, n);
          }
          ancestors = makePair(t, n, ancestors);
        }
        treeNodeRed(t, pairFirst(t, ancestors)) = false;
        treeNodeRed(t, pairFirst(t, pairSecond(t, ancestors))) = true;

        object n = leftRotate(t, pairFirst(t, pairSecond(t, ancestors)));
        if (pairSecond(t, pairSecond(t, ancestors)) == 0) {
          newRoot = n;
        } else if (treeNodeLeft
                   (t, pairFirst(t, pairSecond(t, pairSecond(t, ancestors))))
                   == pairFirst(t, pairSecond(t, ancestors)))
        {
          set(t, pairFirst(t, pairSecond(t, pairSecond(t, ancestors))),
              TreeNodeLeft, n);
        } else {
          set(t, pairFirst(t, pairSecond(t, pairSecond(t, ancestors))),
              TreeNodeRight, n);
        }
        // done
      }
    }
  }

  treeNodeRed(t, newRoot) = false;

  return newRoot;
}

} // namespace

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
  bool weak = objectClass(t, map)
    == arrayBody(t, t->m->types, Machine::WeakHashMapType);

  object array = hashMapArray(t, map);
  PROTECT(t, array);

  ++ hashMapSize(t, map);

  if (array == 0 or hashMapSize(t, map) >= arrayLength(t, array) * 2) { 
    PROTECT(t, map);
    PROTECT(t, key);
    PROTECT(t, value);

    hashMapResize(t, map, hash, array ? arrayLength(t, array) * 2 : 16);
    array = hashMapArray(t, map);
  }

  unsigned index = hash(t, key) & (arrayLength(t, array) - 1);

  if (weak) {
    PROTECT(t, key);
    PROTECT(t, value);

    object r = makeWeakReference(t, 0, 0, 0, 0);
    jreferenceTarget(t, r) = key;
    jreferenceVmNext(t, r) = t->m->weakReferences;
    key = t->m->weakReferences = r;
  }

  object n = makeTriple(t, key, value, arrayBody(t, array, index));

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

object
treeQuery(Thread* t, object tree, intptr_t key, object sentinal)
{
  object node = tree;
  while (node != sentinal) {
    intptr_t difference = key - treeNodeKey(t, node);
    if (difference < 0) {
      node = treeNodeLeft(t, node);
    } else if (difference > 0) {
      node = treeNodeRight(t, node);
    } else {
      return node;
    }
  }

  return 0;
}

object
treeInsert(Thread* t, object tree, object node, object sentinal)
{
  object path = treeFind(t, tree, node, sentinal);
  if (treePathFresh(t, path)) {
    return treeAdd(t, path);
  } else {
    return tree;
  }  
}

} // namespace vm
