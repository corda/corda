/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "stdint.h"
#include "stdio.h"
#include "string.h"

#define MH_MAGIC 0xfeedface

#define MH_OBJECT 1

#define LC_SEGMENT 1
#define LC_SYMTAB 2

#define S_REGULAR 0

#define N_SECT 0xe
#define N_EXT 0x1

#define CPU_ARCH_ABI64 0x01000000

#define CPU_TYPE_I386 7
#define CPU_TYPE_X86_64 (CPU_TYPE_I386 | CPU_ARCH_ABI64)
#define CPU_TYPE_POWERPC 18

#define CPU_SUBTYPE_I386_ALL 3
#define CPU_SUBTYPE_X86_64_ALL CPU_SUBTYPE_I386_ALL
#define CPU_SUBTYPE_POWERPC_ALL 0

#if (BITS_PER_WORD == 64)
#  define FileHeader mach_header_64
#  define SegmentCommand segment_command_64
#  define Section section_64
#elif (BITS_PER_WORD == 32)
#  define FileHeader mach_header
#  define SegmentCommand segment_command
#  define Section section
#else
#  error
#endif

namespace {

typedef int cpu_type_t;
typedef int cpu_subtype_t;
typedef int vm_prot_t;

struct mach_header_64 {
  uint32_t magic;
  cpu_type_t cputype;
  cpu_subtype_t cpusubtype;
  uint32_t filetype;
  uint32_t ncmds;
  uint32_t sizeofcmds;
  uint32_t flags;
  uint32_t reserved;
};

struct segment_command_64 {
  uint32_t cmd;
  uint32_t cmdsize;
  char segname[16];
  uint64_t vmaddr;
  uint64_t vmsize;
  uint64_t fileoff;
  uint64_t filesize;
  vm_prot_t maxprot;
  vm_prot_t initprot;
  uint32_t nsects;
  uint32_t flags;
};

struct section_64 {
  char sectname[16];
  char segname[16];
  uint64_t addr;
  uint64_t size;
  uint32_t offset;
  uint32_t align;
  uint32_t reloff;
  uint32_t nreloc;
  uint32_t flags;
  uint32_t reserved1;
  uint32_t reserved2;
  uint32_t reserved3;
};

struct mach_header {
  uint32_t magic;
  cpu_type_t cputype;
  cpu_subtype_t cpusubtype;
  uint32_t filetype;
  uint32_t ncmds;
  uint32_t sizeofcmds;
  uint32_t flags;
};

struct segment_command {
  uint32_t cmd;
  uint32_t cmdsize;
  char segname[16];
  uint32_t vmaddr;
  uint32_t vmsize;
  uint32_t fileoff;
  uint32_t filesize;
  vm_prot_t maxprot;
  vm_prot_t initprot;
  uint32_t nsects;
  uint32_t flags;
};

struct section {
  char sectname[16];
  char segname[16];
  uint32_t addr;
  uint32_t size;
  uint32_t offset;
  uint32_t align;
  uint32_t reloff;
  uint32_t nreloc;
  uint32_t flags;
  uint32_t reserved1;
  uint32_t reserved2;
};

struct symtab_command {
  uint32_t cmd;
  uint32_t cmdsize;
  uint32_t symoff;
  uint32_t nsyms;
  uint32_t stroff;
  uint32_t strsize;
};

struct nlist {
  union {
    int32_t n_strx;
  } n_un;
  uint8_t n_type;
  uint8_t n_sect;
  int16_t n_desc;
  uint32_t n_value;
};

inline unsigned
pad(unsigned n)
{
  return (n + ((BITS_PER_WORD / 8) - 1)) & ~((BITS_PER_WORD / 8) - 1);
}

inline unsigned
log(unsigned n)
{
  unsigned r = 0;
  for (unsigned i = 1; i < n; ++r) i <<= 1;
  return r;
}

void
writeObject(const uint8_t* data, unsigned size, FILE* out,
            const char* startName, const char* endName,
            const char* segmentName, const char* sectionName,
            unsigned alignment, cpu_type_t cpuType, cpu_subtype_t cpuSubType)
{
  unsigned startNameLength = strlen(startName) + 1;
  unsigned endNameLength = strlen(endName) + 1;

  FileHeader header = {
    MH_MAGIC, // magic
    cpuType,
    cpuSubType,
    MH_OBJECT, // filetype,
    2, // ncmds
    sizeof(SegmentCommand)
    + sizeof(Section)
    + sizeof(symtab_command), // sizeofcmds
    0 // flags
  };

  SegmentCommand segment = {
    LC_SEGMENT, // cmd
    sizeof(SegmentCommand) + sizeof(Section), // cmdsize
    "", // segname
    0, // vmaddr
    pad(size), // vmsize
    sizeof(FileHeader)
    + sizeof(SegmentCommand)
    + sizeof(Section)
    + sizeof(symtab_command), // fileoff
    pad(size), // filesize
    7, // maxprot
    7, // initprot
    1, // nsects
    0 // flags
  };

  strncpy(segment.segname, segmentName, sizeof(segment.segname));

  Section sect = {
    "", // sectname
    "", // segname
    0, // addr
    pad(size), // size
    sizeof(FileHeader)
    + sizeof(SegmentCommand)
    + sizeof(Section)
    + sizeof(symtab_command), // offset
    log(alignment), // align
    0, // reloff
    0, // nreloc
    S_REGULAR, // flags
    0, // reserved1
    0, // reserved2
  };

  strncpy(sect.segname, segmentName, sizeof(sect.segname));
  strncpy(sect.sectname, sectionName, sizeof(sect.sectname));

  symtab_command symbolTable = {
    LC_SYMTAB, // cmd
    sizeof(symtab_command), // cmdsize
    sizeof(FileHeader)
    + sizeof(SegmentCommand)
    + sizeof(Section)
    + sizeof(symtab_command)
    + pad(size), // symoff
    2, // nsyms
    sizeof(FileHeader)
    + sizeof(SegmentCommand)
    + sizeof(Section)
    + sizeof(symtab_command)
    + pad(size)
    + (sizeof(struct nlist) * 2), // stroff
    1 + startNameLength + endNameLength, // strsize
  };

  struct nlist symbolList[] = {
    {
      1, // n_un
      N_SECT | N_EXT, // n_type
      1, // n_sect
      0, // n_desc
      0 // n_value
    },
    {
      1 + startNameLength, // n_un
      N_SECT | N_EXT, // n_type
      1, // n_sect
      0, // n_desc
      size // n_value
    }
  };

  fwrite(&header, 1, sizeof(header), out);
  fwrite(&segment, 1, sizeof(segment), out);
  fwrite(&sect, 1, sizeof(sect), out);
  fwrite(&symbolTable, 1, sizeof(symbolTable), out);

  fwrite(data, 1, size, out);
  for (unsigned i = 0; i < pad(size) - size; ++i) fputc(0, out);

  fwrite(&symbolList, 1, sizeof(symbolList), out);

  fputc(0, out);
  fwrite(startName, 1, startNameLength, out);
  fwrite(endName, 1, endNameLength, out);
}

} // namespace

