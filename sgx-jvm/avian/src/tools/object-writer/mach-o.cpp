/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "endianness.h"

#include <avian/tools/object-writer/tools.h>

#define MH_MAGIC_64 0xfeedfacf
#define MH_MAGIC 0xfeedface

#define MH_OBJECT 1

#define LC_SYMTAB 2

#define S_REGULAR 0

#define N_SECT 0xe
#define N_EXT 0x1

#define CPU_ARCH_ABI64 0x01000000

#define CPU_TYPE_I386 7
#define CPU_TYPE_X86_64 (CPU_TYPE_I386 | CPU_ARCH_ABI64)
#define CPU_TYPE_ARM 12
#define CPU_TYPE_ARM64 (CPU_TYPE_ARM | CPU_ARCH_ABI64)

#define CPU_SUBTYPE_I386_ALL 3
#define CPU_SUBTYPE_X86_64_ALL CPU_SUBTYPE_I386_ALL
#define CPU_SUBTYPE_ARM_V7 9
#define CPU_SUBTYPE_ARM_V8 13

namespace {

using namespace avian::tools;
using namespace avian::util;

typedef int cpu_type_t;
typedef int cpu_subtype_t;
typedef int vm_prot_t;

using avian::endian::Endianness;

#define V1 Endianness<TargetLittleEndian>::v1
#define V2 Endianness<TargetLittleEndian>::v2
#define V3 Endianness<TargetLittleEndian>::v3
#define V4 Endianness<TargetLittleEndian>::v4
#define VANY Endianness<TargetLittleEndian>::vAny

inline unsigned log(unsigned n)
{
  unsigned r = 0;
  for (unsigned i = 1; i < n; ++r)
    i <<= 1;
  return r;
}

template <class AddrTy, bool TargetLittleEndian = true>
class MachOPlatform : public Platform {
 public:
  struct FileHeader {
    uint32_t magic;
    cpu_type_t cputype;
    cpu_subtype_t cpusubtype;
    uint32_t filetype;
    uint32_t ncmds;
    uint32_t sizeofcmds;

    union {
      uint32_t flags;
      AddrTy flagsAndMaybeReserved;
    };
  };

  struct SegmentCommand {
    uint32_t cmd;
    uint32_t cmdsize;
    char segname[16];
    AddrTy vmaddr;
    AddrTy vmsize;
    AddrTy fileoff;
    AddrTy filesize;
    vm_prot_t maxprot;
    vm_prot_t initprot;
    uint32_t nsects;
    uint32_t flags;
  };

  struct Section {
    char sectname[16];
    char segname[16];
    AddrTy addr;
    AddrTy size;
    uint32_t offset;
    uint32_t align;
    uint32_t reloff;
    uint32_t nreloc;
    uint32_t flags;
    uint32_t reserved1;
    AddrTy reserved2AndMaybe3;
  };

  struct NList {
    union {
      uint32_t n_strx;
    } n_un;
    uint8_t n_type;
    uint8_t n_sect;
    uint16_t n_desc;
    AddrTy n_value;
  };

  struct SymtabCommand {
    uint32_t cmd;
    uint32_t cmdsize;
    uint32_t symoff;
    uint32_t nsyms;
    uint32_t stroff;
    uint32_t strsize;
  };

  static const unsigned BytesPerWord = sizeof(AddrTy);
  static const unsigned Segment = BytesPerWord == 8 ? 0x19 : 1;
  static const unsigned Magic = BytesPerWord == 8 ? 0xfeedfacf : 0xfeedface;

  static inline unsigned pad(unsigned n)
  {
    return (n + (BytesPerWord - 1)) & ~(BytesPerWord - 1);
  }

