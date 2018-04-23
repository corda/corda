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

#ifndef _SGX_TAE_SERVICE_H_
#define _SGX_TAE_SERVICE_H_

/**
* File:
*		sgx_tae_service.h
*Description:
*  header for trusted AE support library.
*  ADD from path/sgx_tae_service.edl import *; to your edl file
*  to use sgx_tae_service.lib
*/

#include "sgx.h"
#include "sgx_defs.h"


#ifdef  __cplusplus
extern "C" {
#endif

#pragma pack(push, 1)

typedef uint64_t sgx_time_t;

typedef uint8_t sgx_time_source_nonce_t[32];

#define SGX_MC_UUID_COUNTER_ID_SIZE 3
#define SGX_MC_UUID_NONCE_SIZE      13
typedef struct _mc_uuid {
    uint8_t counter_id[SGX_MC_UUID_COUNTER_ID_SIZE];
    uint8_t nonce[SGX_MC_UUID_NONCE_SIZE];
} sgx_mc_uuid_t;

/* fixed length to align with internal structure */
typedef struct _ps_sec_prop_desc
{
    uint8_t  sgx_ps_sec_prop_desc[256];
} sgx_ps_sec_prop_desc_t;

typedef struct _ps_sec_prop_desc_ex
{
    sgx_ps_sec_prop_desc_t  ps_sec_prop_desc;
    sgx_measurement_t pse_mrsigner;
    sgx_prod_id_t pse_prod_id;
    sgx_isv_svn_t pse_isv_svn;
} sgx_ps_sec_prop_desc_ex_t;

#pragma pack(pop)

/* create a session, call it before using Platform Service */
sgx_status_t SGXAPI sgx_create_pse_session(void);

/* close a created session, call it after finishing using Platform Service */
sgx_status_t SGXAPI sgx_close_pse_session(void);

/* get a data structure describing the Security Property of the Platform Service */
sgx_status_t SGXAPI sgx_get_ps_sec_prop(sgx_ps_sec_prop_desc_t* security_property);

/* get a data structure describing the Security Property of the Platform Service */
sgx_status_t SGXAPI sgx_get_ps_sec_prop_ex(sgx_ps_sec_prop_desc_ex_t* security_property);

/* provides the trusted platform current time */
sgx_status_t SGXAPI sgx_get_trusted_time(
    sgx_time_t* current_time,
    sgx_time_source_nonce_t* time_source_nonce
    );

/* monotonic counter policy */
#define SGX_MC_POLICY_SIGNER  0x1
#define SGX_MC_POLICY_ENCLAVE 0x2
/* create a monotonic counter using given policy(SIGNER 0x1 or ENCLAVE 0x2) and attribute_mask */
sgx_status_t SGXAPI sgx_create_monotonic_counter_ex(
    uint16_t  owner_policy,
    const sgx_attributes_t* owner_attribute_mask,
    sgx_mc_uuid_t* counter_uuid,
    uint32_t* counter_value
    );

/* create a monotonic counter using default policy SIGNER and default attribute_mask */
sgx_status_t SGXAPI sgx_create_monotonic_counter(
    sgx_mc_uuid_t* counter_uuid,
    uint32_t* counter_value
    );

/* destroy a specified monotonic counter */
sgx_status_t SGXAPI sgx_destroy_monotonic_counter(const sgx_mc_uuid_t* counter_uuid);

/* increment a specified monotonic counter by 1 */
sgx_status_t SGXAPI sgx_increment_monotonic_counter(
    const sgx_mc_uuid_t* counter_uuid,
    uint32_t* counter_value
    );

/* read a specified monotonic counter */
sgx_status_t SGXAPI sgx_read_monotonic_counter(
    const sgx_mc_uuid_t* counter_uuid,
    uint32_t* counter_value
    );

#ifdef  __cplusplus
}
#endif

#endif
