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

#include "sgx_tprotected_fs_t.h"
#include "protected_fs_file.h"

#include <sgx_trts.h>

size_t protected_fs_file::write(const void* ptr, size_t size, size_t count)
{
	if (ptr == NULL || size == 0 || count == 0)
		return 0;

	int32_t result32 = sgx_thread_mutex_lock(&mutex);
	if (result32 != 0)
	{
		last_error = result32;
		file_status = SGX_FILE_STATUS_MEMORY_CORRUPTED;
		return 0;
	}

	size_t data_left_to_write = size * count;

	// prevent overlap...
#if defined(_WIN64) || defined(__x86_64__)
	if (size > UINT32_MAX || count > UINT32_MAX)
	{
		last_error = EINVAL;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}
#else
	if (((uint64_t)((uint64_t)size * (uint64_t)count)) != (uint64_t)data_left_to_write)
	{
		last_error = EINVAL;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}
#endif

	if (sgx_is_outside_enclave(ptr, data_left_to_write))
	{
		last_error = SGX_ERROR_INVALID_PARAMETER;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}

	if (file_status != SGX_FILE_STATUS_OK)
	{
		last_error = SGX_ERROR_FILE_BAD_STATUS;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}

	if (open_mode.append == 0 && open_mode.update == 0 && open_mode.write == 0)
	{
		last_error = EACCES;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}

	if (open_mode.append == 1)
		offset = encrypted_part_plain.size; // add at the end of the file

	const unsigned char* data_to_write = (const unsigned char*)ptr;

	// the first block of user data is written in the meta-data encrypted part
	if (offset < MD_USER_DATA_SIZE)
	{
		size_t empty_place_left_in_md = MD_USER_DATA_SIZE - (size_t)offset; // offset is smaller than MD_USER_DATA_SIZE
		if (data_left_to_write <= empty_place_left_in_md)
		{
			memcpy(&encrypted_part_plain.data[offset], data_to_write, data_left_to_write);
			offset += data_left_to_write;
			data_to_write += data_left_to_write; // not needed, to prevent future errors
			data_left_to_write = 0;
		}
		else
		{
			memcpy(&encrypted_part_plain.data[offset], data_to_write, empty_place_left_in_md);
			offset += empty_place_left_in_md;
			data_to_write += empty_place_left_in_md;
			data_left_to_write -= empty_place_left_in_md;
		}
		
		if (offset > encrypted_part_plain.size)
			encrypted_part_plain.size = offset; // file grew, update the new file size

		need_writing = true;
	}

	while (data_left_to_write > 0)
	{
		file_data_node_t* file_data_node = NULL;
		file_data_node = get_data_node(); // return the data node of the current offset, will read it from disk or create new one if needed (and also the mht node if needed)
		if (file_data_node == NULL)
			break;

		size_t offset_in_node = (size_t)((offset - MD_USER_DATA_SIZE) % NODE_SIZE);
		size_t empty_place_left_in_node = NODE_SIZE - offset_in_node;
		
		if (data_left_to_write <= empty_place_left_in_node)
		{ // this will be the last write
			memcpy(&file_data_node->plain.data[offset_in_node], data_to_write, data_left_to_write);
			offset += data_left_to_write;
			data_to_write += data_left_to_write; // not needed, to prevent future errors
			data_left_to_write = 0;
		}
		else
		{
			memcpy(&file_data_node->plain.data[offset_in_node], data_to_write, empty_place_left_in_node);
			offset += empty_place_left_in_node;
			data_to_write += empty_place_left_in_node;
			data_left_to_write -= empty_place_left_in_node;

		}

		if (offset > encrypted_part_plain.size)
			encrypted_part_plain.size = offset; // file grew, update the new file size

		if (file_data_node->need_writing == false)
		{
			file_data_node->need_writing = true;
			file_mht_node_t* file_mht_node = file_data_node->parent;
			while (file_mht_node->mht_node_number != 0) // set all the mht parent nodes as 'need writing'
			{
				file_mht_node->need_writing = true;
				file_mht_node = file_mht_node->parent;
			}
			root_mht.need_writing = true;
			need_writing = true;
		}
	}

	sgx_thread_mutex_unlock(&mutex);

	size_t ret_count = ((size * count) - data_left_to_write) / size;
	return ret_count;
}


