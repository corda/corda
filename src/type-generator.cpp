#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>

#include "input.h"
#include "output.h"

#define UNREACHABLE abort()

namespace {

inline unsigned
pad(unsigned size, unsigned alignment)
{
  unsigned n = alignment;
  while (size and n % size and n % sizeof(void*)) ++ n;
  return n - alignment;
}

inline unsigned
pad(unsigned n)
{
  unsigned extra = n % sizeof(void*);
  return (extra ? n + sizeof(void*) - extra : n);
}

template <class T>
T*
allocate()
{
  T* t = static_cast<T*>(malloc(sizeof(T)));
  assert(t);
  return t;
}

inline bool
equal(const char* a, const char* b)
{
  return strcmp(a, b) == 0;
}

inline bool
startsWith(const char* a, const char* b)
{
  return strncmp(a, b, strlen(a)) == 0;
}

inline bool
endsWith(const char* a, const char* b)
{
  unsigned al = strlen(a);
  unsigned bl = strlen(b);
  return (bl >= al) and strncmp(a, b + (bl - al), al) == 0;
}

inline const char*
take(unsigned n, const char* c)
{
  char* r = static_cast<char*>(malloc(n + 1));
  assert(r);
  memcpy(r, c, n);
  r[n] = 0;
  return r;
}

class Object {
 public:
  typedef enum {
    Scalar,
    Array,
    Pod,
    Type,
    Pair,
    Number,
    Character,
    String,
    Eos
  } ObjectType;

  ObjectType type;
};

class Pair : public Object {
 public:
  Object* car;
  Object* cdr;

  static Pair* make(Object* car, Object* cdr) {
    Pair* o = allocate<Pair>();
    o->type = Object::Pair;
    o->car = car;
    o->cdr = cdr;
    return o;
  }
};

Object*
cons(Object* car, Object* cdr)
{
  return Pair::make(car, cdr);
}

Object*&
car(Object* o)
{
  assert(o->type == Object::Pair);
  return static_cast<Pair*>(o)->car;
}

Object*&
cdr(Object* o)
{
  assert(o->type == Object::Pair);
  return static_cast<Pair*>(o)->cdr;
}

void
setCdr(Object* o, Object* v)
{
  assert(o->type == Object::Pair);
  static_cast<Pair*>(o)->cdr = v;
}

unsigned
length(Object* o)
{
  unsigned c = 0;
  for (; o; o = cdr(o)) ++c;
  return c;
}

class List {
 public:
  Object* first;
  Object* last;

  List(): first(0), last(0) { }

  void append(Object* o) {
    Object* p = cons(o, 0);
    if (last) {
      setCdr(last, p);
      last = p;
    } else {
      first = last = p;
    }
  }
};

class Scalar : public Object {
 public:
  Object* owner;
  Object* typeObject;
  const char* typeName;
  const char* name;
  unsigned elementSize;
  bool noassert;
  bool nogc;
  bool hide;

  static Scalar* make(Object* owner, Object* typeObject, const char* typeName,
                      const char* name, unsigned size)
  {
    Scalar* o = allocate<Scalar>();
    o->type = Object::Scalar;
    o->owner = owner;
    o->typeObject = typeObject;
    o->typeName = typeName;
    o->name = name;
    o->elementSize = size;
    o->noassert = false;
    o->nogc = false;
    o->hide = false;
    return o;
  }
};

class Array : public Scalar {
 public:
  static Array* make(Object* owner, Object* typeObject, const char* typeName,
                     const char* name, unsigned elementSize)
  {
    Array* o = allocate<Array>();
    o->type = Object::Array;
    o->owner = owner;
    o->typeObject = typeObject;
    o->typeName = typeName;
    o->name = name;
    o->elementSize = elementSize;
    return o;
  }
};

unsigned
arrayElementSize(Object* o)
{
  switch (o->type) {
  case Object::Array:
    return static_cast<Array*>(o)->elementSize;

  default:
    UNREACHABLE;
  }
}

Object*
memberOwner(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
  case Object::Array:
    return static_cast<Scalar*>(o)->owner;

  default:
    UNREACHABLE;
  }
}

Object*
memberTypeObject(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
  case Object::Array:
    return static_cast<Scalar*>(o)->typeObject;

  default:
    UNREACHABLE;
  }
}

const char*
memberTypeName(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
  case Object::Array:
    return static_cast<Scalar*>(o)->typeName;

  default:
    UNREACHABLE;
  }
}

const char*
memberName(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
  case Object::Array:
    return static_cast<Scalar*>(o)->name;

  default:
    UNREACHABLE;
  }
}

unsigned
memberSize(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
    return static_cast<Scalar*>(o)->elementSize;

  default:
    UNREACHABLE;
  }
}

