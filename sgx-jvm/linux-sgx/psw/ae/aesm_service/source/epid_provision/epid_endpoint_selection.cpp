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
#include "type_length_value.h"
#include <sgx_trts.h>
#include "PVEClass.h"
#include "aesm_rand.h"
#include "se_wrapper.h"
#include "epid_utility.h"
#include "prof_fun.h"
#include "oal/internal_log.h"
#include <assert.h>

static ae_error_t prov_es_gen_header(provision_request_header_t *es_header,
                                     const uint8_t *xid,
                                     uint32_t msg_buffer_size)
{
    uint32_t total_size = 0;

    total_size = ES_SELECTOR_TLV_SIZE();
    //initialize ES Msg1 Header
    es_header->protocol = ENDPOINT_SELECTION;
    es_header->type = TYPE_ES_MSG1;
    es_header->version = TLV_VERSION_2;
    if(0!=memcpy_s(es_header->xid, sizeof(es_header->xid), xid, XID_SIZE)){
        AESM_DBG_FATAL("memcpy error");
        return PVE_UNEXPECTED_ERROR;
    }
    uint32_t size_in;
    size_in = _htonl(total_size);//use as a tmp size, big endian required in msg header
    if(0!=memcpy_s(&es_header->size,sizeof(es_header->size), &size_in, sizeof(size_in))){
        AESM_DBG_FATAL("memcpy error");
        return PVE_UNEXPECTED_ERROR;
    }
    if(total_size +sizeof(*es_header) >msg_buffer_size){//the input msg body buffer size is not large enough
        AESM_DBG_ERROR("input msg buffer is too small");
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    }
    return AE_SUCCESS;
}

uint32_t CPVEClass::gen_es_msg1(
        uint8_t *msg,
        uint32_t msg_size,
        const gen_endpoint_selection_output_t& es_output)
{
    ae_error_t ret;
    AESM_PROFILE_FUN;
    if(msg_size < PROVISION_REQUEST_HEADER_SIZE)
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    provision_request_header_t *es_header = reinterpret_cast<provision_request_header_t *>(msg);

    ret = prov_es_gen_header(es_header, es_output.xid, msg_size);
    if(AE_SUCCESS != ret){
        AESM_DBG_ERROR("Fail to generate Endpoint Selection Msg1 Header:(ae%d)",ret);
        return ret;
    }

    {
        TLVsMsg tlvs_msg;
        tlv_status_t tlv_status = tlvs_msg.add_es_selector(SE_EPID_PROVISIONING, es_output.selector_id);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("fail to create ES Selector TLV:(ae%d)",ret);
            return ret;
        }
        assert(tlvs_msg.get_tlv_msg_size()<=msg_size - PROVISION_REQUEST_HEADER_SIZE); //The checking should have been done in prov_es_gen_header
        if(0!=memcpy_s(msg+PROVISION_REQUEST_HEADER_SIZE, msg_size-PROVISION_REQUEST_HEADER_SIZE, tlvs_msg.get_tlv_msg(), tlvs_msg.get_tlv_msg_size())){
            AESM_DBG_FATAL("memcpy failed");
            return PVE_UNEXPECTED_ERROR;
        }
        return AE_SUCCESS;
    }
}

