/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "heap.h"
#include "heapwalk.h"
#include "common.h"
#include "machine.h"
#include "util.h"
#include "stream.h"
#include "assembler.h"
#include "target.h"
#include "binaryToObject/tools.h"
#include "lzma.h"

// since we aren't linking against libstdc++, we must implement this
// ourselves:
extern "C" void __cxa_pure_virtual(void) { abort(); }

using namespace vm;
using namespace avian::tools;

namespace {

const unsigned HeapCapacity = 512 * 1024 * 1024;

const unsigned TargetFixieSizeInBytes = 8 + (TargetBytesPerWord * 2);
const unsigned TargetFixieSizeInWords = ceiling
  (TargetFixieSizeInBytes, TargetBytesPerWord);
const unsigned TargetFixieAge = 0;
const unsigned TargetFixieFlags = 2;
const unsigned TargetFixieSize = 4;

const bool DebugNativeTarget = false;

enum Type {
  Type_none,
  Type_object,
  Type_object_nogc,
  Type_int8_t,
  Type_uint8_t,
  Type_int16_t,
  Type_uint16_t,
  Type_int32_t,
  Type_uint32_t,
  Type_intptr_t,
  Type_uintptr_t,
  Type_int64_t,
  Type_int64_t_pad,
  Type_uint64_t,
  Type_float,
  Type_double,
  Type_double_pad,
  Type_word,
  Type_array
};

class Field {
 public:
  Type type;
  unsigned buildOffset;
  unsigned buildSize;
  unsigned targetOffset;
  unsigned targetSize;
};

void
init(Field* f, Type type, unsigned buildOffset, unsigned buildSize,
     unsigned targetOffset, unsigned targetSize)
{
  f->type = type;
  f->buildOffset = buildOffset;
  f->buildSize = buildSize;
  f->targetOffset = targetOffset;
  f->targetSize = targetSize;
}

class TypeMap {
 public:
  enum Kind {
    NormalKind,
    SingletonKind,
    PoolKind
  };

  TypeMap(unsigned buildFixedSizeInWords, unsigned targetFixedSizeInWords,
          unsigned fixedFieldCount, Kind kind = NormalKind,
          unsigned buildArrayElementSizeInBytes = 0,
          unsigned targetArrayElementSizeInBytes = 0,
          Type arrayElementType = Type_none):
    buildFixedSizeInWords(buildFixedSizeInWords),
    targetFixedSizeInWords(targetFixedSizeInWords),
    fixedFieldCount(fixedFieldCount),
    buildArrayElementSizeInBytes(buildArrayElementSizeInBytes),
    targetArrayElementSizeInBytes(targetArrayElementSizeInBytes),
    arrayElementType(arrayElementType),
    kind(kind)
  { }

  uintptr_t* targetFixedOffsets() {
    return reinterpret_cast<uintptr_t*>(this + 1);
  }

  Field* fixedFields() {
    return reinterpret_cast<Field*>
      (targetFixedOffsets() + (buildFixedSizeInWords * BytesPerWord));
  }

  static unsigned sizeInBytes(unsigned buildFixedSizeInWords,
                              unsigned fixedFieldCount)
  {
    return sizeof(TypeMap)
      + (buildFixedSizeInWords * BytesPerWord * BytesPerWord)
      + (sizeof(Field) * fixedFieldCount);
  }

