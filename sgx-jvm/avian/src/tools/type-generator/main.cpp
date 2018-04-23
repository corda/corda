/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "stdlib.h"
#include "stdio.h"
#include "stdint.h"
#include "string.h"
#include "errno.h"

#include <map>
#include <string>
#include <vector>
#include <set>
#include <sstream>
#include <algorithm>

#include "avian/constants.h"
#include "avian/finder.h"

#include <avian/util/arg-parser.h>
#include <avian/util/stream.h>
#include <avian/util/math.h>

#include "io.h"
#include "sexpr.h"

#include "assert.h"

using namespace avian::util;

#define UNREACHABLE abort()

#define UNUSED __attribute__((unused))

using namespace vm;
using namespace avian::tools::typegenerator;

namespace avian {
namespace tools {
namespace typegenerator {

class Class;

class Field {
 public:
  std::string name;
  size_t elementSize;
  size_t offset;
  uintptr_t ownerId;
  bool noassert;
  bool nogc;
  bool polyfill;
  bool threadParam;

  std::string javaSpec;
  std::string typeName;

  Field(Class* ownerId,
        const std::string& typeName,
        const std::string& javaSpec,
        const std::string& name)
      : name(name),
        elementSize(-1),
        offset(0),
        ownerId(reinterpret_cast<uintptr_t>(ownerId)),
        noassert(false),
        nogc(false),
        polyfill(false),
        threadParam(false),
        javaSpec(javaSpec),
        typeName(typeName)
  {
  }

  std::string dump() const
  {
    std::ostringstream ss;
    ss << "field " << name << ":" << typeName << ":" << javaSpec
       << ", size=" << elementSize << ", offset=" << offset;
    if (noassert) {
      ss << " noassert";
    }
    if (nogc) {
      ss << " nogc";
    }
    if (polyfill) {
      ss << " polyfill";
    }
    return ss.str();
  }
};

class Method {
 public:
  std::string javaName;
  std::string javaSpec;

  Method(const std::string& javaName, const std::string& javaSpec)
      : javaName(javaName), javaSpec(javaSpec)
  {
  }

  bool operator==(const Method& o) const
  {
    return javaName == o.javaName && javaSpec == o.javaSpec;
  }

  bool operator<(const Method& o) const
  {
    return javaName < o.javaName
           || (javaName == o.javaName && javaSpec < o.javaSpec);
  }
  std::string dump() const
  {
    return "method " + javaName + javaSpec;
  }
};

class Class {
 public:
  // "simple" name, used for generated code, defined in types.def
  std::string name;

  // Name of the backing Java class, empty if there isn't one
  std::string javaName;

  Class* super;

  std::vector<Field*> fields;
  std::set<Method> methods;

  Field* arrayField;

  bool overridesMethods;

  int fixedSize;

  Class(const std::string& name)
      : name(name),
        super(0),
        arrayField(0),
        overridesMethods(false),
        fixedSize(-1)
  {
  }

  std::string dump() const
  {
    std::ostringstream ss;
    ss << "class " << name;
    if (javaName.size() > 0) {
      ss << "(" << javaName << ")";
    }
    if (super) {
      ss << " : " << super->name << "(" << super->javaName << ")";
    }
    ss << " {\n";

    for (const auto f : fields) {
      ss << "  " << f->dump() << "\n";
    }

    for (const auto m : methods) {
      ss << "  " << m.dump() << "\n";
    }
    ss << "}";
    return ss.str();
  }

  void dumpToStdout() const
  {
    printf("%s\n", dump().c_str());
  }
};

class Module {
 public:
  // Map from java-level name to Class
  std::map<std::string, Class*> javaClasses;

  std::map<std::string, Class*> classes;

  void add(Class* cl)
  {
    assert(classes.find(cl->name) == classes.end());
    classes[cl->name] = cl;
    if (cl->javaName != "") {
      assert(javaClasses.find(cl->javaName) == javaClasses.end());
      javaClasses[cl->javaName] = cl;
    }
  }
};
}
}
}

namespace {

namespace local {

#ifndef POINTER_SIZE
#define POINTER_SIZE sizeof(void*)
#endif

const unsigned BytesPerWord = POINTER_SIZE;

inline bool equal(const char* a, const char* b)
{
  return strcmp(a, b) == 0;
}

inline bool endsWith(const std::string& b, const std::string& a)
{
  if (b.size() > a.size()) {
    return false;
  }
  return std::equal(a.begin() + a.size() - b.size(), a.end(), b.begin());
}

std::string enumName(Module& module, Field* f)
{
  std::string& type = f->typeName;
  if (type == "void*") {
    return "word";
  } else if (type == "maybe_object") {
    return "uintptr_t";
  } else if (f->javaSpec.size() != 0
             && (f->javaSpec[0] == 'L' || f->javaSpec[0] == '[')) {
    return "object";
  }
  const auto it = module.classes.find(f->typeName);
  assert(f->typeName.size() > 0);
  if (it != module.classes.end()) {
    return "object";
  } else {
    return f->typeName;
  }
}

class Character : public Object {
 public:
  char value;

