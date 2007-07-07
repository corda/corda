#include "common.h"
#include "system.h"
#include "heap.h"
#include "class-finder.h"
#include "stream.h"
#include "constants.h"
#include "run.h"
#include "jnienv.h"
#include "builtin.h"
#include "machine.h"

using namespace vm;

namespace {

void
pushFrame(Thread* t, object method)
{
  if (t->frame >= 0) {
    pokeInt(t, t->frame + FrameIpOffset, t->ip);
  }
  t->ip = 0;

  unsigned parameterFootprint = methodParameterFootprint(t, method);
  unsigned base = t->sp - parameterFootprint;
  unsigned locals = parameterFootprint;

  if ((methodFlags(t, method) & ACC_NATIVE) == 0) {
    t->code = methodCode(t, method);

    locals = codeMaxLocals(t, t->code);

    memset(t->stack + ((base + parameterFootprint) * 2), 0,
           (locals - parameterFootprint) * BytesPerWord * 2);
  }

  unsigned frame = base + locals;
  pokeInt(t, frame + FrameNextOffset, t->frame);
  t->frame = frame;

  t->sp = frame + FrameFootprint;

  pokeInt(t, frame + FrameBaseOffset, base);
  pokeObject(t, frame + FrameMethodOffset, method);
}

void
popFrame(Thread* t)
{
  t->sp = frameBase(t, t->frame);
  t->frame = frameNext(t, t->frame);
  if (t->frame >= 0) {
    t->code = methodCode(t, frameMethod(t, t->frame));
    t->ip = frameIp(t, t->frame);
  } else {
    t->code = 0;
    t->ip = 0;
  }
}

inline object
make(Thread* t, object class_)
{
  PROTECT(t, class_);
  unsigned sizeInBytes = pad(classFixedSize(t, class_));
  object instance = allocate(t, sizeInBytes);
  *static_cast<object*>(instance) = class_;
  memset(static_cast<object*>(instance) + 1, 0,
         sizeInBytes - sizeof(object));

  fprintf(stderr, "new instance: %p\n", instance);

  if (class_ == arrayBody(t, t->vm->types, Machine::ThreadType)) {
    if (threadPeer(t, instance)) {
      fprintf(stderr, "yo!\n");
    }
  }

  return instance;
}

unsigned
fieldCode(Thread* t, unsigned javaCode)
{
  switch (javaCode) {
  case 'B':
    return ByteField;
  case 'C':
    return CharField;
  case 'D':
    return DoubleField;
  case 'F':
    return FloatField;
  case 'I':
    return IntField;
  case 'J':
    return LongField;
  case 'S':
    return ShortField;
  case 'V':
    return VoidField;
  case 'Z':
    return BooleanField;
  case 'L':
  case '[':
    return ObjectField;

  default: abort(t);
  }
}

unsigned
fieldType(Thread* t, unsigned code)
{
  switch (code) {
  case VoidField:
    return VOID_TYPE;
  case ByteField:
  case BooleanField:
    return INT8_TYPE;
  case CharField:
  case ShortField:
    return INT16_TYPE;
  case DoubleField:
    return DOUBLE_TYPE;
  case FloatField:
    return FLOAT_TYPE;
  case IntField:
    return INT32_TYPE;
  case LongField:
    return INT64_TYPE;
  case ObjectField:
    return POINTER_TYPE;

  default: abort(t);
  }
}

unsigned
primitiveSize(Thread* t, unsigned code)
{
  switch (code) {
  case VoidField:
    return 0;
  case ByteField:
  case BooleanField:
    return 1;
  case CharField:
  case ShortField:
    return 2;
  case FloatField:
  case IntField:
    return 4;
  case DoubleField:
  case LongField:
    return 8;

  default: abort(t);
  }
}

inline void
setStatic(Thread* t, object field, object value)
{
  set(t, arrayBody(t, classStaticTable(t, fieldClass(t, field)),
                   fieldOffset(t, field)), value);
}

bool
instanceOf(Thread* t, object class_, object o)
{
  if (o == 0) {
    return false;
  }

  if (classFlags(t, class_) & ACC_INTERFACE) {
    for (object oc = objectClass(t, o); oc; oc = classSuper(t, oc)) {
      object itable = classInterfaceTable(t, oc);
      for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
        if (arrayBody(t, itable, i) == class_) {
          return true;
        }
      }
    }
  } else {
    for (object oc = objectClass(t, o); oc; oc = classSuper(t, oc)) {
      if (oc == class_) {
        return true;
      }
    }
  }

  return false;
}

object
findInterfaceMethod(Thread* t, object method, object o)
{
  object interface = methodClass(t, method);
  object itable = classInterfaceTable(t, objectClass(t, o));
  for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
    if (arrayBody(t, itable, i) == interface) {
      return arrayBody(t, arrayBody(t, itable, i + 1),
                       methodOffset(t, method));
    }
  }
  abort(t);
}

inline object
findMethod(Thread* t, object method, object class_)
{
  return arrayBody(t, classVirtualTable(t, class_), 
                   methodOffset(t, method));
}

inline object
findVirtualMethod(Thread* t, object method, object o)
{
  return findMethod(t, method, objectClass(t, o));
}

bool
isSuperclass(Thread* t, object class_, object base)
{
  for (object oc = classSuper(t, base); oc; oc = classSuper(t, oc)) {
    if (oc == class_) {
      return true;
    }
  }
  return false;
}

inline bool
isSpecialMethod(Thread* t, object method, object class_)
{
  return (classFlags(t, class_) & ACC_SUPER)
    and strcmp(reinterpret_cast<const int8_t*>("<init>"), 
               &byteArrayBody(t, methodName(t, method), 0)) != 0
    and isSuperclass(t, methodClass(t, method), class_);
}

object
find(Thread* t, object class_, object table, object reference,
     object& (*name)(Thread*, object),
     object& (*spec)(Thread*, object),
     object (*makeError)(Thread*, object))
{
  object n = referenceName(t, reference);
  object s = referenceSpec(t, reference);
  for (unsigned i = 0; i < arrayLength(t, table); ++i) {
    object o = arrayBody(t, table, i);

    if (strcmp(&byteArrayBody(t, name(t, o), 0),
               &byteArrayBody(t, n, 0)) == 0 and
        strcmp(&byteArrayBody(t, spec(t, o), 0),
               &byteArrayBody(t, s, 0)) == 0)
    {
      return o;
    }               
  }

  object message = makeString
    (t, "%s:%s not found in %s",
     &byteArrayBody(t, n, 0),
     &byteArrayBody(t, s, 0),
     &byteArrayBody(t, className(t, class_), 0));
  t->exception = makeError(t, message);
  return 0;
}

inline object
findFieldInClass(Thread* t, object class_, object reference)
{
  return find(t, class_, classFieldTable(t, class_), reference, fieldName,
              fieldSpec, makeNoSuchFieldError);
}

inline object
findMethodInClass(Thread* t, object class_, object reference)
{
  return find(t, class_, classMethodTable(t, class_), reference, methodName,
              methodSpec, makeNoSuchMethodError);
}

