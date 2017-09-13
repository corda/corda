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


#ifdef DBG_LOG
#include "oal/oal.h"
#include <time.h>
#include <stdio.h>
#include <stdarg.h>
#include <assert.h>
#include <ctype.h>
#include <se_stdio.h>
#include <se_string.h>
#include "se_thread.h"
#include "type_length_value.h"
#include "aeerror.h"
#include "aesm_error.h"
#include "sgx_error.h"

static int aesm_trace_level = TRACE_LOG_LEVEL;
static int at_start=1;

se_mutex_t cs;
static ae_error_t init_log_file(void)
{
    char filename[MAX_PATH];
    ae_error_t err = aesm_get_pathname(FT_PERSISTENT_STORAGE, AESM_DBG_LOG_FID, filename, MAX_PATH);
    if(err != AE_SUCCESS)
        return err;
    return AE_SUCCESS;
}

#include <string>
using namespace std;

static const char *get_sgx_status_t_string(sgx_status_t status);
static const char *get_ae_error_t_string(ae_error_t ae_error);
static const char *get_aesm_error_t_string(aesm_error_t aesm_error);
static const char *get_tlv_enum_type_t_string(uint8_t type);

const char *support_tags[] = {
    "sgx",
    "aesm",//matching longer tag before shorter one so aesm should be arranged before ae
    "ae",
    "tlv"
};
#define COUNT_TAGS (sizeof(support_tags)/sizeof(support_tags[0]))
#define TAG_SGX  0
#define TAG_AESM 1
#define TAG_AE   2
#define TAG_TLV  3

#define MAX_BUF_SIZE 4096
std::string internal_log_msg_trans(const std::string& s)
{
    std::string output;
    size_t i;
    const char *p = s.c_str();
    for (i = 0; i < s.length(); ++i){
        if (p[i] == '('){//begin of tag
            size_t start = i + 1;
            while (isspace(p[start]))start++;//skip all space
            int j;
            for (j = 0; j < COUNT_TAGS; ++j){
                int tag_len = strlen(support_tags[j]);
                if (strncmp(p + start, support_tags[j], tag_len) == 0){
                    start += tag_len;
                    break;
                }
            }
            if (j < COUNT_TAGS){//found a potential tag
                while (isspace(p[start]))start++;//skip all space after tag
                if ((p[start] == '-' || p[start] == '+') && isdigit(p[start + 1]) ||
                    isdigit(p[start])){
                    int number = strtol(p + start,NULL, 0);
                    switch (j){
                    case TAG_SGX:
                        output += "(sgx_status_t:";
                        output += get_sgx_status_t_string((sgx_status_t)number);
                        output += ":";
                        break;
                    case TAG_AESM:
                        output += "(aesm_error_t:";
                        output += get_aesm_error_t_string((aesm_error_t)number);
                        output += ":";
                        break;
                    case TAG_AE:
                        output += "(ae_error_t:";
                        output += get_ae_error_t_string((ae_error_t)number);
                        output += ":";
                        break;
                    case TAG_TLV:
                        output += "(TLV:";
                        output += get_tlv_enum_type_t_string((uint8_t)number);
                        output += ":";
                        break;
                    default:
                        output += "(Unknown type:";
                        break;
                    }
                    i = start-1;
                }
                else{
                    output += p[i];
                }
            }
            else{//not found, keep original flags
                output += p[i];
            }
        }
        else{
            output += p[i];
        }
    }
    return output;
}