#define MACRO_MAKE_NAME(a, b, c) a##b##c
#define MAKE_NAME(a, b, c) MACRO_MAKE_NAME(a, b, c)

namespace binaryToObject {

bool
MAKE_NAME(writeMachO, BITS_PER_WORD, Object)
  (uint8_t* data, unsigned size, FILE* out, const char* startName,
   const char* endName, const char* architecture, unsigned alignment,
   bool, bool executable)
{
  cpu_type_t cpuType;
  cpu_subtype_t cpuSubType;
  if (strcmp(architecture, "x86_64") == 0) {
    cpuType = CPU_TYPE_X86_64;
    cpuSubType = CPU_SUBTYPE_X86_64_ALL;
  } else if (strcmp(architecture, "i386") == 0) {
    cpuType = CPU_TYPE_I386;
    cpuSubType = CPU_SUBTYPE_I386_ALL;
  } else if (strcmp(architecture, "powerpc") == 0) {
    cpuType = CPU_TYPE_POWERPC;
    cpuSubType = CPU_SUBTYPE_POWERPC_ALL;
  } else {
    fprintf(stderr, "unsupported architecture: %s\n", architecture);
    return false;
  }

  const char* segmentName;
  const char* sectionName;
  if (executable) {
    segmentName = "__RWX";
    sectionName = "__rwx";
  } else {
    segmentName = "__TEXT";
    sectionName = "__text";
  }

  unsigned startNameLength = strlen(startName);
  char myStartName[startNameLength + 2];
  myStartName[0] = '_';
  memcpy(myStartName + 1, startName, startNameLength + 1);

  unsigned endNameLength = strlen(endName);
  char myEndName[endNameLength + 2];
  myEndName[0] = '_';
  memcpy(myEndName + 1, endName, endNameLength + 1);

  writeObject(data, size, out, myStartName, myEndName, segmentName,
              sectionName, alignment, cpuType, cpuSubType);

  return true;
}

} // namespace binaryToObject
