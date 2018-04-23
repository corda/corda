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
#include "validation_hook_recovery.h"

#include <sgx_trts.h>

bool protected_fs_file::flush(/*bool mc*/)
{
	bool result = false;

	int32_t result32 = sgx_thread_mutex_lock(&mutex);
	if (result32 != 0)
	{
		last_error = result32;
		file_status = SGX_FILE_STATUS_MEMORY_CORRUPTED;
		return false;
	}

	if (file_status != SGX_FILE_STATUS_OK)
	{
		last_error = SGX_ERROR_FILE_BAD_STATUS;
		sgx_thread_mutex_unlock(&mutex);
		return false;
	}
	
	result = internal_flush(/*mc,*/ true);
	if (result == false)
	{
		assert(file_status != SGX_FILE_STATUS_OK);
		if (file_status == SGX_FILE_STATUS_OK)
			file_status = SGX_FILE_STATUS_FLUSH_ERROR; // for release set this anyway
	}

	sgx_thread_mutex_unlock(&mutex);

	return result;
}


bool protected_fs_file::internal_flush(/*bool mc,*/ bool flush_to_disk)
{
	if (need_writing == false) // no changes at all
		return true;

/*
	if (mc == true && encrypted_part_plain.mc_value > (UINT_MAX-2))
	{
		last_error = SGX_ERROR_FILE_MONOTONIC_COUNTER_AT_MAX;
		return false;
	}
*/
	if (encrypted_part_plain.size > MD_USER_DATA_SIZE && root_mht.need_writing == true) // otherwise it's just one write - the meta-data node
	{
		if (_RECOVERY_HOOK_(0) || write_recovery_file() != true)
		{
			file_status = SGX_FILE_STATUS_FLUSH_ERROR;
			return false;
		}

		if (_RECOVERY_HOOK_(1) || set_update_flag(flush_to_disk) != true)
		{
			file_status = SGX_FILE_STATUS_FLUSH_ERROR;
			return false;
		}

		if (_RECOVERY_HOOK_(2) || update_all_data_and_mht_nodes() != true)
		{
			clear_update_flag();
			file_status = SGX_FILE_STATUS_CRYPTO_ERROR; // this is something that shouldn't happen, can't fix this...
			return false;
		}
	}

/*
	sgx_status_t status;

	if (mc == true)
	{
		// increase monotonic counter local value - only if everything is ok, we will increase the real counter
		if (encrypted_part_plain.mc_value == 0)
		{
			// no monotonic counter so far, need to create a new one
			status = sgx_create_monotonic_counter(&encrypted_part_plain.mc_uuid, &encrypted_part_plain.mc_value);
			if (status != SGX_SUCCESS)
			{
				clear_update_flag();
				file_status = SGX_FILE_STATUS_FLUSH_ERROR;
				last_error = status;
				return false;
			}
		}
		encrypted_part_plain.mc_value++;
	}
*/
	if (_RECOVERY_HOOK_(3) || update_meta_data_node() != true)
	{
		clear_update_flag();
		/*
		if (mc == true)
			encrypted_part_plain.mc_value--; // don't have to do this as the file cannot be fixed, but doing it anyway to prevent future errors
		*/
		file_status = SGX_FILE_STATUS_CRYPTO_ERROR; // this is something that shouldn't happen, can't fix this...
		return false;
	}

	if (_RECOVERY_HOOK_(4) || write_all_changes_to_disk(flush_to_disk) != true)
	{
		//if (mc == false)
			file_status = SGX_FILE_STATUS_WRITE_TO_DISK_FAILED; // special case, need only to repeat write_all_changes_to_disk in order to repair it
		//else
			//file_status = SGX_FILE_STATUS_WRITE_TO_DISK_FAILED_NEED_MC; // special case, need to repeat write_all_changes_to_disk AND increase the monotonic counter in order to repair it

		return false;
	}

	need_writing = false;

/* this is causing problems when we delete and create the file rapidly
   we will just leave the file, and re-write it every time
   u_sgxprotectedfs_recovery_file_open opens it with 'w' so it is truncated
	if (encrypted_part_plain.size > MD_USER_DATA_SIZE)
	{
		erase_recovery_file();
	}
*/
/*
	if (mc == true)
	{
		uint32_t mc_value;
		status = sgx_increment_monotonic_counter(&encrypted_part_plain.mc_uuid, &mc_value);
		if (status != SGX_SUCCESS)
		{
			file_status = SGX_FILE_STATUS_MC_NOT_INCREMENTED; // special case - need only to increase the MC in order to repair it
			last_error = status;
			return false;
		}
		assert(mc_value == encrypted_part_plain.mc_value);
	}
*/
	return true;
}


