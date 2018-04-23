/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */


/*
 * This file is part of trusted loader for tRTS.
 */

#include "elf_parser.h"
#include "rts.h"
#include "util.h"
#include "elf_util.h"
#include "global_data.h"
#include "../trts_emodpr.h"
#include "trts_inst.h"

static int elf_tls_aligned_virtual_size(const void *enclave_base,
                            size_t *aligned_virtual_size);

static ElfW(Phdr)* get_phdr(const ElfW(Ehdr)* ehdr)
{
    if (ehdr == NULL)
        return NULL;  /* Invalid image. */

    /* Check the ElfW Magic number. */
    if ((ehdr->e_ident[EI_MAG0] != ELFMAG0) ||
        (ehdr->e_ident[EI_MAG1] != ELFMAG1) ||
        (ehdr->e_ident[EI_MAG2] != ELFMAG2) ||
        (ehdr->e_ident[EI_MAG3] != ELFMAG3))
        return NULL;

    /* Enclave image should be a shared object file. */
    if (ehdr->e_type != ET_DYN)
        return NULL;

    return GET_PTR(ElfW(Phdr), ehdr, ehdr->e_phoff);
}

static ElfW(Sym)* get_sym(ElfW(Sym)* symtab, size_t idx)
{
    if(STB_WEAK == ELFW(ST_BIND)(symtab[idx].st_info)
            && 0 == symtab[idx].st_value)
    {
        return NULL;
    }

    return &symtab[idx];
}

#ifdef __x86_64__
/* Relocation for x64 (with addend) */
static int do_relocs(const ElfW(Addr) enclave_base,
        ElfW(Addr) rela_offset,
        ElfW(Addr) sym_offset,
        size_t nr_relocs)
{
    ElfW(Rela)* rela = GET_PTR(ElfW(Rela), enclave_base, rela_offset);
    ElfW(Sym)*  symtab = GET_PTR(ElfW(Sym), enclave_base, sym_offset);
    ElfW(Sym)*  sym;
    size_t      i;
    size_t aligned_virtual_size = 0;

    for (i = 0; i < nr_relocs; ++i, ++rela)
    {
        ElfW(Addr)* reloc_addr = GET_PTR(ElfW(Addr), enclave_base, rela->r_offset);

        switch (ELF64_R_TYPE(rela->r_info))
        {
            case R_X86_64_RELATIVE:
                *reloc_addr = enclave_base + (uintptr_t)rela->r_addend;
                break;

            case R_X86_64_GLOB_DAT:
            case R_X86_64_JUMP_SLOT:
            case R_X86_64_64:
                sym = get_sym(symtab, ELF64_R_SYM(rela->r_info));
                if(!sym)
                    break;
                *reloc_addr = enclave_base + sym->st_value + (uintptr_t)rela->r_addend;
                break;

            case R_X86_64_DTPMOD64:
                *reloc_addr = 1;
                break;
 
            case R_X86_64_DTPOFF64:
                sym = get_sym(symtab, ELF64_R_SYM(rela->r_info));
                if(!sym)
                    break;
                *reloc_addr = sym->st_value + (uintptr_t)rela->r_addend;
                break;

            case R_X86_64_TPOFF64:
                sym = get_sym(symtab, ELF64_R_SYM(rela->r_info));
                if(!sym)
                    break;

                if ((0 == elf_tls_aligned_virtual_size((void *)enclave_base, &aligned_virtual_size)) && (aligned_virtual_size))
                {
                    *reloc_addr = sym->st_value + (uintptr_t)rela->r_addend - aligned_virtual_size;
                    break;
                }
                else
                    return -1;

            case R_X86_64_NONE:
                break;

            default:    /* unsupported relocs */
                return -1;
        }
    }

    return 0;
}

