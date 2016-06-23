/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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
#include "sgx_tcrypto_internal.h"
#include "epid_utility.h"
#include "oal/oal.h"
#include "aeerror.h"
#include "PVEClass.h"
#include "se_wrapper.h"

 /**
  * File: epid_provision_msg1.cpp 
  * Description: Provide the untrusted implementation of code to generate ProvMsg1
 */

//For each ProvMsg, the first field is msg header (which including XID)
//But in the code here, XID is not counted as a TLV field(since it is part of msg header)
//   and the index of TLV field is started from 0.

//Msg1 Top TLVs: TLV_CIPHER_TEXT(rsa_oaep_result),E+MAC(encrypted_and_mac_data)
#define MSG1_TOP_FIELDS_COUNT 3
#define MSG1_TOP_FIELD_RSA_OAEP_DATA msg1_fields[0]
#define MSG1_TOP_FIELD_GCM_DATA      msg1_fields[1]
#define MSG1_TOP_FIELD_GCM_MAC       msg1_fields[2]



//Function to initialize  TLV Header for ProvMsg1 and check whether input buffer for ProvMsg1 is large enough
//field1_data_size[in]: size of field1_data which varies according to whether performance rekey used. 
//msg1_buffer_size[in]: size of buffer used to hold generated ProvMsg1, it is used by the function to verify whether size of buffer is large enough
//xid[in]: Transaction ID used in protocol
//msg1_header[out]: request header for ProvMsg1 to fill in
static ae_error_t prov_msg1_gen_header(provision_request_header_t *msg1_header,
                                       uint32_t field1_data_size,
                                       const uint8_t *xid,
                                       uint32_t msg1_buffer_size)
{
    uint32_t total_size = 0;

    if(sizeof(*msg1_header)>msg1_buffer_size){
        AESM_DBG_ERROR("Too small ProvMsg1 buffer size");
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    }
    total_size = CIPHER_TEXT_TLV_SIZE(PVE_RSA_KEY_BYTES) + BLOCK_CIPHER_TEXT_TLV_SIZE(field1_data_size) +MAC_TLV_SIZE(MAC_SIZE);
    //initialize field in Msg1 Header
    msg1_header->protocol = SE_EPID_PROVISIONING;
    msg1_header->type = TYPE_PROV_MSG1;
    msg1_header->version = TLV_VERSION_1;
    if(0!=memcpy_s(msg1_header->xid, XID_SIZE, xid, XID_SIZE)){
        AESM_DBG_FATAL("fail in memcpy");
        return PVE_UNEXPECTED_ERROR;
    }
    uint32_t size_in;
    size_in = _htonl(total_size);//big endian size required in msg header
    if(0!=memcpy_s(&msg1_header->size, sizeof(msg1_header->size),&size_in, sizeof(size_in))){
        AESM_DBG_FATAL("fail in memcpy");
        return PVE_UNEXPECTED_ERROR;
    }
    if(total_size +sizeof(*msg1_header) >msg1_buffer_size){//the input msg body size is not large enough
        AESM_DBG_ERROR("Too small ProvMsg1 buffer size");
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    }
    return AE_SUCCESS;
}

//Function to generate ProvMsg1
//Input psvn could be NULL to use the current psvn, or input the previous_psvn from preivous ProvMsg2 
//The function will generate a random transaction id which is used by msg header of msg1
//The function return AE_SUCCESS on success and other to indicate error
//Format of ProvMsg1: RSA-OAEP(SK,PSID),E+MAC(DeviceID[:Flags])
//@psvn: the input psvn or NULL
//@pek:  the input PEK got from endpoint selection
//@performance_rekey_used: true for performance rekey and false for first provisioning, backup retrieval or TCB upgrade
//@msg1: buffer to receive ProvMsg1 (including both header and body)
//@msg1_size: size of buffer for generating ProvMsg1
//@return AE_SUCCESS on success
uint32_t CPVEClass::gen_prov_msg1(const psvn_t *psvn,
                           const signed_pek_t& pek,
                           bool performance_rekey_used,
                           uint8_t *msg1,
                           uint32_t msg1_size)
{
    uint32_t ret = AE_SUCCESS;
    prov_msg1_output_t msg1_output;
    //ProvMsg1 header will be in the beginning part of the output msg
    provision_request_header_t *msg1_header = reinterpret_cast<provision_request_header_t *>(msg1);
    memset(&msg1_output, 0, sizeof(msg1_output));

    ret = gen_prov_msg1_data(psvn, &pek, performance_rekey_used, &msg1_output);
    if(AE_SUCCESS !=ret ){
        AESM_DBG_ERROR("Gen ProvMsg1 in trusted code failed:%d",ret);
        return ret;
    }

    ret = prov_msg1_gen_header(msg1_header, msg1_output.field1_data_size, msg1_output.xid, msg1_size);
    if(AE_SUCCESS != ret){
        AESM_DBG_ERROR("fail to generate ProvMsg1 Header:%d",ret);
        return ret;
    }

    {
        TLVsMsg tlvs_msg1;
        tlv_status_t tlv_status;
        tlv_status= tlvs_msg1.add_cipher_text(msg1_output.field0, PVE_RSA_KEY_BYTES, PEK_PUB);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to generate field0 TLV of ProvMsg1:%d",ret);
            return ret;
        }
        tlv_status = tlvs_msg1.add_block_cipher_text(msg1_output.field1_iv, msg1_output.field1_data, msg1_output.field1_data_size);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to generate field1 TLV of ProvMsg1:%d",ret);
            return ret;
        }
        tlv_status = tlvs_msg1.add_mac(msg1_output.field1_mac);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to create field2 TLV of ProvMsg1:%d",ret);
            return ret;
        }
        uint32_t size = tlvs_msg1.get_tlv_msg_size();
        if(memcpy_s(msg1+PROVISION_REQUEST_HEADER_SIZE, msg1_size - PROVISION_REQUEST_HEADER_SIZE, 
            tlvs_msg1.get_tlv_msg(), size)!=0){
                AESM_DBG_FATAL("memcpy error");
                return PVE_UNEXPECTED_ERROR;//The size overflow has been checked in header generation
        }
    }
    return AE_SUCCESS;
}