unsigned
memberElementSize(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
  case Object::Array:
    return static_cast<Scalar*>(o)->elementSize;

  default:
    UNREACHABLE;
  }
}

bool&
memberNoAssert(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
  case Object::Array:
    return static_cast<Scalar*>(o)->noassert;

  default:
    UNREACHABLE;
  }
}

bool&
memberNoGC(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
  case Object::Array:
    return static_cast<Scalar*>(o)->nogc;

  default:
    UNREACHABLE;
  }
}

bool
memberGC(Object* o)
{
  return not memberNoGC(o) and equal(memberTypeName(o), "object");
}

bool&
memberHide(Object* o)
{
  switch (o->type) {
  case Object::Scalar:
  case Object::Array:
    return static_cast<Scalar*>(o)->hide;

  default:
    UNREACHABLE;
  }
}

class Type : public Object {
 public:
  const char* name;
  const char* javaName;
  Object* super;
  List members;
  List subtypes;
  bool hideConstructor;

  static Type* make(Object::ObjectType type, const char* name,
                    const char* javaName)
  {
    Type* o = allocate<Type>();
    o->type = type;
    o->name = name;
    o->javaName = javaName;
    o->super = 0;
    o->members.first = o->members.last = 0;
    o->subtypes.first = o->subtypes.last = 0;
    o->hideConstructor = false;
    return o;
  }  
};

const char*
typeName(Object* o)
{
  switch (o->type) {
  case Object::Type: case Object::Pod:
    return static_cast<Type*>(o)->name;

  default:
    UNREACHABLE;
  }
}

const char*
typeJavaName(Object* o)
{
  switch (o->type) {
  case Object::Type: case Object::Pod:
    return static_cast<Type*>(o)->javaName;

  default:
    UNREACHABLE;
  }
}

Object*
typeMembers(Object* o)
{
  switch (o->type) {
  case Object::Type: case Object::Pod:
    return static_cast<Type*>(o)->members.first;

  default:
    UNREACHABLE;
  }
}

void
addMember(Object* o, Object* member)
{
  switch (o->type) {
  case Object::Type: case Object::Pod:
    if (member->type == Object::Array) {
      static_cast<Type*>(o)->members.append
        (Scalar::make(o, 0, "uintptr_t", "length", sizeof(uintptr_t)));
    }
    static_cast<Type*>(o)->members.append(member);
    break;

  default:
    UNREACHABLE;
  }
}

void
addSubtype(Object* o, Object* subtype)
{
  switch (o->type) {
  case Object::Type:
    static_cast<Type*>(o)->subtypes.append(subtype);
    break;

  default:
    UNREACHABLE;
  }
}

Object*
typeSubtypes(Object* o)
{
  switch (o->type) {
  case Object::Type:
    return static_cast<Type*>(o)->subtypes.first;

  default:
    UNREACHABLE;
  }
}

Object*&
typeSuper(Object* o)
{
  switch (o->type) {
  case Object::Type:
    return static_cast<Type*>(o)->super;

  default:
    UNREACHABLE;
  }
}

bool&
typeHideConstructor(Object* o)
{
  switch (o->type) {
  case Object::Type:
    return static_cast<Type*>(o)->hideConstructor;

  default:
    UNREACHABLE;
  }
}

class Number : public Object {
 public:
  unsigned value;

  static Number* make(unsigned value) {
    Number* o = allocate<Number>();
    o->type = Object::Number;
    o->value = value;
    return o;
  }
};

unsigned
number(Object* o)
{
  assert(o->type == Object::Number);
  return static_cast<Number*>(o)->value;
}

class Character : public Object {
 public:
  char value;

  static Character* make(char value) {
    Character* o = allocate<Character>();
    o->type = Object::Character;
    o->value = value;
    return o;
  }
};

char
character(Object* o)
{
  assert(o->type == Object::Character);
  return static_cast<Character*>(o)->value;
}

class String : public Object {
 public:
  const char* value;

  static String* make(Object* s) {
    assert(s);

    String* o = allocate<String>();
    o->type = Object::String;
    
    unsigned length = 0;
    for (Object* p = s; p; p = cdr(p)) ++ length;

    char* value = static_cast<char*>(malloc(length + 1));
    assert(value);
    unsigned i = 0;
    for (Object* p = s; p; p = cdr(p)) value[i++] = character(car(p));
    value[i] = 0;

    o->value = value;
    return o;
  }
};

const char*
string(Object* o)
{
  assert(o->type == Object::String);
  return static_cast<String*>(o)->value;
}

class Singleton : public Object {
 public:
  static Singleton* make(Object::ObjectType type) {
    Singleton* o = allocate<Singleton>();
    o->type = type;
    return o;
  }
};

bool
endsWith(char c, const char* s)
{
  assert(s);
  if (*s == 0) return false;
  
  while (*s) ++ s;
  return (*(s - 1) == c);
}