object
parsePool(Thread* t, Stream& s)
{
  unsigned poolCount = s.read2() - 1;
  object pool = makeArray(t, poolCount, true);

  PROTECT(t, pool);

  for (unsigned i = 0; i < poolCount; ++i) {
    unsigned c = s.read1();

    switch (c) {
    case CONSTANT_Integer: {
      object value = makeInt(t, s.read4());
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_Float: {
      object value = makeFloat(t, s.readFloat());
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_Long: {
      object value = makeLong(t, s.read8());
      set(t, arrayBody(t, pool, i), value);
      ++i;
    } break;

    case CONSTANT_Double: {
      object value = makeLong(t, s.readDouble());
      set(t, arrayBody(t, pool, i), value);
      ++i;
    } break;

    case CONSTANT_Utf8: {
      unsigned length = s.read2();
      object value = makeByteArray(t, length + 1, false);
      s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, value, 0)), length);
      byteArrayBody(t, value, length) = 0;
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_Class: {
      object value = makeIntArray(t, 2, false);
      intArrayBody(t, value, 0) = c;
      intArrayBody(t, value, 1) = s.read2();
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_String: {
      object value = makeIntArray(t, 2, false);
      intArrayBody(t, value, 0) = c;
      intArrayBody(t, value, 1) = s.read2();
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_NameAndType: {
      object value = makeIntArray(t, 3, false);
      intArrayBody(t, value, 0) = c;
      intArrayBody(t, value, 1) = s.read2();
      intArrayBody(t, value, 2) = s.read2();
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_Fieldref:
    case CONSTANT_Methodref:
    case CONSTANT_InterfaceMethodref: {
      object value = makeIntArray(t, 3, false);
      intArrayBody(t, value, 0) = c;
      intArrayBody(t, value, 1) = s.read2();
      intArrayBody(t, value, 2) = s.read2();
      set(t, arrayBody(t, pool, i), value);
    } break;

    default: abort(t);
    }
  }

  for (unsigned i = 0; i < poolCount; ++i) {
    object o = arrayBody(t, pool, i);
    if (o and objectClass(t, o)
        == arrayBody(t, t->vm->types, Machine::IntArrayType))
    {
      switch (intArrayBody(t, o, 0)) {
      case CONSTANT_Class: {
        set(t, arrayBody(t, pool, i),
            arrayBody(t, pool, intArrayBody(t, o, 1) - 1));
      } break;

      case CONSTANT_String: {
        object bytes = arrayBody(t, pool, intArrayBody(t, o, 1) - 1);
        object value = makeString(t, bytes, 0, byteArrayLength(t, bytes), 0);
        set(t, arrayBody(t, pool, i), value);
      } break;

      case CONSTANT_NameAndType: {
        object name = arrayBody(t, pool, intArrayBody(t, o, 1) - 1);
        object type = arrayBody(t, pool, intArrayBody(t, o, 2) - 1);
        object value = makePair(t, name, type);
        set(t, arrayBody(t, pool, i), value);
      } break;
      }
    }
  }

  for (unsigned i = 0; i < poolCount; ++i) {
    object o = arrayBody(t, pool, i);
    if (o and objectClass(t, o)
        == arrayBody(t, t->vm->types, Machine::IntArrayType))
    {
      switch (intArrayBody(t, o, 0)) {
      case CONSTANT_Fieldref:
      case CONSTANT_Methodref:
      case CONSTANT_InterfaceMethodref: {
        object c = arrayBody(t, pool, intArrayBody(t, o, 1) - 1);
        object nameAndType = arrayBody(t, pool, intArrayBody(t, o, 2) - 1);
        object value = makeReference
          (t, c, pairFirst(t, nameAndType), pairSecond(t, nameAndType));
        set(t, arrayBody(t, pool, i), value);
      } break;
      }
    }
  }

  return pool;
}

void
addInterfaces(Thread* t, object class_, object map)
{
  object table = classInterfaceTable(t, class_);
  if (table) {
    unsigned increment = 2;
    if (classFlags(t, class_) & ACC_INTERFACE) {
      increment = 1;
    }

    PROTECT(t, map);
    PROTECT(t, table);

    for (unsigned i = 0; i < arrayLength(t, table); i += increment) {
      object interface = arrayBody(t, table, i);
      object name = className(t, interface);
      hashMapInsertMaybe(t, map, name, interface, byteArrayHash,
                         byteArrayEqual);
    }
  }
}

object
resolveClass(Thread*, object);

void
parseInterfaceTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);
  
  object map = makeHashMap(t, 0, 0);
  PROTECT(t, map);

  if (classSuper(t, class_)) {
    addInterfaces(t, classSuper(t, class_), map);
  }
  
  unsigned count = s.read2();
  for (unsigned i = 0; i < count; ++i) {
    object name = arrayBody(t, pool, s.read2() - 1);
    PROTECT(t, name);

    object interface = resolveClass(t, name);
    PROTECT(t, interface);

    hashMapInsertMaybe(t, map, name, interface, byteArrayHash, byteArrayEqual);

    addInterfaces(t, interface, map);
  }

  object interfaceTable = 0;
  if (hashMapSize(t, map)) {
    unsigned length = hashMapSize(t, map) ;
    if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
      length *= 2;
    }
    interfaceTable = makeArray(t, length, true);
    PROTECT(t, interfaceTable);

    unsigned i = 0;
    object it = hashMapIterator(t, map);
    PROTECT(t, it);

    for (; it; it = hashMapIteratorNext(t, it)) {
      object interface = resolveClass
        (t, tripleFirst(t, hashMapIteratorNode(t, it)));
      if (UNLIKELY(t->exception)) return;

      set(t, arrayBody(t, interfaceTable, i++), interface);

      if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
        // we'll fill in this table in parseMethodTable():
        object vtable = makeArray
          (t, arrayLength(t, classVirtualTable(t, interface)), true);
        
        set(t, arrayBody(t, interfaceTable, i++), vtable);
      }
    }
  }

  set(t, classInterfaceTable(t, class_), interfaceTable);
}

inline unsigned
fieldSize(Thread* t, object field)
{
  unsigned code = fieldCode(t, field);
  if (code == ObjectField) {
    return BytesPerWord;
  } else {
    return primitiveSize(t, code);
  }
}

void
parseFieldTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  unsigned memberOffset = BytesPerWord;
  if (classSuper(t, class_)) {
    memberOffset = classFixedSize(t, classSuper(t, class_));
  }

  unsigned count = s.read2();
  if (count) {
    unsigned staticOffset = 0;
  
    object fieldTable = makeArray(t, count, true);
    PROTECT(t, fieldTable);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        s.read2();
        s.skip(s.read4());
      }

      object field = makeField
        (t,
         flags,
         0, // offset
         fieldCode(t, byteArrayBody(t, arrayBody(t, pool, spec - 1), 0)),
         arrayBody(t, pool, name - 1),
         arrayBody(t, pool, spec - 1),
         class_);

      if (flags & ACC_STATIC) {
        fieldOffset(t, field) = staticOffset++;
      } else {
        unsigned excess = memberOffset % BytesPerWord;
        if (excess and fieldCode(t, field) == ObjectField) {
          memberOffset += BytesPerWord - excess;
        }

        fieldOffset(t, field) = memberOffset;
        memberOffset += fieldSize(t, field);
      }

      set(t, arrayBody(t, fieldTable, i), field);
    }

    set(t, classFieldTable(t, class_), fieldTable);

    if (staticOffset) {
      object staticTable = makeArray(t, staticOffset, true);

      set(t, classStaticTable(t, class_), staticTable);
    }
  }

  classFixedSize(t, class_) = pad(memberOffset);
  
  object mask = makeIntArray
    (t, divide(classFixedSize(t, class_), BitsPerWord), true);
  intArrayBody(t, mask, 0) = 1;

  bool sawReferenceField = false;
  for (object c = class_; c; c = classSuper(t, c)) {
    object fieldTable = classFieldTable(t, c);
    if (fieldTable) {
      for (int i = arrayLength(t, fieldTable) - 1; i >= 0; --i) {
        object field = arrayBody(t, fieldTable, i);
        if (fieldCode(t, field) == ObjectField) {
          unsigned index = fieldOffset(t, field) / BytesPerWord;
          intArrayBody(t, mask, (index / 32)) |= 1 << (index % 32);
          sawReferenceField = true;
        }
      }
    }
  }

  if (sawReferenceField) {
    set(t, classObjectMask(t, class_), mask);
  }
}

object
parseCode(Thread* t, Stream& s, object pool)
{
  unsigned maxStack = s.read2();
  unsigned maxLocals = s.read2();
  unsigned length = s.read4();

  object code = makeCode(t, pool, 0, 0, maxStack, maxLocals, length, false);
  s.read(&codeBody(t, code, 0), length);

  unsigned ehtLength = s.read2();
  if (ehtLength) {
    PROTECT(t, code);

    object eht = makeExceptionHandlerTable(t, ehtLength, false);
    for (unsigned i = 0; i < ehtLength; ++i) {
      ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);
      exceptionHandlerStart(eh) = s.read2();
      exceptionHandlerEnd(eh) = s.read2();
      exceptionHandlerIp(eh) = s.read2();
      exceptionHandlerCatchType(eh) = s.read2();
    }

    set(t, codeExceptionHandlerTable(t, code), eht);
  }

  unsigned attributeCount = s.read2();
  for (unsigned j = 0; j < attributeCount; ++j) {
    object name = arrayBody(t, pool, s.read2() - 1);
    unsigned length = s.read4();

    if (strcmp(reinterpret_cast<const int8_t*>("LineNumberTable"),
               &byteArrayBody(t, name, 0)) == 0)
    {
      unsigned lntLength = s.read2();
      object lnt = makeLineNumberTable(t, lntLength, false);
      for (unsigned i = 0; i < lntLength; ++i) {
        LineNumber* ln = lineNumberTableBody(t, lnt, i);
        lineNumberIp(ln) = s.read2();
        lineNumberLine(ln) = s.read2();
      }

      set(t, codeLineNumberTable(t, code), lnt);
    } else {
      s.skip(length);
    }
  }

  return code;
}

