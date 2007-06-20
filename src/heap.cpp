#include "heap.h"
#include "system.h"
#include "common.h"

using namespace vm;

namespace {

class Segment {
 public:
  class Map {
   public:
    class Iterator {
     public:
      Map* map;
      unsigned index;
      unsigned limit;
      
      Iterator(Map* map, void** start, void** end):
        map(map)
      {
        assert(map);
        assert(map->bitsPerRecord == 1);
        assert(map->segment);

        index = map->indexOf(start);
        assert(index == 0 or
               start != reinterpret_cast<void**>(map->segment->data));

        void** p = reinterpret_cast<void**>(map->segment->data)
          + map->segment->position;
        if (end > p) end = p;

        limit = map->indexOf(end);
        if (static_cast<unsigned>(end - start) % map->scale) ++ limit;

//         printf("iterating from %p (index %d) to %p (index %d) "
//                "(%d of %d bytes) (scale: %d)\n",
//                start, index, end, limit, (end - start) * BytesPerWord,
//                map->segment->position * BytesPerWord, map->scale);
      }

      bool hasMore() {
        assert(map);

        unsigned word = wordOf(index);
        unsigned bit = bitOf(index);
        unsigned wordLimit = wordOf(limit);
        unsigned bitLimit = bitOf(limit);

        for (; word <= wordLimit and (word < wordLimit or bit < bitLimit);
             ++word)
        {
          uintptr_t* p = map->data() + word;
          if (*p) {
            for (; bit < BitsPerWord and (word < wordLimit or bit < bitLimit);
                 ++bit)
            {
              if (map->data()[word] & (static_cast<uintptr_t>(1) << bit)) {
                index = ::indexOf(word, bit);
//                 printf("hit at index %d\n", index);
                return true;
              } else {
//                 printf("miss at index %d\n", indexOf(word, bit));
              }
            }
          }
          bit = 0;
        }

        index = limit;

        return false;
      }
      
      void** next() {
        assert(map->segment->context, hasMore());
        assert(map->segment->context, map->segment);

        return reinterpret_cast<void**>(map->segment->data) +
          ((index++) * map->scale);
      }
    };

    Segment* segment;
    unsigned offset;
    unsigned bitsPerRecord;
    unsigned scale;
    Map* next;
    Map* child;
    
    void init() {
      init(0);
    }

    void init(Segment* segment, unsigned offset, unsigned bitsPerRecord = 1,
              unsigned scale = 1, Map* next = 0, Map* child = 0)
    {
      assert(bitsPerRecord);
      assert(scale);
      assert(powerOfTwo(scale));

      this->segment = segment;
      this->offset = offset;
      this->bitsPerRecord = bitsPerRecord;
      this->scale = scale;
      this->next = next;
      this->child = child;
    }

    uintptr_t* data() {
      return segment->data + offset;
    }

    unsigned size(unsigned capacity) {
      unsigned result = pad
        (divide(divide(capacity, scale) * bitsPerRecord, 8));
      assert(segment->context, result);
      return result;
    }

    unsigned size() {
      assert(segment->context, segment);
      return size(max(segment->capacity, 1));
    }

    unsigned indexOf(void* p) {
      assert(segment->context, segment);
      assert(segment->context,
             segment->position
             and p >= segment->data
             and p <= segment->data + segment->position);
      assert(segment->context, segment->data);

      return ((static_cast<void**>(p)
               - reinterpret_cast<void**>(segment->data))
              / scale) * bitsPerRecord;
    }

    void update(uintptr_t* segmentData) {
      uintptr_t* p = segmentData + offset;
      memcpy(p, data(), size(segment->position));

      if (next) next->update(segmentData);
      if (child) child->update(segmentData);
    }

    void clear() {
      memset(data, 0, size());

      if (next) next->clear();
      if (child) child->clear();
    }

    void clear(unsigned i) {
      data[wordOf(i)] &= ~(static_cast<uintptr_t>(1) << bitOf(i));
    }

    void set(unsigned i) {
      data[wordOf(i)] |= static_cast<uintptr_t>(1) << bitOf(i);
    }

    void clearOnly(void* p) {
      unsigned index = indexOf(p);
      for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
        clear(i);
      }
    }

    void clear(void* p) {
      clearOnly(p);
      if (child) child->clear(p);
    }

    void setOnly(void* p, unsigned v = 1) {
      unsigned index = indexOf(p);
      unsigned i = index + bitsPerRecord - 1;
      while (true) {
        if (v & 1) set(i); else clear(i);
        v >>= 1;
        if (i == index) break;
        --i;
      }
    }

