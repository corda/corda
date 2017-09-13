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
#include <sgx_trts.h>
#include "protocol.h"
#include "helper.h"
#include "cipher.h"
#include <string.h>

//Function to generate 1 byte selector id for end point selection
//The End Point Selection is an optional protocol before SGX EPID Provisioning
//   to get the server address and expired date(of the server) for SGX EPID Provisioning
//   a one byte selector id is required for each machine which never changes for any machine
//   First byte of PPID is used currently as the selector id
static pve_status_t gen_es_selector_id(uint8_t *selector_id)
{
    ppid_t ppid;
    memset(&ppid, 0, sizeof(ppid));
    pve_status_t ret = get_ppid(&ppid);
    if(ret != PVEC_SUCCESS)
        return ret;

    *selector_id = ppid.ppid[0];
    (void)memset_s(&ppid, sizeof(ppid), 0, sizeof(ppid));//clear the PPID in stack
    return PVEC_SUCCESS;
}


//Function to generate End Point Selection id and XID for end point selection msg1
//@es_selector, output XID and SelectorID
//@return PVEC_SUCCESS on success or error code otherwise
pve_status_t gen_es_msg1_data(gen_endpoint_selection_output_t *es_selector)
{
    //randomly generate xid
    pve_status_t ret = se_read_rand_error_to_pve_error(sgx_read_rand(es_selector->xid, XID_SIZE));
    if(ret != PVEC_SUCCESS)
        return ret;
    //generate selector id which is hash value of Provisioning Base Key
    return gen_es_selector_id(&es_selector->selector_id);
}
