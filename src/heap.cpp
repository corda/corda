#include "heap.h"
#include "system.h"
#include "common.h"

#define CHAIN_HEADER_SIZE divide(sizeof(Segment::Chain), BytesPerWord)

using namespace vm;

namespace {

// an object must survive TenureThreshold + 2 garbage collections
// before being copied to gen2:
const unsigned TenureThreshold = 3;

const unsigned MinimumGen1SizeInBytes = 64 * 1024;
const unsigned MinimumGen2SizeInBytes = 128 * 1024;

const unsigned Top = ~static_cast<unsigned>(0);

const bool Verbose = true;
const bool Debug = false;

class Context;

System* system(Context*);
void NO_RETURN abort(Context*);
void assert(Context*, bool);

inline object
get(object o, unsigned offsetInWords)
{
  return mask(cast<object>(o, offsetInWords * BytesPerWord));
}

inline object*
getp(object o, unsigned offsetInWords)
{
  return &cast<object>(o, offsetInWords * BytesPerWord);
}

inline void
set(object* o, object value)
{
  *o = reinterpret_cast<object>
    (reinterpret_cast<uintptr_t>(value)
     | reinterpret_cast<uintptr_t>(*o) & (~PointerMask));
}

inline void
set(object o, unsigned offsetInWords, object value)
{
  set(getp(o, offsetInWords), value);
}

class Segment {
 public:
  class Map {
   public:
    class Chain;

    class Iterator {
     public:
      Map* map;
      unsigned index;
      unsigned limit;
      
      Iterator(Map* map, unsigned start, unsigned end):
        map(map)
      {
        assert(map->segment->context, map->bitsPerRecord == 1);
        assert(map->segment->context, map->segment);
        assert(map->segment->context, start <= map->segment->position());

        if (end > map->segment->position()) end = map->segment->position();

        index = map->indexOf(start);
        limit = map->indexOf(end);

        if ((end - start) % map->scale) ++ limit;
      }

      bool hasMore() {
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
    Map* child;
    unsigned bitsPerRecord;
    unsigned scale;
    bool clearNewData;

    Map(Segment* segment = 0, unsigned bitsPerRecord = 1,
        unsigned scale = 1, Map* child = 0, bool clearNewData = true):
      segment(segment),
      child(child),
      bitsPerRecord(bitsPerRecord),
      scale(scale),
      clearNewData(clearNewData)
    {
      if (segment) {
        assert(segment->context, bitsPerRecord);
        assert(segment->context, scale);
        assert(segment->context, powerOfTwo(scale));
      }
    }

    void replaceWith(Map* m) {
      assert(segment->context, bitsPerRecord == m->bitsPerRecord);
      assert(segment->context, scale == m->scale);

      m->segment = 0;
      
      if (child) child->replaceWith(m->child);
    }

    unsigned offset(unsigned capacity) {
      unsigned n = 0;
      if (child) n += child->footprint(capacity);
      return n;
    }

    unsigned offset() {
      return offset(segment->capacity());
    }

    uintptr_t* data() {
      return segment->rear->data() + segment->rear->capacity + offset();
    }

    unsigned size(unsigned capacity) {
      unsigned result
        = divide(divide(capacity, scale) * bitsPerRecord, BitsPerWord);
      assert(segment->context, result);
      return result;
    }

    unsigned size() {
      return size(max(segment->capacity(), 1));
    }

    unsigned indexOf(unsigned segmentIndex) {
      return (segmentIndex / scale) * bitsPerRecord;
    }

    unsigned indexOf(void* p) {
      assert(segment->context, segment->almostContains(p));
      assert(segment->context, segment->capacity());
      return indexOf(segment->indexOf(p));
    }

    void update(uintptr_t* newData, unsigned capacity) {
      assert(segment->context, capacity >= segment->capacity());

      uintptr_t* p = newData + offset(capacity);
      memcpy(p, data(), size(segment->position()) * BytesPerWord);

      if (child) {
        child->update(newData, capacity);
      }
    }

    void clear() {
      memset(data(), 0, size() * BytesPerWord);

      if (child) child->clear();
    }

    void clearBit(unsigned i) {
      assert(segment->context, wordOf(i) < size());

      data()[wordOf(i)] &= ~(static_cast<uintptr_t>(1) << bitOf(i));
    }

    void setBit(unsigned i) {
      assert(segment->context, wordOf(i) < size());

      data()[wordOf(i)] |= static_cast<uintptr_t>(1) << bitOf(i);
    }

    void clearOnlyIndex(unsigned index) {
      for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
        clearBit(i);
      }
    }

