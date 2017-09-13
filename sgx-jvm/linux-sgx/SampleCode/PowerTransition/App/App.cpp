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



// App.cpp : Define the entry point for the console application.
//

#include <string.h>
#include <assert.h>
#include <fstream>
#include <thread>
#include <iostream>

#include "Enclave_u.h"
#include "sgx_urts.h"
#include "sgx_tseal.h"

#include "rwlock.h"
#include "ErrorSupport.h"

#define ENCLAVE_NAME "libenclave.signed.so"
#define TOKEN_NAME "Enclave.token"

#define THREAD_NUM 3

// Global data
sgx_enclave_id_t global_eid = 0;
sgx_launch_token_t token = {0};
rwlock_t lock_eid;
struct sealed_buf_t sealed_buf;

using namespace std;

// Ocall function
void print(const char *str)
{
    cout<<str;
}

// load_and_initialize_enclave():
//		To load and initialize the enclave     
sgx_status_t load_and_initialize_enclave(sgx_enclave_id_t *eid, struct sealed_buf_t *sealed_buf)
{
    sgx_status_t ret = SGX_SUCCESS;
    int retval = 0;
    int updated = 0;

    for( ; ; )
    {
        // Step 1: check whether the loading and initialization operations are caused by power transition.
        //		If the loading and initialization operations are caused by power transition, we need to call sgx_destory_enclave() first.
        if(*eid != 0)
        {
            sgx_destroy_enclave(*eid);
        }
	
        // Step 2: load the enclave
        // Debug: set the 2nd parameter to 1 which indicates the enclave are launched in debug mode
        ret = sgx_create_enclave(ENCLAVE_NAME, SGX_DEBUG_FLAG, &token, &updated, eid, NULL);
        if(ret != SGX_SUCCESS)
            return ret;

        // Save the launch token if updated
        if(updated == 1)
        {
            ofstream ofs(TOKEN_NAME, std::ios::binary|std::ios::out);
            if(!ofs.good())
            {
                cout<< "Warning: Failed to save the launch token to \"" <<TOKEN_NAME <<"\""<<endl;
            }
            else
                ofs << token;
        }

        // Step 3: enter the enclave to initialize the enclave
        //      If power transition occurs when the process is inside the enclave, SGX_ERROR_ENCLAVE_LOST will be returned after the system resumes.
        //      Then we can load and intialize the enclave again or just return this error code and exit to handle the power transition.
        //      In this sample, we choose to load and intialize the enclave again.
        ret = initialize_enclave(*eid, &retval, sealed_buf);
        if(ret == SGX_ERROR_ENCLAVE_LOST)
        {
            cout<<"Power transition occured in initialize_enclave()" <<endl;
            continue; // Try to load and initialize the enclave again
        }
        else
        {
            // No power transilation occurs.
            // If the initialization operation returns failure, change the return value.
            if(ret == SGX_SUCCESS && retval != 0)
            {
                ret = SGX_ERROR_UNEXPECTED;
                sgx_destroy_enclave(*eid);
            }
            break;
        }
    }
    return ret;
}

bool increase_and_seal_data_in_enclave()
{
    size_t thread_id = std::hash<std::thread::id>()(std::this_thread::get_id());
    sgx_status_t ret = SGX_SUCCESS;
    int retval = 0;
    sgx_enclave_id_t current_eid = 0;

    // Enter the enclave to increase and seal the secret data for 100 times.
    for(unsigned int i = 0; i< 50000; i++)
    {
        for( ; ; )
        {
            // If power transition occurs, all the data inside the enclave will be lost when the system resumes. 
            // Therefore, if there are some secret data which need to be backed up for recover, 
            // users can choose to seal the secret data inside the enclave and back up the sealed data.

            // Enter the enclave to increase the secret data and back up the sealed data
            rdlock(&lock_eid);
            current_eid = global_eid;
            rdunlock(&lock_eid);
            ret = increase_and_seal_data(current_eid, &retval, thread_id, &sealed_buf);

            if(ret == SGX_ERROR_ENCLAVE_LOST)
            {
                // SGX_ERROR_ENCLAVE_LOST indicates the power transition occurs before the system resumes.
                // Lock here is to make sure there is only one thread to load and initialize the enclave at the same time
                wtlock(&lock_eid);
                // The loading and initialization operations happen in current thread only if there is no other thread reloads and initializes the enclave before
                if(current_eid == global_eid)
                {
                    cout <<"power transition occured in increase_and_seal_data()." << endl;
                    // Use the backup sealed data to reload and initialize the enclave.
                    if((ret = load_and_initialize_enclave(&current_eid, &sealed_buf)) != SGX_SUCCESS)
                    {
                        ret_error_support(ret);
                        wtunlock(&lock_eid);
                        return false;
                    }
                    else
                    {
                        // Update the global_eid after initializing the enclave successfully
                        global_eid = current_eid;
                    }
                }
                else
                {
                    // The enclave has been reloaded by another thread. 
                    // Update the current EID and do increase_and_seal_data() again.
                    current_eid = global_eid;
                }
                wtunlock(&lock_eid);
            }
            else
            {
                // No power transition occurs
                break;
            }
        }
        if(ret != SGX_SUCCESS)
        {
            ret_error_support(ret);
            return false;
        }
        else if(retval != 0)
        {
            return false;
        }
    }
    return true;
}