#elif defined(__i386__)
/* Relocation for x86 (without addend) */
static int do_relocs(const ElfW(Addr) enclave_base,
        ElfW(Addr) rel_offset,
        ElfW(Addr) sym_offset,
        size_t nr_relocs)
{
    ElfW(Rel)*  rel = GET_PTR(ElfW(Rel), enclave_base, rel_offset);
    ElfW(Sym)*  symtab = GET_PTR(ElfW(Sym), enclave_base, sym_offset);
    ElfW(Sym)*  sym = NULL;
    size_t      i;
    size_t aligned_virtual_size = 0;

    for (i = 0; i < nr_relocs; ++i, ++rel)
    {
        ElfW(Addr)* reloc_addr = GET_PTR(ElfW(Addr), enclave_base, rel->r_offset);

        if(R_386_RELATIVE == ELF32_R_TYPE(rel->r_info))
        {
            *reloc_addr += enclave_base; /* B+A */
            continue;
        }
        sym = get_sym(symtab, ELF32_R_SYM(rel->r_info));
        if(!sym)  /* when the weak symbol is not implemented, sym is NULL */
            continue;
        switch (ELF32_R_TYPE(rel->r_info))
        {
            case R_386_GLOB_DAT:
            case R_386_JMP_SLOT:    /* S */
                *reloc_addr = enclave_base + sym->st_value;
                break;

            case R_386_32:          /* S+A */
                *reloc_addr += enclave_base + sym->st_value;
                break;

            case R_386_PC32:        /* S+A-P */
                *reloc_addr += (enclave_base + sym->st_value - (ElfW(Addr))reloc_addr);
                break;

            case R_386_NONE:
                break;

            case R_386_TLS_DTPMOD32:
                *reloc_addr = 1;
                break;
 
            case R_386_TLS_DTPOFF32:
                *reloc_addr = sym->st_value;
                break;

            case R_386_TLS_TPOFF:
                if ((0 == elf_tls_aligned_virtual_size((void *)enclave_base, &aligned_virtual_size)) && (aligned_virtual_size))
                {
                    *reloc_addr += sym->st_value - aligned_virtual_size;
                    break;
                }
                else
                    return -1;

            case R_386_TLS_TPOFF32:
                if ((0 == elf_tls_aligned_virtual_size((void *)enclave_base, &aligned_virtual_size)) && (aligned_virtual_size))
                {
                    *reloc_addr += aligned_virtual_size - sym->st_value;
                    break;
                }
                else
                    return -1;

            default:    /* unsupported relocs */
                return -1;
        }
    }

    return 0;
}
#endif

#define DO_REL(base_addr, rel_offset, sym_offset, total_sz, rel_entry_sz)   \
do {                                                        \
    if (rel_offset)                                         \
    {                                                       \
        size_t n;                                           \
        if (rel_entry_sz == 0)                              \
            return -1;                                      \
        n = total_sz/rel_entry_sz;                          \
        if (do_relocs((ElfW(Addr))base_addr, rel_offset, sym_offset, n)) \
            return -1;                                      \
    }                                                       \
} while (0)

/* By default all symbol is linked as global symbol by link editor. When call global symbol,
 * we first call .plt entry. It should have problems if the call goloal symbol when relocation
 * is not done.
 * Declare relocate_enclave as .hidden is to make it local symbol.
 * Since this function is called before relocation is done, we must make
 * it local symbol, so the code is like "fce3:	e8 98 12 00 00    call   10f80 <relocate_enclave>"
 * 0x9812=0x10f80-0xfce8
 */
__attribute__ ((visibility ("hidden")))
int relocate_enclave(void* enclave_base)
{
    ElfW(Half) phnum = 0;
    ElfW(Ehdr) *ehdr = (ElfW(Ehdr)*)enclave_base;
    ElfW(Phdr) *phdr = get_phdr(ehdr);

    if (phdr == NULL)
        return -1;  /* Invalid image. */

    for (; phnum < ehdr->e_phnum; phnum++, phdr++)
    {
        /* Search for dynamic segment */
        if (phdr->p_type == PT_DYNAMIC)
        {
            size_t      count;
            size_t      n_dyn = phdr->p_filesz/sizeof(ElfW(Dyn));
            ElfW(Dyn)   *dyn = GET_PTR(ElfW(Dyn), ehdr, phdr->p_paddr);

            ElfW(Addr)   sym_offset = 0;
            ElfW(Addr)   rel_offset = 0;
            ElfW(Addr)   plt_offset = 0;

            size_t   rel_total_sz = 0;
            size_t   rel_entry_sz = 0;
            size_t   plt_total_sz = 0;

            for (count = 0; count < n_dyn; count++, dyn++)
            {
                if (dyn->d_tag == DT_NULL)  /* End */
                    break;

                switch (dyn->d_tag)
                {
                    case DT_SYMTAB: /* symbol table */
                        sym_offset = dyn->d_un.d_ptr;
                        break;

                    case RTS_DT_REL:/* Rel (x86) or Rela (x64) relocs */
                        rel_offset = dyn->d_un.d_ptr;
                        break;

                    case RTS_DT_RELSZ:
                        rel_total_sz = dyn->d_un.d_val;
                        break;

                    case RTS_DT_RELENT:
                        rel_entry_sz = dyn->d_un.d_val;
                        break;

                    case DT_JMPREL: /* PLT relocs */
                        plt_offset = dyn->d_un.d_ptr;
                        break;

                    case DT_PLTRELSZ:
                        plt_total_sz = dyn->d_un.d_val;
                        break;
                }
            }

            DO_REL(enclave_base, rel_offset, sym_offset, rel_total_sz, rel_entry_sz);
            DO_REL(enclave_base, plt_offset, sym_offset, plt_total_sz, rel_entry_sz);
        }
    }

    return 0;
}