  static Character* make(char value)
  {
    Character* o = allocate<Character>();
    o->type = Object::Character;
    o->value = value;
    return o;
  }
};

char character(Object* o)
{
  assert(o->type == Object::Character);
  return static_cast<Character*>(o)->value;
}

class String : public Object {
 public:
  const char* value;

  static String* make(Object* s)
  {
    assert(s);

    String* o = allocate<String>();
    o->type = Object::String;

    unsigned length = 0;
    for (Object* p = s; p; p = cdr(p))
      ++length;

    char* value = static_cast<char*>(malloc(length + 1));
    assert(value);
    unsigned i = 0;
    for (Object* p = s; p; p = cdr(p))
      value[i++] = character(car(p));
    value[i] = 0;

    o->value = value;
    return o;
  }
};

const char* string(Object* o)
{
  assert(o->type == Object::String);
  return static_cast<String*>(o)->value;
}

class Singleton : public Object {
 public:
  static Singleton* make(Object::ObjectType type)
  {
    Singleton* o = allocate<Singleton>();
    o->type = type;
    return o;
  }
};

std::string capitalize(const std::string& s)
{
  if (s[0] >= 'a' && s[0] <= 'z') {
    return (char)(s[0] + 'A' - 'a') + s.substr(1, s.size() - 1);
  }
  return s;
}

Object* read(Input* in, Object* eos, int level)
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

