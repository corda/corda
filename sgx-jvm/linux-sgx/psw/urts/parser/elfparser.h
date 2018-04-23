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
#ifndef _ELFPARSER_H_
#define _ELFPARSER_H_

#include "binparser.h"
#include "elf_util.h"

#include <assert.h>
#include <stdint.h>
#include <string>
#include <map>

using std::map;
using std::string;

class ElfParser : public BinParser, private Uncopyable {
public:
    // The `start_addr' cannot be NULL
    ElfParser(const uint8_t* start_addr, uint64_t len);
    ~ElfParser();

    // Do the parsing job - use it before calling other methods
    sgx_status_t run_parser();

    bin_fmt_t get_bin_format() const;

    uint64_t get_enclave_max_size() const;

    uint64_t get_metadata_offset() const;

    uint64_t get_metadata_block_size() const;

    const uint8_t* get_start_addr() const;

    // The `section' here is a section in PE's concept.
    // It is in fact a `segment' in ELF's view.
    const vector<Section *>& get_sections() const;
    const Section* get_tls_section() const;
    uint64_t get_symbol_rva(const char* name) const;

    bool get_reloc_bitmap(vector<uint8_t> &bitmap);
    uint32_t get_global_data_size();
    bool update_global_data(const metadata_t *const metadata,
                            const create_param_t* const create_param,
                            uint8_t *data,
                            uint32_t *data_size);

    // Get the offsets (relative to the base address) of the relocation
    // entries, of which the relocation address falls into the range of
    // section identified by `sec_name'.
    //
    // The relocation entry is of type:
    //   - Elf64_Rel on x86_64,
    //   - Elf32_Rel on x86.
    //
    // To check whether current enclave has any TEXTREL:
    //   get_reloc_entry_offset(".text", offsets);
    void get_reloc_entry_offset(const char* sec_name,
                                vector<uint64_t>& offsets);

    sgx_status_t modify_info(enclave_diff_info_t *enclave_diff_info);
    sgx_status_t get_info(enclave_diff_info_t *enclave_diff_info);
    void get_executable_sections(vector<const char *>& xsec_names) const;

private:
    const uint8_t*      m_start_addr;
    uint64_t            m_len;
    bin_fmt_t           m_bin_fmt;
    vector<Section *>   m_sections;
    const Section*      m_tls_section;
    uint64_t            m_metadata_offset;
    uint64_t            m_metadata_block_size;/*multiple metadata block size*/

    ElfW(Dyn)           m_dyn_info[DT_NUM + DT_ADDRNUM];

    // A map from symbol name to its RVA
    map<string, uint64_t> m_sym_table;
};

#endif
