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


/* Test Pointer Auttributes */

#include <sys/types.h>
#include <string.h>

#include "sgx_trts.h"
#include "../Enclave.h"
#include "Enclave_t.h"

/* checksum_internal:
 *   get simple checksum of input buffer and length
 */
int32_t checksum_internal(char *buf, size_t count)
{
    register int32_t sum = 0;
    int16_t *ptr = (int16_t *)buf;

    /* Main summing loop */
    while(count > 1) {
        sum = sum + *ptr++;
        count = count - 2;
    }

    /* Add left-over byte, if any */
    if (count > 0)
        sum = sum + *((char *)ptr);

	return ~sum;
}

/* ecall_pointer_user_check, ecall_pointer_in, ecall_pointer_out, ecall_pointer_in_out:
 *   The root ECALLs to test [in], [out], [user_check] attributes.
 */
size_t ecall_pointer_user_check(void *val, size_t sz)
{
    /* check if the buffer is allocated outside */
    if (sgx_is_outside_enclave(val, sz) != 1)
        abort();

    char tmp[100] = {0};
    size_t len = sz>100?100:sz;
    
    /* copy the memory into the enclave to make sure 'val' 
     * is not being changed in checksum_internal() */
    memcpy(tmp, val, len);
    
    int32_t sum = checksum_internal((char *)tmp, len);
    printf("Checksum(0x%p, %zu) = 0x%x\n", 
            val, len, (unsigned int)sum);
    
    /* modify outside memory directly */
    memcpy(val, "SGX_SUCCESS", len>12?12:len);

	return len;
}

/* ecall_pointer_in:
 *   the buffer of val is copied to the enclave.
 */

void ecall_pointer_in(int *val)
{
    if (sgx_is_within_enclave(val, sizeof(int)) != 1)
        abort();
    *val = 1234;
}

/* ecall_pointer_out:
 *   the buffer of val is copied to the untrusted side.
 */
void ecall_pointer_out(int *val)
{
    if (sgx_is_within_enclave(val, sizeof(int)) != 1)
        abort();
    assert(*val == 0);
    *val = 1234;
}

/* ecall_pointer_in_out:
 * the buffer of val is double-copied.
 */
void ecall_pointer_in_out(int *val)
{
    if (sgx_is_within_enclave(val, sizeof(int)) != 1)
        abort();
    *val = 1234;
}

/* ocall_pointer_attr:
 *   The root ECALL that test OCALL [in], [out], [user_check].
 */
void ocall_pointer_attr(void)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;

    int val = 0;
    ret = ocall_pointer_user_check(&val);
    if (ret != SGX_SUCCESS)
        abort();

    val = 0;
    ret = ocall_pointer_in(&val);
    if (ret != SGX_SUCCESS)
        abort();
    assert(val == 0);

    val = 0;
    ret = ocall_pointer_out(&val);
    if (ret != SGX_SUCCESS)
        abort();
    assert(val == 1234);

    val = 0;
    ret = ocall_pointer_in_out(&val);
    if (ret != SGX_SUCCESS)
        abort();
    assert(val == 1234);

    return;
}

/* ecall_pointer_string:
 *   [string] defines a string.
 */
void ecall_pointer_string(char *str)
{
    strncpy(str, "0987654321", strlen(str));
}

/* ecall_pointer_string_const:
 *   const [string] defines a string that cannot be modified.
 */
void ecall_pointer_string_const(const char *str)
{
    char* temp = new char[strlen(str)];
    strncpy(temp, str, strlen(str));
    delete []temp;
}

/* ecall_pointer_size:
 *   'len' needs to be specified to tell Edger8r the length of 'str'.
 */
void ecall_pointer_size(void *ptr, size_t len)
{
    strncpy((char*)ptr, "0987654321", len);
}

/* ecall_pointer_count:
 *   'cnt' needs to be specified to tell Edger8r the number of elements in 'arr'.
 */
void ecall_pointer_count(int *arr, int cnt)
{
    for (int i = (cnt - 1); i >= 0; i--)
        arr[i] = (cnt - 1 - i);
}

/* ecall_pointer_isptr_readonly:
 *   'buf' is user defined type, shall be tagged with [isptr].
 *   if it's not writable, [readonly] shall be specified. 
 */
void ecall_pointer_isptr_readonly(buffer_t buf, size_t len)
{
    strncpy((char*)buf, "0987654321", len);
}

/* get_buffer_len:
 *   get the length of input buffer 'buf'.
 */
size_t get_buffer_len(const char* buf)
{
    (void)buf;
    return 10*sizeof(int);
}

/* ecall_pointer_sizefunc:
 *   call get_buffer_len to determin the length of 'buf'.
 */
void ecall_pointer_sizefunc(char *buf)
{
    int *tmp = (int*)buf;
    for (int i = 0; i < 10; i++) {
        assert(tmp[i] == 0);
        tmp[i] = i;
    }
}
