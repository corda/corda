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
 * File: global_data.h
 * Description: 
 *     The file defines the structure global_data_t. 
 */

#ifndef _TRTS_GLOBAL_DATA_H_
#define _TRTS_GLOBAL_DATA_H_

#include "se_types.h"
#include "thread_data.h"
#include "metadata.h"
#include "rts.h"

#define LAYOUT_ENTRY_NUM 38
typedef struct _global_data_t
{
    sys_word_t     enclave_size;
    sys_word_t     heap_offset;
    sys_word_t     heap_size;
    sys_word_t     thread_policy;
    thread_data_t  td_template;
    uint8_t        tcs_template[TCS_TEMPLATE_SIZE];
    uint32_t       layout_entry_num;
    uint32_t       reserved;
    layout_t       layout_table[LAYOUT_ENTRY_NUM];
} global_data_t;

#define ENCLAVE_INIT_NOT_STARTED  0
#define ENCLAVE_INIT_IN_PROGRESS  1
#define ENCLAVE_INIT_DONE         2
#define ENCLAVE_CRASHED           3

#ifdef __cplusplus
extern "C" {
#endif
extern SE_DECLSPEC_EXPORT global_data_t const volatile g_global_data;
extern uint32_t g_enclave_state;
extern sdk_version_t g_sdk_version;
extern int EDMM_supported;
extern uint8_t  __ImageBase;
extern int g_xsave_enabled;


#ifdef __cplusplus
}
#endif
#endif
