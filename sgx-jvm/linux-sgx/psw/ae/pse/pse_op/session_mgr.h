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

 
#ifndef SESSION_MGR_H
#define SESSION_MGR_H

#include "pse_inc.h"
#include "pse_types.h"
#include "t_pairing_blob.h"
#include "sgx_dh.h"

//session array parameters
#define SESSION_CONNECTION  128
#define SESSION_IDLE_TIME   (uint64_t)1000*60  //one minute in milliseconds
#define MAX_INST_PER_ENCLAVE 32
#define INVADE_SESSION_ID   -1
//session status
#define SESSION_CLOSE       0x0
#define SESSION_IN_PROGRESS 0x1
#define SESSION_ACTIVE      0x2
//session counter
#define SESSION_COUNTER_MAX ((uint32_t)-1)

#define DEFAULT_VMC_ACCESS_CTL_ATTRI_MASK 0xFFFFFFFFFFFFFFCB

#pragma pack(push, 1)
typedef struct _isv_attributes_t
{
    uint64_t           tick_count;     //ULONGLONG WINAPI GetTickCount64(void)
    sgx_isv_svn_t      isv_svn;
    sgx_prod_id_t      isv_prod_id;
    sgx_attributes_t   attribute;
    sgx_measurement_t  mr_signer;      // The value of the enclave SIGNER's measurement
    sgx_measurement_t  mr_enclave;     // The value of the enclave's measurement
}isv_attributes_t;

typedef struct _pse_session_t
{
    uint32_t          sid;
    uint32_t          state;
    union
    {
        struct 
        {
            sgx_dh_session_t dh_session;
        }in_progress;

        struct
        {
            sgx_key_128bit_t AEK;
            uint32_t   counter;
        }active;
    };
    uint32_t isv_attributes_len;
    isv_attributes_t isv_attributes;
}pse_session_t;

typedef struct _eph_session_t
{
    uint32_t          seq_num;
    uint32_t          sid;
    uint32_t          state;
    sgx_key_128bit_t      TSK;
    sgx_key_128bit_t      TMK;
}eph_session_t;

#pragma pack(pop)

pse_op_error_t pse_create_session(
    uint64_t tick,
    uint32_t &id, 
    pse_dh_msg1_t &dh_msg1);

pse_op_error_t pse_exchange_report(
    uint64_t tick,
    uint32_t sid, 
    const sgx_dh_msg2_t &dh_msg2, 
    pse_dh_msg3_t &dh_msg3);

pse_op_error_t pse_close_session(uint32_t sid);

pse_session_t* sid2session(uint32_t sid);

pse_op_error_t ephemeral_session_m2m3(
    pairing_blob_t* sealed_blob,
    const pse_cse_msg2_t &pse_cse_msg2, 
    pse_cse_msg3_t &pse_cse_msg3);

pse_op_error_t ephemeral_session_m4(const pse_cse_msg4_t &pse_cse_msg4);

bool copy_global_pairing_nonce(uint8_t* target_buffer);
void free_session(pse_session_t* session);
bool is_eph_session_active();
bool is_isv_session_valid(pse_session_t* session);
uint32_t get_session_seq_num(pse_session_t* session);
void set_session_seq_num(pse_session_t* session, uint32_t seq_num);
void update_session_tick_count(pse_session_t* session, uint64_t new_tick_count);
void copy_pse_instance_id(uint8_t* pse_instance_id);
#endif
