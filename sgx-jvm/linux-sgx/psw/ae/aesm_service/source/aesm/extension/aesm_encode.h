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

#ifndef _AESM_ENCODE_H_
#define _AESM_ENCODE_H_
#include "sgx_urts.h"
#include "se_thread.h" 
#ifdef __cplusplus
extern "C"{
#endif

uint32_t certPseSvn();

/*Function to provide an upper bound of buffer size of encoded message for an input request
 *@param req, the header for the input request such as ProvMsg1 or ProvMsg3
 *@return an upper bound of the required buffer size for the encoded message
*/
uint32_t get_request_encoding_length(const uint8_t *req);

/*Function to provide an upper bound of the response body size given the length of encoded response message
 *@param buf_len, the length of the encoded message for an response message
 *@return an upper bound of the length in bytes of decoded response message body such as ProvMsg2 or ProvMsg4
 */
uint32_t get_response_decoding_length(uint32_t buf_len);

/*Function to encode an request message so that we could send it to Provisioning server
 *@param req, pointer to the request 
 *@param out_buf, pointer to a buffer to receive the encoded request message
 *@param out_len, *out_len to pass in the buffer len and return the encoded message length when successful
 *@return true if successful and false if there's any error. No error code provided
 */
bool encode_request(const uint8_t *req, uint32_t req_len, uint8_t *out_buf, uint32_t *out_len);

/*Function to decode an response message from Provisioning Server
 *@param input_buf, pointer to the encoded response message
 *@param input_len, length in bytes of the encoded response message
 *@param resp, pointer to a bufer to recieve the decoded message 
 *@param out_len, *out_len to pass in the buffer len and return the decoded response message
 *@return true if successful and false if there's any error. No error code provided
 */
bool decode_response(const uint8_t *input_buf, uint32_t input_len, uint8_t *resp, uint32_t *out_len);
#ifdef __cplusplus
};
#endif/*__cplusplus*/
#endif/*_AESM_ENCOCE_H_*/