#define TIME_BUF_SIZE 100
void aesm_internal_log(const char *file_name, int line_no, const char *funname, int level, const char *format, ...)
{
    if(level <= aesm_trace_level){
        if(at_start){
            at_start=0;
            se_mutex_init(&cs);
            init_log_file();
        }
        char filename[MAX_PATH];
        ae_error_t err = aesm_get_cpathname(FT_PERSISTENT_STORAGE, AESM_DBG_LOG_FID, filename, MAX_PATH);
        if(err != AE_SUCCESS)
            return;
        FILE *logfile = NULL;
        se_mutex_lock(&cs);
        logfile = fopen(filename, "a+");
        if(logfile == NULL){
            se_mutex_unlock(&cs);
            return;
        }
        time_t t;
        struct tm time_info;
        va_list varg;
        char time_buf[TIME_BUF_SIZE];
        time(&t);
        struct tm *temp_time_info;
        temp_time_info = localtime(&t);
        memcpy_s(&time_info, sizeof(time_info), temp_time_info, sizeof(*temp_time_info));
        if(strftime(time_buf, TIME_BUF_SIZE, "%c", &time_info)!=0){
           fprintf(logfile, "[%s|%d|%s|%s]",file_name, line_no, funname, time_buf);
        }else{
           fprintf(logfile, "[%s|%d|%s]",file_name, line_no, funname);
        }
        va_start(varg, format);
        char message_buf[MAX_BUF_SIZE];
        vsnprintf(message_buf, MAX_BUF_SIZE-1, format, varg);
        va_end(varg);
        std::string input_message = message_buf;
        std::string output_message = internal_log_msg_trans(input_message);
        fprintf(logfile, "%s\n", output_message.c_str());
        fflush(logfile);
        fclose(logfile);
        se_mutex_unlock(&cs);
    }
}


void aesm_set_log_level(int level)
{
    aesm_trace_level = level;
}

static char half_byte_to_char(int x)
{
    assert(0<=x&&x<=0xF);
    if(0<=x&&x<=9)return (char)('0'+x);
    else return (char)('A'+x-10);
}

void aesm_dbg_format_hex(const uint8_t *data, uint32_t data_len, char *out_buf, uint32_t buf_size)
{
    uint32_t i;
    assert(buf_size>0);
    if(data_len==0){
        out_buf[0]='\0';
        return;
    }
    if(buf_size/3>=data_len){
        for(i=0;i<data_len;i++){
            int low=data[i]&0xF;
            int high=(data[i]>>4)&0xF;
            out_buf[i*3]=half_byte_to_char(high);
            out_buf[i*3+1]=half_byte_to_char(low);
            out_buf[i*3+2]=' ';
        }
        out_buf[data_len*3-1]='\0';
    }else if(buf_size>10){
        uint32_t tcount=buf_size/3-1;
        uint32_t off;
        uint32_t ecount=tcount/2,bcount=tcount-ecount;
        for(i=0;i<bcount;i++){
            int low=data[i]&0xF;
            int high=(data[i]>>4)&0xF;
            out_buf[i*3]=half_byte_to_char(high);
            out_buf[i*3+1]=half_byte_to_char(low);
            out_buf[i*3+2]=' ';
        }
        out_buf[i*3]=out_buf[i*3+1]=out_buf[i*3+2]='.';
        off=i*3+3;
        for(i=0;i<ecount;i++){
            int low=data[data_len-ecount+i]&0xF;
            int high=(data[data_len-ecount+i]>>4)&0xF;
            out_buf[off+i*3]=half_byte_to_char(high);
            out_buf[off+i*3+1]=half_byte_to_char(low);
            out_buf[off+i*3+2]=' ';
        }
        out_buf[off+i*3-1]='\0';
    }else{
        for(i=0;/*i<data_len&&*/i<(buf_size-1)/3;i++){//checking for i<data_len is redundant since first if condition in the function has filtered it
            int low=data[i]&0xF;
            int high=(data[i]>>4)&0xF;
            out_buf[i*3]=half_byte_to_char(high);
            out_buf[i*3+1]=half_byte_to_char(low);
            out_buf[i*3+2]=' ';
        }
        out_buf[i*3]='\0';
    }
}


#define CASE_ENUM_RET_STRING(x) case x: return #x;

