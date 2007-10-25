#include "stdio.h"
#include "stdint.h"
#include "stdlib.h"

namespace {

void
writeCode(FILE* in, FILE* out, const char* procedure)
{
  fprintf(out, "#ifdef __MINGW32__\n");
  fprintf(out, "#  define EXPORT __declspec(dllexport)\n");
  fprintf(out, "#else\n");
  fprintf(out, "#  define EXPORT __attribute__"
          "((visibility(\"default\")))\n");
  fprintf(out, "#endif\n\n");

  fprintf(out, "namespace { const unsigned char data[] = {\n");

  const unsigned size = 4096;
  uint8_t buffer[size];
  while (not feof(in)) {
    unsigned c = fread(buffer, 1, size, in);
    for (unsigned i = 0; i < c; ++i) {
      fprintf(out, "0x%x,", buffer[i]);
    }
  }

  fprintf(out, "}; }\n\n");

  fprintf(out, "extern \"C\" EXPORT const unsigned char*\n");
  fprintf(out, "%s(unsigned* size)\n", procedure);
  fprintf(out, "{\n");
  fprintf(out, "  *size = sizeof(data);\n");
  fprintf(out, "  return data;\n");
  fprintf(out, "}\n");
}

void
usageAndExit(const char* name)
{
  fprintf(stderr, "usage: %s <input file> <procedure name>\n", name);
  exit(-1);
}

} // namespace

int
main(int ac, const char** av)
{
  if (ac != 3) {
    usageAndExit(av[0]);
  }

  FILE* in = fopen(av[1], "rb");
  if (in) {
    writeCode(in, stdout, av[2]);
    fclose(in);
  } else {
    fprintf(stderr, "trouble opening %s\n", av[1]);
    exit(-1);
  }
}