  unsigned buildFixedSizeInWords;
  unsigned targetFixedSizeInWords;
  unsigned fixedFieldCount;
  unsigned buildArrayElementSizeInBytes;
  unsigned targetArrayElementSizeInBytes;
  Type arrayElementType;
  Kind kind;
};

// Notes on immutable references in the heap image:
//
// One of the advantages of a bootimage-based build is that reduces
// the overhead of major GCs at runtime since we can avoid scanning
// the pre-built heap image entirely.  However, this only works if we
// can ensure that no part of the heap image (with exceptions noted
// below) ever points to runtime-allocated objects.  Therefore (most)
// references in the heap image are considered immutable, and any
// attempt to update them at runtime will cause the process to abort.
//
// However, some references in the heap image really must be updated
// at runtime: e.g. the static field table for each class.  Therefore,
// we allocate these as "fixed" objects, subject to mark-and-sweep
// collection, instead of as "copyable" objects subject to copying
// collection.  This strategy avoids the necessity of maintaining
// "dirty reference" bitsets at runtime for the entire heap image;
// each fixed object has its own bitset specific to that object.
//
// In addition to the "fixed" object solution, there are other
// strategies available to avoid attempts to update immutable
// references at runtime:
//
//  * Table-based: use a lazily-updated array or vector to associate
//    runtime data with heap image objects (see
//    e.g. getClassRuntimeData in machine.cpp).
//
//  * Update references at build time: for example, we set the names
//    of primitive classes before generating the heap image so that we
//    need not populate them lazily at runtime.

bool
endsWith(const char* suffix, const char* s, unsigned length)
{
  unsigned suffixLength = strlen(suffix);
  return length >= suffixLength
    and memcmp(suffix, s + (length - suffixLength), suffixLength) == 0;
}

object
getNonStaticFields(Thread* t, object typeMaps, object c, object fields,
                   unsigned* count, object* array)
{
  PROTECT(t, typeMaps);
  PROTECT(t, c);
  PROTECT(t, fields);

  *array = hashMapFind(t, typeMaps, c, objectHash, objectEqual);

  if (*array) {
    *count += reinterpret_cast<TypeMap*>(&byteArrayBody(t, *array, 0))
      ->fixedFieldCount;
  } else {
    if (classSuper(t, c)) {
      fields = getNonStaticFields
        (t, typeMaps, classSuper(t, c), fields, count, array);
    }

    if (classFieldTable(t, c)) {
      for (unsigned i = 0; i < arrayLength(t, classFieldTable(t, c)); ++i) {
        object field = arrayBody(t, classFieldTable(t, c), i);

        if ((fieldFlags(t, field) & ACC_STATIC) == 0) {
          ++ (*count);
          fields = vectorAppend(t, fields, field);
        }
      }
    }
  }

  return vectorAppend(t, fields, 0);
}

object
allFields(Thread* t, object typeMaps, object c, unsigned* count, object* array)
{
  PROTECT(t, typeMaps);
  PROTECT(t, c);

  object fields = makeVector(t, 0, 0);
  PROTECT(t, fields);

  *array = hashMapFind(t, typeMaps, c, objectHash, objectEqual);

  bool includeMembers;
  if (*array) {
    includeMembers = false;
    *count += reinterpret_cast<TypeMap*>(&byteArrayBody(t, *array, 0))
      ->fixedFieldCount;
  } else {
    includeMembers = true;
    if (classSuper(t, c)) {
      fields = getNonStaticFields
        (t, typeMaps, classSuper(t, c), fields, count, array);
    }
  }

  if (classFieldTable(t, c)) {
    for (unsigned i = 0; i < arrayLength(t, classFieldTable(t, c)); ++i) {
      object field = arrayBody(t, classFieldTable(t, c), i);

      if (includeMembers or (fieldFlags(t, field) & ACC_STATIC)) {
        ++ (*count);
        fields = vectorAppend(t, fields, field);
      }
    }
  }

  return fields;
}

TypeMap*
classTypeMap(Thread* t, object typeMaps, object p)
{
  return reinterpret_cast<TypeMap*>
    (&byteArrayBody
     (t, hashMapFind(t, typeMaps, p, objectHash, objectEqual), 0));
}

TypeMap*
typeMap(Thread* t, object typeMaps, object p)
{
  return reinterpret_cast<TypeMap*>
    (&byteArrayBody
     (t, objectClass(t, p) == type(t, Machine::SingletonType)
      ? hashMapFind(t, typeMaps, p, objectHash, objectEqual)
      : hashMapFind(t, typeMaps, objectClass(t, p), objectHash, objectEqual),
      0));
}

unsigned
targetFieldOffset(Thread* t, object typeMaps, object field)
{
  // if (strcmp(reinterpret_cast<const char*>
  //            (&byteArrayBody(t, className(t, fieldClass(t, field)), 0)),
  //            "java/lang/Throwable") == 0) trap();

  return ((fieldFlags(t, field) & ACC_STATIC)
          ? typeMap(t, typeMaps, classStaticTable(t, fieldClass(t, field)))
          : classTypeMap(t, typeMaps, fieldClass(t, field)))
    ->targetFixedOffsets()[fieldOffset(t, field)];
}

object
makeCodeImage(Thread* t, Zone* zone, BootImage* image, uint8_t* code,
              const char* className, const char* methodName,
              const char* methodSpec, object typeMaps)
{
  PROTECT(t, typeMaps);

  object constants = 0;
  PROTECT(t, constants);
  
  object calls = 0;
  PROTECT(t, calls);

  object methods = 0;
  PROTECT(t, methods);

  DelayedPromise* addresses = 0;

  class MyOffsetResolver: public OffsetResolver {
   public:
    MyOffsetResolver(object* typeMaps): typeMaps(typeMaps) { }

    virtual unsigned fieldOffset(Thread* t, object field) {
      return targetFieldOffset(t, *typeMaps, field);
    }

    object* typeMaps;
  } resolver(&typeMaps);

  Finder* finder = static_cast<Finder*>
    (systemClassLoaderFinder(t, root(t, Machine::BootLoader)));

  for (Finder::Iterator it(finder); it.hasMore();) {
    unsigned nameSize = 0;
    const char* name = it.next(&nameSize);

    if (endsWith(".class", name, nameSize)
        and (className == 0 or strncmp(name, className, nameSize - 6) == 0))
    {
      // fprintf(stderr, "pass 1 %.*s\n", nameSize - 6, name);
      object c = resolveSystemClass
        (t, root(t, Machine::BootLoader),
         makeByteArray(t, "%.*s", nameSize - 6, name), true);

      PROTECT(t, c);

      System::Region* region = finder->find(name);
      
      { THREAD_RESOURCE(t, System::Region*, region, region->dispose());

        class Client: public Stream::Client {
         public:
          Client(Thread* t): t(t) { }

          virtual void NO_RETURN handleError() {
            vm::abort(t);
          }

         private:
          Thread* t;
        } client(t);

        Stream s(&client, region->start(), region->length());

        uint32_t magic = s.read4();
        expect(t, magic == 0xCAFEBABE);
        s.read2(); // minor version
        s.read2(); // major version

        unsigned count = s.read2() - 1;
        if (count) {
          Type types[count + 2];
          types[0] = Type_object;
          types[1] = Type_intptr_t;

          for (unsigned i = 2; i < count + 2; ++i) {
            switch (s.read1()) {
            case CONSTANT_Class:
            case CONSTANT_String:
              types[i] = Type_object;
              s.skip(2);
              break;

            case CONSTANT_Integer:
            case CONSTANT_Float:
              types[i] = Type_int32_t;
              s.skip(4);
              break;

            case CONSTANT_NameAndType:
            case CONSTANT_Fieldref:
            case CONSTANT_Methodref:
            case CONSTANT_InterfaceMethodref:
              types[i] = Type_object;
              s.skip(4);
              break;

            case CONSTANT_Long:
              types[i++] = Type_int64_t;
              types[i] = Type_int64_t_pad;
              s.skip(8);
              break;

            case CONSTANT_Double:
              types[i++] = Type_double;
              types[i] = Type_double_pad;
              s.skip(8);
              break;

            case CONSTANT_Utf8:
              types[i] = Type_object;
              s.skip(s.read2());
              break;

            default: abort(t);
            }
          }

          object array = makeByteArray
            (t, TypeMap::sizeInBytes(count + 2, count + 2));

          TypeMap* map = new (&byteArrayBody(t, array, 0)) TypeMap
            (count + 2, count + 2, count + 2, TypeMap::PoolKind);

          for (unsigned i = 0; i < count + 2; ++i) {
            expect(t, i < map->buildFixedSizeInWords);

            map->targetFixedOffsets()[i * BytesPerWord]
              = i * TargetBytesPerWord;

            init(new (map->fixedFields() + i) Field, types[i],
                 i * BytesPerWord, BytesPerWord, i * TargetBytesPerWord,
                 TargetBytesPerWord);
          }

          hashMapInsert
            (t, typeMaps, hashMapFind
             (t, root(t, Machine::PoolMap), c, objectHash, objectEqual), array,
             objectHash);
        }
      }

      { object array = 0;
        PROTECT(t, array);

        unsigned count = 0;
        object fields = allFields(t, typeMaps, c, &count, &array);
        PROTECT(t, fields);

        Field memberFields[count + 1];

        unsigned memberIndex;
        unsigned buildMemberOffset;
        unsigned targetMemberOffset;

        if (array) {
          memberIndex = 0;
          buildMemberOffset = 0;
          targetMemberOffset = 0;

          TypeMap* map = reinterpret_cast<TypeMap*>
            (&byteArrayBody(t, array, 0));

          for (unsigned j = 0; j < map->fixedFieldCount; ++j) {
            Field* f = map->fixedFields() + j;

            memberFields[memberIndex] = *f;

            targetMemberOffset = f->targetOffset + f->targetSize;

            ++ memberIndex;
          }
        } else {
          init(new (memberFields) Field, Type_object, 0, BytesPerWord, 0,
               TargetBytesPerWord);

          memberIndex = 1;
          buildMemberOffset = BytesPerWord;
          targetMemberOffset = TargetBytesPerWord;
        }

        const unsigned StaticHeader = 3;

        Field staticFields[count + StaticHeader];
        
        init(new (staticFields) Field, Type_object, 0, BytesPerWord, 0,
             TargetBytesPerWord);

        init(new (staticFields + 1) Field, Type_intptr_t, BytesPerWord,
             BytesPerWord, TargetBytesPerWord, TargetBytesPerWord);

        init(new (staticFields + 2) Field, Type_object, BytesPerWord * 2,
             BytesPerWord, TargetBytesPerWord * 2, TargetBytesPerWord);

        unsigned staticIndex = StaticHeader;
        unsigned buildStaticOffset = BytesPerWord * StaticHeader;
        unsigned targetStaticOffset = TargetBytesPerWord * StaticHeader;

        for (unsigned i = 0; i < vectorSize(t, fields); ++i) {
          object field = vectorBody(t, fields, i);
          if (field) {
            unsigned buildSize = fieldSize(t, fieldCode(t, field));
            unsigned targetSize = buildSize;

            Type type;
            switch (fieldCode(t, field)) {
            case ObjectField:
              type = Type_object;
              targetSize = TargetBytesPerWord;
              break;

            case ByteField:
            case BooleanField:
              type = Type_int8_t;
              break;

            case CharField:
            case ShortField:
              type = Type_int8_t;
              break;

            case FloatField:
            case IntField:
              type = Type_int32_t;
              break;

            case LongField:
            case DoubleField:
              type = Type_int64_t;
              break;

            default: abort(t);
            }

            if (fieldFlags(t, field) & ACC_STATIC) {
              while (targetStaticOffset % targetSize) {
                ++ targetStaticOffset;
              }

              buildStaticOffset = fieldOffset(t, field);

              init(new (staticFields + staticIndex) Field, type,
                   buildStaticOffset, buildSize, targetStaticOffset,
                   targetSize);

              targetStaticOffset += targetSize;

              ++ staticIndex;
            } else {
              while (targetMemberOffset % targetSize) {
                ++ targetMemberOffset;
              }

              buildMemberOffset = fieldOffset(t, field);

              init(new (memberFields + memberIndex) Field, type,
                   buildMemberOffset, buildSize, targetMemberOffset,
                   targetSize);

              targetMemberOffset += targetSize;

              ++ memberIndex;
            }
          } else {
            targetMemberOffset = pad(targetMemberOffset, TargetBytesPerWord);
          }
        }
     
        if (hashMapFind(t, typeMaps, c, objectHash, objectEqual) == 0) {
          object array = makeByteArray
            (t, TypeMap::sizeInBytes
             (ceiling(classFixedSize(t, c), BytesPerWord), memberIndex));

          TypeMap* map = new (&byteArrayBody(t, array, 0)) TypeMap
            (ceiling(classFixedSize(t, c), BytesPerWord),
             ceiling(targetMemberOffset, TargetBytesPerWord), memberIndex);

          for (unsigned i = 0; i < memberIndex; ++i) {
            Field* f = memberFields + i;

            expect(t, f->buildOffset
                   < map->buildFixedSizeInWords * BytesPerWord);

            map->targetFixedOffsets()[f->buildOffset] = f->targetOffset;

            map->fixedFields()[i] = *f;
          }

          hashMapInsert(t, typeMaps, c, array, objectHash);
        }

        if (classStaticTable(t, c)) {
          object array = makeByteArray
            (t, TypeMap::sizeInBytes
             (singletonCount(t, classStaticTable(t, c)) + 2, staticIndex));

          TypeMap* map = new (&byteArrayBody(t, array, 0)) TypeMap
            (singletonCount(t, classStaticTable(t, c)) + 2,
             ceiling(targetStaticOffset, TargetBytesPerWord), staticIndex,
             TypeMap::SingletonKind);

          for (unsigned i = 0; i < staticIndex; ++i) {
            Field* f = staticFields + i;

            expect(t, f->buildOffset
                   < map->buildFixedSizeInWords * BytesPerWord);

            map->targetFixedOffsets()[f->buildOffset] = f->targetOffset;

            map->fixedFields()[i] = *f;
          }

          hashMapInsert
            (t, typeMaps, classStaticTable(t, c), array, objectHash);
        }
      }
    }
  }

  for (Finder::Iterator it(finder); it.hasMore();) {
    unsigned nameSize = 0;
    const char* name = it.next(&nameSize);

    if (endsWith(".class", name, nameSize)
        and (className == 0 or strncmp(name, className, nameSize - 6) == 0))
    {
      // fprintf(stderr, "pass 2 %.*s\n", nameSize - 6, name);
      object c = resolveSystemClass
        (t, root(t, Machine::BootLoader),
         makeByteArray(t, "%.*s", nameSize - 6, name), true);

      PROTECT(t, c);

      if (classMethodTable(t, c)) {
        for (unsigned i = 0; i < arrayLength(t, classMethodTable(t, c)); ++i) {
          object method = arrayBody(t, classMethodTable(t, c), i);
          if (((methodName == 0
                or ::strcmp
                (reinterpret_cast<char*>
                 (&byteArrayBody
                  (t, vm::methodName(t, method), 0)), methodName) == 0)
               and (methodSpec == 0
                    or ::strcmp
                    (reinterpret_cast<char*>
                     (&byteArrayBody
                      (t, vm::methodSpec(t, method), 0)), methodSpec)
                    == 0)))
          {
            if (methodCode(t, method)
                or (methodFlags(t, method) & ACC_NATIVE))
            {
              PROTECT(t, method);

              t->m->processor->compileMethod
                (t, zone, &constants, &calls, &addresses, method, &resolver);

              if (methodCode(t, method)) {
                methods = makePair(t, method, methods);
              }
            }

            object addendum = methodAddendum(t, method);
            if (addendum and methodAddendumExceptionTable(t, addendum)) {
              PROTECT(t, addendum);

              // resolve exception types now to avoid trying to update
              // immutable references at runtime
              for (unsigned i = 0; i < shortArrayLength
                     (t, methodAddendumExceptionTable(t, addendum)); ++i)
              {
                uint16_t index = shortArrayBody
                  (t, methodAddendumExceptionTable(t, addendum), i) - 1;

                object o = singletonObject
                  (t, addendumPool(t, addendum), index);

                if (objectClass(t, o) == type(t, Machine::ReferenceType)) {
                  o = resolveClass
                    (t, root(t, Machine::BootLoader), referenceName(t, o));
    
                  set(t, addendumPool(t, addendum),
                      SingletonBody + (index * BytesPerWord), o);
                }
              }
            }
          }
        }
      }
    }
  }

  for (; calls; calls = tripleThird(t, calls)) {
    object method = tripleFirst(t, calls);
    uintptr_t address;
    if (methodFlags(t, method) & ACC_NATIVE) {
      address = reinterpret_cast<uintptr_t>(code + image->thunks.native.start);
    } else {
      address = codeCompiled(t, methodCode(t, method));
    }

    static_cast<ListenPromise*>(pointerValue(t, tripleSecond(t, calls)))
      ->listener->resolve(address, 0);
  }

  for (; addresses; addresses = addresses->next) {
    uint8_t* value = reinterpret_cast<uint8_t*>(addresses->basis->value());
    expect(t, value >= code);

    addresses->listener->resolve
      (static_cast<target_intptr_t>(value - code), 0);
  }

  for (; methods; methods = pairSecond(t, methods)) {
    codeCompiled(t, methodCode(t, pairFirst(t, methods)))
      -= reinterpret_cast<uintptr_t>(code);
  }

  t->m->processor->normalizeVirtualThunks(t);

  return constants;
}

void
visitRoots(Thread* t, BootImage* image, HeapWalker* w, object constants)
{
  Machine* m = t->m;

  for (HashMapIterator it(t, classLoaderMap(t, root(t, Machine::BootLoader)));
       it.hasMore();)
  {
    w->visitRoot(tripleSecond(t, it.next()));
  }

  image->bootLoader = w->visitRoot(root(t, Machine::BootLoader));
  image->appLoader = w->visitRoot(root(t, Machine::AppLoader));
  image->types = w->visitRoot(m->types);

  m->processor->visitRoots(t, w);

  for (; constants; constants = tripleThird(t, constants)) {
    w->visitRoot(tripleFirst(t, constants));
  }
}

unsigned
targetOffset(Thread* t, object typeMaps, object p, unsigned offset)
{
  TypeMap* map = typeMap(t, typeMaps, p);

  if (map->targetArrayElementSizeInBytes
      and offset >= map->buildFixedSizeInWords * BytesPerWord)
  {
    return (map->targetFixedSizeInWords * TargetBytesPerWord)
      + (((offset - (map->buildFixedSizeInWords * BytesPerWord))
          / map->buildArrayElementSizeInBytes)
         * map->targetArrayElementSizeInBytes);
  } else {
    return map->targetFixedOffsets()[offset];
  }
}

unsigned
targetSize(Thread* t, object typeMaps, object p)
{
  TypeMap* map = typeMap(t, typeMaps, p);

  if (map->targetArrayElementSizeInBytes) {
    return map->targetFixedSizeInWords
      + ceiling(map->targetArrayElementSizeInBytes
                * cast<uintptr_t>
                (p, (map->buildFixedSizeInWords - 1) * BytesPerWord),
                TargetBytesPerWord);
  } else {
    switch (map->kind) {
    case TypeMap::NormalKind:
      return map->targetFixedSizeInWords;

    case TypeMap::SingletonKind:
      return map->targetFixedSizeInWords + singletonMaskSize
        (map->targetFixedSizeInWords - 2, TargetBitsPerWord);

    case TypeMap::PoolKind: {
      unsigned maskSize = poolMaskSize
        (map->targetFixedSizeInWords - 2, TargetBitsPerWord);

      return map->targetFixedSizeInWords + maskSize + singletonMaskSize
        (map->targetFixedSizeInWords - 2 + maskSize, TargetBitsPerWord);
    }

    default: abort(t);
    }
  }
}

unsigned
objectMaskCount(TypeMap* map)
{
  unsigned count = map->targetFixedSizeInWords;

  if (map->targetArrayElementSizeInBytes) {
    ++ count;
  }

  return count;
}

unsigned
targetSize(Thread* t, object typeMaps, object referer, unsigned refererOffset,
           object p)
{
  if (referer
      and objectClass(t, referer) == type(t, Machine::ClassType)
      and (refererOffset * BytesPerWord) == ClassObjectMask)
  {
    return (TargetBytesPerWord * 2)
      + pad
      (ceiling
       (objectMaskCount
        (classTypeMap(t, typeMaps, referer)), 32) * 4, TargetBytesPerWord);
  } else {
    return targetSize(t, typeMaps, p);
  }
}

void
copy(Thread* t, uint8_t* src, uint8_t* dst, Type type)
{
  switch (type) {
  case Type_int8_t:
    memcpy(dst, src, 1);
    break;

  case Type_int16_t: {
    int16_t s; memcpy(&s, src, 2);
    int16_t d = targetV2(s);
    memcpy(dst, &d, 2);
  } break;

  case Type_int32_t:
  case Type_float: {
    int32_t s; memcpy(&s, src, 4);
    int32_t d = targetV4(s);
    memcpy(dst, &d, 4);
  } break;

  case Type_int64_t:
  case Type_double: {
    int64_t s; memcpy(&s, src, 8);
    int64_t d = targetV8(s);
    memcpy(dst, &d, 8);
  } break;

  case Type_int64_t_pad:
  case Type_double_pad:
    break;

  case Type_intptr_t: {
    intptr_t s; memcpy(&s, src, BytesPerWord);
    target_intptr_t d = targetVW(s);
    memcpy(dst, &d, TargetBytesPerWord);
  } break;

  case Type_object: {
    memset(dst, 0, TargetBytesPerWord);
  } break;

  default: abort(t);
  }
}

bool
nonObjectsEqual(uint8_t* src, uint8_t* dst, Type type)
{
  switch (type) {
  case Type_int8_t:
    return memcmp(dst, src, 1) == 0;

  case Type_int16_t:
    return memcmp(dst, src, 2) == 0;

  case Type_int32_t:
  case Type_float:
    return memcmp(dst, src, 4) == 0;

  case Type_int64_t:
  case Type_double:
    return memcmp(dst, src, 8) == 0;

  case Type_int64_t_pad:
  case Type_double_pad:
    return true;

  case Type_intptr_t:
    return memcmp(dst, src, BytesPerWord) == 0;

  case Type_object:
  case Type_object_nogc:
    return true;

  default: abort();
  }  
}

bool
nonObjectsEqual(TypeMap* map, uint8_t* src, uint8_t* dst)
{
  for (unsigned i = 0; i < map->fixedFieldCount; ++i) {
    Field* field = map->fixedFields() + i;
    if (not nonObjectsEqual
        (src + field->buildOffset, dst + field->targetOffset, field->type))
    {
      return false;
    }
  }

  if (map->targetArrayElementSizeInBytes) {
    unsigned fixedSize = map->buildFixedSizeInWords * BytesPerWord;
    unsigned count = cast<uintptr_t>(src, fixedSize - BytesPerWord);

    for (unsigned i = 0; i < count; ++i) {
      if (not nonObjectsEqual
          (src + fixedSize + (i * map->buildArrayElementSizeInBytes),
           dst + (map->targetFixedSizeInWords * TargetBytesPerWord)
           + (i * map->targetArrayElementSizeInBytes), map->arrayElementType))
      {
        return false;
      }
    }
  }

  return true;
}

void
copy(Thread* t, object typeMaps, object p, uint8_t* dst)
{
  TypeMap* map = typeMap(t, typeMaps, p);
  
  uint8_t* src = reinterpret_cast<uint8_t*>(p);

  for (unsigned i = 0; i < map->fixedFieldCount; ++i) {
    Field* field = map->fixedFields() + i;
    if (field->type > Type_array) abort(t);
    copy(t, src + field->buildOffset, dst + field->targetOffset, field->type);
  }

  if (map->targetArrayElementSizeInBytes) {
    unsigned fixedSize = map->buildFixedSizeInWords * BytesPerWord;
    unsigned count = cast<uintptr_t>(p, fixedSize - BytesPerWord);

    for (unsigned i = 0; i < count; ++i) {
      copy(t, src + fixedSize + (i * map->buildArrayElementSizeInBytes),
           dst + (map->targetFixedSizeInWords * TargetBytesPerWord)
           + (i * map->targetArrayElementSizeInBytes), map->arrayElementType);
    }

    if (objectClass(t, p) == type(t, Machine::ClassType)) {
      uint16_t fixedSize;
      uint8_t arrayElementSize;
      object array = hashMapFind(t, typeMaps, p, objectHash, objectEqual);

      if (array) {
        TypeMap* classMap = reinterpret_cast<TypeMap*>
          (&byteArrayBody(t, array, 0));

        fixedSize = targetV2
          (classMap->targetFixedSizeInWords * TargetBytesPerWord);

        arrayElementSize = classMap->targetArrayElementSizeInBytes;
      } else if (classFixedSize(t, p) == BytesPerWord * 2
                 and classArrayElementSize(t, p) == BytesPerWord)
      {
        fixedSize = targetV2(TargetBytesPerWord * 2);

        arrayElementSize = TargetBytesPerWord;
      } else {
        fixedSize = 0;
        arrayElementSize = 0;
      }
  
      if (fixedSize) {
        memcpy(dst + TargetClassFixedSize, &fixedSize, 2);

        memcpy(dst + TargetClassArrayElementSize, &arrayElementSize, 1);
      }
      
      // if (strcmp("vm::lineNumberTable",
      //            reinterpret_cast<const char*>(&byteArrayBody(t, className(t, p), 0))) == 0) trap();
    }
  } else {
    switch (map->kind) {
    case TypeMap::NormalKind:
      if (objectClass(t, p) == type(t, Machine::FieldType)) {
        uint16_t offset = targetV2(targetFieldOffset(t, typeMaps, p));
        memcpy(dst + TargetFieldOffset, &offset, 2);
      }
      break;

    case TypeMap::SingletonKind: {
      unsigned maskSize = singletonMaskSize
        (map->targetFixedSizeInWords - 2, TargetBitsPerWord);

      target_uintptr_t targetLength = targetVW
        (map->targetFixedSizeInWords - 2 + maskSize);
      memcpy(dst + TargetBytesPerWord, &targetLength, TargetBytesPerWord);

      uint8_t* mask = dst + (map->targetFixedSizeInWords * TargetBytesPerWord);
      memset(mask, 0, maskSize * TargetBytesPerWord);

      for (unsigned i = 0; i < map->fixedFieldCount; ++i) {
        Field* field = map->fixedFields() + i;
        if (field->type == Type_object) {
          unsigned offset = field->targetOffset / TargetBytesPerWord;
          reinterpret_cast<uint32_t*>(mask)[offset / 32]
            |= targetV4(static_cast<uint32_t>(1) << (offset % 32));
        }
      }

      if (DebugNativeTarget) {
        expect
          (t, memcmp
           (src + (map->targetFixedSizeInWords * TargetBytesPerWord), mask,
            singletonMaskSize
            (map->targetFixedSizeInWords - 2, TargetBitsPerWord)
            * TargetBytesPerWord) == 0);
      }
    } break;

    case TypeMap::PoolKind: {
      unsigned poolMaskSize = vm::poolMaskSize
        (map->targetFixedSizeInWords - 2, TargetBitsPerWord);

      unsigned objectMaskSize = singletonMaskSize
        (map->targetFixedSizeInWords - 2 + poolMaskSize, TargetBitsPerWord);

      target_uintptr_t targetLength = targetVW
        (map->targetFixedSizeInWords - 2 + poolMaskSize + objectMaskSize);
      memcpy(dst + TargetBytesPerWord, &targetLength, TargetBytesPerWord);

      uint8_t* poolMask = dst
        + (map->targetFixedSizeInWords * TargetBytesPerWord);

      memset(poolMask, 0, poolMaskSize * TargetBytesPerWord);

      uint8_t* objectMask = dst
        + ((map->targetFixedSizeInWords + poolMaskSize) * TargetBytesPerWord);

      memset(objectMask, 0, objectMaskSize * TargetBytesPerWord);

      for (unsigned i = 0; i < map->fixedFieldCount; ++i) {
        Field* field = map->fixedFields() + i;
        switch (field->type) {
        case Type_object:
          reinterpret_cast<uint32_t*>(objectMask)[i / 32]
            |= targetV4(static_cast<uint32_t>(1) << (i % 32));
          break;

        case Type_float:
        case Type_double:
          reinterpret_cast<target_uintptr_t*>(poolMask)
            [i / TargetBitsPerWord]
            |= targetVW
            (static_cast<target_uintptr_t>(1) << (i % TargetBitsPerWord));
          break;

        default:
          break;
        }
      }

      if (DebugNativeTarget) {
        expect
          (t, memcmp
           (src + (map->targetFixedSizeInWords * TargetBytesPerWord), poolMask,
            (poolMaskSize + singletonMaskSize
             (map->targetFixedSizeInWords - 2 + poolMaskSize,
              TargetBitsPerWord))
            * TargetBytesPerWord) == 0);
      }
    } break;

    default: abort(t);
    }
  }
}

void
copy(Thread* t, object typeMaps, object referer, unsigned refererOffset,
     object p, uint8_t* dst)
{
  if (referer
      and objectClass(t, referer) == type(t, Machine::ClassType)
      and (refererOffset * BytesPerWord) == ClassObjectMask)
  {
    TypeMap* map = classTypeMap(t, typeMaps, referer);

    memset(dst, 0, TargetBytesPerWord);

    unsigned length = ceiling(objectMaskCount(map), 32);

    target_uintptr_t targetLength = targetVW(length);

    memcpy(dst + TargetBytesPerWord, &targetLength, TargetBytesPerWord);

    memset(dst + (TargetBytesPerWord * 2), 0, length * 4);

    for (unsigned i = 0; i < map->fixedFieldCount; ++i) {
      Field* field = map->fixedFields() + i;
      if (field->type == Type_object) {
        unsigned offset = field->targetOffset / TargetBytesPerWord;
        reinterpret_cast<uint32_t*>(dst + (TargetBytesPerWord * 2))
          [offset / 32] |= targetV4(static_cast<uint32_t>(1) << (offset % 32));
      }
    }

    if (map->targetArrayElementSizeInBytes
        and map->arrayElementType == Type_object)
    {
      unsigned offset = map->targetFixedSizeInWords;
      reinterpret_cast<uint32_t*>(dst + (TargetBytesPerWord * 2))
        [offset / 32] |= targetV4(static_cast<uint32_t>(1) << (offset % 32));
    }
  } else {
    copy(t, typeMaps, p, dst);
  }

