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



#include "string.h"
#include "stdlib.h"
#include "stdio.h"
#include "sgx_trts.h"
#include "sgx_thread.h"
#include "sgx_tseal.h"

#include "Enclave_t.h"

uint32_t g_secret;
sgx_thread_mutex_t g_mutex = SGX_THREAD_MUTEX_INITIALIZER;

static inline void free_allocated_memory(void *pointer)
{
    if(pointer != NULL)
    {
        free(pointer);
        pointer = NULL;
    }
}


int initialize_enclave(struct sealed_buf_t *sealed_buf)
{
    // sealed_buf == NULL indicates it is the first time to initialize the enclave
    if(sealed_buf == NULL)
    {
        sgx_thread_mutex_lock(&g_mutex);
        g_secret = 0;
        sgx_thread_mutex_unlock(&g_mutex);
        return 0;
    }

    // It is not the first time to initialize the enclave
    // Reinitialize the enclave to recover the secret data from the input backup sealed data.

    uint32_t len = sizeof(sgx_sealed_data_t) + sizeof(uint32_t);
    //Check the sealed_buf length and check the outside pointers deeply
    if(sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index)] == NULL ||
        sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index + 1)] == NULL ||
        !sgx_is_outside_enclave(sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index)], len) ||
        !sgx_is_outside_enclave(sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index + 1)], len))
    {
        print("Incorrect input parameter(s).\n");
        return -1;
    }

    // Retrieve the secret from current backup sealed data
    uint32_t unsealed_data = 0;
    uint32_t unsealed_data_length = sizeof(g_secret);
    uint8_t *plain_text = NULL;
    uint32_t plain_text_length = 0;
    uint8_t *temp_sealed_buf = (uint8_t *)malloc(len);
    if(temp_sealed_buf == NULL)
    {
        print("Out of memory.\n");
        return -1;
    }

    sgx_thread_mutex_lock(&g_mutex);
    memcpy(temp_sealed_buf, sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index)], len);

    // Unseal current sealed buf
    sgx_status_t ret = sgx_unseal_data((sgx_sealed_data_t *)temp_sealed_buf, plain_text, &plain_text_length, (uint8_t *)&unsealed_data, &unsealed_data_length);
    if(ret == SGX_SUCCESS)
    {
        g_secret = unsealed_data;
        sgx_thread_mutex_unlock(&g_mutex);
        free_allocated_memory(temp_sealed_buf);
        return 0;
    }
    else
    {
        sgx_thread_mutex_unlock(&g_mutex);
        print("Failed to reinitialize the enclave.\n");
        free_allocated_memory(temp_sealed_buf);
        return -1;
    }
}

int increase_and_seal_data(size_t tid, struct sealed_buf_t* sealed_buf)
{
    uint32_t sealed_len = sizeof(sgx_sealed_data_t) + sizeof(g_secret);
    // Check the sealed_buf length and check the outside pointers deeply
    if(sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index)] == NULL ||
        sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index + 1)] == NULL ||
        !sgx_is_outside_enclave(sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index)], sealed_len) ||
        !sgx_is_outside_enclave(sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index + 1)], sealed_len))
    {
        print("Incorrect input parameter(s).\n");
        return -1;
    }

    char string_buf[BUFSIZ] = {'\0'};
    uint32_t temp_secret = 0;
    uint8_t *plain_text = NULL;
    uint32_t plain_text_length = 0;
    uint8_t *temp_sealed_buf = (uint8_t *)malloc(sealed_len);
    if(temp_sealed_buf == NULL)
    {
        print("Out of memory.\n");
        return -1;
    }
    memset(temp_sealed_buf, 0, sealed_len);

    sgx_thread_mutex_lock(&g_mutex);

    // Increase and seal the secret data
    temp_secret = ++g_secret;
    sgx_status_t ret = sgx_seal_data(plain_text_length, plain_text, sizeof(g_secret), (uint8_t *)&g_secret, sealed_len, (sgx_sealed_data_t *)temp_sealed_buf);
    if(ret != SGX_SUCCESS)
    {
        sgx_thread_mutex_unlock(&g_mutex);
        print("Failed to seal data\n");
        free_allocated_memory(temp_sealed_buf);
        return -1;
    }
    // Backup the sealed data to outside buffer
    memcpy(sealed_buf->sealed_buf_ptr[MOD2(sealed_buf->index + 1)], temp_sealed_buf, sealed_len);
    sealed_buf->index++;

    sgx_thread_mutex_unlock(&g_mutex);
    free_allocated_memory(temp_sealed_buf);

    // Ocall to print the unsealed secret data outside.
    // In theory, the secret data(s) SHOULD NOT be transferred outside the enclave as clear text(s).
    // So please DO NOT print any secret outside. Here printing the secret data to outside is only for demo.
    snprintf(string_buf, BUFSIZ, "Thread %#x>: %u\n", (unsigned int)tid, (unsigned int)temp_secret);
    print(string_buf);
    return 0;
}
