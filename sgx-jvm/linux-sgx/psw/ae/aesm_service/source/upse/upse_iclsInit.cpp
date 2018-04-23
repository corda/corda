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

#include <stdint.h>
#include <dlfcn.h>
#include "aeerror.h"
#include "oal/oal.h"

static char ICLS_LIB_NAME[] = "libiclsclient.so";
static char ICLS_INIT_FUNC_NAME[] = "iclsInit";
typedef  uint32_t(*iclsInit_t)(const void*);
#define STATUS_OK (0)



uint32_t upse_iclsInit()
{
    uint32_t status = AE_SUCCESS;
    void *hmodule = NULL;
    do{
        //For this to work iclsClient needs to be installed in the system
        hmodule = dlopen(ICLS_LIB_NAME, RTLD_LAZY);
        if(NULL == hmodule)
        {
            status = AESM_PSE_PR_ICLS_CLIENT_MISSING_ERROR;
            break;
        }

        //clear exist error code
        dlerror();

        iclsInit_t iclsInit = (iclsInit_t)dlsym(hmodule, ICLS_INIT_FUNC_NAME);
        if ( NULL != dlerror() || NULL == iclsInit)
        {
            status = AESM_PSE_PR_ICLS_CLIENT_MISSING_ERROR;
            break;
        }
        //If you get error, check /opt/Intel/iclsClient/log/iclsClient.log
        uint32_t status_provision = iclsInit(0);
        if (status_provision != STATUS_OK)
        {
            AESM_DBG_ERROR("upse_iclsInit failed:%d", status_provision);
            status = AESM_PSE_PR_PSDA_PROVISION_ERROR;
            break;
        }
    } while(0);

    if(hmodule)
        dlclose(hmodule);
    return status;
}