unsigned
parameterFootprint(Thread* t, object spec)
{
  unsigned footprint = 0;
  const char* s = reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0));
  ++ s; // skip '('
  while (*s and *s != ')') {
    switch (*s) {
    case 'L':
      while (*s and *s != ';') ++ s;
      ++ s;
      break;

    case '[':
      while (*s == '[') ++ s;
      switch (*s) {
      case 'L':
        while (*s and *s != ';') ++ s;
        ++ s;
        break;

      default:
        ++ s;
        break;
      }
      break;
      
    case 'J':
    case 'D':
      ++ s;
      ++ footprint;
      break;

    default:
      ++ s;
      break;
    }

    ++ footprint;
  }

  return footprint;
}

unsigned
parameterCount(Thread* t, object spec)
{
  unsigned count = 0;
  const char* s = reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0));
  ++ s; // skip '('
  while (*s and *s != ')') {
    switch (*s) {
    case 'L':
      while (*s and *s != ';') ++ s;
      ++ s;
      break;

    case '[':
      while (*s == '[') ++ s;
      switch (*s) {
      case 'L':
        while (*s and *s != ';') ++ s;
        ++ s;
        break;

      default:
        ++ s;
        break;
      }
      break;
      
    default:
      ++ s;
      break;
    }

    ++ count;
  }

  return count;
}

int
lineNumber(Thread* t, object method, unsigned ip)
{
  if (methodFlags(t, method) & ACC_NATIVE) {
    return NativeLine;
  }

  object table = codeLineNumberTable(t, methodCode(t, method));
  if (table) {
    // todo: do a binary search:
    int last = UnknownLine;
    for (unsigned i = 0; i < lineNumberTableLength(t, table); ++i) {
      if (ip <= lineNumberIp(lineNumberTableBody(t, table, i))) {
        return last;
      } else {
        last = lineNumberLine(lineNumberTableBody(t, table, i));
      }
    }
    return last;
  } else {
    return UnknownLine;
  }
}

unsigned
mangledSize(int8_t c)
{
  switch (c) {
  case '_':
  case ';':
  case '[':
    return 2;

  case '$':
    return 6;

  default:
    return 1;
  }
}

unsigned
mangle(int8_t c, int8_t* dst)
{
  switch (c) {
  case '/':
    dst[0] = '_';
    return 1;

  case '_':
    dst[0] = '_';
    dst[1] = '1';
    return 2;

  case ';':
    dst[0] = '_';
    dst[1] = '2';
    return 2;

  case '[':
    dst[0] = '_';
    dst[1] = '3';
    return 2;

  case '$':
    memcpy(dst, "_00024", 6);
    return 6;

  default:
    dst[0] = c;    
    return 1;
  }
}

object
makeJNIName(Thread* t, object method, bool decorate)
{
  unsigned size = 5;
  object className = ::className(t, methodClass(t, method));
  PROTECT(t, className);
  for (unsigned i = 0; i < byteArrayLength(t, className) - 1; ++i) {
    size += mangledSize(byteArrayBody(t, className, i));
  }

  ++ size;

  object methodName = ::methodName(t, method);
  PROTECT(t, methodName);
  for (unsigned i = 0; i < byteArrayLength(t, methodName) - 1; ++i) {
    size += mangledSize(byteArrayBody(t, methodName, i));
  }

  object methodSpec = ::methodSpec(t, method);
  PROTECT(t, methodSpec);
  if (decorate) {
    size += 2;
    for (unsigned i = 1; i < byteArrayLength(t, methodSpec) - 1
           and byteArrayBody(t, methodSpec, i) != ')'; ++i)
    {
      size += mangledSize(byteArrayBody(t, methodSpec, i));
    }
  }

  object name = makeByteArray(t, size + 1, false);
  unsigned index = 0;

  memcpy(&byteArrayBody(t, name, index), "Java_", 5);
  index += 5;

  for (unsigned i = 0; i < byteArrayLength(t, className) - 1; ++i) {
    index += mangle(byteArrayBody(t, className, i),
                    &byteArrayBody(t, name, index));
  }

  byteArrayBody(t, name, index++) = '_';

  for (unsigned i = 0; i < byteArrayLength(t, methodName) - 1; ++i) {
    index += mangle(byteArrayBody(t, methodName, i),
                    &byteArrayBody(t, name, index));
  }
  
  if (decorate) {
    byteArrayBody(t, name, index++) = '_';
    byteArrayBody(t, name, index++) = '_';
    for (unsigned i = 1; i < byteArrayLength(t, methodSpec) - 1
           and byteArrayBody(t, methodSpec, i) != ')'; ++i)
    {
      index += mangle(byteArrayBody(t, className, i),
                      &byteArrayBody(t, name, index));
    }
  }

  byteArrayBody(t, name, index++) = 0;

  assert(t, index == size + 1);

  return name;
}

void
parseMethodTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  object virtualMap = makeHashMap(t, 0, 0);
  PROTECT(t, virtualMap);

  object nativeMap = makeHashMap(t, 0, 0);
  PROTECT(t, nativeMap);

  unsigned virtualCount = 0;

  object superVirtualTable = 0;
  PROTECT(t, superVirtualTable);

  if (classFlags(t, class_) & ACC_INTERFACE) {
    object itable = classInterfaceTable(t, class_);
    if (itable) {
      for (unsigned i = 0; i < arrayLength(t, itable); ++i) {
        object vtable = classVirtualTable(t, arrayBody(t, itable, i));
        for (unsigned j = 0; j < virtualCount; ++j) {
          object method = arrayBody(t, vtable, j);
          if (hashMapInsertMaybe(t, virtualMap, method, method, methodHash,
                                 methodEqual))
          {
            ++ virtualCount;
          }
        }
      }
    }
  } else {
    if (classSuper(t, class_)) {
      superVirtualTable = classVirtualTable(t, classSuper(t, class_));
    }

    if (superVirtualTable) {
      virtualCount = arrayLength(t, superVirtualTable);
      for (unsigned i = 0; i < virtualCount; ++i) {
        object method = arrayBody(t, superVirtualTable, i);
        hashMapInsert(t, virtualMap, method, method, methodHash);
      }
    }
  }

  object newVirtuals = makeList(t, 0, 0, 0);
  PROTECT(t, newVirtuals);
  
  unsigned count = s.read2();
  if (count) {
    object methodTable = makeArray(t, count, true);
    PROTECT(t, methodTable);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      object code = 0;
      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        object name = arrayBody(t, pool, s.read2() - 1);
        unsigned length = s.read4();

        if (strcmp(reinterpret_cast<const int8_t*>("Code"),
                   &byteArrayBody(t, name, 0)) == 0)
        {
          code = parseCode(t, s, pool);
        } else {
          s.skip(length);
        }
      }

      unsigned parameterCount = ::parameterCount
        (t, arrayBody(t, pool, spec - 1));

      unsigned parameterFootprint = ::parameterFootprint
        (t, arrayBody(t, pool, spec - 1));

      if ((flags & ACC_STATIC) == 0) {
        ++ parameterCount;
        ++ parameterFootprint;
      }

      object method = makeMethod(t,
                                 flags,
                                 0, // offset
                                 parameterCount,
                                 parameterFootprint,
                                 arrayBody(t, pool, name - 1),
                                 arrayBody(t, pool, spec - 1),
                                 class_,
                                 code);
      PROTECT(t, method);

      if (flags & ACC_STATIC) {
        if (strcmp(reinterpret_cast<const int8_t*>("<clinit>"), 
                   &byteArrayBody(t, methodName(t, method), 0)) == 0)
        {
          set(t, classInitializer(t, class_), method);
        }
      } else {
        object p = hashMapFindNode
          (t, virtualMap, method, methodHash, methodEqual);

        if (p) {
          methodOffset(t, method) = methodOffset(t, tripleFirst(t, p));

          set(t, tripleSecond(t, p), method);
        } else {
          methodOffset(t, method) = virtualCount++;

          listAppend(t, newVirtuals, method);

          hashMapInsert(t, virtualMap, method, method, methodHash);
        }
      }

      if (flags & ACC_NATIVE) {
        object p = hashMapFindNode
          (t, nativeMap, method, methodHash, methodEqual);
        
        if (p) {
          set(t, tripleSecond(t, p), method);          
        } else {
          hashMapInsert(t, virtualMap, method, 0, methodHash);          
        }
      }

      set(t, arrayBody(t, methodTable, i), method);
    }

    for (unsigned i = 0; i < count; ++i) {
      object method = arrayBody(t, methodTable, i);

      if (methodFlags(t, method) & ACC_NATIVE) {
        object overloaded = hashMapFind
          (t, nativeMap, method, methodHash, methodEqual);

        object jniName = makeJNIName(t, method, overloaded);
        set(t, methodCode(t, method), jniName);
      }
    }

    set(t, classMethodTable(t, class_), methodTable);
  }

  if (virtualCount) {
    // generate class vtable

    unsigned i = 0;
    object vtable = makeArray(t, virtualCount, false);

    if (classFlags(t, class_) & ACC_INTERFACE) {
      object it = hashMapIterator(t, virtualMap);

      for (; it; it = hashMapIteratorNext(t, it)) {
        object method = tripleFirst(t, hashMapIteratorNode(t, it));
        set(t, arrayBody(t, vtable, i++), method);
      }
    } else {
      if (superVirtualTable) {
        for (; i < arrayLength(t, superVirtualTable); ++i) {
          object method = arrayBody(t, superVirtualTable, i);
          method = hashMapFind(t, virtualMap, method, methodHash, methodEqual);

          set(t, arrayBody(t, vtable, i), method);
        }
      }

      for (object p = listFront(t, newVirtuals); p; p = pairSecond(t, p)) {
        set(t, arrayBody(t, vtable, i++), pairFirst(t, p));        
      }
    }

    set(t, classVirtualTable(t, class_), vtable);

    if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
      // generate interface vtables
    
      object itable = classInterfaceTable(t, class_);
      if (itable) {
        PROTECT(t, itable);

        for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
          object ivtable = classVirtualTable(t, arrayBody(t, itable, i));
          object vtable = arrayBody(t, itable, i + 1);
        
          for (unsigned j = 0; j < arrayLength(t, ivtable); ++j) {
            object method = arrayBody(t, ivtable, j);
            method = hashMapFind
              (t, virtualMap, method, methodHash, methodEqual);
            assert(t, method);
          
            set(t, arrayBody(t, vtable, j), method);        
          }
        }
      }
    }
  }
}