    void set(void* p, unsigned v = 1) {
      setOnly(p, v);
      assert(get(p) == v);
      if (child) child->set(p, v);
    }

    unsigned get(void* p) {
      unsigned index = indexOf(p);
      unsigned v = 0;
      for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
        unsigned wi = bitOf(i);
        v <<= 1;
        v |= ((data[wordOf(i)]) & (static_cast<uintptr_t>(1) << wi))
          >> wi;
      }
      return v;
    }

    void dispose() {
      offset = 0;
      segment = 0;
      next = 0;

      if (child) {
        child->dispose();
        child = 0;
      }
    }

    unsigned footprint(unsigned capacity) {
      unsigned n = size(capacity);
      if (next) n += next->footprint(capacity);
      if (child) n += child->footprint(capacity);
      return n;
    }

    void setSegment(Segment* s, bool clear = true) {
      segment = s;
      if (next) next->setSegment(s);
      if (child) child->setSegment(s);
    }
  };

  uintptr_t* data;
  unsigned position;
  unsigned capacity;
  Map* map;

  unsigned footprint(unsigned capacity) {
    unsigned n = capacity * BytesPerWord;
    if (map) n += map->size(capacity);
    return n;
  }

  unsigned footprint() {
    return footprint(capacity);
  }

  void init(Context* context, unsigned capacity, Map* map = 0,
            bool clearMap = true)
  {
    this->context = context;
    this->capacity = capacity;
    this->data = 0;
    this->position = 0;
    this->map = map;

    if (capacity) {
      unsigned count = footprint(capacity);
      this->data = static_cast<uintptr_t*>(system(context)->allocate(&count));

      if (count != footprint(capacity)) {
        abort(context);
      }

      if (map) {
        map->setSegment(this, clearMap);
      }
    }
  }

  void* allocate(unsigned size) {
    assert(c, size);
    assert(c, position + size <= capacity);
    void* p = reinterpret_cast<void**>(data) + position;
    position += size;

    return p;
  }

  void* add(void* p, unsigned size) {
    void* target = allocate(size);
    memcpy(target, p, size * BytesPerWord);
    return target;
  }

  unsigned remaining() {
    return capacity - position;
  }

  void replaceWith(Segment* s) {
    free(data);

    data = s->data;
    s->data = 0;

    position = s->position;
    s->position = 0;

    capacity = s->capacity;
    s->capacity = 0;

    if (s->map) {
      if (map) {
        map->replaceWith(s->map);
      } else {
        map = s->map;
        map->setSegment(this);
      }
      s->map = 0;
    } else {
      if (map) map->reset();
    }    
  }

  void grow(unsigned extra) {
    if (remaining() < extra) {
      unsigned minimumNeeded = position + extra;
      unsigned count = minimumNeeded * 2;
      
      minimumNeeded = footprint(minimumNeeded);
      count = footprint(count);

      uintptr_t* p = static_cast<uintptr_t*>
        (system(context)->allocate(&count));

      if (count >= minimumNeeded) {
        memcpy(p, data, position * BytesPerWord);

        if (map) {
          map->update(p);
        }

        data = p;
        system(context)->free(data);
      } else {
        abort(context);
      }
    }
  }

  bool contains(void* p) {
    return position and p >= data and p < data + position;
  }

  void dispose() {
    free(data);
    data = 0;
    position = 0;
    capacity = 0;

    if (map) map->dispose();
    map = 0;
  }
};

class Context {
 public:
};

void
initGen1(Context* c)
{
  c->ageMap.init(&(c->gen1), log(Arena::TenureThreshold));
  c->gen1.init(c->minimumGen1Size / BytesPerWord, &(c->ageMap), false);
}

void
initGen2(Context* c)
{
  c->pointerMap.init(&(c->gen2));
  c->pageMap.init(&(c->gen2), 1, LikelyPageSize / BytesPerWord, 0,
                  &(c->pointerMap));
  c->heapMap.init(&(c->gen2), 1, c->pageMap.scale * 1024, 0, &(c->pageMap));
  c->gen2.init(c->minimumGen2Size / BytesPerWord, &(c->heapMap));
}

void
initNextGen1(Context* c)
{
  unsigned size = max(c->minimumGen1Size / BytesPerWord,
                      nextPowerOfTwo(c->gen1.position()));
  c->nextAgeMap.init(&(c->nextGen1), log(Arena::TenureThreshold));
  c->nextGen1.init(size, &(c->nextAgeMap), false);
}

