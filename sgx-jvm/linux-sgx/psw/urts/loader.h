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


#ifndef _LOADER_H_
#define _LOADER_H_

#include "se_wrapper.h"
#include "arch.h"
#include "enclave.h"
#include "enclave_creator.h"
#include "section_info.h"
#include "launch_checker.h"
#include "file.h"

#define GET_RELOC_FAILED ((uint8_t *)-1)

#if defined(SE_SIM)
#define ENCLAVE_ID_IOCTL m_enclave_id
#else
//only translate enclave id to start address for linux HW mode.
#define ENCLAVE_ID_IOCTL (sgx_enclave_id_t)((uintptr_t)m_start_addr)
#endif

class BinParser;

class CLoader: private Uncopyable
{
public:
    CLoader(uint8_t *mapped_file_base, BinParser &parser);
    virtual ~CLoader();
    int load_enclave(SGXLaunchToken *lc, int flag, const metadata_t *metadata, le_prd_css_file_t *prd_css_file = NULL, sgx_misc_attribute_t *misc_attr = NULL);
    int load_enclave_ex(SGXLaunchToken *lc, bool is_debug, const metadata_t *metadata, le_prd_css_file_t *prd_css_file = NULL, sgx_misc_attribute_t *misc_attr = NULL);
    int destroy_enclave();
    sgx_enclave_id_t get_enclave_id() const;
    const void* get_start_addr() const;
    const secs_t& get_secs() const;
    const std::vector<std::pair<tcs_t *, bool>>& get_tcs_list() const;
    void* get_symbol_address(const char* const sym);
    int set_memory_protection();
    int post_init_action(layout_t *start, layout_t *end, uint64_t delta);
    int post_init_action_commit(layout_t *start, layout_t *end, uint64_t delta);

private:
    int build_mem_region(const section_info_t &sec_info);
    int build_image(SGXLaunchToken * const lc, sgx_attributes_t * const secs_attr, le_prd_css_file_t *prd_css_file, sgx_misc_attribute_t * const misc_attr);
    int build_secs(sgx_attributes_t * const secs_attr, sgx_misc_attribute_t * const misc_attr);
    int build_context(const uint64_t start_rva, layout_entry_t *layout);
    int build_contexts(layout_t *layout_start, layout_t *layout_end, uint64_t delta);
    int build_partial_page(const uint64_t rva, const uint64_t size, const void *source, const sec_info_t &sinfo, const uint32_t attr);
    int build_pages(const uint64_t start_rva, const uint64_t size, const void *source, const sec_info_t &sinfo, const uint32_t attr);
    bool is_relocation_page(const uint64_t rva, vector<uint8_t> *bitmap);

    bool is_ae(const enclave_css_t *enclave_css);
    bool is_metadata_buffer(uint32_t offset, uint32_t size);
    bool is_enclave_buffer(uint64_t offset, uint64_t size);
    int validate_layout_table();
    int validate_patch_table();
    int validate_metadata();
    int get_debug_flag(const token_t * const launch);
    virtual int build_sections(vector<uint8_t> *bitmap);
    int set_context_protection(layout_t *layout_start, layout_t *layout_end, uint64_t delta);

    uint8_t             *m_mapped_file_base;
    sgx_enclave_id_t    m_enclave_id;
    void                *m_start_addr;

    // the TCS list
    std::vector<std::pair<tcs_t *, bool>> m_tcs_list;
    // the enclave creation parameters
    const metadata_t    *m_metadata;
    secs_t              m_secs;
    BinParser           &m_parser;
};

#endif