void thread_func()
{
    if(increase_and_seal_data_in_enclave() != true)
    {
        abort();
    }
}

bool set_global_data()
{
    // Initialize the read/write lock.
    init_rwlock(&lock_eid);

    // Get the saved launch token.
    // If error occures, zero the token.
    ifstream ifs(TOKEN_NAME, std::ios::binary | std::ios::in);
    if(!ifs.good())
    {
        memset(token, 0, sizeof(sgx_launch_token_t));
    }
    else
    {
        ifs.read(reinterpret_cast<char *>(&token), sizeof(sgx_launch_token_t));
        if(ifs.fail())
        {
            memset(&token, 0, sizeof(sgx_launch_token_t));
        }
    }

    // Allocate memory to save the sealed data.
    uint32_t sealed_len = sizeof(sgx_sealed_data_t) + sizeof(uint32_t);
    for(int i = 0; i < BUF_NUM; i++)
    {
        sealed_buf.sealed_buf_ptr[i] = (uint8_t *)malloc(sealed_len);
        if(sealed_buf.sealed_buf_ptr[i] == NULL)
        {
            cout << "Out of memory" << endl;
            return false;
        }
        memset(sealed_buf.sealed_buf_ptr[i], 0, sealed_len);
    }
    sealed_buf.index = 0; // index indicates which buffer contains current sealed data and which contains the backup sealed data

    return true;
}

void release_source()
{
    for(int i = 0; i < BUF_NUM; i++)
    {
        if(sealed_buf.sealed_buf_ptr[i] != NULL)
        {
            free(sealed_buf.sealed_buf_ptr[i]);
            sealed_buf.sealed_buf_ptr[i] = NULL;
        }
    }
    fini_rwlock(&lock_eid);
    return;
}

int main(int argc, char* argv[])
{
    (void)argc, (void)argv;


    // Initialize the global data
    if(!set_global_data())
    {
        release_source();
        cout << "Enter a character before exit ..." << endl;
        getchar();
        return -1;
    }

    // Load and initialize the signed enclave
    // sealed_buf == NULL indicates it is the first time to initialize the enclave.
    sgx_status_t ret = load_and_initialize_enclave(&global_eid , NULL);
    if(ret != SGX_SUCCESS)
    {
        ret_error_support(ret);
        release_source();
        cout << "Enter a character before exit ..." << endl;
        getchar();
        return -1;
    }

    cout << "****************************************************************" << endl;
    cout << "Demonstrating Power transition needs your cooperation." << endl
        << "Please take the following actions:" << endl
        << "    1. Enter a character;" << endl
        << "    2. Manually put the OS into a sleep or hibernate state;" << endl
        << "    3. Resume the OS from that state;" << endl
        << "Then you will see the application continues." << endl;
    cout << "****************************************************************" << endl;
    cout << "Now enter a character ...";
    getchar();

    // Create multiple threads to calculate the sum
    thread trd[THREAD_NUM];
    for (int i = 0; i< THREAD_NUM; i++)
    {
        trd[i] = thread(thread_func);
    }
    for (int i = 0; i < THREAD_NUM; i++)
    {
        trd[i].join();
    }

    // Release resources
    release_source();

    // Destroy the enclave
    sgx_destroy_enclave(global_eid);

    cout << "Enter a character before exit ..." << endl;
    getchar();
    return 0;
}

