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
 * File: sgx_create_report.cpp
 * Description:
 *     Wrapper for EREPORT instruction
 */

#include "sgx_utils.h"
#include "util.h"
#include <stdlib.h>
#include <string.h>
#include "se_memcpy.h"
#include "sgx_trts.h"
#include "trts_inst.h"
#include "se_cdefs.h"

// add a version to tservice.
SGX_ACCESS_VERSION(tservice, 1)

sgx_status_t sgx_create_report(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report)
{
    int i = 0;
    // check parameters
    //
    // target_info is allowed to be NULL, but if it is not NULL, it must be within the enclave
    if(target_info)
    {
        if (!sgx_is_within_enclave(target_info, sizeof(*target_info)))
            return SGX_ERROR_INVALID_PARAMETER;

        for(i=0; i<SGX_TARGET_INFO_RESERVED1_BYTES; ++i)
        {
            if (target_info->reserved1[i] != 0)
                return SGX_ERROR_INVALID_PARAMETER;
        }

        for(i=0; i<SGX_TARGET_INFO_RESERVED2_BYTES; ++i)
        {
            if (target_info->reserved2[i] != 0)
                return SGX_ERROR_INVALID_PARAMETER;
        }
    }
    // report_data is allowed to be NULL, but if it is not NULL, it must be within the enclave
    if(report_data && !sgx_is_within_enclave(report_data, sizeof(*report_data)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    // report must be within the enclave
    if(!report || !sgx_is_within_enclave(report, sizeof(*report)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    // allocate memory
    // 
    // To minimize the effort of memory management, the three elements allocation 
    // are combined in a single malloc. The calculation for the required size has
    // an assumption, that
    // the elements should be allocated in descending order of the alignment size. 
    //
    // If the alignment requirements are changed, the allocation order needs to
    // change accordingly.
    //
    // Current allocation order is:
    //     report -> target_info -> report_data
    //
    // target_info: 512-byte aligned, 512-byte length
    // report_data: 128-byte aligned,  64-byte length
    // report:      512-byte aligned, 432-byte length
    //
    size_t size = ROUND_TO(sizeof(sgx_target_info_t), TARGET_INFO_ALIGN_SIZE) +
                  ROUND_TO(sizeof(sgx_report_data_t), REPORT_DATA_ALIGN_SIZE) +
                  ROUND_TO(sizeof(sgx_report_t), REPORT_ALIGN_SIZE);
    size += MAX(MAX(TARGET_INFO_ALIGN_SIZE, REPORT_DATA_ALIGN_SIZE), REPORT_ALIGN_SIZE) - 1;

    void *buffer = malloc(size);
    if(buffer == NULL)
    {
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    memset(buffer, 0, size);
    size_t buf_ptr = reinterpret_cast<size_t>(buffer);

    buf_ptr = ROUND_TO(buf_ptr, REPORT_ALIGN_SIZE);
    sgx_report_t *tmp_report = reinterpret_cast<sgx_report_t *>(buf_ptr);
    buf_ptr += sizeof(*tmp_report);

    buf_ptr = ROUND_TO(buf_ptr, TARGET_INFO_ALIGN_SIZE);
    sgx_target_info_t *tmp_target_info = reinterpret_cast<sgx_target_info_t *>(buf_ptr);
    buf_ptr += sizeof(*tmp_target_info);

    buf_ptr = ROUND_TO(buf_ptr, REPORT_DATA_ALIGN_SIZE);
    sgx_report_data_t *tmp_report_data = reinterpret_cast<sgx_report_data_t *>(buf_ptr);

    // Copy data from user buffer to the aligned memory
    if(target_info)
    {
        memcpy_s(tmp_target_info, sizeof(*tmp_target_info), target_info, sizeof(*target_info));
    }
    if(report_data)
    {
        memcpy_s(tmp_report_data, sizeof(*tmp_report_data), report_data, sizeof(*report_data));
    }

    // Do EREPORT
    do_ereport(tmp_target_info, tmp_report_data, tmp_report);

    // Copy data to the user buffer
    memcpy_s(report, sizeof(*report), tmp_report, sizeof(*tmp_report));

    // cleanup
    memset_s(buffer, size, 0, size);
    free(buffer);

    return SGX_SUCCESS;
}