    case ' ':
    case '\t':
    case '\n':
    case '\r': {
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

bool namesPointer(const std::string& s)
{
  return s == "Collector" or s == "Disposer" or endsWith("*", s);
}

unsigned sizeOf(Module& module, const std::string& type)
{
  if (type == "object" or type == "intptr_t" or type == "uintptr_t"
      or type == "maybe_object") {
    return BytesPerWord;
  } else if (type == "unsigned" or type == "int") {
    return sizeof(int);
  } else if (type == "bool") {
    return sizeof(bool);
  } else if (type == "int8_t" or type == "uint8_t") {
    return sizeof(uint8_t);
  } else if (type == "int16_t" or type == "uint16_t") {
    return sizeof(uint16_t);
  } else if (type == "int32_t" or type == "uint32_t") {
    return sizeof(uint32_t);
  } else if (type == "int64_t" or type == "uint64_t") {
    return sizeof(uint64_t);
  } else if (type == "char") {
    return sizeof(char);
  } else if (endsWith("[0]", type)) {
    return 0;
  } else if (namesPointer(type)) {
    return BytesPerWord;
  } else {
    const auto it = module.classes.find(type);
    if (it != module.classes.end()) {
      return BytesPerWord;
    } else {
      fprintf(stderr, "unexpected type: %s\n", type.c_str());
      abort();
    }
  }
}

struct FieldSpec {
  bool isArray;
  std::string aliasName;
  bool require;
  Field* field;

  FieldSpec()
  {
  }

  FieldSpec(bool isArray, Field* field)
      : isArray(isArray), require(false), field(field)
  {
  }
};

class ClassParser {
 public:
  Class* cl;
  std::map<std::string, Field*> fields;

  ClassParser(Class* cl) : cl(cl)
  {
  }

  void add(FieldSpec f)
  {
    if (f.field->polyfill) {
      if (fields.find(f.field->name) == fields.end()) {
        fields[f.field->name] = f.field;
        cl->fields.push_back(f.field);
      } else {
        fields[f.field->name]->threadParam = true;
      }
      return;
    }
    if (f.aliasName.size() > 0) {
      if (fields.find(f.aliasName) == fields.end()) {
        if (fields.find(f.field->name) != fields.end()) {
          // printf("alias %s.%s -> %s.%s\n", cl->name.c_str(),
          // f.field->name.c_str(), cl->name.c_str(), f.aliasName.c_str());
          const auto it = fields.find(f.field->name);
          assert(it != fields.end());
          Field* renamed = it->second;
          fields.erase(it);
          fields[f.aliasName] = renamed;

          renamed->name = f.aliasName;

          // TODO: this currently works around how avian uses an object (either
          // a char[] or byte[]) for String.data
          renamed->typeName = f.field->typeName;
          renamed->javaSpec = f.field->javaSpec;
        } else {
          // printf("ignoring absent alias %s.%s -> %s.%s\n", cl->name.c_str(),
          // f.field->name.c_str(), cl->name.c_str(), f->  aliasName.c_str());
        }
      } else {
        // printf("ignoring already defined alias %s.%s -> %s.%s\n",
        // cl->name.c_str(), f.field->name.c_str(), cl->name.c_str(), f->
        // aliasName.c_str());
      }
    } else {
      if (fields.find(f.field->name) == fields.end()) {
        // printf("add %s.%s\n", cl->name.c_str(), f.field->name.c_str());
        fields[f.field->name] = f.field;
        if (f.isArray) {
          add(FieldSpec(false, new Field(cl, "uintptr_t", "", "length")));
          assert(!cl->arrayField);
          cl->arrayField = f.field;
        } else {
          cl->fields.push_back(f.field);
        }
      } else {
        // printf("required check %s.%s\n", cl->name.c_str(),
        // f.field->name.c_str());
        assert(f.aliasName.size() > 0 || f.require);
        fields[f.field->name]->nogc |= f.field->nogc;
        fields[f.field->name]->noassert |= f.field->noassert;
      }
    }
  }

  void setSuper(Class* super)
  {
    assert(!cl->super);
    cl->super = super;
    assert(!super->arrayField);
    assert(fields.size() == 0);
    for (const auto f : super->fields) {
      add(FieldSpec(false, f));
    }
  }
};

FieldSpec parseArray(Module&, ClassParser& clparser, Object* p)
{
  const char* typeName = string(car(p));

  p = cdr(p);
  const char* name = string(car(p));

  assert(!clparser.cl->arrayField);
  return FieldSpec(true, new Field(clparser.cl, typeName, "", name));
}

FieldSpec parseVerbatimField(Module&, ClassParser& clparser, Object* p)
{
  const char* spec = string(car(p));
  const char* name = string(car(cdr(p)));
  return FieldSpec(false, new Field(clparser.cl, spec, "", name));
}

FieldSpec parseField(Module& module, ClassParser& clparser, Object* p)
{
  FieldSpec f;
  const char* spec = string(car(p));
  if (equal(spec, "field")) {
    return parseVerbatimField(module, clparser, cdr(p));
  } else if (equal(spec, "array")) {
    return parseArray(module, clparser, cdr(p));
  } else if (equal(spec, "noassert")) {
    f = parseField(module, clparser, cdr(p));
    f.field->noassert = true;
    f.require = true;
  } else if (equal(spec, "nogc")) {
    f = parseField(module, clparser, cdr(p));
    f.field->nogc = true;
    f.require = true;
  } else if (equal(spec, "require")) {
    f = parseField(module, clparser, cdr(p));
    f.require = true;
  } else if (equal(spec, "alias")) {
    const char* name = string(car(cdr(p)));
    f = parseField(module, clparser, cdr(cdr(p)));
    f.aliasName = name;
  } else if (equal(spec, "polyfill")) {
    f = parseField(module, clparser, cdr(p));
    f.field->polyfill = true;
  } else {
    return parseVerbatimField(module, clparser, p);
  }
  return f;
}

void parseSubdeclaration(Module& module, ClassParser& clparser, Object* p)
{
  const char* front = string(car(p));
  if (equal(front, "extends")) {
    Class* super = module.classes[string(car(cdr(p)))];
    clparser.setSuper(super);
  } else {
    clparser.add(parseField(module, clparser, p));
  }
}

const char* append(const char* a, const char* b, const char* c, const char* d)
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

const char* append(const char* a, const char* b)
{
  return append(a, b, "", "");
}

const char* fieldType(const char* spec)
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

  default:
    abort();
  }
}

void parseJavaClass(Module& module, ClassParser& clparser, Stream* s)
{
  uint32_t magic = s->read4();
  assert(magic == 0xCAFEBABE);
  (void)magic;
  s->read2();  // minor version
  s->read2();  // major version

  unsigned poolCount = s->read2() - 1;
  std::vector<uintptr_t> pool(poolCount, -1);
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

    default:
      abort();
    }
  }

  s->read2();  // flags
  s->read2();  // name

  unsigned superIndex = s->read2();
  if (superIndex) {
    const char* name
        = reinterpret_cast<const char*>(pool[pool[superIndex - 1] - 1]);
    Class* super = module.javaClasses[name];
    clparser.setSuper(super);
  }

  unsigned interfaceCount = s->read2();
  s->skip(interfaceCount * 2);
  //   for (unsigned i = 0; i < interfaceCount; ++i) {
  //     const char* name = reinterpret_cast<const char*>
  //       (pool[pool[s->read2() - 1] - 1]);
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

      clparser.add(
          FieldSpec(false, new Field(clparser.cl, memberType, spec, name)));
    }
  }

  if (clparser.cl->super) {
    clparser.cl->methods.insert(clparser.cl->super->methods.begin(),
                                clparser.cl->super->methods.end());
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

    const char* name = reinterpret_cast<const char*>(pool[nameIndex - 1]);
    const char* spec = reinterpret_cast<const char*>(pool[specIndex - 1]);

    if ((flags & (ACC_STATIC | ACC_PRIVATE)) == 0 and *name != '<') {
      clparser.cl->methods.insert(Method(name, spec));
      clparser.cl->overridesMethods = true;
    }
  }
}

