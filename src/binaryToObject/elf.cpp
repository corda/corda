/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "stdint.h"
#include "stdio.h"
#include "string.h"

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

#define ELFOSABI_SYSV 0

#define ET_REL 1

#define EM_386 3
#define EM_X86_64 62

#define SHT_PROGBITS 1
#define SHT_SYMTAB 2
#define SHT_STRTAB 3

#define SHF_WRITE (1 << 0)
#define SHF_ALLOC (1 << 1)
#define SHF_EXECINSTR (1 << 2)

#define STB_GLOBAL 1

#define STT_NOTYPE 0

#define STV_DEFAULT 0

#define ELF64_ST_INFO(bind, type) (((bind) << 4) + ((type) & 0xf))
#define ELF32_ST_INFO(bind, type) ELF64_ST_INFO((bind), (type))

#if (BITS_PER_WORD == 64)
#  define FileHeader Elf64_Ehdr
#  define SectionHeader Elf64_Shdr
#  define Symbol Elf64_Sym
#  define Class ELFCLASS64
#  define SYMBOL_INFO ELF64_ST_INFO
#elif (BITS_PER_WORD == 32)
#  define FileHeader Elf32_Ehdr
#  define SectionHeader Elf32_Shdr
#  define Symbol Elf32_Sym
#  define Class ELFCLASS32
#  define SYMBOL_INFO ELF32_ST_INFO
#else
#  error
#endif

#define Data ELFDATA2LSB
#define OSABI ELFOSABI_SYSV

namespace {

typedef uint16_t Elf64_Half;
typedef uint32_t Elf64_Word;
typedef uint64_t Elf64_Addr;
typedef uint64_t Elf64_Xword;
typedef uint16_t Elf64_Section;
typedef uint64_t Elf64_Off;

struct Elf64_Ehdr {
  unsigned char e_ident[EI_NIDENT];
  Elf64_Half e_type;
  Elf64_Half e_machine;
  Elf64_Word e_version;
  Elf64_Addr e_entry;
  Elf64_Off e_phoff;
  Elf64_Off e_shoff;
  Elf64_Word e_flags;
  Elf64_Half e_ehsize;
  Elf64_Half e_phentsize;
  Elf64_Half e_phnum;
  Elf64_Half e_shentsize;
  Elf64_Half e_shnum;
  Elf64_Half e_shstrndx;
};

struct Elf64_Shdr {
  Elf64_Word sh_name;
  Elf64_Word sh_type;
  Elf64_Xword sh_flags;
  Elf64_Addr sh_addr;
  Elf64_Off sh_offset;
  Elf64_Xword sh_size;
  Elf64_Word sh_link;
  Elf64_Word sh_info;
  Elf64_Xword sh_addralign;
  Elf64_Xword sh_entsize;
};

struct Elf64_Sym {
  Elf64_Word st_name;
  unsigned char st_info;
  unsigned char st_other;
  Elf64_Section st_shndx;
  Elf64_Addr st_value;
  Elf64_Xword st_size;
};

typedef uint16_t Elf32_Half;
typedef uint32_t Elf32_Word;
typedef uint32_t Elf32_Addr;
typedef uint64_t Elf32_Xword;
typedef uint16_t Elf32_Section;
typedef uint32_t Elf32_Off;

struct Elf32_Ehdr {
  unsigned char	e_ident[EI_NIDENT];
  Elf32_Half e_type;
  Elf32_Half e_machine;
  Elf32_Word e_version;
  Elf32_Addr e_entry;
  Elf32_Off e_phoff;
  Elf32_Off e_shoff;
  Elf32_Word e_flags;
  Elf32_Half e_ehsize;
  Elf32_Half e_phentsize;
  Elf32_Half e_phnum;
  Elf32_Half e_shentsize;
  Elf32_Half e_shnum;
  Elf32_Half e_shstrndx;
};

struct Elf32_Shdr {
  Elf32_Word sh_name;
  Elf32_Word sh_type;
  Elf32_Word sh_flags;
  Elf32_Addr sh_addr;
  Elf32_Off sh_offset;
  Elf32_Word sh_size;
  Elf32_Word sh_link;
  Elf32_Word sh_info;
  Elf32_Word sh_addralign;
  Elf32_Word sh_entsize;
};

struct Elf32_Sym {
  Elf32_Word st_name;
  Elf32_Addr st_value;
  Elf32_Word st_size;
  unsigned char st_info;
  unsigned char st_other;
  Elf32_Section st_shndx;
};

void
writeObject(const uint8_t* data, unsigned size, FILE* out,
            const char* startName, const char* endName,
            const char* sectionName, unsigned sectionFlags,
            unsigned alignment, int machine)
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
  fileHeader.e_ident[EI_MAG0] = ELFMAG0;
  fileHeader.e_ident[EI_MAG1] = ELFMAG1;
  fileHeader.e_ident[EI_MAG2] = ELFMAG2;
  fileHeader.e_ident[EI_MAG3] = ELFMAG3;
  fileHeader.e_ident[EI_CLASS] = Class;
  fileHeader.e_ident[EI_DATA] = Data;
  fileHeader.e_ident[EI_VERSION] = EV_CURRENT;
  fileHeader.e_ident[EI_OSABI] = OSABI;
  fileHeader.e_ident[EI_ABIVERSION] = 0;
  fileHeader.e_type = ET_REL;
  fileHeader.e_machine = machine;
  fileHeader.e_version = EV_CURRENT;
  fileHeader.e_entry = 0;
  fileHeader.e_phoff = 0;
  fileHeader.e_shoff = sizeof(FileHeader);
  fileHeader.e_flags = 0;
  fileHeader.e_ehsize = sizeof(FileHeader);
  fileHeader.e_phentsize = 0;
  fileHeader.e_phnum = 0;
  fileHeader.e_shentsize = sizeof(SectionHeader);
  fileHeader.e_shnum = sectionCount;
  fileHeader.e_shstrndx = sectionStringTableSectionNumber;

