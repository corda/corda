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
 * File: type_length_value.h
 * Description: Header file for data structure and macro definition of the TLV, the type length value encoding format of Provision Message
 *
 * Unaligned data will be used and for TLV, no data structure but some macros to extract information from TLV(due to var size of some fields)
 * A tlv_info_t structure is defined for decoded TLV header information
 * special macro to begin with upper case letter and end with lower case letter to indicate it will extract one field from the msg, such as
 *    EPID_GID_TLV_gid
 * to extract the gid field from and EPID_GID_TLV
 *
 *To encoding TLV:
 *  i) declare array of tlv_info_t and filling type/version/size of each TLV
 *  ii) call get_tlv_msg_size to calculate size of encoded buffer
 *  iii) prepare the output buffer in tlv_msg_t (set both msg_buf and msg_size)
 *  iv) call tlv_msg_init to initialize tlv headers of all sub-tlv
 *  v) use function like cipher_text_tlv_get_encrypted_text(...) to get pointer to each payload component
 *  vi) copy correspondent data into each payload
 *To decoding TLV:
 *  i) initialize tlv_msg_t to input buffer (msg_buf and msg_size)
 *  ii) call read_tlv_info to get correpondent tlv_info_t array
 *  iii) use function like cipher_text_tlv_get_encrypted_text(...) to get pointer to each payload component
 *           or access size field of tlv_info_t directly to get size of payload
 *Usually, there're multiple level of TLVs. To save memory which is critical inside enclave
 *   we need reuse the memory buffer for all levels of TLV.
 * So in encoding TLV, we need
 *  go through step i) and ii) from inner-most level TLV to out-most level of TLV to define
 *   tlv_info_t of all level of TLVs and get msg size for them
 *     Usually, the tlv_info_t of outer level is dependent on size of inner level
 *  After that, we could prepare the output buffer which is step iii) according to size of outmost level TLVs
 *   call tlv_msg_init to initialize TLV headers and get start address of payload (where inner TLV will share the memory)
 *      from outmost level to innermost level which is step iv) and v)
 *   Now we could copy-in correpondent data from innermost level to outermost level and do correpondent encryption if required
 *      inplace encryption function provided so that no new memory required
 * In decoding TLV, it is simpler, just decode from outmost-level to innermost-level and do decryption if required
 */

#ifndef _PVE_TLV_H
#define _PVE_TLV_H
#include "epid/common/types.h"
#include "sgx_urts.h"
#include <string.h>
#include <stdlib.h>
#include <assert.h>
#include "oal/oal.h"
#include "tlv_common.h"
#include "se_sig_rl.h"
#include "pce_cert.h"
#include "sgx_report.h"
#include "se_memcpy.h"

#define FOUR_BYTES_SIZE_TYPE 128 /*mask used in type of TLV to indicate that 'size' field uses 4 bytes*/

#ifndef UINT16_MAX
#define UINT16_MAX 0xFFFF
#endif
#ifndef UINT32_MAX
#define UINT32_MAX 0xFFFFFFFFU
#endif

#define IS_FOUR_BYTES_SIZE_TYPE(x) (((x)&FOUR_BYTES_SIZE_TYPE)!=0)
#define GET_TLV_TYPE(x)            ((uint8_t)((x)&~FOUR_BYTES_SIZE_TYPE))

typedef enum _tlv_status_t {
    TLV_SUCCESS=0,
    TLV_OUT_OF_MEMORY_ERROR=1,
    TLV_INVALID_PARAMETER_ERROR,
    TLV_INVALID_MSG_ERROR,
    TLV_UNKNOWN_ERROR,
    TLV_MORE_TLVS,           /*There're more TLVs in the encoded buffer than user's expectation*/
    TLV_INSUFFICIENT_MEMORY, /*There'ld be more data in the TLV buffer according to the partially decoded data*/
    TLV_INVALID_FORMAT,      /*Invalid data format in the TLV buffer to be decoded*/
    TLV_UNSUPPORTED          /*the feature has not been supported such as version is later than supported version*/
}tlv_status_t;

/*usually, we could initialize header_size by UNKOWN_TLV_HEADER_SIZE
 * but sometimes, we could initialize it by LARGE_TLV_HEADER_SIZE if we want to always use 4 bytes for size even though it is small such as in EpidSignature TLV
 */
