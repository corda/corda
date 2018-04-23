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

#ifndef _AESM_EPID_BLOB_H_
#define _AESM_EPID_BLOB_H_
/*File to declare class for EPIDBlob*/
#include "se_types.h"
#include "sgx_tseal.h"
#include "oal/oal.h"
#include "AEClass.h"
#include "epid_pve_type.h"
#include "assert.h"
#include "aeerror.h"
#include "se_thread.h"
#include "provision_msg.h"
#include "internal/se_rwlock.h"

typedef struct _epid_blob_with_cur_psvn_t{
    uint8_t trusted_epid_blob[SGX_TRUSTED_EPID_BLOB_SIZE_SDK];
    bk_platform_info_t   cur_pi;
}epid_blob_with_cur_psvn_t;

typedef struct _epid_blob_v2_with_cur_psvn_t{
    uint8_t trusted_epid_blob[SGX_TRUSTED_EPID_BLOB_SIZE_SIK];
    bk_platform_info_t   cur_pi;
}epid_blob_v2_with_cur_psvn_t;

#define SGX_EPID_BLOB_SIZE sizeof(epid_blob_with_cur_psvn_t)

class EPIDBlob: public Singleton<EPIDBlob>{
    CLASS_UNCOPYABLE(EPIDBlob)
    epid_blob_with_cur_psvn_t blob_cache;
    friend class Singleton<EPIDBlob>;
    enum EPIDBlobStatus {not_initialized=0, update_to_date=1, not_available=2} status;
    EPIDBlob(){memset(&blob_cache, 0 ,sizeof(blob_cache));status = not_initialized;}
public:
    ae_error_t read(epid_blob_with_cur_psvn_t& blob);
    ae_error_t write(const epid_blob_with_cur_psvn_t& blob);
    ae_error_t get_sgx_gid(uint32_t* pgid);/*get little endian gid from epid data blob*/
    ae_error_t get_extended_epid_group_id(uint32_t* pxeid);//get little endian extended_epid_group_id from epid data blob
    ae_error_t remove(void);
};
#endif/*_AESM_EPID_BLOB_H_*/