  SectionHeader nullSection;
  memset(&nullSection, 0, sizeof(SectionHeader));

  SectionHeader bodySection;
  bodySection.sh_name = sectionNameOffset;
  bodySection.sh_type = SHT_PROGBITS;
  bodySection.sh_flags = sectionFlags;
  bodySection.sh_addr = 0;
  bodySection.sh_offset = sizeof(FileHeader)
    + (sizeof(SectionHeader) * sectionCount);
  bodySection.sh_size = size;
  bodySection.sh_link = 0;
  bodySection.sh_info = 0;
  bodySection.sh_addralign = alignment;
  bodySection.sh_entsize = 0;

  SectionHeader sectionStringTableSection;
  sectionStringTableSection.sh_name = sectionStringTableNameOffset;
  sectionStringTableSection.sh_type = SHT_STRTAB;
  sectionStringTableSection.sh_flags = 0;
  sectionStringTableSection.sh_addr = 0;
  sectionStringTableSection.sh_offset
    = bodySection.sh_offset + bodySection.sh_size;
  sectionStringTableSection.sh_size = sectionStringTableLength;
  sectionStringTableSection.sh_link = 0;
  sectionStringTableSection.sh_info = 0;
  sectionStringTableSection.sh_addralign = 1;
  sectionStringTableSection.sh_entsize = 0;

  SectionHeader stringTableSection;
  stringTableSection.sh_name = stringTableNameOffset;
  stringTableSection.sh_type = SHT_STRTAB;
  stringTableSection.sh_flags = 0;
  stringTableSection.sh_addr = 0;
  stringTableSection.sh_offset = sectionStringTableSection.sh_offset
    + sectionStringTableSection.sh_size;
  stringTableSection.sh_size = stringTableLength;
  stringTableSection.sh_link = 0;
  stringTableSection.sh_info = 0;
  stringTableSection.sh_addralign = 1;
  stringTableSection.sh_entsize = 0;

