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
#include "sgx_quote.h"

#include "tlv_common.h"
#include "type_length_value.h"
#include "se_wrapper.h"

//*********************************************************************************************************
//* PSE_ProvMsg3
//*   Seq #   Data Item
//*   =====   ============================================================================================
//*     1      Request Header                  (Protocol, Version, TransactionID, Type)
//*     2      Nonce TLV                       (TLV Type, Type, Version, Size, [Nonce])
//*     3      Block Cipher Text TLV           (TLV Type, Type, Version, Size, [IV, EncryptedPayload is 2.1, 2.2, 2.3])
//*     3.1      SE Quote TLV                  (TLV Type, Type, Version, Size, [Quote])
//*     3.2      SE Quote Signature TLV        (TLV Type, Type, Version, Size, [Signature])
//*     3.3      X509 CSR TLV                  (TLV Type, Type, Version, Size, [CSR])
//*     4      Message Authentication Code TLV (TLV Type, Type, Version, Size, [MAC])
//*                MAC over 1, 2, and 3
//*********************************************************************************************************

ae_error_t CertificateProvisioningProtocol::msg3_generate(const upse::Buffer& csrBuffer, const upse::Buffer& quoteBuffer, upse::Buffer& serializedMsg3)
{
    ae_error_t status = AE_FAILURE;
    tlv_status_t tlv_status = TLV_UNKNOWN_ERROR;

    provision_request_header_t serializedHeader;
    memset(&serializedHeader, 0, sizeof(serializedHeader));

    TLVsMsg seq2_0_tlv_nonce;
    TLVsMsg seq3_0_tlv_block_cipher_text;
    TLVsMsg seq3_1_tlv_quote;
    TLVsMsg seq3_2_tlv_quote_signature;
    TLVsMsg seq3_3_tlv_x509_csr;
    TLVsMsg seq4_0_tlv_mac;

    do
    {
        tlv_status = seq2_0_tlv_nonce.add_nonce(Nonce.getData(), Nonce.getSize());
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = msg3_seq3_1_create_quote_tlv(quoteBuffer, seq3_1_tlv_quote);
        if (AE_FAILED(status))
            break;

        status = msg3_seq3_2_create_quote_signature_tlv(quoteBuffer, seq3_2_tlv_quote_signature);
        if (AE_FAILED(status))
            break;

        tlv_status = seq3_3_tlv_x509_csr.add_x509_csr(csrBuffer.getData(), csrBuffer.getSize());
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = msg3_create_header(TransactionID, seq2_0_tlv_nonce.get_tlv_msg_size(), seq3_1_tlv_quote.get_tlv_msg_size(),
            seq3_2_tlv_quote_signature.get_tlv_msg_size(), seq3_3_tlv_x509_csr.get_tlv_msg_size(), serializedHeader);
        if (AE_FAILED(status))
            break;

        upse::Buffer mac;
        status = msg3_seq3_0_create_block_cipher_text_tlv(seq3_1_tlv_quote, seq3_2_tlv_quote_signature, seq3_3_tlv_x509_csr, seq2_0_tlv_nonce, serializedHeader, 
            EK2, seq3_0_tlv_block_cipher_text, mac);
        if (AE_FAILED(status))
            break;

        tlv_status = seq4_0_tlv_mac.add_mac(mac.getData());
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        //*********************************************************************
        // Prepare serialized message buffer
        //*********************************************************************
        uint32_t size_msg3 = static_cast<uint32_t>(PROVISION_REQUEST_HEADER_SIZE + seq2_0_tlv_nonce.get_tlv_msg_size() +
                                seq3_0_tlv_block_cipher_text.get_tlv_msg_size() + seq4_0_tlv_mac.get_tlv_msg_size());

        status = serializedMsg3.Alloc(size_msg3);
        if (AE_FAILED(status))
            break;

        serializedMsg3.zeroMemory();
        upse::BufferWriter bwMsg3(serializedMsg3);

        // Write serialized request header to serialized message
        status = bwMsg3.writeRaw((uint8_t*)&serializedHeader, sizeof(serializedHeader));
        if (AE_FAILED(status))
            break;

        // Write sequence 2.0 - Nonce TLV
        status = bwMsg3.writeRaw(const_cast<uint8_t*>(seq2_0_tlv_nonce.get_tlv_msg()), seq2_0_tlv_nonce.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        // Write sequence 3.0 - Block Cipher Text TLV (contains 3.1, 3.2, and 3.3 as encrypted payload)
        status = bwMsg3.writeRaw(const_cast<uint8_t*>(seq3_0_tlv_block_cipher_text.get_tlv_msg()), seq3_0_tlv_block_cipher_text.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        // Write sequence 4.0 - MAC TLV
        status = bwMsg3.writeRaw(const_cast<uint8_t*>(seq4_0_tlv_mac.get_tlv_msg()), seq4_0_tlv_mac.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::msg3_create_header(const upse::Buffer& transactionID, uint32_t nonceSize, uint32_t quoteSize, uint32_t epidSigSize, uint32_t csrSize, provision_request_header_t& header)
{
    ae_error_t status = AESM_PSE_PR_INTERNAL_ERROR;

    do
    {
        uint32_t seq2_0_tlv_block_cipher_text_size = BLOCK_CIPHER_TEXT_TLV_SIZE(quoteSize + epidSigSize + csrSize);
        uint32_t seq3_0_tlv_nonce_size = nonceSize;
        uint32_t seq4_0_tlv_mac_size = MAC_TLV_SIZE(MAC_SIZE);

        header.protocol = PSE_PROVISIONING;
        header.version = TLV_VERSION_1;
        header.type = static_cast<uint8_t>(TYPE_PSE_MSG3);

        if (XID_SIZE != transactionID.getSize())
            break;

        if (memcpy_s(header.xid, sizeof(header.xid), transactionID.getData(), transactionID.getSize()) != 0)
            break;

        uint32_t totalSize = seq2_0_tlv_block_cipher_text_size + seq3_0_tlv_nonce_size + seq4_0_tlv_mac_size;

        uint32_t serializedSize = _htonl(totalSize);
        if (sizeof(serializedSize) != sizeof(header.size))
            break;

        if (memcpy_s(header.size, sizeof(header.size), &serializedSize, sizeof(serializedSize)) != 0)
            break;

        status = AE_SUCCESS;
    } while (0);

    return status;
}

ae_error_t CertificateProvisioningProtocol::msg3_seq3_2_create_quote_signature_tlv(const upse::Buffer& quote, TLVsMsg& seq3_2_tlv_quote_signature)
{
    ae_error_t status = AESM_PSE_PR_INTERNAL_ERROR;
    tlv_status_t tlv_status;

    do
    {
	    if (sizeof(sgx_quote_t) > quote.getSize())
            break;

	    const sgx_quote_t* pQuote = (const sgx_quote_t*)quote.getData();

	    /* the QUOTE SIGNATURE TLV doesn't include Quote.signature_len */
        uint32_t SigLen = pQuote->signature_len;
  
        if (sizeof(sgx_quote_t) + SigLen > quote.getSize())
            break;

        const uint8_t *pSig = pQuote->signature;

        tlv_status = seq3_2_tlv_quote_signature.add_quote_signature(pSig, SigLen);
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::msg3_seq3_0_create_block_cipher_text_tlv(const TLVsMsg& quote, const TLVsMsg& epidSigTLV, const TLVsMsg& csrTLV, const TLVsMsg& nonceTLV, 
                                                      const provision_request_header_t& requestHeader, const upse::Buffer& ek2,
                                                      TLVsMsg& blockCipherTextTLV, upse::Buffer& mac)
{
    ae_error_t status = AE_FAILURE;
    tlv_status_t tlv_status;

    upse::Buffer plainText;
    upse::Buffer encryptedPayload;

    do
    {
        status = get_random_value(IV_SIZE, M3IV);
        if (AE_FAILED(status))
            break;

        status = plainText.Alloc(quote.get_tlv_msg_size() + epidSigTLV.get_tlv_msg_size() + csrTLV.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        upse::BufferWriter plainTextWriter(plainText);
        status = plainTextWriter.writeRaw(quote.get_tlv_msg(), quote.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;
        status = plainTextWriter.writeRaw(epidSigTLV.get_tlv_msg(), epidSigTLV.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;
        status = plainTextWriter.writeRaw(csrTLV.get_tlv_msg(), csrTLV.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        uint32_t payloadSize = BLOCK_CIPHER_TEXT_TLV_PAYLOAD_SIZE(plainText.getSize());
        uint32_t blockCipherTextHeaderSize = get_tlv_total_size(payloadSize) - payloadSize;

        // Calculate AAD (concatenation of Request header, Nonce, Block Cipher Text TLV header and IV from Block Cipher Text TLV)
        upse::Buffer aad;
        status = aad.Alloc(static_cast<uint32_t>(sizeof(requestHeader) + nonceTLV.get_tlv_msg_size() +  blockCipherTextHeaderSize + M3IV.getSize()));
        if (AE_FAILED(status))
            break;

        TLVsMsg tmpBlockCipherTextTLV;
        tlv_status = tmpBlockCipherTextTLV.add_block_cipher_text(M3IV.getData(), NULL, plainText.getSize());
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        upse::BufferWriter aadWriter(aad);
        status = aadWriter.writeRaw((const uint8_t*)&requestHeader, sizeof(requestHeader));
        if (AE_FAILED(status))
            break;
        status = aadWriter.writeRaw(nonceTLV.get_tlv_msg(), nonceTLV.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;
        status = aadWriter.writeRaw(tmpBlockCipherTextTLV.get_tlv_msg(), blockCipherTextHeaderSize);
        if (AE_FAILED(status))
            break;
        status = aadWriter.writeRaw(M3IV.getData(), M3IV.getSize());
        if (AE_FAILED(status))
            break;

        status = aesGCMEncrypt(M3IV, ek2, plainText, aad, encryptedPayload, mac);
        if (AE_FAILED(status))
            break;

        tlv_status = blockCipherTextTLV.add_block_cipher_text(M3IV.getData(), encryptedPayload.getData(), encryptedPayload.getSize());
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}



ae_error_t CertificateProvisioningProtocol::msg3_seq3_1_create_quote_tlv(const upse::Buffer& quoteBuffer, TLVsMsg& quoteTLV)
{
    ae_error_t status = AESM_PSE_PR_INTERNAL_ERROR;
    tlv_status_t tlv_status;

    do
    {
        if (sizeof(sgx_quote_t) > quoteBuffer.getSize())
            break;

        const sgx_quote_t* pQuote = (const sgx_quote_t*)quoteBuffer.getData();

        tlv_status = quoteTLV.add_quote((uint8_t*)pQuote, static_cast<uint32_t>(sizeof(sgx_quote_t)-sizeof(pQuote->signature_len)));
		status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

    } while (0);

    return status;
}
