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


#ifndef _SIMPLE_VECOTR_H
#define _SIMPLE_VECOTR_H

#include <stdint.h>

#ifdef  __cplusplus
extern "C" {
#endif

typedef struct _simple_vector
{
    uint32_t size;
    uint32_t capacity;
    void** data;
}simple_vector;

//call vector_init first or set all field to 0 to use a simple_vector
void vector_init(simple_vector* vector);

//get number of elements in simple_vector
uint32_t vector_size(const simple_vector* vector);

//insert an element to the end of simple_vector, the element can only be pointer
errno_t vector_push_back(simple_vector* vector, const void* data);

//get an element
errno_t vector_get(const simple_vector* v, uint32_t index, void** data);

//set an element content
errno_t vector_set(simple_vector* v, uint32_t index, const void* data);

//free the simple_vector allocated memory
void vector_free(simple_vector* vector);

#ifdef  __cplusplus
}
#endif

#endif
