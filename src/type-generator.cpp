#include "stdlib.h"
#include "stdio.h"
#include "stdint.h"
#include "string.h"
#include "assert.h"

#include "constants.h"

#define UNREACHABLE abort()

#define UNUSED __attribute__((unused))

inline void operator delete(void*) { abort(); }

extern "C" void __cxa_pure_virtual(void) { abort(); }

using namespace vm;

namespace {

#ifndef POINTER_SIZE
#  define POINTER_SIZE sizeof(void*)
#endif

const unsigned BytesPerWord = POINTER_SIZE;

inline unsigned
pad(unsigned size, unsigned alignment)
{
  unsigned n = alignment;
  while (size and n % size and n % BytesPerWord) ++ n;
  return n - alignment;
}

inline unsigned
pad(unsigned n)
{
  unsigned extra = n % BytesPerWord;
  return (extra ? n + BytesPerWord - extra : n);
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

class Input {
 public:
  virtual ~Input() { }
  
  virtual void dispose() = 0;

  virtual int peek() = 0;

  virtual int read() = 0;

  virtual unsigned line() = 0;

  virtual unsigned column() = 0;

  void skipSpace() {
    bool quit = false;
    while (not quit) {
      int c = peek();
      switch (c) {
      case ' ': case '\t': case '\n':
        read();
        break;

      default: quit = true;
      }
    }
  }
};

class FileInput : public Input {
 public:
  const char* file;
  FILE* stream;
  unsigned line_;
  unsigned column_;
  bool close;

  FileInput(const char* file, FILE* stream = 0, bool close = true):
    file(file), stream(stream), line_(1), column_(1), close(close)
  { }

  virtual ~FileInput() {
    dispose();
  }

  virtual void dispose() {
    if (stream and close) {
      fclose(stream);
      stream = 0;
    }
  }

  virtual int peek() {
    int c = getc(stream);
    ungetc(c, stream);
    return c;
  }

  virtual int read() {
    int c = getc(stream);
    if (c == '\n') {
      ++ line_;
      column_ = 1;
    } else {
      ++ column_;
    }
    return c;
  }

  virtual unsigned line() {
    return line_;
  }

  virtual unsigned column() {
    return column_;
  }
};

class Output {
 public:
  virtual ~Output() { }
  
  virtual void dispose() = 0;

  virtual void write(const char* s) = 0;

  void write(int i) {
    static const int Size = 32;
    char s[Size];
    int c UNUSED = snprintf(s, Size, "%d", i);
    assert(c > 0 and c < Size);
    write(s);
  }
};

class FileOutput : public Output {
 public:
  const char* file;
  FILE* stream;
  bool close;

  FileOutput(const char* file, FILE* stream = 0, bool close = true):
    file(file), stream(stream), close(close)
  { }

  virtual ~FileOutput() {
    dispose();
  }

  virtual void dispose() {
    if (stream and close) {
      fclose(stream);
      stream = 0;
    }
  }

  virtual void write(const char* s) {
    fputs(s, stream);
  }

  const char* filename() {
    return file;
  }
};

class Stream {
 public:
  Stream(FILE* stream, bool close):
    stream(stream), close(close)
  {
    assert(stream);
  }

  ~Stream() {
    if (close) fclose(stream);
  }

  void skip(unsigned size) {
    fseek(stream, size, SEEK_CUR);
  }

  void read(uint8_t* data, unsigned size) {
    fread(data, 1, size, stream);
  }

  uint8_t read1() {
    uint8_t v;
    read(&v, 1);
    return v;
  }

  uint16_t read2() {
    uint16_t a = read1();
    uint16_t b = read1();
    return (a << 8) | b;
  }

  uint32_t read4() {
    uint32_t a = read2();
    uint32_t b = read2();
    return (a << 16) | b;
  }

  uint64_t read8() {
    uint64_t a = read4();
    uint64_t b = read4();
    return (a << 32) | b;
  }

  uint32_t readFloat() {
    return read4();
  }

  uint64_t readDouble() {
    return read8();
  }

 private:
  FILE* stream;
  bool close;
};

class Object {
 public:
  typedef enum {
    Scalar,
    Array,
    Method,
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

void
setCar(Object* o, Object* v)
{
  assert(o->type == Object::Pair);
  static_cast<Pair*>(o)->car = v;
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

class Method : public Object {
 public:
  Object* owner;
  const char* name;
  const char* spec;

  static Method* make(Object* owner, const char* name, const char* spec)
  {
    Method* o = allocate<Method>();
    o->type = Object::Method;
    o->owner = owner;
    o->name = name;
    o->spec = spec;
    return o;
  }
};

const char*
methodName(Object* o)
{
  switch (o->type) {
  case Object::Method:
    return static_cast<Method*>(o)->name;

  default:
    UNREACHABLE;
  }
}

const char*
methodSpec(Object* o)
{
  switch (o->type) {
  case Object::Method:
    return static_cast<Method*>(o)->spec;

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
  List methods;
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
    o->methods.first = o->methods.last = 0;
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

Object*
typeMethods(Object* o)
{
  switch (o->type) {
  case Object::Type:
    return static_cast<Type*>(o)->methods.first;

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
        (Scalar::make(o, 0, "uintptr_t", "length", BytesPerWord));
    }
    static_cast<Type*>(o)->members.append(member);
    break;

  default:
    UNREACHABLE;
  }
}

void
addMethod(Object* o, Object* method)
{
  switch (o->type) {
  case Object::Type:
    for (Object* p = typeMethods(o); p; p = cdr(p)) {
      Object* m = car(p);
      if (equal(methodName(m), methodName(method))
          and equal(methodSpec(m), methodSpec(method)))
      {
        setCar(p, method);
        return;
      }
    }
    static_cast<Type*>(o)->methods.append(method);
    break;

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
javaDeclaration(const char* name, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type:
      if (typeJavaName(o) and equal(name, typeJavaName(o))) return o;
      break;

    case Object::Pod:
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
    offset_(type->type == Object::Pod ? 0 : BytesPerWord),
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
      alignment_ = (alignment_ + size_ + padding_) % BytesPerWord; 
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
  if (equal(type, "object")
      or equal(type, "intptr_t") or equal(type, "uintptr_t"))
  {
    return BytesPerWord;
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
    return BytesPerWord;
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
append(const char* a, const char* b, const char* c, const char* d)
{
  unsigned al = strlen(a);
  unsigned bl = strlen(b);
  unsigned cl = strlen(c);
  unsigned dl = strlen(d);
  char* p = static_cast<char*>(malloc(al + bl + cl + dl + 1));
  memcpy(p, a, al);
  memcpy(p + al, b, bl);
  memcpy(p + al + bl, c, cl);
  memcpy(p + al + bl + cl, d, dl + 1);
  return p;
}

const char*
append(const char* a, const char* b)
{
  return append(a, b, "", "");
}

const char*
fieldType(const char* spec)
{
  switch (*spec) {
  case 'B':
  case 'Z':
    return "uint8_t";
  case 'C':
  case 'S':
    return "uint16_t";
  case 'D':
  case 'J':
    return "uint64_t";
  case 'F':
  case 'I':
    return "uint32_t";
  case 'L':
  case '[':
    return "object";

  default: abort();
  }
}

void
parseJavaClass(Object* type, Stream* s, Object* declarations)
{
  uint32_t magic = s->read4();
  assert(magic == 0xCAFEBABE);
  s->read2(); // minor version
  s->read2(); // major version

  unsigned poolCount = s->read2() - 1;
  uintptr_t pool[poolCount];
  for (unsigned i = 0; i < poolCount; ++i) {
    unsigned c = s->read1();

    switch (c) {
    case CONSTANT_Integer:
    case CONSTANT_Float:
      pool[i] = s->read4();
      break;

    case CONSTANT_Long:
    case CONSTANT_Double:
      pool[i++] = s->read4();
      pool[i] = s->read4();
      break;

    case CONSTANT_Utf8: {
      unsigned length = s->read2();
      uint8_t* p = static_cast<uint8_t*>(malloc(length + 1));
      s->read(p, length);
      p[length] = 0;
      pool[i] = reinterpret_cast<uintptr_t>(p);
    } break;

    case CONSTANT_Class:
    case CONSTANT_String:
      pool[i] = s->read2();
      break;

    case CONSTANT_NameAndType:
      pool[i] = s->read4();
      break;

    case CONSTANT_Fieldref:
    case CONSTANT_Methodref:
    case CONSTANT_InterfaceMethodref:
      pool[i] = s->read4();
      break;

    default: abort();
    }
  }

  s->read2(); // flags
  s->read2(); // name

  unsigned superIndex = s->read2();
  if (superIndex) {
    const char* name = reinterpret_cast<const char*>
      (pool[pool[superIndex - 1] - 1]);
    typeSuper(type) = javaDeclaration(name, declarations);
    assert(typeSuper(type));
  }

  unsigned interfaceCount = s->read2();
  s->skip(interfaceCount * 2);
//   for (unsigned i = 0; i < interfaceCount; ++i) {
//     const char* name = reinterpret_cast<const char*>
//       (pool[pool[s->read2() - 1] - 1]);

//     fprintf(stderr, "%s implements %s\n", typeJavaName(type), name);
//   }

  unsigned fieldCount = s->read2();
  for (unsigned i = 0; i < fieldCount; ++i) {
    unsigned flags = s->read2();
    unsigned nameIndex = s->read2();
    unsigned specIndex = s->read2();

    unsigned attributeCount = s->read2();
    for (unsigned j = 0; j < attributeCount; ++j) {
      s->read2();
      s->skip(s->read4());
    }

    if ((flags & ACC_STATIC) == 0) {
      char* name = reinterpret_cast<char*>(pool[nameIndex - 1]);
      unsigned nameLength = strlen(name);
      if (nameLength > 0 and name[nameLength - 1] == '_') {
        name[nameLength - 1] = 0;
      }

      const char* spec = reinterpret_cast<const char*>(pool[specIndex - 1]);
      const char* memberType = fieldType(spec);

      Object* member = Scalar::make
        (type, 0, memberType, name, sizeOf(memberType, declarations));

      if (equal(typeJavaName(type), "java/lang/ref/Reference")
          and (equal(name, "vmNext")
               or equal(name, "target")
               or equal(name, "queue")))
      {
        memberNoGC(member) = true;
      }

      addMember(type, member);
    }
  }

  if (typeSuper(type)) {
    for (Object* p = typeMethods(typeSuper(type)); p; p = cdr(p)) {
      addMethod(type, car(p));
    }
  }

  unsigned methodCount = s->read2();
  for (unsigned i = 0; i < methodCount; ++i) {
    unsigned flags = s->read2();
    unsigned nameIndex = s->read2();
    unsigned specIndex = s->read2();

    unsigned attributeCount = s->read2();
    for (unsigned j = 0; j < attributeCount; ++j) {
      s->read2();
      s->skip(s->read4());
    }

    if ((flags & (ACC_STATIC | ACC_PRIVATE)) == 0) {
      const char* name = reinterpret_cast<const char*>(pool[nameIndex - 1]);
      const char* spec = reinterpret_cast<const char*>(pool[specIndex - 1]);

      Object* method = Method::make(type, name, spec);
      addMethod(type, method);
    }    
  }
}

Object*
parseType(Object::ObjectType type, Object* p, Object* declarations,
          const char* javaClassDirectory)
{
  const char* name = string(car(p));

  const char* javaName = 0;
  if (cdr(p) and car(cdr(p))->type == Object::String) {
    p = cdr(p);
    javaName = string(car(p));
  }

  Type* t = Type::make(type, name, javaName);

  if (javaName and *javaName != '[') {
    assert(cdr(p) == 0);

    const char* file = append(javaClassDirectory, "/", javaName, ".class");
    Stream s(fopen(file, "rb"), true);
    parseJavaClass(t, &s, declarations);
  } else {
    for (p = cdr(p); p; p = cdr(p)) {
      if (type == Object::Type) {
        parseSubdeclaration(t, car(p), declarations);
      } else {
        Object* member = parseMember(t, car(p), declarations);
        assert(member->type == Object::Scalar);
        addMember(t, member);
      }
    }

    if (type == Object::Type and typeSuper(t)) {
      for (Object* p = typeMethods(typeSuper(t)); p; p = cdr(p)) {
        addMethod(t, car(p));
      }
    }
  }

  return t;
}

Object*
parseDeclaration(Object* p, Object* declarations,
                 const char* javaClassDirectory)
{
  const char* spec = string(car(p));
  if (equal(spec, "type")) {
    return parseType(Object::Type, cdr(p), declarations, javaClassDirectory);
  } else if (equal(spec, "pod")) {
    return parseType(Object::Pod, cdr(p), declarations, javaClassDirectory);
  } else {
    fprintf(stderr, "unexpected declaration spec: %s\n", spec);
    abort();
  }
}

Object*
parse(Input* in, const char* javaClassDirectory)
{
  Object* eos = Singleton::make(Object::Eos);
  List declarations;

  Object* o;
  while ((o = read(in, eos, 0)) != eos) {
    declarations.append
      (parseDeclaration(o, declarations.first, javaClassDirectory));
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
    if (not unsafe) {
      out->write("  assert(t, t->m->unsafe or ");
      out->write("instanceOf(t, arrayBodyUnsafe");
      out->write("(t, t->m->types, Machine::");
      out->write(capitalize(::typeName(memberOwner(member))));
      out->write("Type)");
      out->write(", o));\n");

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
  unsigned padding = pad(BytesPerWord, it.alignment());
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
            cons(Number::make(BytesPerWord), 0) : 0);
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

unsigned
typeFixedSize(Object* type)
{
  unsigned length = BytesPerWord;
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
writeConstructorArguments(Output* out, Object* t)
{
  for (MemberIterator it(t); it.hasMore();) {
    Object* m = it.next();
    switch (m->type) {
    case Object::Scalar: {
      out->write(", ");
      out->write(obfuscate(memberName(m)));
    } break;

    case Object::Array: {
      out->write(", clear");
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
writeInitializerDeclarations(Output* out, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type: {
      out->write("void init");
      out->write(capitalize(typeName(o)));
      if (typeHideConstructor(o)) out->write("0");
      out->write("(Thread* t, object o");
      
      writeConstructorParameters(out, o);

      out->write(");\n\n");
    } break;

    default: break;
    }
  }
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
writeInitializers(Output* out, Object* declarations)
{
  for (Object* p = declarations; p; p = cdr(p)) {
    Object* o = car(p);
    switch (o->type) {
    case Object::Type: {
      out->write("void\ninit");
      out->write(capitalize(typeName(o)));
      if (typeHideConstructor(o)) out->write("0");
      out->write("(Thread* t, object o");
      
      writeConstructorParameters(out, o);

      out->write(")\n{\n");

      out->write("  setObjectClass(t, o, ");
      out->write("arrayBody(t, t->m->types, Machine::");
      out->write(capitalize(typeName(o)));
      out->write("Type));\n");

      writeConstructorInitializations(out, o);

      out->write("}\n\n");
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
      out->write("object make");
      out->write(capitalize(typeName(o)));
      if (typeHideConstructor(o)) out->write("0");
      out->write("(Thread* t");
      
      writeConstructorParameters(out, o);

      out->write(")\n{\n");

      bool hasObjectMask = false;
      for (MemberIterator it(o); it.hasMore();) {
        Object* m = it.next();
        if (m->type == Object::Scalar
            and equal(memberTypeName(m), "object")
            and not memberNoGC(m))
        {
          out->write("  PROTECT(t, ");
          out->write(obfuscate(memberName(m)));
          out->write(");\n");

          hasObjectMask = true;
        }
      }

      out->write("  object o = allocate(t, ");
      writeOffset(out, typeOffset(o), true);
      if (hasObjectMask) {
        out->write(", true");
      } else {
        out->write(", false");
      }
      out->write(");\n");

      out->write("  init");
      out->write(capitalize(typeName(o)));
      if (typeHideConstructor(o)) out->write("0");
      out->write("(t, o");
      writeConstructorArguments(out, o);
      out->write(");\n");

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

unsigned
methodCount(Object* o)
{
  unsigned c = 0;
  for (Object* p = typeMethods(o); p; p = cdr(p)) ++c;
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
         < 32 * BytesPerWord);

  uint32_t mask = 1;

  for (MemberIterator it(type); it.hasMore();) {
    Object* m = it.next();
    unsigned offset = it.offset() / BytesPerWord;

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
            set(&mask, offset + (it.offset() / BytesPerWord));
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
  out->write("bootClass(t, Machine::");
  out->write(capitalize(typeName(type)));
  out->write("Type, ");

  if (typeSuper(type)) {
    out->write("Machine::");
    out->write(capitalize(typeName(typeSuper(type))));
    out->write("Type");
  } else {
    out->write("-1");   
  }
  out->write(", ");

  if (typeObjectMask(type) != 1) {
    out->write(typeObjectMask(type));
  } else {
    out->write("0");    
  }
  out->write(", ");

  out->write(typeFixedSize(type));
  out->write(", ");

  out->write(typeArrayElementSize(type));
  out->write(");\n");
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
  out->write("bootJavaClass(t, Machine::");
  out->write(capitalize(typeName(type)));
  out->write("Type, \"");

  out->write(typeJavaName(type));
  out->write("\", ");

  out->write(methodCount(type));
  out->write(", bootMethod);\n");
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
          "usage: %s <java class directory> "
          "{enums,declarations,constructors,initializations,"
          "java-initializations}\n",
          command);
  exit(-1);
}

} // namespace

int
main(int ac, char** av)
{
  if ((ac != 2 and ac != 3)
      or (ac == 3
          and not equal(av[2], "enums")
          and not equal(av[2], "declarations")
          and not equal(av[2], "constructors")
          and not equal(av[2], "initializations")
          and not equal(av[2], "java-initializations")))
  {
    usageAndExit(av[0]);
  }

  FileInput in(0, stdin, false);

  Object* declarations = parse(&in, av[1]);

  FileOutput out(0, stdout, false);

  if (ac == 2 or equal(av[2], "enums")) {
    writeEnums(&out, declarations);
  }

  if (ac == 2 or equal(av[2], "declarations")) {
    out.write("const unsigned TypeCount = ");
    out.Output::write(typeCount(declarations));
    out.write(";\n\n");

    writePods(&out, declarations);
    writeAccessors(&out, declarations);
    writeInitializerDeclarations(&out, declarations);
    writeConstructorDeclarations(&out, declarations);
  }

  if (ac == 2 or equal(av[2], "constructors")) {
    writeInitializers(&out, declarations);
    writeConstructors(&out, declarations);
  }
  
  if (ac == 2 or equal(av[2], "initializations")) {
    writeInitializations(&out, declarations);
  }

  if (ac == 2 or equal(av[2], "java-initializations")) {
    writeJavaInitializations(&out, declarations);
  }

  return 0;
}
