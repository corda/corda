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

#ifndef _TRTS_INST_H_
#define _TRTS_INST_H_

#include "sgx.h"
#include "arch.h"

/* Attention: 
  * if the following alignment requirement changes, go to selib to
  * review the memory allocation of sgx_create_report and sgx_get_key.
  */
#define TARGET_INFO_ALIGN_SIZE  512
#define REPORT_DATA_ALIGN_SIZE  128
#define REPORT_ALIGN_SIZE       512
#define KEY_REQUEST_ALIGN_SIZE  512
#define KEY_ALIGN_SIZE          16

#define BIT_ERROR(x)            (1 << (x))

typedef enum _egetkey_status_t
{
    EGETKEY_SUCCESS           = 0,
    EGETKEY_INVALID_ATTRIBUTE = BIT_ERROR(1),
    EGETKEY_INVALID_CPUSVN    = BIT_ERROR(5),
    EGETKEY_INVALID_ISVSVN    = BIT_ERROR(6),
    EGETKEY_INVALID_KEYNAME   = BIT_ERROR(8),
}  egetkey_status_t;

struct ms_tcs
{
    void * ptcs;
};

#ifdef __cplusplus
extern "C" {
#endif

int sgx_accept_forward(si_flags_t sfl, size_t lo, size_t hi);
void do_ereport(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report);
int do_egetkey(const sgx_key_request_t *key_request, sgx_key_128bit_t *key);
uint32_t do_rdrand(uint32_t *rand);
int do_eaccept(const sec_info_t *, size_t);
int do_emodpe(const sec_info_t*, size_t);
int apply_EPC_pages(void *start_address, size_t page_number);
int apply_pages_within_exception(void *start_address, size_t page_count);
int trim_EPC_pages(void *start_address, size_t page_number);
sgx_status_t SGXAPI sgx_trts_mprotect(size_t start, size_t size, uint64_t perms);
sgx_status_t do_add_thread(void *ms);
int is_dynamic_thread(void *tcs);
uint32_t get_dynamic_stack_max_page(void);
#ifdef __cplusplus
}
#endif

#endif
