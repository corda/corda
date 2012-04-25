/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_TOOLS_H_
#define AVIAN_TOOLS_H_

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

class ObjectWriter {
public:

  enum AccessFlags {
    Readable = 1 << 0,
    Writable = 1 << 1,
    Executable = 1 << 2
  };

  virtual bool write(uint8_t* data, size_t size, OutputStream* out,
                     const char* startName, const char* endName,
                     unsigned alignment, unsigned accessFlags) = 0;

  virtual void dispose() = 0;
};

class PlatformInfo {
public:
  enum OperatingSystem {
    Linux, Windows, Darwin, UnknownOS
  };

  enum Architecture {
    x86, x86_64, PowerPC, Arm, UnknownArch
  };

  const OperatingSystem os;
  const Architecture arch;

  static OperatingSystem osFromString(const char* os);
  static Architecture archFromString(const char* arch);

  inline PlatformInfo(OperatingSystem os, Architecture arch):
    os(os),
    arch(arch) {}

  inline PlatformInfo(const char* os, const char* arch):
    os(osFromString(os)),
    arch(archFromString(arch)) {}

  inline bool operator == (const PlatformInfo& other) {
    return os == other.os && arch == other.arch;
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

  virtual ObjectWriter* makeObjectWriter() = 0;

  static Platform* getPlatform(PlatformInfo info);
};

} // namespace tools

} // namespace avian

#endif