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
#include "aesm_epid_blob.h"
#include "internal/se_memcpy.h"
#include "oal/oal.h"
#include "byte_order.h"


ae_error_t EPIDBlob::read(epid_blob_with_cur_psvn_t& blob)
{
    ae_error_t ae_ret = AE_FAILURE;
    if(status == not_initialized){
        uint32_t data_size = sizeof(blob_cache);
        if((ae_ret=aesm_read_data(FT_PERSISTENT_STORAGE, EPID_DATA_BLOB_FID, reinterpret_cast<uint8_t *>(&blob_cache), &data_size))!=AE_SUCCESS){
            goto CLEANUP_READ_FILE;
        }

        if (data_size != sizeof(blob_cache)&&
            data_size != sizeof(epid_blob_v2_with_cur_psvn_t)){//support previous version epid blob too
            ae_ret = QE_EPIDBLOB_ERROR;
            goto CLEANUP_READ_FILE;
        }
        if(data_size == sizeof(epid_blob_v2_with_cur_psvn_t)){
            //move bk_platform_info_t so that all other parts of aesm service could access the field consistently
            epid_blob_v2_with_cur_psvn_t *old_format = (epid_blob_v2_with_cur_psvn_t *)&blob_cache;
            memmove(&blob_cache.cur_pi, &old_format->cur_pi, sizeof(old_format->cur_pi));
        }
        status = update_to_date;
CLEANUP_READ_FILE:
        if(status!=update_to_date)
            status = not_available;//epid blob lost
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

ae_error_t EPIDBlob::write(const epid_blob_with_cur_psvn_t& blob)
{
    ae_error_t ae_ret = AE_FAILURE;
    status = not_available;
    if((ae_ret = aesm_write_data(FT_PERSISTENT_STORAGE, EPID_DATA_BLOB_FID,reinterpret_cast<const uint8_t *>(&blob), sizeof(blob)))!=AE_SUCCESS)
    {
        AESM_DBG_WARN("fail to write epid blob to persistent storage:%d",ae_ret);
        AESM_LOG_WARN("%s",g_event_string_table[SGX_EVENT_EPID_BLOB_PERSISTENT_STROAGE_FAILURE]);
        // continue to update cache
    }
    if(memcpy_s(&blob_cache, sizeof(blob_cache), &blob, sizeof(blob))!=0){
        status = not_available; //invalid status
        ae_ret = AE_FAILURE;
    }else{
        status = update_to_date;
        ae_ret = AE_SUCCESS;
    }
    return ae_ret;
}

//
// get_sgx_gid
//
// get sgx gid from epid blob, specifically from stored group cert in epid blob
//
// inputs
//
// pgid: pointer to gid
//
// outputs
//
// *pgid: gid
// status
//
ae_error_t EPIDBlob::get_sgx_gid(uint32_t* pgid) 
{
    ae_error_t aesm_result = AE_SUCCESS;
    epid_blob_with_cur_psvn_t epid_blob;
    sgx_sealed_data_t *sealed_epid = reinterpret_cast<sgx_sealed_data_t *>(epid_blob.trusted_epid_blob);

    if (NULL == pgid)
        return AE_INVALID_PARAMETER;
    //
    // get the epid blob
    //
    aesm_result = this->read(epid_blob);
    if (AE_SUCCESS == aesm_result) {
        //
        // get the gid
        //
        uint32_t plain_text_offset = sealed_epid->plain_text_offset;
        se_plaintext_epid_data_sdk_t* plain_text = reinterpret_cast<se_plaintext_epid_data_sdk_t *>(epid_blob.trusted_epid_blob + sizeof(sgx_sealed_data_t) + plain_text_offset);
         
         if(memcpy_s(pgid, sizeof(*pgid), &plain_text->epid_group_cert.gid, sizeof(plain_text->epid_group_cert.gid)))	//read gid from EPID Data blob
         {
             AESM_DBG_ERROR("memcpy_s failed");
             aesm_result = AE_FAILURE;
         }
         else
         {
            //
            // return little-endian
            //
            *pgid = _htonl(*pgid);
            AESM_DBG_TRACE(": get gid %d from epid blob", *pgid);
        }
    }
    else {
        aesm_result = AE_INVALID_PARAMETER;
    }

    return aesm_result;
}


ae_error_t EPIDBlob::get_extended_epid_group_id(uint32_t* pxeid)
{
    ae_error_t aesm_result = AE_SUCCESS;
    epid_blob_with_cur_psvn_t epid_blob;
    sgx_sealed_data_t *sealed_epid = reinterpret_cast<sgx_sealed_data_t *>(epid_blob.trusted_epid_blob);

    if (NULL == pxeid)
        return AE_INVALID_PARAMETER;
    //
    // get the epid blob
    //
    aesm_result = this->read(epid_blob);
    if (AE_SUCCESS == aesm_result) {
        //
        // get the xeid
        //
        uint32_t plain_text_offset = sealed_epid->plain_text_offset;
        se_plaintext_epid_data_sdk_t* plain_text_new = reinterpret_cast<se_plaintext_epid_data_sdk_t*>(epid_blob.trusted_epid_blob + sizeof(sgx_sealed_data_t)+plain_text_offset);
        se_plaintext_epid_data_sik_t *plain_text_old = reinterpret_cast<se_plaintext_epid_data_sik_t *>(plain_text_new);
        switch (plain_text_new->epid_key_version)
        {
        case EPID_KEY_BLOB_VERSION_SDK:
            
            if (memcpy_s(pxeid, sizeof(*pxeid), &plain_text_new->xeid, sizeof(plain_text_new->xeid)))	//read extended_epid_group_id from EPID Data blob
            {
                AESM_DBG_ERROR("memcpy_s failed");
                aesm_result = AE_FAILURE;
            }
            else
            {
                //
                // return little-endian
                //
                AESM_DBG_TRACE(": get gid %d from epid blob", *pxeid);
                aesm_result = AE_SUCCESS;
            }
            break;
        case EPID_KEY_BLOB_VERSION_SIK:
            
            if (memcpy_s(pxeid, sizeof(*pxeid), &plain_text_old->xeid, sizeof(plain_text_old->xeid)))   //read extended_epid_group_id from EPID Data blob
            {
                AESM_DBG_ERROR("memcpy_s failed");
                aesm_result = AE_FAILURE;
            }
            else
            {
                //
                // return little-endian
                //
                AESM_DBG_TRACE(": get gid %d from epid blob", *pxeid);
                aesm_result = AE_SUCCESS;
            }
            break;
        default:
            AESM_DBG_ERROR("unexpected epid_key_version");
            aesm_result = AE_FAILURE;
            break;
        }
    }
    return aesm_result;

}


ae_error_t EPIDBlob::remove(void)
{
    ae_error_t ae_ret = AE_FAILURE;
    status = not_available;
    if ((ae_ret = aesm_remove_data(FT_PERSISTENT_STORAGE, EPID_DATA_BLOB_FID)) != AE_SUCCESS){
        status = not_initialized;
        return ae_ret;
    }
    status = not_initialized;
    return AE_SUCCESS;
}
