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


#ifndef _ELF_HELPER_H_
#define _ELF_HELPER_H_

#include <cstddef>
#include <iostream>

#include "elf32parser.h"
#include "elf64parser.h"

static void dump_textrel(const uint64_t& offset)
{
    using namespace std;

    cerr << "warning: TEXTRELs found at offset: "
         << hex << showbase     /* show the '0x' prefix */
         << offset << endl;
}

template <int N>
struct ParserType
{
};

template <>
struct ParserType<32>
{
    typedef Elf32Parser elf_parser_t;
    typedef Elf32_Rel   elf_rel_t;
};

template <>
struct ParserType<64>
{
    typedef Elf64Parser elf_parser_t;
    typedef Elf64_Rela  elf_rel_t;
};

template <int N>
class ElfHelper
{
    typedef typename ParserType<N>::elf_parser_t elf_parser_t;
    typedef typename ParserType<N>::elf_rel_t    elf_rel_t;

public:

#define get_rel_entry_addr(p, rel_entry_offset) \
    ((elf_rel_t *)const_cast<uint8_t *>(p->get_start_addr() + rel_entry_offset))

    static uint64_t get_r_offset_from_entry(const elf_parser_t* p,
                                            uint64_t offsets)
    {
        const elf_rel_t *rel = get_rel_entry_addr(p, offsets);
        return (uint64_t)rel->r_offset;
    }

    static bool dump_textrels(BinParser *bp)
    {
        vector<uint64_t> offsets;
        bool no_rel = true;
        /* The dynamic_cast<> shouldn't fail. */
        elf_parser_t   * p = dynamic_cast<elf_parser_t*>(bp);
        if (p == NULL)
            return no_rel;
        vector<const char *> sec_names;
        p->get_executable_sections(sec_names);
        /* Warn user of TEXTRELs */
        for (unsigned i = 0; i< sec_names.size(); i++)
        {
            p->get_reloc_entry_offset(sec_names[i], offsets);
            if(offsets.size() != 0 && no_rel == true)
                 no_rel = false;

            for (size_t idx = 0; idx < offsets.size(); ++idx)
            {
                dump_textrel(get_r_offset_from_entry(p, offsets[idx]));
            }
        }
        return no_rel;
    }
};

#endif
