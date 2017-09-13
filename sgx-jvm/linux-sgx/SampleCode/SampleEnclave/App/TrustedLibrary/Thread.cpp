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


#include <thread>
#include <stdio.h>
using namespace std;

#include "../App.h"
#include "Enclave_u.h"

static size_t counter = 0;

void increase_counter(void)
{
    size_t cnr = 0;
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    ret = ecall_increase_counter(global_eid, &cnr);
    if (cnr != 0) counter = cnr; 
    if (ret != SGX_SUCCESS)
        abort();
}

void data_producer(void)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    ret = ecall_producer(global_eid);
    if (ret != SGX_SUCCESS)
        abort();
}

void data_consumer(void)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    ret = ecall_consumer(global_eid);
    if (ret != SGX_SUCCESS)
        abort();
}

/* ecall_thread_functions:
 *   Invokes thread functions including mutex, condition variable, etc.
 */
void ecall_thread_functions(void)
{
    thread adder1(increase_counter);
    thread adder2(increase_counter);
    thread adder3(increase_counter);
    thread adder4(increase_counter);

    adder1.join();
    adder2.join();
    adder3.join();
    adder4.join();

    assert(counter == 4*LOOPS_PER_THREAD);

    printf("Info: executing thread synchronization, please wait...  \n");
    /* condition variable */
    thread consumer1(data_consumer);
    thread producer0(data_producer);
    thread consumer2(data_consumer);
    thread consumer3(data_consumer);
    thread consumer4(data_consumer);
    
    consumer1.join();
    consumer2.join();
    consumer3.join();
    consumer4.join();
    producer0.join();
}
