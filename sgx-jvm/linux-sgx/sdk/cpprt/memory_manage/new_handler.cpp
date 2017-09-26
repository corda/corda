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


#include <stdlib.h>
#include <new>
#include "sgx_trts.h"
#include "sgx_spinlock.h"
#include "internal/se_cdefs.h"

namespace std{
    static sgx_spinlock_t handler_lock = SGX_SPINLOCK_INITIALIZER;
    //default hanlder is NULL
    static new_handler new_handl = NULL;

    // set_new_handler()
    //      Sets new_p as the new handler function.
    //      The new handler function is the function that is called by functions 
    //      operator new or operator new[] when they are not successful 
    //      in an attempt to allocate memory.
    // Parameter
    //      handler -  a pointer to the handler to be called.
    //					  The function can make more storage available or throw an exception or terminate the program.
    //					  new_handler is a function pointer type taking no parameters and returning void.
    // Return Value
    //      new_handler - The value of the current new_handler function if this has been previously set by this function
    //									 NULL -if this is the first call to set_new_handler
    new_handler set_new_handler(new_handler handle)
    {
        sgx_spin_lock(&handler_lock);
        new_handler retHandle = new_handl;
        if ( handle == NULL ){
            new_handl = NULL;
        } else if ( sgx_is_within_enclave((void *)handle, 0) ){
            //only set the handler when handler address is inside enclave
            new_handl = handle;
        }
        sgx_spin_unlock(&handler_lock);
        return retHandle;
    }
};

using namespace std;

//call new_handl function when  new memory failed
int  call_newh()
{
    int ret = 0;
    sgx_spin_lock(&handler_lock);
    new_handler handler = new_handl;
    //unlock the handler here because new_handl may call set_new_handler again, will cause dead lock.
    sgx_spin_unlock(&handler_lock);

    // call new handler
    if ( handler != NULL ){
        handler();
        ret = 1;
    }
   
    return ret;
}
