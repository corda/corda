/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <avian/tools/object-writer/tools.h>

using namespace avian::util;

namespace avian {

namespace tools {

Buffer::Buffer() : capacity(100), length(0), data((uint8_t*)malloc(capacity))
{
}

Buffer::~Buffer()
{
  free(data);
}

void Buffer::ensure(size_t more)
{
  if (length + more > capacity) {
    capacity = capacity * 2 + more;
    data = (uint8_t*)realloc(data, capacity);
  }
}

void Buffer::write(const void* d, size_t size)
{
  ensure(size);
  memcpy(data + length, d, size);
  length += size;
}

unsigned StringTable::add(String str)
{
  unsigned offset = Buffer::length;
  Buffer::write(str.text, str.length + 1);
  return offset;
}

void OutputStream::write(uint8_t byte)
{
  writeChunk(&byte, 1);
}

void OutputStream::writeRepeat(uint8_t byte, size_t size)
{
  for (size_t i = 0; i < size; i++) {
    write(byte);
  }
}

FileOutputStream::FileOutputStream(const char* name) : file(fopen(name, "wb"))
{
}

FileOutputStream::~FileOutputStream()
{
  if (file) {
    fclose(file);
  }
}

bool FileOutputStream::isValid()
{
  return file;
}

void FileOutputStream::writeChunk(const void* data, size_t size)
{
  fwrite(data, size, 1, file);
}

void FileOutputStream::write(uint8_t byte)
{
  fputc(byte, file);
}

Platform* Platform::first = 0;

PlatformInfo::Format PlatformInfo::formatFromString(const char* format)
{
  if (strcmp(format, "elf") == 0 || strcmp(format, "linux") == 0
      || strcmp(format, "freebsd") == 0 || strcmp(format, "qnx") == 0) {
    return Elf;
  } else if (strcmp(format, "pe") == 0 || strcmp(format, "windows") == 0) {
    return Pe;
  } else if (strcmp(format, "macho") == 0 || strcmp(format, "darwin") == 0
             || strcmp(format, "ios") == 0 || strcmp(format, "macosx") == 0) {
    return MachO;
  } else {
    return UnknownFormat;
  }
}

PlatformInfo::Architecture PlatformInfo::archFromString(const char* arch)
{
  if (strcmp(arch, "i386") == 0) {
    return Architecture::x86;
  } else if (strcmp(arch, "x86_64") == 0) {
    return Architecture::x86_64;
  } else if (strcmp(arch, "arm") == 0) {
    return Architecture::Arm;
  } else if (strcmp(arch, "arm64") == 0) {
    return Architecture::Arm64;
  } else {
    return Architecture::UnknownArch;
  }
}

Platform* Platform::getPlatform(PlatformInfo info)
{
  for (Platform* p = first; p; p = p->next) {
    if (p->info == info) {
      return p;
    }
  }
  return 0;
}

}  // namespace tools

}  // namespace avian
