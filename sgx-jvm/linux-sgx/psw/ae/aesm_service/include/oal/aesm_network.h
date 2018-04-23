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

#ifndef _AESM_NETWORK_H_
#define _AESM_NETWORK_H_
#include "aeerror.h"
#include "se_types.h"

 /**
  * File: aesm_network.h
  * Description: Definition for interface of HTTP/HTTPS network communication used in AESM
  */

typedef enum _network_protocol_type_t
{
	HTTP = 0,
	HTTPS,
} network_protocol_type_t;

typedef enum _http_methods_t
{
	GET = 0,
	POST,
} http_methods_t;

/*Function to send data to a server  and receive the response
 *@server_url:  provide the url of the server 
 *@req: start address of the data to be sent to server
 *@req_size: size in bytes of data to be sent to server
 *@p_resp: output parameter for pointer to the start address of received data, 
 *   we must free the pointer via function aesm_free_network_response_buffer(*p_resp)
 *@p_resp_size: output parameter for size of the received data
 *@is_ocsp: set it to true for OCSP and false for SGX EPID/PSE Provisioning or endpoint selection
 *@return AESM_SUCCESS on success or error code if failed
 */

ae_error_t aesm_network_send_receive(const char *server_url, const uint8_t *req, uint32_t req_size,
                                       uint8_t **p_resp, uint32_t *p_resp_size, http_methods_t method, bool is_ocsp=false);
void aesm_free_network_response_buffer(uint8_t *resp);
#endif