  virtual bool writeObject(OutputStream* out,
                           Slice<SymbolInfo> symbols,
                           Slice<const uint8_t> data,
                           unsigned accessFlags,
                           unsigned alignment)
  {
    cpu_type_t cpuType;
    cpu_subtype_t cpuSubType;
    switch (info.arch) {
    case PlatformInfo::x86_64:
      cpuType = CPU_TYPE_X86_64;
      cpuSubType = CPU_SUBTYPE_X86_64_ALL;
      break;
    case PlatformInfo::x86:
      cpuType = CPU_TYPE_I386;
      cpuSubType = CPU_SUBTYPE_I386_ALL;
      break;
    case PlatformInfo::Arm:
      cpuType = CPU_TYPE_ARM;
      cpuSubType = CPU_SUBTYPE_ARM_V7;
      break;
    case PlatformInfo::Arm64:
      cpuType = CPU_TYPE_ARM64;
      cpuSubType = CPU_SUBTYPE_ARM_V8;
      break;
    default:
      // should never happen (see MachOPlatform declarations at bottom)
      fprintf(stderr, "unsupported architecture: %d\n", info.arch);
      return false;
    }

    const char* segmentName;
    const char* sectionName;
    if (accessFlags & Writable) {
      if (accessFlags & Executable) {
        segmentName = "__RWX";
        sectionName = "__rwx";
      } else {
        segmentName = "__DATA";
        sectionName = "__data";
      }
    } else {
      segmentName = "__TEXT";
      sectionName = "__text";
    }

    FileHeader header = {
        V4(Magic),  // magic
        static_cast<cpu_type_t>(V4(cpuType)),
        static_cast<cpu_subtype_t>(V4(cpuSubType)),
        V4(MH_OBJECT),  // filetype,
        V4(2),          // ncmds
        V4(sizeof(SegmentCommand) + sizeof(Section)
           + sizeof(SymtabCommand)),  // sizeofcmds
        {V4(0)}                       // flags
    };

    AddrTy finalSize = pad(data.count);

    SegmentCommand segment = {
        V4(Segment),                                   // cmd
        V4(sizeof(SegmentCommand) + sizeof(Section)),  // cmdsize
        "",                                            // segname
        VANY(static_cast<AddrTy>(0)),                  // vmaddr
        VANY(static_cast<AddrTy>(finalSize)),          // vmsize
        VANY(static_cast<AddrTy>(sizeof(FileHeader) + sizeof(SegmentCommand)
                                 + sizeof(Section)
                                 + sizeof(SymtabCommand))),  // fileoff
        VANY(static_cast<AddrTy>(finalSize)),                // filesize
        static_cast<vm_prot_t>(V4(7)),                       // maxprot
        static_cast<vm_prot_t>(V4(7)),                       // initprot
        V4(1),                                               // nsects
        V4(0)                                                // flags
    };

    strncpy(segment.segname, segmentName, sizeof(segment.segname));

    Section sect = {
        "",                                    // sectname
        "",                                    // segname
        VANY(static_cast<AddrTy>(0)),          // addr
        VANY(static_cast<AddrTy>(finalSize)),  // size
        V4(sizeof(FileHeader) + sizeof(SegmentCommand) + sizeof(Section)
           + sizeof(SymtabCommand)),  // offset
        V4(log(alignment)),           // align
        V4(0),                        // reloff
        V4(0),                        // nreloc
        V4(S_REGULAR),                // flags
        V4(0),                        // reserved1
        V4(0),                        // reserved2
    };

    strncpy(sect.segname, segmentName, sizeof(sect.segname));
    strncpy(sect.sectname, sectionName, sizeof(sect.sectname));

    StringTable strings;
    strings.add("");
    Buffer symbolList;

    for (SymbolInfo* sym = symbols.begin(); sym != symbols.end(); sym++) {
      unsigned offset = strings.length;
      strings.write("_", 1);
      strings.add(sym->name);
      NList symbol = {
          {V4(offset)},                         // n_un
          V1(N_SECT | N_EXT),                   // n_type
          V1(1),                                // n_sect
          V2(0),                                // n_desc
          VANY(static_cast<AddrTy>(sym->addr))  // n_value
      };
      symbolList.write(&symbol, sizeof(NList));
    }

    SymtabCommand symbolTable = {
        V4(LC_SYMTAB),              // cmd
        V4(sizeof(SymtabCommand)),  // cmdsize
        V4(sizeof(FileHeader) + sizeof(SegmentCommand) + sizeof(Section)
           + sizeof(SymtabCommand) + finalSize),  // symoff
        V4(symbols.count),                        // nsyms
        V4(sizeof(FileHeader) + sizeof(SegmentCommand) + sizeof(Section)
           + sizeof(SymtabCommand) + finalSize
           + (sizeof(NList) * symbols.count)),  // stroff
        V4(strings.length),                     // strsize
    };

    out->writeChunk(&header, sizeof(header));
    out->writeChunk(&segment, sizeof(segment));
    out->writeChunk(&sect, sizeof(sect));
    out->writeChunk(&symbolTable, sizeof(symbolTable));

    out->writeChunk(data.items, data.count);
    out->writeRepeat(0, finalSize - data.count);

    out->writeChunk(symbolList.data, symbolList.length);

    out->writeChunk(strings.data, strings.length);

    return true;
  }

  MachOPlatform(PlatformInfo::Architecture arch)
      : Platform(PlatformInfo(PlatformInfo::MachO, arch))
  {
  }
};

MachOPlatform<uint32_t> darwinx86Platform(PlatformInfo::x86);
MachOPlatform<uint32_t> darwinArmPlatform(PlatformInfo::Arm);
MachOPlatform<uint64_t> darwinArm64Platform(PlatformInfo::Arm64);
MachOPlatform<uint64_t> darwinx86_64Platform(PlatformInfo::x86_64);

}  // namespace