size_t protected_fs_file::read(void* ptr, size_t size, size_t count)
{
	if (ptr == NULL || size == 0 || count == 0)
		return 0;

	int32_t result32 = sgx_thread_mutex_lock(&mutex);
	if (result32 != 0)
	{
		last_error = result32;
		file_status = SGX_FILE_STATUS_MEMORY_CORRUPTED;
		return 0;
	}

	size_t data_left_to_read = size * count;

	// prevent overlap...
#if defined(_WIN64) || defined(__x86_64__)
	if (size > UINT32_MAX || count > UINT32_MAX)
	{
		last_error = EINVAL;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}
#else
	if (((uint64_t)((uint64_t)size * (uint64_t)count)) != (uint64_t)data_left_to_read)
	{
		last_error = EINVAL;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}
#endif


	if (sgx_is_outside_enclave(ptr, data_left_to_read))
	{
		last_error = EINVAL;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}

	if (file_status != SGX_FILE_STATUS_OK)
	{
		last_error = SGX_ERROR_FILE_BAD_STATUS;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}

	if (open_mode.read == 0 && open_mode.update == 0)
	{
		last_error = EACCES;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}

	if (end_of_file == true)
	{// not an error
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}

	// this check is not really needed, can go on with the code and it will do nothing until the end, but it's more 'right' to check it here
	if (offset == encrypted_part_plain.size)
	{
		end_of_file = true;
		sgx_thread_mutex_unlock(&mutex);
		return 0;
	}

	if (((uint64_t)data_left_to_read) > (uint64_t)(encrypted_part_plain.size - offset)) // the request is bigger than what's left in the file
	{
		data_left_to_read = (size_t)(encrypted_part_plain.size - offset);
	}
	size_t data_attempted_to_read = data_left_to_read; // used at the end to return how much we actually read

	unsigned char* out_buffer = (unsigned char*)ptr;

	// the first block of user data is read from the meta-data encrypted part
	if (offset < MD_USER_DATA_SIZE)
	{
		size_t data_left_in_md = MD_USER_DATA_SIZE - (size_t)offset; // offset is smaller than MD_USER_DATA_SIZE
		if (data_left_to_read <= data_left_in_md)
		{
			memcpy(out_buffer, &encrypted_part_plain.data[offset], data_left_to_read);
			offset += data_left_to_read;
			out_buffer += data_left_to_read; // not needed, to prevent future errors
			data_left_to_read = 0;
		}
		else
		{
			memcpy(out_buffer, &encrypted_part_plain.data[offset], data_left_in_md);
			offset += data_left_in_md;
			out_buffer += data_left_in_md;
			data_left_to_read -= data_left_in_md;
		}
	}

	while (data_left_to_read > 0)
	{
		file_data_node_t* file_data_node = NULL;
		file_data_node = get_data_node(); // return the data node of the current offset, will read it from disk if needed (and also the mht node if needed)
		if (file_data_node == NULL)
			break;

		size_t offset_in_node = (offset - MD_USER_DATA_SIZE) % NODE_SIZE;
		size_t data_left_in_node = NODE_SIZE - offset_in_node;
		
		if (data_left_to_read <= data_left_in_node)
		{
			memcpy(out_buffer, &file_data_node->plain.data[offset_in_node], data_left_to_read);
			offset += data_left_to_read;
			out_buffer += data_left_to_read; // not needed, to prevent future errors
			data_left_to_read = 0;
		}
		else
		{
			memcpy(out_buffer, &file_data_node->plain.data[offset_in_node], data_left_in_node);
			offset += data_left_in_node;
			out_buffer += data_left_in_node;
			data_left_to_read -= data_left_in_node;

		}
	}

	sgx_thread_mutex_unlock(&mutex);

	if (data_left_to_read == 0 &&
		data_attempted_to_read != (size * count)) // user wanted to read more and we had to shrink the request
	{
		assert(offset == encrypted_part_plain.size);
		end_of_file = true;
	}

	size_t ret_count = (data_attempted_to_read - data_left_to_read) / size;
	return ret_count;
}


