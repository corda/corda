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

#ifndef _PLATFORM_INFO_BLOB_H_
#define _PLATFORM_INFO_BLOB_H_

#include <stdint.h>
#include "epid_pve_type.h"
#include "sgx_tcrypto.h"


#pragma pack(push, 1)

#define ISVSVN_SIZE 2
#define PSDA_SVN_SIZE 4
#define RSA_SHA256_SIZE 256

typedef uint8_t tcb_psvn_t[PSVN_SIZE];
typedef uint8_t psda_svn_t[PSDA_SVN_SIZE];
typedef uint8_t pse_isvsvn_t[ISVSVN_SIZE];

/* Masks for sgx_epid_group_flags*/
const uint8_t QE_EPID_GROUP_REVOKED = 0x01;
const uint8_t PERF_REKEY_FOR_QE_EPID_GROUP_AVAILABLE = 0x02;
const uint8_t QE_EPID_GROUP_OUT_OF_DATE = 0x04;

/* Masks for sgx_tcb_evaluation_flags*/
const uint16_t QUOTE_CPUSVN_OUT_OF_DATE = 0x0001;
const uint16_t QUOTE_ISVSVN_QE_OUT_OF_DATE = 0x0002;
const uint16_t QUOTE_ISVSVN_PCE_OUT_OF_DATE = 0x0004;

/* Masks for sgx_pse_evaluation_flags
   PS_SEC_PROP_DESC.PSE_ISVSVN is out of date*/
const int PSE_ISVSVN_OUT_OF_DATE = 0x0001;
/* CSME EPID 1.1 group identified by PS_SEC_PROP_DESC. PS_HW_GID has been revoked*/
const int EPID_GROUP_ID_BY_PS_HW_GID_REVOKED = 0x0002;
/* PSDA SVN indicated in PS_SEC_PROP_DESC.PS_HW_SEC_INFO is out of date*/
const int SVN_FROM_PS_HW_SEC_INFO_OUT_OF_DATE = 0x0004;
/* CSME EPID 1.1 SigRL version indicated in PS_SEC_PROP_DESC. PS_HW_SIG_RLver is out of date*/
const int SIGRL_VER_FROM_PS_HW_SIG_RLVER_OUT_OF_DATE = 0x0008;
/* CSME EPID 1.1 PrivRL version indicated in PS_SEC_PROP_DESC. PS_HW_PrivKey_RLver is out of date*/
const int PRIVRL_VER_FROM_PS_HW_PRV_KEY_RLVER_OUT_OF_DATE = 0x0010;



typedef struct _platform_info_blob_wrapper_t
{
    bool valid_info_blob;
    struct
    {
        uint8_t sgx_epid_group_flags;
        uint8_t sgx_tcb_evaluation_flags[2];
        uint8_t pse_evaluation_flags[2];
        tcb_psvn_t latest_equivalent_tcb_psvn;
        pse_isvsvn_t latest_pse_isvsvn;
        psda_svn_t latest_psda_svn;
        uint32_t xeid;
        GroupId gid;
        sgx_ec256_signature_t signature;
    } platform_info_blob;
} platform_info_blob_wrapper_t;

#pragma pack(pop)

ae_error_t pib_verify_signature(platform_info_blob_wrapper_t& piBlobWrapper);

#endif
