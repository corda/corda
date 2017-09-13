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
#pragma once

#include "AEClass.h"
#include "aeerror.h"
#include "provision_msg.h"
#include "pce_cert.h"
#include "sgx_report.h"
#include "epid_pve_type.h"
#include "se_sig_rl.h"

class CPCEClass: public SingletonEnclave<CPCEClass>
{
    friend class Singleton<CPCEClass>;
    friend class SingletonEnclave<CPCEClass>;
    static aesm_enclave_id_t get_enclave_fid(){return PCE_ENCLAVE_FID;}
protected:
    CPCEClass(){};
    ~CPCEClass(){};
    virtual void before_enclave_load();
    virtual int get_debug_flag() { return 0;}

public:
    uint32_t get_pce_target(sgx_target_info_t *p_pce_target);
    uint32_t get_pce_info(const sgx_report_t& report, 
                         const signed_pek_t& pek,
                         uint16_t& pce_id,
                         uint16_t& isv_svn,
                         uint8_t encrypted_ppid[PEK_MOD_SIZE]);
    uint32_t sign_report(const psvn_t& cert_psvn, 
                         const sgx_report_t& report,
                         uint8_t signed_sign[2*SE_ECDSA_SIGN_SIZE]);
};

