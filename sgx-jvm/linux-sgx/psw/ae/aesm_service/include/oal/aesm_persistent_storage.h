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


#ifndef _AESM_PERSISTENT_STORAGE_H_
#define _AESM_PERSISTENT_STORAGE_H_
#include "aeerror.h"
#include "se_types.h"

 /**
  * File: aesm_persistent_storage.h
  * Description: Definition for interface of persistent storage used in AESM
  *   Some types and functions are declared
  */

typedef enum _aesm_data_type_t{
    FT_ENCLAVE_NAME,
    FT_PERSISTENT_STORAGE /*putting all files but enclave into persistent storage to simplify the interface*/
} aesm_data_type_t;

/*please refer to persistent_storage_table.cpp for more detail information about persistent storages*/
typedef enum _aesm_data_id_t{
    /*first encalve files*/
    LE_ENCLAVE_FID,
    QE_ENCLAVE_FID,
    PVE_ENCLAVE_FID,
    PSE_OP_ENCLAVE_FID,
    PSE_PR_ENCLAVE_FID,
    PCE_ENCLAVE_FID,
    LE_PROD_SIG_STRUCT_FID,
    /*some normal persistent storages*/
    EXTENDED_EPID_GROUP_ID_FID,
    EXTENDED_EPID_GROUP_BLOB_INFO_FID,
    PROVISION_PEK_BLOB_FID,
    EPID_DATA_BLOB_FID,
    AESM_SERVER_URL_FID,
    /*some special files where pathname could be used directly inside AESM*/
    VMC_DATABASE_FID,
    VMC_DATABASE_BK_FID,
    VMC_DATABASE_PREBUILD_FID,
    PSDA_FID,
    NETWORK_SETTING_FID,
#ifdef DBG_LOG
    AESM_DBG_LOG_FID,
    AESM_DBG_LOG_CFG_FID,
#endif
#ifdef _PROFILE_
    AESM_PERF_DATA_FID,
#endif
    AESM_WHITE_LIST_CERT_FID,
    AESM_WHITE_LIST_CERT_TO_BE_VERIFY_FID,
    PSE_PR_OCSPRESP_FID,
    PSE_PR_LT_PAIRING_FID,
    PSE_PR_CERTIFICATE_CHAIN_FID,
    PSE_PR_CERTIFICATE_FID,
    PSE_PR_CERTIFICATE_FID2,
    PSE_PR_CERTIFICATE_FID3,
    PSE_PR_CERTIFICATE_FID4,
    PSE_PR_CERTIFICATE_FID5,
    PSE_PR_CERTIFICATE_FID6,
    PSE_PR_CERTIFICATE_FID_MAX,
    PSE_PR_FULLNAME_FID,

    NUMBER_OF_FIDS
} aesm_data_id_t;

aesm_data_id_t operator++(aesm_data_id_t& id, int);

typedef aesm_data_id_t aesm_enclave_id_t;
#define DEFAULT_EGID 0
#define INVALID_EGID 0xFFFFFFFF
/*Function to get pathname of a file object such as vmc database
 *@type: input for the type of the storage
 *@data_id: id of persistent storage
 *@buf: start address  of the buffer to receive the zero terminated path file name of the data
 *@buf_size: size in char of the buffer 'buf'
 *@xgid: extended epid group id associated with the file if the file location info is AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *       the xgid must be INVALID_EGID if the file location info is not AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *@return AESM_SUCCESS on success or error code if failed
 */
ae_error_t aesm_get_pathname(aesm_data_type_t type, aesm_data_id_t data_id, char *buf, uint32_t buf_size, uint32_t xgid = INVALID_EGID);
ae_error_t aesm_get_cpathname(aesm_data_type_t type, aesm_data_id_t data_id, char *buf, uint32_t buf_size, uint32_t xgid = INVALID_EGID);

/*Function to query size of data in persistent storage
 *@type: input for the type of storage
 *@data_id: id of persistent storage
 *@p_size: output parameter to return size of the data blob
 *@xgid: extended epid group id associated with the file if the file location info is AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *       the xgid must be INVALID_EGID if the file location info is not AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *@return AESM_SUCCESS on success or error code if failed
 */
ae_error_t aesm_query_data_size(aesm_data_type_t type, aesm_data_id_t data_id, uint32_t *p_size, uint32_t xgid = INVALID_EGID);

/*Function to read data from persistent storage
 *@type: input type of the storage
 *@data_id: id of persistent storage
 *@buf: start  address of the buffer to receive data from persistent storage
 *@p_size: the input value *p_size is size of the buffer and output the size in bytes of data read
 *@xgid: extended epid group id associated with the file if the file location info is AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *       the xgid must be INVALID_EGID if the file location info is not AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *@return AESM_SUCCESS on success or error code if failed
 *  The functin will not check whether there're too much data in the persistent storage to be read
 */
ae_error_t aesm_read_data(aesm_data_type_t type, aesm_data_id_t data_id, uint8_t *buf, uint32_t *p_size, uint32_t xgid = INVALID_EGID);

/*Function to write data tp persistent storage
 *@type: input type of the storage
 *@data_id: id of persistent storage
 *@buf: start  address of the buffer where the data is to be saved to persistent storage
 *@size: size in bytes of the ti be saved
 *@xgid: extended epid group id associated with the file if the file location info is AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *       the xgid must be INVALID_EGID if the file location info is not AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *@return AESM_SUCCESS on success or error code if failed
 *  The functin will not check whether there're too much data in the persistent storage to be read
 */
ae_error_t aesm_write_data(aesm_data_type_t type, aesm_data_id_t data_id, const uint8_t *buf, uint32_t size, uint32_t xgid = INVALID_EGID);

/*Function to remove data persistent storage
 *@type: input type of the storage
 *@data_id: id of persistent storage
 *@xgid: extended epid group id associated with the file if the file location info is AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *       the xgid must be INVALID_EGID if the file location info is not AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA
 *@return AESM_SUCCESS on success or error code if failed 
 */
ae_error_t aesm_remove_data(aesm_data_type_t type, aesm_data_id_t data_id, uint32_t xgid = INVALID_EGID);
#endif

