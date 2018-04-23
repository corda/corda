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


#ifndef _VMC_DATABASE_EXTERNAL_H
#define _VMC_DATABASE_EXTERNAL_H

#include "sgx_attributes.h"
#include "sgx_report.h"
#include "sgx_key.h"
#include "pse_inc.h"
#include "pse_types.h"
#include "sgx_sha256_128.h"

#define PSE_VMC_QUOTA_SIZE 256

#define ROOT_HASH_SIZE  SGX_SHA256_128_HASH_SIZE
#define HASH_VALUE_SIZE SGX_SHA256_HASH_SIZE

#define INIT_MAX_HASH_TREE_LAYER            14
#define INIT_LEAF_NODE_ID_BASE              8192                            // 2^13
#define INIT_MAX_LEAF_NODE_ID               16383                           // 2^14-1
#define INIT_MIN_LEAF_NODE_ID               (INIT_LEAF_NODE_ID_BASE)
#define INIT_INTERNAL_NODE_NR               ((INIT_MAX_HASH_TREE_LAYER)-2)
#define INIT_TOTAL_ANCESTORS_NODE_NUMBER    ((INIT_MAX_HASH_TREE_LAYER)-2)
#define INIT_TOTAL_BROTHERS_NODE_NUMBER     ((INIT_MAX_HASH_TREE_LAYER)-1)
#define INIT_TOTAL_NODE_NUMBER_FOR_READING  ((INIT_TOTAL_ANCESTORS_NODE_NUMBER)+(INIT_TOTAL_BROTHERS_NODE_NUMBER)+1)

#define TREE_NODE_CACHE_SIZE                static_cast<uint32_t>(sizeof(tree_node_cache_t))
#define ROOT_NODE_SIZE                static_cast<uint32_t>(sizeof(hash_tree_root_node_t))
#define INTERNAL_NODE_SIZE            static_cast<uint32_t>(sizeof(hash_tree_internal_node_t))
#define LEAF_NODE_SIZE                static_cast<uint32_t>(sizeof(hash_tree_leaf_node_t))

#pragma pack(push, 1)

// use script to generate this data set
const uint8_t internal_node_hash_value_table[23][32] = {
#include "hashtable.txt" 
};

typedef struct _root_node {
    uint8_t  hash[ROOT_HASH_SIZE];
}hash_tree_root_node_t;

typedef struct _internal_node {
    uint8_t hash[HASH_VALUE_SIZE];
}hash_tree_internal_node_t;

typedef struct _leaf_node {
    uint8_t          is_used;             // flag that indicates 
    uint8_t          nonce[13];           //
    uint32_t         value;               //
    uint16_t         owner_policy;        //
    uint8_t          owner_attr_mask[16]; //
    uint8_t          owner_id[32];        //
    sgx_isv_svn_t    owner_svn;           //creator's SVN
}hash_tree_leaf_node_t, vmc_data_blob_t;

#define UUID_ENTRY_INDEX_SIZE 3
#define UUID_NONCE_SIZE 13

typedef struct _mc_rpdb_uuid {
    uint8_t entry_index[UUID_ENTRY_INDEX_SIZE];
    uint8_t nonce[UUID_NONCE_SIZE];
}mc_rpdb_uuid_t;

#define INVALID_VMC_ID 0xFFFFFF

#define SIM_ME_MC_READ 0
#define SIM_ME_MC_INC_BY_ONE  1
#define SIM_ME_MC_INC_BY_TWO  2

#define MAX_VMC_ENTRY_NR_LIMIT 8192

typedef struct _cal_root_hash_buf {
    uint8_t             children_hash[HASH_VALUE_SIZE*2];   // left_child_hash followed by right_child_hash, for sha256-128 calculation 
    uint8_t             pairing_nonce[16];
    uint32_t            rp_epoch;
}cal_root_hash_buf_t;

typedef enum _pse_vmc_db_state {
    PSE_VMC_DB_STATE_WORKABLE,
    PSE_VMC_DB_STATE_DOWN
}pse_vmc_db_state_t;

typedef enum _leafnode_flag_op_type {
    CLR_LEAFNODE_FLAG,
    SET_LEAFNODE_FLAG,
    GET_EMPTY_LEAFNODE,
    NON_OP
}leafnode_flag_op_type;

typedef struct _op_leafnode_flag_t {
    leafnode_flag_op_type op_type;
    sgx_measurement_t  mr_signer; // The value of the enclave SIGNER's measurement
}op_leafnode_flag_t;

typedef struct _cse_rpdata_t
{
    uint8_t rpdata_roothash[ROOT_HASH_SIZE];
    uint32_t rpdata_epoch;
}cse_rpdata_t;



typedef enum _rpdb_op_t {
    RPDB_OP_CREATE,
    RPDB_OP_READ,
    RPDB_OP_INCREMENT,
    RPDB_OP_DELETE
}rpdb_op_t;

typedef struct _leaf_node_cache_t {
    uint32_t node_id;
    hash_tree_leaf_node_t leaf;
}leaf_node_cache_t;

typedef struct _internal_node_cache_t {
    uint32_t node_id;
    hash_tree_internal_node_t internal;
}internal_node_cache_t;

typedef struct _pse_vmc_hash_tree_cache_t {
    leaf_node_cache_t self;
    leaf_node_cache_t brother;
    internal_node_cache_t ancestors[INIT_INTERNAL_NODE_NR];
    internal_node_cache_t brother_of_ancestors[INIT_INTERNAL_NODE_NR];
    hash_tree_root_node_t root;
}pse_vmc_hash_tree_cache_t;

typedef struct _pse_vmc_children_of_root_t {
    internal_node_cache_t left_child;
    internal_node_cache_t rigth_child;
}pse_vmc_children_of_root_t;

#pragma pack(pop)

#endif