bool protected_fs_file::write_recovery_file()
{
	void* recovery_file = NULL;
	sgx_status_t status;
	uint8_t result = 0;
	int32_t result32 = 0;

	status = u_sgxprotectedfs_recovery_file_open(&recovery_file, recovery_filename);
	if (status != SGX_SUCCESS || recovery_file == NULL)
	{
		last_error = status != SGX_SUCCESS ? status : SGX_ERROR_FILE_CANT_OPEN_RECOVERY_FILE;
		return false;
	}

	void* data = NULL;
	recovery_node_t* recovery_node = NULL;

	for (data = cache.get_first() ; data != NULL ; data = cache.get_next())
	{
		if (((file_data_node_t*)data)->type == FILE_DATA_NODE_TYPE) // type is in the same offset in both node types
		{
			file_data_node_t* file_data_node = (file_data_node_t*)data;
			if (file_data_node->need_writing == false || file_data_node->new_node == true)
				continue;

			recovery_node = &file_data_node->recovery_node;
		}
		else
		{
			file_mht_node_t* file_mht_node = (file_mht_node_t*)data;
			assert(file_mht_node->type == FILE_MHT_NODE_TYPE);
			if (file_mht_node->need_writing == false || file_mht_node->new_node == true)
				continue;

			recovery_node = &file_mht_node->recovery_node;
		}

		status = u_sgxprotectedfs_fwrite_recovery_node(&result, recovery_file, (uint8_t*)recovery_node, sizeof(recovery_node_t));
		if (status != SGX_SUCCESS || result != 0)
		{
			u_sgxprotectedfs_fclose(&result32, recovery_file);
			u_sgxprotectedfs_remove(&result32, recovery_filename);
			last_error = status != SGX_SUCCESS ? status : SGX_ERROR_FILE_CANT_WRITE_RECOVERY_FILE;
			return false;
		}
	}

	if (root_mht.need_writing == true && root_mht.new_node == false)
	{
		status = u_sgxprotectedfs_fwrite_recovery_node(&result, recovery_file, (uint8_t*)&root_mht.recovery_node, sizeof(recovery_node_t));
		if (status != SGX_SUCCESS || result != 0)
		{
			u_sgxprotectedfs_fclose(&result32, recovery_file);
			u_sgxprotectedfs_remove(&result32, recovery_filename);
			last_error = status != SGX_SUCCESS ? status : SGX_ERROR_FILE_CANT_WRITE_RECOVERY_FILE;
			return false;
		}
	}

	status = u_sgxprotectedfs_fwrite_recovery_node(&result, recovery_file, (uint8_t*)&meta_data_recovery_node, sizeof(recovery_node_t));
	if (status != SGX_SUCCESS || result != 0)
	{
		u_sgxprotectedfs_fclose(&result32, recovery_file);
		u_sgxprotectedfs_remove(&result32, recovery_filename);
		last_error = status != SGX_SUCCESS ? status : SGX_ERROR_FILE_CANT_WRITE_RECOVERY_FILE;
		return false;
	}

	u_sgxprotectedfs_fclose(&result32, recovery_file); // TODO - check result

	return true;
}


bool protected_fs_file::set_update_flag(bool flush_to_disk)
{
	sgx_status_t status;
	uint8_t result;
	int32_t result32;

	file_meta_data.plain_part.update_flag = 1;
	status = u_sgxprotectedfs_fwrite_node(&result32, file, 0, (uint8_t*)&file_meta_data, NODE_SIZE);
	file_meta_data.plain_part.update_flag = 0; // turn it off in memory. at the end of the flush, when we'll write the meta-data to disk, this flag will also be cleared there.
	if (status != SGX_SUCCESS || result32 != 0)
	{
		last_error = (status != SGX_SUCCESS) ? status : 
					 (result32 != -1) ? result32 : EIO;
		return false;
	}

	if (flush_to_disk == true)
	{
		status = u_sgxprotectedfs_fflush(&result, file);
		if (status != SGX_SUCCESS || result != 0)
		{
			last_error = status != SGX_SUCCESS ? status : SGX_ERROR_FILE_FLUSH_FAILED;
			u_sgxprotectedfs_fwrite_node(&result32, file, 0, (uint8_t*)&file_meta_data, NODE_SIZE); // try to clear the update flag, in the OS cache at least...
			return false;
		}

	}

	return true;
}