//(tlv%d)
static const char *get_tlv_enum_type_t_string(uint8_t type)
{
    switch (type){
        CASE_ENUM_RET_STRING(TLV_CIPHER_TEXT)
        CASE_ENUM_RET_STRING(TLV_BLOCK_CIPHER_TEXT)
        CASE_ENUM_RET_STRING(TLV_BLOCK_CIPHER_INFO)
        CASE_ENUM_RET_STRING(TLV_MESSAGE_AUTHENTICATION_CODE)
        CASE_ENUM_RET_STRING(TLV_NONCE)
        CASE_ENUM_RET_STRING(TLV_EPID_GID)
        CASE_ENUM_RET_STRING(TLV_EPID_SIG_RL)
        CASE_ENUM_RET_STRING(TLV_EPID_GROUP_CERT)
        CASE_ENUM_RET_STRING(TLV_DEVICE_ID)
        CASE_ENUM_RET_STRING(TLV_PS_ID)
        CASE_ENUM_RET_STRING(TLV_EPID_JOIN_PROOF)
        CASE_ENUM_RET_STRING(TLV_EPID_SIG)
        CASE_ENUM_RET_STRING(TLV_EPID_MEMBERSHIP_CREDENTIAL)
        CASE_ENUM_RET_STRING(TLV_EPID_PSVN)
        CASE_ENUM_RET_STRING(TLV_QUOTE)
        CASE_ENUM_RET_STRING(TLV_X509_CERT_TLV)
        CASE_ENUM_RET_STRING(TLV_X509_CSR_TLV)
        CASE_ENUM_RET_STRING(TLV_ES_SELECTOR)
        CASE_ENUM_RET_STRING(TLV_ES_INFORMATION)
        CASE_ENUM_RET_STRING(TLV_FLAGS)
        CASE_ENUM_RET_STRING(TLV_QUOTE_SIG)
        CASE_ENUM_RET_STRING(TLV_PEK)
        CASE_ENUM_RET_STRING(TLV_SIGNATURE)
        CASE_ENUM_RET_STRING(TLV_PLATFORM_INFO)
        CASE_ENUM_RET_STRING(TLV_PWK2)
        CASE_ENUM_RET_STRING(TLV_SE_REPORT)
    default:
        return "Unknown TLV";
    }
}