#define UNKNOWN_TLV_HEADER_SIZE 0
#define TLV_HEADER_SIZE_OFFSET  2
#define SMALL_TLV_HEADER_SIZE   4
#define LARGE_TLV_HEADER_SIZE   6
#define MAX_TLV_HEADER_SIZE     6     /*an upper bound for TLV header size*/
#define SHORT_TLV_MAX_SIZE      UINT16_MAX
/*It defines a TLV information
 *All those information is encoded inside a TLV but not in this structure
 */
typedef struct _tlv_info_t{
    uint8_t type;         /*type of tlv must be between 0 and 127 before encoding*/
    uint8_t version;
    uint16_t header_size; /*header_size used to easy query begin and end address of TLV*/
    uint32_t size;        /*2 or 4 bytes size after encoding but always 4 bytes in this structure*/
    uint8_t *payload;     /*pointer to payload of the TLV*/
}tlv_info_t;

typedef struct _tlv_msg_t{
    uint8_t *msg_buf;
    uint32_t msg_size;
}tlv_msg_t;

#define SIGRL_CERT_PREFIX_SIZE  (sizeof(se_sig_rl_t)-sizeof(SigRl))    /*size for version and type*/
#define SIGRL_CERT_HEADER_SIZE  (sizeof(se_sig_rl_t)-sizeof(SigRlEntry))
#define SIGRL_CERT_SIZE(sigrl_count) (sizeof(se_sig_rl_t)+((sigrl_count)-1)*sizeof(SigRlEntry)+2*SE_ECDSA_SIGN_SIZE)
#define EPID_SIGNATURE_HEADER_SIZE (sizeof(EpidSignature)-sizeof(NrProof))
#define EPID_SIGNATURE_SIZE(sign_count) (sizeof(EpidSignature)+((sign_count)-1)*sizeof(NrProof))


/*Function to decode a buffer which contains a list of TLV containers
 *msg.msg_buf: pointer to the start address of the buffer
 *msg.msg_size: number of bytes of the buffer
 *tlvs_count: input/output parameter, the input *tlvs_count gives size of array infos and offsets
 *              when the function return successful (TLV_SUCCESS) or there're too many TLVs (TLV_MORE_TLVS),
 *              the output of *tlvs_count returns the number of TLVs in the msg
 *infos:     output parameter, infos[i] gives the decoded TLV structure information of the i'th TLV in the msg
 *return:      TLV_SUCCESS on success or other value to indicate error
 */
tlv_status_t read_tlv_infos(const tlv_msg_t& msg, uint32_t *tlvs_count, tlv_info_t infos[]);

/*Function to return the header size in encoded TLV buffer*/
uint32_t get_tlv_header_size(const tlv_info_t *info);

/*Function to return the estimated upper bound of length of TLV in bytes given length in bytes of payload.
 * Currently, it returns the exact value for TLV encoding, it should not be used for TLV decoding
 *return 0 if there's any error
 */
inline static uint32_t get_tlv_total_size(size_t payload_size)
{
    if(payload_size>UINT16_MAX){  /*6 bytes TLV header*/
        if(payload_size>UINT32_MAX-LARGE_TLV_HEADER_SIZE)/*overflow of uint32_t, return 0 to indicate error*/
            return 0;
        return static_cast<uint32_t>(payload_size+LARGE_TLV_HEADER_SIZE);
    }else{
        return static_cast<uint32_t>(payload_size+SMALL_TLV_HEADER_SIZE);/*4 bytes TLV header*/
    }
}


/*Function to return number in bytes of a msg with some encoded TLVs
 *tlvs_count gives number of TLVs in the msg and infos[i] gives info of the i'th TLV
 */
uint32_t get_tlv_msg_size(uint32_t tlvs_count, const tlv_info_t infos[]);

/*Function to initialize a msg of some encoded TLVs
 *Before calling the function, all fields except payload of infos[.] has been initialized
 *After calling to the function, the TLV header of all TLVs will be fill in and payload of info[.] will be initialized
 *After calling to the function, we could use payload field
 *tlvs_count:    input parameter gives number of TLV in the msg
 *infos:        input parameter where infos[i] is the tlv structure information of the i'th TLV
 *tlv_msg.msg_buf:  the msg buffer to be initialized
 *tlv_msg.msg_size: input parameter gives the size of the msg buffer, it must be result of function get_tlv_msg_count(tlvs_count, infos);
 *The function return TLV_SUCCESS on success and other to indicate any error
 */
tlv_status_t tlv_msg_init(uint32_t tlvs_count, tlv_info_t infos[], const tlv_msg_t& tlv_msg);

/*Function used to initialize msg header only without checking payload size
 *The function will return size in bytes of the header to be filled
 *   the payload field will be set but payload size not checked
 *msg: The buffer to fill
 *info: the tlv_info of the TLV
 */