  if (DebugNativeTarget) {
    expect(t, targetSize(t, typeMaps, p) == baseSize(t, p, objectClass(t, p)));
    expect(t, nonObjectsEqual
           (typeMap(t, typeMaps, p), reinterpret_cast<uint8_t*>(p), dst));
  }
}

HeapWalker*
makeHeapImage(Thread* t, BootImage* image, target_uintptr_t* heap,
              target_uintptr_t* map, unsigned capacity, object constants,
              object typeMaps)
{
  class Visitor: public HeapVisitor {
   public:
    Visitor(Thread* t, object typeMaps, target_uintptr_t* heap,
            target_uintptr_t* map, unsigned capacity):
      t(t), typeMaps(typeMaps), currentObject(0), currentNumber(0),
      currentOffset(0), heap(heap), map(map), position(0), capacity(capacity)
    { }

    void visit(unsigned number) {
      if (currentObject) {
        if (DebugNativeTarget) {
          expect
            (t, targetOffset
             (t, typeMaps, currentObject, currentOffset * BytesPerWord)
             == currentOffset * BytesPerWord);
        }

        unsigned offset = currentNumber - 1
          + (targetOffset
             (t, typeMaps, currentObject, currentOffset * BytesPerWord)
             / TargetBytesPerWord);

        unsigned mark = heap[offset] & (~TargetPointerMask);
        unsigned value = number | (mark << TargetBootShift);

        if (value) targetMarkBit(map, offset);

        heap[offset] = targetVW(value);
      }
    }

