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


#ifndef _VMC_SQLITE_CACHE_H
#define _VMC_SQLITE_CACHE_H

#include "monotonic_counter_database_types.h"

typedef struct _tree_node_cache_t {
    uint32_t ref_counter;
    uint8_t node[0];
}tree_node_cache_t;

typedef enum _cache_op_t {
    CACHE_OP_READ,              // Read from cache
    CACHE_OP_UPDATE             // Write to cache
}cache_op_t;

typedef struct _leaf_cache_node_t
{
    uint32_t leaf_id;
    _leaf_cache_node_t* next;
} leaf_cache_node_t;

typedef struct _leaf_cache_t
{
    leaf_cache_node_t* list;      // the list of cached leaves. The most recently accessed node is at the head.
    uint32_t size;              // list length, should never exceed MAX_LEAF_CACHE_NUM
} leaf_cache_t;

void flush_hash_tree_cache();
pse_op_error_t access_hash_tree_cache(const rpdb_op_t rpdb_op,           // vmc operation type
                                      const cache_op_t cache_op,         // read/update cache
                                      pse_vmc_hash_tree_cache_t *cache,  // buffer that stores tree nodes required by a VMC operation
                                      const uint8_t *root_hash);         // current root hash

#endif

