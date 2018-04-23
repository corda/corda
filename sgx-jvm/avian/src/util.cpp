/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/util.h"
#include <avian/util/list.h>

using namespace vm;

namespace {

class TreeContext {
 public:
  class MyProtector : public Thread::Protector {
   public:
    MyProtector(Thread* thread, TreeContext* context)
        : Protector(thread), context(context)
    {
    }

    virtual void visit(Heap::Visitor* v)
    {
      v->visit(&(context->root));
      v->visit(&(context->node));

      for (List<GcTreeNode*>* p = context->ancestors; p; p = p->next) {
        v->visit(&(p->item));
      }
    }

    TreeContext* context;
  };

  TreeContext(Thread* thread, Zone* zone)
      : zone(zone),
        root(0),
        node(0),
        ancestors(0),
        protector(thread, this),
        fresh(false)
  {
  }

  Zone* zone;
  GcTreeNode* root;
  GcTreeNode* node;
  List<GcTreeNode*>* ancestors;
  MyProtector protector;
  bool fresh;
};

List<GcTreeNode*>* path(TreeContext* c,
                        GcTreeNode* node,
                        List<GcTreeNode*>* next)
{
  return new (c->zone) List<GcTreeNode*>(node, next);
}

inline object getTreeNodeValue(Thread*, GcTreeNode* n)
{
  return reinterpret_cast<object>(alias(n, TreeNodeValue) & PointerMask);
}

inline void setTreeNodeValue(Thread* t, GcTreeNode* n, object value)
{
  intptr_t red = alias(n, TreeNodeValue) & (~PointerMask);

  n->setValue(t, value);

  alias(n, TreeNodeValue) |= red;
}

inline bool treeNodeRed(Thread*, GcTreeNode* n)
{
  return (alias(n, TreeNodeValue) & (~PointerMask)) == 1;
}

inline void setTreeNodeRed(Thread*, GcTreeNode* n, bool red)
{
  if (red) {
    alias(n, TreeNodeValue) |= 1;
  } else {
    alias(n, TreeNodeValue) &= PointerMask;
  }
}

inline GcTreeNode* cloneTreeNode(Thread* t, GcTreeNode* n)
{
  PROTECT(t, n);

  GcTreeNode* newNode
      = makeTreeNode(t, getTreeNodeValue(t, n), n->left(), n->right());
  setTreeNodeRed(t, newNode, treeNodeRed(t, n));
  return newNode;
}

GcTreeNode* treeFind(Thread* t,
                     GcTreeNode* tree,
                     intptr_t key,
                     GcTreeNode* sentinal,
                     intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  GcTreeNode* node = tree;
  while (node != sentinal) {
    intptr_t difference = compare(t, key, getTreeNodeValue(t, node));
    if (difference < 0) {
      node = node->left();
    } else if (difference > 0) {
      node = node->right();
    } else {
      return node;
    }
  }

  return 0;
}

void treeFind(Thread* t,
              TreeContext* c,
              GcTreeNode* old,
              intptr_t key,
              GcTreeNode* node,
              GcTreeNode* sentinal,
              intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  PROTECT(t, old);
  PROTECT(t, node);
  PROTECT(t, sentinal);

  GcTreeNode* newRoot = cloneTreeNode(t, old);
  PROTECT(t, newRoot);

  GcTreeNode* new_ = newRoot;
  PROTECT(t, new_);

  int count = 0;
  while (old != sentinal) {
    c->ancestors = path(c, new_, c->ancestors);

    intptr_t difference = compare(t, key, getTreeNodeValue(t, old));

    if (difference < 0) {
      old = old->left();
      GcTreeNode* n = cloneTreeNode(t, old);
      new_->setLeft(t, n);
      new_ = n;
    } else if (difference > 0) {
      old = old->right();
      GcTreeNode* n = cloneTreeNode(t, old);
      new_->setRight(t, n);
      new_ = n;
    } else {
      c->fresh = false;
      c->root = newRoot;
      c->node = new_;
      c->ancestors = c->ancestors->next;
      return;
    }

    if (++count > 100) {
      // if we've gone this deep, we probably have an unbalanced tree,
      // which should only happen if there's a serious bug somewhere
      // in our insertion process
      abort(t);
    }
  }

  setTreeNodeValue(t, new_, getTreeNodeValue(t, node));

  c->fresh = true;
  c->root = newRoot;
  c->node = new_;
  c->ancestors = c->ancestors;
}

GcTreeNode* leftRotate(Thread* t, GcTreeNode* n)
{
  PROTECT(t, n);

  GcTreeNode* child = cloneTreeNode(t, n->right());
  n->setRight(t, child->left());
  child->setLeft(t, n);
  return child;
}

GcTreeNode* rightRotate(Thread* t, GcTreeNode* n)
{
  PROTECT(t, n);

  GcTreeNode* child = cloneTreeNode(t, n->left());
  n->setLeft(t, child->right());
  child->setRight(t, n);
  return child;
}

GcTreeNode* treeAdd(Thread* t, TreeContext* c)
{
  GcTreeNode* new_ = c->node;
  PROTECT(t, new_);

  GcTreeNode* newRoot = c->root;
  PROTECT(t, newRoot);

  // rebalance
  setTreeNodeRed(t, new_, true);
  while (c->ancestors != 0 and treeNodeRed(t, c->ancestors->item)) {
    if (c->ancestors->item == c->ancestors->next->item->left()) {
      if (treeNodeRed(t, c->ancestors->next->item->right())) {
        setTreeNodeRed(t, c->ancestors->item, false);

        GcTreeNode* n = cloneTreeNode(t, c->ancestors->next->item->right());

        c->ancestors->next->item->setRight(t, n);

        setTreeNodeRed(t, c->ancestors->next->item->right(), false);

        setTreeNodeRed(t, c->ancestors->next->item, true);

        new_ = c->ancestors->next->item;
        c->ancestors = c->ancestors->next->next;
      } else {
        if (new_ == c->ancestors->item->right()) {
          new_ = c->ancestors->item;
          c->ancestors = c->ancestors->next;

          GcTreeNode* n = leftRotate(t, new_);

          if (new_ == c->ancestors->item->right()) {
            c->ancestors->item->setRight(t, n);
          } else {
            c->ancestors->item->setLeft(t, n);
          }
          c->ancestors = path(c, n, c->ancestors);
        }
        setTreeNodeRed(t, c->ancestors->item, false);
        setTreeNodeRed(t, c->ancestors->next->item, true);

        GcTreeNode* n = rightRotate(t, c->ancestors->next->item);
        if (c->ancestors->next->next == 0) {
          newRoot = n;
        } else if (c->ancestors->next->next->item->right()
                   == c->ancestors->next->item) {
          c->ancestors->next->next->item->setRight(t, n);
        } else {
          c->ancestors->next->next->item->setLeft(t, n);
        }
        // done
      }
    } else {  // this is just the reverse of the code above (right and
              // left swapped):
      if (treeNodeRed(t, c->ancestors->next->item->left())) {
        setTreeNodeRed(t, c->ancestors->item, false);

        GcTreeNode* n = cloneTreeNode(t, c->ancestors->next->item->left());

        c->ancestors->next->item->setLeft(t, n);

        setTreeNodeRed(t, c->ancestors->next->item->left(), false);

        setTreeNodeRed(t, c->ancestors->next->item, true);

        new_ = c->ancestors->next->item;
        c->ancestors = c->ancestors->next->next;
      } else {
        if (new_ == c->ancestors->item->left()) {
          new_ = c->ancestors->item;
          c->ancestors = c->ancestors->next;

          GcTreeNode* n = rightRotate(t, new_);

          if (new_ == c->ancestors->item->left()) {
            c->ancestors->item->setLeft(t, n);
          } else {
            c->ancestors->item->setRight(t, n);
          }
          c->ancestors = path(c, n, c->ancestors);
        }
        setTreeNodeRed(t, c->ancestors->item, false);
        setTreeNodeRed(t, c->ancestors->next->item, true);

        GcTreeNode* n = leftRotate(t, c->ancestors->next->item);
        if (c->ancestors->next->next == 0) {
          newRoot = n;
        } else if (c->ancestors->next->next->item->left()
                   == c->ancestors->next->item) {
          c->ancestors->next->next->item->setLeft(t, n);
        } else {
          c->ancestors->next->next->item->setRight(t, n);
        }
        // done
      }
    }
  }

  setTreeNodeRed(t, newRoot, false);

  return newRoot;
}

}  // namespace

