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
 * File: helper.h
 * Description: Header file to some helper function to extract some enclave information
 *
 * Wrap functions to get PPID, PWK, PSID, PSVN, PSK and seal/unseal function
 */

#ifndef _HELPER_CPP
#define _HELPER_CPP
#include "se_cdefs.h"
#include "ae_ipp.h"
#include "sgx_tseal.h"
#include "provision_msg.h"
#include "epid/common/errors.h"

/*Function to get PvE PPID: AES-128-CMAC using Provisioning Key with both CPUSVN,ISVSVN to be 0*/
/*   return PVEC_SUCCESS on success*/
pve_status_t get_ppid(
    ppid_t* ppid);

/*Function to retrive PWK2
      return PVEC_SUCCESS on success*/
pve_status_t get_pwk2(
    const psvn_t* psvn,
    const uint8_t   n2[NONCE_2_SIZE],
    sgx_key_128bit_t* wrap_key);

/*Function to get PSK for Provision Enclave. 
  The key is used to seal the private parameter f before sending to backend server
     return PVEC_SUCCESS on success*/
pve_status_t get_pve_psk(const psvn_t *psvn,          /*input PSVN, must not be NULL*/
                         sgx_key_128bit_t *seal_key); /*output PSK*/


/*define the struct so that we could use the pointer of it to external memory only
  It is used to prevent mixing the internal and external memory pointer together*/
typedef struct _external_memory_byte_t{
    uint8_t byte;
}external_memory_byte_t;

/*Function to copy out or copy in between external memory and EPC 
   to increase the readability and also to take advantage of compile time checking*/
void pve_memcpy_out(external_memory_byte_t *dst, const void *src, uint32_t size);
void pve_memcpy_in(void *dst, const external_memory_byte_t *src, uint32_t size);

/*a helper function to transform sgx_read_rand error code into pve error code*/
pve_status_t se_read_rand_error_to_pve_error(sgx_status_t error);
/*a helper function to transform ipp error code into pve error code*/
pve_status_t ipp_error_to_pve_error(IppStatus status);
pve_status_t sgx_error_to_pve_error(sgx_status_t status);
pve_status_t epid_error_to_pve_error(EpidStatus epid_result);

#endif
