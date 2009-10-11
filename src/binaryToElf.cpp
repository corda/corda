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

#include "sys/stat.h"
#include "sys/mman.h"
#include "fcntl.h"
#include "unistd.h"

#include "elf.h"

#ifdef __x86_64__
#  define FileHeader Elf64_Ehdr
#  define SectionHeader Elf64_Shdr
#  define Symbol Elf64_Sym
#  define Class ELFCLASS64
#  define Machine EM_X86_64
#  define SYMBOL_INFO ELF64_ST_INFO
#else // not __x86_64__
#  define FileHeader Elf32_Ehdr
#  define SectionHeader Elf32_Shdr
#  define Symbol Elf32_Sym
#  define Class ELFCLASS32
#  define Machine EM_386
#  define SYMBOL_INFO ELF32_ST_INFO
#endif // not __x86_64__

#define Data ELFDATA2LSB
#define OSABI ELFOSABI_SYSV

namespace {

void
writeObject(FILE* out, const uint8_t* data, unsigned size,
            const char* sectionName, const char* startName,
            const char* endName)
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
  fileHeader.e_machine = Machine;
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
  bodySection.sh_flags = SHF_WRITE | SHF_ALLOC | SHF_EXECINSTR;
  bodySection.sh_addr = 0;
  bodySection.sh_offset = sizeof(FileHeader)
    + (sizeof(SectionHeader) * sectionCount);
  bodySection.sh_size = size;
  bodySection.sh_link = 0;
  bodySection.sh_info = 0;
  bodySection.sh_addralign = sizeof(void*);
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
  symbolTableSection.sh_addralign = sizeof(void*);
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
  endSymbol.st_value = 0;
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

int
main(int argc, const char** argv)
{
  if (argc != 5) {
    fprintf(stderr,
            "usage: %s <input file> <section name> <start symbol name> "
            "<end symbol name>\n",
            argv[0]);
    return -1;
  }

  uint8_t* data = 0;
  unsigned size;
  int fd = open(argv[1], O_RDONLY);
  if (fd != -1) {
    struct stat s;
    int r = fstat(fd, &s);
    if (r != -1) {
      data = static_cast<uint8_t*>
        (mmap(0, s.st_size, PROT_READ, MAP_PRIVATE, fd, 0));
      size = s.st_size;
    }
    close(fd);
  }

  if (data) {
    writeObject(stdout, data, size, argv[2], argv[3], argv[4]);

    munmap(data, size);

    return 0;
  } else {
    perror(argv[0]);
    return -1;
  }
}
