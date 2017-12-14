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

#ifndef _RTS_H_
#define _RTS_H_

#include "se_types.h"
#include "rts_cmd.h"

typedef struct _ocall_context_t
{
    uintptr_t shadow0;
    uintptr_t shadow1;
    uintptr_t shadow2;
    uintptr_t shadow3;
    uintptr_t ocall_flag;
    uintptr_t ocall_index;
    uintptr_t pre_last_sp;
    uintptr_t r15;
    uintptr_t r14;
    uintptr_t r13;
    uintptr_t r12;
    uintptr_t xbp;
    uintptr_t xdi;
    uintptr_t xsi;
    uintptr_t xbx;
    uintptr_t reserved[3];
    uintptr_t ocall_depth;
    uintptr_t ocall_ret;
} ocall_context_t;

typedef enum
{
    SDK_VERSION_1_5,
    SDK_VERSION_2_0
} sdk_version_t;

typedef struct _system_features
{
    uint64_t cpu_features;
    sdk_version_t version;
    /* system feature set array. MSb of each element indicates whether this is
     * the last element. This will help tRTS to know when it can stop walking
     * through the array searching for certain features.
    */
    uint64_t system_feature_set[1];
}system_features_t;

// current system_feature_set only contains one element of type uint64_t, the highest
// bit is bit 63
#define SYS_FEATURE_MSb     63

#define OCALL_FLAG        0x4F434944

typedef enum
{
    EDMM_TRIM = -2,
    EDMM_TRIM_COMMIT = -3,
    EDMM_MODPR = -4,
}edmm_ocall_t;


#endif
