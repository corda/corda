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


#ifndef _PROV_MSG_SIZE_H_
#define _PROV_MSG_SIZE_H_

#include "type_length_value.h"

/*Inline functions to estimate size of ProvMsg1, ProvMsg3 etc*/

/*Function to estimate the size of ProvMsg1
  TLV_CIPHER_TEXT(SK, PSID): E+MAC(CIPHER_TLV:PLATFORM_INFO_TLV[:FLAG_TLV])*/
inline uint32_t estimate_msg1_size(bool performance_rekey)
{
    size_t field0_size = CIPHER_TEXT_TLV_SIZE(RSA_3072_KEY_BYTES);
    size_t field1_0_size = CIPHER_TEXT_TLV_SIZE(RSA_3072_KEY_BYTES);
    size_t field1_1_size = PLATFORM_INFO_TLV_SIZE();
    size_t field1_2_size = performance_rekey? FLAGS_TLV_SIZE():0;
    size_t field1_size = BLOCK_CIPHER_TEXT_TLV_SIZE(field1_0_size+field1_1_size+field1_2_size);
    size_t field2_size = MAC_TLV_SIZE(MAC_SIZE);
    return static_cast<uint32_t>(PROVISION_REQUEST_HEADER_SIZE+field0_size+field1_size+field2_size); /*no checking for integer overflow since the size of msg1 is fixed and small*/
}

/*Function to estimate the size of ProvMsg3
  NONCE_TLV(NONCE_SIZE):E+MAC(E+MAC(EPID_JOIN_PROOF_TLV):NONCE_TLV(NONCE_2):CIPHER_TLV:SE_REPRT_TLV):E+MAC(EPID_SIGNATURE_TLV)*/
inline uint32_t calc_msg3_size_by_sigrl_count(uint32_t sigrl_count)
{
    size_t field0_size = NONCE_TLV_SIZE(NONCE_SIZE);
    size_t field1_0_size = BLOCK_CIPHER_TEXT_TLV_SIZE(EPID_JOIN_PROOF_TLV_SIZE());
    size_t field1_1_size = MAC_TLV_SIZE(MAC_SIZE);
    size_t field1_2_size = NONCE_TLV_SIZE(NONCE_2_SIZE);
    size_t field1_3_size = CIPHER_TEXT_TLV_SIZE(RSA_3072_KEY_BYTES);
    size_t field1_4_size = SE_REPORT_TLV_SIZE();
    size_t field3_0_size = EPID_SIGNATURE_TLV_SIZE(sigrl_count);
    size_t field1_size = BLOCK_CIPHER_TEXT_TLV_SIZE(field1_0_size+field1_1_size+field1_2_size+field1_3_size+field1_4_size);
    size_t field2_size = MAC_TLV_SIZE(MAC_SIZE);
    size_t field3_size = BLOCK_CIPHER_TEXT_TLV_SIZE(field3_0_size);
    size_t field4_size = MAC_TLV_SIZE(MAC_SIZE);
    return static_cast<uint32_t>(PROVISION_REQUEST_HEADER_SIZE+field0_size+field1_size+field2_size+field3_size+field4_size);
}

/*Function to estimate the count of SigRl Entry inside a ProvMsg2
  Nonce_TLV(NONCE_SIZE):E+MAC(PubGroupCert:ChallengeNonce[:PlatformInfoPSVN]:PSID:EPID_GID:PlatformInfo)[:signed SigRl]*/
inline uint32_t estimate_sigrl_count_by_msg2_size(uint32_t msg2_size)
{
    size_t field_0_size = NONCE_TLV_SIZE(NONCE_SIZE);
    size_t field_1_0_size = EPID_GROUP_CERT_TLV_SIZE();
    size_t field_1_1_size = NONCE_TLV_SIZE(CHALLENGE_NONCE_SIZE);
    size_t field_1_2_size = PLATFORM_INFO_TLV_SIZE(); //It is always present if sigrl entry count is nonzero
    size_t field_1_3_size = PSID_TLV_SIZE();
    size_t field_1_4_size = EPID_GID_TLV_SIZE();
    size_t field_1_5_size = PLATFORM_INFO_TLV_SIZE();
    size_t field_1_size = BLOCK_CIPHER_TEXT_TLV_SIZE(field_1_0_size+field_1_1_size+field_1_2_size
        + field_1_3_size + field_1_4_size + field_1_5_size );
    size_t field_2_size = MAC_TLV_SIZE(MAC_SIZE);
    size_t field_3_size = 0;
    if(PROVISION_RESPONSE_HEADER_SIZE+field_0_size+field_1_size+field_2_size>=msg2_size)
        return 0;
    field_3_size = msg2_size - (PROVISION_RESPONSE_HEADER_SIZE+field_0_size+field_1_size+field_2_size);
    if(field_3_size < ECDSA_SIGN_SIZE*2 +sizeof(SigRl))
        return 0;
    field_3_size -= ECDSA_SIGN_SIZE*2 + sizeof(SigRl);
    /*The first SigRlEntry has been included into SigRl structure so that an extra 1 is added*/
    return static_cast<uint32_t>(1+field_3_size/sizeof(SigRlEntry));
}

inline uint32_t estimate_msg3_size_by_msg2_size(uint32_t msg2_size)
{
    return calc_msg3_size_by_sigrl_count(estimate_sigrl_count_by_msg2_size(msg2_size));
}

inline uint32_t estimate_es_msg1_size(void)
{
    return static_cast<uint32_t>(PROVISION_REQUEST_HEADER_SIZE+ES_SELECTOR_TLV_SIZE());
}
#endif

