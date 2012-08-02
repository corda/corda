/* Copyright (c) 2009-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_TOOLS_H_
#define AVIAN_TOOLS_H_

#include <stdlib.h>
#include "environment.h"

namespace avian {

namespace tools {

class OutputStream {
public:
  virtual void writeChunk(const void* data, size_t size) = 0;
  virtual void write(uint8_t byte);
  virtual void writeRepeat(uint8_t byte, size_t size);
};

class FileOutputStream : public OutputStream {
private:
  FILE* file;
public:
  FileOutputStream(const char* name);
  ~FileOutputStream();

  bool isValid();

  virtual void writeChunk(const void* data, size_t size);
  virtual void write(uint8_t byte);
};

class String {
public:
  const char* text;
  size_t length;

  String(const char* text);
  
  inline String(const char* text, size_t length):
    text(text),
    length(length) {}
};

class SymbolInfo {
public:
  unsigned addr;
  String name;

  inline SymbolInfo(uint64_t addr, const String& name):
    addr(addr),
    name(name) {}

  inline SymbolInfo():
    name("") {}
};

class Buffer {
public:
  size_t capacity;
  size_t length;
  uint8_t* data;

  Buffer();
  ~Buffer();

  void ensure(size_t more);
  void write(const void* d, size_t size);
};

class StringTable : public Buffer {
public:
  unsigned add(String str);
};

template<class T>
class Slice {
public:
  T* items;
  size_t count;

  inline Slice(T* items, size_t count):
    items(items),
    count(count) {}

  inline Slice(const Slice<T>& copy):
    items(copy.items),
    count(copy.count) {}

  inline T* begin() {
    return items;
  }

  inline T* end() {
    return items + count;
  }
};

template<class T>
class DynamicArray : public Slice<T> {
public:
  size_t capacity;

  DynamicArray():
    Slice<T>((T*)malloc(10 * sizeof(T)), 0),
    capacity(10) {}
  ~DynamicArray() {
    free(Slice<T>::items);
  }

  void ensure(size_t more) {
  if(Slice<T>::count + more > capacity) {
    capacity = capacity * 2 + more;
    Slice<T>::items = (T*)realloc(Slice<T>::items, capacity * sizeof(T));
  }
}

  void add(const T& item) {
    ensure(1);
    Slice<T>::items[Slice<T>::count++] = item;
  }
};

class PlatformInfo {
public:
  enum Format {
    Elf = AVIAN_FORMAT_ELF,
    Pe = AVIAN_FORMAT_PE,
    MachO = AVIAN_FORMAT_MACHO,
    UnknownFormat = AVIAN_FORMAT_UNKNOWN
  };

  enum Architecture {
    x86 = AVIAN_ARCH_X86,
    x86_64 = AVIAN_ARCH_X86_64,
    PowerPC = AVIAN_ARCH_POWERPC,
    Arm = AVIAN_ARCH_ARM,
    UnknownArch = AVIAN_ARCH_UNKNOWN
  };

  const Format format;
  const Architecture arch;

  static Format formatFromString(const char* format);
  static Architecture archFromString(const char* arch);

  inline PlatformInfo(Format format, Architecture arch):
    format(format),
    arch(arch) {}

  inline bool operator == (const PlatformInfo& other) {
    return format == other.format && arch == other.arch;
  }

  inline bool isLittleEndian() {
    return arch != PowerPC;
  }
};

class Platform {
private:
  Platform* next;
  static Platform* first;
public:
  PlatformInfo info;

  inline Platform(PlatformInfo info):
    next(first),
    info(info)
  {
    first = this;
  }

  enum AccessFlags {
    Writable = 1 << 0,
    Executable = 1 << 1
  };

  virtual bool writeObject(OutputStream* out, Slice<SymbolInfo> symbols, Slice<const uint8_t> data, unsigned accessFlags, unsigned alignment) = 0;

  static Platform* getPlatform(PlatformInfo info);
};

} // namespace tools

} // namespace avian

#endif

