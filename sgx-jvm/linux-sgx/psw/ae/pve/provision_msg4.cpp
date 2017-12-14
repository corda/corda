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


#include "provision_msg.h"
#include <sgx_trts.h>
#include "protocol.h"
#include "cipher.h"
#include "sgx_tcrypto.h"
#include "epid/common/errors.h"
#include "epid/member/api.h"
#include "pve_hardcoded_tlv_data.h"
#include "byte_order.h"
#include "pek_pub_key.h"
#include "pve_qe_common.h"
#include <string.h>
#include <stdlib.h>
#include "util.h"

 /**
  * File: provision_msg4.cpp 
  * Description: Provide the implementation of code to process data from ProvMsg4 and generate EPID Data Blob
  *
  * Core Code of Provision Enclave
  */

//Function to verify and process ProvMsg4.field1_2 and decrypt mce to get f part of EPID Private Key by PSK
// to generate PrivKey
//@mce: input pointer to decrypted membership credential and escrow data 
//@msg4_input: input data decoded from ProvMsg4
//@prv_key: output the EPID Private Key on success
//@return PVEC_SUCCESS on success and error code otherwise
static pve_status_t proc_prov_msg4_membercredential(const membership_credential_with_escrow_t *mce,
                                                  const proc_prov_msg4_input_t * msg4_input,
                                                  PrivKey& prv_key)
{
    pve_status_t ret = PVEC_SUCCESS;
    sgx_status_t sgx_status = SGX_SUCCESS;
    sgx_key_128bit_t psk;
    ret = get_pve_psk(&msg4_input->equivalent_psvn, &psk);//generate Provisioning Seal Key used to unseal f
    if(PVEC_SUCCESS != ret){
        goto ret_point;
    }

    if(mce->escrow.version!=0){//only support escrow version 0 now
        ret = PVEC_MSG_ERROR;
        goto ret_point;
    }
    //Decrypt private key f (which had been sealed in ProvMsg3)
    sgx_status = sgx_rijndael128GCM_decrypt(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(&psk),
        reinterpret_cast<const uint8_t *>(&mce->escrow.f), sizeof(FpElemStr), reinterpret_cast<uint8_t *>(&prv_key.f),
        mce->escrow.iv, IV_SIZE, NULL, 0, 
        reinterpret_cast<const sgx_aes_gcm_128bit_tag_t *>(mce->escrow.mac));
    if(SGX_ERROR_MAC_MISMATCH == sgx_status){
        ret = PVEC_MSG_ERROR;
        goto ret_point;
    }else if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }
    //copy A, x and gid
    memcpy(&prv_key.A, &mce->A,sizeof(prv_key.A));
    memcpy(&prv_key.x, &mce->x, sizeof(prv_key.x));
    memcpy(&prv_key.gid, &msg4_input->group_cert.key.gid, sizeof(GroupId));
ret_point:
    if(PVEC_SUCCESS != ret){
        (void)memset_s(&prv_key, sizeof(prv_key),0, sizeof(prv_key));
    }
    (void)memset_s(&psk,sizeof(psk),0,sizeof(psk));
    return ret;
}

