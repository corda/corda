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

#include "CertificateProvisioningProtocol.h"
#include <cstddef>
#include "epid_utility.h"

#include "tlv_common.h"
#include "type_length_value.h"

#define MSG2_NONCE_INDEX                    0
#define MSG2_SIGRL_INDEX                    1
#define MSG2_MAC_INDEX_NO_SIGRL             1
#define MSG2_MAC_INDEX_WITH_SIGRL           2

#define MSG2_FIELD_COUNT_WITHOUT_SIGRL      2
#define MSG2_FIELD_COUNT_WITH_SIGRL         3


//*********************************************************************************************************
//* PSE_ProvMsg2
//*   Seq #   Data Item
//*   =====   ============================================================================================
//*     1      Response Header                 (Protocol, Version, TransactionID, Type)
//*     2      Nonce TLV                       (TLV Type, Type, Version, Size, [Nonce])
//*     3      EPID SigRL Nonce TLV (Optional) (TLV Type, Type, Version, Size, [Nonce])
//*     4      Message Authentication Code TLV (TLV Type, Type, Version, Size, [MAC])
//*                MAC over 1, 2, and 3
//*********************************************************************************************************

ae_error_t CertificateProvisioningProtocol::msg2_process(const upse::Buffer& serializedMsg2, upse::Buffer& nonce, upse::Buffer& sigRL)
{
    ae_error_t status = AE_FAILURE;
	tlv_status_t tlv_status;

    do
    {
        TLVsMsg tlvs;
        const provision_response_header_t& header = reinterpret_cast<const provision_response_header_t&>(*serializedMsg2.getData());

        status = check_response_header(header, TYPE_PSE_MSG2, serializedMsg2.getSize());
        BREAK_IF_FAILED_ERR(status, AESM_PSE_PR_BACKEND_MSG2_RESPONSE_HEADER_INTEGRITY);

        status = check_response_status(header);
        if (AE_FAILED(status))
            break;

        tlv_status= tlvs.init_from_buffer(serializedMsg2.getData() + PROVISION_RESPONSE_HEADER_SIZE, 
                                          static_cast<uint32_t>(serializedMsg2.getSize() - PROVISION_RESPONSE_HEADER_SIZE));
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = msg2_check_integrity(tlvs);
        if (AE_FAILED(status))
            break;

        status = msg2_derive_ek2_and_retrieve_nonce(tlvs, EK2, nonce);
        if (AE_FAILED(status))
            break;

        status = Nonce.Clone(nonce);
        if (AE_FAILED(status))
            break;

        status = msg2_verify_mac_and_retrieve_sigrl(header, tlvs, EK2, sigRL);
        if (AE_FAILED(status))
            break;

    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::msg2_check_integrity(const TLVsMsg& tlvs)
{
    ae_error_t status = PVE_INTEGRITY_CHECK_ERROR;

    do
    {
        uint32_t tlv_count = tlvs.get_tlv_count();

        if (tlv_count < MSG2_FIELD_COUNT_WITHOUT_SIGRL || 
            tlv_count > MSG2_FIELD_COUNT_WITH_SIGRL)
        {
            break;
        }

        // NONCE TLV
        if (tlvs[MSG2_NONCE_INDEX].type != TLV_NONCE || 
            tlvs[MSG2_NONCE_INDEX].size != NONCE_SIZE || 
            tlvs[MSG2_NONCE_INDEX].version < TLV_VERSION_1)
        {
            break;
        }

        if (tlv_count == MSG2_FIELD_COUNT_WITH_SIGRL)
        {
            // EPID SIG RL TLV
            if (tlvs[MSG2_SIGRL_INDEX].type != TLV_EPID_SIG_RL || tlvs[MSG2_SIGRL_INDEX].version < TLV_VERSION_1)
            {
                break;
            }

            // MAC TLV
            if (tlvs[MSG2_MAC_INDEX_WITH_SIGRL].type != TLV_MESSAGE_AUTHENTICATION_CODE || 
                tlvs[MSG2_MAC_INDEX_WITH_SIGRL].size != MAC_SIZE || 
                tlvs[MSG2_MAC_INDEX_WITH_SIGRL].version < TLV_VERSION_1)
            {
                break;
            }
        }
        else
        {
            // MAC TLV
            if (tlvs[MSG2_MAC_INDEX_NO_SIGRL].type != TLV_MESSAGE_AUTHENTICATION_CODE || 
                tlvs[MSG2_MAC_INDEX_NO_SIGRL].size != MAC_SIZE || 
                tlvs[MSG2_MAC_INDEX_NO_SIGRL].version < TLV_VERSION_1)
            {
                break;
            }
        }

        status = AE_SUCCESS;

    } while (0);

    return status;
}

ae_error_t CertificateProvisioningProtocol::msg2_derive_ek2_and_retrieve_nonce(const TLVsMsg& tlvs, upse::Buffer& ek2, upse::Buffer& nonce)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        status = nonce.Alloc(NONCE_SIZE);
        if (AE_FAILED(status))
            break;
        status = upse::BufferWriter(nonce).writeRaw(tlvs[MSG2_NONCE_INDEX].payload, NONCE_SIZE);
        if (AE_FAILED(status))
            break;

        upse::Buffer message;
        status = message.Alloc(TransactionID.getSize() + nonce.getSize());
        if (AE_FAILED(status))
            break;

        upse::BufferWriter messageWriter(message);
        status = messageWriter.writeRaw(TransactionID.getData(), TransactionID.getSize());
        if (AE_FAILED(status))
            break;
        status = messageWriter.writeRaw(nonce.getData(), nonce.getSize());
        if (AE_FAILED(status))
            break;

        status = aesCMAC(M1SK, message, ek2);
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}

ae_error_t CertificateProvisioningProtocol::msg2_verify_mac_and_retrieve_sigrl(const provision_response_header_t& header, const TLVsMsg& tlvs, const upse::Buffer& ek2, upse::Buffer& sigRL)
{
    ae_error_t status = AE_FAILURE;
    tlv_status_t tlv_status;

    do
    {
        uint32_t tlv_count = tlvs.get_tlv_count();

        upse::Buffer m2IV;
        status = M1IV.Not(m2IV);
        if (AE_FAILED(status))
            break;

        upse::Buffer m2HeaderBuf;
        status = m2HeaderBuf.Alloc((const uint8_t*)&header, sizeof(header));
        if (AE_FAILED(status))
            break;

        TLVsMsg nonceTLV;
        tlv_status = nonceTLV.add_nonce(tlvs[MSG2_NONCE_INDEX].payload, NONCE_SIZE);
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        upse::Buffer nonceTlvBuf;
        status = nonceTlvBuf.Alloc(nonceTLV.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;
        status = upse::BufferWriter(nonceTlvBuf).writeRaw(nonceTLV.get_tlv_msg(), nonceTLV.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        upse::Buffer macBuf;

        const uint8_t* pSigRLTLV = NULL;
        uint32_t nSigRLTLV = 0;

        if (tlv_count == MSG2_FIELD_COUNT_WITH_SIGRL)
        {
            // Locate the SIG RL TLV within the serialized TLV message
            nSigRLTLV = tlvs[MSG2_SIGRL_INDEX].header_size + tlvs[MSG2_SIGRL_INDEX].size;
            pSigRLTLV = tlvs.get_tlv_msg();
            for (int i = 0; i < MSG2_SIGRL_INDEX; i++)
            {
                pSigRLTLV += (tlvs[i].header_size + tlvs[i].size);
            }

            // EPID SIG RL
            status = sigRL.Alloc(tlvs[MSG2_SIGRL_INDEX].size);
            if (AE_FAILED(status))
                break;

            status = upse::BufferWriter(sigRL).writeRaw(tlvs[MSG2_SIGRL_INDEX].payload, tlvs[MSG2_SIGRL_INDEX].size);
            if (AE_FAILED(status))
                break;

            // MAC TLV
            status = macBuf.Alloc(tlvs[MSG2_MAC_INDEX_WITH_SIGRL].size);
            if (AE_FAILED(status))
                break;

            status = upse::BufferWriter(macBuf).writeRaw(tlvs[MSG2_MAC_INDEX_WITH_SIGRL].payload, tlvs[MSG2_MAC_INDEX_WITH_SIGRL].size);
            if (AE_FAILED(status))
                break;
        }
        else
        {
            // MAC TLV
            status = macBuf.Alloc(tlvs[MSG2_MAC_INDEX_NO_SIGRL].size);
            if (AE_FAILED(status))
                break;

            status = upse::BufferWriter(macBuf).writeRaw(tlvs[MSG2_MAC_INDEX_NO_SIGRL].payload, tlvs[MSG2_MAC_INDEX_NO_SIGRL].size);
            if (AE_FAILED(status))
                break;
        }

        upse::Buffer aad;
        status = aad.Alloc(m2HeaderBuf.getSize() + nonceTlvBuf.getSize() + nSigRLTLV);
        if (AE_FAILED(status))
            break;

        upse::BufferWriter aadWriter(aad);
        status = aadWriter.writeRaw(m2HeaderBuf.getData(), m2HeaderBuf.getSize());
        if (AE_FAILED(status))
            break;
        status = aadWriter.writeRaw(nonceTlvBuf.getData(), nonceTlvBuf.getSize());
        if (AE_FAILED(status))
            break;
        status = aadWriter.writeRaw(pSigRLTLV, nSigRLTLV);
        if (AE_FAILED(status))
            break;

        upse::Buffer emptyCipherText;
        upse::Buffer plainText;

        status = aesGCMDecrypt(m2IV, ek2, emptyCipherText, aad, macBuf, plainText);
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}

