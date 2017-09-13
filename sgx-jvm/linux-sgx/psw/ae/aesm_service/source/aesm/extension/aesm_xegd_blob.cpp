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
#include <stdio.h>
#include "aesm_xegd_blob.h"
#include "endpoint_select_info.h"
#include "internal/se_memcpy.h"
#include "oal/oal.h"
#include "byte_order.h"
#include "aesm_epid_blob.h"

ae_error_t XEGDBlob::verify_xegd_by_xgid(uint32_t xgid)
{
    extended_epid_group_blob_t blob;
    if (xgid == DEFAULT_EGID){//always return success for default xgid
        return AE_SUCCESS;
    }
    uint32_t data_size = sizeof(blob);
    ae_error_t ae_ret = aesm_read_data(FT_PERSISTENT_STORAGE, EXTENDED_EPID_GROUP_BLOB_INFO_FID, reinterpret_cast<uint8_t *>(&blob), &data_size, xgid);
    if (AE_SUCCESS != ae_ret){
        return ae_ret;
    }
    if (data_size != sizeof(blob)){
        return OAL_CONFIG_FILE_ERROR;
    }
    ae_ret = verify(blob);
    return ae_ret;
}

ae_error_t XEGDBlob::read(extended_epid_group_blob_t& blob)
{
    ae_error_t ae_ret = AE_FAILURE;
    if(status == not_initialized){
        uint32_t data_size = sizeof(blob_cache);
        if ((ae_ret = aesm_read_data(FT_PERSISTENT_STORAGE, EXTENDED_EPID_GROUP_BLOB_INFO_FID, reinterpret_cast<uint8_t *>(&blob_cache), &data_size, AESMLogic::get_active_extended_epid_group_id())) != AE_SUCCESS){
            goto CLEANUP_READ_FILE;
        }
        if (data_size != sizeof(blob_cache)){
            ae_ret = OAL_CONFIG_FILE_ERROR;
            goto CLEANUP_READ_FILE;
        }
        ae_ret = verify(blob_cache);
        if (AE_SUCCESS != ae_ret){
            AESM_DBG_ERROR("signature error in XEGD file");
            goto CLEANUP_READ_FILE;
        }
        status = update_to_date;
CLEANUP_READ_FILE:
        if (status != update_to_date){
            if (AESMLogic::get_active_extended_epid_group_id() == DEFAULT_EGID){
                memset(&blob_cache, 0, sizeof(blob_cache));//indicate other part to use default data
                status = update_to_date;
            }
            else{
                status = not_available;//xegd blob lost
            }
        }
    }
    if(status == update_to_date){
        if(memcpy_s(&blob, sizeof(blob), &blob_cache, sizeof(blob_cache))!=0){
            status = not_available; //invalid
            ae_ret = AE_FAILURE;
        }else{
            ae_ret = AE_SUCCESS;
        }
    }
    return ae_ret;
}

ae_error_t aesm_verify_xegb(const extended_epid_group_blob_t& signed_xegb);

ae_error_t XEGDBlob::verify(const extended_epid_group_blob_t& signed_xegb)
{
    ae_error_t aesm_result = aesm_verify_xegb(signed_xegb);
    if (AE_SUCCESS != aesm_result)
    {
        AESM_DBG_ERROR("Extended EPID Group Blob Signature verifcation not passed:%d", aesm_result);
        return aesm_result;
    }
    return aesm_result;
}

