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


#include "arch.h"
#include "launch_checker.h"
#include "se_vendor.h"
#include "prd_css_util.h"
#include "se_memcpy.h"

#include <stdio.h>
#include <string.h>
#include <assert.h>



extern "C" int read_prd_css(const prd_css_path_t prd_css_path, enclave_css_t *css)
{
    assert(css != NULL);

    enclave_css_t prd_css;
    memset(&prd_css, 0, sizeof(enclave_css_t));

    FILE * fp = NULL;
    fp = fopen(prd_css_path, "rb");
    if(fp == NULL)
        return SGX_ERROR_INVALID_PARAMETER;


    fseek(fp, 0L, SEEK_END);
    if(ftell(fp) != sizeof(enclave_css_t))
    {
        fclose(fp);
        return SGX_ERROR_INVALID_PARAMETER;
    }
    fseek(fp, 0L, SEEK_SET);
    if(fread(&prd_css, 1, sizeof(enclave_css_t), fp) != sizeof(enclave_css_t))
    {
        fclose(fp);
        return SGX_ERROR_INVALID_PARAMETER;
    }
    fclose(fp);

    memcpy_s(css, sizeof(enclave_css_t), &prd_css, sizeof(prd_css));
    return SGX_SUCCESS;
}

extern "C" bool is_le(SGXLaunchToken *lc, const enclave_css_t *const css)
{
    assert(NULL != css && NULL != lc);
    sgx_launch_token_t token;

    lc->get_launch_token(&token);

    token_t *launch = reinterpret_cast<token_t *>(token);

    if(INTEL_VENDOR_ID == css->header.module_vendor
            && LE_PROD_ID == css->body.isv_prod_id
            && 0 != css->header.hw_version
            && 0 == launch->body.valid)
        return true;

    return false;
}
