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

#ifndef _PROFILE_FUN_H_
#define _PROFILE_FUN_H_
#include "sgx_profile.h"
#include "oal/oal.h"

#ifdef _PROFILE_
class AESMProfileUtil{
    const char *tag;
public:
    AESMProfileUtil(const char *ttag){
        this->tag = ttag;
        PROFILE_START(ttag);
    }
    ~AESMProfileUtil(){
        PROFILE_END(tag);
    }
    static void output(){
        char filename[MAX_PATH];
        if(aesm_get_cpathname(FT_PERSISTENT_STORAGE, AESM_PERF_DATA_FID, filename, MAX_PATH)==AE_SUCCESS){
            PROFILE_OUTPUT(filename);
        }
    }
};
#define AESM_PROFILE_FUN AESMProfileUtil __aesm_profile_util_##__LINE__(__PRETTY_FUNCTION__)   /*Gcc in Linux does not support concatenation of __FUNCTION__, is it a compiler bug?*/
#define AESM_PROFILE_INIT PROFILE_INIT()
#define AESM_PROFILE_OUTPUT AESMProfileUtil::output()
#else
#define AESM_PROFILE_FUN
#define AESM_PROFILE_INIT
#define AESM_PROFILE_OUTPUT
#endif

#endif/*_AESM_PROFILE_H_*/