const char*
capitalize(const char* s)
{
  assert(s);
  unsigned length = strlen(s);
  assert(length);
  char* r = static_cast<char*>(malloc(length + 1));
  assert(r);

  memcpy(r, s, length + 1);
  if (r[0] >= 'a' and r[0] <= 'z') r[0] = (r[0] - 'a') + 'A';

  return r;
}

Object*
read(Input* in, Object* eos, int level)
{
  List s;

  int c;
  while ((c = in->peek()) >= 0) {
    switch (c) {
    case '(': {
      if (s.first) {
        return String::make(s.first);
      } else {
        List list;
        Object* o;
        in->read();
        while ((o = read(in, eos, level + 1)) != eos) {
          list.append(o);
        }
        return list.first;
      }
    } break;

    case ')': {
      if (s.first) {
        return String::make(s.first);
      } else {
        if (level == 0) {
          fprintf(stderr, "unexpected ')'\n");
          abort();
        }
        in->read();
        return eos;
      }
    } break;

    case ' ': case '\t': case '\n': {
      if (s.first) {
        return String::make(s.first);
      }
    } break;

    default: {
      s.append(Character::make(c));
    } break;
    }

    in->read();
  }

  if (level == 0) {
    if (s.first) {
      return String::make(s.first);
    } else {
      return eos;
    }
  } else {
    fprintf(stderr, "unexpected end of stream\n");
    abort();
  }
}

Object*
declaration(const char* name, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type: case Object::Pod:
      if (equal(name, typeName(o))) return o;
      break;

    default: UNREACHABLE;
    }
  }
  return 0;
}

Object*
derivationChain(Object* o)
{
  if (o->type == Object::Pod) {
    return cons(o, 0);
  } else {
    Object* chain = 0;
    for (Object* p = o; p; p = typeSuper(p)) {
      chain = cons(p, chain);
    }
    return chain;
  }
}

class MemberIterator {
 public:
  Object* types;
  Object* type;
  Object* members;
  Object* member;
  int index_;
  unsigned offset_;
  unsigned size_;
  unsigned padding_;
  unsigned alignment_;

  MemberIterator(Object* type, bool skipSupers = false):
    types(derivationChain(type)),
    type(car(types)),
    members(0),
    member(0),
    index_(-1),
    offset_(type->type == Object::Pod ? 0 : sizeof(void*)),
    size_(0),
    padding_(0),
    alignment_(0)
  { 
    while (skipSupers and hasMore() and this->type != type) next();
    padding_ = 0;
    alignment_ = 0;
  }

  bool hasMore() {
    if (members) {
      return true;
    } else {
      while (types) {
        type = car(types);
        members = typeMembers(type);
        types = cdr(types);
        if (members) return true;
      }
      return false;
    }
  }

  Object* next() {
    assert(hasMore());

    if (member) {
      assert(member->type == Object::Scalar);
      offset_ += size_;
    }

    member = car(members);
    members = cdr(members);

    ++ index_;

    switch (member->type) {
    case Object::Scalar: {
      size_ = memberSize(member);
      padding_ = pad(size_, alignment_);
      alignment_ = (alignment_ + size_ + padding_) % sizeof(void*); 
    } break;

    case Object::Array: {
      size_ = 0x7FFFFFFF;
      padding_ = pad(memberElementSize(member), alignment_);
      alignment_ = 0;
    } break;

    default: UNREACHABLE;
    }

    offset_ += padding_;

//     printf("size: %d; padding: %d; alignment: %d; offset: %d;\n",
//            size_, padding_, alignment_, offset_);

    return member;
  }

  unsigned offset() {
    return offset_;
  }

  unsigned size() {
    return size_;
  }

  unsigned padding() {
    return padding_;
  }

  unsigned space() {
    return size_ + padding_;
  }

  unsigned index() {
    return index_;
  }

  unsigned alignment() {
    return alignment_;
  }
};

unsigned
typeSize(Object* o)
{
  switch (o->type) {
  case Object::Pod: {
    MemberIterator it(o);
    while (it.hasMore()) it.next();
    return pad(it.offset() + it.space());
  } break;

  default:
    UNREACHABLE;
  }
}

bool
namesPointer(const char* s)
{
  return equal(s, "Collector")
    or equal(s, "Disposer")
    or endsWith('*', s);
}

