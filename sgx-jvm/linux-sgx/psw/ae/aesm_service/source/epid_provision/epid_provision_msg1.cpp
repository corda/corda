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
#include "epid_utility.h"
#include "oal/oal.h"
#include "aeerror.h"
#include "PVEClass.h"
#include "PCEClass.h"
#include "aesm_rand.h"
#include "epid_pve_type.h"
#include "ipp_wrapper.h"

 /**
  * File: epid_provision_msg1.cpp
  * Description: Provide the untrusted implementation of code to generate ProvMsg1
  */

//For each ProvMsg,the first field is msg header (which including XID)
//But in the code here, XID is not counted as a TLV field(since it is part of msg header)
//and the index of TLV field is started from 0.

//Msg1 Top TLVs: TLV_CIPHER_TEXT(rsa_oaep_result),E+MAC(encrypted_and_mac_data)
//#define MSG1_TOP_FIELDS_COUNT        3
//#define MSG1_TOP_FIELD_RSA_OAEP_DATA msg1_fields[0]
//#define MSG1_TOP_FIELD_GCM_DATA      msg1_fields[1]
//#define MSG1_TOP_FIELD_GCM_MAC       msg1_fields[2]

//Function to transform the RSA public key(big endian) into ipp format, the function is defined in pve_pub_key.cpp
//the key is received in endpoint selection from Provision Server which is used for rsa-oaep in ProvMsg1
//secure_free_rsa_pub_key should be called to release the memory on successfully returned rsa_pub_key
//return PVEC_SUCCESS on success
IppStatus get_provision_server_rsa_pub_key_in_ipp_format(const signed_pek_t& pek, IppsRSAPublicKeyState **rsa_pub_key);


//Function to initialize request header for ProvMsg1
//msg1_header: request header for ProvMsg1 to fill in
//use_flags: whether the flag tlv is included
//xid: transaction ID
//msg1_buffer_size: buffer size for ProvMsg1, in bytes
static ae_error_t prov_msg1_gen_header(provision_request_header_t *msg1_header,
                                       bool use_flags,
                                       const uint8_t *xid,
                                       uint32_t msg1_buffer_size)
{
    uint32_t total_size = 0;
    //platform info tlv size
    uint32_t field1_data_size = PLATFORM_INFO_TLV_SIZE();
    field1_data_size += CIPHER_TEXT_TLV_SIZE(RSA_3072_KEY_BYTES);
    //add flag tlv if needed
    if(use_flags){
        field1_data_size += FLAGS_TLV_SIZE();
    }

    if(sizeof(*msg1_header)>msg1_buffer_size){
        AESM_DBG_ERROR("Too small ProvMsg1 buffer size");
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    }
    total_size = CIPHER_TEXT_TLV_SIZE(RSA_3072_KEY_BYTES) + BLOCK_CIPHER_TEXT_TLV_SIZE(field1_data_size) +MAC_TLV_SIZE(MAC_SIZE);
    //initialize Msg1 Header
    msg1_header->protocol = SE_EPID_PROVISIONING;
    msg1_header->type = TYPE_PROV_MSG1;
    msg1_header->version = TLV_VERSION_2;
    if(0!=memcpy_s(msg1_header->xid, sizeof(msg1_header->xid), xid, XID_SIZE)){
        AESM_DBG_FATAL("fail in memcpy_s");
        return PVE_UNEXPECTED_ERROR;
    }
    uint32_t size_in;
    //use as a tmp size, big endian required in msg header
    size_in = _htonl(total_size);
    //copy big endian msg body size into header
    if(0!=memcpy_s(&msg1_header->size, sizeof(msg1_header->size),&size_in, sizeof(size_in))){
        AESM_DBG_FATAL("fail in memcpy_s");
        return PVE_UNEXPECTED_ERROR;
    }
    if(total_size +sizeof(*msg1_header) >msg1_buffer_size){
        //the input msg body size is not large enough
        AESM_DBG_ERROR("Too small ProvMsg1 buffer size");
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    }
    return AE_SUCCESS;
}

