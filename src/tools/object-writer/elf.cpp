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
#include <stdlib.h>

#include "endianness.h"

#include <avian/tools/object-writer/tools.h>

#define EI_NIDENT 16

#define EI_MAG0 0
#define EI_MAG1 1
#define EI_MAG2 2
#define EI_MAG3 3
#define EI_CLASS 4
#define EI_DATA 5
#define EI_VERSION 6
#define EI_OSABI 7
#define EI_ABIVERSION 8

#define ELFMAG0 0x7f
#define ELFMAG1 'E'
#define ELFMAG2 'L'
#define ELFMAG3 'F'

#define ELFCLASS64 2
#define ELFCLASS32 1

#define EV_CURRENT 1

#define ELFDATA2LSB 1
#define ELFDATA2MSB 2

#define ELFOSABI_SYSV 0

#define ET_REL 1

#define EM_386 3
#define EM_X86_64 62
#define EM_ARM 40
#define EM_AARCH64 183

#define SHT_PROGBITS 1
#define SHT_SYMTAB 2
#define SHT_STRTAB 3

#define SHF_WRITE (1 << 0)
#define SHF_ALLOC (1 << 1)
#define SHF_EXECINSTR (1 << 2)

#define STB_GLOBAL 1

#define STT_NOTYPE 0

#define STV_DEFAULT 0

#define SYMBOL_INFO(bind, type) (((bind) << 4) + ((type)&0xf))

#define OSABI ELFOSABI_SYSV

namespace {

using namespace avian::tools;
using namespace avian::util;

template <class AddrTy>
struct ElfTypes {
  typedef uint16_t Half;
  typedef uint32_t Word;
  typedef AddrTy Addr;
  typedef uint64_t Xword;
  typedef uint16_t Section;
  typedef AddrTy Off;
  typedef AddrTy XFlags;
  static const unsigned BytesPerWord = sizeof(AddrTy);
};

template <class AddrTy>
struct Symbol_Ty;

template <>
struct Symbol_Ty<uint64_t> {
  typedef ElfTypes<uint64_t> Elf;

  Elf::Word st_name;
  unsigned char st_info;
  unsigned char st_other;
  Elf::Section st_shndx;
  Elf::Addr st_value;
  Elf::Xword st_size;
};

template <>
struct Symbol_Ty<uint32_t> {
  typedef ElfTypes<uint32_t> Elf;

  Elf::Word st_name;
  Elf::Addr st_value;
  Elf::Word st_size;
  unsigned char st_info;
  unsigned char st_other;
  Elf::Section st_shndx;
};

using avian::endian::Endianness;

#define V1 Endianness<TargetLittleEndian>::v1
#define V2 Endianness<TargetLittleEndian>::v2
#define V3 Endianness<TargetLittleEndian>::v3
#define V4 Endianness<TargetLittleEndian>::v4
#define VANY Endianness<TargetLittleEndian>::vAny

unsigned getElfPlatform(PlatformInfo::Architecture arch)
{
  switch (arch) {
  case PlatformInfo::x86_64:
    return EM_X86_64;
  case PlatformInfo::x86:
    return EM_386;
  case PlatformInfo::Arm:
    return EM_ARM;
  case PlatformInfo::Arm64:
    return EM_AARCH64;
  default:
    return ~0;
  }
}

const char* getSectionName(unsigned accessFlags, unsigned& sectionFlags)
{
  sectionFlags = SHF_ALLOC;
  if (accessFlags & Platform::Writable) {
    if (accessFlags & Platform::Executable) {
      sectionFlags |= SHF_WRITE | SHF_EXECINSTR;
      return ".rwx";
    } else {
      sectionFlags |= SHF_WRITE;
      return ".data";
    }
  } else if (accessFlags & Platform::Executable) {
    sectionFlags |= SHF_EXECINSTR;
    return ".text";
  } else {
    return ".rodata";
  }
}

template <class AddrTy, bool TargetLittleEndian = true>
class ElfPlatform : public Platform {
 public:
  typedef ElfTypes<AddrTy> Elf;
  static const unsigned Class = Elf::BytesPerWord / 4;