unsigned
sizeOf(const char* type, Object* declarations)
{
  if (equal(type, "object")) {
    return sizeof(void*);
  } else if (equal(type, "intptr_t")) {
    return sizeof(intptr_t);
  } else if (equal(type, "unsigned") or equal(type, "int")) {
    return sizeof(int);
  } else if (equal(type, "bool")) {
    return sizeof(bool);
  } else if (equal(type, "int8_t") or equal(type, "uint8_t")) {
    return sizeof(uint8_t);
  } else if (equal(type, "int16_t") or equal(type, "uint16_t")) {
    return sizeof(uint16_t);
  } else if (equal(type, "int32_t") or equal(type, "uint32_t")) {
    return sizeof(uint32_t);
  } else if (equal(type, "int64_t") or equal(type, "uint64_t")) {
    return sizeof(uint64_t);
  } else if (equal(type, "char")) {
    return sizeof(char);
  } else if (endsWith("[0]", type)) {
    return 0;
  } else if (namesPointer(type)) {
    return sizeof(void*);
  } else {
    Object* dec = declaration(type, declarations);
    if (dec) return typeSize(dec);

    fprintf(stderr, "unexpected type: %s\n", type);
    abort();    
  }
}

Object*
parseArray(Object* t, Object* p, Object* declarations)
{
  const char* typeName = string(car(p));

  p = cdr(p);
  const char* name = string(car(p));

  return Array::make(t, declaration(typeName, declarations),
                     typeName, name, sizeOf(typeName, declarations));
}

Object*
parseMember(Object* t, Object* p, Object* declarations)
{
  const char* spec = string(car(p));
  if (equal(spec, "array")) {
    return parseArray(t, cdr(p), declarations);
  } else if (equal(spec, "noassert")) {
    Object* member = parseMember(t, cdr(p), declarations);
    memberNoAssert(member) = true;
    return member;
  } else if (equal(spec, "nogc")) {
    Object* member = parseMember(t, cdr(p), declarations);
    memberNoGC(member) = true;
    return member;
  } else {
    return Scalar::make(t, declaration(spec, declarations), spec,
                        string(car(cdr(p))),
                        sizeOf(spec, declarations));
  }
}

void
parseSubdeclaration(Object* t, Object* p, Object* declarations)
{
  const char* front = string(car(p));
  if (equal(front, "hide")) {
    if (equal(string(car(cdr(p))), "constructor")) {
      typeHideConstructor(t) = true;
    } else {
      Object* member = parseMember(t, cdr(p), declarations);
      memberHide(member) = true;
      addMember(t, member);
    }
  } else if (equal(front, "extends")) {
    assert(t->type == Object::Type);
    assert(typeSuper(t) == 0);
    typeSuper(t) = declaration(string(car(cdr(p))), declarations);
    assert(typeSuper(t));
    assert(typeSuper(t)->type == Object::Type);
    addSubtype(typeSuper(t), t);
  } else {
    Object* member = parseMember(t, p, declarations);
    addMember(t, member);
  }
}

bool
memberEqual(Object* a, Object* b)
{
  if (a->type == b->type) {
    switch (a->type) {
    case Object::Scalar:
      return equal(memberTypeName(a), memberTypeName(b))
        and memberNoAssert(a) == memberNoAssert(b)
        and memberNoGC(a) == memberNoGC(b)
        and memberHide(a) == memberHide(b);

      // todo: compare array fields

    default: return false;
    }
  } else {
    return false;
  }
}

bool
specEqual(Object* a, Object* b)
{
  if (a->type == Object::Type and
      b->type == Object::Type)
  {
    MemberIterator ai(a);
    MemberIterator bi(b);
    while (ai.hasMore()) {
      if (not bi.hasMore()) {
        return false;
      }

      if (not memberEqual(ai.next(), bi.next())) {
        return false;
      }
    }

    if (bi.hasMore()) {
      return false;
    } else {
      return true;
    }
  } else {
    return false;
  }
}

const char*
append(const char* a, const char* b)
{
  assert(a and b);
  unsigned aLength = strlen(a);
  unsigned bLength = strlen(b);
  assert(aLength + bLength);
  char* r = static_cast<char*>(malloc(aLength + bLength + 1));
  assert(r);
  
  memcpy(r, a, aLength);
  memcpy(r + aLength, b, bLength + 1);
  return r;
}

Object*
parseType(Object::ObjectType type, Object* p, Object* declarations)
{
  const char* name = string(car(p));

  const char* javaName = 0;
  if (cdr(p) and car(cdr(p))->type == Object::String) {
    p = cdr(p);
    javaName = string(car(p));
  }

  Type* t = Type::make(type, name, javaName);

  for (p = cdr(p); p; p = cdr(p)) {
    if (type == Object::Type) {
      parseSubdeclaration(t, car(p), declarations);
    } else {
      Object* member = parseMember(t, car(p), declarations);
      assert(member->type == Object::Scalar);
      addMember(t, member);
    }
  }

  return t;
}

