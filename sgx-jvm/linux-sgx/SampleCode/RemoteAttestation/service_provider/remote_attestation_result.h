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

#ifndef _REMOTE_ATTESTATION_RESULT_H_
#define _REMOTE_ATTESTATION_RESULT_H_

#include <stdint.h>

#ifdef  __cplusplus
extern "C" {
#endif

#define SAMPLE_MAC_SIZE             16  /* Message Authentication Code*/
                                        /* - 16 bytes*/
typedef uint8_t                     sample_mac_t[SAMPLE_MAC_SIZE];

#ifndef SAMPLE_FEBITSIZE
    #define SAMPLE_FEBITSIZE        256
#endif

#define SAMPLE_NISTP256_KEY_SIZE    (SAMPLE_FEBITSIZE/ 8 /sizeof(uint32_t))

typedef struct sample_ec_sign256_t
{
    uint32_t x[SAMPLE_NISTP256_KEY_SIZE];
    uint32_t y[SAMPLE_NISTP256_KEY_SIZE];
} sample_ec_sign256_t;

#pragma pack(push,1)

#define SAMPLE_SP_TAG_SIZE          16

typedef struct sp_aes_gcm_data_t {
    uint32_t        payload_size;       /*  0: Size of the payload which is*/
                                        /*     encrypted*/
    uint8_t         reserved[12];       /*  4: Reserved bits*/
    uint8_t         payload_tag[SAMPLE_SP_TAG_SIZE];
                                        /* 16: AES-GMAC of the plain text,*/
                                        /*     payload, and the sizes*/
    uint8_t         payload[];          /* 32: Ciphertext of the payload*/
                                        /*     followed by the plain text*/
} sp_aes_gcm_data_t;


#define ISVSVN_SIZE 2
#define PSDA_SVN_SIZE 4
#define GID_SIZE 4
#define PSVN_SIZE 18

/* @TODO: Modify at production to use the values specified by an Production*/
/* attestation server API*/
typedef struct ias_platform_info_blob_t
{
     uint8_t sample_epid_group_status;
     uint16_t sample_tcb_evaluation_status;
     uint16_t pse_evaluation_status;
     uint8_t latest_equivalent_tcb_psvn[PSVN_SIZE];
     uint8_t latest_pse_isvsvn[ISVSVN_SIZE];
     uint8_t latest_psda_svn[PSDA_SVN_SIZE];
     uint8_t performance_rekey_gid[GID_SIZE];
     sample_ec_sign256_t signature;
} ias_platform_info_blob_t;


typedef struct sample_ra_att_result_msg_t {
    ias_platform_info_blob_t    platform_info_blob;
    sample_mac_t                mac;    /* mac_smk(attestation_status)*/
    sp_aes_gcm_data_t           secret;
} sample_ra_att_result_msg_t;

#pragma pack(pop)

#ifdef  __cplusplus
}
#endif

#endif