//check whether prv key is valid and generate epid blob if PrivKey is valid
//@prv_key: input the EPID Private Key
//@psvn: the equivalent psvn
//@pub_key: input the EPID group public Key
//@epid_blob: output the EPID_DATA_BLOB on success, the size of the buffer has been checked before calling to the function
//@return PVEC_SUCCESS on success or error code on failure
static pve_status_t gen_epid_blob(const extended_epid_group_blob_t* pxegb,
                                  const PrivKey *prv_key,
                                  const psvn_t *psvn,
                                  const GroupPubKey *pub_key,
                                  sgx_sealed_data_t *epid_blob)
{
    uint8_t *tmp_buffer = NULL;
    pve_status_t ret = PVEC_SUCCESS;
    sgx_status_t sgx_status = SGX_SUCCESS;
    EpidStatus epid_ret = kEpidNoErr;
    MemberCtx *p_epid_context = NULL;
    se_secret_epid_data_sdk_t *epid_data = NULL;
    se_plaintext_epid_data_sdk_t *plaintext = NULL;
    uint32_t tmp_buffer_size = static_cast<uint32_t>(sizeof(se_plaintext_epid_data_sdk_t) + sizeof(se_secret_epid_data_sdk_t));
    if(!EpidIsPrivKeyInGroup(pub_key, prv_key)){
        ret = PVEC_MSG_ERROR;
        goto ret_point;
    }
    //alloc one temp buffer to hold both plaintext and secret data before encryption
    //all data before encryption will be copied into the buffer inorder
    tmp_buffer = reinterpret_cast<uint8_t *>(malloc(tmp_buffer_size));
    if(!tmp_buffer)
    {
        ret =PVEC_MALLOC_ERROR;
        goto ret_point;
    }
    memset(tmp_buffer,0,tmp_buffer_size);
    plaintext = reinterpret_cast<se_plaintext_epid_data_sdk_t *>(tmp_buffer);//plaintext in the beginning of the buffer
    //group cert  as part of aad
    memcpy(&plaintext->epid_group_cert, pub_key, sizeof(*pub_key));
    //PVE ISVSVN, CPUSVN  and some public key from xegb in aad too
    epid_data = reinterpret_cast<se_secret_epid_data_sdk_t*>(plaintext + 1);//secret data in same buffer
    memcpy(&plaintext->equiv_cpu_svn, &psvn->cpu_svn, SGX_CPUSVN_SIZE); //equivalent CPUSVN and ISVSVN from PSVN of ProvMsgs used in AAD
    memcpy(&plaintext->equiv_pve_isv_svn, &psvn->isv_svn, sizeof(plaintext->equiv_pve_isv_svn));
    plaintext->seal_blob_type = PVE_SEAL_EPID_KEY_BLOB;
    plaintext->epid_key_version = EPID_KEY_BLOB_VERSION_SDK;
    plaintext->xeid = pxegb->xeid;
    memcpy(&plaintext->qsdk_exp, pxegb->qsdk_exp, sizeof(plaintext->qsdk_exp));
    memcpy(&plaintext->qsdk_mod, pxegb->qsdk_mod, sizeof(plaintext->qsdk_mod));
    memcpy(&plaintext->epid_sk, pxegb->epid_sk, sizeof(plaintext->epid_sk));
    memcpy(&epid_data->epid_private_key,prv_key,sizeof(PrivKey)); //EPID Private key is sealed in EPID_DATA_BLOB together with Member Precomputation
    epid_ret = EpidMemberCreate(&(plaintext->epid_group_cert),
            (PrivKey*)&(epid_data->epid_private_key),
            NULL,
            epid_random_func,
            NULL,
            &p_epid_context);
    if(kEpidNoErr!=epid_ret){
        ret = epid_error_to_pve_error(epid_ret);
        goto ret_point;
    }
    epid_ret = EpidMemberWritePrecomp(p_epid_context, &epid_data->member_precomp_data);//Create Member Precomputation
    if(kEpidNoErr!=epid_ret){
        ret = epid_error_to_pve_error(epid_ret);
        goto ret_point;
    }
    EpidMemberDelete(&p_epid_context);
    //call sgx_seal_data to generate EPID_DATA_BLOB
    if((sgx_status=sgx_seal_data(
        sizeof(se_plaintext_epid_data_sdk_t), reinterpret_cast<uint8_t*>(plaintext),//plaintext as AAD
        sizeof(se_secret_epid_data_sdk_t), reinterpret_cast<uint8_t*>(epid_data), //secret data to SEAL
        SGX_TRUSTED_EPID_BLOB_SIZE_SDK,
        epid_blob))!=SGX_SUCCESS){//generate EPID_DATA_BLOB in epid_blob
            ret = sgx_error_to_pve_error(sgx_status);
            goto ret_point;
    }
ret_point:
    if(tmp_buffer){
        //reset memory to 0 of the temp buffer to defense in depth
        (void)memset_s(tmp_buffer,tmp_buffer_size, 0, tmp_buffer_size);
        free(tmp_buffer);
    }
    return ret;
}