void parseType(Finder* finder, Module& module, Object* p)
{
  const char* name = string(car(p));

  Class* cl = new Class(name);

  ClassParser clparser(cl);

  const char* javaName = 0;
  if (cdr(p) and car(cdr(p))->type == Object::String) {
    p = cdr(p);
    javaName = string(car(p));
    cl->javaName = javaName;
  }

  bool isJavaType = javaName and *javaName != '[';

  if (isJavaType) {
    class Client : public Stream::Client {
     public:
      virtual void NO_RETURN handleError()
      {
        abort();
      }
    } client;
    System::Region* region = finder->find(append(javaName, ".class"));
    if (region == 0) {
      return;
    }
    Stream s(&client, region->start(), region->length());
    parseJavaClass(module, clparser, &s);
    region->dispose();
  }

  module.add(cl);

  for (p = cdr(p); p; p = cdr(p)) {
    parseSubdeclaration(module, clparser, car(p));
  }

  if (not isJavaType) {
    if (cl->super) {
      cl->methods.insert(cl->super->methods.begin(), cl->super->methods.end());
    }
  }
}

void parseDeclaration(Finder* finder, Module& module, Object* p)
{
  const char* spec = string(car(p));
  if (equal(spec, "type")) {
    parseType(finder, module, cdr(p));
  } else {
    fprintf(stderr, "unexpected declaration spec: %s\n", spec);
    abort();
  }
}

void parse(Finder* finder, Input* in, Module& module)
{
  Object* eos = Singleton::make(Object::Eos);
  List declarations;

  Object* o;
  while ((o = read(in, eos, 0)) != eos) {
    parseDeclaration(finder, module, o);
  }
}

void layoutClass(Module& module, Class* cl)
{
  if (cl->fixedSize >= 0) {
    return;
  }

  unsigned offset = BytesPerWord;

  unsigned size = 0;
  unsigned alignment = BytesPerWord;

  alignment = BytesPerWord;

  for (const auto f : cl->fields) {
    f->elementSize = sizeOf(module, f->typeName);

    if (!f->polyfill) {  // polyfills contribute no size
      alignment = f->elementSize;
      offset = (offset + alignment - 1) & ~(alignment - 1);
      f->offset = offset;

      size = f->elementSize;

      offset += size;
    }
  }
  if (cl->arrayField) {
    Field* f = cl->arrayField;

    f->elementSize = sizeOf(module, f->typeName);

    alignment = f->elementSize;
    offset = (offset + alignment - 1) & ~(alignment - 1);
    f->offset = offset;
  }
  // offset = (offset + BytesPerWord - 1) & ~(BytesPerWord - 1);
  cl->fixedSize = offset;
}

void layoutClasses(Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;
    layoutClass(module, cl);
  }
}

void writeOffset(Output* out, size_t offset)
{
  out->write(offset);
}

void writeOffset(Output* out, Class* cl)
{
  out->write(cl->fixedSize);
  if (cl->arrayField) {
    out->write(" + pad(length * ");
    out->write(cl->arrayField->elementSize);
    out->write(")");
  }
}

std::string cppClassName(Class* cl)
{
  if (cl->name == "jobject") {
    return "object";
  } else {
    return "Gc" + capitalize(cl->name) + "*";
  }
}

std::string cppFieldType(Module& module, Field* f)
{
  if (f->javaSpec.size() != 0) {
    if (f->javaSpec[0] == 'L') {
      std::string className = f->javaSpec.substr(1, f->javaSpec.size() - 2);
      const auto it = module.javaClasses.find(className);
      if (it != module.javaClasses.end()) {
        return cppClassName(it->second);
      }
    } else if (f->javaSpec[0] == '[') {
      const auto it = module.javaClasses.find(f->javaSpec);
      if (it != module.javaClasses.end()) {
        return cppClassName(it->second);
      }
    }
  }
  const auto it = module.classes.find(f->typeName);
  assert(f->typeName.size() > 0);
  if (it != module.classes.end()) {
    return cppClassName(it->second);
  } else if (f->typeName == "maybe_object") {
    return "uintptr_t";
  } else {
    return f->typeName;
  }
}

void writeAccessor(Output* out, Class* cl, Field* f)
{
  out->write("const unsigned ");
  out->write(capitalize(cl->name));
  out->write(capitalize(f->name));
  out->write(" = ");
  writeOffset(out, f->offset);
  out->write(";\n\n");

  out->write("#define HAVE_");
  out->write(capitalize(cl->name));
  out->write(capitalize(f->name));
  out->write(" 1\n");

  if (! f->javaSpec.empty()) {
    std::string s = f->javaSpec;
    std::replace(s.begin(), s.end(), '/', '_');
    std::replace(s.begin(), s.end(), '$', '_');
    std::replace(s.begin(), s.end(), ';', '_');
    std::replace(s.begin(), s.end(), '[', '_');

    out->write("#define HAVE_");
    out->write(capitalize(cl->name));
    out->write(capitalize(f->name));
    out->write("_");
    out->write(s);
    out->write(" 1\n\n");
  }
}