//This function will do the rsa oaep encryption with input src[0:src_len] and put the output to buffer dst
//The function will assume that buffer src_len is no more than PVE_RSAOAEP_ENCRYPT_MAXLEN and the buffer size of dst is at least RSA_3072_KEY_BITS
static ae_error_t aesm_rsa_oaep_encrypt(const uint8_t *src, uint32_t src_len, const IppsRSAPublicKeyState *rsa, uint8_t dst[RSA_3072_KEY_BYTES])
{
    const int hashsize = SHA_SIZE_BIT;
    Ipp8u seeds[hashsize];
    IppStatus status = ippStsNoErr;
    ae_error_t ret = AE_SUCCESS;
    uint8_t* pub_key_buffer = NULL;
    int pub_key_size;

    ret = aesm_read_rand(seeds, hashsize);
    if(AE_SUCCESS!=ret){
        goto ret_point;
    }

    if((status = ippsRSA_GetBufferSizePublicKey(&pub_key_size, rsa)) != ippStsNoErr)
    {
        ret = AE_FAILURE;
        goto ret_point;
    }

    //allocate temporary buffer
    pub_key_buffer = (uint8_t*)malloc(pub_key_size);
    if(pub_key_buffer == NULL)
    {
        ret = AE_OUT_OF_MEMORY_ERROR;
        goto ret_point;
    }

    if((status = ippsRSAEncrypt_OAEP(src, src_len,
                                        NULL, 0, seeds,
                                        dst, rsa, IPP_ALG_HASH_SHA256, pub_key_buffer)) != ippStsNoErr)
    {
        ret = AE_FAILURE;
        goto ret_point;
    }

ret_point:
    if(pub_key_buffer)
        free(pub_key_buffer);
    return ret;
}