object
parseClass(Thread* t, const uint8_t* data, unsigned size)
{
  class Client : public Stream::Client {
   public:
    Client(Thread* t): t(t) { }

    virtual void NO_RETURN handleEOS() {
      abort(t);
    }

   private:
    Thread* t;
  } client(t);

  Stream s(&client, data, size);

  uint32_t magic = s.read4();
  assert(t, magic == 0xCAFEBABE);
  s.read2(); // minor version
  s.read2(); // major version

  object pool = parsePool(t, s);

  unsigned flags = s.read2();
  unsigned name = s.read2();

  object class_ = makeClass(t,
                            flags,
                            0, // VM flags
                            0, // fixed size
                            0, // array size
                            0, // object mask
                            arrayBody(t, pool, name - 1),
                            0, // super
                            0, // interfaces
                            0, // vtable
                            0, // fields
                            0, // methods
                            0, // static table
                            0); // initializer
  PROTECT(t, class_);
  
  unsigned super = s.read2();
  if (super) {
    object sc = resolveClass(t, arrayBody(t, pool, super - 1));
    if (UNLIKELY(t->exception)) return 0;

    set(t, classSuper(t, class_), sc);

    classVmFlags(t, class_) |= classVmFlags(t, sc);
  }
  
  parseInterfaceTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  parseFieldTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  parseMethodTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  return class_;
}

void
updateBootstrapClass(Thread* t, object bootstrapClass, object class_)
{
  expect(t, bootstrapClass != class_);

  // verify that the classes have the same layout
  expect(t, classSuper(t, bootstrapClass) == classSuper(t, class_));
  expect(t, classFixedSize(t, bootstrapClass) == classFixedSize(t, class_));
  expect(t, (classObjectMask(t, bootstrapClass) == 0
             and classObjectMask(t, class_) == 0)
         or intArrayEqual(t, classObjectMask(t, bootstrapClass),
                          classObjectMask(t, class_)));

  PROTECT(t, bootstrapClass);
  PROTECT(t, class_);

  ENTER(t, Thread::ExclusiveState);

  classVmFlags(t, class_) |= classVmFlags(t, bootstrapClass);

  memcpy(bootstrapClass,
         class_,
         extendedSize(t, class_, baseSize(t, class_, objectClass(t, class_)))
         * BytesPerWord);

  object fieldTable = classFieldTable(t, class_);
  if (fieldTable) {
    for (unsigned i = 0; i < arrayLength(t, fieldTable); ++i) {
      set(t, fieldClass(t, arrayBody(t, fieldTable, i)), bootstrapClass);
    }
  }

  object methodTable = classMethodTable(t, class_);
  if (methodTable) {
    for (unsigned i = 0; i < arrayLength(t, methodTable); ++i) {
      set(t, methodClass(t, arrayBody(t, methodTable, i)), bootstrapClass);
    }
  }
}

object
resolveClass(Thread* t, object spec)
{
  PROTECT(t, spec);
  ACQUIRE(t, t->vm->classLock);

  object class_ = hashMapFind
    (t, t->vm->classMap, spec, byteArrayHash, byteArrayEqual);
  if (class_ == 0) {
    ClassFinder::Data* data = t->vm->classFinder->find
      (reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0)));

    if (data) {
      if (Verbose) {
        fprintf(stderr, "parsing %s\n", &byteArrayBody
                (t, spec, 0));
      }

      // parse class file
      class_ = parseClass(t, data->start(), data->length());
      data->dispose();

      if (Verbose) {
        fprintf(stderr, "done parsing %s\n", &byteArrayBody
                (t, className(t, class_), 0));
      }

      PROTECT(t, class_);

      object bootstrapClass = hashMapFind
        (t, t->vm->bootstrapClassMap, spec, byteArrayHash, byteArrayEqual);

      if (bootstrapClass) {
        PROTECT(t, bootstrapClass);

        updateBootstrapClass(t, bootstrapClass, class_);
        class_ = bootstrapClass;
      }

      hashMapInsert(t, t->vm->classMap, spec, class_, byteArrayHash);
    } else {
      object message = makeString(t, "%s", &byteArrayBody(t, spec, 0));
      t->exception = makeClassNotFoundException(t, message);
    }
  }
  return class_;
}

inline object
resolveClass(Thread* t, object pool, unsigned index)
{
  object o = arrayBody(t, pool, index);
  if (objectClass(t, o) == arrayBody(t, t->vm->types, Machine::ByteArrayType))
  {
    PROTECT(t, pool);

    o = resolveClass(t, o);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, arrayBody(t, pool, index), o);
  }
  return o; 
}

inline object
resolveClass(Thread* t, object container, object& (*class_)(Thread*, object))
{
  object o = class_(t, container);
  if (objectClass(t, o) == arrayBody(t, t->vm->types, Machine::ByteArrayType))
  {
    PROTECT(t, container);

    o = resolveClass(t, o);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, class_(t, container), o);
  }
  return o; 
}

inline object
resolve(Thread* t, object pool, unsigned index,
        object (*find)(Thread*, object, object))
{
  object o = arrayBody(t, pool, index);
  if (objectClass(t, o) == arrayBody(t, t->vm->types, Machine::ReferenceType))
  {
    PROTECT(t, pool);

    object class_ = resolveClass(t, o, referenceClass);
    if (UNLIKELY(t->exception)) return 0;

    o = find(t, class_, arrayBody(t, pool, index));
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, arrayBody(t, pool, index), o);
  }
  return o;
}

inline object
resolveField(Thread* t, object pool, unsigned index)
{
  return resolve(t, pool, index, findFieldInClass);
}

inline object
resolveMethod(Thread* t, object pool, unsigned index)
{
  return resolve(t, pool, index, findMethodInClass);
}

