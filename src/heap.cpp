#include "heap.h"
#include "system.h"
#include "common.h"

using namespace vm;

namespace {

// an object must survive TenureThreshold + 2 garbage collections
// before being copied to gen2:
static const unsigned TenureThreshold = 3;

static const unsigned MinimumGen1Size = 64 * 1024;
static const unsigned MinimumGen2Size = 128 * 1024;

class Context;

System* system(Context*);
void NO_RETURN abort(Context*);
void assert(Context*, bool);

class Segment {
 public:
  class Map {
   public:
    class Iterator {
     public:
      Map* map;
      unsigned index;
      unsigned limit;
      
      Iterator(Map* map, unsigned start, unsigned end):
        map(map)
      {
        assert(map->segment->context, map);
        assert(map->segment->context, map->bitsPerRecord == 1);
        assert(map->segment->context, map->segment);

        index = map->indexOf(map->segment->data + start);
        assert(map->segment->context, index == 0 or start != 0);

        if (end > map->segment->position) end = map->segment->position;

        limit = map->indexOf(map->segment->data + end);
        if (end - start % map->scale) ++ limit;

//         printf("iterating from %p (index %d) to %p (index %d) "
//                "(%d of %d bytes) (scale: %d)\n",
//                start, index, end, limit, (end - start) * BytesPerWord,
//                map->segment->position * BytesPerWord, map->scale);
      }

      bool hasMore() {
        assert(map->segment->context, map);

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
      
      unsigned next() {
        assert(map->segment->context, hasMore());
        assert(map->segment->context, map->segment);

        return (index++) * map->scale;
      }
    };

    Segment* segment;
    unsigned bitsPerRecord;
    unsigned scale;
    Map* child;
    
    Map(Segment* segment = 0, unsigned bitsPerRecord = 1,
        unsigned scale = 1, Map* child = 0):
      segment(segment),
      bitsPerRecord(bitsPerRecord),
      scale(scale),
      child(child)
    {
      if (segment) {
        assert(segment->context, bitsPerRecord);
        assert(segment->context, scale);
        assert(segment->context, powerOfTwo(scale));
      }
    }

    unsigned offset() {
      unsigned n = segment->capacity;
      if (child) n += child->footprint(segment->capacity);
      return n;
    }

    uintptr_t* data() {
      return segment->data + offset();
    }

    unsigned size(unsigned capacity) {
      unsigned result
        = divide(divide(capacity, scale) * bitsPerRecord, BitsPerWord);
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
      uintptr_t* p = segmentData + offset();
      memcpy(p, data(), size(segment->position) * BytesPerWord);

      if (child) child->update(segmentData);
    }

    void clear() {
      memset(data(), 0, size() * BytesPerWord);

      if (child) child->clear();
    }

    void clear(unsigned i) {
      data()[wordOf(i)] &= ~(static_cast<uintptr_t>(1) << bitOf(i));
    }

    void set(unsigned i) {
      data()[wordOf(i)] |= static_cast<uintptr_t>(1) << bitOf(i);
    }

    void clearOnly(unsigned index) {
      for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
        clear(i);
      }
    }

    void clearOnly(void* p) {
      clearOnly(indexOf(p));
    }

    void clear(void* p) {
      clearOnly(p);
      if (child) child->clear(p);
    }

    void setOnly(unsigned index, unsigned v = 1) {
      unsigned i = index + bitsPerRecord - 1;
      while (true) {
        if (v & 1) set(i); else clear(i);
        v >>= 1;
        if (i == index) break;
        --i;
      }
    }

    void setOnly(void* p, unsigned v = 1) {
      setOnly(indexOf(p), v);
    }

    void set(void* p, unsigned v = 1) {
      setOnly(p, v);
      assert(segment->context, get(p) == v);
      if (child) child->set(p, v);
    }

    unsigned get(void* p) {
      unsigned index = indexOf(p);
      unsigned v = 0;
      for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
        unsigned wi = bitOf(i);
        v <<= 1;
        v |= ((data()[wordOf(i)]) & (static_cast<uintptr_t>(1) << wi)) >> wi;
      }
      return v;
    }