//generate ProvMsg1
//The function will generate a random transaction id which is used by msg header of msg1 and saved in pve_data
//The function return AE_SUCCESS on success and other to indicate error
//@pve_data: global structure used to store pve relative data
//@msg1: buffer to receive ProvMsg1 (including both header and body)
//@msg1_size: size of buffer msg1
//@return AE_SUCCESS on success

 uint32_t CPVEClass::gen_prov_msg1(
     pve_data_t &pve_data,
     uint8_t *msg1,
     uint32_t msg1_size)
{
    uint32_t ret = AE_SUCCESS;
    uint16_t pce_id = 0;
    uint16_t pce_isv_svn = 0;
    sgx_report_t pek_report;
    uint8_t *field2 = NULL;
    uint8_t field2_iv[IV_SIZE];
    uint8_t field2_mac[MAC_SIZE];
    uint8_t encrypted_ppid[RSA_3072_KEY_BYTES];
    //msg1 header will be in the beginning part of the output msg
    provision_request_header_t *msg1_header = reinterpret_cast<provision_request_header_t *>(msg1);
    memset(&pek_report, 0, sizeof(pek_report));
    sgx_target_info_t pce_target_info;
    sgx_status_t sgx_status;

    //Load PCE Enclave required
    ret = CPCEClass::instance().load_enclave();
    if(ret != AE_SUCCESS){
        AESM_DBG_ERROR("Fail to load PCE enclave:( ae%d)\n",ret);
        return ret;
    }
    ret = CPCEClass::instance().get_pce_target(&pce_target_info);
    if(ret != AE_SUCCESS){
        AESM_DBG_ERROR("Fail to get PCE target info:( ae %d)\n",ret);
        return ret;
    }

    //Load PvE enclave now
    ret = CPVEClass::instance().load_enclave();
    if( ret != AE_SUCCESS){
        AESM_DBG_ERROR("Fail to load PvE enclave:(ae%d)\n",ret);
        return ret;
    }
    //The code will generate a report on PEK by PvE
    ret = gen_prov_msg1_data(&pve_data.pek, &pce_target_info, &pek_report);
    if(AE_SUCCESS != ret ){
        AESM_DBG_ERROR("Gen ProvMsg1 in trusted code failed:( ae %d)",ret);
        return ret;
    }
    se_static_assert(sizeof(encrypted_ppid)==PEK_MOD_SIZE);
    //Load PCE Enclave required
    ret = CPCEClass::instance().load_enclave();
    if(ret != AE_SUCCESS){
        AESM_DBG_ERROR("Fail to load PCE enclave:( ae %d)\n",ret);
        return ret;
    }
    ret = CPCEClass::instance().get_pce_info(pek_report, pve_data.pek, pce_id,
        pce_isv_svn, encrypted_ppid);
    if(AE_SUCCESS != ret){
        AESM_DBG_ERROR("Fail to generate pc_info:(ae%d)",ret);
        return ret;
    }

    //randomly generate XID
    ret = aesm_read_rand(pve_data.xid, XID_SIZE);
    if(AE_SUCCESS != ret ){
        AESM_DBG_ERROR("Fail to generate random XID (ae%d)",ret);
        return ret;
    }
    //randomly generate SK
    ret = aesm_read_rand(pve_data.sk, SK_SIZE);
    if(AE_SUCCESS != ret ){
        AESM_DBG_ERROR("Fail to generate random SK (ae%d)",ret);
        return ret;
    }
    CPCEClass::instance().unload_enclave();
    ret = prov_msg1_gen_header(msg1_header, pve_data.is_performance_rekey, pve_data.xid, msg1_size);
    if(AE_SUCCESS != ret){
        AESM_DBG_ERROR("fail to generate ProvMsg1 Header:(ae %d)",ret);
        return ret;
    }

    {
        TLVsMsg tlvs_msg1_sub;
        tlv_status_t tlv_status;

        sgx_sha256_hash_t psid;
        tlv_status = tlvs_msg1_sub.add_block_cipher_info(pve_data.sk);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){

            AESM_DBG_ERROR("Fail to generate SK TLV of ProvMsg1 (ae %d)",ret);
            return ret;
        }
        sgx_status = sgx_sha256_msg(reinterpret_cast<const uint8_t *>(&pve_data.pek.n),
            static_cast<uint32_t>(sizeof(pve_data.pek.n) + sizeof(pve_data.pek.e)), &psid);
        if(SGX_SUCCESS != sgx_status){
            AESM_DBG_ERROR("Fail to generate PSID, (sgx0x%x)",sgx_status);
            return AE_FAILURE;
        }
        se_static_assert(sizeof(sgx_sha256_hash_t)==sizeof(psid_t));
        tlv_status = tlvs_msg1_sub.add_psid(reinterpret_cast<const psid_t *>(&psid));
        ret = tlv_error_2_pve_error(tlv_status);
        if(SGX_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to add PSID TLV ae(%d)",ret);
            return ret;
        }
        //transform rsa format PEK public key of Provision Server into IPP library format
        IppsRSAPublicKeyState *rsa_pub_key = NULL;
        IppStatus ippStatus = get_provision_server_rsa_pub_key_in_ipp_format(pve_data.pek, &rsa_pub_key);
        if( ippStsNoErr != ippStatus){
            AESM_DBG_ERROR("Fail to decode PEK:%d",ippStatus);
            return AE_FAILURE;
        }
        uint8_t field0[RSA_3072_KEY_BYTES];
        ret = aesm_rsa_oaep_encrypt(tlvs_msg1_sub.get_tlv_msg(), tlvs_msg1_sub.get_tlv_msg_size(), rsa_pub_key, field0);
        secure_free_rsa_pub_key(RSA_3072_KEY_BYTES, sizeof(uint32_t), rsa_pub_key);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to in RSA_OAEP for ProvMsg1:(ae%d)",ret);
            return ret;
        }
        TLVsMsg tlvs_msg1;
        tlv_status= tlvs_msg1.add_cipher_text(field0, RSA_3072_KEY_BYTES, PEK_3072_PUB);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to generate field0 TLV of ProvMsg1( ae%d)",ret);
            return ret;
        }

        TLVsMsg tlvs_msg2_sub;
        tlv_status = tlvs_msg2_sub.add_cipher_text(encrypted_ppid, RSA_3072_KEY_BYTES, PEK_3072_PUB);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            return ret;
        }

        if(!pve_data.is_backup_retrieval){
            if(0!=memcpy_s(&pve_data.bpi.cpu_svn, sizeof(pve_data.bpi.cpu_svn),
                     &pek_report.body.cpu_svn, sizeof(pek_report.body.cpu_svn))){
                AESM_DBG_FATAL("fail in memcpy_s");
                return PVE_UNEXPECTED_ERROR;
            }
            if(0!=memcpy_s(&pve_data.bpi.pve_svn, sizeof(pve_data.bpi.pve_svn),
                     &pek_report.body.isv_svn, sizeof(pek_report.body.isv_svn))){
                AESM_DBG_FATAL("fail in memcpy_s");
                return PVE_UNEXPECTED_ERROR;
            }
            if(0!=memcpy_s(&pve_data.bpi.pce_svn, sizeof(pve_data.bpi.pce_svn),
                     &pce_isv_svn, sizeof(pce_isv_svn))){
                AESM_DBG_FATAL("fail in memcpy_s");
                return PVE_UNEXPECTED_ERROR;
            }
        }
        //always use pce_id from PCE enclave
        pve_data.bpi.pce_id = pce_id;
        memset(&pve_data.bpi.fmsp, 0, sizeof(pve_data.bpi.fmsp));
        tlv_status = tlvs_msg2_sub.add_platform_info(pve_data.bpi);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate Platform Info TLV of ProvMsg1 (ae%d)",ret);
            return ret;
        }
        if(pve_data.is_performance_rekey){
            flags_t flags;
            memset(&flags,0,sizeof(flags));
            //set performance rekey flags
            flags.flags[FLAGS_SIZE-1]=1;
            tlv_status = tlvs_msg2_sub.add_flags(&flags);
            ret = tlv_error_2_pve_error(tlv_status);
            if(AE_SUCCESS != ret){
                AESM_DBG_ERROR("Fail to generate FLAGS TLV of ProvMsg1, (ae %d)",ret);
                return ret;
            }
        }

        ret = aesm_read_rand(field2_iv, IV_SIZE);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to read rand:(ae%d)",ret);
            return ret;
        }
        sgx_cmac_128bit_tag_t ek1;
        se_static_assert(SK_SIZE==sizeof(sgx_cmac_128bit_key_t));
        if((sgx_status = sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(pve_data.sk),
             pve_data.xid, XID_SIZE, &ek1))!=SGX_SUCCESS){
                 AESM_DBG_ERROR("Fail to generate ek1:(sgx%d)",sgx_status);
                 return AE_FAILURE;
        }

        field2 = (uint8_t *)malloc(tlvs_msg2_sub.get_tlv_msg_size());
        if(NULL == field2){
            AESM_DBG_ERROR("Out of memory");
            return AE_OUT_OF_MEMORY_ERROR;
        }


        sgx_status = sgx_rijndael128GCM_encrypt(&ek1,
            tlvs_msg2_sub.get_tlv_msg(), tlvs_msg2_sub.get_tlv_msg_size(),
            field2,field2_iv, IV_SIZE, (const uint8_t *)msg1_header, sizeof(provision_request_header_t),
            (sgx_aes_gcm_128bit_tag_t *)field2_mac);
        if(SGX_SUCCESS != sgx_status){
            ret = sgx_error_to_ae_error(sgx_status);
            AESM_DBG_ERROR("Fail to do AES encrypt (sgx %d)", sgx_status);
            free(field2);
            return ret;
        }

        tlv_status = tlvs_msg1.add_block_cipher_text(field2_iv, field2, tlvs_msg2_sub.get_tlv_msg_size());
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            free(field2);
            AESM_DBG_ERROR("Fail to generate field1 TLV of ProvMsg1(ae%d)",ret);
            return ret;
        }

        free(field2);
        tlv_status = tlvs_msg1.add_mac(field2_mac);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to create field2 TLV of ProvMsg1:(ae %d)",ret);
            return ret;
        }
        uint32_t size = tlvs_msg1.get_tlv_msg_size();
        if(memcpy_s(msg1+PROVISION_REQUEST_HEADER_SIZE, msg1_size - PROVISION_REQUEST_HEADER_SIZE,
            tlvs_msg1.get_tlv_msg(), size)!=0){
                //The size overflow has been checked in header generation
                AESM_DBG_FATAL("fail in memcpy_s");
                return PVE_UNEXPECTED_ERROR;
        }
    }
    return AE_SUCCESS;
}