Object*
parseDeclaration(Object* p, Object* declarations)
{
  const char* spec = string(car(p));
  if (equal(spec, "type")) {
    return parseType(Object::Type, cdr(p), declarations);
  } else if (equal(spec, "pod")) {
    return parseType(Object::Pod, cdr(p), declarations);
  } else {
    fprintf(stderr, "unexpected declaration spec: %s\n", spec);
    abort();
  }
}

Object*
parse(Input* in)
{
  Object* eos = Singleton::make(Object::Eos);
  List declarations;

  Object* o;
  while ((o = read(in, eos, 0)) != eos) {
    declarations.append(parseDeclaration(o, declarations.first));
  }

  return declarations.first;
}

void
writeAccessorName(Output* out, Object* member, bool respectHide = false,
                  bool unsafe = false)
{
  const char* owner = typeName(memberOwner(member));
  out->write(owner);
  out->write(capitalize(memberName(member)));
  if (unsafe) {
    out->write("Unsafe");
  }
  if (respectHide and memberHide(member)) {
    out->write("0");
  }
}

void
writeOffset(Output* out, Object* offset, bool allocationStyle = false)
{
  if (offset) {
    bool wrote = false;
    unsigned padLevel = 0;
    for (Object* p = offset; p; p = cdr(p)) {
      Object* o = car(p);
      if (wrote) {
        out->write(" + ");
      }
      switch (o->type) {
      case Object::Number: {
        if (number(o)) {
          out->write(number(o));
          wrote = true;
        }
      } break;

      case Object::Array: {
        out->write("pad((");
        if (allocationStyle) {
          out->write("length");
        } else {
          out->write(typeName(memberOwner(o)));
          out->write(capitalize("length"));
          out->write("(o)");
        }
        out->write(" * ");
        out->write(arrayElementSize(o));
        out->write(")");
        ++ padLevel;
        wrote = true;
      } break;

      default: UNREACHABLE;
      }
    }

    for (unsigned i = 0; i < padLevel; ++i) out->write(")");
  } else {
    out->write("0");
  }
}

void
writeSubtypeAssertions(Output* out, Object* o)
{
  for (Object* p = typeSubtypes(o); p; p = cdr(p)) {
    Object* st = car(p);
    out->write(" or objectClass(t, o) == arrayBodyUnsafe");
    out->write("(t, t->m->types, Machine::");
    out->write(capitalize(typeName(st)));
    out->write("Type)");
    writeSubtypeAssertions(out, st);
  }
}

void
writeAccessor(Output* out, Object* member, Object* offset, bool unsafe = false)
{
  const char* typeName = memberTypeName(member);
  if (memberTypeObject(member)) typeName = capitalize(typeName);

  if (not unsafe) {
    out->write("const unsigned ");
    out->write(capitalize(::typeName(memberOwner(member))));
    out->write(capitalize(memberName(member)));
    out->write(" = ");
    writeOffset(out, offset);
    out->write(";\n\n");
  }

  out->write("inline ");

  if (endsWith("[0]", typeName)) {
    out->write(take(strlen(typeName) - 3, typeName));
    out->write("*");
  } else {
    out->write(typeName);
    if (member->type != Object::Scalar and memberTypeObject(member)) {
      out->write("*");
    } else {
      out->write("&");
    }
  }

  out->write("\n");
  writeAccessorName(out, member, true, unsafe);
  if (memberOwner(member)->type == Object::Pod) {
    out->write("(");
    out->write(capitalize(::typeName(memberOwner(member))));
    out->write("*");
  } else {
    out->write("(Thread* t UNUSED, object");
  }
  out->write(" o");
  if (member->type != Object::Scalar) {
    out->write(", unsigned i");
  }
  out->write(") {\n");

  if (memberOwner(member)->type == Object::Type) {
    if (unsafe) {
      out->write("  assert(t, true);");
    } else {
      out->write("  assert(t, t->m->unsafe or ");
      out->write("objectClass(t, o) == arrayBodyUnsafe");
      out->write("(t, t->m->types, Machine::");
      out->write(capitalize(::typeName(memberOwner(member))));
      out->write("Type)");
      writeSubtypeAssertions(out, memberOwner(member));
      out->write(");\n");

      if (member->type != Object::Scalar) {
        out->write("  assert(t, i < ");
        out->write(::typeName(memberOwner(member)));
        out->write("Length(t, o));\n");
      }
    }
  }

  out->write("  return reinterpret_cast<");

  if (endsWith("[0]", typeName)) {
    out->write(take(strlen(typeName) - 3, typeName));
    out->write("*");
  } else {
    out->write(typeName);
    if (member->type != Object::Scalar and memberTypeObject(member)) {
      out->write("*");
    } else {
      out->write("&");
    }
  }

  if (memberOwner(member)->type == Object::Pod) {
    out->write(">(o->body");
  } else {
    out->write(">(reinterpret_cast<uint8_t*>(o)");
  }
  if (endsWith("[0]", typeName)
      or (member->type != Object::Scalar
          and memberTypeObject(member)))
  {
    out->write(" + ");
  } else {
    out->write("[");
  }

  out->write(capitalize(::typeName(memberOwner(member))));
  out->write(capitalize(memberName(member)));

  if (member->type != Object::Scalar) {
    out->write(" + (i * ");
    unsigned elementSize = (memberTypeObject(member) ?
                            typeSize(memberTypeObject(member)) :
                            sizeOf(memberTypeName(member), 0));
    out->write(elementSize);
    out->write(")");
  }
  if (not endsWith("[0]", typeName)
      and (member->type == Object::Scalar
          or memberTypeObject(member) == 0))
  {
    out->write("]");
  }
  out->write(");\n}\n\n");
}