    virtual void root() {
      currentObject = 0;
    }

    virtual unsigned visitNew(object p) {
      if (p) {
        unsigned size = targetSize
          (t, typeMaps, currentObject, currentOffset, p);

        unsigned number;
        if ((currentObject
             and objectClass(t, currentObject) == type(t, Machine::ClassType)
             and (currentOffset * BytesPerWord) == ClassStaticTable)
            or instanceOf(t, type(t, Machine::SystemClassLoaderType), p))
        {
          // Static tables and system classloaders must be allocated
          // as fixed objects in the heap image so that they can be
          // marked as dirty and visited during GC.  Otherwise,
          // attempts to update references in these objects to point
          // to runtime-allocated memory would fail because we don't
          // scan non-fixed objects in the heap image during GC.

          target_uintptr_t* dst = heap + position + TargetFixieSizeInWords;

          unsigned maskSize = ceiling(size, TargetBitsPerWord);

          unsigned total = TargetFixieSizeInWords + size + maskSize;

          expect(t, position + total < capacity);

          memset(heap + position, 0, TargetFixieSizeInBytes);

          uint16_t age = targetV2(FixieTenureThreshold + 1);
          memcpy(reinterpret_cast<uint8_t*>(heap + position)
                 + TargetFixieAge, &age, 2);

          uint16_t flags = targetV2(1);
          memcpy(reinterpret_cast<uint8_t*>(heap + position)
                 + TargetFixieFlags, &flags, 2);

          uint32_t targetSize = targetV4(size);
          memcpy(reinterpret_cast<uint8_t*>(heap + position)
                 + TargetFixieSize, &targetSize, 4);

          copy(t, typeMaps, currentObject, currentOffset, p,
               reinterpret_cast<uint8_t*>(dst));

          dst[0] |= FixedMark;

          memset(heap + position + TargetFixieSizeInWords + size, 0,
                 maskSize * TargetBytesPerWord);

          number = (dst - heap) + 1;
          position += total;
        } else {
          expect(t, position + size < capacity);

          copy(t, typeMaps, currentObject, currentOffset, p,
               reinterpret_cast<uint8_t*>(heap + position));

          number = position + 1;
          position += size;
        }

        visit(number);

        return number;
      } else {
        return 0;
      }
    }

