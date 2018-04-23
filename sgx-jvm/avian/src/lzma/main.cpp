#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <sys/mman.h>
#include <unistd.h>
#endif
#include <fcntl.h>

#include "LzmaEnc.h"
#include "LzmaDec.h"

namespace {

int32_t read4(const uint8_t* in)
{
  return (static_cast<int32_t>(in[3]) << 24)
         | (static_cast<int32_t>(in[2]) << 16)
         | (static_cast<int32_t>(in[1]) << 8) | (static_cast<int32_t>(in[0]));
}

void* myAllocate(void*, size_t size)
{
  return malloc(size);
}

void myFree(void*, void* address)
{
  free(address);
}

SRes myProgress(void*, UInt64, UInt64)
{
  return SZ_OK;
}

void usageAndExit(const char* program)
{
  fprintf(stderr,
          "usage: %s {encode|decode} <input file> <output file> "
          "[<uncompressed size>]",
          program);
  exit(-1);
}

}  // namespace

int main(int argc, const char** argv)
{
  if (argc < 4 or argc > 5) {
    usageAndExit(argv[0]);
  }

  bool encode = strcmp(argv[1], "encode") == 0;

  uint8_t* data = 0;
  unsigned size;
  int fd = open(argv[2], O_RDONLY);
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
    const unsigned PropHeaderSize = 5;
    const unsigned HeaderSize = 13;

    SizeT outSize;
    bool outSizeIsValid;
    if (encode) {
      outSize = size * 2;
      outSizeIsValid = true;
    } else {
      int32_t outSize32 = read4(data + PropHeaderSize);
      if (outSize32 < 0 and argc == 5) {
        outSize32 = atoi(argv[4]);
      }

      outSize = outSize32;
      outSizeIsValid = outSize32 >= 0;
    }

    if (outSizeIsValid) {
      uint8_t* out = static_cast<uint8_t*>(malloc(outSize));
      if (out) {
        SizeT inSize = size;
        ISzAlloc allocator = {myAllocate, myFree};
        ELzmaStatus status = LZMA_STATUS_NOT_SPECIFIED;
        int result;
        if (encode) {
          CLzmaEncProps props;
          LzmaEncProps_Init(&props);
          props.level = 9;
          props.writeEndMark = 1;

          ICompressProgress progress = {myProgress};

          SizeT propsSize = PropHeaderSize;

          int32_t inSize32 = inSize;
          memcpy(out + PropHeaderSize, &inSize32, 4);

          result = LzmaEncode(out + HeaderSize,
                              &outSize,
                              data,
                              inSize,
                              &props,
                              out,
                              &propsSize,
                              1,
                              &progress,
                              &allocator,
                              &allocator);

          outSize += HeaderSize;
        } else {
          result = LzmaDecode(out,
                              &outSize,
                              data + HeaderSize,
                              &inSize,
                              data,
                              PropHeaderSize,
                              LZMA_FINISH_END,
                              &status,
                              &allocator);
        }

        if (result == SZ_OK) {
          FILE* outFile = fopen(argv[3], "wb");

          if (outFile) {
            if (fwrite(out, outSize, 1, outFile) == 1) {
              success = true;
            } else {
              fprintf(stderr, "unable to write to %s\n", argv[3]);
            }

            fclose(outFile);
          } else {
            fprintf(stderr, "unable to open %s\n", argv[3]);
          }
        } else {
          fprintf(stderr,
                  "unable to %s data: result %d status %d\n",
                  encode ? "encode" : "decode",
                  result,
                  status);
        }

        free(out);
      } else {
        fprintf(stderr, "unable to allocate output buffer\n");
      }
    } else {
      fprintf(stderr, "unable to determine uncompressed size\n");
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
