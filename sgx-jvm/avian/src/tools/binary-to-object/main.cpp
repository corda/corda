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

#include <sys/stat.h>
#ifdef _WIN32
#include <windows.h>
#include <io.h>
#else
#include <sys/mman.h>
#include <unistd.h>
#endif
#include <fcntl.h>

#include <avian/tools/object-writer/tools.h>

extern "C" void __cxa_pure_virtual()
{
  abort();
}

void* operator new(size_t size)
{
  return malloc(size);
}

void operator delete(void*) throw()
{
  abort();
}

namespace {

using namespace avian::tools;
using namespace avian::util;

bool writeObject(uint8_t* data,
                 size_t size,
                 OutputStream* out,
                 const char* startName,
                 const char* endName,
                 Platform* platform,
                 unsigned alignment,
                 bool writable,
                 bool executable)
{

  SymbolInfo symbols[] = {SymbolInfo(0, startName), SymbolInfo(size, endName)};

  unsigned accessFlags = (writable ? Platform::Writable : 0)
                         | (executable ? Platform::Executable : 0);

  return platform->writeObject(out,
                               Slice<SymbolInfo>(symbols, 2),
                               Slice<const uint8_t>(data, size),
                               accessFlags,
                               alignment);
}

void usageAndExit(const char* name)
{
  fprintf(stderr,
          "usage: %s <input file> <output file> <start name> <end name> "
          "<platform> <architecture> "
          "[<alignment> [{writable|executable}...]]\n",
          name);
  exit(-1);
}

}  // namespace

int main(int argc, const char** argv)
{
  if (argc < 7 || argc > 10) {
    usageAndExit(argv[0]);
  }

  unsigned alignment = 1;
  if (argc > 7) {
    alignment = atoi(argv[7]);
  }

  bool writable = false;
  bool executable = false;

  for (int i = 8; i < argc; ++i) {
    if (strcmp("writable", argv[i]) == 0) {
      writable = true;
    } else if (strcmp("executable", argv[i]) == 0) {
      executable = true;
    } else {
      usageAndExit(argv[0]);
    }
  }

  const char* format = argv[5];
  const char* architecture = argv[6];

  Platform* platform = Platform::getPlatform(
      PlatformInfo(PlatformInfo::formatFromString(format),
                   PlatformInfo::archFromString(architecture)));

  if (!platform) {
    fprintf(stderr, "unsupported platform: %s/%s\n", format, architecture);
    return 1;
  }


  uint8_t* data = 0;
  unsigned size;
  int fd = open(argv[1], O_RDONLY);
  if (fd != -1) {
    struct stat s;
    int r = fstat(fd, &s);
    if (r != -1) {
#ifdef _WIN32
      HANDLE fm;
      HANDLE h = (HANDLE)_get_osfhandle(fd);

      fm = CreateFileMapping(h, NULL, PAGE_READONLY, 0, 0, NULL);
      data = static_cast<uint8_t*>(
          MapViewOfFile(fm, FILE_MAP_READ, 0, 0, s.st_size));

      CloseHandle(fm);
#else
      data = static_cast<uint8_t*>(
          mmap(0, s.st_size, PROT_READ, MAP_PRIVATE, fd, 0));
#endif
      size = s.st_size;
    }
    close(fd);
  }

  bool success = false;

  if (data) {
    FileOutputStream out(argv[2]);
    if (out.isValid()) {
      success = writeObject(data,
                            size,
                            &out,
                            argv[3],
                            argv[4],
                            platform,
                            alignment,
                            writable,
                            executable);
    } else {
      fprintf(stderr, "unable to open %s\n", argv[2]);
    }

#ifdef _WIN32
    UnmapViewOfFile(data);
#else
    munmap(data, size);
#endif
  } else {
    perror(argv[0]);
  }

  return (success ? 0 : -1);
}