// this is a very 'specific' function, tied to the architecture of the file layout, returning the node numbers according to the offset in the file 
void get_node_numbers(uint64_t offset, uint64_t* mht_node_number, uint64_t* data_node_number, 
					 uint64_t* physical_mht_node_number, uint64_t* physical_data_node_number)
{
	// node 0 - meta data node
	// node 1 - mht
	// nodes 2-97 - data (ATTACHED_DATA_NODES_COUNT == 96)
	// node 98 - mht
	// node 99-195 - data
	// etc.
	uint64_t _mht_node_number;
	uint64_t _data_node_number;
	uint64_t _physical_mht_node_number;
	uint64_t _physical_data_node_number;

	assert(offset >= MD_USER_DATA_SIZE);

	_data_node_number = (offset - MD_USER_DATA_SIZE) / NODE_SIZE;
	_mht_node_number = _data_node_number / ATTACHED_DATA_NODES_COUNT;
	_physical_data_node_number = _data_node_number
								+ 1 // meta data node
								+ 1 // mht root
								+ _mht_node_number; // number of mht nodes in the middle (the root mht mht_node_number is 0)
	_physical_mht_node_number = _physical_data_node_number
								- _data_node_number % ATTACHED_DATA_NODES_COUNT // now we are at the first data node attached to this mht node
								- 1; // and now at the mht node itself!

	if (mht_node_number != NULL) *mht_node_number = _mht_node_number;
	if (data_node_number != NULL) *data_node_number = _data_node_number;
	if (physical_mht_node_number != NULL) *physical_mht_node_number = _physical_mht_node_number;
	if (physical_data_node_number != NULL) *physical_data_node_number = _physical_data_node_number;
}


file_data_node_t* protected_fs_file::get_data_node()
{
	file_data_node_t* file_data_node = NULL;

	if (offset < MD_USER_DATA_SIZE)
	{
		last_error = SGX_ERROR_UNEXPECTED;
		return NULL;
	}

	if ((offset - MD_USER_DATA_SIZE) % NODE_SIZE == 0 && 
		offset == encrypted_part_plain.size)
	{// new node
		file_data_node = append_data_node();
	}
	else
	{// existing node
		file_data_node = read_data_node();
	}

	// bump all the parents mht to reside before the data node in the cache
	if (file_data_node != NULL)
	{
		file_mht_node_t* file_mht_node = file_data_node->parent;
		while (file_mht_node->mht_node_number != 0)
		{
			cache.get(file_mht_node->physical_node_number); // bump the mht node to the head of the lru
			file_mht_node = file_mht_node->parent;
		}
	}

	// even if we didn't get the required data_node, we might have read other nodes in the process
	while (cache.size() > MAX_PAGES_IN_CACHE)
	{
		void* data = cache.get_last();
		assert(data != NULL);
		// for production - 
		if (data == NULL)
		{
			last_error = SGX_ERROR_UNEXPECTED;
			return NULL;
		}
		if (((file_data_node_t*)data)->need_writing == false) // need_writing is in the same offset in both node types
		{
			cache.remove_last();

			// before deleting the memory, need to scrub the plain secrets
			if (((file_data_node_t*)data)->type == FILE_DATA_NODE_TYPE) // type is in the same offset in both node types
			{
				file_data_node_t* file_data_node1 = (file_data_node_t*)data;
				memset_s(&file_data_node1->plain, sizeof(data_node_t), 0, sizeof(data_node_t));
				delete file_data_node1;
			}
			else
			{
				file_mht_node_t* file_mht_node = (file_mht_node_t*)data;
				memset_s(&file_mht_node->plain, sizeof(mht_node_t), 0, sizeof(mht_node_t));
				delete file_mht_node;
			}
		}
		else
		{
			if (internal_flush(/*false,*/ false) == false) // error, can't flush cache, file status changed to error
			{
				assert(file_status != SGX_FILE_STATUS_OK);
				if (file_status == SGX_FILE_STATUS_OK)
					file_status = SGX_FILE_STATUS_FLUSH_ERROR; // for release set this anyway
				return NULL; // even if we got the data_node!
			}
		}
	}
	
	return file_data_node;
}