  struct FileHeader {
    unsigned char e_ident[EI_NIDENT];
    typename Elf::Half e_type;
    typename Elf::Half e_machine;
    typename Elf::Word e_version;
    typename Elf::Addr e_entry;
    typename Elf::Off e_phoff;
    typename Elf::Off e_shoff;
    typename Elf::Word e_flags;
    typename Elf::Half e_ehsize;
    typename Elf::Half e_phentsize;
    typename Elf::Half e_phnum;
    typename Elf::Half e_shentsize;
    typename Elf::Half e_shnum;
    typename Elf::Half e_shstrndx;
  };

  struct SectionHeader {
    typename Elf::Word sh_name;
    typename Elf::Word sh_type;
    typename Elf::XFlags sh_flags;
    typename Elf::Addr sh_addr;
    typename Elf::Off sh_offset;
    typename Elf::Off sh_size;
    typename Elf::Word sh_link;
    typename Elf::Word sh_info;
    typename Elf::Addr sh_addralign;
    typename Elf::Off sh_entsize;
  };

  typedef Symbol_Ty<AddrTy> Symbol;

  static const unsigned Encoding = TargetLittleEndian ? ELFDATA2LSB
                                                      : ELFDATA2MSB;

  const unsigned machine;

  ElfPlatform(PlatformInfo::Architecture arch)
      : Platform(PlatformInfo(PlatformInfo::Elf, arch)),
        machine(getElfPlatform(arch))
  {
  }

  class FileWriter {
   public:
    unsigned sectionCount;
    unsigned sectionStringTableSectionNumber;

    AddrTy dataOffset;

    FileHeader header;
    StringTable strings;

    FileWriter(unsigned machine)
        : sectionCount(0), dataOffset(sizeof(FileHeader))
    {
      memset(&header, 0, sizeof(FileHeader));
      header.e_ident[EI_MAG0] = V1(ELFMAG0);
      header.e_ident[EI_MAG1] = V1(ELFMAG1);
      header.e_ident[EI_MAG2] = V1(ELFMAG2);
      header.e_ident[EI_MAG3] = V1(ELFMAG3);
      header.e_ident[EI_CLASS] = V1(Class);
      header.e_ident[EI_DATA] = V1(Encoding);
      header.e_ident[EI_VERSION] = V1(EV_CURRENT);
      header.e_ident[EI_OSABI] = V1(OSABI);
      header.e_ident[EI_ABIVERSION] = V1(0);
      header.e_type = V2(ET_REL);
      header.e_machine = V2(machine);
      header.e_version = V4(EV_CURRENT);
      header.e_entry = VANY(static_cast<AddrTy>(0));
      header.e_phoff = VANY(static_cast<AddrTy>(0));
      header.e_shoff = VANY(static_cast<AddrTy>(sizeof(FileHeader)));
      header.e_flags = V4(machine == EM_ARM ? 0x04000000 : 0);
      header.e_ehsize = V2(sizeof(FileHeader));
      header.e_phentsize = V2(0);
      header.e_phnum = V2(0);
      header.e_shentsize = V2(sizeof(SectionHeader));
    }

    void writeHeader(OutputStream* out)
    {
      header.e_shnum = V2(sectionCount);
      header.e_shstrndx = V2(sectionStringTableSectionNumber);
      out->writeChunk(&header, sizeof(FileHeader));
    }
  };

  class SectionWriter {
   public:
    FileWriter& file;
    String name;
    SectionHeader header;
    const size_t* dataSize;
    const uint8_t* const* data;

    SectionWriter(FileWriter& file) : file(file), name(""), dataSize(0), data(0)
    {
      memset(&header, 0, sizeof(SectionHeader));
      file.sectionCount++;
      file.dataOffset += sizeof(SectionHeader);
      size_t nameOffset = file.strings.add(name);
      header.sh_name = V4(nameOffset);
    }