void
initNextGen2(Context* c)
{
  unsigned size = max(c->minimumGen2Size / BytesPerWord,
                      nextPowerOfTwo(c->gen2.position()));
  c->pointerMap.init(&(c->nextGen2));
  c->pageMap.init(&(c->nextGen2), 1, LikelyPageSize / BytesPerWord, 0,
                  &(c->pointerMap));
  c->heapMap.init(&(c->nextGen2), 1, c->pageMap.scale * 1024, 0,
                  &(c->pageMap));
  c->nextGen2.init(size, &(c->heapMap));
  c->gen2.map = 0;
}

inline object&
follow(object o)
{
  return cast<object>(o, 0);
}

inline object&
parent(object o)
{
  return cast<object>(o, BytesPerWord);
}

inline uintptr_t*
bitset(object o)
{
  return &cast<uintptr_t>(o, BytesPerWord * 2);
}

object
copyTo(Context* c, Segment* s, object o, unsigned size)
{
  if (s->remaining() < size) {
    s->grow(c->sys, size);
  }

  return static_cast<object>(s->add(o, size));
}

object
copy2(Context* c, object o)
{
  unsigned size = c->client->sizeInWords(o);

  if (c->gen2.contains(o)) {
    assert(c, c->mode == MajorCollection
           or c->mode == Gen2Collection);

    return copyTo(c, &(c->nextGen2), o, size);
  } else if (c->gen1.contains(o)) {
    unsigned age = c->ageMap.get(o);
    if (age == Arena::TenureThreshold) {
      if (c->mode == MinorCollection) {
        if (c->gen2.front == 0) initGen2(a);

        if (c->gen2.remaining() >= size) {
          return copyTo(c, &(c->gen2), o, size);
        } else {
          c->mode = OverflowCollection;
          initNextGen2(a);
          return copyTo(c, &(c->nextGen2), o, size);          
        }
      } else {
        return copyTo(c, &(c->nextGen2), o, size);
      }
    } else {
      o = copyTo(c, &(c->nextGen1), o, size);
      c->nextAgeMap.setOnly(o, age + 1);
      return o;
    }
  } else {
    assert(c, not c->nextGen1.contains(o));
    assert(c, not c->nextGen2.contains(o));

    o = copyTo(c, &(c->nextGen1), o, size);

    c->nextAgeMap.clear(o);

    return o;
  }
}

object
copy(Context* c, object o)
{
  object r = copy2(c, o);

  // leave a pointer to the copy in the original
  follow(o) = r;

  return r;
}

inline bool
wasCollected(Context* c, object o)
{
  return o and (c->nextGen1.contains(follow(o)) or
                c->nextGen2.contains(follow(o)));
}

object
update3(Context* c, object *p, bool* needsVisit)
{
  if (wasCollected(c, *p)) {
    *needsVisit = false;
    return follow(*p);
  } else {
    *needsVisit = true;
    return copy(c, *p);
  }
}

object
update2(Context* c, object* p, bool* needsVisit)
{
  switch (c->mode) {
  case MinorCollection:
  case OverflowCollection:
    if (c->gen2.contains(*p)) {
      *needsVisit = false;
      return *p;
    }
    break;
    
  case Gen2Collection:
    if (c->gen2.contains(*p)) {
      return update3(c, p, needsVisit);
    } else {
      *needsVisit = false;
      return *p;
    }
    break;

  default: break;
  }

  return update3(c, p, needsVisit);
}

object
update(Context* c, object* p, bool* needsVisit)
{
  if (*p == 0) {
    *needsVisit = false;
    return *p;
  }

  object r = update2(c, p, needsVisit);

  // update heap map.
  if (r) {
    if (c->mode == MinorCollection) {
      if (c->gen2.contains(p) and not c->gen2.contains(r)) {
        c->heapMap.set(p);
      }
    } else {
      if (c->nextGen2.contains(p) and not c->nextGen2.contains(r)) {
        c->heapMap.set(p);
      }      
    }
  }

  return r;
}

const uintptr_t BitsetExtensionBit
= (static_cast<uintptr_t>(1) << (BitsPerWord - 1));

void
bitsetInit(uintptr_t* p)
{
  memset(p, 0, BytesPerWord);
}