  SectionHeader symbolTableSection;
  symbolTableSection.sh_name = symbolTableNameOffset;
  symbolTableSection.sh_type = SHT_SYMTAB;
  symbolTableSection.sh_flags = 0;
  symbolTableSection.sh_addr = 0;
  symbolTableSection.sh_offset = stringTableSection.sh_offset
    + stringTableSection.sh_size;
  symbolTableSection.sh_size = sizeof(Symbol) * symbolCount;
  symbolTableSection.sh_link = stringTableSectionNumber;
  symbolTableSection.sh_info = 0;
  symbolTableSection.sh_addralign = BITS_PER_WORD / 8;
  symbolTableSection.sh_entsize = sizeof(Symbol);

  Symbol startSymbol;
  startSymbol.st_name = startNameOffset;
  startSymbol.st_value = 0;
  startSymbol.st_size = 0;
  startSymbol.st_info = SYMBOL_INFO(STB_GLOBAL, STT_NOTYPE);
  startSymbol.st_other = STV_DEFAULT;
  startSymbol.st_shndx = bodySectionNumber;

  Symbol endSymbol;
  endSymbol.st_name = endNameOffset;
  endSymbol.st_value = size;
  endSymbol.st_size = 0;
  endSymbol.st_info = SYMBOL_INFO(STB_GLOBAL, STT_NOTYPE);
  endSymbol.st_other = STV_DEFAULT;
  endSymbol.st_shndx = bodySectionNumber;

  fwrite(&fileHeader, 1, sizeof(fileHeader), out);
  fwrite(&nullSection, 1, sizeof(nullSection), out);
  fwrite(&bodySection, 1, sizeof(bodySection), out);
  fwrite(&sectionStringTableSection, 1, sizeof(sectionStringTableSection),
         out);
  fwrite(&stringTableSection, 1, sizeof(stringTableSection), out);
  fwrite(&symbolTableSection, 1, sizeof(symbolTableSection), out);

  fwrite(data, 1, size, out);

  fputc(0, out);
  fwrite(sectionStringTableName, 1, sectionStringTableNameLength, out);
  fwrite(stringTableName, 1, stringTableNameLength, out);
  fwrite(symbolTableName, 1, symbolTableNameLength, out);
  fwrite(sectionName, 1, sectionNameLength, out);

  fputc(0, out);
  fwrite(startName, 1, startNameLength, out);
  fwrite(endName, 1, endNameLength, out);

  fwrite(&startSymbol, 1, sizeof(startSymbol), out);
  fwrite(&endSymbol, 1, sizeof(endSymbol), out);
}

} // namespace

#define MACRO_MAKE_NAME(a, b, c) a##b##c
#define MAKE_NAME(a, b, c) MACRO_MAKE_NAME(a, b, c)

namespace binaryToObject {

bool
MAKE_NAME(writeElf, BITS_PER_WORD, Object)
  (uint8_t* data, unsigned size, FILE* out, const char* startName,
   const char* endName, const char* architecture, unsigned alignment,
   bool writable, bool executable)
{
  int machine;
  if (strcmp(architecture, "x86_64") == 0) {
    machine = EM_X86_64;
  } else if (strcmp(architecture, "i386") == 0) {
    machine = EM_386;
  } else {
    fprintf(stderr, "unsupported architecture: %s\n", architecture);
    return false;
  }

  const char* sectionName;
  unsigned sectionFlags = SHF_ALLOC;
  if (writable and executable) {
    sectionName = ".rwx";
    sectionFlags |= SHF_WRITE | SHF_EXECINSTR;
  } else if (writable) {
    sectionName = ".data";
    sectionFlags |= SHF_WRITE;
  } else if (executable) {
    sectionName = ".text";
    sectionFlags |= SHF_EXECINSTR;
  } else {
    sectionName = ".rodata";
  }

  writeObject(data, size, out, startName, endName, sectionName, sectionFlags,
              alignment, machine);

  return true;
}

} // namespace binaryToObject
