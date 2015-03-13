/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_TOOLS_H_
#define AVIAN_TOOLS_H_

#include <stdlib.h>

#include <avian/util/string.h>
#include <avian/util/slice.h>

#include "avian/environment.h"

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

class SymbolInfo {
 public:
  unsigned addr;
  util::String name;

  inline SymbolInfo(uint64_t addr, const util::String& name)
      : addr(addr), name(name)
  {
  }

  inline SymbolInfo() : name("")
  {
  }
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
  unsigned add(util::String str);
};

template <class T>
class DynamicArray : public util::Slice<T> {
 public:
  size_t capacity;

  DynamicArray() : util::Slice<T>((T*)malloc(10 * sizeof(T)), 0), capacity(10)
  {
  }
  ~DynamicArray()
  {
    free(util::Slice<T>::items);
  }

  void ensure(size_t more)
  {
    if (util::Slice<T>::count + more > capacity) {
      capacity = capacity * 2 + more;
      util::Slice<T>::items
          = (T*)realloc(util::Slice<T>::items, capacity * sizeof(T));
    }
  }

  void add(const T& item)
  {
    ensure(1);
    util::Slice<T>::items[util::Slice<T>::count++] = item;
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
    Arm = AVIAN_ARCH_ARM,
    Arm64 = AVIAN_ARCH_ARM64,
    UnknownArch = AVIAN_ARCH_UNKNOWN
  };

  const Format format;
  const Architecture arch;

  static Format formatFromString(const char* format);
  static Architecture archFromString(const char* arch);

  inline PlatformInfo(Format format, Architecture arch)
      : format(format), arch(arch)
  {
  }

  inline bool operator==(const PlatformInfo& other)
  {
    return format == other.format && arch == other.arch;
  }
};

class Platform {
 private:
  Platform* next;
  static Platform* first;

 public:
  PlatformInfo info;

  inline Platform(PlatformInfo info) : next(first), info(info)
  {
    first = this;
  }

  enum AccessFlags { Writable = 1 << 0, Executable = 1 << 1 };

  virtual bool writeObject(OutputStream* out,
                           util::Slice<SymbolInfo> symbols,
                           util::Slice<const uint8_t> data,
                           unsigned accessFlags,
                           unsigned alignment) = 0;

  static Platform* getPlatform(PlatformInfo info);
};

}  // namespace tools

}  // namespace avian

#endif