void
bitsetClear(uintptr_t* p, unsigned start, unsigned end)
{
  if (end < BitsPerWord - 1) {
    // do nothing
  } else if (start < BitsPerWord - 1) {
    memset(p + 1, 0, (wordOf(end + (BitsPerWord * 2) + 1)) * BytesPerWord);
  } else {
    unsigned startWord = wordOf(start + (BitsPerWord * 2) + 1);
    unsigned endWord = wordOf(end + (BitsPerWord * 2) + 1);
    if (endWord > startWord) {
      memset(p + startWord + 1, 0, (endWord - startWord) * BytesPerWord);
    }
  }
}

void
bitsetSet(uintptr_t* p, unsigned i, bool v)
{
  if (i >= BitsPerWord - 1) {
    i += (BitsPerWord * 2) + 1;
    if (v) {
      p[0] |= BitsetExtensionBit;
      if (p[2] <= wordOf(i) - 3) p[2] = wordOf(i) - 2;
    }
  }

  if (v) {
    p[wordOf(i)] |= static_cast<uintptr_t>(1) << bitOf(i);
  } else {
    p[wordOf(i)] &= ~(static_cast<uintptr_t>(1) << bitOf(i));
  }
}

unsigned
bitsetHasMore(uintptr_t* p)
{
  switch (*p) {
  case 0: return false;

  case BitsetExtensionBit: {
    uintptr_t length = p[2];
    uintptr_t word = wordOf(p[1]);
    for (; word < length; ++word) {
      if (p[word + 3]) {
        p[1] = indexOf(word, 0);
        return true;
      }
    }
    p[1] = indexOf(word, 0);
    return false;
  }

  default: return true;
  }
}

unsigned
bitsetNext(Context* c, uintptr_t* p)
{
  assert(c, bitsetHasMore(p));

  switch (*p) {
  case 0: abort(c);

  case BitsetExtensionBit: {
    uintptr_t i = p[1];
    uintptr_t word = wordOf(i);
    assert(c, word < p[2]);
    for (uintptr_t bit = bitOf(i); bit < BitsPerWord; ++bit) {
      if (p[word + 3] & (static_cast<uintptr_t>(1) << bit)) {
        p[1] = indexOf(word, bit) + 1;
        bitsetSet(p, p[1] + BitsPerWord - 2, false);
        return p[1] + BitsPerWord - 2;
      }
    }
    abort(c);
  }

  default: {
    for (unsigned i = 0; i < BitsPerWord - 1; ++i) {
      if (*p & (static_cast<uintptr_t>(1) << i)) {
        bitsetSet(p, i, false);
        return i;
      }
    }
    abort(c);
  }
  }
}

void
collect(Context* c, void** p)
{
  object original = *p;
  object parent = 0;

  bool needsVisit;
  *p = update(c, p, &needsVisit);

  if (not needsVisit) return;

 visit: {
    object copy = follow(original);

    class Walker : public Heap::Walker {
     public:
      Walker(Context* c, object copy, uintptr_t* bitset):
        c(c),
        copy(copy),
        bitset(bitset),
        first(0),
        last(0),
        visits(0),
        total(0)
      { }

      virtual bool visit(unsigned offset) {
        bool needsVisit;
        object childCopy = update
          (c, &cast<object>(copy, offset * BytesPerWord), &needsVisit);
        
        ++ total;

        if (total == 3) {
          bitsetInit(bitset);
        }

        if (needsVisit) {
          ++ visits;

          if (visits == 1) {
            first = offset;
          }

          if (total >= 3) {
            bitsetClear(bitset, last, offset);
            last = offset;

            bitsetSet(bitset, offset, true);
          }          
        } else {
          cast<object>(copy, offset * BytesPerWord) = childCopy;
        }

        return true;
      }

      Context* c;
      object copy;
      uintptr_t* bitset;
      unsigned first;
      unsigned last;
      unsigned visits;
      unsigned total;
    } walker(c, copy, bitset(original));

    c->client->walk(copy, &walker);

    if (walker.visits) {
      // descend
      if (walker.visits > 1) {
        ::parent(original) = parent;
        parent = original;
      }

      original = cast<object>(copy, walker.first * BytesPerWord);
      cast<object>(copy, walker.first * BytesPerWord) = follow(original);
      goto visit;
    } else {
      // ascend
      original = parent;
    }
  }

  if (original) {
    object copy = follow(original);

    class Walker : public Heap::Walker {
     public:
      Walker(Context* c, uintptr_t* bitset):
        c(c),
        bitset(bitset),
        next(0),
        total(0)
      { }

      virtual bool visit(unsigned offset) {
        switch (++ total) {
        case 1:
          return true;

        case 2:
          next = offset;
          return true;
          
        case 3:
          next = bitsetNext(c, bitset);
          return false;

        default:
          abort(c);
        }
      }

      Context* c;
      uintptr_t* bitset;
      unsigned next;
      unsigned total;
    } walker(c, copy, bitset(original));

    assert(c, walker.total > 1);

    if (walker.total == 3 and bitsetHasMore(bitset(original))) {
      parent = original;
    } else {
      parent = ::parent(original);
    }

    original = cast<object>(copy, walker.next * BytesPerWord);
    cast<object>(copy, walker.next * BytesPerWord) = follow(original);
    goto visit;
  } else {
    return;
  }
}