int elf_tls_info(const void* enclave_base,
        uintptr_t *tls_addr, size_t *tdata_size)
{
    ElfW(Half) phnum = 0;
    const ElfW(Ehdr) *ehdr = (const ElfW(Ehdr)*)enclave_base;
    ElfW(Phdr) *phdr = get_phdr(ehdr);

    if (!tls_addr || !tdata_size)
        return -1;

    if (phdr == NULL)
        return -1;  /* Invalid image. */

    /* Search for TLS segment */
    *tls_addr = 0;
    *tdata_size = 0;
    for (; phnum < ehdr->e_phnum; phnum++, phdr++)
    {
        if (phdr->p_type == PT_TLS)
        {
            /* tls_addr here is got from the program header, the address
             * need to be added by the enclave base.
             */
            *tls_addr = (size_t)enclave_base + phdr->p_vaddr;
            *tdata_size = phdr->p_filesz;
            break;
        }
    }

    return 0;
}

static int elf_tls_aligned_virtual_size(const void *enclave_base,
                                        size_t *aligned_virtual_size)
{
    ElfW(Half) phnum = 0;
    const ElfW(Ehdr) *ehdr = (const ElfW(Ehdr)*)enclave_base;
    ElfW(Phdr) *phdr = get_phdr(ehdr);
    size_t virtual_size =0, align = 0;

    if (phdr == NULL)
        return -1;

    if (!aligned_virtual_size)
        return -1;

    *aligned_virtual_size = 0;
    for (; phnum < ehdr->e_phnum; phnum++, phdr++)
    {
        if (phdr->p_type == PT_TLS)
        {
            virtual_size = phdr->p_memsz;
            align = phdr->p_align;

            /* p_align == 0 or p_align == 1 means no alignment is required */
            if (align == 0 || align == 1)
                *aligned_virtual_size = virtual_size;
            else
                *aligned_virtual_size = (virtual_size + align - 1) & (~(align - 1));

            break;
        }
    }

    return 0;
}

int elf_get_init_array(const void* enclave_base,
        uintptr_t *init_array_addr, size_t *init_array_size)
{
    ElfW(Half) phnum = 0;
    const ElfW(Ehdr) *ehdr = (const ElfW(Ehdr)*)enclave_base;
    ElfW(Phdr) *phdr = get_phdr(ehdr);

    if (!init_array_addr || !init_array_size)
        return -1;

    if (phdr == NULL)
        return -1;  /* Invalid image. */

    *init_array_addr = 0;
    *init_array_size = 0;

    /* Search for Dynamic segment */
    for (; phnum < ehdr->e_phnum; phnum++, phdr++)
    {
        if (phdr->p_type == PT_DYNAMIC)
        {
            size_t      count;
            size_t      n_dyn = phdr->p_filesz/sizeof(ElfW(Dyn));
            ElfW(Dyn)   *dyn = GET_PTR(ElfW(Dyn), ehdr, phdr->p_paddr);
            
            for (count = 0; count < n_dyn; count++, dyn++)
            {
                switch (dyn->d_tag)
                {
                    case DT_INIT_ARRAY:
                        *init_array_addr = dyn->d_un.d_ptr;
                        break;
                    case DT_INIT_ARRAYSZ:
                        *init_array_size = dyn->d_un.d_val;
                        break;
                }
            }
        }
    }

    return 0;
}