object
makeNativeMethodData(Thread* t, object method, void* function, bool builtin)
{
  PROTECT(t, method);

  object data = makeNativeMethodData(t,
                                     function,
                                     0, // argument table size
                                     0, // return code,
                                     builtin,
                                     methodParameterCount(t, method) + 1,
                                     false);
        
  unsigned argumentTableSize = BytesPerWord;
  unsigned index = 0;

  nativeMethodDataParameterTypes(t, data, index++) = POINTER_TYPE;

  if ((methodFlags(t, method) & ACC_STATIC) == 0) {
    nativeMethodDataParameterTypes(t, data, index++) = POINTER_TYPE;
    argumentTableSize += BytesPerWord;
  }

  const char* s = reinterpret_cast<const char*>
    (&byteArrayBody(t, methodSpec(t, method), 0));
  ++ s; // skip '('
  while (*s and *s != ')') {
    unsigned code = fieldCode(t, *s);
    nativeMethodDataParameterTypes(t, data, index++) = fieldType(t, code);

    switch (*s) {
    case 'L':
      argumentTableSize += BytesPerWord;
      while (*s and *s != ';') ++ s;
      ++ s;
      break;

    case '[':
      argumentTableSize += BytesPerWord;
      while (*s == '[') ++ s;
      break;
      
    default:
      argumentTableSize += pad(primitiveSize(t, code));
      ++ s;
      break;
    }
  }

  nativeMethodDataArgumentTableSize(t, data) = argumentTableSize;
  nativeMethodDataReturnCode(t, data) = fieldCode(t, s[1]);

  return data;
}

inline object
resolveNativeMethodData(Thread* t, object method)
{
  if (objectClass(t, methodCode(t, method))
      == arrayBody(t, t->vm->types, Machine::ByteArrayType))
  {
    object data = 0;
    for (System::Library* lib = t->vm->libraries; lib; lib = lib->next()) {
      void* p = lib->resolve(reinterpret_cast<const char*>
                             (&byteArrayBody(t, methodCode(t, method), 0)));
      if (p) {
        PROTECT(t, method);
        data = makeNativeMethodData(t, method, p, false);
        break;
      }
    }

    if (data == 0) {
      object p = hashMapFind(t, t->vm->builtinMap, methodCode(t, method),
                             byteArrayHash, byteArrayEqual);
      if (p) {
        PROTECT(t, method);
        data = makeNativeMethodData(t, method, pointerValue(t, p), true);
      }
    }

    if (LIKELY(data)) {
      set(t, methodCode(t, method), data);
    } else {
      object message = makeString
        (t, "%s", &byteArrayBody(t, methodCode(t, method), 0));
      t->exception = makeUnsatisfiedLinkError(t, message);
    }

    return data;
  } else {
    return methodCode(t, method);
  }  
}

inline void
checkStack(Thread* t, object method)
{
  unsigned parameterFootprint = methodParameterFootprint(t, method);
  unsigned base = t->sp - parameterFootprint;
  if (UNLIKELY(base
               + codeMaxLocals(t, methodCode(t, method))
               + FrameFootprint
               + codeMaxStack(t, methodCode(t, method))
               > Thread::StackSizeInWords / 2))
  {
    t->exception = makeStackOverflowError(t);
  }
}

unsigned
invokeNative(Thread* t, object method)
{
  object data = resolveNativeMethodData(t, method);
  if (UNLIKELY(t->exception)) {
    return VoidField;
  }

  pushFrame(t, method);

  unsigned count = methodParameterCount(t, method);

  unsigned size = nativeMethodDataArgumentTableSize(t, data);
  uintptr_t args[size / BytesPerWord];
  unsigned offset = 0;

  args[offset++] = reinterpret_cast<uintptr_t>(t);

  unsigned sp = frameBase(t, t->frame);
  for (unsigned i = 0; i < count; ++i) {
    unsigned type = nativeMethodDataParameterTypes(t, data, i + 1);

    switch (type) {
    case INT8_TYPE:
    case INT16_TYPE:
    case INT32_TYPE:
    case FLOAT_TYPE:
      args[offset++] = peekInt(t, sp++);
      break;

    case INT64_TYPE:
    case DOUBLE_TYPE: {
      uint64_t v = peekLong(t, sp);
      memcpy(args + offset, &v, 8);
      offset += (8 / BytesPerWord);
      sp += 2;
    } break;

    case POINTER_TYPE:
      args[offset++] = reinterpret_cast<uintptr_t>
        (t->stack + ((sp++) * 2) + 1);
      break;

    default: abort(t);
    }
  }

  unsigned returnCode = nativeMethodDataReturnCode(t, data);
  unsigned returnType = fieldType(t, returnCode);
  void* function = nativeMethodDataFunction(t, data);

  bool builtin = nativeMethodDataBuiltin(t, data);
  Thread::State oldState = t->state;
  if (not builtin) {    
    enter(t, Thread::IdleState);
  }

  uint64_t result = t->vm->system->call
    (function,
     args,
     &nativeMethodDataParameterTypes(t, data, 0),
     count + 1,
     size,
     returnType);

  if (not builtin) {
    enter(t, oldState);
  }

  popFrame(t);

  if (UNLIKELY(t->exception)) {
    return VoidField;
  }

  switch (returnCode) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    pushInt(t, result);
    break;

  case LongField:
  case DoubleField:
    pushLong(t, result);
    break;

  case ObjectField:
    pushObject(t, result == 0 ? 0 :
               *reinterpret_cast<object*>(static_cast<uintptr_t>(result)));
    break;

  case VoidField:
    break;

  default:
    abort(t);
  };

  return returnCode;
}

