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

#ifndef _NETWORK_WRAPPER_H_
#define _NETWORK_WRAPPER_H_
#include "sgx_urts.h"
#include "aesm_error.h"
#include "arch.h"
#include "aeerror.h"
#include "tlv_common.h"
#include "se_thread.h"
#include "oal/oal.h"
#include "se_wrapper.h"

/*Class for network interface inside AESM*/
class AESMNetworkEncoding{
protected:
    static ae_error_t aesm_send_recv_msg_encoding_internal(const char *url, const uint8_t * msg, uint32_t msg_size, uint8_t* &resp_msg, uint32_t& resp_size);
public:
    /*Function to send data to server via HTTP/HTTPS protocol*/
    static ae_error_t aesm_send_recv_msg(const char *url, const uint8_t * msg, uint32_t msg_size, uint8_t* &resp_msg, uint32_t& resp_size, http_methods_t type= POST, bool is_ocsp=false);
    /*Function to send data to server via HTTP/HTTPS protocol but the data to be send will be HEX/BASE64 encoded and the respose message is decoded. It is used for ES/SGX/PSEPR Provisioning*/
    static ae_error_t aesm_send_recv_msg_encoding(const char *url, const uint8_t * msg, uint32_t msg_size, uint8_t* &resp_msg, uint32_t& resp_size);
    static void aesm_free_response_msg(uint8_t *resp); /*Function to free the buffer of resp_msg from previous two functions*/
};
#endif

