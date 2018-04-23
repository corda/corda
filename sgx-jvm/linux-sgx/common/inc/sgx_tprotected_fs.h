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


/**
* File: sgx_tprotected_fs.h
* Description:
*     Interface for file API
*/

#pragma once

#ifndef _SGX_TPROTECTED_FS_H_
#define _SGX_TPROTECTED_FS_H_

#include <stddef.h>

#include "sgx_defs.h"
#include "sgx_key.h"

#define SGX_FILE void

#define EOF        (-1)

#define SEEK_SET    0
#define SEEK_CUR    1
#define SEEK_END    2

#define FILENAME_MAX  260
#define FOPEN_MAX     20

#ifdef __cplusplus
extern "C" {
#endif

/* sgx_fopen
 *  Purpose: open existing protected file (created with previous call to sgc_fopen) or create a new one (see c++ fopen documentation for more details).
 *
 *  Parameters:
 *      filename - [IN] the name of the file to open/create.
 *      mode - [IN] open mode. only supports 'r' or 'w' or 'a' (one and only one of them must be present), and optionally 'b' and/or '+'.
 *      key - [IN] encryption key that will be used for the file encryption
 *      NOTE - the key is actually used as a KDK (key derivation key) and only for the meta-data node, and not used directly for the encryption of any part of the file
 *             this is important in order to prevent hitting the key wear-out problem, and some other issues with GCM encryptions using the same key
 *
 *  Return value:
 *     SGX_FILE*  - pointer to the newly created file handle, NULL if an error occurred - check errno for the error code.
*/
SGX_FILE* SGXAPI sgx_fopen(const char* filename, const char* mode, const sgx_key_128bit_t *key);


/* sgx_fopen_auto_key
*  Purpose: open existing protected file (created with previous call to sgc_fopen_auto_key) or create a new one (see c++ fopen documentation for more details).
*           this API doesn't require a key from the user, and instead uses the enclave's SEAL key as a KDK for deriving the mete-data encryption keys
*           using the SEAL key as a KDK, may result losing file access in cases of disaster-recovery or in cases of VM migration in servers
*           users that any of these scenarious apply to them, are advised to use sgx_fopen, where they can provide their own key and manage the keys provisioning for those scenarious
*           for further information, please read the SGX SDK manual
*
*  Parameters:
*      filename - [IN] the name of the file to open/create.
*      mode - [IN] open mode. only supports 'r' or 'w' or 'a' (one and only one of them must be present), and optionally 'b' and/or '+'.
*
*  Return value:
*     SGX_FILE*  - pointer to the newly created file handle, NULL if an error occurred - check errno for the error code.
*/
SGX_FILE* SGXAPI sgx_fopen_auto_key(const char* filename, const char* mode);


/* sgx_fwrite
 *  Purpose: write data to a file (see c++ fwrite documentation for more details).
 *
 *  Parameters:
 *      ptr - [IN] pointer to the input data buffer
 *      size - [IN] size of data block
 *      count - [IN] count of data blocks to write
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *
 *  Return value:
 *     size_t  - number of 'size' blocks written to the file, 0 in case of an error - check sgx_ferror for error code
*/
size_t SGXAPI sgx_fwrite(const void* ptr, size_t size, size_t count, SGX_FILE* stream);


/* sgx_fread
 *  Purpose: read data from a file (see c++ fread documentation for more details).
 *
 *  Parameters:
 *      ptr - [OUT] pointer to the output data buffer
 *      size - [IN] size of data block
 *      count - [IN] count of data blocks to write
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *
 *  Return value:
 *     size_t  - number of 'size' blocks read from the file, 0 in case of an error - check sgx_ferror for error code
*/
size_t SGXAPI sgx_fread(void* ptr, size_t size, size_t count, SGX_FILE* stream);


/* sgx_ftell
 *  Purpose: get the current value of the position indicator of the file (see c++ ftell documentation for more details).
 *
 *  Parameters:
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *
 *  Return value:
 *     int64_t  - the current value of the position indicator, -1 on error - check errno for the error code
*/
int64_t SGXAPI sgx_ftell(SGX_FILE* stream);


/* sgx_fseek
 *  Purpose: set the current value of the position indicator of the file (see c++ fseek documentation for more details).
 *
 *  Parameters:
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *      offset - [IN] the new required value, relative to the origin parameter
 *      origin - [IN] the origin from which to calculate the offset (SEEK_SET, SEEK_CUR or SEEK_END)
 *
 *  Return value:
 *     int32_t  - result, 0 on success, -1 in case of an error - check sgx_ferror for error code
*/
int32_t SGXAPI sgx_fseek(SGX_FILE* stream, int64_t offset, int origin);


/* sgx_fflush
 *  Purpose: force actual write of all the cached data to the disk (see c++ fflush documentation for more details).
 *
 *  Parameters:
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *
 *  Return value:
 *     int32_t  - result, 0 on success, 1 in case of an error - check sgx_ferror for error code
*/
int32_t SGXAPI sgx_fflush(SGX_FILE* stream);


/* sgx_ferror
 *  Purpose: get the latest operation error code (see c++ ferror documentation for more details).
 *
 *  Parameters:
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *
 *  Return value:
 *     int32_t  - the error code, 0 means no error, anything else is the latest operation error code
*/
int32_t SGXAPI sgx_ferror(SGX_FILE* stream);


/* sgx_feof
 *  Purpose: did the file's position indicator hit the end of the file in a previous read operation (see c++ feof documentation for more details).
 *
 *  Parameters:
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *
 *  Return value:
 *     int32_t  - 1 - end of file was reached, 0 - end of file wasn't reached
*/
int32_t SGXAPI sgx_feof(SGX_FILE* stream);


/* sgx_clearerr
 *  Purpose: try to clear an error in the file status, also clears the end-of-file flag (see c++ clearerr documentation for more details).
 *           call sgx_ferror or sgx_feof after a call to this function to learn if it was successful or not
 *
 *  Parameters:
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *
 *  Return value:
 *      none
*/
void SGXAPI sgx_clearerr(SGX_FILE* stream);


/* sgx_fclose
 *  Purpose: close an open file handle (see c++ fclose documentation for more details).
 *           after a call to this function, the handle is invalid even if an error is returned
 *
 *  Parameters:
 *      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key)
 *
 *  Return value:
 *     int32_t  - result, 0 - file was closed successfully, 1 - there were errors during the operation
*/
int32_t SGXAPI sgx_fclose(SGX_FILE* stream);


/* sgx_remove
 *  Purpose: delete a file from the file system (see c++ remove documentation for more details).
 *
 *  Parameters:
 *      filename - [IN] the name of the file to remvoe.
 *
 *  Return value:
 *     int32_t  - result, 0 - success, 1 - there was an error, check errno for the error code
*/
int32_t SGXAPI sgx_remove(const char* filename);


/* sgx_fexport_auto_key
*  Purpose: export the last key that was used in the encryption of the file.
*           with this key we can import the file in a different enclave/system
*           NOTE - 1. in order for this function to work, the file should not be opened in any other process
*                  2. this function only works with files created with sgx_fopen_auto_key
*
*  Parameters:
*      filename - [IN] the name of the file to export the encryption key from.
*      key - [OUT] the exported encryption key
*
*  Return value:
*     int32_t  - result, 0 - success, 1 - there was an error, check errno for the error code
*/
int32_t SGXAPI sgx_fexport_auto_key(const char* filename, sgx_key_128bit_t *key);


/* sgx_fimport_auto_key
*  Purpose: import a file that was created in a different enclave/system and make it a local file
*           after this call return successfully, the file can be opened with sgx_fopen_auto_key
*           NOTE - this function only works with files created with sgx_fopen_auto_key
*
*  Parameters:
*      filename - [IN] the name of the file to be imported.
*      key - [IN] the encryption key, exported with a call to sgx_fexport_auto_key in the source enclave/system
*
*  Return value:
*     int32_t  - result, 0 - success, 1 - there was an error, check errno for the error code
*/
int32_t SGXAPI sgx_fimport_auto_key(const char* filename, const sgx_key_128bit_t *key);


/* sgx_fclear_cache
*  Purpose: scrubs and delete the internal cache used by the file, any changed data is flushed to disk before deletion
*           Note - only the secrets that were in the cache are deleted, the file structure itself still holds keys and plain file data
*                  if a user wishes to remove all secrets from memory, he should close the file handle with sgx_fclose
*
*  Parameters:
*      stream - [IN] the file handle (opened with sgx_fopen or sgx_fopen_auto_key
*
*  Return value:
*     int32_t  - result, 0 - success, 1 - there was an error, check errno for the error code
*/
int32_t SGXAPI sgx_fclear_cache(SGX_FILE* stream);


#ifdef __cplusplus
}
#endif

#endif // _SGX_TPROTECTED_FS_H_