file_data_node_t* protected_fs_file::append_data_node()
{
	file_mht_node_t* file_mht_node = get_mht_node();
	if (file_mht_node == NULL) // some error happened
		return NULL;

	file_data_node_t* new_file_data_node = NULL;

	try {
		new_file_data_node = new file_data_node_t;
	}
	catch (std::bad_alloc e) {
		(void)e; // remove warning
		last_error = ENOMEM;
		return NULL;
	}
	memset(new_file_data_node, 0, sizeof(file_data_node_t));

	new_file_data_node->type = FILE_DATA_NODE_TYPE;
	new_file_data_node->new_node = true;
	new_file_data_node->parent = file_mht_node;
	get_node_numbers(offset, NULL, &new_file_data_node->data_node_number, NULL, &new_file_data_node->physical_node_number);

	if (cache.add(new_file_data_node->physical_node_number, new_file_data_node) == false)
	{
		delete new_file_data_node;
		last_error = ENOMEM;
		return NULL;
	}

	return new_file_data_node;
}


file_data_node_t* protected_fs_file::read_data_node()
{
	uint64_t data_node_number;
	uint64_t physical_node_number;
	file_mht_node_t* file_mht_node;
	int32_t result32;
	sgx_status_t status;

	get_node_numbers(offset, NULL, &data_node_number, NULL, &physical_node_number);

	file_data_node_t* file_data_node = (file_data_node_t*)cache.get(physical_node_number);
	if (file_data_node != NULL)
		return file_data_node;
	
	// need to read the data node from the disk

	file_mht_node = get_mht_node();
	if (file_mht_node == NULL) // some error happened
		return NULL;

	try {
		file_data_node = new file_data_node_t;
	}
	catch (std::bad_alloc e) {
		(void)e; // remove warning
		last_error = ENOMEM;
		return NULL;
	}
	memset(file_data_node, 0, sizeof(file_data_node_t));
	file_data_node->type = FILE_DATA_NODE_TYPE;
	file_data_node->data_node_number = data_node_number;
	file_data_node->physical_node_number = physical_node_number;
	file_data_node->parent = file_mht_node;
		
	status = u_sgxprotectedfs_fread_node(&result32, file, file_data_node->physical_node_number, file_data_node->encrypted.cipher, NODE_SIZE);
	if (status != SGX_SUCCESS || result32 != 0)
	{
		delete file_data_node;
		last_error = (status != SGX_SUCCESS) ? status : 
					 (result32 != -1) ? result32 : EIO;
		return NULL;
	}

	gcm_crypto_data_t* gcm_crypto_data = &file_data_node->parent->plain.data_nodes_crypto[file_data_node->data_node_number % ATTACHED_DATA_NODES_COUNT];

	// this function decrypt the data _and_ checks the integrity of the data against the gmac
	status = sgx_rijndael128GCM_decrypt(&gcm_crypto_data->key, file_data_node->encrypted.cipher, NODE_SIZE, file_data_node->plain.data, empty_iv, SGX_AESGCM_IV_SIZE, NULL, 0, &gcm_crypto_data->gmac);
	if (status != SGX_SUCCESS)
	{
		delete file_data_node;
		last_error = status;
		if (status == SGX_ERROR_MAC_MISMATCH)
		{
			file_status = SGX_FILE_STATUS_CORRUPTED;
		}
		return NULL;
	}
		
	if (cache.add(file_data_node->physical_node_number, file_data_node) == false)
	{
		memset_s(&file_data_node->plain, sizeof(data_node_t), 0, sizeof(data_node_t)); // scrub the plaintext data
		delete file_data_node;
		last_error = ENOMEM;
		return NULL;
	}

	return file_data_node;
}


file_mht_node_t* protected_fs_file::get_mht_node()
{
	file_mht_node_t* file_mht_node;
	uint64_t mht_node_number;
	uint64_t physical_mht_node_number;

	if (offset < MD_USER_DATA_SIZE)
	{
		last_error = SGX_ERROR_UNEXPECTED;
		return NULL;
	}

	get_node_numbers(offset, &mht_node_number, NULL, &physical_mht_node_number, NULL);

	if (mht_node_number == 0)
		return &root_mht;

	// file is constructed from 128*4KB = 512KB per MHT node.
	if ((offset - MD_USER_DATA_SIZE) % (ATTACHED_DATA_NODES_COUNT * NODE_SIZE) == 0 && 
		 offset == encrypted_part_plain.size)
	{
		file_mht_node = append_mht_node(mht_node_number);
	}
	else
	{
		file_mht_node = read_mht_node(mht_node_number);
	}

	return file_mht_node;
}