    virtual void visitOld(object, unsigned number) {
      visit(number);
    }

    virtual void push(object object, unsigned number, unsigned offset) {
      currentObject = object;
      currentNumber = number;
      currentOffset = offset;
    }

    virtual void pop() {
      currentObject = 0;
    }

    Thread* t;
    object typeMaps;
    object currentObject;
    unsigned currentNumber;
    unsigned currentOffset;
    target_uintptr_t* heap;
    target_uintptr_t* map;
    unsigned position;
    unsigned capacity;
  } visitor(t, typeMaps, heap, map, capacity / TargetBytesPerWord);

  HeapWalker* w = makeHeapWalker(t, &visitor);
  visitRoots(t, image, w, constants);
  
  image->heapSize = visitor.position * TargetBytesPerWord;

  return w;
}

void
updateConstants(Thread* t, object constants, HeapMap* heapTable)
{
  for (; constants; constants = tripleThird(t, constants)) {
    unsigned target = heapTable->find(tripleFirst(t, constants));
    expect(t, target > 0);

    for (Promise::Listener* pl = static_cast<ListenPromise*>
           (pointerValue(t, tripleSecond(t, constants)))->listener;
         pl; pl = pl->next)
    {
      pl->resolve((target - 1) * TargetBytesPerWord, 0);
    }
  }
}

BootImage::Thunk
targetThunk(BootImage::Thunk t)
{
  return BootImage::Thunk
    (targetV4(t.start), targetV4(t.frameSavedOffset), targetV4(t.length));
}

void
writeBootImage2(Thread* t, OutputStream* bootimageOutput, OutputStream* codeOutput,
                BootImage* image, uint8_t* code, const char* className,
                const char* methodName, const char* methodSpec,
                const char* bootimageStart, const char* bootimageEnd,
                const char* codeimageStart, const char* codeimageEnd,
                bool useLZMA)
{
  setRoot(t, Machine::OutOfMemoryError,
          make(t, type(t, Machine::OutOfMemoryErrorType)));

  Zone zone(t->m->system, t->m->heap, 64 * 1024);

  class MyCompilationHandler : public Processor::CompilationHandler {
   public:

    String heapDup(const char* name) {
      String ret(name);
      char* n = (char*)heap->allocate(ret.length + 1);
      memcpy(n, ret.text, ret.length + 1);
      ret.text = n;
      return ret;
    }

    virtual void compiled(const void* code, unsigned size UNUSED, unsigned frameSize UNUSED, const char* name) {
      uint64_t offset = reinterpret_cast<uint64_t>(code) - codeOffset;
      symbols.add(SymbolInfo(offset, heapDup(name)));
      // printf("%ld %ld %s.%s%s\n", offset, offset + size, class_, name, spec);
    }

    virtual void dispose() {}

    DynamicArray<SymbolInfo> symbols;
    uint64_t codeOffset;
    Heap* heap;

    MyCompilationHandler(uint64_t codeOffset, Heap* heap):
      codeOffset(codeOffset),
      heap(heap) {}

  } compilationHandler(reinterpret_cast<uint64_t>(code), t->m->heap);

  t->m->processor->addCompilationHandler(&compilationHandler);

  object classPoolMap;
  object typeMaps;
  object constants;

  { classPoolMap = makeHashMap(t, 0, 0);
    PROTECT(t, classPoolMap);

    setRoot(t, Machine::PoolMap, classPoolMap);

    typeMaps = makeHashMap(t, 0, 0);
    PROTECT(t, typeMaps);

#include "type-maps.cpp"

    for (unsigned i = 0; i < arrayLength(t, t->m->types); ++i) {
      Type* source = types[i];
      unsigned count = 0;
      while (source[count] != Type_none) {
        ++ count;
      }
      ++ count;

      Field fields[count];

      init(new (fields) Field, Type_object, 0, BytesPerWord, 0,
           TargetBytesPerWord);

      unsigned buildOffset = BytesPerWord;
      unsigned targetOffset = TargetBytesPerWord;
      bool sawArray = false;
      Type type = Type_none;
      unsigned buildSize = 0;
      unsigned targetSize = 0;
      for (unsigned j = 1; j < count; ++j) {
        switch (source[j - 1]) {
        case Type_object:
          type = Type_object;
          buildSize = BytesPerWord;
          targetSize = TargetBytesPerWord;
          break;

        case Type_object_nogc:
          type = Type_object_nogc;
          buildSize = BytesPerWord;
          targetSize = TargetBytesPerWord;
          break;

        case Type_word:
        case Type_intptr_t:
        case Type_uintptr_t:
          type = Type_intptr_t;
          buildSize = BytesPerWord;
          targetSize = TargetBytesPerWord;
          break;

        case Type_int8_t:
        case Type_uint8_t:
          type = Type_int8_t;
          buildSize = targetSize = 1;
          break;

        case Type_int16_t:
        case Type_uint16_t:
          type = Type_int16_t;
          buildSize = targetSize = 2;
          break;

        case Type_int32_t:
        case Type_uint32_t:
        case Type_float:
          type = Type_int32_t;
          buildSize = targetSize = 4;
          break;

        case Type_int64_t:
        case Type_uint64_t:
        case Type_double:
          type = Type_int64_t;
          buildSize = targetSize = 8;
          break;

        case Type_array:
          type = Type_none;
          buildSize = targetSize = 0;
          break;

        default: abort(t);
        }

        if (source[j - 1] == Type_array) {
          sawArray = true;
        }

        if (not sawArray) {
          while (buildOffset % buildSize) {
            ++ buildOffset;
          }

          while (targetOffset % targetSize) {
            ++ targetOffset;
          }

          init(new (fields + j) Field, type, buildOffset, buildSize,
               targetOffset, targetSize);

          buildOffset += buildSize;
          targetOffset += targetSize;
        }
      }

      unsigned fixedFieldCount;
      Type arrayElementType;
      unsigned buildArrayElementSize;
      unsigned targetArrayElementSize;
      if (sawArray) {
        fixedFieldCount = count - 2;
        arrayElementType = type;
        buildArrayElementSize = buildSize; 
        targetArrayElementSize = targetSize;
      } else {
        fixedFieldCount = count;
        arrayElementType = Type_none;
        buildArrayElementSize = 0;
        targetArrayElementSize = 0;
      }

      object array = makeByteArray
        (t, TypeMap::sizeInBytes
         (ceiling(buildOffset, BytesPerWord), fixedFieldCount));

      TypeMap* map = new (&byteArrayBody(t, array, 0)) TypeMap
        (ceiling(buildOffset, BytesPerWord),
         ceiling(targetOffset, TargetBytesPerWord),
         fixedFieldCount, TypeMap::NormalKind, buildArrayElementSize,
         targetArrayElementSize, arrayElementType);

      for (unsigned j = 0; j < fixedFieldCount; ++j) {
        Field* f = fields + j;

        expect(t, f->buildOffset
               < map->buildFixedSizeInWords * BytesPerWord);

        map->targetFixedOffsets()[f->buildOffset] = f->targetOffset;

        map->fixedFields()[j] = *f;
      }

      hashMapInsert
        (t, typeMaps, vm::type(t, static_cast<Machine::Type>(i)), array,
         objectHash);
    }

    constants = makeCodeImage
      (t, &zone, image, code, className, methodName, methodSpec, typeMaps);

    PROTECT(t, constants);

    // these roots will not be used when the bootimage is loaded, so
    // there's no need to preserve them:
    setRoot(t, Machine::PoolMap, 0);
    setRoot(t, Machine::ByteArrayMap, makeWeakHashMap(t, 0, 0));

    // name all primitive classes so we don't try to update immutable
    // references at runtime:
    { object name = makeByteArray(t, "void");
      set(t, type(t, Machine::JvoidType), ClassName, name);
    
      name = makeByteArray(t, "boolean");
      set(t, type(t, Machine::JbooleanType), ClassName, name);

      name = makeByteArray(t, "byte");
      set(t, type(t, Machine::JbyteType), ClassName, name);

      name = makeByteArray(t, "short");
      set(t, type(t, Machine::JshortType), ClassName, name);

      name = makeByteArray(t, "char");
      set(t, type(t, Machine::JcharType), ClassName, name);

      name = makeByteArray(t, "int");
      set(t, type(t, Machine::JintType), ClassName, name);

      name = makeByteArray(t, "float");
      set(t, type(t, Machine::JfloatType), ClassName, name);

      name = makeByteArray(t, "long");
      set(t, type(t, Machine::JlongType), ClassName, name);

      name = makeByteArray(t, "double");
      set(t, type(t, Machine::JdoubleType), ClassName, name);
    }

    // resolve primitive array classes in case they are needed at
    // runtime:
    { object name = makeByteArray(t, "[B");
      resolveSystemClass(t, root(t, Machine::BootLoader), name, true);

      name = makeByteArray(t, "[Z");
      resolveSystemClass(t, root(t, Machine::BootLoader), name, true);

      name = makeByteArray(t, "[S");
      resolveSystemClass(t, root(t, Machine::BootLoader), name, true);

      name = makeByteArray(t, "[C");
      resolveSystemClass(t, root(t, Machine::BootLoader), name, true);

      name = makeByteArray(t, "[I");
      resolveSystemClass(t, root(t, Machine::BootLoader), name, true);

      name = makeByteArray(t, "[J");
      resolveSystemClass(t, root(t, Machine::BootLoader), name, true);

      name = makeByteArray(t, "[F");
      resolveSystemClass(t, root(t, Machine::BootLoader), name, true);

      name = makeByteArray(t, "[D");
      resolveSystemClass(t, root(t, Machine::BootLoader), name, true);
    }
  }

  target_uintptr_t* heap = static_cast<target_uintptr_t*>
    (t->m->heap->allocate(HeapCapacity));

  target_uintptr_t* heapMap = static_cast<target_uintptr_t*>
    (t->m->heap->allocate(heapMapSize(HeapCapacity)));
  memset(heapMap, 0, heapMapSize(HeapCapacity));

  HeapWalker* heapWalker = makeHeapImage
    (t, image, heap, heapMap, HeapCapacity, constants, typeMaps);

  updateConstants(t, constants, heapWalker->map());

  image->bootClassCount = hashMapSize
    (t, classLoaderMap(t, root(t, Machine::BootLoader)));

  unsigned* bootClassTable = static_cast<unsigned*>
    (t->m->heap->allocate(image->bootClassCount * sizeof(unsigned)));

  { unsigned i = 0;
    for (HashMapIterator it
           (t, classLoaderMap(t, root(t, Machine::BootLoader)));
         it.hasMore();)
    {
      bootClassTable[i++] = targetVW
        (heapWalker->map()->find(tripleSecond(t, it.next())));
    }
  }

  image->appClassCount = hashMapSize
    (t, classLoaderMap(t, root(t, Machine::AppLoader)));

  unsigned* appClassTable = static_cast<unsigned*>
    (t->m->heap->allocate(image->appClassCount * sizeof(unsigned)));

  { unsigned i = 0;
    for (HashMapIterator it
           (t, classLoaderMap(t, root(t, Machine::AppLoader)));
         it.hasMore();)
    {
      appClassTable[i++] = targetVW
        (heapWalker->map()->find(tripleSecond(t, it.next())));
    }
  }

  image->stringCount = hashMapSize(t, root(t, Machine::StringMap));
  unsigned* stringTable = static_cast<unsigned*>
    (t->m->heap->allocate(image->stringCount * sizeof(unsigned)));

  { unsigned i = 0;
    for (HashMapIterator it(t, root(t, Machine::StringMap)); it.hasMore();) {
      stringTable[i++] = targetVW
        (heapWalker->map()->find
         (jreferenceTarget(t, tripleFirst(t, it.next()))));
    }
  }

  unsigned* callTable = t->m->processor->makeCallTable(t, heapWalker);

  heapWalker->dispose();

  image->magic = BootImage::Magic;
  image->initialized = 0;

  fprintf(stderr, "class count %d string count %d call count %d\n"
          "heap size %d code size %d\n",
          image->bootClassCount, image->stringCount, image->callCount,
          image->heapSize, image->codeSize);

  Buffer bootimageData;

  if (true) {
    { BootImage targetImage;

#ifdef FIELD
#  undef FIELD
#endif

#define FIELD(name) targetImage.name = targetV4(image->name);
#include "bootimage-fields.cpp"
#undef FIELD

#define THUNK_FIELD(name) \
      targetImage.thunks.name = targetThunk(image->thunks.name);
#include "bootimage-fields.cpp"
#undef THUNK_FIELD

      bootimageData.write(&targetImage, sizeof(BootImage));
    }

    bootimageData.write(bootClassTable, image->bootClassCount * sizeof(unsigned));
    bootimageData.write(appClassTable, image->appClassCount * sizeof(unsigned));
    bootimageData.write(stringTable, image->stringCount * sizeof(unsigned));
    bootimageData.write(callTable, image->callCount * sizeof(unsigned) * 2);

    unsigned offset = sizeof(BootImage)
      + (image->bootClassCount * sizeof(unsigned))
      + (image->appClassCount * sizeof(unsigned))
      + (image->stringCount * sizeof(unsigned))
      + (image->callCount * sizeof(unsigned) * 2);

    while (offset % TargetBytesPerWord) {
      uint8_t c = 0;
      bootimageData.write(&c, 1);
      ++ offset;
    }

    bootimageData.write(heapMap, pad(heapMapSize(image->heapSize), TargetBytesPerWord));

    bootimageData.write(heap, pad(image->heapSize, TargetBytesPerWord));

    // fwrite(code, pad(image->codeSize, TargetBytesPerWord), 1, codeOutput);
    
    Platform* platform = Platform::getPlatform(PlatformInfo((PlatformInfo::Format)AVIAN_TARGET_FORMAT, (PlatformInfo::Architecture)AVIAN_TARGET_ARCH));

    // if(!platform) {
    //   fprintf(stderr, "unsupported platform: %s/%s\n", os, architecture);
    //   return false;
    // }

    SymbolInfo bootimageSymbols[] = {
      SymbolInfo(0, bootimageStart),
      SymbolInfo(bootimageData.length, bootimageEnd)
    };

    uint8_t* bootimage;
    unsigned bootimageLength;
    if (useLZMA) {
#ifdef AVIAN_USE_LZMA
      bootimage = encodeLZMA(t->m->system, t->m->heap, bootimageData.data,
                             bootimageData.length, &bootimageLength);

      fprintf(stderr, "compressed heap size %d\n", bootimageLength);
#else
      abort(t);
#endif
    } else {
      bootimage = bootimageData.data;
      bootimageLength = bootimageData.length;
    }

    platform->writeObject(bootimageOutput, Slice<SymbolInfo>(bootimageSymbols, 2), Slice<const uint8_t>(bootimage, bootimageLength), Platform::Writable, TargetBytesPerWord);

    if (useLZMA) {
      t->m->heap->free(bootimage, bootimageLength);
    }

    compilationHandler.symbols.add(SymbolInfo(0, codeimageStart));
    compilationHandler.symbols.add(SymbolInfo(image->codeSize, codeimageEnd));

    platform->writeObject(codeOutput, Slice<SymbolInfo>(compilationHandler.symbols), Slice<const uint8_t>(code, image->codeSize), Platform::Executable, TargetBytesPerWord);

    for(SymbolInfo* sym = compilationHandler.symbols.begin(); sym != compilationHandler.symbols.end() - 2; sym++) {
      t->m->heap->free(const_cast<void*>((const void*)sym->name.text), sym->name.length + 1);
    }
  }
}

uint64_t
writeBootImage(Thread* t, uintptr_t* arguments)
{
  OutputStream* bootimageOutput = reinterpret_cast<OutputStream*>(arguments[0]);
  OutputStream* codeOutput = reinterpret_cast<OutputStream*>(arguments[1]);
  BootImage* image = reinterpret_cast<BootImage*>(arguments[2]);
  uint8_t* code = reinterpret_cast<uint8_t*>(arguments[3]);
  const char* className = reinterpret_cast<const char*>(arguments[4]);
  const char* methodName = reinterpret_cast<const char*>(arguments[5]);
  const char* methodSpec = reinterpret_cast<const char*>(arguments[6]);

  const char* bootimageStart = reinterpret_cast<const char*>(arguments[7]);
  const char* bootimageEnd = reinterpret_cast<const char*>(arguments[8]);
  const char* codeimageStart = reinterpret_cast<const char*>(arguments[9]);
  const char* codeimageEnd = reinterpret_cast<const char*>(arguments[10]);
  bool useLZMA = arguments[11];

  writeBootImage2
    (t, bootimageOutput, codeOutput, image, code, className, methodName,
     methodSpec, bootimageStart, bootimageEnd, codeimageStart, codeimageEnd,
     useLZMA);

  return 1;
}

class Arg;

class ArgParser {
public:
  Arg* first;
  Arg** last;

