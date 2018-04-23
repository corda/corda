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


#ifndef _AESM_LOGIC_H_
#define _AESM_LOGIC_H_
#include "sgx_urts.h"
#include "aesm_error.h"
#include "arch.h"
#include "aeerror.h"
#include "tlv_common.h"
#include "se_thread.h"
#include "internal/se_stdio.h"
#include "internal/se_memcpy.h"
#include "internal/uncopyable.h"
#include "oal/oal.h"
#include <time.h>
#include <string.h>
#include "se_wrapper.h"
#include "upse/platform_info_blob.h"

#include "default_url_info.hh"

/*File to declare AESMLogic Class and facility class(Mutex/Lock) for it*/

const uint32_t THREAD_TIMEOUT = 5000;

class AESMLogicMutex{
    CLASS_UNCOPYABLE(AESMLogicMutex)
public:
    AESMLogicMutex() {se_mutex_init(&mutex);}
    ~AESMLogicMutex() { se_mutex_destroy(&mutex);}
    void lock() { se_mutex_lock(&mutex); }
    void unlock() { se_mutex_unlock(&mutex); }
private:
    se_mutex_t mutex;
};

class AESMLogicLock {
    CLASS_UNCOPYABLE(AESMLogicLock)
public:
    explicit AESMLogicLock(AESMLogicMutex& cs) :_cs(cs) { _cs.lock(); }
    ~AESMLogicLock() { _cs.unlock(); }
private:
    AESMLogicMutex& _cs;
};

#define QE_PROD_ID  1
#define PSE_PROD_ID 2


typedef struct _endpoint_selection_infos_t endpoint_selection_infos_t;
class AESMLogic{
public:
    static AESMLogicMutex _qe_pve_mutex, _pse_mutex, _le_mutex; /*mutex to lock external interface*/
private:
    static aesm_thread_t qe_thread, pse_thread;
    static psvn_t _qe_psvn, _pse_psvn, _pce_psvn;   /*different cpu svn used although they're same. We should only access _qe_psvn/_pce_svn when qe_pve_mutex is acquired and only access _pse_psvn when pse_mutext is acquired*/
    static bool _is_qe_psvn_set, _is_pse_psvn_set, _is_pce_psvn_set;
    static uint32_t active_extended_epid_group_id;
    static ae_error_t set_psvn(uint16_t prod_id, uint16_t isv_svn, sgx_cpu_svn_t cpu_svn, uint32_t mrsigner_index);
    static ae_error_t save_unverified_white_list(const uint8_t *white_list_cert, uint32_t white_list_cert_size);
    static ae_error_t get_white_list_size_without_lock(uint32_t *white_list_cert_size);
public:
    static ae_error_t get_qe_isv_svn(uint16_t& isv_svn);     /*This function should only be called when _qe_pve_mutex is acquired*/
    static ae_error_t get_qe_cpu_svn(sgx_cpu_svn_t& cpu_svn);/*This function should only be called when _qe_pve_mutex is acquired*/
    static ae_error_t get_pse_isv_svn(uint16_t& isv_svn);    /*This function should only be called when _pse_mutex is acquired*/
    static ae_error_t get_pse_cpu_svn(sgx_cpu_svn_t& cpu_svn);/*This function should only be called when _pse_mutex is acquired*/
    static ae_error_t get_pce_isv_svn(uint16_t& isv_svn);
    static uint32_t   get_active_extended_epid_group_id(void);

    static ae_error_t service_start();
    static void service_stop();

    static bool is_service_running();

    static sgx_status_t get_launch_token(const enclave_css_t* signature, 
        const sgx_attributes_t* attribute, 
        sgx_launch_token_t* launch_token);

    static aesm_error_t get_launch_token(
        const uint8_t *mrenclave,  uint32_t mrenclave_size,
        const uint8_t *public_key, uint32_t public_key_size,
        const uint8_t *se_attributes, uint32_t se_attributes_size,
        uint8_t * lictoken, uint32_t lictoken_size);

    static aesm_error_t init_quote(uint8_t *target_info, uint32_t target_info_size,
        uint8_t *gid, uint32_t gid_size);

    static aesm_error_t get_quote(const uint8_t *report, uint32_t report_size,
        uint32_t quote_type,
        const uint8_t *spid, uint32_t spid_size,
        const uint8_t *nonce, uint32_t nonce_size,
        const uint8_t *sigrl, uint32_t sigrl_size,
        uint8_t *qe_report, uint32_t qe_report_size,
        uint8_t *quote, uint32_t buf_size);

    static aesm_error_t create_session(
        uint32_t* session_id,
        uint8_t* se_dh_msg1, uint32_t se_dh_msg1_size);

    static aesm_error_t exchange_report(
        uint32_t session_id,
        const uint8_t* se_dh_msg2, uint32_t se_dh_msg2_size,
        uint8_t* se_dh_msg3, uint32_t se_dh_msg3_size);

    static aesm_error_t close_session(
        uint32_t session_id);

    static aesm_error_t invoke_service(
        const uint8_t* pse_message_req, uint32_t pse_message_req_size,
        uint8_t* pse_message_resp, uint32_t pse_message_resp_size);

    static aesm_error_t get_ps_cap(
        uint64_t* ps_cap);

    static uint32_t endpoint_selection(endpoint_selection_infos_t& es_info);
    enum {GIDMT_UNMATCHED, GIDMT_NOT_AVAILABLE, GIDMT_MATCHED,GIDMT_UNEXPECTED_ERROR};
    static uint32_t is_gid_matching_result_in_epid_blob(const GroupId& gid);

    static aesm_error_t report_attestation_status(
        uint8_t* platform_info, uint32_t platform_info_size,
        uint32_t attestation_status,
        uint8_t* update_info, uint32_t update_info_size);
		
    static aesm_error_t white_list_register(
        const uint8_t *white_list_cert, uint32_t white_list_cert_size);

    static aesm_error_t get_white_list_size(
        uint32_t* white_list_cert_size);

    static aesm_error_t get_white_list(
        uint8_t *white_list_cert, uint32_t buf_size);

    static aesm_error_t get_extended_epid_group_id(
        uint32_t* extended_epid_group_id);

    static aesm_error_t switch_extended_epid_group( 
        uint32_t extended_epid_group_id );
};
#endif