//(ae%d)
static const char *get_ae_error_t_string(ae_error_t ae_error)
{
    switch (ae_error){
        CASE_ENUM_RET_STRING(AE_SUCCESS)
        CASE_ENUM_RET_STRING(AE_FAILURE)
        CASE_ENUM_RET_STRING(AE_ENCLAVE_LOST)
        CASE_ENUM_RET_STRING(OAL_PARAMETER_ERROR)
        CASE_ENUM_RET_STRING(OAL_PATHNAME_BUFFER_OVERFLOW_ERROR)
        CASE_ENUM_RET_STRING(OAL_FILE_ACCESS_ERROR)
        CASE_ENUM_RET_STRING(OAL_CONFIG_FILE_ERROR)
        CASE_ENUM_RET_STRING(OAL_NETWORK_UNAVAILABLE_ERROR)
        CASE_ENUM_RET_STRING(OAL_NETWORK_BUSY)
        CASE_ENUM_RET_STRING(OAL_NETWORK_RESEND_REQUIRED)
        CASE_ENUM_RET_STRING(OAL_PROXY_SETTING_ASSIST)
        CASE_ENUM_RET_STRING(OAL_THREAD_ERROR)
        CASE_ENUM_RET_STRING(OAL_THREAD_TIMEOUT_ERROR)
        CASE_ENUM_RET_STRING(AE_PSVN_UNMATCHED_ERROR)
        CASE_ENUM_RET_STRING(AE_SERVER_NOT_AVAILABLE)
        CASE_ENUM_RET_STRING(AE_INVALID_PARAMETER)
        CASE_ENUM_RET_STRING(AE_READ_RAND_ERROR)
        CASE_ENUM_RET_STRING(AE_OUT_OF_MEMORY_ERROR)
        CASE_ENUM_RET_STRING(AE_INSUFFICIENT_DATA_IN_BUFFER)
        CASE_ENUM_RET_STRING(QE_UNEXPECTED_ERROR)
        CASE_ENUM_RET_STRING(QE_PARAMETER_ERROR)
        CASE_ENUM_RET_STRING(QE_EPIDBLOB_ERROR)
        CASE_ENUM_RET_STRING(QE_REVOKED_ERROR)
        CASE_ENUM_RET_STRING(QE_SIGRL_ERROR)
        CASE_ENUM_RET_STRING(PVE_UNEXPECTED_ERROR)
        CASE_ENUM_RET_STRING(PVE_PARAMETER_ERROR)
        CASE_ENUM_RET_STRING(PVE_EPIDBLOB_ERROR)
        CASE_ENUM_RET_STRING(PVE_INSUFFICIENT_MEMORY_ERROR)
        CASE_ENUM_RET_STRING(PVE_INTEGRITY_CHECK_ERROR)
        CASE_ENUM_RET_STRING(PVE_SIGRL_INTEGRITY_CHECK_ERROR)
        CASE_ENUM_RET_STRING(PVE_SERVER_REPORTED_ERROR)
        CASE_ENUM_RET_STRING(PVE_PEK_SIGN_ERROR)
        CASE_ENUM_RET_STRING(PVE_MSG_ERROR)
        CASE_ENUM_RET_STRING(PVE_REVOKED_ERROR)
        CASE_ENUM_RET_STRING(PVE_SESSION_OUT_OF_ORDER_ERROR)
        CASE_ENUM_RET_STRING(PVE_SERVER_BUSY_ERROR)
        CASE_ENUM_RET_STRING(PVE_PERFORMANCE_REKEY_NOT_SUPPORTED)
        CASE_ENUM_RET_STRING(LE_UNEXPECTED_ERROR)
        CASE_ENUM_RET_STRING(LE_INVALID_PARAMETER)
        CASE_ENUM_RET_STRING(LE_GET_EINITTOKEN_KEY_ERROR)
        CASE_ENUM_RET_STRING(LE_INVALID_ATTRIBUTE)
        CASE_ENUM_RET_STRING(LE_INVALID_PRIVILEGE_ERROR)
        CASE_ENUM_RET_STRING(LE_WHITELIST_UNINITIALIZED_ERROR)
        CASE_ENUM_RET_STRING(LE_CALC_LIC_TOKEN_ERROR)
        // PSE ERROR CASES
        CASE_ENUM_RET_STRING(PSE_PAIRING_BLOB_SEALING_ERROR)
        CASE_ENUM_RET_STRING(PSE_PAIRING_BLOB_UNSEALING_ERROR)
        CASE_ENUM_RET_STRING(PSE_PAIRING_BLOB_INVALID_ERROR)

        // PSE_OP ERROR CASES
        CASE_ENUM_RET_STRING(PSE_OP_PARAMETER_ERROR)
        CASE_ENUM_RET_STRING(PSE_OP_INTERNAL_ERROR)
        CASE_ENUM_RET_STRING(PSE_OP_MAX_NUM_SESSION_REACHED)
        CASE_ENUM_RET_STRING(PSE_OP_SESSION_INVALID)
        CASE_ENUM_RET_STRING(PSE_OP_SERVICE_MSG_ERROR)
        CASE_ENUM_RET_STRING(PSE_OP_EPHEMERAL_SESSION_INVALID)
        CASE_ENUM_RET_STRING(PSE_OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR)
        CASE_ENUM_RET_STRING(PSE_OP_UNKNWON_REQUEST_ERROR)
        CASE_ENUM_RET_STRING(PSE_OP_PSDA_BUSY_ERROR)
        CASE_ENUM_RET_STRING(PSE_OP_LTPB_SEALING_OUT_OF_DATE)

        // PSDA ERROR CODES
        CASE_ENUM_RET_STRING(AESM_PSDA_NOT_AVAILABLE)
        CASE_ENUM_RET_STRING(AESM_PSDA_INTERNAL_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSDA_NEED_REPAIRING)
        CASE_ENUM_RET_STRING(AESM_PSDA_LT_SESSION_INTEGRITY_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSDA_NOT_PROVISONED_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSDA_PROTOCOL_NOT_SUPPORTED)
        CASE_ENUM_RET_STRING(AESM_PSDA_PLATFORM_KEYS_REVOKED)
        CASE_ENUM_RET_STRING(AESM_PSDA_SESSION_LOST)
        CASE_ENUM_RET_STRING(AESM_PSDA_WRITE_THROTTLED)

        // PSE_Pr ERROR CASES
        CASE_ENUM_RET_STRING(PSE_PR_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_PARAMETER_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_ENCLAVE_EXCEPTION)
        CASE_ENUM_RET_STRING(PSE_PR_CALL_ORDER_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_ASN1DER_DECODING_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_PAIRING_BLOB_SIZE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_BAD_POINTER_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_SIGNING_CSR_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_MSG_SIGNING_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_INSUFFICIENT_MEMORY_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_BUFFER_TOO_SMALL_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_S3_DATA_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_KEY_PAIR_GENERATION_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_DERIVE_SMK_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_CREATE_REPORT_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_HASH_CALC_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_HMAC_CALC_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_ID_CALC_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_HMAC_COMPARE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_GA_COMPARE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_TASK_INFO_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_MSG_COMPARE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_GID_MISMATCH_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_PR_CALC_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_PARAM_CERT_SIZE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_CERT_SIZE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_NO_OCSP_RESPONSE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_X509_PARSE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_READ_RAND_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_INTERNAL_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_ENCLAVE_BRIDGE_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_ENCLAVE_LOST_ERROR)

        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_SIG_INVALID)             
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_SIG_REVOKED_IN_GROUPRL)     
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_SIG_REVOKED_IN_PRIVRL)  
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_SIG_REVOKED_IN_SIGRL)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_SIG_REVOKED_IN_VERIFIERRL)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_UNKNOWN_ERROR)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_NOT_IMPLEMENTED)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_BAD_ARG_ERR)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_NO_MEMORY_ERR)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_MATH_ERR)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_DIVIDED_BY_ZERO_ERR)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_UNDERFLOW_ERR)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_HASH_ALGORITHM_NOT_SUPPORTED)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_RAND_MAX_ITER_ERR)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_DUPLICATE_ERR)
        CASE_ENUM_RET_STRING(PSE_PR_PCH_EPID_INCONSISTENT_BASENAME_SET_ERR)

        // AESM PSE_Pr ERROR CASES
        CASE_ENUM_RET_STRING(AESM_PSE_PR_ERROR_GETTING_GROUP_ID_FROM_ME)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_INIT_QUOTE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_GET_QUOTE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_INSUFFICIENT_MEMORY_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BUFFER_TOO_SMALL)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_MAX_SIGRL_ENTRIES_EXCEEDED)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_MAX_PRIVRL_ENTRIES_EXCEEDED)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_GET_SIGRL_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_GET_OCSPRESP_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_CERT_SAVE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_CERT_LOAD_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_CERT_DELETE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PSDA_LOAD_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PSDA_PROVISION_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PSDA_NOT_PROVISIONED)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PSDA_GET_GROUP_ID)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PSDA_LTP_EXCHANGE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PSDA_LTP_S1_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PERSISTENT_STORAGE_DELETE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PERSISTENT_STORAGE_OPEN_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PERSISTENT_STORAGE_WRITE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_PERSISTENT_STORAGE_READ_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BAD_POINTER_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_CALL_ORDER_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_INTERNAL_ERROR)
        CASE_ENUM_RET_STRING(AESM_PRSE_HECI_INIT_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_LOAD_VERIFIER_CERT_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_EXCEPTION)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_STATUS_MALFORMEDREQUEST)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_STATUS_INTERNALERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_STATUS_TRYLATER)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_STATUS_SIGREQUIRED)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_STATUS_UNAUTHORIZED)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_INTERNAL_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_NO_NONCE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_NONCE_VERIFY_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_OCSP_RESPONSE_VERIFY_ERROR)
        CASE_ENUM_RET_STRING(AESP_PSE_PR_OCSP_RESPONSE_CERT_COUNT_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_ICLS_CLIENT_MISSING_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_NO_OCSP_RESPONSE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_RL_RESP_HEADER_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_RL_SERVER_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_INVALID_GID)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_GID_REVOKED)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_INVALID_QUOTE)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_INVALID_REQUEST)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_UNKNOWN_PROTOCOL_RESPONSE)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_SERVER_BUSY)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_INTEGRITY_CHECK_FAIL)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_INCORRECT_SYNTAX)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_INCOMPATIBLE_VERSION)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_TRANSACTION_STATE_LOST)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_PROTOCOL_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_INTERNAL_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_UNKNOWN_GENERAL_RESPONSE)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_MSG1_GENERATE)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_MSG2_RESPONSE_HEADER_INTEGRITY)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_MSG3_GENERATE)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_MSG4_RESPONSE_HEADER_INTEGRITY)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_MSG4_TLV_INTEGRITY)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_MSG4_PLATFORM_INFO_BLOB_SIZE)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_MSG4_LEAF_CERTIFICATE_SIZE)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_MSG4_UNEXPECTED_TLV_TYPE)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_INVALID_URL)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_BACKEND_NOT_INITIALIZED)
        CASE_ENUM_RET_STRING(AESM_NLTP_NO_LTP_BLOB)
        CASE_ENUM_RET_STRING(AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP)
        CASE_ENUM_RET_STRING(AESM_NLTP_MAY_NEED_UPDATE_LTP)
        CASE_ENUM_RET_STRING(AESM_NLTP_OLD_EPID11_RLS)
        CASE_ENUM_RET_STRING(AESM_PCP_NEED_PSE_UPDATE)
        CASE_ENUM_RET_STRING(AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE)
        CASE_ENUM_RET_STRING(AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE)
        CASE_ENUM_RET_STRING(AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR)
        CASE_ENUM_RET_STRING(AESM_PCP_SIMPLE_EPID_PROVISION_ERROR)
        CASE_ENUM_RET_STRING(AESM_NPC_DONT_NEED_PSEP)
        CASE_ENUM_RET_STRING(AESM_NPC_NO_PSE_CERT)
        CASE_ENUM_RET_STRING(AESM_NPC_DONT_NEED_UPDATE_PSEP)
        CASE_ENUM_RET_STRING(AESM_NPC_MAY_NEED_UPDATE_PSEP)
        CASE_ENUM_RET_STRING(AESM_NEP_DONT_NEED_EPID_PROVISIONING)
        CASE_ENUM_RET_STRING(AESM_NEP_DONT_NEED_UPDATE_PVEQE)
        CASE_ENUM_RET_STRING(AESM_NEP_PERFORMANCE_REKEY)
        CASE_ENUM_RET_STRING(AESM_NEP_MAY_NEED_UPDATE)
        CASE_ENUM_RET_STRING(AESM_CP_ATTESTATION_FAILURE)
        CASE_ENUM_RET_STRING(AESM_LTP_PSE_CERT_REVOKED)
        CASE_ENUM_RET_STRING(AESM_LTP_SIMPLE_LTP_ERROR)
        CASE_ENUM_RET_STRING(AESM_PSE_PR_GET_PRIVRL_ERROR)
        CASE_ENUM_RET_STRING(AESM_NETWORK_TIMEOUT)

        CASE_ENUM_RET_STRING(PSW_UPDATE_REQUIRED)
        CASE_ENUM_RET_STRING(PSE_OP_ERROR_KDF_MISMATCH)
        CASE_ENUM_RET_STRING(AESM_AE_OUT_OF_EPC)

        CASE_ENUM_RET_STRING(PVE_PROV_ATTEST_KEY_NOT_FOUND)
        CASE_ENUM_RET_STRING(PVE_INVALID_REPORT)
        CASE_ENUM_RET_STRING(PVE_XEGDSK_SIGN_ERROR)

        // PCE ERROR CODES
        CASE_ENUM_RET_STRING(PCE_UNEXPECTED_ERROR)
        CASE_ENUM_RET_STRING(PCE_INVALID_PRIVILEGE)
        CASE_ENUM_RET_STRING(PCE_INVALID_REPORT)

        CASE_ENUM_RET_STRING(LE_WHITE_LIST_QUERY_BUSY)
        CASE_ENUM_RET_STRING(AESM_AE_NO_DEVICE)
        CASE_ENUM_RET_STRING(EXTENDED_GROUP_NOT_AVAILABLE)
    default:
        return "Unknown ae_error_t";
    }
}

