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

#include "upse.h"
#include <list>
#include <Buffer.h>
#include "helper.h"
#include "uecall_bridge.h"
#include "u_certificate_provisioning.h"
#include "u_long_term_pairing.h"

#include "provision_msg.h"
#include "aesm_logic.h"
#include "endpoint_select_info.h"
#include "oal/oal.h"





ae_error_t upse_certificate_provisioning(sgx_enclave_id_t enclave_id, platform_info_blob_wrapper_t* pib_wrapper)
{
    ae_error_t status = AE_SUCCESS;
    AESM_DBG_TRACE("enter fun");

    SaveEnclaveID(enclave_id);      // Save the enclave ID for use in ECALLs

    do
    {
        endpoint_selection_infos_t es_info;

        if(AE_SUCCESS == (status = (ae_error_t)AESMLogic::endpoint_selection(es_info)) )
		{
			status = certificate_chain_provisioning(es_info, pib_wrapper);
		}
        BREAK_IF_FAILED(status);

    } while (0);

    return status;
}


ae_error_t upse_long_term_pairing(sgx_enclave_id_t enclave_id, bool* new_pairing)
{
    ae_error_t status = AE_SUCCESS;
    AESM_DBG_TRACE("enter fun");

    SaveEnclaveID(enclave_id);      // Save the enclave ID for use in ECALLs

    do
    {
        status = create_sigma_long_term_pairing(new_pairing);
        BREAK_IF_FAILED(status);

    } while (0);

    return status;
}
