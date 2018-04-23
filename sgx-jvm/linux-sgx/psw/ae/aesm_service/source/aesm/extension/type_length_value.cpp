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


/**
 * File: type_length_value.cpp
 * Description: Cpp file for code to decoding/encoding of the TLV, the type length value encoding format of Provision Message
 */

#include "type_length_value.h"
#include "sgx.h"
#include "se_wrapper.h"
#include "oal/oal.h"
#include <assert.h>

//Function to write tlv header into a msg according to tlv info, the input buffer size should be at least MAX_TLV_HEADER_SIZE
static tlv_status_t write_tlv_header(uint8_t *msg, const tlv_info_t *info)
{
    uint8_t type = info->type;
    if(info->size>UINT16_MAX ||info->header_size == LARGE_TLV_HEADER_SIZE){//6 bytes header
        uint32_t size = info->size; //4 bytes in size field
        type |= FOUR_BYTES_SIZE_TYPE;
        msg[0] = type;
        msg[1] = info->version;
        size = _htonl(size);
        if(memcpy_s(&msg[2], sizeof(uint32_t), &size, sizeof(size))!=0){
            AESM_DBG_ERROR("memcpy failed");
            return TLV_UNKNOWN_ERROR;
        }
    }else{//4 bytes header
        uint16_t size = (uint16_t)info->size;//2 bytes in size field
        msg[0] = type;
        msg[1] = info->version;
        size = _htons(size); 
        if(memcpy_s(&msg[2], sizeof(uint16_t), &size, sizeof(size))!=0){
            AESM_DBG_ERROR("memcpy failed");
            return TLV_UNKNOWN_ERROR;
        }
    }
    return TLV_SUCCESS;
}

//Function to read the first tlv info from msg and return length in bytes of the TLV header (so it provides offset of TLV payload)
// the function return 0 on error
static uint32_t read_tlv_info(const tlv_msg_t& msg, tlv_info_t *info)
{
    if(msg.msg_size<SMALL_TLV_HEADER_SIZE){//The TLV header has at least 4 bytes
        return 0;
    }
    //read TLV type and version, the highest bit of type to tell whether size is 2 or 4 bytes is removed
    info->type = GET_TLV_TYPE(msg.msg_buf[0]);
    info->version = msg.msg_buf[1];
    if(IS_FOUR_BYTES_SIZE_TYPE(msg.msg_buf[0])){//four bytes or two bytes of size
        if(msg.msg_size<LARGE_TLV_HEADER_SIZE)return 0;
        (void)memcpy_s(&info->size, sizeof(info->size), &msg.msg_buf[2], sizeof(uint32_t));
        info->size = _ntohl(info->size);
        info->payload = msg.msg_buf+LARGE_TLV_HEADER_SIZE;
        info->header_size = LARGE_TLV_HEADER_SIZE;
        return LARGE_TLV_HEADER_SIZE;//6 bytes TLV header
    }else{
        uint16_t size = 0;
        (void)memcpy_s(&size, sizeof(uint16_t), &msg.msg_buf[2], sizeof(uint16_t)); 
        info->size = (uint32_t)_ntohs(size); //reorder to form little endian size value and extended by 0
        info->payload = msg.msg_buf+SMALL_TLV_HEADER_SIZE;
        info->header_size = SMALL_TLV_HEADER_SIZE;
        return SMALL_TLV_HEADER_SIZE; //4 bytes TLV header
    }
}

static bool decode_one_tlv(tlv_msg_t& msg, tlv_info_t *info)
{
    if(msg.msg_size<SMALL_TLV_HEADER_SIZE)return false;
    uint32_t header_size = read_tlv_info(msg, info);
    if(header_size == 0) return false;
    uint32_t total_size = header_size + info->size;
    if(msg.msg_size<total_size)return false;
    msg.msg_buf += total_size;
    msg.msg_size -= total_size;
    return true;
}