void writeAccessors(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;
    for (const auto f : cl->fields) {
      if (!f->polyfill) {
        writeAccessor(out, cl, f);
      }
    }
    if (cl->arrayField) {
      writeAccessor(out, cl, cl->arrayField);
    }
  }
}

void writeSizes(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;

    out->write("const unsigned FixedSizeOf");
    out->write(capitalize(cl->name));
    out->write(" = ");
    out->write(cl->fixedSize);
    out->write(";\n\n");

    if (cl->arrayField) {
      out->write("const unsigned ArrayElementSizeOf");
      out->write(capitalize(cl->name));
      out->write(" = ");
      out->write(cl->arrayField->elementSize);
      out->write(";\n\n");
    }
  }
}

std::string obfuscate(const std::string& s)
{
  if (s == "default" || s == "template" || s == "class" || s == "register"
      || s == "this") {
    return s + "_";
  } else {
    return s;
  }
}

void writeConstructorParameters(Output* out, Module& module, Class* cl)
{
  for (const auto f : cl->fields) {
    if (!f->polyfill) {
      out->write(", ");
      out->write(cppFieldType(module, f));
      out->write(" ");
      out->write(obfuscate(f->name));
    }
  }
}

void writeConstructorArguments(Output* out, Class* cl)
{
  for (const auto f : cl->fields) {
    if (!f->polyfill) {
      out->write(", ");
      out->write(obfuscate(f->name));
    }
  }
}

void writeConstructorInitializations(Output* out, Class* cl)
{
  for (const auto f : cl->fields) {
    if (!f->polyfill) {
      out->write("  o->set");
      out->write(capitalize(f->name));
      out->write("(t, ");
      out->write(obfuscate(f->name));
      out->write(");\n");
    }
  }
}

void writeClassDeclarations(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;

    out->write("class Gc");
    out->write(capitalize(cl->name));
    out->write(";\n");
  }
  out->write("\n");
}

bool isFieldGcVisible(Module& module, Field* f)
{
  return enumName(module, f) == "object" && !f->nogc;
}

bool isFieldGcMarkable(Module& module, Field* f)
{
  return (f->typeName == "maybe_object" || enumName(module, f) == "object")
         && !f->nogc;
}

void writeClassAccessors(Output* out, Module& module, Class* cl)
{
  for (const auto f : cl->fields) {
    if (!f->polyfill) {
      out->write("  void set");
      out->write(capitalize(f->name));
      out->write("(Thread* t UNUSED, ");
      out->write(cppFieldType(module, f));
      out->write(" value) { ");
      if (isFieldGcMarkable(module, f)) {
        out->write("setField(t, this , ");
        out->write(capitalize(cl->name));
        out->write(capitalize(f->name));
        out->write(", reinterpret_cast<object>(value));");
      } else {
        out->write("field_at<");
        out->write(cppFieldType(module, f));
        out->write(">(");
        out->write(capitalize(cl->name));
        out->write(capitalize(f->name));
        out->write(") = value;");
      }
      out->write(" }\n");

      out->write("  ");
      out->write(cppFieldType(module, f));
      out->write("* ");
      out->write(obfuscate(f->name));
      out->write("Ptr() { return &field_at<");
      out->write(cppFieldType(module, f));
      out->write(">(");
      out->write(capitalize(cl->name));
      out->write(capitalize(f->name));
      out->write("); }\n");
    }

    out->write("  ");
    out->write(cppFieldType(module, f));
    if (!f->polyfill && !isFieldGcMarkable(module, f)) {
      out->write("&");
    }
    out->write(" ");
    out->write(obfuscate(f->name));
    if (f->threadParam || f->polyfill) {
      out->write("(Thread*");
    } else {
      out->write("(");
    }
    if (f->polyfill) {
      out->write("); // polyfill, assumed to be implemented elsewhere\n");
    } else {
      out->write(") { return field_at<");
      out->write(cppFieldType(module, f));
      out->write(">(");
      out->write(capitalize(cl->name));
      out->write(capitalize(f->name));
      out->write("); }\n");
    }
  }
  if (cl->arrayField) {
    Field* f = cl->arrayField;
    out->write("  avian::util::Slice<");
    if (isFieldGcVisible(module, f)) {
      out->write("const ");
    }
    out->write(cppFieldType(module, f));
    out->write("> ");
    out->write(obfuscate(f->name));
    out->write("() { return avian::util::Slice<");
    if (isFieldGcVisible(module, f)) {
      out->write("const ");
    }
    out->write(cppFieldType(module, f));
    out->write("> (&field_at<");
    if (isFieldGcVisible(module, f)) {
      out->write("const ");
    }
    out->write(cppFieldType(module, f));
    out->write(">(");
    out->write(capitalize(cl->name));
    out->write(capitalize(f->name));
    out->write("), field_at<uintptr_t>(");
    out->write(capitalize(cl->name));
    out->write("Length)); }\n");

    out->write("  void set");
    out->write(capitalize(f->name));
    out->write("Element(Thread* t UNUSED, size_t index, ");
    out->write(cppFieldType(module, f));
    out->write(" value) { ");
    if (isFieldGcMarkable(module, f)) {
      out->write("setField(t, this , ");
      out->write(capitalize(cl->name));
      out->write(capitalize(f->name));
      out->write(" + index * (");
      out->write(sizeOf(module, f->typeName));
      out->write("), reinterpret_cast<object>(value));");
    } else {
      out->write("field_at<");
      out->write(cppFieldType(module, f));
      out->write(">(");
      out->write(capitalize(cl->name));
      out->write(capitalize(f->name));
      out->write(" + index * (");
      out->write(sizeOf(module, f->typeName));
      out->write(")) = value;");
    }
    out->write(" }\n");
  }
}

