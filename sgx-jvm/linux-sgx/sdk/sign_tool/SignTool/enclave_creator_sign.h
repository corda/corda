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

#ifndef _ENCLAVE_CREATOR_SIGN_H_
#define _ENCLAVE_CREATOR_SIGN_H_

#include <openssl/evp.h>


#include "enclave_creator.h"
#include "sgx_eid.h"

#define SIZE_NAMED_VALUE 8

class EnclaveCreatorST : public EnclaveCreator
{
public:
    EnclaveCreatorST();
    virtual ~EnclaveCreatorST();
    int create_enclave(secs_t *secs, sgx_enclave_id_t *enclave_id, void **start_addr, bool ae);
    int add_enclave_page(sgx_enclave_id_t enclave_id, void *source, uint64_t offset, const sec_info_t &sinfo, uint32_t attr);
    int init_enclave(sgx_enclave_id_t enclave_id, enclave_css_t *enclave_css, SGXLaunchToken *lc, le_prd_css_file_t *prd_css_file);
    int get_misc_attr(sgx_misc_attribute_t *sgx_misc_attr, metadata_t *metadata, SGXLaunchToken * const lc, uint32_t flag);
    bool get_plat_cap(sgx_misc_attribute_t *se_attr);
    int destroy_enclave(sgx_enclave_id_t enclave_id, uint64_t enclave_size);
    int initialize(sgx_enclave_id_t enclave_id);
    bool use_se_hw() const;
    bool is_EDMM_supported(sgx_enclave_id_t enclave_id);
    bool is_driver_compatible();
    int get_enclave_info(uint8_t *hash, int size, uint64_t *quota);
    int emodpr(uint64_t addr, uint64_t size, uint64_t flag);
    int mktcs(uint64_t tcs_addr);
    int trim_range(uint64_t fromaddr, uint64_t toaddr);
    int trim_accept(uint64_t addr);
    int remove_range(uint64_t fromaddr, uint64_t numpages);
private:
    uint8_t m_enclave_hash[SGX_HASH_SIZE];
    EVP_MD_CTX  *m_ctx;
    bool m_hash_valid_flag;
    sgx_enclave_id_t m_eid;
    uint64_t m_quota;
};

#endif