static uint8_t get_tlv_header_size_from_payload_size(uint32_t payload_size)
{
    if(payload_size>UINT16_MAX){//6 bytes TLV header
        if(payload_size>UINT32_MAX-LARGE_TLV_HEADER_SIZE)//overflow of uint32_t, return 0 to indicate error
            return 0;
        return LARGE_TLV_HEADER_SIZE;
    }else{
        return SMALL_TLV_HEADER_SIZE;//4 bytes TLV header
    }
}

uint32_t get_tlv_header_size(const tlv_info_t *info)
{
    assert(info->header_size == LARGE_TLV_HEADER_SIZE||
        info->header_size == SMALL_TLV_HEADER_SIZE);
    return info->header_size;
}

uint32_t get_tlv_total_size(const tlv_info_t &info)
{
    return get_tlv_header_size(&info)+info.size;
}

static uint32_t calc_one_tlv_size(const tlv_info_t& infos)
{
    uint32_t the_size = 0;
    if(infos.header_size == UNKNOWN_TLV_HEADER_SIZE)
        the_size = get_tlv_total_size(infos.size);
    else
        the_size = get_tlv_total_size(infos);
    return the_size;
}


static tlv_status_t tlv_msg_init_one_tlv(tlv_info_t* infos, const tlv_msg_t& tlv_msg)
{
    uint16_t hsize;
    tlv_status_t status = TLV_SUCCESS;
    if(infos->header_size == UNKNOWN_TLV_HEADER_SIZE)
        hsize = get_tlv_header_size_from_payload_size(infos->size); //TLV header size
    else
        hsize = (uint8_t)infos->header_size;
    uint32_t tsize = hsize+infos->size; //size of header and payload
    if(tlv_msg.msg_size<tsize)return TLV_INSUFFICIENT_MEMORY;
    if((status=write_tlv_header(tlv_msg.msg_buf , infos))!=TLV_SUCCESS)
        return status; //initialize the header of the tlv but the payload is not initialized
    infos->header_size = hsize;
    infos->payload = tlv_msg.msg_buf + hsize; //initialize the payload of the tlv_info
    if(tsize<tlv_msg.msg_size){
        return TLV_MORE_TLVS;
    }
    return TLV_SUCCESS;
}


uint8_t *cipher_text_tlv_get_key_id(const tlv_info_t& info)
{
    assert(info.type == TLV_CIPHER_TEXT && info.size>=1);
    assert(info.payload!=NULL);
    return info.payload;
}

tlv_msg_t cipher_text_tlv_get_encrypted_text(const tlv_info_t& info)
{
    assert(info.type == TLV_CIPHER_TEXT && info.size>=1);
    assert(info.payload!=NULL);
    tlv_msg_t tlv_msg;
    tlv_msg.msg_buf = info.payload+1;
    tlv_msg.msg_size = info.size -1;
    return tlv_msg;
}

tlv_iv_t *block_cipher_tlv_get_iv(const tlv_info_t& info)
{
    assert(info.type == TLV_BLOCK_CIPHER_TEXT && info.size >= IV_SIZE);
    assert(info.payload!=NULL);
    return reinterpret_cast<tlv_iv_t *>(info.payload);
}

tlv_msg_t block_cipher_tlv_get_encrypted_text(const tlv_info_t& info)
{
    assert(info.type == TLV_BLOCK_CIPHER_TEXT && info.size >= IV_SIZE);
    assert(info.payload!=NULL);
    tlv_msg_t tlv_msg;
    tlv_msg.msg_buf = info.payload + IV_SIZE;
    tlv_msg.msg_size = info.size - IV_SIZE;
    return tlv_msg;
}

fmsp_t *platform_info_tlv_get_fmsp(const tlv_info_t& info)
{
    assert(info.type == TLV_PLATFORM_INFO && info.size == PLATFORM_INFO_TLV_PAYLOAD_SIZE());
    assert(info.payload!=NULL);
    return reinterpret_cast<fmsp_t *>(info.payload+sizeof(psvn_t)+sizeof(sgx_isv_svn_t)+sizeof(CUR_PCE_ID));
}