uint32_t tlv_msg_init_one_header(uint8_t msg[MAX_TLV_HEADER_SIZE], tlv_info_t& info);

/*Macro used to crach tlv structure, the pointer type of payload must be uint8_t *
 *The alignment of all types used inside payload should be 1 (which means no alignment)
 *cipher text TLV
 */
#define CIPHER_TEXT_TLV_PAYLOAD_SIZE(text_size)         ((text_size)+1) /*size of payload*/
#define CIPHER_TEXT_TLV_SIZE(text_size)                 get_tlv_total_size(CIPHER_TEXT_TLV_PAYLOAD_SIZE(text_size)) /*size of TLV*/
uint8_t *cipher_text_tlv_get_key_id(const tlv_info_t& info);          /*given tlv_info_t, return pointer to key_id*/
tlv_msg_t cipher_text_tlv_get_encrypted_text(const tlv_info_t& info); /*given tlv_info_t return tlv_msg for encrypted text so that we could restart decode*/
/*block cipher text TLV*/
#define BLOCK_CIPHER_TEXT_TLV_PAYLOAD_SIZE(text_size)   ((text_size)+IV_SIZE) /*size of payload*/
#define BLOCK_CIPHER_TEXT_TLV_SIZE(text_size)           get_tlv_total_size(BLOCK_CIPHER_TEXT_TLV_PAYLOAD_SIZE(text_size)) /*size of TLV*/
struct _tlv_iv_t;/*an incomplete type for iv only*/
typedef struct _tlv_iv_t tlv_iv_t;
tlv_iv_t *block_cipher_tlv_get_iv(const tlv_info_t& info);
tlv_msg_t block_cipher_tlv_get_encrypted_text(const tlv_info_t& info);
#define BLOCK_CIPHER_TEXT_SIZE_FROM_PAYLOAD_SIZE(psize) ((psize)-IV_SIZE)

/*block cipher info TLV*/
#define BLOCK_CIPHER_INFO_TLV_PAYLOAD_SIZE()            SK_SIZE              /*size of payload*/
#define BLOCK_CIPHER_INFO_TLV_SIZE()                    get_tlv_total_size(BLOCK_CIPHER_INFO_TLV_PAYLOAD_SIZE())
struct _tlv_sk_t; /*an incomplete type for SK only*/
typedef struct _tlv_sk_t tlv_sk_t;
tlv_sk_t *block_cipher_info_tlv_get_sk(const tlv_info_t& info);

/*message authentication code TLV*/
#define MAC_TLV_PAYLOAD_SIZE(mac_size)                  (mac_size)
#define MAC_TLV_SIZE(mac_size)                          get_tlv_total_size(MAC_TLV_PAYLOAD_SIZE(mac_size))
struct _tlv_mac_t; /*an incomplete type for MAC only*/
typedef struct _tlv_mac_t tlv_mac_t;
tlv_mac_t *mac_tlv_get_mac(const tlv_info_t& info);
/*NONCE TLV*/
#define NONCE_TLV_PAYLOAD_SIZE(nonce_size)              (nonce_size)
#define NONCE_TLV_SIZE(nonce_size)                      get_tlv_total_size(NONCE_TLV_PAYLOAD_SIZE(nonce_size))
struct _tlv_nonce_t;
typedef struct _tlv_nonce_t tlv_nonce_t;
tlv_nonce_t *nonce_tlv_get_nonce(const tlv_info_t& info);

/*EPID GID TLV*/
#define EPID_GID_TLV_PAYLOAD_SIZE()                     (sizeof(GroupId))
#define EPID_GID_TLV_SIZE()                             get_tlv_total_size(EPID_GID_TLV_PAYLOAD_SIZE())
struct _tlv_gid_t; /*an incomplete type for GID only*/
typedef struct _tlv_gid_t tlv_gid_t;
tlv_sk_t *epid_gid_tlv_get_gid(const tlv_info_t& info);

/*EPID SigRl TLV*/
#define EPID_SIGRL_TLV_PAYLOAD_SIZE(sigrl_count)        (sizeof(se_sig_rl_t)+sizeof(SigRlEntry)*(sigrl_count)-sizeof(SigRlEntry)+ECDSA_SIGN_SIZE*2)
#define EPID_SIGRL_TLV_SIZE(sigrl_count)                (sigrl_count>0?get_tlv_total_size(EPID_SIGRL_TLV_PAYLOAD_SIZE(sigrl_count)):0)

