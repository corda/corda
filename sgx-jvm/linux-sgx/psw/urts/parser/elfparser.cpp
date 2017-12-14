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
#include "elfparser.h"
#include "cpputil.h"
#include "se_trace.h"
#include "se_memcpy.h"
#include "global_data.h"

namespace {
/** the callback function to filter a section.
 *
 * @shstrtab:  the section header string table
 * @shdr:      the current section header to be examined
 * @user_data: user supplied data for the callback
 *
 * @return: true if current section header is what we are looking for.
 */
typedef bool (* section_filter_f)(const char* shstrtab,
                                  const ElfW(Shdr)* shdr,
                                  const void* user_data);

bool compare_section_name(const char* shstrtab,
                          const ElfW(Shdr)* shdr,
                          const void* user_data)
{
    // `shstrtab + shdr->sh_name' is the section name.
    return (!strcmp(shstrtab + shdr->sh_name, (const char*)user_data));
}

bool compare_section_addr(const char* shstrtab,
                          const ElfW(Shdr)* shdr,
                          const void* user_data)
{
    UNUSED(shstrtab);
    return (shdr->sh_addr == (ElfW(Addr))(size_t)user_data);
}

const ElfW(Shdr)* get_section(const ElfW(Ehdr) *elf_hdr,
                              section_filter_f f,
                              const void* user_data)
{
    const ElfW(Shdr) *shdr = GET_PTR(ElfW(Shdr), elf_hdr, elf_hdr->e_shoff);
    assert(sizeof(ElfW(Shdr)) == elf_hdr->e_shentsize);

    // section header string table
    const char *shstrtab = GET_PTR(char, elf_hdr, shdr[elf_hdr->e_shstrndx].sh_offset);

    for (unsigned idx = 0; idx < elf_hdr->e_shnum; ++idx, ++shdr)
    {
        SE_TRACE(SE_TRACE_DEBUG, "section [%u] %s: sh_addr = %x, sh_size = %x, sh_offset = %x, sh_name = %x\n",
                 idx, shstrtab + shdr->sh_name, shdr->sh_addr, shdr->sh_size, shdr->sh_offset, shdr->sh_name);
        if (f(shstrtab, shdr, user_data))
            return shdr;
    }

    return NULL;
}

const ElfW(Shdr)* get_section_by_name(const ElfW(Ehdr) *elf_hdr, const char *name)
{
    return get_section(elf_hdr, compare_section_name, name);
}

const ElfW(Shdr)* get_section_by_addr(const ElfW(Ehdr) *elf_hdr, ElfW(Addr) start_addr)
{
    return get_section(elf_hdr, compare_section_addr, (const void*)(size_t)start_addr);
}

template <typename T>
const T* get_section_raw_data(const ElfW(Ehdr) *elf_hdr, ElfW(Addr) start_addr)
{
    const ElfW(Shdr)* shdr = get_section_by_addr(elf_hdr, start_addr);
    if (shdr == NULL)
        return NULL;

    return GET_PTR(T, elf_hdr, shdr->sh_offset);
}

bool validate_elf_header(const ElfW(Ehdr) *elf_hdr)
{
    // validate magic number
    if (memcmp(&elf_hdr->e_ident, ELFMAG, SELFMAG))
        return false;

#if RTS_SYSTEM_WORDSIZE == 64
    if (ELFCLASS64 != elf_hdr->e_ident[EI_CLASS])
        return false;
#else
    if (ELFCLASS32 != elf_hdr->e_ident[EI_CLASS])
        return false;
#endif

    if (ELFDATA2LSB!= elf_hdr->e_ident[EI_DATA])
        return false;

    if (EV_CURRENT != elf_hdr->e_ident[EI_VERSION])
        return false;

    if (ET_DYN != elf_hdr->e_type)
        return false;

    if (sizeof(ElfW(Phdr)) != elf_hdr->e_phentsize)
        return false;

    return true;
}

bool parse_dyn(const ElfW(Ehdr) *elf_hdr, ElfW(Dyn)* dyn_info)
{
    const ElfW(Phdr) *prg_hdr = GET_PTR(ElfW(Phdr), elf_hdr, elf_hdr->e_phoff);
    bool has_dyn = false;

    for (unsigned idx = 0; idx < elf_hdr->e_phnum; ++idx, ++prg_hdr)
    {
        if (PT_DYNAMIC == prg_hdr->p_type)
        {
            const ElfW(Dyn) *dyn_entry = GET_PTR(ElfW(Dyn), elf_hdr, prg_hdr->p_offset);

            // parse dynamic segment
            // An entry with a DT_NULL tag marks the end.
            while (dyn_entry->d_tag != DT_NULL)
            {
                SE_TRACE(SE_TRACE_DEBUG, "dynamic tag = %x, ptr = %x\n", dyn_entry->d_tag, dyn_entry->d_un.d_ptr);

                if (dyn_entry->d_tag < DT_NUM)
                {
                    memcpy_s(&dyn_info[dyn_entry->d_tag], sizeof(ElfW(Dyn)), dyn_entry, sizeof(ElfW(Dyn)));
                }
                else if (dyn_entry->d_tag > DT_ADDRRNGLO && dyn_entry->d_tag <= DT_ADDRRNGHI)
                {
                    memcpy_s(&dyn_info[DT_ADDRTAGIDX(dyn_entry->d_tag) + DT_NUM], sizeof(ElfW(Dyn)), dyn_entry, sizeof(ElfW(Dyn)));
                }

                dyn_entry++;
                has_dyn = true;
            }

            return has_dyn;
        }
    }

    return false;
}

/** Check whether there are undefined symbols and save the address
 * for a few reserved symbols.
 *
 * ELF format defined two symbol tables, `.symtab' and `.dynsym'.
 *
 * `.symtab' is non-allocable, and might be stripped.
 * `.dynsym' is allocable, and only contains global symbols.
 *
 * We only need to search `.dynsym' for undefined symbols.
 */
bool check_symbol_table(const ElfW(Ehdr) *elf_hdr, const ElfW(Dyn) *dyn_info,
                        map<string, uint64_t>& sym_table)
{
    const ElfW(Shdr) *sh_symtab = get_section_by_addr(elf_hdr, dyn_info[DT_SYMTAB].d_un.d_ptr);

    if (sh_symtab == NULL)
    {
        // We must at least have "enclave_entry"
        SE_TRACE(SE_TRACE_WARNING, "There is no .dynsym section");
        return false;
    }

    if (sh_symtab->sh_entsize == 0)
    {
        SE_TRACE(SE_TRACE_WARNING, "In section .dynsym, sh_entsize is 0.");
        return false;
    }

    const ElfW(Sym) *symtab = GET_PTR(ElfW(Sym), elf_hdr, sh_symtab->sh_offset);
    uint32_t sym_num = (uint32_t)(sh_symtab->sh_size/sh_symtab->sh_entsize);
    const char *strtab = get_section_raw_data<char>(elf_hdr, dyn_info[DT_STRTAB].d_un.d_ptr);

    // We only store "enclave_entry", "g_global_data_sim" and "g_peak_heap_used".
    // To export new symbols, add them here.
    //
    // "g_global_data_sim" is needed so that we can check that whether
    // an simulated enclave is given when running an HW loader.
    const char* str[] = { "enclave_entry", "g_global_data_sim", "g_peak_heap_used", "g_global_data" };

    // The first entry is reserved, and must be all zeros
    for (uint32_t idx = 1; idx < sym_num; ++idx)
    {
        // st_name == 0 indicates the symble table entry has no name.
        if (symtab[idx].st_name == 0) continue;

        const char* sym = strtab + symtab[idx].st_name;
        if (sym == NULL)
        {
            SE_TRACE(SE_TRACE_WARNING, "Malformed enclave with NULL symbol name\n");
            return false;
        }

        if (SHN_UNDEF == symtab[idx].st_shndx
                && STB_WEAK != ELFW(ST_BIND)(symtab[idx].st_info))
        {
            SE_TRACE(SE_TRACE_WARNING, "symbol '%s' is undefined\n", sym);
            return false;
        }

#define SYMBOL_NUM (ARRAY_LENGTH(str))
        for (size_t i = 0; i < SYMBOL_NUM; ++i)
        {
            if (0 == strcmp(str[i], sym))
            {
                sym_table[sym] = (uint64_t)symtab[idx].st_value;
            }
        }
    }

    // If the enclave if compiled/linked with -fpie/-pie, and setting the
    // enclave entry to `enclave_entry', the `st_name' for `enclave_entry'
    // will be 0 in `.dynsym'.
    map<string, uint64_t>::const_iterator it = sym_table.find("enclave_entry");
    if (it == sym_table.end())
    {
        sym_table["enclave_entry"] = (uint64_t)elf_hdr->e_entry;
    }

    return true;
}

bool do_validate_reltab(const ElfW(Rel) *reltab, size_t nr_rel)
{
    if (reltab == NULL && nr_rel != 0) return false;

#if RTS_SYSTEM_WORDSIZE == 64
    const ElfW(Rel) *rela = reltab;

    for (unsigned idx = 0; idx < nr_rel; idx++, rela++)
    {
        switch (ELF64_R_TYPE(rela->r_info))

        {
        case R_X86_64_RELATIVE:
            break;

        case R_X86_64_GLOB_DAT:
        case R_X86_64_JUMP_SLOT:
        case R_X86_64_64:
            break;

        case R_X86_64_NONE:
            break;

        case R_X86_64_DTPMOD64:
        case R_X86_64_DTPOFF64:
        case R_X86_64_TPOFF64:
            break;
#else
    const ElfW(Rel) *rel = reltab;

    for (unsigned idx = 0; idx < nr_rel; idx++, rel++)
    {
        switch (ELF32_R_TYPE(rel->r_info))
        {
        case R_386_RELATIVE:    /* B+A */
            break;

        case R_386_GLOB_DAT:
        case R_386_JMP_SLOT:    /* S */
            break;

        case R_386_32:          /* S+A */
            break;

        case R_386_PC32:        /* S+A-P */
            break;

        case R_386_NONE:
            break;

        case R_386_TLS_DTPMOD32:
            break;

        case R_386_TLS_DTPOFF32:
            break;

        case R_386_TLS_TPOFF:
            break;

        case R_386_TLS_TPOFF32:
            break;
#endif

        default:    /* unsupported relocs */
            SE_TRACE(SE_TRACE_WARNING, "unsupported relocation type detected\n");
            return false;
        }
    }

    return true;
}

bool validate_reltabs(const ElfW(Ehdr) *elf_hdr, const ElfW(Dyn) *dyn_info)
{
#if RTS_SYSTEM_WORDSIZE == 64
    // The relocation struct must be rela on x64.
    if (dyn_info[DT_REL].d_un.d_ptr)
    {
        SE_TRACE(SE_TRACE_WARNING, "Rel struct detected on x64\n");
        return false;
    }
#else
    // The relocation struct must be rel on x86.
    if (dyn_info[DT_RELA].d_un.d_ptr)
    {
        SE_TRACE(SE_TRACE_WARNING, "Rela struct detected on x86\n");
        return false;
    }
#endif

    const ElfW(Rel) *reltab = get_section_raw_data<ElfW(Rel)>(elf_hdr, dyn_info[RTS_DT_REL].d_un.d_ptr);
    const ElfW(Word) reltab_sz = (ElfW(Word))dyn_info[RTS_DT_RELSZ].d_un.d_val;

    const ElfW(Rel) *jmpreltab = get_section_raw_data<ElfW(Rel)>(elf_hdr, dyn_info[DT_JMPREL].d_un.d_ptr);
    const ElfW(Word) jmpreltab_sz = (ElfW(Word))dyn_info[DT_PLTRELSZ].d_un.d_val;

    return (do_validate_reltab(reltab, reltab_sz / sizeof(ElfW(Rel)))
            && do_validate_reltab(jmpreltab, jmpreltab_sz / sizeof(ElfW(Rel))));
}

bool has_ctor_section(const ElfW(Ehdr) *elf_hdr)
{
    const ElfW(Shdr) *shdr = get_section_by_name(elf_hdr, ".ctors");
    if (NULL == shdr) return false;

    se_trace(SE_TRACE_ERROR, "ERROR: .ctors section is found, global initializers will not be invoked correctly!\n");
    return true;
}

inline bool is_tls_segment(const ElfW(Phdr)* prg_hdr)
{
    return (PT_TLS == prg_hdr->p_type);
}

bool get_meta_property(const uint8_t *start_addr, const ElfW(Ehdr) *elf_hdr, uint64_t &meta_offset, uint64_t &meta_block_size)
{
    const ElfW(Shdr)* shdr = get_section_by_name(elf_hdr, ".note.sgxmeta");
    if (shdr == NULL)
    {
        se_trace(SE_TRACE_ERROR, "ERROR: The enclave image should have '.note.sgxmeta' section\n");
        return false;
    }

    /* We require that enclaves should have .note.sgxmeta section to store the metadata information
     * We limit this section is used for metadata only and ISV should not extend this section.
     *
     * .note.sgxmeta layout:
     *
     * |  namesz         |
     * |  metadata size  |
     * |  type           |
     * |  name           |
     * |  metadata       |
     */

    const ElfW(Note) *note = GET_PTR(ElfW(Note), start_addr, shdr->sh_offset);
    assert(note != NULL);

    if (shdr->sh_size != ROUND_TO(sizeof(ElfW(Note)) + note->namesz + note->descsz, shdr->sh_addralign ))
    {
        se_trace(SE_TRACE_ERROR, "ERROR: The '.note.sgxmeta' section size is not correct.\n");
        return false;
    }
    
    const char * meta_name = "sgx_metadata";
    if (note->namesz != (strlen(meta_name)+1) || memcmp(GET_PTR(void, start_addr, shdr->sh_offset + sizeof(ElfW(Note))), meta_name, note->namesz))
    {
        se_trace(SE_TRACE_ERROR, "ERROR: The note in the '.note.sgxmeta' section must be named as \"sgx_metadata\"\n");
        return false;
    }

    meta_offset = static_cast<uint64_t>(shdr->sh_offset + sizeof(ElfW(Note)) + note->namesz);
    meta_block_size = note->descsz;
    return true;
}

bool validate_segment(const ElfW(Ehdr) *elf_hdr, uint64_t len)
{
    const ElfW(Phdr) *prg_hdr = GET_PTR(ElfW(Phdr), elf_hdr, elf_hdr->e_phoff);
    assert(sizeof(ElfW(Phdr)) == elf_hdr->e_phentsize);

    std::vector< std::pair<ElfW(Addr), ElfW(Addr)> > load_seg(elf_hdr->e_phnum, std::make_pair(0, 0));
    int k = 0;

    for (int idx = 0; idx < elf_hdr->e_phnum; idx++, prg_hdr++)
    {
        /* Validate the size of the buffer */
        if (len < (uint64_t)prg_hdr->p_offset + prg_hdr->p_filesz)
            return false;

        if (PT_LOAD == prg_hdr->p_type)
        {
            // The default align is max page size. On x86-64, the max page size is 2M, but EPC page size is 4K,
            // so in x86-64, we just treat it as EPC page size. The (2M - 4K) size is not eadded. We leave it
            // as a hole.
            if (!IS_PAGE_ALIGNED(prg_hdr->p_align))
            {
                SE_TRACE(SE_TRACE_WARNING, "A segment is not PAGE aligned, alignment = %x\n", prg_hdr->p_align);
                return false;
            }

            // Verify the overlap of segment. we don't verify here, because a well compiled file has no overlapped segment.
            load_seg[k].first = prg_hdr->p_vaddr;
            load_seg[k].second = ROUND_TO(prg_hdr->p_vaddr + prg_hdr->p_memsz, prg_hdr->p_align) - 1;

            for (int j = 0; j < k; j++)
            {
                if (is_overlap(load_seg[k], load_seg[j]))
                {
                    SE_TRACE(SE_TRACE_WARNING, "there is overlap segment [%x : %x] [%x : %x]\n",
                             load_seg[k].first, load_seg[k].second, load_seg[j].first, load_seg[j].second);
                    return false;
                }

            }

            k++;
        }
    }
    return true;
}

bool get_bin_fmt(const ElfW(Ehdr) *elf_hdr, bin_fmt_t& bf)
{
    switch(elf_hdr->e_machine)
    {
#if RTS_SYSTEM_WORDSIZE == 32
    case EM_386:
        bf = BF_ELF32;
        return true;
#endif

#if RTS_SYSTEM_WORDSIZE == 64
    case EM_X86_64:
        bf = BF_ELF64;
        return true;
#endif
    }

    return false;
}

si_flags_t page_attr_to_si_flags(uint32_t page_attr)
{
    si_flags_t res = SI_FLAG_REG;

    if (page_attr & PF_R)
        res |= SI_FLAG_R;

    if (page_attr & PF_W)
        res |= SI_FLAG_W;

    if (page_attr & PF_X)
        res |= SI_FLAG_X;

    return res;
}

Section* build_section(const uint8_t* raw_data, uint64_t size, uint64_t virtual_size,
                       uint64_t rva, uint32_t page_attr)
{
    si_flags_t sf = page_attr_to_si_flags(page_attr);

    if (sf != SI_FLAG_REG)
        return new Section(raw_data, size, virtual_size, rva, sf);

    return NULL;
}

bool build_regular_sections(const uint8_t* start_addr,
                            vector<Section *>& sections,
                            const Section*& tls_sec,
                            uint64_t& metadata_offset,
                            uint64_t& metadata_block_size)
{
    const ElfW(Ehdr) *elf_hdr = (const ElfW(Ehdr) *)start_addr;
    const ElfW(Phdr) *prg_hdr = GET_PTR(ElfW(Phdr), start_addr, elf_hdr->e_phoff);
    uint64_t virtual_size = 0, alignment = 0, aligned_virtual_size = 0;

    if (get_meta_property(start_addr, elf_hdr, metadata_offset, metadata_block_size) == false)
        return false;

    for (unsigned idx = 0; idx < elf_hdr->e_phnum; ++idx, ++prg_hdr)
    {
        Section* sec = NULL;

        switch (prg_hdr->p_type)
        {
        case PT_LOAD:
            sec = build_section(GET_PTR(uint8_t, start_addr, prg_hdr->p_offset),
                                (uint64_t)prg_hdr->p_filesz, (uint64_t)prg_hdr->p_memsz,
                                (uint64_t)prg_hdr->p_vaddr, (uint32_t) prg_hdr->p_flags);
            break;

        case PT_TLS:
            virtual_size = (uint64_t)prg_hdr->p_memsz;
            alignment = (uint64_t)prg_hdr->p_align;

            /*  according to ELF spec, alignment equals zero or one means no align requirement */
            if (alignment == 0 || alignment == 1)
                aligned_virtual_size = virtual_size;
            else
                aligned_virtual_size = (virtual_size + alignment - 1) & (~(alignment - 1));

            sec = build_section(GET_PTR(uint8_t, start_addr, prg_hdr->p_offset),
                                (uint64_t)prg_hdr->p_filesz, aligned_virtual_size,
                                (uint64_t)prg_hdr->p_vaddr, (uint32_t) prg_hdr->p_flags);
            break;

        default:
            continue;
        }

        if (sec == NULL)
            return false;

        /* We've filtered segments that are not of PT_LOAD or PT_TLS type. */
        if (!is_tls_segment(prg_hdr))
        {
            /* A PT_LOAD segment. */
            sections.push_back(sec);
            continue;
        }

        /* It is a TLS segment. */
        tls_sec = sec;
    }

    return true;
}

const Section* get_max_rva_section(const vector<Section*> sections)
{
    size_t sec_size = sections.size();

    if (sec_size == 0)
        return NULL;

    const Section* psec = sections[0];
    for (size_t idx = 1; idx < sec_size; ++idx)
    {
        if (sections[idx]->get_rva() > psec->get_rva())
            psec = sections[idx];
    }

    return psec;
}
}

ElfParser::ElfParser (const uint8_t* start_addr, uint64_t len)
    :m_start_addr(start_addr), m_len(len), m_bin_fmt(BF_UNKNOWN),
     m_tls_section(NULL), m_metadata_offset(0), m_metadata_block_size(0)
{
    memset(&m_dyn_info, 0, sizeof(m_dyn_info));
}

sgx_status_t ElfParser::run_parser()
{
    /* We only need to run the parser once. */
    if (m_sections.size() != 0) return SGX_SUCCESS;

    const ElfW(Ehdr) *elf_hdr = (const ElfW(Ehdr) *)m_start_addr;
    if (elf_hdr == NULL || m_len < sizeof(ElfW(Ehdr)))
        return SGX_ERROR_INVALID_ENCLAVE;

    /* Check elf header*/
    if (!validate_elf_header(elf_hdr))
        return SGX_ERROR_INVALID_ENCLAVE;

    /* Get and check machine mode */
    if (!get_bin_fmt(elf_hdr, m_bin_fmt))
        return SGX_ERROR_MODE_INCOMPATIBLE;

    /* Check if there is any overlap segment, and make sure the segment is 1 page aligned;
    * TLS segment must exist.
    */
    if (!validate_segment(elf_hdr, m_len))
        return SGX_ERROR_INVALID_ENCLAVE;

    if (!parse_dyn(elf_hdr, &m_dyn_info[0]))
        return SGX_ERROR_INVALID_ENCLAVE;

    /* Check if there is any undefined symbol */
    if (!check_symbol_table(elf_hdr, m_dyn_info, m_sym_table))
    {
        return SGX_ERROR_UNDEFINED_SYMBOL;
    }

    /* Check if there is unexpected relocation type */
    if (!validate_reltabs(elf_hdr, m_dyn_info))
        return SGX_ERROR_INVALID_ENCLAVE;

    /* Check if there is .ctor section */
    if (has_ctor_section(elf_hdr))
        return SGX_ERROR_INVALID_ENCLAVE;

    /* build regular sections */
    if (build_regular_sections(m_start_addr, m_sections, m_tls_section, m_metadata_offset, m_metadata_block_size))
        return SGX_SUCCESS;
    else
        return SGX_ERROR_INVALID_ENCLAVE;
}

ElfParser::~ElfParser()
{
    delete_ptrs_from_container(m_sections);
    if (m_tls_section) delete m_tls_section;
}

bin_fmt_t ElfParser::get_bin_format() const
{
    return m_bin_fmt;
}

uint64_t ElfParser::get_enclave_max_size() const
{
    if(m_bin_fmt == BF_ELF64)
        return ENCLAVE_MAX_SIZE_64;
    else
        return ENCLAVE_MAX_SIZE_32;
}

uint64_t ElfParser::get_metadata_offset() const
{
    return m_metadata_offset;
}

uint64_t ElfParser::get_metadata_block_size() const
{
    return m_metadata_block_size;
}


const uint8_t* ElfParser::get_start_addr() const
{
    return m_start_addr;
}

const vector<Section *>& ElfParser::get_sections() const
{
    return m_sections;
}

const Section* ElfParser::get_tls_section() const
{
    return m_tls_section;
}

uint64_t ElfParser::get_symbol_rva(const char* name) const
{
    map<string, uint64_t>::const_iterator it = m_sym_table.find(name);
    if (it != m_sym_table.end())
        return it->second;
    else
        return 0;
}

bool ElfParser::get_reloc_bitmap(vector<uint8_t>& bitmap)
{
    // Clear the `bitmap' so that it is in a known state
    bitmap.clear();

    if (!m_dyn_info[DT_TEXTREL].d_tag)
        return true;

    const ElfW(Ehdr) *elf_hdr = (const ElfW(Ehdr) *)m_start_addr;
    const ElfW(Rel) *rel[4] = { NULL, NULL, NULL, NULL };

    if (m_dyn_info[DT_JMPREL].d_tag)
    {
        rel[2] = get_section_raw_data<ElfW(Rel)>(elf_hdr, m_dyn_info[DT_JMPREL].d_un.d_ptr);
        rel[3] = GET_PTR(const ElfW(Rel), rel[2], m_dyn_info[DT_PLTRELSZ].d_un.d_val);
    }

    if (m_dyn_info[RTS_DT_REL].d_tag)
    {
        rel[0] = get_section_raw_data<ElfW(Rel)>(elf_hdr, m_dyn_info[RTS_DT_REL].d_un.d_ptr);
        rel[1] = GET_PTR(const ElfW(Rel), rel[0], m_dyn_info[RTS_DT_RELSZ].d_un.d_val);
        assert(sizeof(ElfW(Rel)) ==  m_dyn_info[RTS_DT_RELENT].d_un.d_val);
    }

    // The enclave size mapped in memory is calculated by
    //   sec->get_rva() + sec->virtual_size();
    // where the `sec' is the section with maximum RVA value.
    uint64_t image_size = 0;
    const Section* max_rva_sec = get_max_rva_section(this->m_sections);
    if (max_rva_sec == NULL)
        return false;

    image_size = max_rva_sec->get_rva() + max_rva_sec->virtual_size();

    // NOTE:
    //  Current enclave size is not beyond 64G, so the type-casting from (uint64>>15) to (size_t) is OK.
    //  In the future, if the max enclave size is extended to beyond 1<<49, this type-casting will not work.
    //  It only impacts the enclave signing process. (32bit signing tool to sign 64 bit enclaves)

    // allocate bitmap
    bitmap.resize((size_t)((((image_size + (SE_PAGE_SIZE - 1)) >> SE_PAGE_SHIFT) + 7) / 8));

    for (unsigned idx = 0; idx < ARRAY_LENGTH(rel); idx += 2)
    {
        const ElfW(Rel) *rel_entry = rel[idx], *rel_end = rel[idx+1];
        if (NULL == rel_entry)
            continue;

        for (; rel_entry < rel_end; rel_entry++)
        {
#if RTS_SYSTEM_WORDSIZE == 64
            if (ELF64_R_TYPE(rel_entry->r_info) == R_X86_64_NONE)
#else
            if (ELF32_R_TYPE(rel_entry->r_info) == R_386_NONE)
#endif
                continue;

            ElfW(Addr) reloc_addr = rel_entry->r_offset;
            uint64_t page_frame = (uint64_t)(reloc_addr >> SE_PAGE_SHIFT);

            // NOTE:
            //  Current enclave size is not beyond 64G, so the type-casting from (uint64>>15) to (size_t) is OK.
            //  In the future, if the max enclave size is extended to beyond 1<<49, this type-casting will not work.
            //  It only impacts the enclave signing process. (32bit signing tool to sign 64 bit enclaves)

            // If there is more than one relocation in one page, then "|" works as there
            // is only one relocation in one page.
            bitmap[(size_t)(page_frame/8)] = (uint8_t)(bitmap[(size_t)(page_frame/8)] | (uint8_t)(1 << (page_frame % 8)));

            // Check if the relocation across boundary
            if ((reloc_addr & (SE_PAGE_SIZE - 1)) > (SE_PAGE_SIZE - sizeof(sys_word_t)))
            {
                page_frame++;
                bitmap[(size_t)(page_frame/8)] = (uint8_t)(bitmap[(size_t)(page_frame/8)] | (uint8_t)(1 << (page_frame % 8)));
            }
        }
    }

    return true;
}

void ElfParser::get_reloc_entry_offset(const char* sec_name, vector<uint64_t>& offsets)
{
    if (sec_name == NULL)
        return;

    const ElfW(Ehdr) *ehdr = (const ElfW(Ehdr) *)m_start_addr;
    const ElfW(Shdr) *shdr = get_section_by_name(ehdr, sec_name);

    if (shdr == NULL)
        return;

    /* find the start and end offset of the target section */
    const uint64_t start = shdr->sh_addr;
    const uint64_t end   = start + shdr->sh_size;

    offsets.clear();
    SE_TRACE(SE_TRACE_DEBUG, "found section '%s' - offset %#lx, size %#lx\n",
             sec_name, (long)start, (long)shdr->sh_size);

    /* iterate sections to find the relocs */
    shdr = GET_PTR(ElfW(Shdr), m_start_addr, ehdr->e_shoff);
    for (unsigned idx = 0; idx < ehdr->e_shnum; ++idx, ++shdr)
    {
        if (shdr->sh_type != SHT_RELA &&
                shdr->sh_type != SHT_REL)
            continue;

        uint64_t rel_size   = shdr->sh_size;
        uint64_t rel_offset = shdr->sh_offset;
        uint64_t nr_rel     = rel_size / shdr->sh_entsize;

        /* for each reloc, check its target address */
        const ElfW(Rel) *rel = GET_PTR(ElfW(Rel), m_start_addr, rel_offset);
        for (; nr_rel > 0; --nr_rel, ++rel)
        {
            if (rel->r_offset >= start && rel->r_offset < end)
            {
                uint64_t offset = DIFF64(rel, m_start_addr);
                SE_TRACE(SE_TRACE_DEBUG, "found one reloc at offset %#lx\n", offset);
                offsets.push_back(offset);
            }
        }
    }
}

#include "se_page_attr.h"
#include "update_global_data.hxx"

uint32_t ElfParser::get_global_data_size()
{
    return (uint32_t)sizeof(global_data_t);
}
bool ElfParser::update_global_data(const metadata_t *const metadata,
                                   const create_param_t* const create_param,
                                   uint8_t *data,
                                   uint32_t *data_size)
{
    if(*data_size < sizeof(global_data_t))
    {
        *data_size = sizeof(global_data_t);
        return false;
    }
    *data_size = sizeof(global_data_t);
    return do_update_global_data(metadata, create_param, (global_data_t *)data);
}

sgx_status_t ElfParser::modify_info(enclave_diff_info_t *enclave_diff_info)
{
    UNUSED(enclave_diff_info);
    return SGX_SUCCESS;
}

sgx_status_t ElfParser::get_info(enclave_diff_info_t *enclave_diff_info)
{
    UNUSED(enclave_diff_info);
    return SGX_SUCCESS;
}

void ElfParser::get_executable_sections(vector<const char *>& xsec_names) const
{
    xsec_names.clear();

    const ElfW(Ehdr) *elf_hdr = (const ElfW(Ehdr) *)m_start_addr;
    const ElfW(Shdr) *shdr = GET_PTR(ElfW(Shdr), elf_hdr, elf_hdr->e_shoff);
    const char *shstrtab = GET_PTR(char, elf_hdr, shdr[elf_hdr->e_shstrndx].sh_offset);

    for (unsigned idx = 0; idx < elf_hdr->e_shnum; ++idx, ++shdr)
    {
        if ((shdr->sh_flags & SHF_EXECINSTR) == SHF_EXECINSTR)
            xsec_names.push_back(shstrtab + shdr->sh_name);
    }
    return;
}
