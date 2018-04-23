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

#include "type_length_value.h"
#include <sgx_trts.h>
#include "epid_utility.h"
#include "aesm_xegd_blob.h"
#include "aeerror.h"
#include "PVEClass.h"

/**
* File: epid_provision_msg4.cpp
* Description: Provide the untrusted implementation of code to process ProvMsg4
*
* Untrusted Code for EPID Provision
*/
#define MSG4_TOP_FIELDS_COUNT       3
#define MSG4_TOP_FIELD_NONCE        tlvs_msg4[0]
#define MSG4_TOP_FIELD_DATA         tlvs_msg4[1]
#define MSG4_TOP_FIELD_MAC          tlvs_msg4[2]

#define MSG4_FIELD1_COUNT           5
#define MSG4_FIELD1_PLATFORM_INFO   tlvs_field1[4]
#define MSG4_FIELD1_Nonce2          tlvs_field1[0]
#define MSG4_FIELD1_ENC_Axf         tlvs_field1[1]
#define MSG4_FIELD1_MAC_Axf         tlvs_field1[2]
#define MSG4_FIELD1_GROUP_CERT      tlvs_field1[3]


static ae_error_t msg4_integrity_checking(const TLVsMsg& tlvs_msg4)
{
    uint32_t tlv_count = tlvs_msg4.get_tlv_count();
    if(tlv_count != MSG4_TOP_FIELDS_COUNT)
        return PVE_INTEGRITY_CHECK_ERROR;
    if(MSG4_TOP_FIELD_NONCE.type != TLV_NONCE || MSG4_TOP_FIELD_NONCE.version != TLV_VERSION_1 ||
        MSG4_TOP_FIELD_NONCE.size != NONCE_SIZE || MSG4_TOP_FIELD_NONCE.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_INTEGRITY_CHECK_ERROR;
    if(MSG4_TOP_FIELD_DATA.type != TLV_BLOCK_CIPHER_TEXT || MSG4_TOP_FIELD_DATA.version != TLV_VERSION_1)
        return PVE_INTEGRITY_CHECK_ERROR;
    if(MSG4_TOP_FIELD_MAC.type != TLV_MESSAGE_AUTHENTICATION_CODE || MSG4_TOP_FIELD_MAC.version != TLV_VERSION_1 ||
        MSG4_TOP_FIELD_MAC.size != MAC_SIZE || MSG4_TOP_FIELD_MAC.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_INTEGRITY_CHECK_ERROR;
    return AE_SUCCESS;
}

static ae_error_t msg4_field1_msg_checking(const TLVsMsg& tlvs_field1)
{
    uint32_t tlv_count = tlvs_field1.get_tlv_count();
    if(tlv_count!=MSG4_FIELD1_COUNT){
        return PVE_MSG_ERROR;
    }
    uint32_t i;
    for(i=0;i<tlv_count;++i)
        if(tlvs_field1[i].version != TLV_VERSION_1)
            return PVE_MSG_ERROR;

    if(MSG4_FIELD1_Nonce2.type != TLV_NONCE ||
        MSG4_FIELD1_Nonce2.size != NONCE_2_SIZE ||
        MSG4_FIELD1_Nonce2.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG4_FIELD1_ENC_Axf.type != TLV_BLOCK_CIPHER_TEXT||
        MSG4_FIELD1_ENC_Axf.size != BLOCK_CIPHER_TEXT_TLV_PAYLOAD_SIZE(HARD_CODED_EPID_MEMBER_WITH_ESCROW_TLV_SIZE))
        return PVE_MSG_ERROR;
    if(MSG4_FIELD1_MAC_Axf.type != TLV_MESSAGE_AUTHENTICATION_CODE||
        MSG4_FIELD1_MAC_Axf.size != MAC_SIZE||
        MSG4_FIELD1_MAC_Axf.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG4_FIELD1_GROUP_CERT.type != TLV_EPID_GROUP_CERT||
        MSG4_FIELD1_GROUP_CERT.size != sizeof(signed_epid_group_cert_t)||
        MSG4_FIELD1_GROUP_CERT.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if (MSG4_FIELD1_PLATFORM_INFO.type != TLV_PLATFORM_INFO ||
        MSG4_FIELD1_PLATFORM_INFO.size != sizeof(bk_platform_info_t) ||
        MSG4_FIELD1_PLATFORM_INFO.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    return AE_SUCCESS;
}

//Function to check message header of ProvMsg4 to determine whether it is valid
//@msg4_header, input the message header of ProvMsg4
//@return PVEC_SUCCESS if the message header is valid ProvMsg4 or error code if there're any problems
static ae_error_t check_prov_msg4_header(const provision_response_header_t *msg4_header, uint32_t msg4_size)
{
    if(msg4_header->protocol != SE_EPID_PROVISIONING || msg4_header->type != TYPE_PROV_MSG4 ||
        msg4_header->version != TLV_VERSION_2){
            return PVE_INTEGRITY_CHECK_ERROR;
    }
    uint32_t size_in_header = lv_ntohl(msg4_header->size);
    if(size_in_header + PROVISION_RESPONSE_HEADER_SIZE != msg4_size)
        return PVE_INTEGRITY_CHECK_ERROR;
    return AE_SUCCESS;
}


//Function to decode ProvMsg4 and generate epid data blob
uint32_t CPVEClass::proc_prov_msg4(
        const pve_data_t &data,
        const uint8_t *msg4,
        uint32_t msg4_size,
        uint8_t *data_blob,
        uint32_t blob_size)
{
    ae_error_t ret = AE_SUCCESS;
    uint8_t local_ek2[SK_SIZE];
    uint8_t *decoded_msg4 = NULL;
    uint8_t temp[XID_SIZE+NONCE_SIZE];
    sgx_status_t sgx_status;
    const provision_response_header_t *msg4_header = reinterpret_cast<const provision_response_header_t *>(msg4);
    if(msg4_size < PROVISION_RESPONSE_HEADER_SIZE){
        AESM_DBG_ERROR("invalid msg4 size");
        return PVE_MSG_ERROR;
    }
    if (blob_size != SGX_TRUSTED_EPID_BLOB_SIZE_SDK){
        AESM_DBG_FATAL("invalid input of epid blob size");
        return PVE_PARAMETER_ERROR;
    }

    ret = check_prov_msg4_header(msg4_header, msg4_size);
    if( AE_SUCCESS != ret){
        AESM_DBG_ERROR("Invalid ProvMsg4 Header:(ae%d)",ret);
        return ret;
    }
    if(0!=memcmp(msg4_header->xid, data.xid, XID_SIZE)){
        AESM_DBG_ERROR("Invalid XID in msg4 header");
        return PVE_MSG_ERROR;
    }
    ret = check_epid_pve_pg_status_before_mac_verification(msg4_header);
    if( AE_SUCCESS != ret){
        AESM_DBG_ERROR("Backend return failure in ProvMsg4 Header:(ae%d)",ret);
        return ret;
    }

    do{
        TLVsMsg tlvs_msg4;
        uint8_t aad[PROVISION_RESPONSE_HEADER_SIZE+NONCE_SIZE];
        tlv_status_t tlv_status;
        tlv_status = tlvs_msg4.init_from_buffer(msg4+static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE), msg4_size - static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE));
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("fail to decode ProvMsg4:(ae%d)",ret);
            break;
        }
        ret = msg4_integrity_checking(tlvs_msg4);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("ProvMsg4 integrity checking error:(ae%d)",ret);
            break;
        }
        AESM_DBG_TRACE("ProvMsg4 decoded");
        se_static_assert(sizeof(sgx_cmac_128bit_key_t)==SK_SIZE);
        if(0!=memcpy_s(temp,sizeof(temp), data.xid, sizeof(data.xid))||
            0!=memcpy_s(temp+XID_SIZE, sizeof(temp)-XID_SIZE, MSG4_TOP_FIELD_NONCE.payload, MSG4_TOP_FIELD_NONCE.size)){
                AESM_DBG_ERROR("Fail in memcpy");
                ret = AE_FAILURE;
                break;
        }
        if((sgx_status=sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(data.sk),
            temp, XID_SIZE+NONCE_SIZE, reinterpret_cast<sgx_cmac_128bit_tag_t *>(local_ek2)))!=SGX_SUCCESS){
                AESM_DBG_ERROR("Fail to generate ek2:(sgx0x%x)",sgx_status);
                ret = AE_FAILURE;
                break;

        }
        se_static_assert(SK_SIZE==sizeof(sgx_aes_gcm_128bit_key_t));
        tlv_msg_t field1 = block_cipher_tlv_get_encrypted_text(MSG4_TOP_FIELD_DATA);
        decoded_msg4 = static_cast<uint8_t *>(malloc(field1.msg_size));
        if(NULL == decoded_msg4){
            AESM_DBG_ERROR("malloc error");
            ret = AE_OUT_OF_MEMORY_ERROR;
            break;
        }
        if (memcpy_s(aad, sizeof(aad), msg4_header, PROVISION_RESPONSE_HEADER_SIZE) != 0 ||
                memcpy_s(aad + PROVISION_RESPONSE_HEADER_SIZE, sizeof(aad)-PROVISION_RESPONSE_HEADER_SIZE,
                MSG4_TOP_FIELD_NONCE.payload, MSG4_TOP_FIELD_NONCE.size) != 0){
            AESM_DBG_ERROR("memcpy failure");
            ret = AE_FAILURE;
            break;
        }
        sgx_status = sgx_rijndael128GCM_decrypt(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(local_ek2),
            field1.msg_buf, field1.msg_size, decoded_msg4,
            reinterpret_cast<uint8_t *>(block_cipher_tlv_get_iv(MSG4_TOP_FIELD_DATA)), IV_SIZE,
            aad, sizeof(aad),
            reinterpret_cast<const sgx_aes_gcm_128bit_tag_t *>(MSG4_TOP_FIELD_MAC.payload));
        if(SGX_ERROR_MAC_MISMATCH == sgx_status){
            AESM_DBG_ERROR("fail to decrypt ProvMsg4 by EK2 (sgx0x%x)",sgx_status);
            ret = PVE_INTEGRITY_CHECK_ERROR;
            break;
        }
        if( AE_SUCCESS != (ret = sgx_error_to_ae_error(sgx_status))){
            AESM_DBG_ERROR("error in decrypting ProvMsg4:(sgx0x%x)",sgx_status);
            break;
        }
        AESM_DBG_TRACE("ProvMsg4 decrypted by EK2 successfully");
        ret = check_epid_pve_pg_status_after_mac_verification(msg4_header);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Backend reported error passed MAC verification:(ae%d)",ret);
            break;
        }
        TLVsMsg tlvs_field1;
        tlv_status = tlvs_field1.init_from_buffer(decoded_msg4, field1.msg_size);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("ProvMsg4 Field2.1 decoding failed:(ae%d)",ret);
            break;
        }
        ret = msg4_field1_msg_checking(tlvs_field1);
        if( AE_SUCCESS != ret){
            AESM_DBG_ERROR("ProvMsg4 Field2.1 invalid:(ae%d)",ret);
            break;
        }
        proc_prov_msg4_input_t msg4_input;
        tlv_msg_t Axf_data = block_cipher_tlv_get_encrypted_text(MSG4_FIELD1_ENC_Axf);
        if(0!=memcpy_s(&msg4_input.group_cert, sizeof(msg4_input.group_cert), MSG4_FIELD1_GROUP_CERT.payload, MSG4_FIELD1_GROUP_CERT.size)||
            0!=memcpy_s(&msg4_input.n2, sizeof(msg4_input.n2), MSG4_FIELD1_Nonce2.payload, MSG4_FIELD1_Nonce2.size) ||
            0!=memcpy_s(&msg4_input.equivalent_psvn, sizeof(msg4_input.equivalent_psvn), platform_info_tlv_get_psvn(MSG4_FIELD1_PLATFORM_INFO), sizeof(psvn_t))||
            0!=memcpy_s(&msg4_input.fmsp, sizeof(msg4_input.fmsp), platform_info_tlv_get_fmsp(MSG4_FIELD1_PLATFORM_INFO), sizeof(fmsp_t))||
            0!=memcpy_s(&msg4_input.member_credential_iv, sizeof(msg4_input.member_credential_iv), block_cipher_tlv_get_iv(MSG4_FIELD1_ENC_Axf), IV_SIZE)||
            0!=memcpy_s(&msg4_input.encrypted_member_credential, sizeof(msg4_input.encrypted_member_credential), Axf_data.msg_buf, Axf_data.msg_size)||
            0!=memcpy_s(&msg4_input.member_credential_mac, sizeof(msg4_input.member_credential_mac), MSG4_FIELD1_MAC_Axf.payload, MSG4_FIELD1_MAC_Axf.size)){
                AESM_DBG_ERROR("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;
        }
        if (AE_SUCCESS != (ret =XEGDBlob::instance().read(msg4_input.xegb))){
            AESM_DBG_ERROR("Fail to read extend epid blob info (ae%d)",ret);
            break;
        }

        ret = CPVEClass::instance().load_enclave();//Load PvE enclave now
        if( ret != AE_SUCCESS){
            AESM_DBG_ERROR("Fail to load PvE enclave:(ae%d)\n",ret);
            break;
        }
        ret = (ae_error_t)proc_prov_msg4_data(&msg4_input, reinterpret_cast<proc_prov_msg4_output_t *>(data_blob));
        AESM_DBG_TRACE("PvE return (ae%d) in Process ProvMsg4",ret);
    }while(0);
    if(decoded_msg4)free(decoded_msg4);
    return ret;
}
