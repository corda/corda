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

#include "sgx_tprotected_fs.h"
#include "sgx_tprotected_fs_t.h"
#include "protected_fs_file.h"

#include <sgx_utils.h>

// remove the file path if it's there, leave only the filename, null terminated
bool protected_fs_file::cleanup_filename(const char* src, char* dest)
{
	const char* p = src;
	const char* name = src;

	while ((*p) != '\0')
	{
		if ((*p) == '\\' || (*p) == '/')
			name = p+1;
		p++;
	}

	if (strnlen(name, FILENAME_MAX_LEN) >= FILENAME_MAX_LEN-1)
	{
		last_error = ENAMETOOLONG;
		return false;
	}

	strncpy(dest, name, FILENAME_MAX_LEN-1);
	dest[FILENAME_MAX_LEN-1] = '\0';

	if (strnlen(dest, 1) == 0)
	{
		last_error = EINVAL;
		return false;
	}

	return true;
}


protected_fs_file::protected_fs_file(const char* filename, const char* mode, const sgx_aes_gcm_128bit_key_t* import_key, const sgx_aes_gcm_128bit_key_t* kdk_key)
{
	sgx_status_t status = SGX_SUCCESS;
	uint8_t result = 0;
	int32_t result32 = 0;
	
	init_fields();

	if (filename == NULL || mode == NULL || 
		strnlen(filename, 1) == 0 || strnlen(mode, 1) == 0)
	{
		last_error = EINVAL;
		return;
	}

	if (strnlen(filename, FULLNAME_MAX_LEN) >= FULLNAME_MAX_LEN - 1)
	{
		last_error = ENAMETOOLONG;
		return;
	}

	if (import_key != NULL && kdk_key != NULL)
	{// import key is used only with auto generated keys
		last_error = EINVAL;
		return;
	}

	status = sgx_create_report(NULL, NULL, &report);
	if (status != SGX_SUCCESS)
	{
		last_error = status;
		return;
	}

	result32 = sgx_thread_mutex_init(&mutex, NULL);
	if (result32 != 0)
	{
		last_error = result32;
		return;
	}

	if (init_session_master_key() == false) 
		// last_error already set
		return;

	if (kdk_key != NULL)
	{
		// for new file, this value will later be saved in the meta data plain part (init_new_file)
		// for existing file, we will later compare this value with the value from the file (init_existing_file)
		use_user_kdk_key = 1; 
		memcpy(user_kdk_key, kdk_key, sizeof(sgx_aes_gcm_128bit_key_t));
	}
	
	// get the clean file name (original name might be clean or with relative path or with absolute path...)
	char clean_filename[FILENAME_MAX_LEN];
	if (cleanup_filename(filename, clean_filename) == false)
		// last_error already set
		return;
	
	if (import_key != NULL)
	{// verify the key is not empty - note from SAFE review
		sgx_aes_gcm_128bit_key_t empty_aes_key = {0};
		if (consttime_memequal(import_key, &empty_aes_key, sizeof(sgx_aes_gcm_128bit_key_t)) == 1)
		{
			last_error = EINVAL;
			return;
		}
	}

	if (parse_mode(mode) == false)
	{
		last_error = EINVAL;
		return;
	}

	status = u_sgxprotectedfs_check_if_file_exists(&result, filename); // if result == 1 --> file exists
	if (status != SGX_SUCCESS)
	{
		last_error = status;
		return;
	}

	if (open_mode.write == 1 && result == 1)
	{// try to delete existing file
		int32_t saved_errno = 0;

		result32 = remove(filename);
		if (result32 != 0)
		{
			// either can't delete or the file was already deleted by someone else
			saved_errno = errno;
			errno = 0;
		}

		// re-check
		status = u_sgxprotectedfs_check_if_file_exists(&result, filename);
		if (status != SGX_SUCCESS || result == 1)
		{
			last_error = (status != SGX_SUCCESS) ? status :
						 (saved_errno != 0) ? saved_errno : EACCES;
			return;
		}
	}

	if (open_mode.read == 1 && result == 0)
	{// file must exists
		last_error = ENOENT;
		return;
	}

	if (import_key != NULL && result == 0)
	{// file must exists - otherwise the user key is not used
		last_error = ENOENT;
		return;
	}

	// now open the file
	read_only = (open_mode.read == 1 && open_mode.update == 0); // read only files can be opened simultaneously by many enclaves

	do {
		status = u_sgxprotectedfs_exclusive_file_open(&file, filename, read_only, &real_file_size, &result32);
		if (status != SGX_SUCCESS || file == NULL)
		{
			last_error = (status != SGX_SUCCESS) ? status :
					     (result32 != 0) ? result32 : EACCES;
			break;
		}

		if (real_file_size < 0)
		{
			last_error = EINVAL;
			break;
		}

		if (real_file_size % NODE_SIZE != 0)
		{
			last_error = SGX_ERROR_FILE_NOT_SGX_FILE;
			break;
		}
		
		strncpy(recovery_filename, filename, FULLNAME_MAX_LEN - 1); // copy full file name
		recovery_filename[FULLNAME_MAX_LEN - 1] = '\0'; // just to be safe
		size_t full_name_len = strnlen(recovery_filename, RECOVERY_FILE_MAX_LEN);
		strncpy(&recovery_filename[full_name_len], "_recovery", 10);

		if (real_file_size > 0)
		{// existing file
			if (open_mode.write == 1) // redundant check, just in case
			{
				last_error = EACCES;
				break;
			}

			if (init_existing_file(filename, clean_filename, import_key) == false)
				break;
				
			if (open_mode.append == 1 && open_mode.update == 0)
				offset = encrypted_part_plain.size;
		}
		else
		{// new file
			if (init_new_file(clean_filename) == false)
				break;
		}

		file_status = SGX_FILE_STATUS_OK;

	} while(0);

	if (file_status != SGX_FILE_STATUS_OK)
	{
		if (file != NULL)
		{
			u_sgxprotectedfs_fclose(&result32, file); // we don't care about the result
			file = NULL;
		}
	}
}