Object*
typeBodyOffset(Object* type, Object* offset)
{
  MemberIterator it(type, true);
  while (it.hasMore()) {
    Object* m = it.next();
    switch (m->type) {
    case Object::Scalar: {
      offset = cons(Number::make(it.space()), offset);
    } break;

    case Object::Array: {
      if (it.padding()) offset = cons(Number::make(it.padding()), offset);
      offset = cons(m, offset);
    } break;

    default: UNREACHABLE;
    }
  }
  unsigned padding = pad(sizeof(void*), it.alignment());
  if (padding) offset = cons(Number::make(padding), offset);
  return offset;
}

Object*
typeOffset(Object* type, Object* super)
{
  if (super) {
    return typeBodyOffset(super, typeOffset(super, typeSuper(super)));
  } else {
    return (type->type == Object::Type ?
            cons(Number::make(sizeof(void*)), 0) : 0);
  }
}

Object*
typeOffset(Object* type)
{
  return typeOffset(0, type);
}

void
writePods(Output* out, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Pod: {
      out->write("const unsigned ");
      out->write(capitalize(typeName(o)));
      out->write("Size = ");
      out->write(typeSize(o));
      out->write(";\n\n");

      out->write("struct ");
      out->write(capitalize(typeName(o)));
      out->write(" { uint8_t body[");
      out->write(capitalize(typeName(o)));
      out->write("Size]; };\n\n");
    } break;

    default: break;
    }
  }
}

void
writeAccessors(Output* out, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type:
    case Object::Pod: {
      Object* offset = typeOffset
        (o, o->type == Object::Type ? typeSuper(o) : 0);
      for (MemberIterator it(o, true); it.hasMore();) {
        Object* m = it.next();
        switch (m->type) {
        case Object::Scalar: {
          if (it.padding()) offset = cons(Number::make(it.padding()), offset);
          writeAccessor(out, m, offset);
          if (memberNoAssert(m)) {
            writeAccessor(out, m, offset, true);
          }
          offset = cons(Number::make(it.size()), offset);
        } break;

        case Object::Array: {
          if (it.padding()) offset = cons(Number::make(it.padding()), offset);
          writeAccessor(out, m, offset);
          if (memberNoAssert(m)) {
            writeAccessor(out, m, offset, true);
          }
          offset = cons(m, offset);
        } break;

        default: UNREACHABLE;
        }
      }
    } break;

    default: break;
    }
  }
}

const char*
obfuscate(const char* s)
{
  if (equal(s, "default")) {
    return "default_";
  } else if (equal(s, "template")) {
    return "template_";
  } else if (equal(s, "class")) {
    return "class_";
  } else if (equal(s, "register")) {
    return "register_";
  } else if (equal(s, "this")) {
    return "this_";
  } else {
    return s;
  }
}

void
writeConstructorParameters(Output* out, Object* t)
{
  for (MemberIterator it(t); it.hasMore();) {
    Object* m = it.next();
    switch (m->type) {
    case Object::Scalar: {
      out->write(", ");
      out->write(memberTypeName(m));
      out->write(" ");
      out->write(obfuscate(memberName(m)));
    } break;

    case Object::Array: {
      out->write(", bool clear");
    } break;
            
    default: break;
    }    
  }
}

void
writeConstructorInitializations(Output* out, Object* t)
{
  for (MemberIterator it(t); it.hasMore();) {
    Object* m = it.next();
    switch (m->type) {
    case Object::Scalar: {
      out->write("  ");
      writeAccessorName(out, m, true);
      out->write("(t, o) = ");
      out->write(obfuscate(memberName(m)));
      out->write(";\n");
    } break;
            
    case Object::Array: {
      out->write("  if (clear and length) memset(");
      if (memberTypeObject(m) == 0) {
        out->write("&");
      }
      writeAccessorName(out, m, true);
      out->write("(t, o, 0), 0, length * ");
      out->write(arrayElementSize(m));
      out->write(");\n");
    } break;

    default: break;
    }    
  }
}