//(aesm%d)
static const char *get_aesm_error_t_string(aesm_error_t aesm_error)
{
    switch (aesm_error){
        CASE_ENUM_RET_STRING(AESM_SUCCESS)
        CASE_ENUM_RET_STRING(AESM_UNEXPECTED_ERROR)
        CASE_ENUM_RET_STRING(AESM_NO_DEVICE_ERROR)
        CASE_ENUM_RET_STRING(AESM_PARAMETER_ERROR)
        CASE_ENUM_RET_STRING(AESM_EPIDBLOB_ERROR)
        CASE_ENUM_RET_STRING(AESM_EPID_REVOKED_ERROR)
        CASE_ENUM_RET_STRING(AESM_GET_LICENSETOKEN_ERROR)
        CASE_ENUM_RET_STRING(AESM_SESSION_INVALID)
        CASE_ENUM_RET_STRING(AESM_MAX_NUM_SESSION_REACHED)
        CASE_ENUM_RET_STRING(AESM_PSDA_UNAVAILABLE)
        CASE_ENUM_RET_STRING(AESM_KDF_MISMATCH)
        CASE_ENUM_RET_STRING(AESM_EPH_SESSION_FAILED)
        CASE_ENUM_RET_STRING(AESM_LONG_TERM_PAIRING_FAILED)
        CASE_ENUM_RET_STRING(AESM_NETWORK_ERROR)
        CASE_ENUM_RET_STRING(AESM_NETWORK_BUSY_ERROR)
        CASE_ENUM_RET_STRING(AESM_PROXY_SETTING_ASSIST)
        CASE_ENUM_RET_STRING(AESM_FILE_ACCESS_ERROR)
        CASE_ENUM_RET_STRING(AESM_SGX_PROVISION_FAILED)
        CASE_ENUM_RET_STRING(AESM_SERVICE_STOPPED)
        CASE_ENUM_RET_STRING(AESM_BUSY)
        CASE_ENUM_RET_STRING(AESM_BACKEND_SERVER_BUSY)
        CASE_ENUM_RET_STRING(AESM_UPDATE_AVAILABLE)
        CASE_ENUM_RET_STRING(AESM_OUT_OF_MEMORY_ERROR)
        CASE_ENUM_RET_STRING(AESM_MSG_ERROR)
        CASE_ENUM_RET_STRING(AESM_ENABLE_SGX_DEVICE_FAILED)
        CASE_ENUM_RET_STRING(AESM_PLATFORM_INFO_BLOB_INVALID_SIG)
        CASE_ENUM_RET_STRING(AESM_OUT_OF_EPC)
        CASE_ENUM_RET_STRING(AESM_SERVICE_UNAVAILABLE)
        CASE_ENUM_RET_STRING(AESM_UNRECOGNIZED_PLATFORM)
    default:
        return "Unknow aesm_error_t";
    }
}

