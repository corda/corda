/* Copyright (c) 2009-2011, Avian Contributors

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

#include "tools.h"

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
#define EM_PPC 20

#define SHT_PROGBITS 1
#define SHT_SYMTAB 2
#define SHT_STRTAB 3

#define SHF_WRITE (1 << 0)
#define SHF_ALLOC (1 << 1)
#define SHF_EXECINSTR (1 << 2)

#define STB_GLOBAL 1

#define STT_NOTYPE 0

#define STV_DEFAULT 0

#define SYMBOL_INFO(bind, type) (((bind) << 4) + ((type) & 0xf))

#define OSABI ELFOSABI_SYSV

namespace {

using namespace avian::tools;

template<class AddrTy>
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

template<class AddrTy>
struct Symbol_Ty;

template<>
struct Symbol_Ty<uint64_t> {
  typedef ElfTypes<uint64_t> Elf;

  Elf::Word st_name;
  unsigned char st_info;
  unsigned char st_other;
  Elf::Section st_shndx;
  Elf::Addr st_value;
  Elf::Xword st_size;
};

template<>
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

template<class AddrTy, bool TargetLittleEndian = true>
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

  class ElfObjectWriter : public ObjectWriter {
  public:

    PlatformInfo::Architecture arch;
    OutputStream* out;

    ElfObjectWriter(PlatformInfo::Architecture arch, OutputStream* out):
      arch(arch),
      out(out) {}

    void writeObject(const uint8_t* data, unsigned size,
                     const char* startName, const char* endName,
                     const char* sectionName, unsigned sectionFlags,
                     unsigned alignment, int machine, int encoding)
    {
      const unsigned sectionCount = 5;
      const unsigned symbolCount = 2;

      const unsigned sectionNameLength = strlen(sectionName) + 1;
      const unsigned startNameLength = strlen(startName) + 1;
      const unsigned endNameLength = strlen(endName) + 1;

      const char* const sectionStringTableName = ".shstrtab";
      const char* const stringTableName = ".strtab";
      const char* const symbolTableName = ".symtab";

      const unsigned sectionStringTableNameLength
        = strlen(sectionStringTableName) + 1;
      const unsigned stringTableNameLength = strlen(stringTableName) + 1;
      const unsigned symbolTableNameLength = strlen(symbolTableName) + 1;

      const unsigned nullStringOffset = 0;

      const unsigned sectionStringTableNameOffset = nullStringOffset + 1;
      const unsigned stringTableNameOffset
        = sectionStringTableNameOffset + sectionStringTableNameLength;
      const unsigned symbolTableNameOffset
        = stringTableNameOffset + stringTableNameLength;
      const unsigned sectionNameOffset
        = symbolTableNameOffset + symbolTableNameLength;
      const unsigned sectionStringTableLength
        = sectionNameOffset + sectionNameLength;

      const unsigned startNameOffset = nullStringOffset + 1;
      const unsigned endNameOffset = startNameOffset + startNameLength;
      const unsigned stringTableLength = endNameOffset + endNameLength;

      const unsigned bodySectionNumber = 1;
      const unsigned sectionStringTableSectionNumber = 2;
      const unsigned stringTableSectionNumber = 3;

      FileHeader fileHeader;
      memset(&fileHeader, 0, sizeof(FileHeader));
      fileHeader.e_ident[EI_MAG0] = V1(ELFMAG0);
      fileHeader.e_ident[EI_MAG1] = V1(ELFMAG1);
      fileHeader.e_ident[EI_MAG2] = V1(ELFMAG2);
      fileHeader.e_ident[EI_MAG3] = V1(ELFMAG3);
      fileHeader.e_ident[EI_CLASS] = V1(Class);
      fileHeader.e_ident[EI_DATA] = V1(encoding);
      fileHeader.e_ident[EI_VERSION] = V1(EV_CURRENT);
      fileHeader.e_ident[EI_OSABI] = V1(OSABI);
      fileHeader.e_ident[EI_ABIVERSION] = V1(0);
      fileHeader.e_type = V2(ET_REL);
      fileHeader.e_machine = V2(machine);
      fileHeader.e_version = V4(EV_CURRENT);
      fileHeader.e_entry = VANY(static_cast<AddrTy>(0));
      fileHeader.e_phoff = VANY(static_cast<AddrTy>(0));
      fileHeader.e_shoff = VANY(static_cast<AddrTy>(sizeof(FileHeader)));
      fileHeader.e_flags = V4(machine == EM_ARM ? 0x04000000 : 0);
      fileHeader.e_ehsize = V2(sizeof(FileHeader));
      fileHeader.e_phentsize = V2(0);
      fileHeader.e_phnum = V2(0);
      fileHeader.e_shentsize = V2(sizeof(SectionHeader));
      fileHeader.e_shnum = V2(sectionCount);
      fileHeader.e_shstrndx = V2(sectionStringTableSectionNumber);

      SectionHeader nullSection;
      memset(&nullSection, 0, sizeof(SectionHeader));

      SectionHeader bodySection;
      bodySection.sh_name = V4(sectionNameOffset);
      bodySection.sh_type = V4(SHT_PROGBITS);
      bodySection.sh_flags = VANY(static_cast<AddrTy>(sectionFlags));
      bodySection.sh_addr = VANY(static_cast<AddrTy>(0));
      unsigned bodySectionOffset
        = sizeof(FileHeader) + (sizeof(SectionHeader) * sectionCount);
      bodySection.sh_offset = VANY(static_cast<AddrTy>(bodySectionOffset));
      unsigned bodySectionSize = size;
      bodySection.sh_size = VANY(static_cast<AddrTy>(bodySectionSize));
      bodySection.sh_link = V4(0);
      bodySection.sh_info = V4(0);
      bodySection.sh_addralign = VANY(static_cast<AddrTy>(alignment));
      bodySection.sh_entsize = VANY(static_cast<AddrTy>(0));

      SectionHeader sectionStringTableSection;
      sectionStringTableSection.sh_name = V4(sectionStringTableNameOffset);
      sectionStringTableSection.sh_type = V4(SHT_STRTAB);
      sectionStringTableSection.sh_flags = VANY(static_cast<AddrTy>(0));
      sectionStringTableSection.sh_addr = VANY(static_cast<AddrTy>(0));
      unsigned sectionStringTableSectionOffset
        = bodySectionOffset + bodySectionSize;
      sectionStringTableSection.sh_offset = VANY(static_cast<AddrTy>(sectionStringTableSectionOffset));
      unsigned sectionStringTableSectionSize = sectionStringTableLength;
      sectionStringTableSection.sh_size = VANY(static_cast<AddrTy>(sectionStringTableSectionSize));
      sectionStringTableSection.sh_link = V4(0);
      sectionStringTableSection.sh_info = V4(0);
      sectionStringTableSection.sh_addralign = VANY(static_cast<AddrTy>(1));
      sectionStringTableSection.sh_entsize = VANY(static_cast<AddrTy>(0));

      SectionHeader stringTableSection;
      stringTableSection.sh_name = V4(stringTableNameOffset);
      stringTableSection.sh_type = V4(SHT_STRTAB);
      stringTableSection.sh_flags = VANY(static_cast<AddrTy>(0));
      stringTableSection.sh_addr = VANY(static_cast<AddrTy>(0));
      unsigned stringTableSectionOffset
        = sectionStringTableSectionOffset + sectionStringTableSectionSize;
      stringTableSection.sh_offset  = VANY(static_cast<AddrTy>(stringTableSectionOffset));
      unsigned stringTableSectionSize = stringTableLength;
      stringTableSection.sh_size = VANY(static_cast<AddrTy>(stringTableSectionSize));
      stringTableSection.sh_link = V4(0);
      stringTableSection.sh_info = V4(0);
      stringTableSection.sh_addralign = VANY(static_cast<AddrTy>(1));
      stringTableSection.sh_entsize = VANY(static_cast<AddrTy>(0));

      SectionHeader symbolTableSection;
      symbolTableSection.sh_name = V4(symbolTableNameOffset);
      symbolTableSection.sh_type = V4(SHT_SYMTAB);
      symbolTableSection.sh_flags = VANY(static_cast<AddrTy>(0));
      symbolTableSection.sh_addr = VANY(static_cast<AddrTy>(0));
      unsigned symbolTableSectionOffset
        = stringTableSectionOffset + stringTableSectionSize;
      symbolTableSection.sh_offset = VANY(static_cast<AddrTy>(symbolTableSectionOffset));
      unsigned symbolTableSectionSize = sizeof(Symbol) * symbolCount;
      symbolTableSection.sh_size = VANY(static_cast<AddrTy>(symbolTableSectionSize));
      symbolTableSection.sh_link = V4(stringTableSectionNumber);
      symbolTableSection.sh_info = V4(0);
      symbolTableSection.sh_addralign = VANY(static_cast<AddrTy>(Elf::BytesPerWord));
      symbolTableSection.sh_entsize = VANY(static_cast<AddrTy>(sizeof(Symbol)));

      Symbol startSymbol;
      startSymbol.st_name = V4(startNameOffset);
      startSymbol.st_value = VANY(static_cast<AddrTy>(0));
      startSymbol.st_size = VANY(static_cast<AddrTy>(0));
      startSymbol.st_info = V1(SYMBOL_INFO(STB_GLOBAL, STT_NOTYPE));
      startSymbol.st_other = V1(STV_DEFAULT);
      startSymbol.st_shndx = V2(bodySectionNumber);

      Symbol endSymbol;
      endSymbol.st_name = V4(endNameOffset);
      endSymbol.st_value = VANY(static_cast<AddrTy>(size));
      endSymbol.st_size = VANY(static_cast<AddrTy>(0));
      endSymbol.st_info = V1(SYMBOL_INFO(STB_GLOBAL, STT_NOTYPE));
      endSymbol.st_other = V1(STV_DEFAULT);
      endSymbol.st_shndx = V2(bodySectionNumber);

      out->writeChunk(&fileHeader, sizeof(fileHeader));
      out->writeChunk(&nullSection, sizeof(nullSection));
      out->writeChunk(&bodySection, sizeof(bodySection));
      out->writeChunk(&sectionStringTableSection, sizeof(sectionStringTableSection));
      out->writeChunk(&stringTableSection, sizeof(stringTableSection));
      out->writeChunk(&symbolTableSection, sizeof(symbolTableSection));

      out->writeChunk(data, size);

      out->write(0);
      out->writeChunk(sectionStringTableName, sectionStringTableNameLength);
      out->writeChunk(stringTableName, stringTableNameLength);
      out->writeChunk(symbolTableName, symbolTableNameLength);
      out->writeChunk(sectionName, sectionNameLength);

      out->write(0);
      out->writeChunk(startName, startNameLength);
      out->writeChunk(endName, endNameLength);

      out->writeChunk(&startSymbol, sizeof(startSymbol));
      out->writeChunk(&endSymbol, sizeof(endSymbol));
    }

    virtual bool write(uint8_t* data, size_t size,
                       const char* startName, const char* endName,
                       unsigned alignment, unsigned accessFlags)
    {
      int machine;
      int encoding;
      if (arch == PlatformInfo::x86_64) {
        machine = EM_X86_64;
        encoding = ELFDATA2LSB;
      } else if (arch == PlatformInfo::x86) {
        machine = EM_386;
        encoding = ELFDATA2LSB;
      } else if (arch == PlatformInfo::Arm) {
        machine = EM_ARM;
        encoding = ELFDATA2LSB;
      } else if (arch == PlatformInfo::PowerPC) {
        machine = EM_PPC;
        encoding = ELFDATA2MSB;
      } else {
        fprintf(stderr, "unsupported architecture: %s\n", arch);
        return false;
      }

      const char* sectionName;
      unsigned sectionFlags = SHF_ALLOC;
      if (accessFlags & Writable) {
        if (accessFlags & Executable) {
          sectionName = ".rwx";
          sectionFlags |= SHF_WRITE | SHF_EXECINSTR;
        } else {
          sectionName = ".data";
          sectionFlags |= SHF_WRITE;
        }
      } else if (accessFlags & Executable) {
        sectionName = ".text";
        sectionFlags |= SHF_EXECINSTR;
      } else {
        sectionName = ".rodata";
      }

      writeObject(data, size, startName, endName, sectionName, sectionFlags,
                  alignment, machine, encoding);

      return true;
    }

    virtual void dispose() {
      delete this;
    }
  };

  ElfPlatform(PlatformInfo::Architecture arch):
    Platform(PlatformInfo(PlatformInfo::Linux, arch)) {}

  virtual ObjectWriter* makeObjectWriter(OutputStream* out) {
    return new ElfObjectWriter(info.arch, out);
  }
};

ElfPlatform<uint32_t> elfx86Platform(PlatformInfo::x86);
ElfPlatform<uint32_t> elfArmPlatform(PlatformInfo::Arm);
ElfPlatform<uint32_t, false> elfPowerPCPlatform(PlatformInfo::PowerPC);
ElfPlatform<uint64_t> elfx86_64Platform(PlatformInfo::x86_64);


} // namespace
