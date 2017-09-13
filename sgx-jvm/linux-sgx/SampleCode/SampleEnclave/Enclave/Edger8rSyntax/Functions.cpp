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


/* Test Calling Conventions */

#include <string.h>
#include <stdio.h>

#include "../Enclave.h"
#include "Enclave_t.h"

/* ecall_function_calling_convs:
 *   memccpy is defined in system C library.
 */
void ecall_function_calling_convs(void)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;

    char s1[] = "1234567890";
    char s2[] = "0987654321";

    char buf[BUFSIZ] = {'\0'};
    memcpy(buf, s1, strlen(s1));

    ret = memccpy(NULL, s1, s2, '\0', strlen(s1));
    
    if (ret != SGX_SUCCESS)
        abort();
    assert(memcmp(s1, s2, strlen(s1)) == 0);

    return;
}

/* ecall_function_public:
 *   The public ECALL that invokes the OCALL 'ocall_function_allow'.
 */
void ecall_function_public(void)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;

    ret = ocall_function_allow();
    if (ret != SGX_SUCCESS)
        abort();
    
    return;
}

/* ecall_function_private:
 *   The private ECALL that only can be invoked in the OCALL 'ocall_function_allow'.
 */
int ecall_function_private(void)
{
    return 1;
}