//(sgx)
static const char *get_sgx_status_t_string(sgx_status_t status)
{
    switch (status){
        CASE_ENUM_RET_STRING(SGX_SUCCESS)

        CASE_ENUM_RET_STRING(SGX_ERROR_UNEXPECTED)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_PARAMETER)
        CASE_ENUM_RET_STRING(SGX_ERROR_OUT_OF_MEMORY)
        CASE_ENUM_RET_STRING(SGX_ERROR_ENCLAVE_LOST)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_STATE)

        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_FUNCTION)
        CASE_ENUM_RET_STRING(SGX_ERROR_OUT_OF_TCS)
        CASE_ENUM_RET_STRING(SGX_ERROR_ENCLAVE_CRASHED )
        CASE_ENUM_RET_STRING(SGX_ERROR_ECALL_NOT_ALLOWED)
        CASE_ENUM_RET_STRING(SGX_ERROR_OCALL_NOT_ALLOWED)

        CASE_ENUM_RET_STRING(SGX_ERROR_UNDEFINED_SYMBOL)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_ENCLAVE)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_ENCLAVE_ID)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_SIGNATURE)
        CASE_ENUM_RET_STRING(SGX_ERROR_NDEBUG_ENCLAVE)
        CASE_ENUM_RET_STRING(SGX_ERROR_OUT_OF_EPC)
        CASE_ENUM_RET_STRING(SGX_ERROR_NO_DEVICE)
        CASE_ENUM_RET_STRING(SGX_ERROR_MEMORY_MAP_CONFLICT)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_METADATA)
        CASE_ENUM_RET_STRING(SGX_ERROR_DEVICE_BUSY)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_VERSION)
        CASE_ENUM_RET_STRING(SGX_ERROR_MODE_INCOMPATIBLE)
        CASE_ENUM_RET_STRING(SGX_ERROR_ENCLAVE_FILE_ACCESS)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_MISC)

        CASE_ENUM_RET_STRING(SGX_ERROR_MAC_MISMATCH)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_ATTRIBUTE)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_CPUSVN)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_ISVSVN)
        CASE_ENUM_RET_STRING(SGX_ERROR_INVALID_KEYNAME)

        CASE_ENUM_RET_STRING(SGX_ERROR_SERVICE_UNAVAILABLE)
        CASE_ENUM_RET_STRING(SGX_ERROR_SERVICE_TIMEOUT)
        CASE_ENUM_RET_STRING(SGX_ERROR_AE_INVALID_EPIDBLOB)
        CASE_ENUM_RET_STRING(SGX_ERROR_SERVICE_INVALID_PRIVILEGE)
        CASE_ENUM_RET_STRING(SGX_ERROR_EPID_MEMBER_REVOKED)
        CASE_ENUM_RET_STRING(SGX_ERROR_UPDATE_NEEDED)
        CASE_ENUM_RET_STRING(SGX_ERROR_NETWORK_FAILURE)
        CASE_ENUM_RET_STRING(SGX_ERROR_AE_SESSION_INVALID)
        CASE_ENUM_RET_STRING(SGX_ERROR_BUSY)
        CASE_ENUM_RET_STRING(SGX_ERROR_MC_NOT_FOUND)
        CASE_ENUM_RET_STRING(SGX_ERROR_MC_NO_ACCESS_RIGHT)
        CASE_ENUM_RET_STRING(SGX_ERROR_MC_USED_UP)
        CASE_ENUM_RET_STRING(SGX_ERROR_MC_OVER_QUOTA)
        CASE_ENUM_RET_STRING(SGX_ERROR_KDF_MISMATCH)

    default:
        return "Unknown sgx_status_t";
    }
}

#endif

