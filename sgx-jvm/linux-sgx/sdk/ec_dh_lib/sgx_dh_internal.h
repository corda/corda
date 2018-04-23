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


#ifndef _SGX_DH_INTERNAL_H_
#define _SGX_DH_INTERNAL_H_
 
#include "sgx.h"
#include "sgx_defs.h"
#include "sgx_ecp_types.h"
#include "sgx_dh.h"
#include "arch.h"

#pragma pack(push, 1)
 
typedef enum _sgx_dh_session_state_t
{
    SGX_DH_SESSION_STATE_ERROR,
    SGX_DH_SESSION_STATE_RESET,
    SGX_DH_SESSION_RESPONDER_WAIT_M2,
    SGX_DH_SESSION_INITIATOR_WAIT_M1,
    SGX_DH_SESSION_INITIATOR_WAIT_M3,
    SGX_DH_SESSION_ACTIVE
} sgx_dh_session_state_t;

typedef struct _sgx_dh_responder_t{
    sgx_dh_session_state_t   state;	   /*Responder State Machine State */
    sgx_ec256_private_t prv_key;             /* 256bit EC private key */
    sgx_ec256_public_t  pub_key;             /* 512 bit EC public key */
} sgx_dh_responder_t;
 
typedef struct _sgx_dh_initator_t{
    sgx_dh_session_state_t state;    /* Initiator State Machine State */
    union{
        sgx_ec256_private_t prv_key;    /* 256bit EC private key */
        sgx_key_128bit_t smk_aek;    /* 128bit SMK or AEK. Depending on the State */
    };
    sgx_ec256_public_t pub_key;    /* 512 bit EC public key */
    sgx_ec256_public_t peer_pub_key;    /* 512 bit EC public key from the Responder */
    sgx_ec256_dh_shared_t shared_key;
} sgx_dh_initator_t;

typedef struct _sgx_internal_dh_session_t{
    sgx_dh_session_role_t role;             /* Initiator or Responder */
    union{
        sgx_dh_responder_t responder;
        sgx_dh_initator_t  initiator;
    };
} sgx_internal_dh_session_t;

se_static_assert(sizeof(sgx_internal_dh_session_t) == SGX_DH_SESSION_DATA_SIZE); /*size mismatch on sgx_internal_dh_session_t and sgx_dh_session_t*/

#pragma pack(pop)

#endif



