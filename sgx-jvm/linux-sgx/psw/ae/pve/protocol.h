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


/**
 * File: protocol.h
 * Description: Header file of interface functions for provision enclave
 *
 * Function to generate ProvMsg1, ProvMsg3 and function to process ProvMsg2/ProvMsg4
 */

#ifndef _PROTOCOL_H
#define _PROTOCOL_H
#include "provision_msg.h"
#include "helper.h"

/*Function to generate ProvMsg1 required data inside PvE
     return PVEC_SUCCESS on success
     return other value to indicate correpondent error*/
pve_status_t gen_prov_msg1_data(const sgx_target_info_t& pce_target_info,
                           const extended_epid_group_blob_t& xegb,
                           const signed_pek_t& pek,                       /*input the signed PEK*/
                           sgx_report_t& pek_report);                     /*output report of PEK for PCE to verify it*/

/*Function to process ProvMsg2 data and generate ProvMsg3 data in PvE
    return PVEC_SUCCESS on success
    return other value to indicate correpondent error*/
pve_status_t proc_prov_msg2_data(const proc_prov_msg2_blob_input_t *msg2_blob_input, /*Input data from ProvMsg2*/
                            uint8_t performance_rekey_used,                          /*1 if performance rekey*/
                            const external_memory_byte_t *sigrl,                     /*optional sigrl inside external memory*/
                            uint32_t sigrl_size,
                            gen_prov_msg3_output_t *msg3_output,                     /*the output buffer for fixed part of msg3 data*/
                            external_memory_byte_t *emp_epid_sig,                    /*optional epid signature buffer for output*/
                            uint32_t epid_sig_buffer_size);                          /*input length of buffer 'emp_epid_sig'*/

/*Function to process ProvMsg4 and generate new EPID BLOB data
    return PVEC_SUCCESS on success
    return other value to indicate error*/
pve_status_t proc_prov_msg4_data(const proc_prov_msg4_input_t *msg4_input,  /*Input data from ProvMsg4*/
                            sgx_sealed_data_t *epid_blob);                  /*Output the EPID BLOB, the size of the buffer should be at least SGX_TRUSTED_EPID_BLOB_SIZE_SDK*/

/*Function to generate endpoint selection msg1 for SGX Provisioning*/
pve_status_t gen_es_msg1_data(gen_endpoint_selection_output_t *es_selector);

#endif
