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
#include <assert.h>
#include "parserfactory.h"
#include "util.h"

#include "elf32parser.h"
#include "elf64parser.h"

namespace {
    bin_fmt_t check_elf_format(const uint8_t* start_addr, uint64_t len)
    {
        assert(start_addr != NULL);
        const Elf32_Ehdr* ehdr = (const Elf32_Ehdr *) start_addr;

        if (len < sizeof(Elf32_Ehdr))
            return BF_UNKNOWN;

        if (strncmp((const char *)ehdr->e_ident, ELFMAG, SELFMAG) != 0)
            return BF_UNKNOWN;

        switch (ehdr->e_ident[EI_CLASS])
        {
            case ELFCLASS32: return BF_ELF32;
            case ELFCLASS64: return BF_ELF64;
            default: return BF_UNKNOWN;
        }
    }
}

namespace binparser {
    /* Note, the `start_addr' should NOT be NULL! */
    BinParser* get_parser(const uint8_t* start_addr, uint64_t len)
    {
        assert(start_addr != NULL);

        bin_fmt_t bf = BF_UNKNOWN;

        bf = check_elf_format(start_addr, len);
        if (bf == BF_ELF64) return new Elf64Parser(start_addr, len);

        /* Doesn't matter whether it is an ELF32 shared library or not,
         * here we just make sure that the factory method won't return
         * NULL.
         */
        return new Elf32Parser(start_addr, len);
    }
}
