#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <dlfcn.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>

#include "LzmaDec.h"

#ifdef __MINGW32__
#  define EXPORT __declspec(dllexport)
#else
#  define EXPORT __attribute__ ((visibility("default")))
#endif

#if defined __MINGW32__ && ! defined __x86_64__
#  define SYMBOL(x) binary_exe_##x
#else
#  define SYMBOL(x) _binary_exe_##x
#endif

extern "C" {

  extern const uint8_t SYMBOL(start)[];
  extern const uint8_t SYMBOL(end)[];

} // extern "C"

namespace {

void*
myAllocate(void*, size_t size)
{
  return malloc(size);
}

void
myFree(void*, void* address)
{
  free(address);
}

} // namespace

int
main(int ac, const char** av)
{
  const unsigned PropHeaderSize = 5;
  const unsigned HeaderSize = 13;

  SizeT inSize = SYMBOL(end) - SYMBOL(start);

  int32_t outSize32;
  memcpy(&outSize32, SYMBOL(start) + PropHeaderSize, 4);
  SizeT outSize = outSize32;

  uint8_t* out = static_cast<uint8_t*>(malloc(outSize));
  if (out) {
    ISzAlloc allocator = { myAllocate, myFree };
    ELzmaStatus status = LZMA_STATUS_NOT_SPECIFIED;

    if (SZ_OK == LzmaDecode
        (out, &outSize, SYMBOL(start) + HeaderSize, &inSize, SYMBOL(start),
         PropHeaderSize, LZMA_FINISH_END, &status, &allocator))
    {
      char name[L_tmpnam];
      if (tmpnam(name)) {
        int file = open(name, O_CREAT | O_EXCL | O_WRONLY, S_IRWXU);
        if (file != -1) {
          SizeT result = write(file, out, outSize);
          free(out);

          if (close(file) == 0 and outSize == result) {
            void* library = dlopen(name, RTLD_LAZY | RTLD_GLOBAL);
            unlink(name);

            if (library) {
              void* main = dlsym(library, "main");
              if (main) {
                int (*mainFunction)(int, const char**);
                memcpy(&mainFunction, &main, sizeof(void*));
                return mainFunction(ac, av);
              } else {
                fprintf(stderr, "unable to find main in %s", name);
              }
            } else {
              fprintf(stderr, "unable to dlopen %s: %s\n", name, dlerror());
            }
          } else {
            unlink(name);

            fprintf(stderr, "close or write failed; tried %d, got %d; %s\n",
                    static_cast<int>(outSize), static_cast<int>(result),
                    strerror(errno));
          }
        } else {
          fprintf(stderr, "unable to open %s\n", name);
        }
      } else {
        fprintf(stderr, "unable to make temporary file name\n");
      }
    } else {
      fprintf(stderr, "unable to decode LZMA data\n");
    }
  } else {
    fprintf(stderr, "unable to allocate buffer of size %d\n",
            static_cast<int>(outSize));
  }

  return -1;
}
