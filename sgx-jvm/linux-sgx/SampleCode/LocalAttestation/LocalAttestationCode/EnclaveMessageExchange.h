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


#include "datatypes.h"
#include "sgx_eid.h"
#include "sgx_trts.h"
#include <map>
#include "dh_session_protocol.h"

#ifndef LOCALATTESTATION_H_
#define LOCALATTESTATION_H_

#ifdef __cplusplus
extern "C" {
#endif

uint32_t SGXAPI create_session(sgx_enclave_id_t src_enclave_id, sgx_enclave_id_t dest_enclave_id, dh_session_t *p_session_info);
uint32_t SGXAPI send_request_receive_response(sgx_enclave_id_t src_enclave_id, sgx_enclave_id_t dest_enclave_id, dh_session_t *p_session_info, char *inp_buff, size_t inp_buff_len, size_t max_out_buff_size, char **out_buff, size_t* out_buff_len);
uint32_t SGXAPI close_session(sgx_enclave_id_t src_enclave_id, sgx_enclave_id_t dest_enclave_id);

#ifdef __cplusplus
}
#endif

#endif
