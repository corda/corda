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
* File:
*     parse_key_file.cpp
* Description:
*     Parse the RSA key file that user inputs
* to get the key type and RSA structure.
*/

#include "parse_key_file.h"
#include "se_trace.h"
#include "util_st.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <openssl/pem.h>

//parse_key_file():
//       parse the RSA key file
//Return Value:
//      true: success
//      false: fail
bool parse_key_file(int mode, const char *key_path, RSA **prsa, int *pkey_type)
{
    assert(prsa != NULL && pkey_type != NULL);

    if(key_path == NULL)
    {
        *pkey_type = NO_KEY;
        return false;
    }
    FILE *fp = fopen(key_path, "rb");
    if(fp == NULL)
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, key_path);
        return false;
    }
    int key_type = UNIDENTIFIABLE_KEY;
    RSA *rsa = NULL;

    if(mode == SIGN)
    {
        rsa = PEM_read_RSAPrivateKey(fp, NULL, NULL, NULL);
        fclose(fp);
        if(!rsa)
        {
            se_trace(SE_TRACE_ERROR, KEY_FORMAT_ERROR);
            return false;
        }
        key_type = PRIVATE_KEY;
    }
    else if(mode == CATSIG)
    {
        rsa = PEM_read_RSA_PUBKEY(fp, NULL, NULL, NULL);
        fclose(fp);
        if(!rsa)
        {
            se_trace(SE_TRACE_ERROR, KEY_FORMAT_ERROR);
            return false;
        }
        key_type = PUBLIC_KEY;
    }
    else
    {
        se_trace(SE_TRACE_ERROR, "ERROR: Invalid command\n %s", USAGE_STRING);
        fclose(fp);
        return false;
    }

    // Check the key size and exponent
    if(BN_num_bytes(rsa->n) != N_SIZE_IN_BYTES)
    {
        se_trace(SE_TRACE_ERROR, INVALID_KEYSIZE_ERROR);
        RSA_free(rsa);
        return false;
    }
    char *p = BN_bn2dec(rsa->e);
    if(memcmp(p, "3", 2))
    {
        se_trace(SE_TRACE_ERROR, INVALID_EXPONENT_ERROR);
        OPENSSL_free(p);
        RSA_free(rsa);
        return false;
    }

    OPENSSL_free(p);
    *prsa = rsa;
    *pkey_type = key_type;
    return true;
}