  ArgParser():
    first(0),
    last(&first) {}

  bool parse(int ac, const char** av);
  void printUsage(const char* exe);
};

class Arg {
public:
  Arg* next;
  bool required;
  const char* name;
  const char* desc;

  const char* value;

  Arg(ArgParser& parser, bool required, const char* name, const char* desc):
    next(0),
    required(required),
    name(name),
    desc(desc),
    value(0)
  {
    *parser.last = this;
    parser.last = &next;
  }
};

bool ArgParser::parse(int ac, const char** av) {
  Arg* state = 0;

  for(int i = 1; i < ac; i++) {
    if(state) {
      if(state->value) {
        fprintf(stderr, "duplicate parameter %s: '%s' and '%s'\n", state->name, state->value, av[i]);
        return false;
      }
      state->value = av[i];
      state = 0;
    } else {
      if(av[i][0] != '-') {
        fprintf(stderr, "expected -parameter\n");
        return false;
      }
      bool found = false;
      for(Arg* arg = first; arg; arg = arg->next) {
        if(strcmp(arg->name,  &av[i][1]) == 0) {
          found = true;
          if (arg->desc == 0) {
            arg->value = "true";
          } else {
            state = arg;
          }
        }
      }
      if (not found) {
        fprintf(stderr, "unrecognized parameter %s\n", av[i]);
        return false;
      }
    }
  }

  if(state) {
    fprintf(stderr, "expected argument after -%s\n", state->name);
    return false;
  }

  for(Arg* arg = first; arg; arg = arg->next) {
    if(arg->required && !arg->value) {
      fprintf(stderr, "expected value for %s\n", arg->name);
      return false;
    }
  }

  return true;
}

void ArgParser::printUsage(const char* exe) {
  fprintf(stderr, "usage:\n%s \\\n", exe);
  for(Arg* arg = first; arg; arg = arg->next) {
    const char* lineEnd = arg->next ? " \\" : "";
    if(arg->required) {
      fprintf(stderr, "  -%s\t%s%s\n", arg->name, arg->desc, lineEnd);
    } else if (arg->desc) {
      fprintf(stderr, "  [-%s\t%s]%s\n", arg->name, arg->desc, lineEnd);
    } else {
      fprintf(stderr, "  [-%s]%s\n", arg->name, lineEnd);
    }
  }
}

char*
myStrndup(const char* src, unsigned length)
{
  char* s = static_cast<char*>(malloc(length + 1));
  memcpy(s, src, length);
  s[length] = 0;
  return s;
}

class Arguments {
public:

