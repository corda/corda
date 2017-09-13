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


#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include "simple_vector.h"

//initial vector capacity when fisrt item is added to vector
#define INIT_SIZE 10

//max vector capacity of a vector
#define MAX_SIZE 10000

//init vector to all zero
void vector_init(simple_vector* v)
{
    if (v)
    {
        v->size = 0;
        v->capacity = 0;
        v->data = NULL;
    }
}

//return current number of items the vector holds  
uint32_t vector_size(const simple_vector* v)
{
    if (v)
        return v->size;
    else
        return 0;
}

//push a pointer to the end of the vector
//return 0 if success, return 1 if memory malloc failure.
errno_t vector_push_back(simple_vector* v, const void* data)
{
    if (v)
    {
        if (v->capacity == 0) {
            //first item
            v->data = (void**)malloc(sizeof(void*) * INIT_SIZE);
            if (v->data ==NULL)
                return 1;
            v->capacity = INIT_SIZE;
            memset(v->data, '\0', sizeof(void*) * INIT_SIZE);
        }
        else if (v->size == v->capacity) {
            void** new_data;
            if( v->capacity >= MAX_SIZE - INIT_SIZE)
                return 1;
            //increate size by INIT_SIZE
            new_data = (void**)realloc(v->data, sizeof(void*) *( v->capacity + INIT_SIZE));
            if (new_data ==NULL)
                return 1;
            memset(&new_data[v->capacity], '\0', sizeof(void*) * INIT_SIZE);
            v->data = new_data;
            v->capacity += INIT_SIZE;
        }
        //assign new item
        v->data[v->size] = const_cast<void*>(data);
        v->size++;
        return 0;
    }
    return 1;
}

//get the item pointer in the vector
//return 0 if success, return 1 if index is out of range or data pointer is invalid.
errno_t vector_get(const simple_vector* v, uint32_t index, void** data)
{
    if (!v || index >= v->size || !data)
        return 1;
    *data = v->data[index];
    return 0;
}

//set the pointer in the vector
//return 0 if success, return 1 if index is out of range.
errno_t vector_set(simple_vector* v, uint32_t index, const void* data)
{
    if (!v || index >= v->size)
        return 1;
    v->data[index] = const_cast<void*>(data);
    return 0;
}

//release memory used by the vector
void vector_free(simple_vector* v)
{
    if (v)
    {
        v->size = 0;
        v->capacity = 0;
        if(v->data)
        {
            free(v->data);
            v->data = NULL;
        }
    }
}
