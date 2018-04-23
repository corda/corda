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

#define MSG4_FIELD_COUNT_MINIMUM            3

//*********************************************************************************************************
//* PSE_ProvMsg4
//*   Seq #   Data Item
//*   =====   ============================================================================================
//*     1      Request Header                  (Protocol, Version, TransactionID, Type)
//*     2      X509 Certificate TLV            (TLV Type, Type, Version, Size, [X509 Certificate]) - signed certificate issued by CA
//*     3      X509 Certificate TLV            (TLV Type, Type, Version, Size, [X509 Certificate]) - first certificate in CA's certificate chain
//*     X,*    (Optional) X509 Certificate TLV --- a subsequent certificate in the CA's certificate chain (only present if chain has more than two elements)
//*     N      (Optional) X509 Certificate TLV --- the last certificate in the CA's certificate chain     (only present if chain has more than one element )
//*     N+1    (Optional) Platform Info Blob TLV (TLV Type, Type, Version, Size, [PlatformInfoBlob])
//*     N+2    Message Authentication Code TLV (TLV Type, Type, Version, Size, [MAC])
//*                MAC over 1, 2, 3, and 4:[X, N]
//*********************************************************************************************************

ae_error_t CertificateProvisioningProtocol::msg4_process(const upse::Buffer& serializedMsg4, std::list< upse::Buffer >& certificateChainList, platform_info_blob_wrapper_t& piBlobWrapper)
{
    ae_error_t status = AE_FAILURE;
    tlv_status_t tlv_status;

    do
    {
        TLVsMsg tlvs;
        const provision_response_header_t& header = reinterpret_cast<const provision_response_header_t&>(*serializedMsg4.getData());

        status = check_response_header(header, TYPE_PSE_MSG4, serializedMsg4.getSize());
        BREAK_IF_FAILED_ERR(status, AESM_PSE_PR_BACKEND_MSG4_RESPONSE_HEADER_INTEGRITY);

        status = check_response_status(header);
        if (AE_FAILED(status))
            break;

        tlv_status= tlvs.init_from_buffer(serializedMsg4.getData() + PROVISION_RESPONSE_HEADER_SIZE, static_cast<uint32_t>(serializedMsg4.getSize() - PROVISION_RESPONSE_HEADER_SIZE));
        status = tlv_error_2_pve_error(tlv_status);
        if (AE_FAILED(status))
            break;

        status = msg4_validate_tlvs(tlvs);
        if (AE_FAILED(status))
            break;

        status = msg4_verify_mac(header, tlvs);
        if (AE_FAILED(status))
            break;

        status = msg4_get_certificates(tlvs, certificateChainList, piBlobWrapper);
        if (AE_FAILED(status))
            break;

    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::msg4_validate_tlvs(const TLVsMsg& tlvs)
{
    ae_error_t status = AESM_PSE_PR_BACKEND_MSG4_TLV_INTEGRITY;

    do
    {
        uint32_t tlv_count = tlvs.get_tlv_count();

        if (tlv_count < MSG4_FIELD_COUNT_MINIMUM)
            break;

        uint32_t mac_tlv_index = tlv_count - 1;

        // MAC TLV
        if (tlvs[mac_tlv_index].type != TLV_MESSAGE_AUTHENTICATION_CODE || 
            tlvs[mac_tlv_index].size != MAC_SIZE || 
            tlvs[mac_tlv_index].version < TLV_VERSION_1)
        {
            break;
        }

        uint32_t i;
        for (i = 0; i < mac_tlv_index; i++)
        {
            if (tlvs[i].type != TLV_X509_CERT_TLV || tlvs[i].version < TLV_VERSION_1)
                break;
        }

        if (i < mac_tlv_index - 1)
            break;

        if (i < mac_tlv_index)
        {
            // If we stopped before reaching MAC TLV, then TLV directly prior to MAC must be Platform Info Blob
            if (tlvs[i].type != TLV_PLATFORM_INFO_BLOB || tlvs[i].version < TLV_VERSION_1)
                break;
        }

        status = AE_SUCCESS;

    } while (0);

    return status;
}

ae_error_t CertificateProvisioningProtocol::msg4_verify_mac(const provision_response_header_t& header, const TLVsMsg& tlvs)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        // The MAC consists of the header and all TLVs, prior to the MAC TLV
        uint32_t bytes_to_mac = tlvs.get_tlv_msg_size() - MAC_TLV_SIZE(MAC_SIZE);
        uint32_t aadSize = static_cast<uint32_t>(sizeof(header)) + bytes_to_mac;

        uint32_t mac_tlv_index = tlvs.get_tlv_count() - 1;

        upse::Buffer aad;
        status = aad.Alloc(aadSize);
        if (AE_FAILED(status))
            break;

        upse::BufferWriter aadWriter(aad);
        status = aadWriter.writeRaw((const uint8_t*)&header, sizeof(header));
        if (AE_FAILED(status))
            break;
        status = aadWriter.writeRaw(const_cast<uint8_t*>(tlvs.get_tlv_msg()), bytes_to_mac);
        if (AE_FAILED(status))
            break;

        upse::Buffer mac;
        status = mac.Alloc(tlvs[mac_tlv_index].size);
        if (AE_FAILED(status))
            break;
        status = upse::BufferWriter(mac).writeRaw(tlvs[mac_tlv_index].payload, tlvs[mac_tlv_index].size);
        if (AE_FAILED(status))
            break;

        upse::Buffer emptyCipherText;
        upse::Buffer msg4_iv;
        status = M3IV.Not(msg4_iv);
        if (AE_FAILED(status))
            break;

        upse::Buffer plainText;
        status = aesGCMDecrypt(msg4_iv, EK2, emptyCipherText, aad, mac, plainText);
        if (AE_FAILED(status))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::msg4_get_certificates(const TLVsMsg& tlvs, std::list< upse::Buffer >& certificateChainList, platform_info_blob_wrapper_t& piBlobWrapper)
{
    // NOTE: With Backend Server 1.1.105.0, the order of TLV_X509_CERT_TLV was [LeafCertificate, CA CHAIN]. 
    // This was out of spec. It's fixed now and this comment is the only reminder of what was.
    ae_error_t status = AE_FAILURE;

    certificateChainList.clear();

    upse::Buffer leafCertificate;

    memset(&piBlobWrapper, 0, sizeof(piBlobWrapper));

    do
    {
        uint32_t mac_tlv_index = tlvs.get_tlv_count() - 1;

        uint32_t i;
        for (i = 0; i < mac_tlv_index; i++)
        {
            upse::Buffer b;
            status = b.Alloc(tlvs[i].size);
            if (AE_FAILED(status))
                break;

            status = upse::BufferWriter(b).writeRaw(tlvs[i].payload, tlvs[i].size);
            if (AE_FAILED(status))
                break;

            if (tlvs[i].type == TLV_X509_CERT_TLV)
            {
                if (i == 0)
                {
                    //
                    // this is sort of a reminder of the incorrect cert order in the poc
                    // but leaving it here makes it easy to do checks here on the leaf cert
                    //
                    status = leafCertificate.Clone(b);
                    if (AE_FAILED(status))
                        break;
                }
                certificateChainList.push_back(b);
            }
            else if (tlvs[i].type == TLV_PLATFORM_INFO_BLOB)
            {
                if (sizeof(piBlobWrapper.platform_info_blob) > b.getSize())
                {
                    status = AESM_PSE_PR_BACKEND_MSG4_PLATFORM_INFO_BLOB_SIZE;
                    break;
                }
                // Ignore PIB during PSE cert provisioning.
            }

            else
            {
                status = AESM_PSE_PR_BACKEND_MSG4_UNEXPECTED_TLV_TYPE;
                break;
            }
        }

        if (i < mac_tlv_index)
            break;

        BREAK_IF_FALSE((leafCertificate.getSize() > 0), status, AESM_PSE_PR_BACKEND_MSG4_LEAF_CERTIFICATE_SIZE);

        status = AE_SUCCESS;

    } while (0);

    return status;
}