  const char* classpath;

  const char* bootimage;
  const char* codeimage;

  char* entryClass;
  char* entryMethod;
  char* entrySpec;

  char* bootimageStart;
  char* bootimageEnd;

  char* codeimageStart;
  char* codeimageEnd;

  bool useLZMA;

  bool maybeSplit(const char* src, char*& destA, char*& destB) {
    if(src) {
      const char* split = strchr(src, ':');
      if(!split) {
        return false;
      }

      destA = myStrndup(src, split - src);
      destB = strdup(split + 1);
    }
    return true;
  }

  Arguments(int ac, const char** av):
    entryClass(0),
    entryMethod(0),
    entrySpec(0),
    bootimageStart(0),
    bootimageEnd(0),
    codeimageStart(0),
    codeimageEnd(0)
  {
    ArgParser parser;
    Arg classpath(parser, true, "cp", "<classpath>");
    Arg bootimage(parser, true, "bootimage", "<bootimage file>");
    Arg codeimage(parser, true, "codeimage", "<codeimage file>");
    Arg entry(parser, false, "entry", "<class name>[.<method name>[<method spec>]]");
    Arg bootimageSymbols(parser, false, "bootimage-symbols", "<start symbol name>:<end symbol name>");
    Arg codeimageSymbols(parser, false, "codeimage-symbols", "<start symbol name>:<end symbol name>");
    Arg useLZMA(parser, false, "use-lzma", 0);

    if(!parser.parse(ac, av)) {
      parser.printUsage(av[0]);
      exit(1);
    }

    this->classpath = classpath.value;
    this->bootimage = bootimage.value;
    this->codeimage = codeimage.value;
    this->useLZMA = useLZMA.value != 0;

    if(entry.value) {
      if(const char* entryClassEnd = strchr(entry.value, '.')) {
        entryClass = myStrndup(entry.value, entryClassEnd - entry.value);
        if(const char* entryMethodEnd = strchr(entryClassEnd, '(')) {
          entryMethod = myStrndup(entryClassEnd + 1, entryMethodEnd - entryClassEnd - 1);
          entrySpec = strdup(entryMethodEnd);
        } else {
          entryMethod = strdup(entryClassEnd + 1);
        }
      } else {
        entryClass = strdup(entry.value);
      }
    }

    if(!maybeSplit(bootimageSymbols.value, bootimageStart, bootimageEnd) ||
       !maybeSplit(codeimageSymbols.value, codeimageStart, codeimageEnd))
    {
      fprintf(stderr, "wrong format for symbols\n");
      parser.printUsage(av[0]);
      exit(1);
    }

    if(!bootimageStart) {
      bootimageStart = strdup("_binary_bootimage_bin_start");
    }

    if(!bootimageEnd) {
      bootimageEnd = strdup("_binary_bootimage_bin_end");
    }

    if(!codeimageStart) {
      codeimageStart = strdup("_binary_codeimage_bin_start");
    }

    if(!codeimageEnd) {
      codeimageEnd = strdup("_binary_codeimage_bin_end");
    }

  }

