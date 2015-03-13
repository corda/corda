/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef UTIL_H
#define UTIL_H

#include "avian/machine.h"
#include "avian/zone.h"

namespace vm {

GcTriple* hashMapFindNode(Thread* t,
                          GcHashMap* map,
                          object key,
                          uint32_t (*hash)(Thread*, object),
                          bool (*equal)(Thread*, object, object));

inline object hashMapFind(Thread* t,
                          GcHashMap* map,
                          object key,
                          uint32_t (*hash)(Thread*, object),
                          bool (*equal)(Thread*, object, object))
{
  GcTriple* n = hashMapFindNode(t, map, key, hash, equal);
  return (n ? n->second() : 0);
}

void hashMapResize(Thread* t,
                   GcHashMap* map,
                   uint32_t (*hash)(Thread*, object),
                   unsigned size);

void hashMapInsert(Thread* t,
                   GcHashMap* map,
                   object key,
                   object value,
                   uint32_t (*hash)(Thread*, object));

inline bool hashMapInsertOrReplace(Thread* t,
                                   GcHashMap* map,
                                   object key,
                                   object value,
                                   uint32_t (*hash)(Thread*, object),
                                   bool (*equal)(Thread*, object, object))
{
  GcTriple* n = hashMapFindNode(t, map, key, hash, equal);
  if (n == 0) {
    hashMapInsert(t, map, key, value, hash);
    return true;
  } else {
    n->setSecond(t, value);
    return false;
  }
}

inline bool hashMapInsertMaybe(Thread* t,
                               GcHashMap* map,
                               object key,
                               object value,
                               uint32_t (*hash)(Thread*, object),
                               bool (*equal)(Thread*, object, object))
{
  GcTriple* n = hashMapFindNode(t, map, key, hash, equal);
  if (n == 0) {
    hashMapInsert(t, map, key, value, hash);
    return true;
  } else {
    return false;
  }
}

object hashMapRemove(Thread* t,
                     GcHashMap* map,
                     object key,
                     uint32_t (*hash)(Thread*, object),
                     bool (*equal)(Thread*, object, object));

object hashMapIterator(Thread* t, GcHashMap* map);

object hashMapIteratorNext(Thread* t, object it);

void listAppend(Thread* t, GcList* list, object value);

GcVector* vectorAppend(Thread* t, GcVector* vector, object value);

GcArray* growArray(Thread* t, GcArray* array);

object treeQuery(Thread* t,
                 GcTreeNode* tree,
                 intptr_t key,
                 GcTreeNode* sentinal,
                 intptr_t (*compare)(Thread* t, intptr_t key, object b));

GcTreeNode* treeInsert(Thread* t,
                       Zone* zone,
                       GcTreeNode* tree,
                       intptr_t key,
                       object value,
                       GcTreeNode* sentinal,
                       intptr_t (*compare)(Thread* t, intptr_t key, object b));

void treeUpdate(Thread* t,
                GcTreeNode* tree,
                intptr_t key,
                object value,
                GcTreeNode* sentinal,
                intptr_t (*compare)(Thread* t, intptr_t key, object b));

class HashMapIterator : public Thread::Protector {
 public:
  HashMapIterator(Thread* t, GcHashMap* map)
      : Protector(t), map(map), node(0), index(0)
  {
    find();
  }

  void find()
  {
    GcArray* array = map->array();
    if (array) {
      for (unsigned i = index; i < array->length(); ++i) {
        if (array->body()[i]) {
          node = cast<GcTriple>(t, array->body()[i]);
          index = i + 1;
          return;
        }
      }
    }
    node = 0;
  }

  bool hasMore()
  {
    return node != 0;
  }

  GcTriple* next()
  {
    if (node) {
      GcTriple* n = node;
      if (node->third()) {
        node = cast<GcTriple>(t, node->third());
      } else {
        find();
      }
      return n;
    } else {
      return 0;
    }
  }

  virtual void visit(Heap::Visitor* v)
  {
    v->visit(&map);
    v->visit(&node);
  }

  GcHashMap* map;
  GcTriple* node;
  unsigned index;
};

}  // vm

#endif  // UTIL_H