void protected_fs_file::init_fields()
{
	meta_data_node_number = 0;
	memset(&file_meta_data, 0, sizeof(meta_data_node_t));
	memset(&encrypted_part_plain, 0, sizeof(meta_data_encrypted_t));

	memset(&empty_iv, 0, sizeof(sgx_iv_t));

	memset(&root_mht, 0, sizeof(file_mht_node_t));
	root_mht.type = FILE_MHT_NODE_TYPE;
	root_mht.physical_node_number = 1;
	root_mht.mht_node_number = 0;
	root_mht.new_node = true;
	root_mht.need_writing = false;
	
	offset = 0;
	file = NULL;
	end_of_file = false;
	need_writing = false;
	read_only = 0;
	file_status = SGX_FILE_STATUS_NOT_INITIALIZED;
	last_error = SGX_SUCCESS;
	real_file_size = 0;	
	open_mode.raw = 0;
	use_user_kdk_key = 0;
	master_key_count = 0;

	recovery_filename[0] = '\0';
	
	memset(&mutex, 0, sizeof(sgx_thread_mutex_t));

	// set hash size to fit MAX_PAGES_IN_CACHE
	cache.rehash(MAX_PAGES_IN_CACHE);
}


#define MAX_MODE_STRING_LEN 5
bool protected_fs_file::parse_mode(const char* mode)
{
	if (mode == NULL) // re-check
		return false;

	size_t mode_len = strnlen(mode, MAX_MODE_STRING_LEN+1);
	if (mode_len > MAX_MODE_STRING_LEN)
		return false;

	for (size_t i = 0 ; i < mode_len ; i++)
	{
		switch (mode[i])
		{
		case 'r':
			if (open_mode.write == 1 || open_mode.read == 1 || open_mode.append == 1)
				return false;
			open_mode.read = 1;
			break;
		case 'w':
			if (open_mode.write == 1 || open_mode.read == 1 || open_mode.append == 1)
				return false;
			open_mode.write = 1;
			break;
		case 'a':
			if (open_mode.write == 1 || open_mode.read == 1 || open_mode.append == 1)
				return false;
			open_mode.append = 1;
			break;
		case 'b':
			if (open_mode.binary == 1)
				return false;
			open_mode.binary = 1;
			break;
		case '+':
			if (open_mode.update == 1)
				return false;
			open_mode.update = 1;
			break;
		default:
			return false;
		}
	}

	if (open_mode.write == 0 && open_mode.read == 0 && open_mode.append == 0)
		return false;

	return true;
}


bool protected_fs_file::file_recovery(const char* filename)
{
	sgx_status_t status = SGX_SUCCESS;
	int32_t result32 = 0;
	int64_t new_file_size = 0;

	status = u_sgxprotectedfs_fclose(&result32, file);
	if (status != SGX_SUCCESS || result32 != 0)
	{
		last_error = (status != SGX_SUCCESS) ? status : 
					 (result32 != -1) ? result32 : EINVAL;
		return false;
	}

	file = NULL;

	status = u_sgxprotectedfs_do_file_recovery(&result32, filename, recovery_filename, NODE_SIZE);
	if (status != SGX_SUCCESS || result32 != 0)
	{
		last_error = (status != SGX_SUCCESS) ? status :
					 (result32 != -1) ? result32 : EINVAL;
		return false;
	}

	status = u_sgxprotectedfs_exclusive_file_open(&file, filename, read_only, &new_file_size, &result32);
	if (status != SGX_SUCCESS || file == NULL)
	{
		last_error = (status != SGX_SUCCESS) ? status : 
					 (result32 != 0) ? result32 : EACCES;
		return false;
	}

	// recovery only change existing data, it does not shrink or grow the file
	if (new_file_size != real_file_size)
	{
		last_error = SGX_ERROR_UNEXPECTED;
		return false;
	}

	status = u_sgxprotectedfs_fread_node(&result32, file, 0, (uint8_t*)&file_meta_data, NODE_SIZE);
	if (status != SGX_SUCCESS || result32 != 0)
	{
		last_error = (status != SGX_SUCCESS) ? status : 
					 (result32 != -1) ? result32 : EIO;
		return false;
	}

	return true;
}