unsigned
typeMemberCount(Object* o)
{
  if (o == 0) return 0;
  return length(typeMembers(o)) + typeMemberCount(typeSuper(o));
}

void
writeConstructorDeclarations(Output* out, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type: {
      out->write("object make");
      out->write(capitalize(typeName(o)));
      if (typeHideConstructor(o)) out->write("0");
      out->write("(Thread* t");
      
      writeConstructorParameters(out, o);

      out->write(");\n\n");
    } break;

    default: break;
    }
  }
}

void
writeConstructors(Output* out, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type: {
      out->write("object\nmake");
      out->write(capitalize(typeName(o)));
      if (typeHideConstructor(o)) out->write("0");
      out->write("(Thread* t");
      
      writeConstructorParameters(out, o);

      out->write(")\n{\n");

      for (MemberIterator it(o); it.hasMore();) {
        Object* m = it.next();
        if (m->type == Object::Scalar
            and equal(memberTypeName(m), "object"))
        {
          out->write("  PROTECT(t, ");
          out->write(obfuscate(memberName(m)));
          out->write(");\n");
        }
      }

      if (typeJavaName(o)
          and not equal("class", typeName(o))
          and startsWith("java/", typeJavaName(o)))
      {
        out->write("  object class__ ");
        out->write("= arrayBody(t, t->m->types, Machine::");
        out->write(capitalize(typeName(o)));
        out->write("Type);\n");

        out->write("  if (classVmFlags(t, class__) & BootstrapFlag) {\n");
        out->write("    classVmFlags(t, class__) &= ~BootstrapFlag;\n");
        out->write("    resolveClass(t, className(t, class__));\n");
        out->write("    assert(t, t->exception == 0);\n");
        out->write("  }\n");
      }

      out->write("  object o = allocate(t, ");
      writeOffset(out, typeOffset(o), true);
      out->write(");\n");

      out->write("  cast<object>(o, 0) ");
      out->write("= arrayBody(t, t->m->types, Machine::");
      out->write(capitalize(typeName(o)));
      out->write("Type);\n");

      writeConstructorInitializations(out, o);

      out->write("  return o;\n}\n\n");
    } break;

    default: break;
    }
  }
}

void
writeEnums(Output* out, Object* declarations)
{
  bool wrote = false;
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type: {
      if (wrote) {
        out->write(",\n");
      } else {
        wrote = true;
      }
      out->write(capitalize(typeName(o)));
      out->write("Type");
    } break;

    default: break;
    }
  }

  if (wrote) {
    out->write("\n");
  } 
}

unsigned
memberCount(Object* o)
{
  unsigned c = 0;
  for (MemberIterator it(o); it.hasMore();) {
    it.next();
    ++c;
  }
  return c;
}

void
set(uint32_t* mask, unsigned index)
{
  if (index < 32) {
    *mask |= 1 << index;
  } else {
    UNREACHABLE;
  }
}

unsigned
typeFixedSize(Object* type)
{
  unsigned length = sizeof(void*);
  for (MemberIterator it(type); it.hasMore();) {
    Object* m = it.next();
    switch (m->type) {
    case Object::Scalar: {
      length = pad(it.offset() + it.size());
    } break;

    case Object::Array: break;

    default: UNREACHABLE;
    }
  }
  return length;
}

unsigned
typeArrayElementSize(Object* type)
{
  for (MemberIterator it(type); it.hasMore();) {
    Object* m = it.next();
    switch (m->type) {
    case Object::Scalar: break;

    case Object::Array: {
      return memberElementSize(m);
    } break;

    default: UNREACHABLE;
    }
  }
  return 0;
}

uint32_t
typeObjectMask(Object* type)
{
  assert(typeFixedSize(type) + typeArrayElementSize(type)
         < 32 * sizeof(void*));

  uint32_t mask = 1;

  for (MemberIterator it(type); it.hasMore();) {
    Object* m = it.next();
    unsigned offset = it.offset() / sizeof(void*);

    switch (m->type) {
    case Object::Scalar: {
      if (memberGC(m)) {
        set(&mask, offset);
      }
    } break;

    case Object::Array: {
      if (memberGC(m)) {
        set(&mask, offset);
      } else if (memberTypeObject(m)
                 and memberTypeObject(m)->type == Object::Pod)
      {
        for (MemberIterator it(memberTypeObject(m)); it.hasMore();) {
          Object* m = it.next();
          if (memberGC(m)) {
            set(&mask, offset + (it.offset() / sizeof(void*)));
          }
        }  
      }
    } break;

    default: UNREACHABLE;
    }
  }

  return mask;
}

