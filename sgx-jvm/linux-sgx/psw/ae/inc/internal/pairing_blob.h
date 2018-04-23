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

#ifndef _PAIRING_BLOB_H_
#define _PAIRING_BLOB_H_

#include "sgx_tseal.h"
#include "pse_pr_types.h"

#include "aeerror.h"
#include <stdint.h>

typedef uint8_t     UINT8;
typedef uint32_t    UINT32;

typedef UINT8 SIGMA_ID[32];
typedef UINT8 SIGMA_MAC_KEY[16];
typedef UINT8 SIGMA_SECRET_KEY[16];

// BLOB TYPE - Sealblob Type definition is per {MRSIGNER, ProdID} pair
#define PSE_SEAL_PAIRING_BLOB       0
#define PSE_PAIRING_BLOB_VERSION    1

#pragma pack(1)
typedef struct          // for SunrisePoint from TaskInfo of SIGMA1.1 message:
{
    uint32_t taskId;    // byte[ 0- 3] ME_TASK_INFO.TaskID, for SunrisePoint should be 8
    uint32_t rsvd1;     // byte[ 4- 7] For SKL/GLM time frame, should be 0
    uint32_t psdaId;    // byte[ 8-11] PSDA ID, mapped from the PSDA Applet ID in ME_TASK_INFO. For SKL/GLM timeframe, should be 1
    uint32_t psdaSvn;   // byte[12-15] PSDA SVN from ME_TASK_INFO
    uint8_t rsvd2[76];  // byte[16-91] Reserved, MBZ
} PS_HW_SEC_INFO;

typedef struct _cse_security_info_t     // PS_HW_SEC_PROP_DESC
{
    uint32_t        ps_hw_sec_info_type;        // DESC_TYPE
    uint32_t        ps_hw_gid;                  // PS_HW_GID
    uint32_t        ps_hw_privkey_rlversion;    // PS_HW_PrivKey_RLver
    uint32_t        ps_hw_sig_rlversion;        // PS_HW_SIG_RLver
    uint8_t         ps_hw_CA_id[20];            // PS_HW_CA_ID
    PS_HW_SEC_INFO  ps_hw_sec_info;             // PS_HW_SEC_INFO
} cse_security_info_t;

typedef struct _se_secret_pairing_data_t
{
    SHA256_HASH         Id_pse;
    SHA256_HASH         Id_cse;
    SIGMA_MAC_KEY       mk;
    SIGMA_SECRET_KEY    sk;
    SIGMA_SECRET_KEY    pairingID;  // old_sk used for repairing check
    Nonce128_t          pairingNonce;
    EcDsaPrivKey        VerifierPrivateKey;
} se_secret_pairing_data_t;

typedef struct _se_plaintext_pairing_data_t
{
    uint8_t             pse_instance_id[16];    // instance id for sigma 1.1 session between pse and csme 
    uint8_t             seal_blob_type;         // PSE_SEAL_PAIRING_DATA_BLOB
    uint8_t             pairing_blob_version;   // PSE_PAIRING_DATA_BLOB_VERSION
    cse_security_info_t cse_sec_prop;
} se_plaintext_pairing_data_t;

// Pairing blob; only cse_sec_prop is usable outside of enclave
typedef struct _pairing_blob_t
{
    struct
    {
        UINT8 header[sizeof(sgx_sealed_data_t)];
        UINT8 encrypted_payload[sizeof(se_secret_pairing_data_t)];
    } sealed_pairing_data;
    se_plaintext_pairing_data_t plaintext;
} pairing_blob_t;

#define PAIRING_BLOB_PLAINTEXT_OFFSET(pairing_blob_buf)  ((reinterpret_cast<sgx_sealed_data_t *>(pairing_blob_buf))->plain_text_offset)

#define PAIRING_BLOB_PLAINTEXT_PTR(pairing_blob_buf)     ((reinterpret_cast<se_plaintext_pairing_data_t *>((uint8_t*)pairing_blob_buf) \
                                                           + sizeof(sgx_sealed_data_t) + PAIRING_BLOB_PLAINTEXT_OFFSET(pairing_blob_buf))

#pragma pack()


#endif