void writeClasses(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;

    out->write("class Gc");
    out->write(capitalize(cl->name));
    out->write(": public GcObject {\n");
    out->write(" public:\n");
    out->write("  static const Gc::Type Type = Gc::");
    out->write(capitalize(cl->name));
    out->write("Type;\n");
    out->write("  static const size_t FixedSize = FixedSizeOf");
    out->write(capitalize(cl->name));
    out->write(";\n\n");

    out->write("  static Gc" + capitalize(cl->name) + "* makeZeroed(Thread* t");
    if (cl->arrayField) {
      out->write(", uintptr_t length");
    }
    out->write(");\n");

    writeClassAccessors(out, module, cl);

    out->write("};\n\n");
  }
}

void writeInitializerDeclarations(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;
    out->write("void init");
    out->write(capitalize(cl->name));
    out->write("(Thread* t, Gc");
    out->write(capitalize(cl->name));
    out->write("* o");

    writeConstructorParameters(out, module, cl);

    out->write(");\n\n");
  }
}

void writeConstructorDeclarations(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;
    out->write("Gc");
    out->write(capitalize(cl->name));
    out->write("* make");
    out->write(capitalize(cl->name));
    out->write("(Thread* t");

    writeConstructorParameters(out, module, cl);

    out->write(");\n\n");
  }
}

void writeInitializers(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;
    out->write("void init");
    out->write(capitalize(cl->name));
    out->write("(Thread* t, Gc");
    out->write(capitalize(cl->name));
    out->write("* o");

    writeConstructorParameters(out, module, cl);

    out->write(")\n{\n");

    out->write("  setObjectClass(t, reinterpret_cast<object>(o), ");
    out->write(
        "reinterpret_cast<GcClass*>(reinterpret_cast<GcArray*>(t->m->types)->"
        "body()[Gc::");
    out->write(capitalize(cl->name));
    out->write("Type]));\n");

    writeConstructorInitializations(out, cl);

    out->write("}\n\n");
  }
}

void writeConstructors(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;

    bool hasObjectMask = cl->name == "singleton";

    std::string name = "Gc" + capitalize(cl->name);

    out->write(name + "* " + name + "::makeZeroed(Thread* t");
    if (cl->arrayField) {
      out->write(", uintptr_t length");
    }
    out->write(")\n{\n");
    out->write("  " + name + "* o = reinterpret_cast<" + name
               + "*>(allocate(t, ");
    writeOffset(out, cl);
    if (hasObjectMask) {
      out->write(", true");
    } else {
      out->write(", false");
    }
    out->write("));\n");
    out->write("  setObjectClass(t, reinterpret_cast<object>(o), ");
    out->write(
        "reinterpret_cast<GcClass*>(reinterpret_cast<GcArray*>(t->m->types)->"
        "body()[Gc::");
    out->write(capitalize(cl->name));
    out->write("Type]));\n");
    out->write("  return o;\n");
    out->write("}\n\n");

    out->write(name + "* make" + capitalize(cl->name));
    out->write("(Thread* t");

    writeConstructorParameters(out, module, cl);

    out->write(")\n{\n");
    for (const auto f : cl->fields) {
      if (enumName(module, f) == "object" and not f->nogc) {
        out->write("  PROTECT(t, ");
        out->write(obfuscate(f->name));
        out->write(");\n");

        hasObjectMask = true;
      }
    }
    if (cl->arrayField) {
      Field* f = cl->arrayField;
      if (f->typeName == "object" and not f->nogc) {
        hasObjectMask = true;
      }
    }

    out->write("  " + name + "* o = reinterpret_cast<" + name
               + "*>(allocate(t, ");
    writeOffset(out, cl);
    if (hasObjectMask) {
      out->write(", true");
    } else {
      out->write(", false");
    }
    out->write("));\n");

    out->write("  init");
    out->write(capitalize(cl->name));
    out->write("(t, o");
    writeConstructorArguments(out, cl);
    out->write(");\n");

    out->write("  return o;\n}\n\n");
  }
}

void writeEnums(Output* out, Module& module)
{
  bool wrote = false;
  for (const auto p : module.classes) {
    Class* cl = p.second;
    if (wrote) {
      out->write(",\n");
    } else {
      wrote = true;
    }
    out->write(capitalize(cl->name));
    out->write("Type");
  }

  if (wrote) {
    out->write("\n");
  }
}