namespace vm {

GcTriple* hashMapFindNode(Thread* t,
                          GcHashMap* map,
                          object key,
                          uint32_t (*hash)(Thread*, object),
                          bool (*equal)(Thread*, object, object))
{
  bool weak = objectClass(t, map) == type(t, GcWeakHashMap::Type);

  GcArray* array = map->array();
  if (array) {
    unsigned index = hash(t, key) & (array->length() - 1);
    for (GcTriple* n = cast<GcTriple>(t, array->body()[index]); n;
         n = cast<GcTriple>(t, n->third())) {
      object k = n->first();
      if (weak) {
        k = cast<GcJreference>(t, k)->target();
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

void hashMapResize(Thread* t,
                   GcHashMap* map,
                   uint32_t (*hash)(Thread*, object),
                   unsigned size)
{
  PROTECT(t, map);

  GcArray* newArray = 0;

  if (size) {
    GcArray* oldArray = map->array();
    PROTECT(t, oldArray);

    unsigned newLength = nextPowerOfTwo(size);
    if (oldArray and oldArray->length() == newLength) {
      return;
    }

    newArray = makeArray(t, newLength);

    if (oldArray != map->array()) {
      // a resize was performed during a GC via the makeArray call
      // above; nothing left to do
      return;
    }

    if (oldArray) {
      bool weak = objectClass(t, map) == type(t, GcWeakHashMap::Type);
      for (unsigned i = 0; i < oldArray->length(); ++i) {
        GcTriple* next;
        for (GcTriple* p = cast<GcTriple>(t, oldArray->body()[i]); p;
             p = next) {
          next = cast<GcTriple>(t, p->third());

          object k = p->first();
          if (weak) {
            k = cast<GcJreference>(t, k)->target();
            if (k == 0) {
              continue;
            }
          }

          unsigned index = hash(t, k) & (newLength - 1);

          p->setThird(t, newArray->body()[index]);
          newArray->setBodyElement(t, index, p);
        }
      }
    }
  }

  map->setArray(t, newArray);
}

void hashMapInsert(Thread* t,
                   GcHashMap* map,
                   object key,
                   object value,
                   uint32_t (*hash)(Thread*, object))
{
  // note that we reinitialize the array variable whenever an
  // allocation (and thus possibly a collection) occurs, in case the
  // array changes due to a table resize.

  PROTECT(t, map);

  uint32_t h = hash(t, key);

  bool weak = objectClass(t, map) == type(t, GcWeakHashMap::Type);

  GcArray* array = map->array();

  ++map->size();

  if (array == 0 or map->size() >= array->length() * 2) {
    PROTECT(t, key);
    PROTECT(t, value);

    hashMapResize(t, map, hash, array ? array->length() * 2 : 16);

    array = map->array();
  }

  object k = key;

  if (weak) {
    PROTECT(t, key);
    PROTECT(t, value);

    GcWeakReference* r = makeWeakReference(t, 0, 0, 0, 0);

    r->setTarget(t, key);
    r->setVmNext(t, t->m->weakReferences);
    t->m->weakReferences = r->as<GcJreference>(t);
    k = r;

    array = map->array();
  }

  GcTriple* n = makeTriple(t, k, value, 0);

  array = map->array();

  unsigned index = h & (array->length() - 1);

  n->setThird(t, array->body()[index]);
  array->setBodyElement(t, index, n);

  if (map->size() <= array->length() / 3) {
    // this might happen if nodes were removed during GC in which case
    // we weren't able to resize at the time
    hashMapResize(t, map, hash, array->length() / 2);
  }
}

GcTriple* hashMapRemoveNode(Thread* t,
                            GcHashMap* map,
                            unsigned index,
                            GcTriple* p,
                            GcTriple* n)
{
  if (p) {
    p->setThird(t, n->third());
  } else {
    map->array()->setBodyElement(t, index, n->third());
  }
  --map->size();
  return n;
}

object hashMapRemove(Thread* t,
                     GcHashMap* map,
                     object key,
                     uint32_t (*hash)(Thread*, object),
                     bool (*equal)(Thread*, object, object))
{
  bool weak = objectClass(t, map) == type(t, GcWeakHashMap::Type);

  GcArray* array = map->array();
  object o = 0;
  if (array) {
    unsigned index = hash(t, key) & (array->length() - 1);
    GcTriple* p = 0;
    for (GcTriple* n = cast<GcTriple>(t, array->body()[index]); n;) {
      object k = n->first();
      if (weak) {
        k = cast<GcJreference>(t, k)->target();
        if (k == 0) {
          n = cast<GcTriple>(t,
                             hashMapRemoveNode(t, map, index, p, n)->third());
          continue;
        }
      }

      if (equal(t, key, k)) {
        o = hashMapRemoveNode(t, map, index, p, n)->second();
        break;
      } else {
        p = n;
        n = cast<GcTriple>(t, n->third());
      }
    }

    if ((not t->m->collecting) and map->size() <= array->length() / 3) {
      PROTECT(t, o);
      hashMapResize(t, map, hash, array->length() / 2);
    }
  }

  return o;
}

void listAppend(Thread* t, GcList* list, object value)
{
  PROTECT(t, list);

  ++list->size();

  object p = makePair(t, value, 0);
  if (list->front()) {
    cast<GcPair>(t, list->rear())->setSecond(t, p);
  } else {
    list->setFront(t, p);
  }
  list->setRear(t, p);
}

GcVector* vectorAppend(Thread* t, GcVector* vector, object value)
{
  if (vector->length() == vector->size()) {
    PROTECT(t, vector);
    PROTECT(t, value);

    GcVector* newVector
        = makeVector(t, vector->size(), max(16, vector->size() * 2));

    if (vector->size()) {
      for (size_t i = 0; i < vector->size(); i++) {
        newVector->setBodyElement(t, i, vector->body()[i]);
      }
    }

    vector = newVector;
  }

  vector->setBodyElement(t, vector->size(), value);
  ++vector->size();
  return vector;
}

GcArray* growArray(Thread* t, GcArray* array)
{
  PROTECT(t, array);

  GcArray* newArray = makeArray(t, array == 0 ? 16 : (array->length() * 2));

  if (array) {
    for (size_t i = 0; i < array->length(); i++) {
      newArray->setBodyElement(t, i, array->body()[i]);
    }
  }

  return newArray;
}

object treeQuery(Thread* t,
                 GcTreeNode* tree,
                 intptr_t key,
                 GcTreeNode* sentinal,
                 intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  GcTreeNode* node = treeFind(t, tree, key, sentinal, compare);
  return (node ? getTreeNodeValue(t, node) : 0);
}

GcTreeNode* treeInsert(Thread* t,
                       Zone* zone,
                       GcTreeNode* tree,
                       intptr_t key,
                       object value,
                       GcTreeNode* sentinal,
                       intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  PROTECT(t, tree);
  PROTECT(t, sentinal);

  GcTreeNode* node = makeTreeNode(t, value, sentinal, sentinal);

  TreeContext c(t, zone);
  treeFind(t, &c, tree, key, node, sentinal, compare);
  expect(t, c.fresh);

  return treeAdd(t, &c);
}

void treeUpdate(Thread* t,
                GcTreeNode* tree,
                intptr_t key,
                object value,
                GcTreeNode* sentinal,
                intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  setTreeNodeValue(t, treeFind(t, tree, key, sentinal, compare), value);
}

}  // namespace vm