file_mht_node_t* protected_fs_file::append_mht_node(uint64_t mht_node_number)
{
	file_mht_node_t* parent_file_mht_node = read_mht_node((mht_node_number - 1) / CHILD_MHT_NODES_COUNT);
	if (parent_file_mht_node == NULL) // some error happened
		return NULL;

	uint64_t physical_node_number = 1 + // meta data node
										mht_node_number * (1 + ATTACHED_DATA_NODES_COUNT); // the '1' is for the mht node preceding every 96 data nodes

	file_mht_node_t* new_file_mht_node = NULL;
	try {
		new_file_mht_node = new file_mht_node_t;
	}
	catch (std::bad_alloc e) {
		(void)e; // remove warning
		last_error = ENOMEM;
		return NULL;
	}
	memset(new_file_mht_node, 0, sizeof(file_mht_node_t));

	new_file_mht_node->type = FILE_MHT_NODE_TYPE;
	new_file_mht_node->new_node = true;
	new_file_mht_node->parent = parent_file_mht_node;
	new_file_mht_node->mht_node_number = mht_node_number;
	new_file_mht_node->physical_node_number = physical_node_number;

	if (cache.add(new_file_mht_node->physical_node_number, new_file_mht_node) == false)
	{
		delete new_file_mht_node;
		last_error = ENOMEM;
		return NULL;
	}
	
	return new_file_mht_node;
}


file_mht_node_t* protected_fs_file::read_mht_node(uint64_t mht_node_number)
{
	int32_t result32;
	sgx_status_t status;

	if (mht_node_number == 0)
		return &root_mht;

	uint64_t physical_node_number = 1 + // meta data node
										mht_node_number * (1 + ATTACHED_DATA_NODES_COUNT); // the '1' is for the mht node preceding every 96 data nodes

	file_mht_node_t* file_mht_node = (file_mht_node_t*)cache.find(physical_node_number);
	if (file_mht_node != NULL)
		return file_mht_node;

	file_mht_node_t* parent_file_mht_node = read_mht_node((mht_node_number - 1) / CHILD_MHT_NODES_COUNT);
	if (parent_file_mht_node == NULL) // some error happened
		return NULL;

	try {
		file_mht_node = new file_mht_node_t;
	}
	catch (std::bad_alloc e) {
		(void)e; // remove warning
		last_error = ENOMEM;
		return NULL;
	}
	memset(file_mht_node, 0, sizeof(file_mht_node_t));
	file_mht_node->type = FILE_MHT_NODE_TYPE;
	file_mht_node->mht_node_number = mht_node_number;
	file_mht_node->physical_node_number = physical_node_number;
	file_mht_node->parent = parent_file_mht_node;
		
	status = u_sgxprotectedfs_fread_node(&result32, file, file_mht_node->physical_node_number, file_mht_node->encrypted.cipher, NODE_SIZE);
	if (status != SGX_SUCCESS || result32 != 0)
	{
		delete file_mht_node;
		last_error = (status != SGX_SUCCESS) ? status : 
					 (result32 != -1) ? result32 : EIO;
		return NULL;
	}
	
	gcm_crypto_data_t* gcm_crypto_data = &file_mht_node->parent->plain.mht_nodes_crypto[(file_mht_node->mht_node_number - 1) % CHILD_MHT_NODES_COUNT];

	// this function decrypt the data _and_ checks the integrity of the data against the gmac
	status = sgx_rijndael128GCM_decrypt(&gcm_crypto_data->key, file_mht_node->encrypted.cipher, NODE_SIZE, (uint8_t*)&file_mht_node->plain, empty_iv, SGX_AESGCM_IV_SIZE, NULL, 0, &gcm_crypto_data->gmac);
	if (status != SGX_SUCCESS)
	{
		delete file_mht_node;
		last_error = status;
		if (status == SGX_ERROR_MAC_MISMATCH)
		{
			file_status = SGX_FILE_STATUS_CORRUPTED;
		}
		return NULL;
	}

	if (cache.add(file_mht_node->physical_node_number, file_mht_node) == false)
	{
		memset_s(&file_mht_node->plain, sizeof(mht_node_t), 0, sizeof(mht_node_t));
		delete file_mht_node;
		last_error = ENOMEM;
		return NULL;
	}

	return file_mht_node;
}