object
run(Thread* t)
{
  unsigned instruction = nop;
  unsigned& ip = t->ip;
  unsigned& sp = t->sp;
  int& frame = t->frame;
  object& code = t->code;
  object& exception = t->exception;
  uintptr_t* stack = t->stack;

  if (UNLIKELY(exception)) goto throw_;

 loop:
  instruction = codeBody(t, code, ip++);

  if (DebugRun) {
    fprintf(stderr, "ip: %d; instruction: 0x%x in %s.%s ",
            ip - 1,
            instruction,
            &byteArrayBody
            (t, className(t, methodClass(t, frameMethod(t, frame))), 0),
            &byteArrayBody
            (t, methodName(t, frameMethod(t, frame)), 0));

    int line = lineNumber(t, frameMethod(t, frame), ip);
    switch (line) {
    case NativeLine:
      fprintf(stderr, "(native)\n");
      break;
    case UnknownLine:
      fprintf(stderr, "(unknown line)\n");
      break;
    default:
      fprintf(stderr, "(line %d)\n", line);
    }
  }

  switch (instruction) {
  case aaload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < objectArrayLength(t, array)))
      {
        pushObject(t, objectArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    objectArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case aastore: {
    object value = popObject(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < objectArrayLength(t, array)))
      {
        set(t, objectArrayBody(t, array, index), value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    objectArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case aconst_null: {
    pushObject(t, 0);
  } goto loop;

  case aload: {
    pushObject(t, localObject(t, codeBody(t, code, ip++)));
  } goto loop;

  case aload_0: {
    pushObject(t, localObject(t, 0));
  } goto loop;

  case aload_1: {
    pushObject(t, localObject(t, 1));
  } goto loop;

  case aload_2: {
    pushObject(t, localObject(t, 2));
  } goto loop;

  case aload_3: {
    pushObject(t, localObject(t, 3));
  } goto loop;

  case anewarray: {
    int32_t count = popInt(t);

    if (LIKELY(count >= 0)) {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;
      
      object array = makeObjectArray(t, class_, count, true);
      
      pushObject(t, array);
    } else {
      object message = makeString(t, "%d", count);
      exception = makeNegativeArrayStoreException(t, message);
      goto throw_;
    }
  } goto loop;

  case areturn:
  case ireturn:
  case lreturn: {
    popFrame(t);
    if (frame >= 0) {
      goto loop;
    } else {
      switch (instruction) {
      case areturn:
        return popObject(t);

      case ireturn:
        return makeInt(t, popInt(t));

      case lreturn:
        return makeLong(t, popLong(t));
      }
    }
  } goto loop;

  case arraylength: {
    object array = popObject(t);
    if (LIKELY(array)) {
      if (objectClass(t, array)
          == arrayBody(t, t->vm->types, Machine::ObjectArrayType))
      {
        pushInt(t, objectArrayLength(t, array));
      } else {
        // for all other array types, the length follow the class pointer.
        pushInt(t, cast<uint32_t>(array, BytesPerWord));
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } abort(t);

  case astore: {
    setLocalObject(t, codeBody(t, code, ip++), popObject(t));
  } goto loop;

  case astore_0: {
    setLocalObject(t, 0, popObject(t));
  } goto loop;

  case astore_1: {
    setLocalObject(t, 1, popObject(t));
  } goto loop;

  case astore_2: {
    setLocalObject(t, 2, popObject(t));
  } goto loop;

  case astore_3: {
    setLocalObject(t, 3, popObject(t));
  } goto loop;

  case athrow: {
    exception = popObject(t);
    if (UNLIKELY(exception == 0)) {
      exception = makeNullPointerException(t);      
    }
  } goto throw_;

  case baload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < byteArrayLength(t, array)))
      {
        pushInt(t, byteArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    byteArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case bastore: {
    int8_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < byteArrayLength(t, array)))
      {
        byteArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    byteArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case bipush: {
    pushInt(t, codeBody(t, code, ip++));
  } goto loop;

  case caload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < charArrayLength(t, array)))
      {
        pushInt(t, charArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    charArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case castore: {
    uint16_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < charArrayLength(t, array)))
      {
        charArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    charArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case checkcast: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    if (peekObject(t, sp - 1)) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;

      if (not instanceOf(t, class_, peekObject(t, sp - 1))) {
        object message = makeString
          (t, "%s as %s",
           &byteArrayBody
           (t, className(t, objectClass(t, peekObject(t, sp - 1))), 0),
           &byteArrayBody(t, className(t, class_), 0));
        exception = makeClassCastException(t, message);
        goto throw_;
      }
    }
  } goto loop;

  case dup: {
    if (DebugStack) {
      fprintf(stderr, "dup\n");
    }

    memcpy(stack + ((sp    ) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    ++ sp;
  } goto loop;

  case dup_x1: {
    if (DebugStack) {
      fprintf(stderr, "dup_x1\n");
    }

    memcpy(stack + ((sp    ) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp    ) * 2), BytesPerWord * 2);
    ++ sp;
  } goto loop;

  case dup_x2: {
    if (DebugStack) {
      fprintf(stderr, "dup_x2\n");
    }

    memcpy(stack + ((sp    ) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 3) * 2), stack + ((sp    ) * 2), BytesPerWord * 2);
    ++ sp;
  } goto loop;

  case dup2: {
    if (DebugStack) {
      fprintf(stderr, "dup2\n");
    }

    memcpy(stack + ((sp + 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 4);
    sp += 2;
  } goto loop;

  case dup2_x1: {
    if (DebugStack) {
      fprintf(stderr, "dup2_x1\n");
    }

    memcpy(stack + ((sp + 1) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp    ) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 3) * 2), stack + ((sp    ) * 2), BytesPerWord * 4);
    sp += 2;
  } goto loop;

  case dup2_x2: {
    if (DebugStack) {
      fprintf(stderr, "dup2_x2\n");
    }

    memcpy(stack + ((sp + 1) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp    ) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp - 4) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 4) * 2), stack + ((sp    ) * 2), BytesPerWord * 4);
    sp += 2;
  } goto loop;

  case getfield: {
    object instance = popObject(t);

    if (LIKELY(instance)) {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      uint16_t index = (index1 << 8) | index2;
    
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;
      
      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        pushInt(t, cast<int8_t>(instance, fieldOffset(t, field)));
        break;

      case CharField:
      case ShortField:
        pushInt(t, cast<int16_t>(instance, fieldOffset(t, field)));
        break;

      case FloatField:
      case IntField:
        pushInt(t, cast<int32_t>(instance, fieldOffset(t, field)));
        break;

      case DoubleField:
      case LongField:
        pushLong(t, cast<int64_t>(instance, fieldOffset(t, field)));
        break;

      case ObjectField:
        pushObject(t, cast<object>(instance, fieldOffset(t, field)));
        break;

      default:
        abort(t);
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case getstatic: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object field = resolveField(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;

    object clinit = classInitializer(t, fieldClass(t, field));
    if (clinit) {
      set(t, classInitializer(t, fieldClass(t, field)), 0);
      code = clinit;
      ip -= 3;
      goto invoke;
    }

    object v = arrayBody(t, classStaticTable(t, fieldClass(t, field)),
                         fieldOffset(t, field));

    switch (fieldCode(t, field)) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField:
      pushInt(t, intValue(t, v));
      break;

    case DoubleField:
    case LongField:
      pushLong(t, longValue(t, v));
      break;

    case ObjectField:
      pushObject(t, v);
      break;

    default: abort(t);
    }
  } goto loop;

  case goto_: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
  } goto loop;
    
  case goto_w: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);
    uint8_t offset3 = codeBody(t, code, ip++);
    uint8_t offset4 = codeBody(t, code, ip++);

    ip = (ip - 5) + static_cast<int32_t>
      (((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4));
  } goto loop;

  case i2b: {
    pushInt(t, static_cast<int8_t>(popInt(t)));
  } goto loop;

  case i2c: {
    pushInt(t, static_cast<uint16_t>(popInt(t)));
  } goto loop;

  case i2l: {
    pushLong(t, popInt(t));
  } goto loop;

  case i2s: {
    pushInt(t, static_cast<int16_t>(popInt(t)));
  } goto loop;

  case iadd: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a + b);
  } goto loop;

  case iaload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < intArrayLength(t, array)))
      {
        pushInt(t, intArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    intArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case iand: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a & b);
  } goto loop;

  case iastore: {
    int32_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < intArrayLength(t, array)))
      {
        intArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    intArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case iconst_0: {
    pushInt(t, 0);
  } goto loop;

  case iconst_1: {
    pushInt(t, 1);
  } goto loop;

  case iconst_2: {
    pushInt(t, 2);
  } goto loop;

  case iconst_3: {
    pushInt(t, 3);
  } goto loop;

  case iconst_4: {
    pushInt(t, 4);
  } goto loop;

  case iconst_5: {
    pushInt(t, 5);
  } goto loop;

  case idiv: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a / b);
  } goto loop;

  case if_acmpeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a == b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_acmpne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a != b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a == b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a != b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpgt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a > b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpge: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a >= b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmplt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a < b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmple: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a < b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (popInt(t) == 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (popInt(t)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifgt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (static_cast<int32_t>(popInt(t)) > 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifge: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (static_cast<int32_t>(popInt(t)) >= 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case iflt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (static_cast<int32_t>(popInt(t)) < 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifle: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (static_cast<int32_t>(popInt(t)) <= 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifnonnull: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (popObject(t)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifnull: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (popObject(t) == 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case iinc: {
    uint8_t index = codeBody(t, code, ip++);
    int8_t c = codeBody(t, code, ip++);
    
    setLocalInt(t, index, localInt(t, index) + c);
  } goto loop;

  case iload: {
    pushInt(t, localInt(t, codeBody(t, code, ip++)));
  } goto loop;

  case iload_0: {
    pushInt(t, localInt(t, 0));
  } goto loop;

  case iload_1: {
    pushInt(t, localInt(t, 1));
  } goto loop;

  case iload_2: {
    pushInt(t, localInt(t, 2));
  } goto loop;

  case iload_3: {
    pushInt(t, localInt(t, 3));
  } goto loop;

  case imul: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a * b);
  } goto loop;

  case ineg: {
    pushInt(t, - popInt(t));
  } goto loop;

  case instanceof: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    if (peekObject(t, sp - 1)) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;

      if (instanceOf(t, class_, peekObject(t, sp - 1))) {
        pushInt(t, 1);
      } else {
        pushInt(t, 0);
      }
    } else {
      pushInt(t, 0);
    }
  } goto loop;

  case invokeinterface: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;
    
    ip += 2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    unsigned parameterFootprint = methodParameterFootprint(t, method);
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      code = findInterfaceMethod
        (t, method, peekObject(t, sp - parameterFootprint));
      if (UNLIKELY(exception)) goto throw_;

      goto invoke;
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case invokespecial: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    unsigned parameterFootprint = methodParameterFootprint(t, method);
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      object class_ = methodClass(t, frameMethod(t, frame));
      if (isSpecialMethod(t, method, class_)) {
        code = findMethod(t, method, classSuper(t, class_));
        if (UNLIKELY(exception)) goto throw_;
      } else {
        code = method;
      }
      
      goto invoke;
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case invokestatic: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    object clinit = classInitializer(t, methodClass(t, method));
    if (clinit) {
      set(t, classInitializer(t, methodClass(t, method)), 0);
      code = clinit;
      ip -= 3;
      goto invoke;
    }

    code = method;
  } goto invoke;

  case invokevirtual: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    unsigned parameterFootprint = methodParameterFootprint(t, method);
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      code = findVirtualMethod
        (t, method, peekObject(t, sp - parameterFootprint));
      if (UNLIKELY(exception)) goto throw_;
      
      goto invoke;
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case ior: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a | b);
  } goto loop;

  case irem: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a % b);
  } goto loop;

  case ishl: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a << b);
  } goto loop;

  case ishr: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a >> b);
  } goto loop;

  case istore: {
    setLocalInt(t, codeBody(t, code, ip++), popInt(t));
  } goto loop;

  case istore_0: {
    setLocalInt(t, 0, popInt(t));
  } goto loop;

  case istore_1: {
    setLocalInt(t, 1, popInt(t));
  } goto loop;

  case istore_2: {
    setLocalInt(t, 2, popInt(t));
  } goto loop;

  case istore_3: {
    setLocalInt(t, 3, popInt(t));
  } goto loop;

  case isub: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a - b);
  } goto loop;

  case iushr: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, static_cast<uint32_t>(a >> b));
  } goto loop;

  case ixor: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a ^ b);
  } goto loop;

  case jsr: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    pushInt(t, ip);
    ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
  } goto loop;

  case jsr_w: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);
    uint8_t offset3 = codeBody(t, code, ip++);
    uint8_t offset4 = codeBody(t, code, ip++);

    pushInt(t, ip);
    ip = (ip - 3) + static_cast<int32_t>
      ((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4);
  } goto loop;

  case l2i: {
    pushLong(t, static_cast<int32_t>(popLong(t)));
  } goto loop;

  case ladd: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a + b);
  } goto loop;

  case laload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < longArrayLength(t, array)))
      {
        pushLong(t, longArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    longArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case land: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a & b);
  } goto loop;

  case lastore: {
    int64_t value = popLong(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < longArrayLength(t, array)))
      {
        longArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    longArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case lcmp: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushInt(t, a > b ? 1 : a == b ? 0 : -1);
  } goto loop;

  case lconst_0: {
    pushLong(t, 0);
  } goto loop;

  case lconst_1: {
    pushLong(t, 1);
  } goto loop;

  case ldc:
  case ldc_w: {
    uint16_t index;

    if (instruction == ldc) {
      index = codeBody(t, code, ip++);
    } else {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      index = (index1 << 8) | index2;
    }

    object v = arrayBody(t, codePool(t, code), index - 1);

    if (objectClass(t, v) == arrayBody(t, t->vm->types, Machine::IntType)) {
      pushInt(t, intValue(t, v));
    } else if (objectClass(t, v)
               == arrayBody(t, t->vm->types, Machine::StringType))
    {
      pushObject(t, v);
    } else if (objectClass(t, v)
               == arrayBody(t, t->vm->types, Machine::FloatType))
    {
      pushInt(t, floatValue(t, v));
    }
  } goto loop;

  case ldc2_w: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    object v = arrayBody(t, codePool(t, code), ((index1 << 8) | index2) - 1);

    if (objectClass(t, v) == arrayBody(t, t->vm->types, Machine::LongType)) {
      pushLong(t, longValue(t, v));
    } else if (objectClass(t, v)
               == arrayBody(t, t->vm->types, Machine::DoubleType))
    {
      pushLong(t, doubleValue(t, v));
    }
  } goto loop;

  case vm::ldiv: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a / b);
  } goto loop;

  case lload: {
    pushLong(t, localLong(t, codeBody(t, code, ip++)));
  } goto loop;

  case lload_0: {
    pushLong(t, localLong(t, 0));
  } goto loop;

  case lload_1: {
    pushLong(t, localLong(t, 1));
  } goto loop;

  case lload_2: {
    pushLong(t, localLong(t, 2));
  } goto loop;

  case lload_3: {
    pushLong(t, localLong(t, 3));
  } goto loop;

  case lmul: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a * b);
  } goto loop;

  case lneg: {
    pushLong(t, - popInt(t));
  } goto loop;

  case lor: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a | b);
  } goto loop;

  case lrem: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a % b);
  } goto loop;

  case lshl: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a << b);
  } goto loop;

  case lshr: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a >> b);
  } goto loop;

  case lstore: {
    setLocalLong(t, codeBody(t, code, ip++), popLong(t));
  } goto loop;

  case lstore_0: {
    setLocalLong(t, 0, popLong(t));
  } goto loop;

  case lstore_1: {
    setLocalLong(t, 1, popLong(t));
  } goto loop;

  case lstore_2: {
    setLocalLong(t, 2, popLong(t));
  } goto loop;

  case lstore_3: {
    setLocalLong(t, 3, popLong(t));
  } goto loop;

  case lsub: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a - b);
  } goto loop;

  case lushr: {
    uint64_t b = popLong(t);
    uint64_t a = popLong(t);
    
    pushLong(t, a >> b);
  } goto loop;

  case lxor: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a ^ b);
  } goto loop;

  case monitorenter: {
    object o = popObject(t);
    if (LIKELY(o)) {
      objectMonitor(t, o)->acquire(t);
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case monitorexit: {
    object o = popObject(t);
    if (LIKELY(o)) {
      objectMonitor(t, o)->release(t);
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case new_: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;
    
    object class_ = resolveClass(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;

    object clinit = classInitializer(t, class_);
    if (clinit) {
      set(t, classInitializer(t, class_), 0);
      code = clinit;
      ip -= 3;
      goto invoke;
    }

    pushObject(t, make(t, class_));
  } goto loop;

  case newarray: {
    int32_t count = popInt(t);

    if (LIKELY(count >= 0)) {
      uint8_t type = codeBody(t, code, ip++);

      object array;

      switch (type) {
      case T_BOOLEAN:
        array = makeBooleanArray(t, count, true);
        break;

      case T_CHAR:
        array = makeCharArray(t, count, true);
        break;

      case T_FLOAT:
        array = makeFloatArray(t, count, true);
        break;

      case T_DOUBLE:
        array = makeDoubleArray(t, count, true);
        break;

      case T_BYTE:
        array = makeByteArray(t, count, true);
        break;

      case T_SHORT:
        array = makeShortArray(t, count, true);
        break;

      case T_INT:
        array = makeIntArray(t, count, true);
        break;

      case T_LONG:
        array = makeLongArray(t, count, true);
        break;

      default: abort(t);
      }
            
      pushObject(t, array);
    } else {
      object message = makeString(t, "%d", count);
      exception = makeNegativeArrayStoreException(t, message);
      goto throw_;
    }
  } goto loop;

  case nop: goto loop;

  case pop_: {
    -- sp;
  } goto loop;

  case pop2: {
    sp -= 2;
  } goto loop;

  case putfield: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;
    
    object field = resolveField(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
      
    switch (fieldCode(t, field)) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField: {
      int32_t value = popInt(t);
      object o = popObject(t);
      if (LIKELY(o)) {
        switch (fieldCode(t, field)) {
        case ByteField:
        case BooleanField:
          cast<int8_t>(o, fieldOffset(t, field)) = value;
          break;
            
        case CharField:
        case ShortField:
          cast<int16_t>(o, fieldOffset(t, field)) = value;
          break;
            
        case FloatField:
        case IntField:
          cast<int32_t>(o, fieldOffset(t, field)) = value;
          break;
        }
      } else {
        exception = makeNullPointerException(t);
        goto throw_;
      }
    } break;

    case DoubleField:
    case LongField: {
      int64_t value = popLong(t);
      object o = popObject(t);
      if (LIKELY(o)) {
        cast<int64_t>(o, fieldOffset(t, field)) = value;
      } else {
        exception = makeNullPointerException(t);
        goto throw_;
      }
    } break;

    case ObjectField: {
      object value = popObject(t);
      object o = popObject(t);
      if (LIKELY(o)) {
        set(t, cast<object>(o, fieldOffset(t, field)), value);
      } else {
        exception = makeNullPointerException(t);
        goto throw_;
      }
    } break;

    default: abort(t);
    }
  } goto loop;

  case putstatic: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object field = resolveField(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;

    object clinit = classInitializer(t, fieldClass(t, field));
    if (clinit) {
      set(t, classInitializer(t, fieldClass(t, field)), 0);
      code = clinit;
      ip -= 3;
      goto invoke;
    }

    PROTECT(t, field);
      
    object v;

    switch (fieldCode(t, field)) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField: {
      v = makeInt(t, popInt(t));
    } break;

    case DoubleField:
    case LongField: {
      v = makeLong(t, popLong(t));
    } break;

    case ObjectField:
      v = popObject(t);
      break;

    default: abort(t);
    }

    set(t, arrayBody(t, classStaticTable(t, fieldClass(t, field)),
                     fieldOffset(t, field)), v);
  } goto loop;

  case ret: {
    ip = localInt(t, codeBody(t, code, ip));
  } goto loop;

  case return_: {
    popFrame(t);
    if (frame >= 0) {
      goto loop;
    } else {
      return 0;
    }
  } goto loop;

  case saload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < shortArrayLength(t, array)))
      {
        pushInt(t, shortArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    shortArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case sastore: {
    int16_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < shortArrayLength(t, array)))
      {
        shortArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    shortArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case sipush: {
    uint8_t byte1 = codeBody(t, code, ip++);
    uint8_t byte2 = codeBody(t, code, ip++);

    pushInt(t, (byte1 << 8) | byte2);
  } goto loop;

  case swap: {
    uintptr_t tmp[2];
    memcpy(tmp                   , stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), tmp                   , BytesPerWord * 2);
  } goto loop;

  case wide: goto wide;

  default: abort(t);
  }

 wide:
  switch (codeBody(t, code, ip++)) {
  case aload: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    pushObject(t, localObject(t, (index1 << 8) | index2));
  } goto loop;

  case astore: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    setLocalObject(t, (index1 << 8) | index2, popObject(t));
  } goto loop;

  case iinc: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    uint8_t count1 = codeBody(t, code, ip++);
    uint8_t count2 = codeBody(t, code, ip++);
    uint16_t count = (count1 << 8) | count2;
    
    setLocalInt(t, index, localInt(t, index) + count);
  } goto loop;

  case iload: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    pushInt(t, localInt(t, (index1 << 8) | index2));
  } goto loop;

  case istore: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    setLocalInt(t, (index1 << 8) | index2, popInt(t));
  } goto loop;

  case lload: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    pushLong(t, localLong(t, (index1 << 8) | index2));
  } goto loop;

  case lstore: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    setLocalLong(t, (index1 << 8) | index2,  popLong(t));
  } goto loop;

  case ret: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    ip = localInt(t, (index1 << 8) | index2);
  } goto loop;

  default: abort(t);
  }

 invoke: {
    if (methodFlags(t, code) & ACC_NATIVE) {
      invokeNative(t, code);
      if (UNLIKELY(exception)) goto throw_;
    } else {
      checkStack(t, code);
      if (UNLIKELY(exception)) goto throw_;

      pushFrame(t, code);
    }
  } goto loop;

 throw_:
  for (; frame >= 0; frame = frameNext(t, frame)) {
    if (methodFlags(t, frameMethod(t, frame)) & ACC_NATIVE) {
      return 0;
    }

    code = methodCode(t, frameMethod(t, frame));
    object eht = codeExceptionHandlerTable(t, code);
    if (eht) {
      for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
        ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);
        if (frameIp(t, frame) >= exceptionHandlerStart(eh)
            and frameIp(t, frame) >= exceptionHandlerEnd(eh))
        {
          object catchType = 0;
          if (exceptionHandlerCatchType(eh)) {
            catchType = arrayBody
              (t, codePool(t, code), exceptionHandlerCatchType(eh) - 1);
          }

          if (catchType == 0 or
              (objectClass(t, catchType)
               == arrayBody(t, t->vm->types, Machine::ClassType) and
               instanceOf(t, catchType, exception)))
          {
            sp = frameBase(t, frame);
            ip = exceptionHandlerIp(eh);
            pushObject(t, exception);
            exception = 0;
            goto loop;
          }
        }
      }
    }
  }

  for (object e = exception; e; e = throwableCause(t, e)) {
    if (e == exception) {
      fprintf(stderr, "uncaught exception: ");
    } else {
      fprintf(stderr, "caused by: ");
    }

    fprintf(stderr, "%s", &byteArrayBody
            (t, className(t, objectClass(t, exception)), 0));
  
    if (throwableMessage(t, exception)) {
      object m = throwableMessage(t, exception);
      char message[stringLength(t, m) + 1];
      memcpy(message,
             &byteArrayBody(t, stringBytes(t, m), stringOffset(t, m)),
             stringLength(t, m));
      message[stringLength(t, m)] = 0;
      fprintf(stderr, ": %s\n", message);
    } else {
      fprintf(stderr, "\n");
    }

    object trace = throwableTrace(t, e);
    for (unsigned i = 0; i < objectArrayLength(t, trace); ++i) {
      object e = objectArrayBody(t, trace, i);
      const int8_t* class_ = &byteArrayBody
        (t, className(t, methodClass(t, stackTraceElementMethod(t, e))), 0);
      const int8_t* method = &byteArrayBody
        (t, methodName(t, stackTraceElementMethod(t, e)), 0);
      int line = lineNumber
        (t, stackTraceElementMethod(t, e), stackTraceElementIp(t, e));

      fprintf(stderr, "  at %s.%s ", class_, method);

      switch (line) {
      case NativeLine:
        fprintf(stderr, "(native)\n");
        break;
      case UnknownLine:
        fprintf(stderr, "(unknown line)\n");
        break;
      default:
        fprintf(stderr, "(line %d)\n", line);
      }
    }
  }

  return 0;
}

