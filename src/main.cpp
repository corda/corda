#include "common.h"
#include "system.h"
#include "heap.h"
#include "finder.h"
#include "run.h"

using namespace vm;

namespace {

int
run(unsigned heapSize, const char* path, const char* class_, int argc,
    const char** argv)
{
  System* s = makeSystem(heapSize);
  Finder* f = makeFinder(s, path);
  Heap* heap = makeHeap(s);

  int exitCode = run(s, heap, f, class_, argc, argv);

  heap->dispose();
  f->dispose();
  s->dispose();

  return exitCode;
}

void
usageAndExit(const char* name)
{
  fprintf(stderr, "usage: %s [-cp <classpath>] [-hs <maximum heap size>] "
          "<class name> [<argument> ...]\n", name);
  exit(-1);
}

} // namespace

int
main(int ac, const char** av)
{
  unsigned heapSize = 128 * 1024 * 1024;
  const char* path = ".";
  const char* class_ = 0;
  int argc = 0;
  const char** argv = 0;

  for (int i = 1; i < ac; ++i) {
    if (strcmp(av[i], "-cp") == 0) {
      path = av[++i];
    } else if (strcmp(av[i], "-hs") == 0) {
      heapSize = atoi(av[++i]);
    } else {
      class_ = av[i++];
      if (i < ac) {
        argc = ac - i;
        argv = av + i;
        i = ac;
      }
    }
  }

  if (class_ == 0) {
    usageAndExit(av[0]);
  }

  return run(heapSize, path, class_, argc, argv);
}