void set(uint32_t* mask, unsigned index)
{
  if (index < 32) {
    *mask |= 1 << index;
  } else {
    UNREACHABLE;
  }
}

void set(std::vector<uint32_t>& mask, unsigned index)
{
  set(&(mask[index / 32]), index % 32);
}

std::vector<uint32_t> typeObjectMask(Module& module, Class* cl)
{
  std::vector<uint32_t> mask(ceilingDivide(
      cl->fixedSize + (cl->arrayField ? cl->arrayField->elementSize : 0),
      32 * BytesPerWord));

  set(mask, 0);

  for (const auto f : cl->fields) {
    unsigned offset = f->offset / BytesPerWord;
    if (isFieldGcVisible(module, f)) {
      set(mask, offset);
    }
  }

  if (cl->arrayField) {
    Field* f = cl->arrayField;
    unsigned offset = f->offset / BytesPerWord;
    if (isFieldGcVisible(module, f)) {
      set(mask, offset);
    }
  }

  return mask;
}

bool trivialMask(const std::vector<uint32_t>& mask)
{
  if (mask[0] != 1) {
    return false;
  }

  for (size_t i = 1; i < mask.size(); ++i) {
    if (mask[i]) {
      return false;
    }
  }

  return true;
}

void writeInitialization(Output* out,
                         Module& module,
                         std::set<Class*>& alreadyInited,
                         Class* cl)
{
  if (alreadyInited.find(cl) != alreadyInited.end()) {
    return;
  }
  alreadyInited.insert(cl);
  if (cl->super && cl->name != "intArray" && cl->name != "class") {
    writeInitialization(out, module, alreadyInited, cl->super);
  }

  std::vector<uint32_t> mask = typeObjectMask(module, cl);
  bool trivialMask = local::trivialMask(mask);
  if (trivialMask) {
    out->write("{ ");
  } else {
    out->write("{ uint32_t mask[");
    out->write(mask.size());
    out->write("] = { ");
    for (size_t i = 0; i < mask.size(); ++i) {
      out->writeUnsigned(mask[i]);
      if (i < mask.size() - 1) {
        out->write(", ");
      }
    }
    out->write(" };\n");
  }

  out->write("bootClass(t, Gc::");
  out->write(capitalize(cl->name));
  out->write("Type, ");

  if (cl->super) {
    out->write("Gc::");
    out->write(capitalize(cl->super->name));
    out->write("Type");
  } else {
    out->write("-1");
  }
  out->write(", ");

  if (trivialMask) {
    out->write("0");
  } else {
    out->write("mask");
  }
  out->write(", ");

  out->write(cl->fixedSize);
  out->write(", ");

  out->write(cl->arrayField ? cl->arrayField->elementSize : 0);
  out->write(", ");

  out->write(cl->methods.size());
  out->write("); }\n");
}

void writeInitializations(Output* out, Module& module)
{
  std::set<Class*> alreadyInited;

  writeInitialization(out, module, alreadyInited, module.classes["intArray"]);
  writeInitialization(out, module, alreadyInited, module.classes["class"]);

  for (const auto p : module.classes) {
    Class* cl = p.second;
    if (cl->name != "intArray" && cl->name != "class") {
      writeInitialization(out, module, alreadyInited, cl);
    }
  }
}

void writeJavaInitialization(Output* out,
                             Class* cl,
                             std::set<Class*>& alreadyInited)
{
  if (alreadyInited.find(cl) != alreadyInited.end()) {
    return;
  }

  alreadyInited.insert(cl);

  if (cl->super) {
    writeJavaInitialization(out, cl->super, alreadyInited);
  }

  out->write("bootJavaClass(t, Gc::");
  out->write(capitalize(cl->name));
  out->write("Type, ");

  if (cl->super) {
    out->write("Gc::");
    out->write(capitalize(cl->super->name));
    out->write("Type");
  } else {
    out->write("-1");
  }
  out->write(", \"");

  out->write(cl->javaName);
  out->write("\", ");

  if (cl->overridesMethods) {
    out->write(cl->methods.size());
  } else {
    out->write("-1");
  }
  out->write(", bootMethod);\n");
}

void writeJavaInitializations(Output* out, Module& module)
{
  std::set<Class*> alreadyInited;
  for (const auto p : module.classes) {
    Class* cl = p.second;
    if (cl->javaName.size()) {
      writeJavaInitialization(out, cl, alreadyInited);
    }
  }
}

void writeNameInitialization(Output* out, Class* cl)
{
  out->write("nameClass(t, Gc::");
  out->write(capitalize(cl->name));
  out->write("Type, \"");
  if (cl->name == "jbyte" or cl->name == "jboolean" or cl->name == "jshort"
      or cl->name == "jchar" or cl->name == "jint" or cl->name == "jlong"
      or cl->name == "jfloat" or cl->name == "jdouble" or cl->name == "jvoid") {
    out->write(cl->name.substr(1, cl->name.size() - 1));
  } else {
    out->write("vm::");
    out->write(cl->name);
  }
  out->write("\");\n");
}