    SectionWriter(FileWriter& file,
                  const char* chname,
                  unsigned type,
                  AddrTy flags,
                  unsigned alignment,
                  AddrTy addr,
                  const uint8_t* const* data,
                  size_t* dataSize,
                  size_t entsize = 0,
                  unsigned link = 0)
        : file(file), name(chname), dataSize(dataSize), data(data)
    {
      if (strcmp(chname, ".shstrtab") == 0) {
        file.sectionStringTableSectionNumber = file.sectionCount;
      }
      file.sectionCount++;
      file.dataOffset += sizeof(SectionHeader);
      size_t nameOffset = file.strings.add(name);

      header.sh_name = V4(nameOffset);
      header.sh_type = V4(type);
      header.sh_flags = VANY(flags);
      header.sh_addr = VANY(addr);
      // header.sh_offset = VANY(static_cast<AddrTy>(bodySectionOffset));
      // header.sh_size = VANY(static_cast<AddrTy>(*dataSize));
      header.sh_link = V4(link);
      header.sh_info = V4(0);
      header.sh_addralign = VANY(static_cast<AddrTy>(alignment));
      header.sh_entsize = VANY(static_cast<AddrTy>(entsize));
    }

    void writeHeader(OutputStream* out)
    {
      if (dataSize) {
        header.sh_offset = VANY(file.dataOffset);
        header.sh_size = VANY(static_cast<AddrTy>(*dataSize));
        file.dataOffset += *dataSize;
      }

      out->writeChunk(&header, sizeof(SectionHeader));
    }

    void writeData(OutputStream* out)
    {
      if (data) {
        out->writeChunk(*data, *dataSize);
      }
    }
  };

  virtual bool writeObject(OutputStream* out,
                           Slice<SymbolInfo> symbols,
                           Slice<const uint8_t> data,
                           unsigned accessFlags,
                           unsigned alignment)
  {
    unsigned sectionFlags;
    const char* sectionName = getSectionName(accessFlags, sectionFlags);

    StringTable symbolStringTable;
    Buffer symbolTable;

    FileWriter file(machine);

    const int bodySectionNumber = 1;
    const int stringTableSectionNumber = 3;

    SectionWriter sections[] = {SectionWriter(file),  // null section
                                SectionWriter(file,
                                              sectionName,
                                              SHT_PROGBITS,
                                              sectionFlags,
                                              alignment,
                                              0,
                                              &data.items,
                                              &data.count),  // body section
                                SectionWriter(file,
                                              ".shstrtab",
                                              SHT_STRTAB,
                                              0,
                                              1,
                                              0,
                                              &file.strings.data,
                                              &file.strings.length),
                                SectionWriter(file,
                                              ".strtab",
                                              SHT_STRTAB,
                                              0,
                                              1,
                                              0,
                                              &symbolStringTable.data,
                                              &symbolStringTable.length),
                                SectionWriter(file,
                                              ".symtab",
                                              SHT_SYMTAB,
                                              0,
                                              8,
                                              0,
                                              &symbolTable.data,
                                              &symbolTable.length,
                                              sizeof(Symbol),
                                              stringTableSectionNumber)};

    // for some reason, string tables require a null first element...
    symbolStringTable.add("");

    for (SymbolInfo* sym = symbols.begin(); sym != symbols.end(); sym++) {
      size_t nameOffset = symbolStringTable.add(sym->name);

      Symbol symbolStruct;
      symbolStruct.st_name = V4(nameOffset);
      symbolStruct.st_value = VANY(static_cast<AddrTy>(sym->addr));
      symbolStruct.st_size = VANY(static_cast<AddrTy>(0));
      symbolStruct.st_info = V1(SYMBOL_INFO(STB_GLOBAL, STT_NOTYPE));
      symbolStruct.st_other = V1(STV_DEFAULT);
      symbolStruct.st_shndx = V2(bodySectionNumber);
      symbolTable.write(&symbolStruct, sizeof(Symbol));
    }

    file.writeHeader(out);

    for (unsigned i = 0; i < file.sectionCount; i++) {
      sections[i].writeHeader(out);
    }

    for (unsigned i = 0; i < file.sectionCount; i++) {
      sections[i].writeData(out);
    }

    return true;
  }
};

ElfPlatform<uint32_t> elfX86Platform(PlatformInfo::x86);
ElfPlatform<uint32_t> elfArmPlatform(PlatformInfo::Arm);
ElfPlatform<uint64_t> elfArm64Platform(PlatformInfo::Arm64);
ElfPlatform<uint64_t> elfX86_64Platform(PlatformInfo::x86_64);

}  // namespace