int elf_get_uninit_array(const void* enclave_base,
        uintptr_t *uninit_array_addr, size_t *uninit_array_size)
{
    ElfW(Half) phnum = 0;
    const ElfW(Ehdr) *ehdr = (const ElfW(Ehdr)*)enclave_base;
    ElfW(Phdr) *phdr = get_phdr(ehdr);

    if (!uninit_array_addr || !uninit_array_size)
        return -1;

    if (phdr == NULL)
        return -1;  /* Invalid image. */

    *uninit_array_addr = 0;
    *uninit_array_size = 0;

    /* Search for Dynamic segment */
    for (; phnum < ehdr->e_phnum; phnum++, phdr++)
    {
        if (phdr->p_type == PT_DYNAMIC)
        {
            size_t      count;
            size_t      n_dyn = phdr->p_filesz/sizeof(ElfW(Dyn));
            ElfW(Dyn)   *dyn = GET_PTR(ElfW(Dyn), ehdr, phdr->p_paddr);

            for (count = 0; count < n_dyn; count++, dyn++)
            {
                switch (dyn->d_tag)
                {
                    case DT_FINI_ARRAY:
                        *uninit_array_addr = dyn->d_un.d_ptr;
                        break;
                    case DT_FINI_ARRAYSZ:
                        *uninit_array_size = dyn->d_un.d_val;
                        break;
                }
            }
        }
    }

    return 0;
}

static int has_text_relo(const ElfW(Ehdr) *ehdr, const ElfW(Phdr) *phdr, ElfW(Half) phnum)
{
    ElfW(Half) phi = 0;
    int text_relo = 0;

    for (; phi < phnum; phi++, phdr++)
    {
        if (phdr->p_type == PT_DYNAMIC)
        {
            size_t count;
            size_t n_dyn = phdr->p_filesz/sizeof(ElfW(Dyn));
            ElfW(Dyn) *dyn = GET_PTR(ElfW(Dyn), ehdr, phdr->p_paddr);

            for (count = 0; count < n_dyn; count++, dyn++)
            {
                if (dyn->d_tag == DT_NULL)
                    break;

                if (dyn->d_tag == DT_TEXTREL)
                {
                    text_relo = 1;
                    break;
                }
            }
            break;
        }
    }
    return text_relo;
}

sgx_status_t change_protection(void *enclave_base)
{
    ElfW(Half) phnum = 0;
    const ElfW(Ehdr) *ehdr = (const ElfW(Ehdr)*)enclave_base;
    const ElfW(Phdr) *phdr = get_phdr(ehdr);
    uint64_t perms;
    sgx_status_t status = SGX_ERROR_UNEXPECTED;

    if (phdr == NULL)
        return status;

    int text_relocation = has_text_relo(ehdr, phdr, ehdr->e_phnum);

    for (; phnum < ehdr->e_phnum; phnum++, phdr++)
    {
        if (text_relocation && (phdr->p_type == PT_LOAD) && ((phdr->p_flags & PF_W) == 0))
        {
            perms = 0;
            size_t start = (size_t)enclave_base + (phdr->p_vaddr & (size_t)(~(SE_PAGE_SIZE-1)));
            size_t end = (size_t)enclave_base + ((phdr->p_vaddr + phdr->p_memsz + SE_PAGE_SIZE - 1) & (size_t)(~(SE_PAGE_SIZE-1)));

            if (phdr->p_flags & PF_R)
                perms |= SI_FLAG_R;
            if (phdr->p_flags & PF_X)
                perms |= SI_FLAG_X;

            if((status = sgx_trts_mprotect(start, end - start, perms)) != SGX_SUCCESS)
                return status;
        }

        if (phdr->p_type == PT_GNU_RELRO)
        {
            size_t start = (size_t)enclave_base + (phdr->p_vaddr & (size_t)(~(SE_PAGE_SIZE-1)));
            size_t end = (size_t)enclave_base + ((phdr->p_vaddr + phdr->p_memsz + SE_PAGE_SIZE - 1) & (size_t)(~(SE_PAGE_SIZE-1)));
            if ((start != end) &&
                    (status = sgx_trts_mprotect(start, end - start, SI_FLAG_R)) != SGX_SUCCESS)
                return status;
        }
    }

    return SGX_SUCCESS;
}

/* vim: set ts=4 sw=4 et cin: */
