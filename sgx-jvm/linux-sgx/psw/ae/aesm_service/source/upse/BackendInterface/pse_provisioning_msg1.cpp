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
#include "epid_utility.h"

#include "tlv_common.h"
#include "type_length_value.h"
#include "se_wrapper.h"

//*********************************************************************************************************
//* PSE_ProvMsg1
//*   Seq #   Data Item
//*   =====   ============================================================================================
//*     1      Request Header                  (Protocol, Version, TransactionID, Type)
//*     2      Cipher Text TLV                 (TLV Type, Type, Version, Size, [KeyID, EncryptedPayload is 2.1])
//*     2.1      Block Cipher Info TLV         (TLV Type, Type, Version, Size, [SK])
//*     3      Block Cipher Text TLV           (TLV Type, Type, Version, Size, [IV, EncryptedPayload is 3.1])
//*     3.1      EPID GID TLV                  (TLV Type, Type, Version, Size, [GID])
//*     4      Message Authentication Code TLV (TLV Type, Type, Version, Size, [MAC])
//*                MAC over 1 and 3:EncryptedPayload
//*********************************************************************************************************

ae_error_t CertificateProvisioningProtocol::msg1_generate(const GroupId gid, upse::Buffer& serializedMsg1)
{
    ae_error_t status = AE_FAILURE;
    tlv_status_t tlv_status = TLV_UNKNOWN_ERROR;
    GroupId be_gid; //gid from init_quote is little endian, change to bigendian for backend server here
    be_gid.data[0]=gid.data[3];
    be_gid.data[1]=gid.data[2];
    be_gid.data[2]=gid.data[1];
    be_gid.data[3]=gid.data[0];

    provision_request_header_t header;
    memset(&header, 0, sizeof(header));

    TLVsMsg seq2_0_tlv_cipher_text;
    TLVsMsg seq2_1_tlv_block_cipher_info;
    TLVsMsg seq3_0_tlv_block_cipher_text;
    TLVsMsg seq3_1_tlv_epid_gid;
    TLVsMsg seq4_0_tlv_mac;

    do
    {
        status = get_random_value(XID_SIZE, TransactionID);
        if (AE_FAILED(status))
            break;

        // Prepare sequence 2.1 -- Block Cipher Text TLV with SK
        status = msg1_create_seq2_1(seq2_1_tlv_block_cipher_info);
        if (AE_FAILED(status))
            break;

        // Prepare sequence 2.0 -- Cipher Text TLV with KeyID and encrypted 2.1
        status = msg1_create_seq2_0(seq2_1_tlv_block_cipher_info, seq2_0_tlv_cipher_text);
        if (AE_FAILED(status))
            break;

        // Prepare sequence 3.1 -- EPID GID TLV
        tlv_status = seq3_1_tlv_epid_gid.add_epid_gid(be_gid);
        status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        // Derive EK1
        upse::Buffer EK1;
        status = aesCMAC(M1SK, TransactionID, EK1);
        if (AE_FAILED(status))
            break;

        // Create Request Header (we need to calculate size before AES-GCM CMAC)
        status = msg1_create_header(seq2_0_tlv_cipher_text.get_tlv_msg_size(), seq3_1_tlv_epid_gid.get_tlv_msg_size(), TransactionID, header);
        if (AE_FAILED(status))
            break;

        // Prepare sequence 3.0 -- Block Cipher Text TLV with IV and encrypted 3.1
        upse::Buffer mac;
        status = msg1_create_seq3_0(seq3_1_tlv_epid_gid, header, EK1, seq3_0_tlv_block_cipher_text, mac);
        if (AE_FAILED(status))
            break;

        // Prepare sequence 4.0 -- MAC TLV
        tlv_status = seq4_0_tlv_mac.add_mac(mac.getData());
        status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        //*********************************************************************
        // Prepare serialized message buffer
        //*********************************************************************
        uint32_t size_msg1 = static_cast<uint32_t>(PROVISION_REQUEST_HEADER_SIZE) + seq2_0_tlv_cipher_text.get_tlv_msg_size() +
                                seq3_0_tlv_block_cipher_text.get_tlv_msg_size() + seq4_0_tlv_mac.get_tlv_msg_size();

        status = serializedMsg1.Alloc(size_msg1);
        if (AE_FAILED(status))
            break;

        serializedMsg1.zeroMemory();
        upse::BufferWriter bwMsg1(serializedMsg1);

        // Write serialized request header to serialized message
        status = bwMsg1.writeRaw((uint8_t*)&header, sizeof(header));
        if (AE_FAILED(status))
            break;

        // Write sequence 2.0 - Cipher Text TLV (contains 2.1 as encrypted payload)
        status = bwMsg1.writeRaw(const_cast<uint8_t*>(seq2_0_tlv_cipher_text.get_tlv_msg()), seq2_0_tlv_cipher_text.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        // Write sequence 3.0 - Block Cipher Text TLV (contains 3.1 as encrypted payload)
        status = bwMsg1.writeRaw(const_cast<uint8_t*>(seq3_0_tlv_block_cipher_text.get_tlv_msg()), seq3_0_tlv_block_cipher_text.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        // Write sequence 4.0 - MAC TLV
        status = bwMsg1.writeRaw(const_cast<uint8_t*>(seq4_0_tlv_mac.get_tlv_msg()), seq4_0_tlv_mac.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}

ae_error_t CertificateProvisioningProtocol::msg1_create_header(uint32_t seq2_0_cipher_text_size, uint32_t seq3_1_epid_gid_size, const upse::Buffer& transactionID, provision_request_header_t& header)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        uint32_t seq3_0_block_cipher_text_size = BLOCK_CIPHER_TEXT_TLV_SIZE(seq3_1_epid_gid_size);
        uint32_t seq4_0_tlv_mac_size = MAC_TLV_SIZE(MAC_SIZE);

        header.protocol = PSE_PROVISIONING;
        header.version = TLV_VERSION_1;
        header.type = static_cast<uint8_t>(TYPE_PSE_MSG1);

        if (XID_SIZE != transactionID.getSize())
            break;

        if (memcpy_s(header.xid, sizeof(header.xid), transactionID.getData(), transactionID.getSize()) != 0)
            break;

        uint32_t totalSize = seq2_0_cipher_text_size + seq3_0_block_cipher_text_size + seq4_0_tlv_mac_size;

        uint32_t serializedSize = _htonl(totalSize);
        if (sizeof(serializedSize) != sizeof(header.size))
            break;

        if (memcpy_s(header.size, sizeof(header.size), &serializedSize, sizeof(serializedSize)) != 0)
            break;

        status = AE_SUCCESS;
    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::msg1_create_seq2_0(const TLVsMsg& seq2_1_tlv_block_cipher_info, TLVsMsg& seq2_0_tlv_cipher_text)
{
    //* 2.0 Cipher Text TLV (TLV Type, Type, Version, Size, [KeyID, EncryptedPayload is 2.1])
    ae_error_t status = AE_FAILURE;
    tlv_status_t tlv_status;

    do
    {
        upse::Buffer seq2_1_encrypted_tlv;
        upse::Buffer encryptedBlockCipherInfo;

        const public_key_t& public_key = get_intel_pek();

        // Encrypt TLV 2.1
        upse::Buffer blockCipherInfo;
        status = blockCipherInfo.Alloc(seq2_1_tlv_block_cipher_info.get_tlv_msg(), seq2_1_tlv_block_cipher_info.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        upse::BufferReader blockCipherInfoReader(blockCipherInfo);

        status = encryptRSA_OAEP_SHA256(public_key, blockCipherInfoReader, encryptedBlockCipherInfo);
        if (AE_FAILED(status))
            break;

        tlv_status = seq2_0_tlv_cipher_text.add_cipher_text(encryptedBlockCipherInfo.getData(), encryptedBlockCipherInfo.getSize(), PEK_3072_PUB);
        status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;
    } while (0);


    return status;
}

ae_error_t CertificateProvisioningProtocol::msg1_create_seq2_1(TLVsMsg& seq2_1_tlv_block_cipher_info)
{
    //* 2.1 Block Cipher Info TLV (TLV Type, Type, Version, Size, [SK])
    ae_error_t status = AE_FAILURE;
    tlv_status_t tlv_status;

    do
    {
        status = get_random_value(SK_SIZE, M1SK);
        if (AE_FAILED(status))
            break;

        tlv_status = seq2_1_tlv_block_cipher_info.add_block_cipher_info(M1SK.getData());
        status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;
    } while (0);

    return status;
}

ae_error_t CertificateProvisioningProtocol::msg1_create_seq3_0(const TLVsMsg& seq3_1_tlv_epid_gid, const provision_request_header_t& serializedHeader,
                               const upse::Buffer& ek1, TLVsMsg& seq3_0_tlv_block_cipher_text, upse::Buffer& mac)
{
    //* 3.0 Block Cipher Text TLV (TLV Type, Type, Version, Size, [IV, EncryptedPayload is 3.1])
    ae_error_t status = AE_FAILURE;
    tlv_status_t tlv_status;

    do
    {
        status = get_random_value(IV_SIZE, M1IV);
        if (AE_FAILED(status))
            break;

        upse::Buffer aad;
        status = aad.Alloc(sizeof(serializedHeader));
        if (AE_FAILED(status))
            break;

        upse::BufferWriter aadWriter(aad);
        status = aadWriter.writeRaw((const uint8_t*)&serializedHeader, sizeof(serializedHeader));
        if (AE_FAILED(status))
            break;

        upse::Buffer epidGid;
        status = epidGid.Alloc(seq3_1_tlv_epid_gid.get_tlv_msg(), seq3_1_tlv_epid_gid.get_tlv_msg_size());
        if (AE_FAILED(status))
            break;

        upse::Buffer encryptedPayload;
        status = aesGCMEncrypt(M1IV, ek1, epidGid, aad, encryptedPayload, mac);
        if (AE_FAILED(status))
            break;

        tlv_status = seq3_0_tlv_block_cipher_text.add_block_cipher_text(M1IV.getData(), encryptedPayload.getData(), encryptedPayload.getSize());
        status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}

