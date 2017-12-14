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
#ifndef TRTS_INTERNAL_H
#define TRTS_INTERNAL_H

#include "util.h"

#define STATIC_STACK_SIZE   688

#define TD2TCS(td) ((const void *)(((thread_data_t*)(td))->stack_base_addr + (size_t)STATIC_STACK_SIZE + (size_t)SE_GUARD_PAGE_SIZE))
#define TCS2CANARY(addr)    ((size_t *)((size_t)(addr)-(size_t)SE_GUARD_PAGE_SIZE-(size_t)STATIC_STACK_SIZE+sizeof(size_t)))

typedef struct {
    const void     *ecall_addr;
    uint8_t         is_priv;
} ecall_addr_t;

typedef struct {
    size_t          nr_ecall;
    ecall_addr_t    ecall_table[1];
} ecall_table_t;

typedef struct {
    size_t  nr_ocall;
    uint8_t entry_table[1];
} entry_table_t;


#ifdef __cplusplus
extern "C" {
#endif
extern ecall_table_t g_ecall_table;
extern entry_table_t g_dyn_entry_table;

int lock_enclave();
void *get_enclave_base();
int get_enclave_state();
void set_enclave_state(int state);

sgx_status_t do_init_enclave(void *ms);
sgx_status_t do_ecall(int index, void *ms, void *tcs);
sgx_status_t do_oret(void *ms);
sgx_status_t trts_handle_exception(void *tcs);
sgx_status_t do_ecall_add_thread(void *ms, void *tcs);
sgx_status_t do_uninit_enclave(void *tcs);
int check_static_stack_canary(void *tcs);
#ifdef __cplusplus
}
#endif

#endif
