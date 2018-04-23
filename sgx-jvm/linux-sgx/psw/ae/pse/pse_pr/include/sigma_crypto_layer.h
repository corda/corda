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
 
 
#ifndef _SIGMA_CRYPTO_LAYER_H_
#define _SIGMA_CRYPTO_LAYER_H_

#include <string>

#include "pse_pr_inc.h"
#include "pse_pr_types.h"
#include "pse_pr_sigma_1_1_defs.h"
#include "sgx_tcrypto.h" 
#include "pairing_blob.h"

class SigmaCryptoLayer
{
public:

    SigmaCryptoLayer();
    ~SigmaCryptoLayer();

    ae_error_t DeriveSkMk(sgx_ecc_state_handle_t ecc_handle);

    ae_error_t calc_s2_hmac(SIGMA_HMAC* hmac, 
                      const SIGMA_S2_MESSAGE* s2, 
                      size_t nS2VLDataLen);

    ae_error_t calc_s3_hmac(SIGMA_HMAC* hmac, 
                      const SIGMA_S3_MESSAGE* s3,
                      size_t nS3VLDataLen);

    ae_error_t ComputePR(SIGMA_SECRET_KEY* oldSK, Ipp8u byteToAdd, SIGMA_HMAC* hmac);
    ae_error_t ComputeId(Ipp8u byteToAdd, SHA256_HASH* hmac);

    ae_error_t MsgVerifyPch(Ipp8u* PubKeyPch, int PubKeyPchLen, 
        Ipp8u* EpidParamsCert,  Ipp8u* Msg, int MsgLen, Ipp8u* Bsn, int BsnLen, 
        Ipp8u* Signature, int SignatureLen, 
        Ipp8u* PrivRevList, int PrivRL_Len, Ipp8u* SigRevList, int SigRL_Len, Ipp8u* GrpRevList, int GrpRL_Len);

    const uint8_t* get_pub_key_gb_be() { return m_local_public_key_gb_big_endian; }
    const uint8_t* get_remote_pub_key_ga_be() { return m_remote_public_key_ga_big_endian; }
    void set_prv_key_b_le(Ipp8u* pb) { memcpy(m_local_private_key_b_little_endian, pb, sizeof(m_local_private_key_b_little_endian)); }
    void set_pub_key_gb_be(Ipp8u* pGb) { memcpy(m_local_public_key_gb_big_endian, pGb, sizeof(m_local_public_key_gb_big_endian)); }
    void set_remote_pub_key_ga_be(Ipp8u* pGa) { memcpy(m_remote_public_key_ga_big_endian, pGa, sizeof(m_remote_public_key_ga_big_endian)); }

    const uint8_t* get_SMK() { return m_SMK; }
    const uint8_t* get_SK() { return m_SK; }
    const uint8_t* get_MK() { return m_MK; }

private:
    uint8_t m_local_private_key_b_little_endian[SIGMA_SESSION_PRIVKEY_LENGTH];
    uint8_t m_local_public_key_gb_big_endian[SIGMA_SESSION_PUBKEY_LENGTH];
    uint8_t m_remote_public_key_ga_big_endian[SIGMA_SESSION_PUBKEY_LENGTH];
    uint8_t m_SMK[SIGMA_SMK_LENGTH];
    SIGMA_SECRET_KEY m_SK;
    SIGMA_MAC_KEY  m_MK;

    // Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
    //SigmaCryptoLayer(void);                                     // default constructor
    SigmaCryptoLayer(const SigmaCryptoLayer& rhs);              // copy constructor
    SigmaCryptoLayer& operator=(const SigmaCryptoLayer& rhs);   // assignment operator
    SigmaCryptoLayer* operator&();                              // address-of operator
    const SigmaCryptoLayer* operator&() const;                  // address-of operator

};

#endif