psvn_t *platform_info_tlv_get_psvn(const tlv_info_t& info)
{
    assert(info.type == TLV_PLATFORM_INFO && info.size == PLATFORM_INFO_TLV_PAYLOAD_SIZE());
    assert(info.payload!=NULL);
    return reinterpret_cast<psvn_t *>(info.payload);
}


tlv_status_t TLVsMsg::init_from_tlv_msg(const tlv_msg_t& tlv_msg)
{
    clear();
    msg.msg_size = tlv_msg.msg_size;
    msg.msg_buf = (uint8_t *)malloc(msg.msg_size);
    if(msg.msg_buf == NULL){
        msg.msg_size = 0;
        AESM_DBG_ERROR("malloc failed");
        return TLV_OUT_OF_MEMORY_ERROR;
    }
    if(memcpy_s(msg.msg_buf, msg.msg_size, tlv_msg.msg_buf, tlv_msg.msg_size)!=0){
        AESM_DBG_ERROR("memcpy failed");
        return TLV_UNKNOWN_ERROR;
    }
    tlv_msg_t tlv_tmp = msg;
    tlv_info_t one_info;
    tlv_info_t *new_info = NULL;
    while(tlv_tmp.msg_size>0){
        if(decode_one_tlv(tlv_tmp, &one_info)){
            tlv_status_t ret = create_new_info(new_info);
            if(ret != TLV_SUCCESS) return ret;
            if(memcpy_s(new_info, sizeof(*new_info), &one_info, sizeof(one_info))!=0){
                AESM_DBG_ERROR("memcpy failed");
               return TLV_UNKNOWN_ERROR;
            }
#ifdef DBG_LOG
            char dbg_str[256];
            aesm_dbg_format_hex(new_info->payload, new_info->size, dbg_str, 256);
            AESM_DBG_TRACE("Decode One TLV: type (tlv %d), size %u, version %d, payload:%s",new_info->type, new_info->size, (int)new_info->version,dbg_str);
#endif
        }else{
            return TLV_INVALID_MSG_ERROR;
        }
    }
    return TLV_SUCCESS;
}

tlv_status_t TLVsMsg::init_from_buffer(const uint8_t *msg_buf, uint32_t msg_size)
{
     tlv_msg_t tlv_msg;
     tlv_msg.msg_buf=const_cast<uint8_t *>(msg_buf);
     tlv_msg.msg_size = msg_size;
     return init_from_tlv_msg(tlv_msg);
}

tlv_status_t TLVsMsg::alloc_more_buffer(uint32_t new_size, tlv_msg_t& new_buf)
{
    if(msg.msg_buf == NULL){
        assert(msg.msg_size==0);
        msg.msg_buf = (uint8_t *)malloc(new_size);
        if(msg.msg_buf == NULL){
            return TLV_OUT_OF_MEMORY_ERROR;
        }
        msg.msg_size = new_size;
        new_buf = msg;
    }else{
        uint8_t *old_p = msg.msg_buf;
        uint8_t *p = (uint8_t *)malloc(msg.msg_size+new_size);
        if(p==NULL){
            return (TLV_OUT_OF_MEMORY_ERROR);
        }
        if(0!=memcpy_s(p,msg.msg_size+new_size, old_p, msg.msg_size)){
            free(p);
            return TLV_UNKNOWN_ERROR;
        }
        size_t i;
        for(i=0;i<num_infos;++i){
            infos[i].payload = p + (infos[i].payload - old_p);//update payload
        }
        new_buf.msg_buf = p + msg.msg_size;
        new_buf.msg_size = new_size;
        msg.msg_buf = p;
        msg.msg_size += new_size;
        free(old_p);
    }
    return TLV_SUCCESS;
}

