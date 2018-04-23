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

#pragma once

#ifndef _PROTECTED_FS_NODES_H_
#define _PROTECTED_FS_NODES_H_

#include <stdint.h>

#include <sgx_report.h>
#include <sgx_tcrypto.h>
#include <sgx_tae_service.h>

#pragma pack(push, 1)

#define NODE_SIZE 4096
// the rest of the 'defines' are relative to node size, so we can decrease node size in tests and have deeper tree

// compile time checks
#define COMPILE_TIME_ASSERT(compile_time_assert_name,compile_time_assert_condition) \
    typedef unsigned char compile_time_assert_name[(compile_time_assert_condition) ? 1 : -1]

typedef uint8_t sgx_iv_t[SGX_AESGCM_IV_SIZE];

#define SGX_FILE_ID            0x5347585F46494C45 // SGX_FILE
#define SGX_FILE_MAJOR_VERSION 0x01
#define SGX_FILE_MINOR_VERSION 0x00

typedef struct _meta_data_plain
{
	uint64_t         file_id;
	uint8_t          major_version;
	uint8_t          minor_version;

	sgx_key_id_t     meta_data_key_id;
	sgx_cpu_svn_t    cpu_svn;
	sgx_isv_svn_t    isv_svn;
	uint8_t          use_user_kdk_key;
	sgx_attributes_t attribute_mask;

	sgx_aes_gcm_128bit_tag_t meta_data_gmac;
	
	uint8_t          update_flag;
} meta_data_plain_t;

// these are all defined as relative to node size, so we can decrease node size in tests and have deeper tree
#define FILENAME_MAX_LEN  260
#define MD_USER_DATA_SIZE (NODE_SIZE*3/4)  // 3072
COMPILE_TIME_ASSERT(md_user_data_size, MD_USER_DATA_SIZE == 3072);

typedef struct _meta_data_encrypted
{
	char          clean_filename[FILENAME_MAX_LEN];
	int64_t       size;
	
	sgx_mc_uuid_t mc_uuid; // not used
	uint32_t      mc_value; // not used

	sgx_aes_gcm_128bit_key_t mht_key;
	sgx_aes_gcm_128bit_tag_t mht_gmac;

	uint8_t       data[MD_USER_DATA_SIZE];
} meta_data_encrypted_t;

typedef uint8_t meta_data_encrypted_blob_t[sizeof(meta_data_encrypted_t)];

#define META_DATA_NODE_SIZE NODE_SIZE
typedef uint8_t meta_data_padding_t[META_DATA_NODE_SIZE - (sizeof(meta_data_plain_t) + sizeof(meta_data_encrypted_blob_t))];

typedef struct _meta_data_node
{
	meta_data_plain_t          plain_part;
	meta_data_encrypted_blob_t encrypted_part;
	meta_data_padding_t        padding;
} meta_data_node_t;

COMPILE_TIME_ASSERT(sizeof_meta_data_node_t, sizeof(meta_data_node_t) == 4096);


typedef struct _data_node_crypto
{
	sgx_aes_gcm_128bit_key_t key;
	sgx_aes_gcm_128bit_tag_t gmac;
} gcm_crypto_data_t;

// for NODE_SIZE == 4096, we have 96 attached data nodes and 32 mht child nodes
// for NODE_SIZE == 2048, we have 48 attached data nodes and 16 mht child nodes
// for NODE_SIZE == 1024, we have 24 attached data nodes and 8 mht child nodes
#define ATTACHED_DATA_NODES_COUNT ((NODE_SIZE/sizeof(gcm_crypto_data_t))*3/4) // 3/4 of the node size is dedicated to data nodes
COMPILE_TIME_ASSERT(attached_data_nodes_count, ATTACHED_DATA_NODES_COUNT == 96);
#define CHILD_MHT_NODES_COUNT ((NODE_SIZE/sizeof(gcm_crypto_data_t))*1/4) // 1/4 of the node size is dedicated to child mht nodes
COMPILE_TIME_ASSERT(child_mht_nodes_count, CHILD_MHT_NODES_COUNT == 32);

typedef struct _mht_node
{
	gcm_crypto_data_t data_nodes_crypto[ATTACHED_DATA_NODES_COUNT];
	gcm_crypto_data_t mht_nodes_crypto[CHILD_MHT_NODES_COUNT];
} mht_node_t;

COMPILE_TIME_ASSERT(sizeof_mht_node_t, sizeof(mht_node_t) == 4096);

typedef struct _data_node
{
	uint8_t data[NODE_SIZE];
} data_node_t;

COMPILE_TIME_ASSERT(sizeof_data_node_t, sizeof(data_node_t) == 4096);

typedef struct _encrypted_node
{
	uint8_t cipher[NODE_SIZE];
} encrypted_node_t;

COMPILE_TIME_ASSERT(sizeof_encrypted_node_t, sizeof(encrypted_node_t) == 4096);

typedef struct _recovery_node
{
	uint64_t physical_node_number;
	uint8_t node_data[NODE_SIZE];
} recovery_node_t;

#pragma pack(pop)

#endif // _PROTECTED_FS_NODES_H_