    unsigned footprint(unsigned capacity) {
      unsigned n = size(capacity);
      if (child) n += child->footprint(capacity);
      return n;
    }

    void setSegment(Segment* s) {
      segment = s;

      if (child) child->setSegment(s);
    }
  };

  Context* context;
  uintptr_t* data;
  unsigned position;
  unsigned capacity;
  Map* map;

  Segment(Context* context, unsigned capacity, Map* map = 0,
          bool clearMap = true):
    context(context),
    data(0),
    position(0),
    capacity(capacity),
    map(map)
  {
    if (capacity) {
      data = static_cast<uintptr_t*>
        (system(context)->allocate(footprint(capacity) * BytesPerWord));

      if (map) {
        map->setSegment(this);
        if (clearMap) map->clear();
      }
    }
  }

  unsigned footprint(unsigned capacity) {
    unsigned n = capacity;
    if (map) n += map->size(capacity);
    return n;
  }

  unsigned footprint() {
    return footprint(capacity);
  }

  void* allocate(unsigned size) {
    assert(context, size);
    assert(context, position + size <= capacity);
    void* p = reinterpret_cast<void**>(data) + position;
    position += size;

    return p;
  }

  void* add(void* p, unsigned size) {
    void* target = allocate(size);
    memcpy(target, p, size * BytesPerWord);
    return target;
  }

  void* get(unsigned offset) {
    assert(context, offset < position);
    return data + offset;
  }

  unsigned remaining() {
    return capacity - position;
  }

  void replaceWith(Segment* s) {
    system(context)->free(data);

    data = s->data;
    s->data = 0;

    position = s->position;
    s->position = 0;

    capacity = s->capacity;
    s->capacity = 0;

    if (s->map) {
      map = s->map;
      map->setSegment(this);
      s->map = 0;
    } else {
      map = 0;
    }    
  }

  void grow(unsigned extra) {
    if (remaining() < extra) {
      unsigned minimumNeeded = position + extra;
      unsigned count = minimumNeeded * 2;
      
      minimumNeeded = footprint(minimumNeeded) * BytesPerWord;
      count = footprint(count) * BytesPerWord;

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
    system(context)->free(data);
    data = 0;
    position = 0;
    capacity = 0;

    map = 0;
  }
};

enum CollectionMode {
  MinorCollection,
  MajorCollection,
  OverflowCollection,
  Gen2Collection
};

class Context {
 public:
  Context(System* system):
    system(system),
    client(0),
    gen1(this, 0),
    nextGen1(this, 0),
    gen2(this, 0),
    nextGen2(this, 0)
  { }

  void dispose() {
    gen1.dispose();
    nextGen1.dispose();
    gen2.dispose();
    nextGen2.dispose();
  }

  System* system;
  Heap::Client* client;
  
  Segment gen1;
  Segment nextGen1;
  Segment gen2;
  Segment nextGen2;

  Segment::Map ageMap;
  Segment::Map nextAgeMap;
  Segment::Map pointerMap;
  Segment::Map pageMap;
  Segment::Map heapMap;