/*EPID SigRl PSVN TLV*/
#define EPID_SIGRL_PSVN_TLV_PAYLOAD_SIZE()              (sizeof(psvn_t))
#define EPID_SIGRL_PSVN_TLV_SIZE()                      get_tlv_total_size(EPID_SIGRL_PSVN_TLV_PAYLOAD_SIZE())
psvn_t *epid_sigrl_psvn_tlv_get_psvn(const tlv_info_t& info);

/*EPID group certificate TLV*/
#define EPID_GROUP_CERT_TLV_PAYLOAD_SIZE()              (sizeof(signed_epid_group_cert_t))
#define EPID_GROUP_CERT_TLV_SIZE()                      get_tlv_total_size(EPID_GROUP_CERT_TLV_PAYLOAD_SIZE())

/*Device ID TLV*/
#define DEVICE_ID_TLV_PAYLOAD_SIZE()                    (sizeof(ppid_t)+sizeof(fmsp_t)+sizeof(psvn_t))
#define DEVICE_ID_TLV_SIZE()                            get_tlv_total_size(DEVICE_ID_TLV_PAYLOAD_SIZE())
ppid_t *device_id_tlv_get_ppid(const tlv_info_t& info);

/*PPID TLV*/
#define PPID_TLV_PAYLOAD_SIZE()                         (sizeof(ppid_t))
#define PPID_TLV_SIZE()                                 get_tlv_total_size(PPID_TLV_PAYLOAD_SIZE())

/*PWK2 TLV*/
#define PWK2_TLV_PAYLOAD_SIZE()                         (SK_SIZE)
#define PWK2_TLV_SIZE()                                 get_tlv_total_size(PWK2_TLV_PAYLOAD_SIZE())

/*PlatfromInfo TLV*/
#define PLATFORM_INFO_TLV_PAYLOAD_SIZE()                (sizeof(bk_platform_info_t))
#define PLATFORM_INFO_TLV_SIZE()                        get_tlv_total_size(PLATFORM_INFO_TLV_PAYLOAD_SIZE())
fmsp_t *platform_info_tlv_get_fmsp(const tlv_info_t& info);
psvn_t *platform_info_tlv_get_psvn(const tlv_info_t& info);

/*SE_REPORT_TLV*/
#define SE_REPORT_TLV_PAYLOAD_SIZE()                    (sizeof(sgx_report_body_t)+2*ECDSA_SIGN_SIZE)
#define SE_REPORT_TLV_SIZE()                            (LARGE_TLV_HEADER_SIZE+SE_REPORT_TLV_PAYLOAD_SIZE())

/*PSID TLV*/
#define PSID_TLV_PAYLOAD_SIZE()                         (sizeof(psid_t))
#define PSID_TLV_SIZE()                                 get_tlv_total_size(PSID_TLV_PAYLOAD_SIZE())
psid_t *psid_tlv_get_psid(const tlv_info_t& info);

/*Join Proof TLV*/
#define EPID_JOIN_PROOF_TLV_PAYLOAD_SIZE()              (JOIN_PROOF_SIZE+BLIND_ESCROW_SIZE)
#define EPID_JOIN_PROOF_TLV_SIZE()                      get_tlv_total_size(EPID_JOIN_PROOF_TLV_PAYLOAD_SIZE())

/*EPID Signature TLV*/
#define EPID_SIGNATURE_TLV_PAYLOAD_SIZE(sign_count)     (sizeof(EpidSignature)+sizeof(NrProof)*(sign_count)-sizeof(NrProof))
#define EPID_SIGNATURE_TLV_SIZE(sign_count)             (LARGE_TLV_HEADER_SIZE+EPID_SIGNATURE_TLV_PAYLOAD_SIZE(sign_count)) /*always use large header size for Signature TLV*/

/*EPID Membership Credential TLV*/
#define MEMBERSHIP_CREDENTIAL_TLV_PAYLOAD_SIZE()        (sizeof(membership_credential_with_escrow_t))
#define MEMBERSHIP_CREDENTIAL_TLV_SIZE()                get_tlv_total_size(MEMBERSHIP_CREDENTIAL_TLV_PAYLOAD_SIZE())
FpElemStr *membership_credential_tlv_get_x(const tlv_info_t& info);
G1ElemStr *membership_credential_tlv_get_A(const tlv_info_t& info);

/*FLAGS TLV*/
#define FLAGS_TLV_PAYLOAD_SIZE()    FLAGS_SIZE
#define FLAGS_TLV_SIZE()            get_tlv_total_size(FLAGS_TLV_PAYLOAD_SIZE())