// this function is called if we had an error after we updated the update flag
// in normal flow, the flag is cleared when the meta-data is written to disk
void protected_fs_file::clear_update_flag()
{
	uint8_t result;
	int32_t result32;

	if (_RECOVERY_HOOK_(3))
		return;
	assert(file_meta_data.plain_part.update_flag == 0);
	u_sgxprotectedfs_fwrite_node(&result32, file, 0, (uint8_t*)&file_meta_data, NODE_SIZE);
	u_sgxprotectedfs_fflush(&result, file);
}


// sort function, we need the mht nodes sorted before we start to update their gmac's
bool mht_order(const file_mht_node_t* first, const file_mht_node_t* second)
{// higher (lower tree level) node number first
	return first->mht_node_number > second->mht_node_number;
}


bool protected_fs_file::update_all_data_and_mht_nodes()
{
	std::list<file_mht_node_t*> mht_list;
	std::list<file_mht_node_t*>::iterator mht_list_it;
	file_mht_node_t* file_mht_node;
	sgx_status_t status;
	void* data = cache.get_first();

	// 1. encrypt the changed data
	// 2. set the IV+GMAC in the parent MHT
	// [3. set the need_writing flag for all the parents]
	while (data != NULL)
	{
		if (((file_data_node_t*)data)->type == FILE_DATA_NODE_TYPE) // type is in the same offset in both node types
		{
			file_data_node_t* data_node = (file_data_node_t*)data;

			if (data_node->need_writing == true)
			{
				if (derive_random_node_key(data_node->physical_node_number) == false)
					return false;

				gcm_crypto_data_t* gcm_crypto_data = &data_node->parent->plain.data_nodes_crypto[data_node->data_node_number % ATTACHED_DATA_NODES_COUNT];

				// encrypt the data, this also saves the gmac of the operation in the mht crypto node
				status = sgx_rijndael128GCM_encrypt(&cur_key, data_node->plain.data, NODE_SIZE, data_node->encrypted.cipher, 
													empty_iv, SGX_AESGCM_IV_SIZE, NULL, 0, &gcm_crypto_data->gmac);
				if (status != SGX_SUCCESS)
				{
					last_error = status;
					return false;
				}

				memcpy(gcm_crypto_data->key, cur_key, sizeof(sgx_aes_gcm_128bit_key_t)); // save the key used for this encryption

				file_mht_node = data_node->parent;
				// this loop should do nothing, add it here just to be safe
				while (file_mht_node->mht_node_number != 0)
				{
					assert(file_mht_node->need_writing == true);
					file_mht_node->need_writing = true; // just in case, for release
					file_mht_node = file_mht_node->parent;
				}
			}
		}
		data = cache.get_next();
	}

	// add all the mht nodes that needs writing to a list
	data = cache.get_first();
	while (data != NULL)
	{
		if (((file_mht_node_t*)data)->type == FILE_MHT_NODE_TYPE) // type is in the same offset in both node types
		{
			file_mht_node = (file_mht_node_t*)data;

			if (file_mht_node->need_writing == true)
				mht_list.push_front(file_mht_node);
		}

		data = cache.get_next();
	}

	// sort the list from the last node to the first (bottom layers first)
	mht_list.sort(mht_order);

	// update the gmacs in the parents
	while ((mht_list_it = mht_list.begin()) != mht_list.end())
	{
		file_mht_node = *mht_list_it;

		gcm_crypto_data_t* gcm_crypto_data = &file_mht_node->parent->plain.mht_nodes_crypto[(file_mht_node->mht_node_number - 1) % CHILD_MHT_NODES_COUNT];

		if (derive_random_node_key(file_mht_node->physical_node_number) == false)
		{
			mht_list.clear();
			return false;
		}

		status = sgx_rijndael128GCM_encrypt(&cur_key, (const uint8_t*)&file_mht_node->plain, NODE_SIZE, file_mht_node->encrypted.cipher, 
											empty_iv, SGX_AESGCM_IV_SIZE, NULL, 0, &gcm_crypto_data->gmac);
		if (status != SGX_SUCCESS)
		{
			mht_list.clear();
			last_error = status;
			return false;
		}

		memcpy(gcm_crypto_data->key, cur_key, sizeof(sgx_aes_gcm_128bit_key_t)); // save the key used for this gmac

		mht_list.pop_front();
	}

	// update mht root gmac in the meta data node
	if (derive_random_node_key(root_mht.physical_node_number) == false)
		return false;

	status = sgx_rijndael128GCM_encrypt(&cur_key, (const uint8_t*)&root_mht.plain, NODE_SIZE, root_mht.encrypted.cipher, 
										empty_iv, SGX_AESGCM_IV_SIZE, NULL, 0, &encrypted_part_plain.mht_gmac);
	if (status != SGX_SUCCESS)
	{
		last_error = status;
		return false;
	}

	memcpy(&encrypted_part_plain.mht_key, cur_key, sizeof(sgx_aes_gcm_128bit_key_t)); // save the key used for this gmac

	return true;
}