void writeNameInitializations(Output* out, Module& module)
{
  for (const auto p : module.classes) {
    Class* cl = p.second;
    if (!cl->javaName.size()) {
      writeNameInitialization(out, cl);
    }
  }
}

void writeMap(Output* out, Module& module, Class* cl)
{
  std::ostringstream ss;
  for (const auto f : cl->fields) {
    ss << "Type_";
    ss << enumName(module, f);
    if (f->nogc) {
      ss << "_nogc";
    }

    ss << ", ";
  }

  if (cl->arrayField) {
    Field* f = cl->arrayField;
    ss << "Type_array, ";
    ss << "Type_";
    ss << enumName(module, f);
    ss << ", ";
  }

  ss << "Type_none";

  out->write(ss.str());
}

void writeMaps(Output* out, Module& module)
{
  out->write("Type types[][");
  out->write(module.classes.size());
  out->write("] = {\n");
  bool wrote = false;
  for (const auto p : module.classes) {
    Class* cl = p.second;
    if (wrote) {
      out->write(",\n");
    } else {
      wrote = true;
    }

    out->write("// ");
    out->write(cl->name);
    out->write("\n{ ");
    writeMap(out, module, cl);
    out->write(" }");
  }
  out->write("\n};");
}

}  // namespace local

}  // namespace

extern "C" uint64_t vmNativeCall(void*, void*, unsigned, unsigned)
{
  abort();
}

extern "C" void vmJump(void*, void*, void*, void*, uintptr_t, uintptr_t)
{
  abort();
}

int main(int ac, char** av)
{
  ArgParser parser;
  Arg classpath(parser, true, "cp", "<classpath>");
  Arg input(parser, true, "i", "<input.def>");
  Arg output(parser, true, "o", "<output.cpp/h>");
  Arg outputType(parser,
                 true,
                 "t",
                 "<enums|declarations|constructors|initializations|java-"
                 "initializations|name-initializations|maps>");

  if (!parser.parse(ac, av)) {
    parser.printUsage(av[0]);
    exit(1);
  }

  if (!(local::equal(outputType.value, "enums")
        || local::equal(outputType.value, "declarations")
        || local::equal(outputType.value, "constructors")
        || local::equal(outputType.value, "initializations")
        || local::equal(outputType.value, "java-initializations")
        || local::equal(outputType.value, "name-initializations")
        || local::equal(outputType.value, "maps"))) {
    parser.printUsage(av[0]);
    exit(1);
  }

  System* system = makeSystem();

  class MyAllocator : public avian::util::Alloc {
   public:
    MyAllocator(System* s) : s(s)
    {
    }

    virtual void* allocate(size_t size)
    {
      void* p = s->tryAllocate(size);
      if (p == 0) {
        abort(s);
      }
      return p;
    }

    virtual void free(const void* p, size_t)
    {
      s->free(p);
    }

    System* s;
  } allocator(system);

  Finder* finder = makeFinder(system, &allocator, classpath.value, 0);

  FILE* inStream = ::fopen(input.value, "rb");
  if (inStream == 0) {
    fprintf(stderr, "unable to open %s: %s\n", input.value, strerror(errno));
    exit(1);
  }

  FileInput in(0, inStream, false);

  Module module;
  local::parse(finder, &in, module);
  local::layoutClasses(module);

  finder->dispose();
  system->dispose();

  FILE* outStream = ::fopen(output.value, "wb");
  if (outStream == 0) {
    fprintf(stderr, "unable to open %s: %s\n", output.value, strerror(errno));
    exit(1);
  }
  FileOutput out(0, outStream, false);

  if (local::equal(outputType.value, "enums")) {
    local::writeEnums(&out, module);
  } else if (local::equal(outputType.value, "declarations")) {
    out.write("const unsigned TypeCount = ");
    out.Output::write(module.classes.size());
    out.write(";\n\n");

    local::writeClassDeclarations(&out, module);
    local::writeAccessors(&out, module);
    local::writeSizes(&out, module);
    local::writeClasses(&out, module);
    local::writeInitializerDeclarations(&out, module);
    local::writeConstructorDeclarations(&out, module);
  } else if (local::equal(outputType.value, "constructors")) {
    local::writeInitializers(&out, module);
    local::writeConstructors(&out, module);
  } else if (local::equal(outputType.value, "initializations")) {
    local::writeInitializations(&out, module);
  } else if (local::equal(outputType.value, "java-initializations")) {
    local::writeJavaInitializations(&out, module);
  } else if (local::equal(outputType.value, "name-initializations")) {
    local::writeNameInitializations(&out, module);
  } else if (local::equal(outputType.value, "maps")) {
    local::writeMaps(&out, module);
  }

  out.write("\n");
}