bool protected_fs_file::init_existing_file(const char* filename, const char* clean_filename, const sgx_aes_gcm_128bit_key_t* import_key)
{
	sgx_status_t status;
	int32_t result32;

	// read meta-data node
	status = u_sgxprotectedfs_fread_node(&result32, file, 0, (uint8_t*)&file_meta_data, NODE_SIZE);
	if (status != SGX_SUCCESS || result32 != 0)
	{
		last_error = (status != SGX_SUCCESS) ? status : 
					 (result32 != -1) ? result32 : EIO;
		return false;
	}

	if (file_meta_data.plain_part.file_id != SGX_FILE_ID)
	{// such a file exists, but it is not an SGX file
		last_error = SGX_ERROR_FILE_NOT_SGX_FILE;
		return false;
	}

	if (file_meta_data.plain_part.major_version != SGX_FILE_MAJOR_VERSION)
	{
		last_error = ENOTSUP;
		return false;
	}

	if (file_meta_data.plain_part.update_flag == 1)
	{// file was in the middle of an update, must do a recovery
		if (file_recovery(filename) == false)
		{// override internal error
			last_error = SGX_ERROR_FILE_RECOVERY_NEEDED;
			return false;
		}

		if (file_meta_data.plain_part.update_flag == 1) // recovery failed, flag is still set!
		{// recovery didn't clear the flag
			last_error = SGX_ERROR_FILE_RECOVERY_NEEDED;
			return false;
		}

		// re-check after recovery
		if (file_meta_data.plain_part.major_version != SGX_FILE_MAJOR_VERSION)
		{
			last_error = ENOTSUP;
			return false;
		}
	}

	if (file_meta_data.plain_part.use_user_kdk_key != use_user_kdk_key)
	{
		last_error = EINVAL;
		return false;
	}

	if (restore_current_meta_data_key(import_key) == false)
		return false;

	// decrypt the encrypted part of the meta-data
	status = sgx_rijndael128GCM_decrypt(&cur_key, 
										(const uint8_t*)file_meta_data.encrypted_part, sizeof(meta_data_encrypted_blob_t), (uint8_t*)&encrypted_part_plain,
										empty_iv, SGX_AESGCM_IV_SIZE,
										NULL, 0,
										&file_meta_data.plain_part.meta_data_gmac);
	if (status != SGX_SUCCESS)
	{
		last_error = status;
		return false;
	}

	if (strncmp(clean_filename, encrypted_part_plain.clean_filename, FILENAME_MAX_LEN) != 0)
	{
		last_error = SGX_ERROR_FILE_NAME_MISMATCH;
		return false;
	}

/*
	sgx_mc_uuid_t empty_mc_uuid = {0};

	// check if the file contains an active monotonic counter
	if (consttime_memequal(&empty_mc_uuid, &encrypted_part_plain.mc_uuid, sizeof(sgx_mc_uuid_t)) == 0)
	{
		uint32_t mc_value = 0;

		status = sgx_read_monotonic_counter(&encrypted_part_plain.mc_uuid, &mc_value);
		if (status != SGX_SUCCESS)
		{
			last_error = status;
			return false;
		}

		if (encrypted_part_plain.mc_value < mc_value)
		{
			last_error = SGX_ERROR_FILE_MONOTONIC_COUNTER_IS_BIGGER;
			return false;
		}

		if (encrypted_part_plain.mc_value == mc_value + 1) // can happen if AESM failed - file value stayed one higher
		{
			sgx_status_t status = sgx_increment_monotonic_counter(&encrypted_part_plain.mc_uuid, &mc_value);
			if (status != SGX_SUCCESS)
			{
				file_status = SGX_FILE_STATUS_MC_NOT_INCREMENTED;
				last_error = status;
				return false;
			}
		}

		if (encrypted_part_plain.mc_value != mc_value)
		{
			file_status = SGX_FILE_STATUS_CORRUPTED;
			last_error = SGX_ERROR_UNEXPECTED;
			return false;
		}
	}
	else
	{
		assert(encrypted_part_plain.mc_value == 0);
		encrypted_part_plain.mc_value = 0; // do this anyway for release...
	}
*/
	if (encrypted_part_plain.size > MD_USER_DATA_SIZE)
	{
		// read the root node of the mht
		status = u_sgxprotectedfs_fread_node(&result32, file, 1, root_mht.encrypted.cipher, NODE_SIZE);
		if (status != SGX_SUCCESS || result32 != 0)
		{
			last_error = (status != SGX_SUCCESS) ? status : 
						 (result32 != -1) ? result32 : EIO;
			return false;
		}

		// this also verifies the root mht gmac against the gmac in the meta-data encrypted part
		status = sgx_rijndael128GCM_decrypt(&encrypted_part_plain.mht_key, 
											root_mht.encrypted.cipher, NODE_SIZE, (uint8_t*)&root_mht.plain, 
											empty_iv, SGX_AESGCM_IV_SIZE, NULL, 0, &encrypted_part_plain.mht_gmac);
		if (status != SGX_SUCCESS)
		{
			last_error = status;
			return false;
		}

		root_mht.new_node = false;
	}

	return true;
}


