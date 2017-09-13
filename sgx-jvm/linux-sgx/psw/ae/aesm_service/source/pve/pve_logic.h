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

#ifndef _PVE_LOGIC_H_
#define _PVE_LOGIC_H_
#include "sgx_urts.h"
#include "aesm_error.h"
#include "arch.h"
#include "aeerror.h"
#include "tlv_common.h"
#include "se_thread.h"
#include "oal/oal.h"
#include "se_wrapper.h"
#include "epid_pve_type.h"
#include <time.h>
#include <string.h>

typedef struct _endpoint_selection_infos_t endpoint_selection_infos_t;

typedef struct _pve_data_t{
    uint8_t sk[SK_SIZE];
    uint8_t xid[XID_SIZE];
    signed_pek_t pek;
    bool is_performance_rekey;
    bool is_backup_retrieval;
    bk_platform_info_t bpi;
}pve_data_t;
class PvEAESMLogic{
public:
    static aesm_error_t provision(bool performance_rekey_used, uint32_t timeout_usec);
    static aesm_error_t pve_error_postprocess(ae_error_t ae_error);
    static ae_error_t   epid_provision_thread_func(bool  performance_rekey_used); /*call get_epid_provision_thread_status().start(performance_rekey_used) to invoke this function with timeout*/
private:
    static ae_error_t update_old_blob(pve_data_t& pve_data, const endpoint_selection_infos_t& es_info);
    static ae_error_t process_pve_msg2(pve_data_t& pve_data, const uint8_t *msg2, uint32_t msg2_size, const endpoint_selection_infos_t& es_info);
    static ae_error_t process_pve_msg4(const pve_data_t& pve_data, const uint8_t *msg4, uint32_t msg4_size);
};
#endif