#define ES_MSG2_FIELD_COUNT 3
#define ES_FIELD0_MIN_SIZE 3
#define ES_FIELD0_MAX_SIZE (MAX_PATH-1)
uint32_t CPVEClass::proc_es_msg2(
    const uint8_t *msg,
    uint32_t msg_size,
    char server_url[MAX_PATH],
    uint16_t& ttl,
    const uint8_t xid[XID_SIZE],
    uint8_t rsa_signature[RSA_3072_KEY_BYTES],
    signed_pek_t& pek)
{
    uint32_t ae_ret = PVE_MSG_ERROR;
    TLVsMsg tlvs_msg;
    AESM_PROFILE_FUN;
    tlv_status_t tlv_status;
    uint16_t time_in_net;
    const provision_response_header_t *resp_header = (const provision_response_header_t *)msg;
    const uint8_t *resp_body = msg + PROVISION_RESPONSE_HEADER_SIZE;
    if(msg_size<PROVISION_RESPONSE_HEADER_SIZE){//at least response header is available
        AESM_DBG_ERROR("Endpoint selection Msg2 buffer size too small");
        goto final_point;
    }
    //first checking resp header for protocol, version and type
    if(resp_header->protocol != ENDPOINT_SELECTION || resp_header->version!=TLV_VERSION_2 || resp_header->type != TYPE_ES_MSG2){
        AESM_DBG_ERROR("ES Msg2 header error");
        goto final_point;
    }
    ae_ret = check_endpoint_pg_stauts(resp_header);
    if(AE_SUCCESS != ae_ret){
        AESM_DBG_ERROR("Backend report error in ES Msg2 Header:(ae%d)",ae_ret);
        goto final_point;
    }
    if(0!=memcmp(xid, resp_header->xid, XID_SIZE)){
        AESM_DBG_ERROR("XID in ES Msg2 header doesn't match the one in ES Msg1");
        ae_ret = PVE_MSG_ERROR;
        goto final_point;
    }
    uint32_t size; size = GET_BODY_SIZE_FROM_PROVISION_RESPONSE(msg);
    if(size + PROVISION_RESPONSE_HEADER_SIZE != msg_size){ //size information inconsistent
        AESM_DBG_ERROR("message size inconsistent in ES Msg2");
        ae_ret = PVE_MSG_ERROR;
        goto final_point;
    }
    tlv_status = tlvs_msg.init_from_buffer(resp_body, msg_size - static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE));
    ae_ret = tlv_error_2_pve_error(tlv_status);
    if(AE_SUCCESS!=ae_ret){
        AESM_DBG_ERROR("Fail to decode ES Msg2:(ae%d)",ae_ret);
        goto final_point;
    }
    if(tlvs_msg.get_tlv_count() != ES_MSG2_FIELD_COUNT){//three TLVs
        AESM_DBG_ERROR("Invaid number of TLV in ES Msg2");
        ae_ret = PVE_MSG_ERROR;
        goto final_point;
    }
    if(tlvs_msg[0].type != TLV_ES_INFORMATION || tlvs_msg[0].version != TLV_VERSION_1 || tlvs_msg[0].header_size != SMALL_TLV_HEADER_SIZE){//TLV header checking
        AESM_DBG_ERROR("Invalid TLV in ES Msg2");
        ae_ret = PVE_MSG_ERROR;
        goto final_point;
    }

    if(tlvs_msg[0].size<ES_FIELD0_MIN_SIZE||tlvs_msg[0].size>ES_FIELD0_MAX_SIZE){//size checking
        AESM_DBG_ERROR("Invalid TLV in ES Msg2");
        ae_ret = PVE_MSG_ERROR;
        goto final_point;
    }
    if(tlvs_msg[1].type != TLV_SIGNATURE || tlvs_msg[1].version != TLV_VERSION_1 ||
        tlvs_msg[1].header_size!=SMALL_TLV_HEADER_SIZE||tlvs_msg[1].size != RSA_3072_KEY_BYTES+1 ||
        tlvs_msg[1].payload[0] != PEK_3072_PRIV){
        ae_ret = PVE_MSG_ERROR;
        AESM_DBG_ERROR("Invalid Signature TLV: type (tlv%d), version %d, size %d while expected value is (tlv%d,) %d, %d",
            tlvs_msg[1].type, tlvs_msg[1].version, tlvs_msg[1].size,
            TLV_SIGNATURE, TLV_VERSION_1, RSA_3072_KEY_BYTES);
        goto final_point;
    }
    if(tlvs_msg[2].type != TLV_PEK || tlvs_msg[2].version != TLV_VERSION_2 ||
        tlvs_msg[2].header_size!=SMALL_TLV_HEADER_SIZE||tlvs_msg[2].size != sizeof(signed_pek_t)){
            ae_ret = PVE_MSG_ERROR;
        AESM_DBG_ERROR("Invalid PEK TLV: type (tlv%d), version %d, size %d while expected value is (tlv%d), %d, %d",
            tlvs_msg[2].type, tlvs_msg[2].version, tlvs_msg[2].size,
            TLV_PEK, TLV_VERSION_2, sizeof(signed_pek_t));
        goto final_point;
    }
    //skip the byte for KEY_ID
    if(memcpy_s(rsa_signature, RSA_3072_KEY_BYTES, tlvs_msg[1].payload+1, tlvs_msg[1].size-1)!=0){//skip key id
        ae_ret = AE_FAILURE;
        AESM_DBG_ERROR("memcpy failed");
        goto final_point;
    }
    if(memcpy_s(&pek, sizeof(pek), tlvs_msg[2].payload, tlvs_msg[2].size)!=0){
        ae_ret = AE_FAILURE;
        AESM_DBG_ERROR("memcpy failed");
        goto final_point;
    }

    time_in_net = *(uint16_t *)tlvs_msg[0].payload;//TTL in ES
    ttl = lv_ntohs(time_in_net);//First two bytes in payload for TTL (maximal seconds that the URL to be valid)
    if(memcpy_s(server_url, MAX_PATH, tlvs_msg[0].payload+2, tlvs_msg[0].size-2)!=0){//other bytes for URL
        ae_ret = AE_FAILURE;
        AESM_DBG_ERROR("memcpy failed");
        goto final_point;
    }
    server_url[tlvs_msg[0].size-2]='\0';
    ae_ret = AE_SUCCESS;
final_point:
    return ae_ret;
}