bool protected_fs_file::update_meta_data_node()
{
	sgx_status_t status;
	
	// randomize a new key, saves the key _id_ in the meta data plain part
	if (generate_random_meta_data_key() != true)
	{
		// last error already set
		return false;
	}
		
	// encrypt meta data encrypted part, also updates the gmac in the meta data plain part
	status = sgx_rijndael128GCM_encrypt(&cur_key, 
										(const uint8_t*)&encrypted_part_plain, sizeof(meta_data_encrypted_t), (uint8_t*)&file_meta_data.encrypted_part, 
										empty_iv, SGX_AESGCM_IV_SIZE, 
										NULL, 0, 
										&file_meta_data.plain_part.meta_data_gmac);
	if (status != SGX_SUCCESS)
	{
		last_error = status;
		return false;
	}

	return true;
}


bool protected_fs_file::write_all_changes_to_disk(bool flush_to_disk)
{
	uint8_t result;
	int32_t result32;
	sgx_status_t status;

	if (encrypted_part_plain.size > MD_USER_DATA_SIZE && root_mht.need_writing == true)
	{
		void* data = NULL;
		uint8_t* data_to_write;
		uint64_t node_number;
		file_data_node_t* file_data_node;
		file_mht_node_t* file_mht_node;

		for (data = cache.get_first() ; data != NULL ; data = cache.get_next())
		{
			file_data_node = NULL;
			file_mht_node = NULL;

			if (((file_data_node_t*)data)->type == FILE_DATA_NODE_TYPE) // type is in the same offset in both node types
			{
				file_data_node = (file_data_node_t*)data;
				if (file_data_node->need_writing == false)
					continue;

				data_to_write = (uint8_t*)&file_data_node->encrypted;
				node_number = file_data_node->physical_node_number;
			}
			else
			{
				file_mht_node = (file_mht_node_t*)data;
				assert(file_mht_node->type == FILE_MHT_NODE_TYPE);
				if (file_mht_node->need_writing == false)
					continue;

				data_to_write = (uint8_t*)&file_mht_node->encrypted;
				node_number = file_mht_node->physical_node_number;
			}

			status = u_sgxprotectedfs_fwrite_node(&result32, file, node_number, data_to_write, NODE_SIZE);
			if (status != SGX_SUCCESS || result32 != 0)
			{
				last_error = (status != SGX_SUCCESS) ? status : 
							 (result32 != -1) ? result32 : EIO;
				return false;
			}

			// data written - clear the need_writing and the new_node flags (for future transactions, this node it no longer 'new' and should be written to recovery file)
			if (file_data_node != NULL)
			{
				file_data_node->need_writing = false;
				file_data_node->new_node = false;
			}
			else
			{
				file_mht_node->need_writing = false;
				file_mht_node->new_node = false;
			}

		}

		status = u_sgxprotectedfs_fwrite_node(&result32, file, 1, (uint8_t*)&root_mht.encrypted, NODE_SIZE);
		if (status != SGX_SUCCESS || result32 != 0)
		{
			last_error = (status != SGX_SUCCESS) ? status : 
						 (result32 != -1) ? result32 : EIO;
			return false;
		}
		root_mht.need_writing = false;
		root_mht.new_node = false;
	}

	status = u_sgxprotectedfs_fwrite_node(&result32, file, 0, (uint8_t*)&file_meta_data, NODE_SIZE);
	if (status != SGX_SUCCESS || result32 != 0)
	{
		last_error = (status != SGX_SUCCESS) ? status : 
					 (result32 != -1) ? result32 : EIO;
		return false;
	}

	if (flush_to_disk == true)
	{
		status = u_sgxprotectedfs_fflush(&result, file);
		if (status != SGX_SUCCESS || result != 0)
		{
			last_error = status != SGX_SUCCESS ? status : SGX_ERROR_FILE_FLUSH_FAILED;
			return false;
		}
	}

	return true;
}


void protected_fs_file::erase_recovery_file()
{
	sgx_status_t status;
	int32_t result32;

	if (recovery_filename[0] == '\0') // not initialized yet
		return;

	status = u_sgxprotectedfs_remove(&result32, recovery_filename);
	(void)status; // don't care if it succeeded or failed...just remove the warning
}