bool protected_fs_file::init_new_file(const char* clean_filename)
{
	file_meta_data.plain_part.file_id = SGX_FILE_ID;
	file_meta_data.plain_part.major_version = SGX_FILE_MAJOR_VERSION;
	file_meta_data.plain_part.minor_version = SGX_FILE_MINOR_VERSION;

	file_meta_data.plain_part.use_user_kdk_key = use_user_kdk_key;

	strncpy(encrypted_part_plain.clean_filename, clean_filename, FILENAME_MAX_LEN);
	
	need_writing = true;

	return true;
}


protected_fs_file::~protected_fs_file()
{
	void* data;
	
	while ((data = cache.get_last()) != NULL)
	{
		if (((file_data_node_t*)data)->type == FILE_DATA_NODE_TYPE) // type is in the same offset in both node types, need to scrub the plaintext
		{
			file_data_node_t* file_data_node = (file_data_node_t*)data;
			memset_s(&file_data_node->plain, sizeof(data_node_t), 0, sizeof(data_node_t));
			delete file_data_node;
		}
		else
		{
			file_mht_node_t* file_mht_node = (file_mht_node_t*)data;
			memset_s(&file_mht_node->plain, sizeof(mht_node_t), 0, sizeof(mht_node_t));
			delete file_mht_node;
		}
		cache.remove_last();
	}

	// scrub the last encryption key and the session key
	memset_s(&cur_key, sizeof(sgx_aes_gcm_128bit_key_t), 0, sizeof(sgx_aes_gcm_128bit_key_t));
	memset_s(&session_master_key, sizeof(sgx_aes_gcm_128bit_key_t), 0, sizeof(sgx_aes_gcm_128bit_key_t));
	
	// scrub first 3KB of user data and the gmac_key
	memset_s(&encrypted_part_plain, sizeof(meta_data_encrypted_t), 0, sizeof(meta_data_encrypted_t));

	sgx_thread_mutex_destroy(&mutex);
}


bool protected_fs_file::pre_close(sgx_key_128bit_t* key, bool import)
{
	int32_t result32 = 0;
	bool retval = true;
	sgx_status_t status = SGX_SUCCESS;

	sgx_thread_mutex_lock(&mutex);

	if (import == true)
	{
		if (use_user_kdk_key == 1) // import file is only needed for auto-key
			retval = false;
		else
			need_writing = true; // will re-encrypt the neta-data node with local key
	}

	if (file_status != SGX_FILE_STATUS_OK)
	{
		sgx_thread_mutex_unlock(&mutex);
		clear_error(); // last attempt to fix it
		sgx_thread_mutex_lock(&mutex);
	}
	else // file_status == SGX_FILE_STATUS_OK
	{
		internal_flush(/*false,*/ true);
	}

	if (file_status != SGX_FILE_STATUS_OK)
		retval = false;

	if (file != NULL)
	{
		status = u_sgxprotectedfs_fclose(&result32, file);
		if (status != SGX_SUCCESS || result32 != 0)
		{
			last_error = (status != SGX_SUCCESS) ? status : 
						 (result32 != -1) ? result32 : SGX_ERROR_FILE_CLOSE_FAILED;
			retval = false;
		}

		file = NULL;
	}

	if (file_status == SGX_FILE_STATUS_OK && 
		last_error == SGX_SUCCESS) // else...maybe something bad happened and the recovery file will be needed
		erase_recovery_file();

	if (key != NULL)
	{
		if (use_user_kdk_key == 1) // export key is only used for auto-key
		{
			retval = false;
		}
		else
		{
			if (restore_current_meta_data_key(NULL) == true)
				memcpy(key, cur_key, sizeof(sgx_key_128bit_t));
			else
				retval = false;
		}
	}

	file_status = SGX_FILE_STATUS_CLOSED;

	sgx_thread_mutex_unlock(&mutex);

	return retval;
}

