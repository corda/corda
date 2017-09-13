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

#ifndef _PERSISTENT_STORAGE_INFO_H_
#define _PERSISTENT_STORAGE_INFO_H_
#include "oal/aesm_persistent_storage.h"

typedef enum _aesm_location_info_t {AESM_LOCATION_EXE_FOLDER, AESM_LOCATION_DATA, AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA} aesm_location_info_t;
typedef enum _aesm_file_access_type_t {
    AESM_FILE_ACCESS_PATH_ONLY, /*We will only get pathname of the file obj via oal interface but AESM could still access the file via other APIs*/
    AESM_FILE_ACCESS_READ_ONLY, /*Only read the data*/
    AESM_FILE_ACCESS_ALL        /*read and write*/
} aesm_file_access_type_t;

/*The table defines detail information about the persistent storages*/
typedef struct _persistent_storage_info_t{
    aesm_data_type_t type;
    aesm_location_info_t loc;
    aesm_file_access_type_t access;
    const char *name;
}persistent_storage_info_t;

const persistent_storage_info_t* get_persistent_storage_info(aesm_data_id_t id);
#endif