#define ES_SELECTOR_TLV_PAYLOAD_SIZE()                  2
#define ES_SELECTOR_TLV_SIZE()                          get_tlv_total_size(ES_SELECTOR_TLV_PAYLOAD_SIZE())
uint8_t *es_selector_tlv_get_es_type(const tlv_info_t& info);
uint8_t *es_selector_tlv_get_selector_id(const tlv_info_t& info);

#define PCE_CERT_TLV_PAYLOAD_SIZE()                     sizeof(pce_tcb_cert_t)
#define PCE_CERT_TLV_SIZE()                             get_tlv_total_size(PCE_CERT_TLV_PAYLOAD_SIZE())

#define PCE_SIGNATURE_TLV_PAYLOAD_SIZE()                (2*SE_ECDSA_SIGN_SIZE+sizeof(sgx_report_t))
#define PCE_SIGNATURE_TLV_SIZE()                        get_tlv_total_size(PCE_SIGNATURE_TLV_PAYLOAD_SIZE())

class TLVsMsg{
    uint32_t num_infos;
    tlv_info_t* infos;
    tlv_msg_t msg;
    CLASS_UNCOPYABLE(TLVsMsg)
protected:
    void clear(){
        if(msg.msg_buf!=NULL){free(msg.msg_buf);msg.msg_buf=NULL;msg.msg_size=0;}
        if(infos!=NULL){free(infos);infos=NULL;num_infos=0;}
    }
    tlv_status_t alloc_more_buffer(uint32_t buffer, tlv_msg_t& new_buf);
    tlv_status_t create_new_info(tlv_info_t *& new_info){
        if(num_infos==0){
            assert(infos==NULL);
            infos = (tlv_info_t *)malloc(sizeof(tlv_info_t));
            if(infos==NULL) return TLV_OUT_OF_MEMORY_ERROR;
            num_infos=1;
            new_info = infos;
        }else{
            tlv_info_t * p = (tlv_info_t *)malloc(sizeof(tlv_info_t)*(num_infos+1));
            if(p==NULL){
                return TLV_OUT_OF_MEMORY_ERROR;
            }
            memcpy_s(p, sizeof(tlv_info_t)*num_infos, infos, sizeof(tlv_info_t)*num_infos);
            free(infos);
            infos = p;
            new_info = infos + (num_infos);
            num_infos ++;
        }
        return TLV_SUCCESS;
    }
public:
    TLVsMsg(){
        num_infos = 0;
        infos = NULL;
        msg.msg_size = 0;
        msg.msg_buf = NULL;
    }
    ~TLVsMsg(){
        clear();
    }
    tlv_status_t init_from_buffer(const uint8_t *msg, uint32_t msg_size);
    tlv_status_t init_from_tlv_msg(const tlv_msg_t& tlv_msg);
    uint32_t get_tlv_count()const{return num_infos;}
    tlv_info_t& operator[](uint32_t x){
        assert(x<num_infos&&infos!=NULL);
        return infos[x];
    }
    const tlv_info_t& operator[](uint32_t x)const{
        assert(x<num_infos&&infos!=NULL);
        return infos[x];
    }
    uint32_t get_tlv_msg_size()const{return msg.msg_size;}
    const uint8_t *get_tlv_msg()const{return msg.msg_buf;}
    /*Functions to add different types of TLV into the TLVsMsg*/
    tlv_status_t add_cipher_text(const uint8_t *text, uint32_t len, uint8_t key_id);
    tlv_status_t add_block_cipher_text(const uint8_t iv[IV_SIZE],const uint8_t *text, uint32_t len);
    tlv_status_t add_block_cipher_info(const uint8_t sk[SK_SIZE]);
    tlv_status_t add_mac(const uint8_t mac[MAC_SIZE]);
    tlv_status_t add_nonce(const uint8_t *nonce, uint32_t nonce_size);
    tlv_status_t add_epid_gid(const GroupId& gid);
    tlv_status_t add_platform_info(const bk_platform_info_t& pi);
    tlv_status_t add_pce_report_sign(const sgx_report_body_t& report, const uint8_t ecdsa_sign[64]);
    tlv_status_t add_psid(const psid_t *psid);
    tlv_status_t add_flags(const flags_t *flags);
    tlv_status_t add_es_selector(uint8_t protocol, uint8_t selector_id);
    tlv_status_t add_quote(const uint8_t *quote_data, uint32_t quote_size);
    tlv_status_t add_quote_signature(const uint8_t *quote_signature, uint32_t sign_size);
    tlv_status_t add_x509_csr(const uint8_t *csr_data, uint32_t csr_size);
};

#endif
