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


#ifndef _PVE_MSG3_PARM_H_
#define _PVE_MSG3_PARM_H_
#include "provision_msg.h"
#include "protocol.h"
#include "epid/common/errors.h"
#include "epid/member/api.h"
#include "sgx_tcrypto.h"
#include "pve_qe_common.h"
#include "se_sig_rl.h"

#ifndef UINT32_MAX
#define UINT32_MAX 0xFFFFFFFFU
#endif

typedef MemberCtx EPIDMember;

/*define a local structure to collect information required for ProvMsg3*/
typedef struct _prov_msg3_parm_t{
    se_sig_rl_t                  sigrl_header;           /*used to keep a copy of sigrl_header from msg2 in trusted memory*/
    extended_epid_group_blob_t   local_xegb;
    uint8_t                      iv[IV_SIZE];            /*iv to encrypt EPIDSigTLV by EK1*/
    const external_memory_byte_t *emp_sigrl_sig_entries; /*pointer to start address of sigrl_body in external memory*/
    EpidSignature                signature_header;       /*keep a copy of signature_header in ProvMsg3*/
    uint32_t                     sigrl_count;            /*Count of SigRL Entry in the Previous SigRL*/
    EPIDMember                   *epid_member;           /*A handle to Epid Member Ctx in epid library. Save here for piece meal processing*/
    IppsAES_GCMState             *p_msg3_state;          /*State to encrypt ProvMsg3 in piece meal processing*/
    uint32_t                     msg3_state_size;
    sgx_sha_state_handle_t       sha_state;              /*State to calcuate SHA256 value of PreviousSigRL in ProvMsg2 in piece meal processing*/
}prov_msg3_parm_t;

/*declare gen_prov_msg3. It will be called by function to process ProvMsg2 to generate ProvMsg3*/
pve_status_t gen_prov_msg3_data(const proc_prov_msg2_blob_input_t *msg2_blob_input,
                           prov_msg3_parm_t& msg3_parm,
                           uint8_t performance_rekey_used,
                           gen_prov_msg3_output_t *msg3_output,
                           external_memory_byte_t *emp_epid_sig, 
                           uint32_t epid_sig_buffer_size);
#endif