  CollectionMode mode;
};

inline System*
system(Context* c)
{
  return c->system;
}

inline void NO_RETURN
abort(Context* c)
{
  c->system->abort(); // this should not return
  ::abort();
}

inline void
assert(Context* c, bool v)
{
  if (UNLIKELY(not v)) abort(c);
}

void
initGen1(Context* c)
{
  new (&(c->ageMap)) Segment::Map(&(c->gen1), log(TenureThreshold));
  new (&(c->gen1)) Segment
    (c, MinimumGen1Size / BytesPerWord, &(c->ageMap), false);
}

void
initGen2(Context* c)
{
  new (&(c->pointerMap)) Segment::Map(&(c->gen2));
  new (&(c->pageMap)) Segment::Map
    (&(c->gen2), 1, LikelyPageSize / BytesPerWord, &(c->pointerMap));
  new (&(c->heapMap)) Segment::Map
    (&(c->gen2), 1, c->pageMap.scale * 1024, &(c->pageMap));
  new (&(c->gen2)) Segment(c, MinimumGen2Size / BytesPerWord, &(c->heapMap));
}

void
initNextGen1(Context* c)
{
  unsigned size = max(MinimumGen1Size / BytesPerWord,
                      nextPowerOfTwo(c->gen1.position));
  new (&(c->nextAgeMap)) Segment::Map(&(c->nextGen1), log(TenureThreshold));
  new (&(c->nextGen1)) Segment(c, size, &(c->nextAgeMap), false);
}

void
initNextGen2(Context* c)
{
  unsigned size = max(MinimumGen2Size / BytesPerWord,
                      nextPowerOfTwo(c->gen2.position));
  new (&(c->pointerMap)) Segment::Map(&(c->nextGen2));
  new (&(c->pageMap)) Segment::Map
    (&(c->nextGen2), 1, LikelyPageSize / BytesPerWord, &(c->pointerMap));
  new (&(c->heapMap)) Segment::Map
    (&(c->nextGen2), 1, c->pageMap.scale * 1024, &(c->pageMap));
  new (&(c->nextGen2)) Segment(c, size, &(c->heapMap));
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

inline object
copyTo(Context*, Segment* s, object o, unsigned size)
{
  if (s->remaining() < size) {
    s->grow(size);
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
    if (age == TenureThreshold) {
      if (c->mode == MinorCollection) {
        if (c->gen2.data == 0) initGen2(c);

        if (c->gen2.remaining() >= size) {
          return copyTo(c, &(c->gen2), o, size);
        } else {
          c->mode = OverflowCollection;
          initNextGen2(c);
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
    } walker(c, bitset(original));

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
        bool* dirty, bool expectDirty)
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
      if (c->mode == OverflowCollection) {
        return;
      } else if (childDirty) {
        map->setOnly(s);
        *dirty = true;
      }
    } else {
      assert(c, map->scale == 1);
      object* p = reinterpret_cast<object*>(map->segment->get(it.next()));

      map->clearOnly(p);
      if (c->nextGen1.contains(*p)) {
        map->setOnly(p);
        *dirty = true;
      } else {
        collect(c, p);

        if (c->mode == OverflowCollection) {
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

    class Walker : public Heap::Walker {
     public:
      Walker(Context* c, object p): c(c), p(p) { }

      virtual bool visit(unsigned offset) {
        collect(c, &cast<object>(p, offset * BytesPerWord));
        return true;
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
  if (c->mode == MinorCollection and c->gen2.position) {
    unsigned start = 0;
    unsigned end = start + c->gen2.position;
    bool dirty;
    collect(c, &(c->heapMap), start, end, &dirty, false);
  } else if (c->mode == Gen2Collection) {
    unsigned ng2Position = c->nextGen2.position;
    collect(c, &(c->nextGen1), c->nextGen1.position);
    collect(c, &(c->nextGen2), ng2Position);
  }

  class Visitor : public Heap::Visitor {
   public:
    Visitor(Context* c): c(c) { }

    virtual void visit(void** p) {
      collect(c, p);
    }

    Context* c;
  } v(c);

  c->client->visitRoots(&v);
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

  default: abort(c);
  }
}

} // namespace

namespace vm {

Heap*
makeHeap(System* system)
{
  class Heap: public vm::Heap {
   public:
    Heap(System* system): c(system) { }

    virtual void collect(CollectionType type, Client* client) {
      switch (type) {
      case MinorCollection:
        c.mode = ::MinorCollection;
        break;

      case MajorCollection:
        c.mode = ::MajorCollection;
        break;

      default: abort(&c);
      }

      c.client = client;

      ::collect(&c);
    }

    virtual bool needsMark(void** p) {
      return *p and c.gen2.contains(p) and not c.gen2.contains(*p);
    }

    virtual void mark(void** p) {
      c.heapMap.set(p);
    }

    virtual void dispose() {
      c.dispose();
      c.system->free(this);
    }

    virtual void* follow(void* p) {
      if (wasCollected(&c, p)) {
        return ::follow(p);
      } else {
        return p;
      }
    }

    Context c;
  };
  
  return new (system->allocate(sizeof(Heap))) Heap(system);
}

} // namespace vm
