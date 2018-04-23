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

#ifndef _PVE_CLASS_H_
#define _PVE_CLASS_H_
#include "AEClass.h"
#include "provision_msg.h"
#include "pve_logic.h"
#include "ae_debug_flag.hh"

class CPVEClass: public SingletonEnclave<CPVEClass>
{
    friend class Singleton<CPVEClass>;
    friend class SingletonEnclave<CPVEClass>;
    static aesm_enclave_id_t get_enclave_fid(){return PVE_ENCLAVE_FID;}
protected:
    CPVEClass(){};
    ~CPVEClass(){};
    virtual void before_enclave_load();
    virtual int get_debug_flag() { return AE_DEBUG_FLAG;}

    uint32_t gen_prov_msg1_data(
        const signed_pek_t *pek,
        const sgx_target_info_t *pce_target_info,
        sgx_report_t *pek_report);

    uint32_t proc_prov_msg2_data(
        const proc_prov_msg2_blob_input_t* input,
        bool performance_rekey_used,
        const uint8_t* sigrl,
        uint32_t sigrl_size,
        gen_prov_msg3_output_t* msg3_fixed_output,
        uint8_t* epid_sig,
        uint32_t epid_sig_buffer_size);

    uint32_t proc_prov_msg4_data(
        const proc_prov_msg4_input_t* msg4_input,
        proc_prov_msg4_output_t* data_blob);

public:
    uint32_t gen_es_msg1_data(
        gen_endpoint_selection_output_t* es_output);

    uint32_t gen_prov_msg1(pve_data_t& pve_data,
        uint8_t* msg1,
        uint32_t msg1_size);//input output parameter, input for back_retrieval and output only for other cases

    uint32_t proc_prov_msg2(
        pve_data_t& data,
        const uint8_t*  msg2,
        uint32_t msg2_size,
        const uint8_t*  epid_blob,
        uint32_t  blob_size,
        uint8_t*  msg3,
        uint32_t msg3_size);

    uint32_t proc_prov_msg4(
        const pve_data_t& data,
        const uint8_t* msg4,
        uint32_t msg4_size,
        uint8_t* data_blob,
        uint32_t blob_size);

    uint32_t gen_es_msg1(
        uint8_t *msg,
        uint32_t msg_size,
        const gen_endpoint_selection_output_t& es1_output);

    uint32_t proc_es_msg2(
        const uint8_t *msg,
        uint32_t msg_size,
        char server_url[MAX_PATH],
        uint16_t& ttl,
        const uint8_t xid[XID_SIZE],
        uint8_t rsa_signature[RSA_3072_KEY_BYTES],
        signed_pek_t& pek);
};
#endif