void
run(Thread* t, const char* className, int argc, const char** argv)
{
  object args = makeObjectArray
    (t, arrayBody(t, t->vm->types, Machine::StringType), argc, true);

  PROTECT(t, args);

  for (int i = 0; i < argc; ++i) {
    object arg = makeString(t, "%s", argv);
    set(t, objectArrayBody(t, args, i), arg);
  }

  run(t, className, "main", "([Ljava/lang/String;)V", args);
}

} // namespace

namespace vm {

object
run(Thread* t, const char* className, const char* methodName,
    const char* methodSpec, ...)
{
  enter(t, Thread::ActiveState);

  object class_ = resolveClass(t, makeByteArray(t, "%s", className));
  if (LIKELY(t->exception == 0)) {
    PROTECT(t, class_);

    object name = makeByteArray(t, methodName);
    PROTECT(t, name);

    object spec = makeByteArray(t, methodSpec);
    object reference = makeReference(t, class_, name, spec);
    
    object method = findMethodInClass(t, class_, reference);
    if (LIKELY(t->exception == 0)) {
      va_list a;
      va_start(a, methodSpec);

      if ((methodFlags(t, method) & ACC_STATIC) == 0) {
        pushObject(t, va_arg(a, object));
      }

      const char* s = methodSpec;
      ++ s; // skip '('
      while (*s and *s != ')') {
        switch (*s) {
        case 'L':
          while (*s and *s != ';') ++ s;
          ++ s;
          pushObject(t, va_arg(a, object));
          break;

        case '[':
          while (*s == '[') ++ s;
          switch (*s) {
          case 'L':
            while (*s and *s != ';') ++ s;
            ++ s;
            break;

          default:
            ++ s;
            break;
          }
          pushObject(t, va_arg(a, object));
          break;
      
        case 'J':
        case 'D':
          ++ s;
          pushLong(t, va_arg(a, uint64_t));
          break;
          
        default:
          ++ s;
          pushInt(t, va_arg(a, uint32_t));
          break;
        }
      }

      va_end(a);

      if (methodFlags(t, method) & ACC_NATIVE) {
        unsigned returnCode = invokeNative(t, method);

        if (LIKELY(t->exception == 0)) {
          switch (returnCode) {
          case ByteField:
          case BooleanField:
          case CharField:
          case ShortField:
          case FloatField:
          case IntField:
            return makeInt(t, popInt(t));

          case LongField:
          case DoubleField:
            return makeLong(t, popLong(t));
        
          case ObjectField:
            return popObject(t);

          case VoidField:
            return 0;

          default:
            abort(t);
          };
        }
      } else {
        checkStack(t, method);
        if (LIKELY(t->exception == 0)) {
          pushFrame(t, method);
        }
      }
    }    
  }

  return ::run(t);
}

void
run(System* system, Heap* heap, ClassFinder* classFinder,
    const char* className, int argc, const char** argv)
{
  Machine m(system, heap, classFinder);
  Thread t(&m, 0, 0, 0);

  ::run(&t, className, argc, argv);

  exit(&t);
}

}