//Function to process the decoded data from ProvMsg4 and generate EPID data blob
//It will create EPIDDataBlob in output parameter epid_blob on success
//@msg_input: input data decoded from ProvMsg4
//@epid_blob: output buffer to hold the generated EPID_DATA_BLOB on success
//@return PVEC_SUCCESS on success and error code otherwise
pve_status_t proc_prov_msg4_data(const proc_prov_msg4_input_t *msg4_input,
                            sgx_sealed_data_t *epid_blob)
{
    uint8_t member_escrow_tlv_buf[MEMBERSHIP_CREDENTIAL_TLV_TOTAL_SIZE];
    pve_status_t ret = PVEC_SUCCESS;
    sgx_status_t sgx_status = SGX_SUCCESS;
    uint8_t pek_result = SGX_EC_INVALID_SIGNATURE;
    sgx_key_128bit_t pwk2;
    PrivKey prv_key;
    uint8_t aad_buf[sizeof(device_id_t)+sizeof(GroupId)];
    extended_epid_group_blob_t local_xegb;
    device_id_t *device_id_in_aad = reinterpret_cast<device_id_t *>(aad_buf+sizeof(GroupId));
    membership_credential_with_escrow_t *mce = reinterpret_cast<membership_credential_with_escrow_t *>(member_escrow_tlv_buf+MEMBERSHIP_CREDENTIAL_TLV_HEADER_SIZE);
    memset(member_escrow_tlv_buf, 0, sizeof(member_escrow_tlv_buf));
    memset(aad_buf, 0 ,sizeof(aad_buf));
    memset(&pwk2, 0, sizeof(pwk2));
    memset(&prv_key, 0, sizeof(prv_key));

    sgx_status = verify_xegb_with_default(msg4_input->xegb, &pek_result,local_xegb);
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }else if(pek_result != SGX_EC_VALID){
        ret = PVEC_XEGDSK_SIGN_ERROR;
        goto ret_point;
    }

    //Verify it is signed by EPID Signing key
    ret = check_signature_of_group_pub_cert(&msg4_input->group_cert, local_xegb.epid_sk);
    if (PVEC_SUCCESS != ret){
        goto ret_point;
    }
    //create PWK2
    ret = get_pwk2(&msg4_input->equivalent_psvn, msg4_input->n2, &pwk2);
    if (PVEC_SUCCESS != ret){
        goto ret_point;
    }
    //preparing device id for AAD
    memcpy(aad_buf, &msg4_input->group_cert.key.gid, sizeof(GroupId));
    memcpy(&device_id_in_aad->fmsp, &msg4_input->fmsp, sizeof(fmsp_t));
    memcpy(&device_id_in_aad->psvn, &msg4_input->equivalent_psvn, sizeof(psvn_t));
    memset(&device_id_in_aad->ppid, 0 ,sizeof(ppid_t));


    se_static_assert(sizeof(sgx_aes_gcm_128bit_key_t)==sizeof(pwk2)); /*SK_SIZE should be same as that of sgx_aes_gcm_128bit_key_t*/
    se_static_assert(sizeof(sgx_aes_gcm_128bit_tag_t)==sizeof(msg4_input->member_credential_mac)); /*member_credential_mac size should be same as that of sgx_aes_gcm_128bit_tag_t*/

    se_static_assert(HARD_CODED_EPID_MEMBER_WITH_ESCROW_TLV_SIZE == MEMBERSHIP_CREDENTIAL_TLV_TOTAL_SIZE); /*hardcoded size should be matched*/

    sgx_status = sgx_rijndael128GCM_decrypt(reinterpret_cast<sgx_aes_gcm_128bit_key_t *>(&pwk2),
        msg4_input->encrypted_member_credential,static_cast<uint32_t>(HARD_CODED_EPID_MEMBER_WITH_ESCROW_TLV_SIZE), member_escrow_tlv_buf,
        msg4_input->member_credential_iv, IV_SIZE, aad_buf, sizeof(aad_buf),
        reinterpret_cast<const sgx_aes_gcm_128bit_tag_t *>(msg4_input->member_credential_mac));//decrypt membership credential and escrow data TLV
    if(sgx_status == SGX_ERROR_MAC_MISMATCH){
        ret = PVEC_MSG_ERROR;
        goto ret_point;
    }else if(sgx_status != SGX_SUCCESS){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }
    if(memcmp(member_escrow_tlv_buf, MEMBERSHIP_CREDENTIAL_TLV_HEADER, MEMBERSHIP_CREDENTIAL_TLV_HEADER_SIZE)!=0){//checking TLV header matches hard-coded value
        ret = PVEC_MSG_ERROR;
        goto ret_point;
    }

    se_static_assert(sizeof(membership_credential_with_escrow_t)+MEMBERSHIP_CREDENTIAL_TLV_HEADER_SIZE==MEMBERSHIP_CREDENTIAL_TLV_TOTAL_SIZE); /*invalid hard-coded value*/
    ret = proc_prov_msg4_membercredential(mce, msg4_input, prv_key);//decrypt and generate epid private key
    if(PVEC_SUCCESS!=ret){
        goto ret_point;
    }
    ret = gen_epid_blob(&local_xegb, &prv_key, &msg4_input->equivalent_psvn, &msg4_input->group_cert.key, epid_blob);//seal epid private key to generate EPID data blob
ret_point:
    //now clear secret data from memory to defense in depth
    (void)memset_s(&pwk2,sizeof(pwk2), 0, sizeof(pwk2));
    (void)memset_s(&prv_key, sizeof(prv_key), 0, sizeof(prv_key));
    (void)memset_s(member_escrow_tlv_buf, sizeof(member_escrow_tlv_buf), 0, sizeof(member_escrow_tlv_buf));
    (void)memset_s(aad_buf, sizeof(aad_buf), 0, sizeof(aad_buf));
    return ret;
}
