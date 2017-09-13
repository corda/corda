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

#ifndef _PROTECTED_FS_H_
#define _PROTECTED_FS_H_

#include "protected_fs_nodes.h"
#include "lru_cache.h"
#include "sgx_error.h"
#include "sgx_tcrypto.h"
#include "errno.h"

#include <sgx_thread.h>
#include "sgx_tprotected_fs.h"

typedef enum
{
	SGX_FILE_STATUS_OK = 0,
	SGX_FILE_STATUS_NOT_INITIALIZED,
	SGX_FILE_STATUS_FLUSH_ERROR,
	SGX_FILE_STATUS_WRITE_TO_DISK_FAILED,
	SGX_FILE_STATUS_CRYPTO_ERROR,
	SGX_FILE_STATUS_CORRUPTED,
	SGX_FILE_STATUS_MEMORY_CORRUPTED,
	//SGX_FILE_STATUS_WRITE_TO_DISK_FAILED_NEED_MC,
	//SGX_FILE_STATUS_MC_NOT_INCREMENTED,
	SGX_FILE_STATUS_CLOSED,
} protected_fs_status_e;

/* copied from tseal_internal.h */
/* set MISCMASK.exinfo_bit = 0 for data migration to the enclave
   built with the SDK that supports exinfo bit */
#define SGX_MISCSEL_EXINFO     0x00000001  /* report #PF and #GP inside enclave */
#define TSEAL_DEFAULT_MISCMASK (~SGX_MISCSEL_EXINFO)
/* end of copied... */

#define MAX_PAGES_IN_CACHE 48

COMPILE_TIME_ASSERT(filename_length, FILENAME_MAX_LEN == FILENAME_MAX);

typedef void FILE;

typedef union
{
	struct
	{
		uint8_t read   :1;
		uint8_t write  :1;
		uint8_t append :1;
		uint8_t binary: 1;
		uint8_t update: 1;
	};
	uint8_t raw;
} open_mode_t;


#define FILE_MHT_NODE_TYPE  1
#define FILE_DATA_NODE_TYPE 2

#define PATHNAME_MAX_LEN      (512)
#define FULLNAME_MAX_LEN      (PATHNAME_MAX_LEN+FILENAME_MAX_LEN)
#define RECOVERY_FILE_MAX_LEN (FULLNAME_MAX_LEN+10)

#pragma pack(push, 1)

/*
the following 2 structures are almost identical, i do not merge them (union or c++ inheritance from parent class) for 2 reasons:
1. when the code was written, they were much more different, during time the differences were almost gone, but "if it's working don't fix it", so i leave it this way
2. the code is more readable this way, it is clear when we deal with each type
*/
typedef struct _file_mht_node
{
	/* these are exactly the same as file_data_node_t below, any change should apply to both (both are saved in the cache as void*) */
	uint8_t type;
	uint64_t mht_node_number;
	struct _file_mht_node* parent;
	bool need_writing;
	bool new_node;
	union {
		struct {
			uint64_t physical_node_number;
			encrypted_node_t encrypted; // the actual data from the disk
		};
		recovery_node_t recovery_node;
	};
	/* from here the structures are different */
	mht_node_t plain; // decrypted data
} file_mht_node_t;


typedef struct _file_data_node
{
	/* these are exactly the same as file_mht_node_t above, any change should apply to both (both are saved in the cache as void*) */
	uint8_t type;
	uint64_t data_node_number;
	file_mht_node_t* parent;
	bool need_writing;
	bool new_node;
	union {
		struct {
			uint64_t physical_node_number;
			encrypted_node_t encrypted; // the actual data from the disk
		};
		recovery_node_t recovery_node;
	};
	/* from here the structures are different */
	data_node_t plain; // decrypted data
} file_data_node_t;


class protected_fs_file
{
private:
	union {
		struct {
			uint64_t meta_data_node_number; // for recovery purpose, so it is easy to write this node
			meta_data_node_t file_meta_data; // actual data from disk's meta data node
		};
		recovery_node_t meta_data_recovery_node;
	};

	meta_data_encrypted_t encrypted_part_plain; // encrypted part of meta data node, decrypted
	
	file_mht_node_t root_mht; // the root of the mht is always needed (for files bigger than 3KB)

	FILE* file; // OS's FILE pointer
	
	open_mode_t open_mode;
	uint8_t read_only;
	int64_t offset; // current file position (user's view)
	bool end_of_file; // flag

	int64_t real_file_size;
	
	bool need_writing; // flag
	uint32_t last_error; // last operation error
	protected_fs_status_e file_status;
	
	sgx_thread_mutex_t mutex;

	uint8_t use_user_kdk_key;
	sgx_aes_gcm_128bit_key_t user_kdk_key; // recieved from user, used instead of the seal key

	sgx_aes_gcm_128bit_key_t cur_key;
	sgx_aes_gcm_128bit_key_t session_master_key;
	uint32_t master_key_count;
	
	char recovery_filename[RECOVERY_FILE_MAX_LEN]; // might include full path to the file

	lru_cache cache;

	// these don't change after init...
	sgx_iv_t empty_iv;
	sgx_report_t report;

	void init_fields();
	bool cleanup_filename(const char* src, char* dest);
	bool parse_mode(const char* mode);
	bool file_recovery(const char* filename);
	bool init_existing_file(const char* filename, const char* clean_filename, const sgx_aes_gcm_128bit_key_t* import_key);
	bool init_new_file(const char* clean_filename);
	
	bool generate_secure_blob(sgx_aes_gcm_128bit_key_t* key, const char* label, uint64_t physical_node_number, sgx_aes_gcm_128bit_tag_t* output);
	bool generate_secure_blob_from_user_kdk(bool restore);
	bool init_session_master_key();
	bool derive_random_node_key(uint64_t physical_node_number);
	bool generate_random_meta_data_key();
	bool restore_current_meta_data_key(const sgx_aes_gcm_128bit_key_t* import_key);
	
	
	file_data_node_t* get_data_node();
	file_data_node_t* read_data_node();
	file_data_node_t* append_data_node();
	file_mht_node_t* get_mht_node();
	file_mht_node_t* read_mht_node(uint64_t mht_node_number);
	file_mht_node_t* append_mht_node(uint64_t mht_node_number);
	bool write_recovery_file();
	bool set_update_flag(bool flush_to_disk);
	void clear_update_flag();
	bool update_all_data_and_mht_nodes();
	bool update_meta_data_node();
	bool write_all_changes_to_disk(bool flush_to_disk);
	void erase_recovery_file();
	bool internal_flush(/*bool mc,*/ bool flush_to_disk);

public:
	protected_fs_file(const char* filename, const char* mode, const sgx_aes_gcm_128bit_key_t* import_key, const sgx_aes_gcm_128bit_key_t* kdk_key);
	~protected_fs_file();

	size_t write(const void* ptr, size_t size, size_t count);
	size_t read(void* ptr, size_t size, size_t count);
	int64_t tell();
	int seek(int64_t new_offset, int origin);
	bool get_eof();
	uint32_t get_error();
	void clear_error();
	int32_t clear_cache();
	bool flush(/*bool mc*/);
	bool pre_close(sgx_key_128bit_t* key, bool import);
	static int32_t remove(const char* filename);
};

#pragma pack(pop)

#endif // _PROTECTED_FS_H_