    void clearOnly(unsigned segmentIndex) {
      clearOnlyIndex(indexOf(segmentIndex));
    }

    void clearOnly(void* p) {
      clearOnlyIndex(indexOf(p));
    }

    void clear(void* p) {
      clearOnly(p);
      if (child) child->clear(p);
    }

    void setOnlyIndex(unsigned index, unsigned v = 1) {
      unsigned i = index + bitsPerRecord - 1;
      while (true) {
        if (v & 1) setBit(i); else clearBit(i);
        v >>= 1;
        if (i == index) break;
        --i;
      }
    }

    void setOnly(unsigned segmentIndex, unsigned v = 1) {
      setOnlyIndex(indexOf(segmentIndex), v);
    }

    void setOnly(void* p, unsigned v = 1) {
      setOnlyIndex(indexOf(p), v);
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

  class Chain {
   public:
    Segment* segment;
    unsigned offset;
    unsigned position;
    unsigned capacity;
    Chain* next;
    Chain* previous;

    Chain(Segment* segment, unsigned capacity, Chain* previous):
      segment(segment),
      offset(previous ? previous->offset + previous->position : 0),
      position(0),
      capacity(capacity),
      next(0),
      previous(previous)
    {
      assert(segment->context, sizeof(Chain) % BytesPerWord == 0);
    }

    static Chain* make(Segment* s, unsigned minimum, unsigned desired) {
      assert(s->context, minimum > 0);
      assert(s->context, desired >= minimum);

      void* p = 0;
      unsigned capacity = desired;
      while (p == 0) {
        p = system(s->context)->tryAllocate
          (footprint(capacity, s->rear, s->map) * BytesPerWord);

        if (p == 0) {
          if (capacity > minimum) {
            capacity = avg(minimum, capacity);
          } else {
            abort(s->context);
          }
        }
      }
      
      return new (p) Chain(s, capacity, s->rear);
    }

    static void dispose(Chain* c) {
      if (c) {
        if (c->next) dispose(c->next);
        system(c->segment->context)->free(c);
      }
    }

    uintptr_t* data() {
      return reinterpret_cast<uintptr_t*>(this) + CHAIN_HEADER_SIZE;
    }

    static unsigned footprint(unsigned capacity, Chain* previous,
                              Map* map)
    {
      unsigned n = CHAIN_HEADER_SIZE + capacity;
      if (map) {
        unsigned segmentCapacity = capacity;
        if (previous) {
          segmentCapacity += previous->offset + previous->position;
        }

        n += map->footprint(segmentCapacity);
      }
      return n;
    }

    unsigned footprint() {
      return footprint(capacity, previous, segment->map);
    }
  };

  Context* context;
  Chain* front;
  Chain* rear;
  Map* map;

  Segment(Context* context, unsigned minimum, unsigned desired, Map* map = 0):
    context(context),
    front(0),
    rear(0),
    map(map)
  {
    if (desired) {
      front = rear = Chain::make(this, minimum, desired);

      if (map) {
        if (map->clearNewData) {
          memset(front->data() + front->capacity, 0,
                 map->footprint(front->capacity) * BytesPerWord);
        }
        map->setSegment(this);
      }
    }
  }

  unsigned capacity() {
    return (rear? rear->offset + rear->capacity : 0);
  }

  unsigned position() {
    return (rear? rear->offset + rear->position : 0);
  }

  void truncate(unsigned offset) {
    assert(context, offset <= position());
    
    for (Chain* c = front; c; c = c->next) {
      if (offset >= c->offset
          and offset <= c->offset + c->position)
      {
        c->position = offset - c->offset;
        Chain::dispose(c->next);
        return;
      }
    }
    abort(context);
  }

  unsigned footprint() {
    unsigned n = 0;
    for (Chain* c = front; c; c = c->next) n += c->footprint();
    return n;
  }

  unsigned remaining() {
    return capacity() - position();
  }

  void replaceWith(Segment* s) {
    Chain::dispose(front);

    front = s->front;
    rear = s->rear;

    s->front = s->rear = 0;

    if (s->map) {
      if (map) {
        map->replaceWith(s->map);
      } else {
        map = s->map;
        map->setSegment(this);
      }
      s->map = 0;
    } else {
      map = 0;
    }    
  }

  bool contains(void* p) {
    for (Chain* c = front; c; c = c->next) {
      if (c->position and p >= c->data() and p < c->data() + c->position) {
        return true;
      }
    }
    return false;
  }

  bool almostContains(void* p) {
    return contains(p) or p == rear->data() + rear->position;
  }

  void* get(unsigned offset) {
    for (Chain* c = front; c; c = c->next) {
      if (c->position
          and offset >= c->offset
          and offset < c->offset + c->position)
      {
        return c->data() + (offset - c->offset);
      }
    }

    if (offset == rear->offset + rear->position) {
      return rear->data() + (offset - rear->offset);
    }

    abort(context);
  }

  unsigned indexOf(void* p) {
    for (Chain* c = front; c; c = c->next) {
      if (c->position and p >= c->data() and p < c->data() + c->position) {
        return (static_cast<uintptr_t*>(p) - c->data()) + c->offset;
      }
    }

    if (p == rear->data() + rear->position) {
      return (static_cast<uintptr_t*>(p) - rear->data()) + rear->offset;
    }

    abort(context);
  }

  void* allocate(unsigned size) {
    assert(context, size);
    assert(context, rear->position + size <= rear->capacity);
    void* p = reinterpret_cast<void**>(rear->data()) + rear->position;
    rear->position += size;

    return p;
  }

  void ensure(unsigned minimum) {
    if (remaining() < minimum) {
      assert(context, rear->position);
      assert(context, rear->next == 0);

      unsigned desired = capacity() + minimum;
      
      Chain* c = Chain::make(this, minimum, desired);

      if (map) {
        if (map->clearNewData) {
          memset(c->data() + c->capacity, 0,
                 map->footprint(c->offset + c->capacity) * BytesPerWord);
        }

        map->update(c->data() + c->capacity, c->offset + c->capacity);
      }

      rear->next = c;
      rear = c;
    }
  }

  void dispose() {
    Chain::dispose(front);
    front = rear = 0;
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
    gen1(this, 0, 0),
    nextGen1(this, 0, 0),
    gen2(this, 0, 0),
    nextGen2(this, 0, 0)
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

  unsigned gen2Base;

  Segment::Map ageMap;
  Segment::Map nextAgeMap;

  Segment::Map pointerMap;
  Segment::Map pageMap;
  Segment::Map heapMap;

  Segment::Map nextPointerMap;
  Segment::Map nextPageMap;
  Segment::Map nextHeapMap;

  CollectionMode mode;
};

const char*
segment(Context* c, void* p)
{
  if (c->gen1.contains(p)) {
    return "gen1";
  } else if (c->nextGen1.contains(p)) {
    return "nextGen1";
  } else if (c->gen2.contains(p)) {
    return "gen2";
  } else if (c->nextGen2.contains(p)) {
    return "nextGen2";
  } else {
    return "none";
  }
}

inline System*
system(Context* c)
{
  return c->system;
}

inline void NO_RETURN
abort(Context* c)
{
  abort(c->system);
}

inline void
assert(Context* c, bool v)
{
  assert(c->system, v);
}

void
initGen1(Context* c)
{
  unsigned minimum = MinimumGen1SizeInBytes / BytesPerWord;
  unsigned desired = minimum;

  new (&(c->ageMap)) Segment::Map
    (&(c->gen1), log(TenureThreshold), 1, 0, false);

  new (&(c->gen1)) Segment(c, minimum, desired, &(c->ageMap));

  if (Verbose) {
    fprintf(stderr, "init gen1 to %d bytes\n",
            c->gen1.capacity() * BytesPerWord);
  }
}

void
initNextGen1(Context* c)
{
  unsigned minimum = MinimumGen1SizeInBytes / BytesPerWord;
  unsigned desired = max(minimum, avg(c->gen1.position(), c->gen1.capacity()));

  new (&(c->nextAgeMap)) Segment::Map
    (&(c->nextGen1), log(TenureThreshold), 1, 0, false);

  new (&(c->nextGen1)) Segment(c, minimum, desired, &(c->nextAgeMap));

  if (Verbose) {
    fprintf(stderr, "init nextGen1 to %d bytes\n",
            c->nextGen1.capacity() * BytesPerWord);
  }
}

void
initGen2(Context* c)
{
  unsigned minimum = MinimumGen2SizeInBytes / BytesPerWord;
  unsigned desired = minimum;

  new (&(c->pointerMap)) Segment::Map(&(c->gen2));
  new (&(c->pageMap)) Segment::Map
    (&(c->gen2), 1, LikelyPageSizeInBytes / BytesPerWord, &(c->pointerMap));
  new (&(c->heapMap)) Segment::Map
    (&(c->gen2), 1, c->pageMap.scale * 1024, &(c->pageMap));

  new (&(c->gen2)) Segment(c, minimum, desired, &(c->heapMap));

  if (Verbose) {
    fprintf(stderr, "init gen2 to %d bytes\n",
            c->gen2.capacity() * BytesPerWord);
  }
}

void
initNextGen2(Context* c)
{
  unsigned minimum = MinimumGen2SizeInBytes / BytesPerWord;
  unsigned desired = max(minimum, avg(c->gen2.position(), c->gen2.capacity()));

  new (&(c->nextPointerMap)) Segment::Map(&(c->nextGen2));
  new (&(c->nextPageMap)) Segment::Map
    (&(c->nextGen2), 1, LikelyPageSizeInBytes / BytesPerWord,
     &(c->nextPointerMap));
  new (&(c->nextHeapMap)) Segment::Map
    (&(c->nextGen2), 1, c->pageMap.scale * 1024, &(c->nextPageMap));

  new (&(c->nextGen2)) Segment(c, minimum, desired, &(c->nextHeapMap));

  if (Verbose) {
    fprintf(stderr, "init nextGen2 to %d bytes\n",
            c->nextGen2.capacity() * BytesPerWord);
  }
}

inline bool
fresh(Context* c, object o)
{
  return c->nextGen1.contains(o)
    or c->nextGen2.contains(o)
    or (c->gen2.contains(o) and c->gen2.indexOf(o) >= c->gen2Base);
}

inline bool
wasCollected(Context* c, object o)
{
  return o and (not fresh(c, o)) and fresh(c, get(o, 0));
}

inline object
follow(Context* c, object o)
{
  assert(c, wasCollected(c, o));
  return cast<object>(o, 0);
}

inline object&
parent(Context* c, object o)
{
  assert(c, wasCollected(c, o));
  return cast<object>(o, BytesPerWord);
}

inline uintptr_t*
bitset(Context* c, object o)
{
  assert(c, wasCollected(c, o));
  return &cast<uintptr_t>(o, BytesPerWord * 2);
}

inline object
copyTo(Context* c, Segment* s, object o, unsigned size)
{
  if (s->remaining() < size) {
    s->ensure(size);

    if (Verbose) {
      if (s == &(c->gen2)) {
        fprintf(stderr, "grow gen2 to %d bytes\n",
                c->gen2.capacity() * BytesPerWord);
      } else if (s == &(c->nextGen1)) {
        fprintf(stderr, "grow nextGen1 to %d bytes\n",
                c->nextGen1.capacity() * BytesPerWord);
      } else if (s == &(c->nextGen2)) {
        fprintf(stderr, "grow nextGen2 to %d bytes\n",
                c->nextGen2.capacity() * BytesPerWord);
      } else {
        abort(c);
      }
    }
  }
  
  object dst = s->allocate(size);
  c->client->copy(o, dst);
  return dst;
}

object
copy2(Context* c, object o)
{
  unsigned size = c->client->copiedSizeInWords(o);

  if (c->gen2.contains(o)) {
    assert(c, c->mode == MajorCollection
           or c->mode == Gen2Collection);

    return copyTo(c, &(c->nextGen2), o, size);
  } else if (c->gen1.contains(o)) {
    unsigned age = c->ageMap.get(o);
    if (age == TenureThreshold) {
      if (c->mode == MinorCollection) {
        if (c->gen2.front == 0) initGen2(c);

        if (c->gen2.remaining() >= size) {
          if (c->gen2Base == Top) {
            c->gen2Base = c->gen2.position();
          }

          return copyTo(c, &(c->gen2), o, size);
        } else {
          if (Verbose) {
            fprintf(stderr, "overflow collection\n");
          }

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

  if (Debug) {
    fprintf(stderr, "copy %p (%s) to %p (%s)\n",
            o, segment(c, o), r, segment(c, r));
  }

  // leave a pointer to the copy in the original
  cast<object>(o, 0) = r;

  return r;
}

object
update3(Context* c, object o, bool* needsVisit)
{
  if (wasCollected(c, o)) {
    *needsVisit = false;
    return follow(c, o);
  } else {
    *needsVisit = true;
    return copy(c, o);
  }
}

object
update2(Context* c, object o, bool* needsVisit)
{
  switch (c->mode) {
  case MinorCollection:
  case OverflowCollection:
    if (c->gen2.contains(o)) {
      *needsVisit = false;
      return o;
    }
    break;
    
  case Gen2Collection:
    if (c->gen2.contains(o)) {
      return update3(c, o, needsVisit);
    } else {
      assert(c, c->nextGen1.contains(o) or c->nextGen2.contains(o));

      *needsVisit = false;
      return o;
    }
    break;

  default: break;
  }

  return update3(c, o, needsVisit);
}

object
update(Context* c, object* p, bool* needsVisit)
{
  if (mask(*p) == 0) {
    *needsVisit = false;
    return 0;
  }

  object r = update2(c, mask(*p), needsVisit);

  // update heap map.
  if (r) {
    if (c->mode == MinorCollection) {
      if (c->gen2.contains(p) and not c->gen2.contains(r)) {
        if (Debug) {        
          fprintf(stderr, "mark %p (%s) at %p (%s)\n",
                  r, segment(c, r), p, segment(c, p));
        }

        c->heapMap.set(p);
      }
    } else {
      if (c->nextGen2.contains(p) and not c->nextGen2.contains(r)) {
        if (Debug) {        
          fprintf(stderr, "mark %p (%s) at %p (%s)\n",
                  r, segment(c, r), p, segment(c, p));
        }

        c->nextHeapMap.set(p);
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
collect(Context* c, object* p)
{
  object original = mask(*p);
  object parent = 0;
  
  if (Debug) {
    fprintf(stderr, "update %p (%s) at %p (%s)\n",
            mask(*p), segment(c, *p), p, segment(c, p));
  }

  bool needsVisit;
  set(p, update(c, mask(p), &needsVisit));

  if (Debug) {
    fprintf(stderr, "  result: %p (%s) (visit? %d)\n",
            mask(*p), segment(c, *p), needsVisit);
  }

  if (not needsVisit) return;

 visit: {
    object copy = follow(c, original);

    class Walker : public Heap::Walker {
     public:
      Walker(Context* c, object copy, uintptr_t* bitset):
        c(c),
        copy(copy),
        bitset(bitset),
        first(0),
        second(0),
        last(0),
        visits(0),
        total(0)
      { }

      virtual bool visit(unsigned offset) {
        if (Debug) {
          fprintf(stderr, "  update %p (%s) at %p - offset %d from %p (%s)\n",
                  get(copy, offset),
                  segment(c, get(copy, offset)),
                  getp(copy, offset),
                  offset,
                  copy,
                  segment(c, copy));
        }

        bool needsVisit;
        object childCopy = update(c, getp(copy, offset), &needsVisit);
        
        if (Debug) {
          fprintf(stderr, "    result: %p (%s) (visit? %d)\n",
                  childCopy, segment(c, childCopy), needsVisit);
        }

        ++ total;

        if (total == 3) {
          bitsetInit(bitset);
        }

        if (needsVisit) {
          ++ visits;

          if (visits == 1) {
            first = offset;
          } else if (visits == 2) {
            second = offset;
          }
        } else {
          set(copy, offset, childCopy);
        }

        if (visits > 1 and total > 2 and (second or needsVisit)) {
          bitsetClear(bitset, last, offset);
          last = offset;

          if (second) {
            bitsetSet(bitset, second, true);
            second = 0;
          }
          
          if (needsVisit) {
            bitsetSet(bitset, offset, true);
          }
        }

        return true;
      }

      Context* c;
      object copy;
      uintptr_t* bitset;
      unsigned first;
      unsigned second;
      unsigned last;
      unsigned visits;
      unsigned total;
    } walker(c, copy, bitset(c, original));

    if (Debug) {
      fprintf(stderr, "walk %p (%s)\n", copy, segment(c, copy));
    }

    c->client->walk(copy, &walker);

    if (walker.visits) {
      // descend
      if (walker.visits > 1) {
        ::parent(c, original) = parent;
        parent = original;
      }

      original = get(copy, walker.first);
      set(copy, walker.first, follow(c, original));
      goto visit;
    } else {
      // ascend
      original = parent;
    }
  }

  if (original) {
    object copy = follow(c, original);

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
    } walker(c, bitset(c, original));

    if (Debug) {
      fprintf(stderr, "scan %p\n", copy);
    }

    c->client->walk(copy, &walker);

    assert(c, walker.total > 1);

    if (walker.total == 3 and bitsetHasMore(bitset(c, original))) {
      parent = original;
    } else {
      parent = ::parent(c, original);
    }

    if (Debug) {
      fprintf(stderr, "  next is %p (%s) at %p - offset %d from %p (%s)\n",
              get(copy, walker.next),
              segment(c, get(copy, walker.next)),
              getp(copy, walker.next),
              walker.next,
              copy,
              segment(c, copy));
    }

    original = get(copy, walker.next);
    set(copy, walker.next, follow(c, original));
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
      if (c->mode != OverflowCollection and childDirty) {
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

        if (c->mode != OverflowCollection and not c->gen2.contains(*p)) {
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
  Context* context;
  Segment::Chain* chain;
  unsigned index;
  unsigned end;
  bool dirty;

  ObjectSegmentIterator(Segment* segment, unsigned end):
    context(segment->context),
    chain(segment->front),
    index(0),
    end(end),
    dirty(false)
  { }

  bool hasNext() {
    if (dirty) {
      dirty = false;
      uintptr_t* p = chain->data() + index;
      index += context->client->sizeInWords(p);
    }

    if (chain and index == chain->position) {
      chain = chain->next;
      index = 0;
    }

    return chain and index + chain->offset < end;
  }

  object next() {
    dirty = true;
    return chain->data() + index;
  }
};

void
collect(Context* c, Segment* s, unsigned limit)
{
  for (ObjectSegmentIterator it(s, limit); it.hasNext();) {
    object p = it.next();

    class Walker : public Heap::Walker {
     public:
      Walker(Context* c, object p): c(c), p(p) { }

      virtual bool visit(unsigned offset) {
        collect(c, getp(p, offset));
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

    Context* c;
  } v(c);

  c->client->visitRoots(&v);
}

void
collect(Context* c)
{
  if (c->gen1.front == 0) initGen1(c);

  c->gen2Base = Top;

  switch (c->mode) {
  case MinorCollection: {
    initNextGen1(c);

    if (Verbose) {
      fprintf(stderr, "minor collection\n");
    }

    collect2(c);

    if (c->mode == OverflowCollection) {
      c->mode = Gen2Collection;

      if (Verbose) {
        fprintf(stderr, "gen2 collection\n");
      }

      c->gen2Base = Top;

      collect2(c);

      c->gen2.replaceWith(&(c->nextGen2));
    }

    c->gen1.replaceWith(&(c->nextGen1));
  } break;

  case MajorCollection: {
    initNextGen1(c);
    initNextGen2(c);
    
    c->heapMap.clear();

    if (Verbose) {
      fprintf(stderr, "major collection\n");
    }

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
      if (Debug) {        
        fprintf(stderr, "mark %p (%s) at %p (%s)\n",
                *p, segment(&c, *p), p, segment(&c, p));
      }

      c.heapMap.set(p);
    }

    virtual void dispose() {
      c.dispose();
      c.system->free(this);
    }

    virtual void* follow(void* p) {
      if (wasCollected(&c, p)) {
        if (Debug) {
          fprintf(stderr, "follow %p (%s) to %p (%s)\n",
                  p, segment(&c, p),
                  ::follow(&c, p), segment(&c, ::follow(&c, p)));
        }

        return ::follow(&c, p);
      } else {
        return p;
      }
    }

    Context c;
  };
  
  return new (system->allocate(sizeof(Heap))) Heap(system);
}

} // namespace vm