#ifdef DBG_LOG
#define ADD_TLV_DBG_INFO \
{\
    char dbg_str[256]; \
    aesm_dbg_format_hex(new_info->payload, new_info->size, dbg_str, 256);\
    AESM_DBG_INFO("create TLV: type (tlv %d), size %u, version %d, payload %s", new_info->type, new_info->size, (int)new_info->version, dbg_str);\
}
#else
#define ADD_TLV_DBG_INFO 
#endif

tlv_status_t TLVsMsg::add_cipher_text(const uint8_t *text, uint32_t len, uint8_t key_id)
{
    tlv_info_t one_info;
    one_info.header_size = UNKNOWN_TLV_HEADER_SIZE;
    one_info.payload = NULL;
    one_info.size = CIPHER_TEXT_TLV_PAYLOAD_SIZE(len);
    one_info.type = TLV_CIPHER_TEXT;
    one_info.version = TLV_VERSION_1;
    uint32_t size = ::calc_one_tlv_size(one_info);
    tlv_msg_t new_buf;
    tlv_info_t *new_info = NULL;
    tlv_status_t ret = alloc_more_buffer(size, new_buf);
    if(ret != TLV_SUCCESS)
        return ret;
    ret = create_new_info(new_info);
    if(ret != TLV_SUCCESS)
        return ret;
    if((ret = ::tlv_msg_init_one_tlv(&one_info, new_buf)) !=TLV_SUCCESS){
        return ret;
    }
    *cipher_text_tlv_get_key_id(one_info)=key_id;
    new_buf = cipher_text_tlv_get_encrypted_text(one_info);
    if(memcpy_s(new_buf.msg_buf, new_buf.msg_size, text, len)!=0){
        return TLV_UNKNOWN_ERROR;
    }
    if(memcpy_s(new_info, sizeof(*new_info), &one_info, sizeof(one_info))!=0){
         return TLV_UNKNOWN_ERROR;
    }
    ADD_TLV_DBG_INFO
    return TLV_SUCCESS;
}

tlv_status_t TLVsMsg::add_block_cipher_text(const uint8_t iv[IV_SIZE],const uint8_t *text, uint32_t len)
{
    tlv_info_t one_info;
    one_info.header_size = UNKNOWN_TLV_HEADER_SIZE;
    one_info.payload = NULL;
    one_info.size = BLOCK_CIPHER_TEXT_TLV_PAYLOAD_SIZE(len);
    one_info.type = TLV_BLOCK_CIPHER_TEXT;
    one_info.version = TLV_VERSION_1;
    uint32_t size = ::calc_one_tlv_size(one_info);
    tlv_msg_t new_buf;
    tlv_info_t *new_info = NULL;
    tlv_status_t ret = alloc_more_buffer(size, new_buf);
    if(ret != TLV_SUCCESS)
        return ret;
    ret = create_new_info(new_info);
    if(ret != TLV_SUCCESS)
        return ret;
    if((ret=::tlv_msg_init_one_tlv(&one_info, new_buf))!=TLV_SUCCESS){
        return ret;
    }
    new_buf = block_cipher_tlv_get_encrypted_text(one_info);
    if(len>0&&text!=NULL){
        if(memcpy_s(new_buf.msg_buf, new_buf.msg_size, text, len)!=0){
            return (TLV_UNKNOWN_ERROR);
        }
    }
    if(memcpy_s(block_cipher_tlv_get_iv(one_info), IV_SIZE, iv, IV_SIZE)!=0){
        return (TLV_UNKNOWN_ERROR);
    }
    if(memcpy_s(new_info, sizeof(*new_info), &one_info, sizeof(one_info))!=0){
        return TLV_UNKNOWN_ERROR;
    }
    ADD_TLV_DBG_INFO
    return TLV_SUCCESS;
}