void
writeInitialization(Output* out, Object* type)
{
  out->write("{\n");

  if (typeObjectMask(type) != 1) {
    out->write("  object mask = makeIntArray(t, 1, false);\n");

    out->write("  intArrayBody(t, mask, 0) = ");
    out->write(typeObjectMask(type));
    out->write(";\n");
  } else {
    out->write("  object mask = 0;\n");    
  }

  if (typeJavaName(type) and typeSuper(type)) {
    out->write("  object super = arrayBody(t, t->m->types, Machine::");
    out->write(capitalize(typeName(typeSuper(type))));
    out->write("Type);\n");
  } else {
    out->write("  object super = 0;\n");   
  }

  out->write("  object class_ = makeClass");
  out->write("(t, 0, ");

  if (typeJavaName(type)
      and not equal("class", typeName(type))
      and startsWith("java/", typeJavaName(type)))
  {
    out->write("BootstrapFlag");
  } else {
    out->write("0");
  }

  out->write(", 0, ");
  out->write(typeFixedSize(type));
  out->write(", ");
  out->write(typeArrayElementSize(type));
  out->write(", mask, 0, super, 0, 0, 0, 0, 0, t->m->loader);\n");

  out->write("  set(t, arrayBody(t, t->m->types, Machine::");
  out->write(capitalize(typeName(type)));
  out->write("Type), class_);\n");

  out->write("}\n\n");
}

unsigned
typeCount(Object* declarations)
{
  unsigned count = 0;
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type: {
      ++ count;
    } break;

    default: break;
    }
  }
  return count;
}

Object*
reorder(Object* declarations)
{
  Object* intArrayType = 0;
  Object* classType = 0;
  for (Object** p = &declarations; *p;) {
    Object* o = car(*p);
    if (o->type == Object::Type and equal(typeName(o), "intArray")) {
      intArrayType = o;
      *p = cdr(*p);
    } else if (o->type == Object::Type and equal(typeName(o), "class")) {
      classType = o;
      *p = cdr(*p);
    } else {
      p = &cdr(*p);
    }
  }

  return cons(intArrayType, cons(classType, declarations));
}

void
writeInitializations(Output* out, Object* declarations)
{
  unsigned count = typeCount(declarations);

  out->write("t->m->types = allocate(t, pad((");
  out->write(count);
  out->write(" * sizeof(void*)) + sizeof(uintptr_t) + sizeof(void*)));\n");
  out->write("cast<object>(t->m->types, 0) = 0;\n");
  out->write("arrayLength(t, t->m->types) = ");
  out->write(count);
  out->write(";\n");
  out->write("memset(&arrayBody(t, t->m->types, 0), 0, ");
  out->write(count);
  out->write(" * sizeof(void*));\n\n");

  declarations = reorder(declarations);

  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    if (o->type == Object::Type) {
      writeInitialization(out, o);
    }
  }
}

void
writeJavaInitialization(Output* out, Object* type)
{
  out->write("{\n");

  out->write("  object name = ::makeByteArray(t, \"");
  out->write(typeJavaName(type));
  out->write("\");\n");

  out->write("  object class_ = arrayBody(t, t->m->types, Machine::");
  out->write(capitalize(typeName(type)));
  out->write("Type);\n");

  out->write("  set(t, className(t, class_), name);\n");

  out->write("  hashMapInsert(t, t->m->bootstrapClassMap, ");
  out->write("name, class_, byteArrayHash);\n");

  out->write("}\n\n");
}

void
writeJavaInitializations(Output* out, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    if (o->type == Object::Type and typeJavaName(o)) {
      writeJavaInitialization(out, o);
    }
  }
}

void
usageAndExit(const char* command)
{
  fprintf(stderr,
          "usage: %s {enums,declarations,constructors,initializations,"
          "java-initializations}\n",
          command);
  exit(-1);
}

} // namespace

int
main(int ac, char** av)
{
  if ((ac != 1 and ac != 2)
      or (ac == 2
          and not equal(av[1], "enums")
          and not equal(av[1], "declarations")
          and not equal(av[1], "constructors")
          and not equal(av[1], "initializations")
          and not equal(av[1], "java-initializations")))
  {
    usageAndExit(av[0]);
  }

  FileInput in(0, stdin, false);

  Object* declarations = parse(&in);

  FileOutput out(0, stdout, false);

  if (ac == 1 or equal(av[1], "enums")) {
    writeEnums(&out, declarations);
  }

  if (ac == 1 or equal(av[1], "declarations")) {
    writePods(&out, declarations);
    writeAccessors(&out, declarations);
    writeConstructorDeclarations(&out, declarations);
  }

  if (ac == 1 or equal(av[1], "constructors")) {
    writeConstructors(&out, declarations);
  }
  
  if (ac == 1 or equal(av[1], "initializations")) {
    writeInitializations(&out, declarations);
  }

  if (ac == 1 or equal(av[1], "java-initializations")) {
    writeJavaInitializations(&out, declarations);
  }

  return 0;
}
