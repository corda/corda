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

#ifndef _PSE_CLASS_H_
#define _PSE_CLASS_H_
#include "AEClass.h"
#include "aeerror.h"
#include "pse_types.h"
#include "sgx_dh.h"
#include "ae_debug_flag.hh"
#include "se_wrapper.h"

#define PS_CAP_NOT_AVAILABLE    ((uint64_t)-1)
#define PSDA_CAP_RESULT_MSG_LEN 12

/* defines PSE service status*/
typedef enum _pse_status_t
{
    PSE_STATUS_INIT = 0,                    /* Init status*/
    PSE_STATUS_UNAVAILABLE,                 /* service is unavailable*/
    PSE_STATUS_CSE_PROVISIONED,             /* CSE is provisioned*/
    PSE_STATUS_SERVICE_READY                /* Waiting for PS requests (pse_op enclave is loaded)*/
} pse_status_t;

class CPSEClass: public SingletonEnclave<CPSEClass>
{
    friend class Singleton<CPSEClass>;
    friend class SingletonEnclave<CPSEClass>;
    static aesm_enclave_id_t get_enclave_fid(){return PSE_OP_ENCLAVE_FID;}
protected:
    CPSEClass(){
        m_status = PSE_STATUS_INIT;
        m_ps_cap = PS_CAP_NOT_AVAILABLE;
        m_freq = se_get_tick_count_freq();
    };
    ~CPSEClass(){
    };

    virtual void before_enclave_load();
    virtual int get_debug_flag() { return AE_DEBUG_FLAG;}
    pse_status_t m_status;
    uint64_t m_ps_cap;
    uint64_t m_freq;
public:
    ae_error_t init_ps(void);

    ae_error_t create_session(
        uint32_t* session_id,
        uint8_t* se_dh_msg1, uint32_t se_dh_msg1_size
        );

    /*if ok return 0*/
    ae_error_t exchange_report(
        uint32_t session_id,
        uint8_t* se_dh_msg2, uint32_t se_dh_msg2_size,
        uint8_t* se_dh_msg3, uint32_t se_dh_msg3_size
        );

    /*if ok return 0*/
    ae_error_t close_session(uint32_t session_id
        );

    /*if ok return 0*/
    ae_error_t invoke_service(
        uint8_t* pse_message_req, size_t pse_message_req_size,
        uint8_t* pse_message_resp, size_t pse_message_resp_size
        );

    /* establish ephemeral session between PSE and CSE*/
    ae_error_t create_ephemeral_session_pse_cse(bool is_new_pairing, bool redo = false);

    pse_status_t get_status() {
        return m_status;
    }

    ae_error_t psda_invoke_service(uint8_t* psda_req_msg, uint32_t psda_req_msg_size,
                        uint8_t* psda_resp_msg, uint32_t psda_resp_msg_size);
    ae_error_t get_ps_cap(uint64_t* ps_cap);

	void unload_enclave()
	{
		if (PSE_STATUS_SERVICE_READY == m_status) {
			m_status = PSE_STATUS_CSE_PROVISIONED;
		}
		SingletonEnclave<CPSEClass>::unload_enclave();
	}

private:
    ae_error_t psda_start_ephemeral_session(
        const uint8_t* pse_instance_id, 
        pse_cse_msg2_t* cse_msg2
    );
    ae_error_t psda_finalize_session(
        const uint8_t* pse_instance_id, 
        const pse_cse_msg3_t* cse_msg3, 
        pse_cse_msg4_t* cse_msg4
    );

};
#endif