tlv_status_t TLVsMsg::add_block_cipher_info(const uint8_t sk[SK_SIZE])
{
    tlv_info_t one_info;
    one_info.header_size = UNKNOWN_TLV_HEADER_SIZE;
    one_info.payload = NULL;
    one_info.size = BLOCK_CIPHER_INFO_TLV_PAYLOAD_SIZE();
    one_info.type = TLV_BLOCK_CIPHER_INFO;
    one_info.version = TLV_VERSION_1;
    uint32_t size = ::calc_one_tlv_size(one_info);
    tlv_msg_t new_buf;
    tlv_info_t *new_info = NULL;
    tlv_status_t ret = alloc_more_buffer(size, new_buf);
    if(ret!=TLV_SUCCESS)
        return ret;
    ret = create_new_info(new_info);
    if(ret!=TLV_SUCCESS)
        return ret;
    if((ret=::tlv_msg_init_one_tlv(&one_info, new_buf))!=TLV_SUCCESS){
        return ret;
    }
    if(memcpy_s(one_info.payload, one_info.size, sk, SK_SIZE)!=0){
        return (TLV_UNKNOWN_ERROR);
    }
    if(memcpy_s(new_info, sizeof(*new_info),&one_info, sizeof(one_info))!=0)
        return TLV_UNKNOWN_ERROR;
    ADD_TLV_DBG_INFO
    return TLV_SUCCESS;
}

#define ADD_TLV_BY_DATA_SIZE(data_type, data, data_size) \
    tlv_info_t one_info; \
    one_info.header_size = UNKNOWN_TLV_HEADER_SIZE; \
    one_info.payload = NULL;\
    one_info.size = data_size;\
    one_info.type = data_type; \
    one_info.version = TLV_VERSION_1; \
    uint32_t size = ::calc_one_tlv_size(one_info);\
    tlv_msg_t new_buf; \
    tlv_info_t *new_info = NULL; \
    tlv_status_t ret = alloc_more_buffer(size, new_buf); \
    if(ret!=TLV_SUCCESS){ \
        return ret; \
    } \
    ret = create_new_info(new_info); \
    if(ret != TLV_SUCCESS) \
        return ret; \
    if((ret=::tlv_msg_init_one_tlv(&one_info, new_buf))!=TLV_SUCCESS){ \
        return ret; \
    }\
    if(memcpy_s(one_info.payload, one_info.size, data, data_size)!=0){\
        return (TLV_UNKNOWN_ERROR);\
    }\
    if(memcpy_s(new_info, sizeof(*new_info), &one_info, sizeof(one_info))!=0) \
        return TLV_UNKNOWN_ERROR; \
    ADD_TLV_DBG_INFO \
    return TLV_SUCCESS;

tlv_status_t TLVsMsg::add_mac(const uint8_t mac[MAC_SIZE])
{
    ADD_TLV_BY_DATA_SIZE(TLV_MESSAGE_AUTHENTICATION_CODE, mac, MAC_SIZE)
}

tlv_status_t TLVsMsg::add_nonce(const uint8_t *nonce, uint32_t nonce_size)
{
    ADD_TLV_BY_DATA_SIZE(TLV_NONCE, nonce, nonce_size)
}


tlv_status_t TLVsMsg::add_epid_gid(const GroupId& gid)
{
    ADD_TLV_BY_DATA_SIZE(TLV_EPID_GID, &gid, sizeof(GroupId))
}


tlv_status_t TLVsMsg::add_quote(const uint8_t *quote_data, uint32_t quote_size)
{
    ADD_TLV_BY_DATA_SIZE(TLV_QUOTE, quote_data, quote_size)
}


tlv_status_t TLVsMsg::add_x509_csr(const uint8_t *csr_data, uint32_t csr_size)
{
    ADD_TLV_BY_DATA_SIZE(TLV_X509_CSR_TLV, csr_data, csr_size)
}


