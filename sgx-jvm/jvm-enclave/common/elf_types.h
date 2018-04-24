#ifndef __ELF_TYPES_H__
#define __ELF_TYPES_H__

typedef uint64_t        Elf64_Addr;
typedef uint16_t        Elf64_Quarter;
typedef uint32_t        Elf64_Half;
typedef uint64_t        Elf64_Off;
typedef uint32_t        Elf64_Word;
typedef uint64_t        Elf64_Xword;

#define ELFMAG0         0x7f                    // e_ident[EI_MAG0]
#define ELFMAG1         'E'                     // e_ident[EI_MAG1]
#define ELFMAG2         'L'                     // e_ident[EI_MAG2]
#define ELFMAG3         'F'                     // e_ident[EI_MAG3]

#define EI_NIDENT       16                      // Size of e_ident array
#define EI_CLASS        4                       // Class of machine
#define ELFCLASSNONE    0                       // Unknown class
#define ELFCLASS32      1                       // 32-bit architecture
#define ELFCLASS64      2                       // 64-bit architecture

typedef struct {
    unsigned char       e_ident[EI_NIDENT];     // File identification
    Elf64_Quarter       e_type;                 // File type
    Elf64_Quarter       e_machine;              // Machine architecture
    Elf64_Word          e_version;              // ELF format version
    Elf64_Addr          e_entry;                // Entry point
    Elf64_Off           e_phoff;                // Program header file offset
    Elf64_Off           e_shoff;                // Section header file offset
    Elf64_Word          e_flags;                // Architecture-specific flags
    Elf64_Quarter       e_ehsize;               // Size of ELF header in bytes
    Elf64_Quarter       e_phentsize;            // Size of program header entry
    Elf64_Quarter       e_phnum;                // Number of program header entries
    Elf64_Quarter       e_shentsize;            // Size of section header entry
    Elf64_Quarter       e_shnum;                // Number of section header entries
    Elf64_Quarter       e_shstrndx;             // Section name strings section
} Elf64_Ehdr;

typedef struct {
    Elf64_Word          sh_name;                // Section name (index into section header string table)
    Elf64_Word          sh_type;                // Section type
    Elf64_Xword         sh_flags;               // Section flags
    Elf64_Addr          sh_addr;                // Address in memory image
    Elf64_Off           sh_offset;              // Offset in file
    Elf64_Xword         sh_size;                // Size in bytes
    Elf64_Word          sh_link;                // Index of a related section
    Elf64_Word          sh_info;                // Depends on section type
    Elf64_Xword         sh_addralign;           // Alignment in bytes
    Elf64_Xword         sh_entsize;             // Size of each entry in section
} Elf64_Shdr;

typedef struct {
    Elf64_Half          namesz;                 // Length of note name string
    Elf64_Half          descsz;                 // Length of note description string
    Elf64_Half          type;                   // Note type
} Elf64_Note;

#endif /* __ELF_TYPES_H__ */
