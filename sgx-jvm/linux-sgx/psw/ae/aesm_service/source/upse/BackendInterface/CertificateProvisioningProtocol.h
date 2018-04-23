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

#ifndef _CERTIFICATE_PROVISIONING_INTERFACE_H_
#define _CERTIFICATE_PROVISIONING_INTERFACE_H_

#include <stdint.h>
#include <list>
#include <string>
#include "Buffer.h"
#include "aeerror.h"
#include "epid/common/types.h"
#include "tlv_common.h"


#include "platform_info_blob.h"

struct _cpRSA_public_key;
struct _provision_request_header_t;
struct _provision_response_header_t;
class TLVsMsg;

typedef struct _public_key
{
    uint8_t n[RSA_3072_KEY_BYTES];
    uint32_t e;
} public_key_t;


class CertificateProvisioningProtocol
{
public:
    CertificateProvisioningProtocol(void);
    ~CertificateProvisioningProtocol(void);

    ae_error_t init(const char* szURL, const signed_pek_t& pek);

    ae_error_t SendM1_ReceiveM2
        (   /*in */ uint32_t gid,
            /*out*/ upse::Buffer& nonce,
            /*out*/ upse::Buffer& sigRLBuffer
        );

    ae_error_t SendM3_ReceiveM4
        (   /*in */ const upse::Buffer& csrBuffer,
            /*in */ const upse::Buffer& quoteBuffer,
            /*out*/ std::list< upse::Buffer >& certificateChainList,
            /*out*/ platform_info_blob_wrapper_t& piBlobWrapper
        );

    general_response_status_t GetGeneralResponseStatus()        { return generalResponseStatus;  }
    pse_protocol_response_status_t GetProtocolResponseStatus()  { return protocolResponseStatus; }

private:

    typedef enum
    {
        msg_next_state_init = 0,
        msg_next_state_M1 = 1,
        msg_next_state_M2 = 2,
        msg_next_state_M3 = 3,
        msg_next_state_M4 = 4
    } msg_state_t;

    bool m_is_initialized;
    std::string m_url;
    msg_state_t m_nextState;

    public_key_t m_publicKey;

    general_response_status_t generalResponseStatus;
    pse_protocol_response_status_t protocolResponseStatus;

    upse::Buffer M1SK;
    upse::Buffer M1IV;
    upse::Buffer M3IV;
    upse::Buffer TransactionID;
    upse::Buffer EK2;
    upse::Buffer Nonce;

    const public_key_t& get_intel_pek();
    int get_intel_pek_cipher_text_size();
    void free_intel_ipp_rsa_pub_key(_cpRSA_public_key* rsa_pub_key);

    ae_error_t get_random_value(uint32_t size, upse::Buffer& randomValue);
    ae_error_t check_response_header(const _provision_response_header_t& header, uint8_t msg_type, uint32_t msg_size);
    ae_error_t check_response_status(const _provision_response_header_t& msg2_header);

    ae_error_t sendReceive(const upse::Buffer& sendSerialized, upse::Buffer& recvSerialized);

    ae_error_t msg1_generate(const GroupId gid, upse::Buffer& serializedMsg1);
    ae_error_t msg1_create_header(uint32_t cipherTextSize, uint32_t epidGidSize, const upse::Buffer& transactionID, _provision_request_header_t& header);
    ae_error_t msg1_create_seq2_0(const TLVsMsg& seq2_1_tlv_block_cipher_info, TLVsMsg& seq2_0_tlv_cipher_text);
    ae_error_t msg1_create_seq2_1(TLVsMsg& seq2_1_tlv_block_cipher_info);
    ae_error_t msg1_create_seq3_0(const TLVsMsg& seq3_1_tlv_epid_gid, const _provision_request_header_t& serializedHeader,
                               const upse::Buffer& ek1, TLVsMsg& seq3_0_tlv_block_cipher_text, upse::Buffer& mac);

    ae_error_t msg2_process(const upse::Buffer& serializedMsg2, upse::Buffer& nonce, upse::Buffer& sigRLBuffer);
    ae_error_t msg2_check_integrity(const TLVsMsg& tlvs);
    ae_error_t msg2_derive_ek2_and_retrieve_nonce(const TLVsMsg& tlvs, upse::Buffer& ek2, upse::Buffer& nonce);
    ae_error_t msg2_verify_mac_and_retrieve_sigrl(const provision_response_header_t& header, const TLVsMsg& tlvs, const upse::Buffer& ek2, upse::Buffer& sigRL);

    ae_error_t msg3_generate(const upse::Buffer& csrBuffer, const upse::Buffer& quoteBuffer, upse::Buffer& serializedMsg3);
    ae_error_t msg3_create_header(const upse::Buffer& transactionID, uint32_t nonceSize, uint32_t quoteSize, uint32_t epidSigSize, uint32_t csrSize, _provision_request_header_t& header);
    ae_error_t msg3_seq3_0_create_block_cipher_text_tlv(const TLVsMsg& quote, const TLVsMsg& epidSigTLV, const TLVsMsg& csrTLV, const TLVsMsg& nonceTLV,
                                                      const _provision_request_header_t& requestHeader, const upse::Buffer& ek2,
                                                      TLVsMsg& blockCipherTextTLV, upse::Buffer& mac);
    ae_error_t msg3_seq3_1_create_quote_tlv(const upse::Buffer& quoteBuffer, TLVsMsg& quoteTLV);
    ae_error_t msg3_seq3_2_create_quote_signature_tlv(const upse::Buffer& quote, TLVsMsg& seq3_2_tlv_quote_signature);

    ae_error_t msg4_process(const upse::Buffer& serializedMsg4, std::list< upse::Buffer >& certificateChainList, platform_info_blob_wrapper_t& piBlobWrapper);
    ae_error_t msg4_validate_tlvs(const TLVsMsg& tlvs);
    ae_error_t msg4_verify_mac(const _provision_response_header_t& header, const TLVsMsg& tlvs);
    ae_error_t msg4_get_certificates(const TLVsMsg& tlvs, std::list< upse::Buffer >& certificateChainList, platform_info_blob_wrapper_t& piBlobWrapper);

    ae_error_t aesGCMEncrypt(const upse::Buffer& iv, const upse::Buffer& keyReader, const upse::Buffer& plainText,
                                const upse::Buffer& aad, upse::Buffer& encryptedText, upse::Buffer& mac);
    ae_error_t aesGCMDecrypt(const upse::Buffer& iv, const upse::Buffer& key, const upse::Buffer& cipherText,
                                const upse::Buffer& aad, const upse::Buffer& mac, upse::Buffer& plainText);
    ae_error_t aesCMAC(const upse::Buffer& key, const upse::Buffer& message, upse::Buffer& cmac);

    ae_error_t encryptRSA_OAEP_SHA256(const public_key_t& publicKey, upse::BufferReader& plainTextReader, upse::Buffer& encryptedText);


private:

    // Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
    //CertificateProvisioningProtocol();										            // default constructor
    CertificateProvisioningProtocol(const CertificateProvisioningProtocol& rhs);			// copy constructor
    CertificateProvisioningProtocol& operator=(const CertificateProvisioningProtocol& rhs); // address-of operator
    const CertificateProvisioningProtocol* operator&() const;				                // address-of operator

};


#endif