tlv_status_t TLVsMsg::add_quote_signature(const uint8_t *quote_signature, uint32_t sign_size)
{
    tlv_info_t one_info;
    one_info.header_size = LARGE_TLV_HEADER_SIZE;//always use large tlv for sigrl and epid sig
    one_info.payload = NULL;
    one_info.size = sign_size;
    one_info.type = TLV_QUOTE_SIG;
    one_info.version = TLV_VERSION_1;
    uint32_t size = ::calc_one_tlv_size(one_info);
    tlv_msg_t new_buf;
    tlv_info_t *new_info = NULL;
    tlv_status_t ret = alloc_more_buffer(size, new_buf);
    if(ret!=TLV_SUCCESS)
        return ret;
    ret = create_new_info(new_info);
    if(ret!=TLV_SUCCESS)
        return ret;
    if((ret=::tlv_msg_init_one_tlv(&one_info, new_buf))!=TLV_SUCCESS){
        return (TLV_UNKNOWN_ERROR);
    }
    if(memcpy_s(one_info.payload, one_info.size, quote_signature, sign_size)!=0){
        return (TLV_UNKNOWN_ERROR);
    }
    if(memcpy_s(new_info, sizeof(*new_info),&one_info, sizeof(one_info))!=0)
        return TLV_UNKNOWN_ERROR;
    ADD_TLV_DBG_INFO
    return TLV_SUCCESS;
}

tlv_status_t TLVsMsg::add_es_selector(uint8_t protocol, uint8_t selector_id)
{
    uint8_t buf[2];
    buf[0]=protocol;
    buf[1]=selector_id;
    ADD_TLV_BY_DATA_SIZE(TLV_ES_SELECTOR, buf, static_cast<uint32_t>(2*sizeof(uint8_t)))
}

tlv_status_t TLVsMsg::add_psid(const psid_t *psid)
{
    ADD_TLV_BY_DATA_SIZE(TLV_PS_ID, psid, sizeof(psid_t))
}

tlv_status_t TLVsMsg::add_platform_info(const bk_platform_info_t& pi)
{
    ADD_TLV_BY_DATA_SIZE(TLV_PLATFORM_INFO, &pi, sizeof(pi))
}

tlv_status_t TLVsMsg::add_flags(const flags_t *flags)
{
    ADD_TLV_BY_DATA_SIZE(TLV_FLAGS, flags, sizeof(flags_t))
}

tlv_status_t TLVsMsg::add_pce_report_sign(const sgx_report_body_t& report, const uint8_t ecdsa_sign[64])
{
    tlv_info_t one_info;
    one_info.header_size = LARGE_TLV_HEADER_SIZE;
    one_info.payload = NULL;
    one_info.size = static_cast<uint32_t>(sizeof(report)+64);
    one_info.type = TLV_SE_REPORT;
    one_info.version = TLV_VERSION_1;
    uint32_t size = ::calc_one_tlv_size(one_info);
    tlv_msg_t new_buf;
    tlv_info_t *new_info = NULL;
    tlv_status_t ret = alloc_more_buffer(size, new_buf);
    if(ret!=TLV_SUCCESS){
        return ret;
    }
    ret = create_new_info(new_info);
    if(ret != TLV_SUCCESS)
        return ret;
    if((ret=::tlv_msg_init_one_tlv(&one_info, new_buf))!=TLV_SUCCESS){
        return ret;
    }
    if(memcpy_s(one_info.payload, one_info.size, &report, sizeof(report))!=0){
        return (TLV_UNKNOWN_ERROR);
    }
    if(memcpy_s(one_info.payload+sizeof(report), one_info.size-sizeof(report), ecdsa_sign, 64)!=0){
        return (TLV_UNKNOWN_ERROR);
    }
    if(memcpy_s(new_info, sizeof(*new_info), &one_info, sizeof(one_info))!=0)
        return TLV_UNKNOWN_ERROR;
    ADD_TLV_DBG_INFO
    return TLV_SUCCESS;
}

