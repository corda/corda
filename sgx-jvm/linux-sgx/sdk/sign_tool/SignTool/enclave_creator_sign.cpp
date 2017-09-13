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

/**
* File: 
*     enclave_creator_sign.cpp
* Description: 
*     Measure the necessary information of the enclave
* to calculate the HASH value using SHA256 algorithm. 
*/

#include "enclave_creator.h"
#include "sgx_eid.h"
#include "enclave_creator_sign.h"
#include "ipp_wrapper.h"
#include "se_trace.h"
#include "sgx_error.h"
#include "util_st.h"
#include "util.h"
#include "se_page_attr.h"

#include <stdlib.h>
#include <string.h>
#include <assert.h>

#define DATA_BLOCK_SIZE 64
#define EID             0x44444444

EnclaveCreatorST::EnclaveCreatorST()
{
    m_hash_valid_flag = false;
    memset(m_enclave_hash, 0, SGX_HASH_SIZE);
    m_ctx = NULL;
    m_eid = EID;
    m_quota = 0;
}

EnclaveCreatorST::~EnclaveCreatorST()
{
    SAFE_FREE_MM(m_ctx);
}

int EnclaveCreatorST::create_enclave(secs_t *secs, sgx_enclave_id_t *enclave_id, void **start_addr, bool ae)
{
    if(!secs || !enclave_id || !start_addr)
    {
        se_trace(SE_TRACE_DEBUG, "ERROR: Bad pointer.\n");
        return SGX_ERROR_UNEXPECTED;
    }

    UNUSED(ae);

    memset(m_enclave_hash, 0, SGX_HASH_SIZE);
    int size_in_byte = 0;
    IppStatus error_code = ippsHashGetSize(&size_in_byte);
    if(error_code != ippStsNoErr)
    {
        se_trace(SE_TRACE_DEBUG, "ERROR:ippsHashGetSize() failed in the enclave measurement process.\n");
        return SGX_ERROR_UNEXPECTED;
    }
    m_ctx = (IppsHashState *)malloc(size_in_byte);
    if(m_ctx == NULL)
    {
        se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    error_code = ippsHashInit(m_ctx, IPP_ALG_HASH_SHA256); 
    if(error_code != ippStsNoErr)
    {
        se_trace(SE_TRACE_DEBUG, "ERROR:ippsHashInit() failed in the enclave measurement process.\n");
        return SGX_ERROR_UNEXPECTED;
    }
    uint8_t ecreat_val[SIZE_NAMED_VALUE] = "ECREATE";

    uint8_t data_block[DATA_BLOCK_SIZE];
    size_t offset = 0;
    memset(data_block, 0, DATA_BLOCK_SIZE);
    memcpy_s(data_block, sizeof(data_block), ecreat_val, SIZE_NAMED_VALUE);
    offset += SIZE_NAMED_VALUE;
    memcpy_s(&data_block[offset], sizeof(data_block)-offset, &secs->ssa_frame_size, sizeof(secs->ssa_frame_size));
    offset += sizeof(secs->ssa_frame_size);
    memcpy_s(&data_block[offset], sizeof(data_block)-offset, &secs->size, sizeof(secs->size));
    error_code = ippsHashUpdate((Ipp8u *)&data_block, DATA_BLOCK_SIZE, m_ctx);
    if(error_code != ippStsNoErr)
    {
        se_trace(SE_TRACE_DEBUG, "ERROR:ippsHashUpdate() failed in the enclave measurement(ECREATE) process.\n");
        return SGX_ERROR_UNEXPECTED;
    }

    *enclave_id = m_eid;
    *start_addr = secs->base;
    return SGX_SUCCESS;
}

int EnclaveCreatorST::add_enclave_page(sgx_enclave_id_t enclave_id, void *src, uint64_t offset, const sec_info_t &sinfo, uint32_t attr)
{   
    assert(m_ctx!=NULL);
    UNUSED(enclave_id);
    void* source = src;
    uint8_t color_page[SE_PAGE_SIZE];
    if(!source)
    {
        memset(color_page, 0, SE_PAGE_SIZE);
        source = reinterpret_cast<void*>(&color_page);
    }

    for(unsigned int i = 0; i< sizeof(sinfo.reserved)/sizeof(sinfo.reserved[0]); i++)
    {
        if(sinfo.reserved[i] != 0)
            return SGX_ERROR_UNEXPECTED;
    }
    /* sinfo.flags[64:16] should be 0 */
    if((sinfo.flags & (~SI_FLAGS_EXTERNAL)) != 0)
    {
        return SGX_ERROR_UNEXPECTED;
    }

    /* Check the page attributes: must be ADD only or ADD+EXTEND */
    if (!(attr & (ADD_PAGE_ONLY)) || (attr & (~(ADD_EXTEND_PAGE))))
    {
        return SGX_ERROR_UNEXPECTED;
    }

    uint64_t page_offset = (uint64_t)offset;
    uint8_t eadd_val[SIZE_NAMED_VALUE] = "EADD\0\0\0";

    uint8_t data_block[DATA_BLOCK_SIZE];
    size_t db_offset = 0;
    memset(data_block, 0, DATA_BLOCK_SIZE);
    memcpy_s(data_block, sizeof(data_block), eadd_val, SIZE_NAMED_VALUE);
    db_offset += SIZE_NAMED_VALUE;
    memcpy_s(data_block+db_offset, sizeof(data_block)-db_offset, &page_offset, sizeof(page_offset));
    db_offset += sizeof(page_offset);
    memcpy_s(data_block+db_offset, sizeof(data_block)-db_offset, &sinfo, sizeof(data_block)-db_offset);
    IppStatus error_code = ippsHashUpdate((Ipp8u *)&data_block, DATA_BLOCK_SIZE, m_ctx);
    if(error_code != ippStsNoErr)
    {
        se_trace(SE_TRACE_DEBUG, "ERROR::ippsHashUpdate() failed in the enclave measurement(EADD) process.\n");
        return SGX_ERROR_UNEXPECTED;
    }

    /* If the page need to eextend, do eextend. */
    if((attr & ADD_EXTEND_PAGE) == ADD_EXTEND_PAGE)
    {
        uint8_t *pdata = (uint8_t *)source;
        uint8_t eextend_val[SIZE_NAMED_VALUE] = "EEXTEND";

#define EEXTEND_TIME  4 
        for(int i = 0; i < SE_PAGE_SIZE; i += (DATA_BLOCK_SIZE * EEXTEND_TIME))
        {
            db_offset = 0;
            memset(data_block, 0, DATA_BLOCK_SIZE);
            memcpy_s(data_block, sizeof(data_block), eextend_val, SIZE_NAMED_VALUE);
            db_offset += SIZE_NAMED_VALUE;
            memcpy_s(data_block+db_offset, sizeof(data_block)-db_offset, &page_offset, sizeof(page_offset));

            error_code = ippsHashUpdate((Ipp8u *)&data_block, DATA_BLOCK_SIZE, m_ctx);
            if(error_code != ippStsNoErr)
            {
                se_trace(SE_TRACE_DEBUG, "ERROR:ippsHashUpdate() failed in the enclave measurement(EEXTEND) process.\n");
                return SGX_ERROR_UNEXPECTED;
            }

            for(int j = 0; j < EEXTEND_TIME; j++)
            {
                memcpy_s(data_block, sizeof(data_block), pdata, DATA_BLOCK_SIZE);
                error_code = ippsHashUpdate((Ipp8u *)&data_block, DATA_BLOCK_SIZE, m_ctx);
                if(error_code != ippStsNoErr)
                {
                    se_trace(SE_TRACE_DEBUG, "ERROR:ippsHashUpdate() failed in the enclave measurement(EEXTEND) process.\n");
                    return SGX_ERROR_UNEXPECTED;
                }
                pdata += DATA_BLOCK_SIZE;
                page_offset += DATA_BLOCK_SIZE;
            }
        }
    }

    m_quota += SE_PAGE_SIZE;
    return SGX_SUCCESS;
}

int EnclaveCreatorST::init_enclave(sgx_enclave_id_t enclave_id, enclave_css_t *enclave_css, SGXLaunchToken *lc, le_prd_css_file_t *prd_css_file)
{
    assert(m_ctx != NULL);
    UNUSED(enclave_id), UNUSED(enclave_css), UNUSED(lc), UNUSED(prd_css_file);

    uint8_t temp_hash[SGX_HASH_SIZE];
    memset(temp_hash, 0, SGX_HASH_SIZE);

    /* Complete computation of the SHA256 digest and store the result into the hash. */
    IppStatus  error_code = ippsHashFinal((Ipp8u *)temp_hash, m_ctx);
    if(error_code != ippStsNoErr)
    {
        se_trace(SE_TRACE_DEBUG, "ERROR:ippsHashFinal() failed in the enclave measurement process.\n");
        return SGX_ERROR_UNEXPECTED;
    }

    for (int i = 0; i< SGX_HASH_SIZE; i++)
    {
        m_enclave_hash[i] = temp_hash[i];
    }
    m_hash_valid_flag = true;
    return SGX_SUCCESS;
}

int EnclaveCreatorST::get_misc_attr(sgx_misc_attribute_t *sgx_misc_attr, metadata_t *metadata, SGXLaunchToken * const lc, uint32_t flag)
{
    UNUSED(metadata), UNUSED(lc), UNUSED(flag);
    memset(sgx_misc_attr, 0, sizeof(sgx_misc_attribute_t));
    return SGX_SUCCESS;
}

int EnclaveCreatorST::destroy_enclave(sgx_enclave_id_t enclave_id, uint64_t enclave_size)
{
    UNUSED(enclave_id);
    UNUSED(enclave_size);
    SAFE_FREE_MM(m_ctx);
    return SGX_SUCCESS;
}

bool EnclaveCreatorST::get_plat_cap(sgx_misc_attribute_t *se_attr)
{
    UNUSED(se_attr);
    return false;
}

int EnclaveCreatorST::initialize(sgx_enclave_id_t enclave_id)
{
    UNUSED(enclave_id);
    return SGX_SUCCESS;
}

bool EnclaveCreatorST::use_se_hw() const
{
    return false;
}

int EnclaveCreatorST::get_enclave_info(uint8_t *hash, int size, uint64_t *quota)
{
    if(hash == NULL || size != SGX_HASH_SIZE || m_hash_valid_flag == false)
    {
        se_trace(SE_TRACE_DEBUG, "ERROR: something went wrong in the function get_enclave_hash().\n");
        return SGX_ERROR_UNEXPECTED;
    }
    else
    {
        memcpy_s(hash, size, m_enclave_hash, SGX_HASH_SIZE);
    }
    *quota = m_quota;
    return SGX_SUCCESS;
}

static EnclaveCreatorST g_enclave_creator_st;
EnclaveCreator* g_enclave_creator = &g_enclave_creator_st;
