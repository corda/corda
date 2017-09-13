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


#include "../App.h"
#include "Enclave_u.h"

/* edger8r_array_attributes:
 *   Invokes ECALLs declared with array attributes.
 */
void edger8r_array_attributes(void)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;

    /* user_check */
    int arr1[4] = {0, 1, 2, 3};
    ret = ecall_array_user_check(global_eid, arr1);
    if (ret != SGX_SUCCESS)
        abort();

    /* make sure arr1 is changed */
    for (int i = 0; i < 4; i++)
        assert(arr1[i] == (3 - i));

    /* in */
    int arr2[4] = {0, 1, 2, 3};
    ret = ecall_array_in(global_eid, arr2);
    if (ret != SGX_SUCCESS)
        abort();
    
    /* arr2 is not changed */
    for (int i = 0; i < 4; i++)
        assert(arr2[i] == i);
    
    /* out */
    int arr3[4] = {0, 1, 2, 3};
    ret = ecall_array_out(global_eid, arr3);
    if (ret != SGX_SUCCESS)
        abort();
    
    /* arr3 is changed */
    for (int i = 0; i < 4; i++)
        assert(arr3[i] == (3 - i));
    
    /* in, out */
    int arr4[4] = {0, 1, 2, 3};
    ret = ecall_array_in_out(global_eid, arr4);
    if (ret != SGX_SUCCESS)
        abort();
    
    /* arr4 is changed */
    for (int i = 0; i < 4; i++)
        assert(arr4[i] == (3 - i));
    
    /* isary */
    array_t arr5 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    ret = ecall_array_isary(global_eid, arr5);
    if (ret != SGX_SUCCESS)
        abort();
    
    /* arr5 is changed */
    for (int i = 0; i < 10; i++)
        assert(arr5[i] == (9 - i));
}
