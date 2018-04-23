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

#ifndef _BINPARSER_H_
#define _BINPARSER_H_

#include "section.h"
#include "util.h"
#include "se_memcpy.h"
#include "create_param.h"
#include "sgx_error.h"

#include <stdint.h>
#include <vector>
#include <string>
using std::vector;
using std::string;

#define ENCLAVE_MAX_SIZE_32 0xffffffff
#define ENCLAVE_MAX_SIZE_64 0x1fffffffff

typedef enum _bin_fmt_t
{
    BF_UNKNOWN = 0,
    BF_PE32,
    BF_PE64,
    BF_ELF32,
    BF_ELF64
} bin_fmt_t;

typedef struct _enclave_diff_info_t
{
} enclave_diff_info_t;

class BinParser {
public:
    virtual ~BinParser() {}

    virtual sgx_status_t run_parser() = 0;

    virtual bin_fmt_t get_bin_format() const = 0;

    virtual uint64_t get_enclave_max_size() const = 0;

    virtual uint64_t get_metadata_offset() const = 0;

    virtual uint64_t get_metadata_block_size() const = 0;

    virtual const uint8_t* get_start_addr() const = 0;

    // Get a vector of sections to be loaded
    virtual const vector<Section *>& get_sections() const = 0;

    // Get the TLS section
    virtual const Section* get_tls_section() const = 0;

    virtual uint64_t get_symbol_rva(const char* name) const = 0;

    virtual bool get_reloc_bitmap(vector<uint8_t> &bitmap) = 0;

    virtual void get_reloc_entry_offset(const char* sec_name,
                                        vector<uint64_t>& offsets) = 0;

    // !We need to put this method into BinParser class since
    // !the `global_data_t' is platform-dependent as the parser.
    // !c.f. global_data.h for more information.
    // !
    // !Unfortunately, although the implementation is the same,
    // !we can't put them here but into the subclasses -
    // !ElfParsr and PEParser, since the `global_data_t' for
    // !them are different in terms of size and layout.
    virtual bool update_global_data(const metadata_t *const metadata, 
                                    const create_param_t* const create_param,
                                    uint8_t *data,
                                    uint32_t *data_size) = 0;
    virtual uint32_t get_global_data_size() = 0;

    virtual sgx_status_t modify_info(enclave_diff_info_t *enclave_diff_info) = 0;

    virtual sgx_status_t get_info(enclave_diff_info_t *enclave_diff_info) = 0;

    virtual void get_executable_sections(vector<const char *>& xsec_names) const = 0;
};

#endif
