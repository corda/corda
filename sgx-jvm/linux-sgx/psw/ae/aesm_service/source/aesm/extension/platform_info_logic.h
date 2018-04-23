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

#ifndef _PLATFORM_INFO_LOGIC_H_
#define _PLATFORM_INFO_LOGIC_H_
#include "sgx_urts.h"
#include "aesm_error.h"
#include "aeerror.h"
#include "oal/oal.h"
#include "upse/platform_info_blob.h"

struct update_pse_thread_func_arg;

/*File to declare PlatformInfoLogic Class which is platform independent*/
class PlatformInfoLogic{
public:
    static ae_error_t update_pse_thread_func(const platform_info_blob_wrapper_t* p_platform_info, uint32_t attestation_status);
    static aesm_error_t report_attestation_status(
        uint8_t* platform_info, uint32_t platform_info_size,
        uint32_t attestation_status,
        uint8_t* update_info, uint32_t update_info_size);
    static ae_error_t check_ltp_thread_func(bool& is_new_pairing);//call get_long_term_pairing_thread_status().start_check_ltp(is_new_pairing) to invoke the function with time_out
    static ae_error_t create_session_pre_internal(void);
    static ae_error_t need_epid_provisioning(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static ae_error_t need_pse_cert_provisioning();
    static ae_error_t attestation_failure_in_pse_cert_provisioning(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static ae_error_t pse_cert_provisioning_helper(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static ae_error_t need_long_term_pairing(const platform_info_blob_wrapper_t* platformInfoBlobWrapper);
    
    static uint32_t latest_psda_svn(const platform_info_blob_wrapper_t* p_platform_info_blob);    
    static uint16_t latest_pse_svn(const platform_info_blob_wrapper_t* p_platform_info_blob);

    static bool cse_gid_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool sgx_gid_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool cpu_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool qe_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool pce_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool pse_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool psda_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool ps_collectively_not_uptodate(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool old_epid11_rls(const platform_info_blob_wrapper_t* p_platform_info_blob);
    static bool performance_rekey_available(const platform_info_blob_wrapper_t* p_platform_info_blob);
private:
    static ae_error_t get_sgx_epid_group_flags(const platform_info_blob_wrapper_t* p_platform_info_blob, uint8_t* flags);
    static ae_error_t get_pse_evaluation_flags(const platform_info_blob_wrapper_t* p_platform_info_blob, uint16_t* flags);
    static ae_error_t get_sgx_tcb_evaluation_flags(const platform_info_blob_wrapper_t* p_platform_info_blob, uint16_t* flags);

};
#endif