  ~Arguments() {
    if(entryClass) {
      free(entryClass);
    }
    if(entryMethod) {
      free(entryMethod);
    }
    if(entrySpec) {
      free(entrySpec);
    }
    if(bootimageStart) {
      free(bootimageStart);
    }
    if(bootimageEnd) {
      free(bootimageEnd);
    }
    if(codeimageStart) {
      free(codeimageStart);
    }
    if(codeimageEnd) {
      free(codeimageEnd);
    }
  }

  void dump() {
    printf(
      "classpath = %s\n"
      "bootimage = %s\n"
      "codeimage = %s\n"
      "entryClass = %s\n"
      "entryMethod = %s\n"
      "entrySpec = %s\n"
      "bootimageStart = %s\n"
      "bootimageEnd = %s\n"
      "codeimageStart = %s\n"
      "codeimageEnd = %s\n",
      classpath,
      bootimage,
      codeimage,
      entryClass,
      entryMethod,
      entrySpec,
      bootimageStart,
      bootimageEnd,
      codeimageStart,
      codeimageEnd);
  }
};

} // namespace

int
main(int ac, const char** av)
{
  Arguments args(ac, av);
  // args.dump();

  System* s = makeSystem(0);
  Heap* h = makeHeap(s, HeapCapacity * 2);
  Classpath* c = makeClasspath(s, h, AVIAN_JAVA_HOME, AVIAN_EMBED_PREFIX);
  Finder* f = makeFinder(s, h, args.classpath, 0);
  Processor* p = makeProcessor(s, h, false);

  // todo: currently, the compiler cannot compile code with jumps or
  // calls spanning more than the maximum size of an immediate value
  // in a branch instruction for the target architecture (~32MB on
  // PowerPC and ARM).  When that limitation is removed, we'll be able
  // to specify a capacity as large as we like here:
#if (defined ARCH_x86_64) || (defined ARCH_x86_32)
  const unsigned CodeCapacity = 128 * 1024 * 1024;
#else
  const unsigned CodeCapacity = 30 * 1024 * 1024;
#endif

  uint8_t* code = static_cast<uint8_t*>(h->allocate(CodeCapacity));
  BootImage image;
  p->initialize(&image, code, CodeCapacity);

  Machine* m = new (h->allocate(sizeof(Machine))) Machine
    (s, h, f, 0, p, c, 0, 0, 0, 0, 128 * 1024);
  Thread* t = p->makeThread(m, 0, 0);
  
  enter(t, Thread::ActiveState);
  enter(t, Thread::IdleState);

  FileOutputStream bootimageOutput(args.bootimage);
  if (!bootimageOutput.isValid()) {
    fprintf(stderr, "unable to open %s\n", args.bootimage);    
    return -1;
  }

  FileOutputStream codeOutput(args.codeimage);
  if (!codeOutput.isValid()) {
    fprintf(stderr, "unable to open %s\n", args.codeimage);    
    return -1;
  }

  uintptr_t arguments[] = {
    reinterpret_cast<uintptr_t>(&bootimageOutput),
    reinterpret_cast<uintptr_t>(&codeOutput),
    reinterpret_cast<uintptr_t>(&image),
    reinterpret_cast<uintptr_t>(code),
    reinterpret_cast<uintptr_t>(args.entryClass),
    reinterpret_cast<uintptr_t>(args.entryMethod),
    reinterpret_cast<uintptr_t>(args.entrySpec),
    reinterpret_cast<uintptr_t>(args.bootimageStart),
    reinterpret_cast<uintptr_t>(args.bootimageEnd),
    reinterpret_cast<uintptr_t>(args.codeimageStart),
    reinterpret_cast<uintptr_t>(args.codeimageEnd),
    static_cast<uintptr_t>(args.useLZMA)
  };

  run(t, writeBootImage, arguments);

  if (t->exception) {
    printTrace(t, t->exception);
    return -1;
  } else {
    return 0;
  }
}