void
collect(Context* c, Segment::Map* map, unsigned start, unsigned end,
        bool* dirty, bool expectDirty UNUSED)
{
  bool wasDirty = false;
  for (Segment::Map::Iterator it(map, start, end); it.hasMore();) {
    wasDirty = true;
    if (map->child) {
      assert(c, map->scale > 1);
      unsigned s = it.next();
      unsigned e = s + map->scale;

      map->clearOnly(s);
      bool childDirty = false;
      collect(c, map->child, s, e, &childDirty, true);
      if (c->collectionMode == OverflowCollection) {
        return;
      } else if (childDirty) {
        map->setOnly(s);
        *dirty = true;
      }
    } else {
      assert(c, map->scale == 1);
      object* p = reinterpret_cast<object*>(map->heap->get(it.next()));

      map->clearOnly(p);
      if (c->nextGen1.contains(*p)) {
        map->setOnly(p);
        *dirty = true;
      } else {
        collect(c, p);

        if (c->collectionMode == OverflowCollection) {
          return;
        } else if (c->gen2.contains(*p)) {
          // done
        } else {
          map->setOnly(p);
          *dirty = true;
        }
      }
    }
  }

  assert(c, wasDirty or not expectDirty);
}

class ObjectSegmentIterator {
 public:
  ObjectSegmentIterator(Context* c, Segment* s, unsigned end):
    c(c), s(s), index(0), end(end)
  { }

  bool hasNext() {
    return index < end;
  }

  object next() {
    assert(c, hasNext());
    object p = s->data + (index * BytesPerWord);
    index += c->client->sizeInWords(p);
    return p;
  }

  Context* c;
  Segment* s;
  unsigned index;
  unsigned end;
};

void
collect(Context* c, Segment* s, unsigned limit)
{
  for (ObjectSegmentIterator it(c, s, limit); it.hasNext();) {
    object p = it.next();
    collect(c, &objectClass(p));

    class Walker : public Heap::Walker {
     public:
      Walker(Context* c, object p): c(c), p(p) { }

      virtual bool visit(unsigned offset) {
        collect(c, &cast<object>(p, offset * BytesPerWord));
      }

      Context* c;
      object p;
    } walker(c, p);

    c->client->walk(p, &walker);
  }
}

void
collect2(Context* c)
{
  if (c->mode == MinorCollection and c->gen2.position()) {
    unsigned start = 0;
    unsigned end = start + c->gen2.position();
    bool dirty;
    collect(c, &(c->heapMap), start, end, &dirty, false);
  } else if (c->mode == Gen2Collection) {
    unsigned ng2Position = c->nextGen2.position();
    collect(c, &(c->nextGen1), c->nextGen1.position());
    collect(c, &(c->nextGen2), ng2Position);
  }

  class Visitor : public Heap::Visitor {
   public:
    Visitor(Context* c): c(c) { }

    virtual void visit(void** p) {
      collect(c, p);
    }
  } v(c);

  c->iterator->iterate(&v);
}

void
collect(Context* c)
{
  switch (c->mode) {
  case MinorCollection: {
    initNextGen1(c);

    collect2(c);

    if (c->mode == OverflowCollection) {
      c->mode = Gen2Collection;
      collect2(c);

      c->gen2.replaceWith(&(c->nextGen2));
    }

    c->gen1.replaceWith(&(c->nextGen1));
  } break;

  case MajorCollection: {
    initNextGen1(c);
    initNextGen2(c);
    
    c->heapMap.clear();

    collect2(c);

    c->gen1.replaceWith(&(c->nextGen1));
    c->gen2.replaceWith(&(c->nextGen2));    
  } break;
  }
}

} // namespace

Heap*
makeHeap(System* system)
{
  

  HeapImp* h = static_cast<HeapImp*>(system->allocate(sizeof(HeapImp)));
  init(h, system);
  return h;
}
