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

//#include "memset_s.h"
#include "persistent_storage_info.h"
#include "oal/aesm_persistent_storage.h"
#include "internal/se_stdio.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>
#include <dlfcn.h>


static ae_error_t aesm_get_path(
    const char *p_file_name,
    char *p_file_path,
    size_t buf_size)
{
    if(!p_file_name || !p_file_path)
        return OAL_PARAMETER_ERROR;

    Dl_info dl_info;
    if(0==dladdr(__builtin_return_address(0), &dl_info)||
        NULL==dl_info.dli_fname)
        return AE_FAILURE;
    if(strnlen(dl_info.dli_fname,buf_size)>=buf_size)
        return OAL_PATHNAME_BUFFER_OVERFLOW_ERROR;
    (void)strncpy(p_file_path,dl_info.dli_fname,buf_size);
    char* p_last_slash = strrchr(p_file_path, '/' );
    if ( p_last_slash != NULL )
    {
        p_last_slash++;   //increment beyond the last slash
        *p_last_slash = '\0';  //null terminate the string
    }
    else p_file_path[0] = '\0';
    if(strnlen(p_file_path,buf_size)+strnlen(p_file_name,buf_size)+sizeof(char)>buf_size)
        return OAL_PATHNAME_BUFFER_OVERFLOW_ERROR;
    (void)strncat(p_file_path,p_file_name, strnlen(p_file_name,buf_size));
    return AE_SUCCESS;
}

#define AESM_DATA_FOLDER "/var/opt/aesmd/data/"
static ae_error_t aesm_get_data_path(
    const char *p_file_name,
    char *p_file_path,
    size_t buf_size)
{
    if(!p_file_name || !p_file_path)
        return OAL_PARAMETER_ERROR;

    if(strlen(AESM_DATA_FOLDER)+strnlen(p_file_name,buf_size)+sizeof(char)>buf_size)
        return OAL_PATHNAME_BUFFER_OVERFLOW_ERROR;
    (void)strcpy(p_file_path, AESM_DATA_FOLDER);
    (void)strncat(p_file_path,p_file_name, strnlen(p_file_name,buf_size));
    return AE_SUCCESS;
}

static ae_error_t aesm_write_file(
    const uint8_t *p_buf,
    uint32_t buf_size,
    char *p_file_name,
    bool is_full_path)
{
    ae_error_t ret = AE_FAILURE;
    uint32_t write_length = 0;
    FILE* p_file = NULL;
    char p_full_path[MAX_PATH]= {0};
    if(is_full_path){
        if(strnlen(p_file_name,MAX_PATH)>=MAX_PATH){
            ret = OAL_PATHNAME_BUFFER_OVERFLOW_ERROR;
            goto CLEANUP;
        }
        (void)strcpy(p_full_path, p_file_name);
    }else{
        if((ret=aesm_get_data_path(p_file_name, p_full_path, MAX_PATH)) != AE_SUCCESS)
            goto CLEANUP;
    }
    if(NULL == (p_file = fopen(p_full_path, "wb"))){
        ret = OAL_FILE_ACCESS_ERROR;
        goto CLEANUP;
    }

    write_length = (uint32_t)fwrite(p_buf, 1, buf_size, p_file);
    if(buf_size != write_length){
        ret = OAL_FILE_ACCESS_ERROR;
        goto CLEANUP;
    }
    ret = AE_SUCCESS;

CLEANUP:
    if(p_file)
        fclose(p_file);
    return ret;
}

static ae_error_t aesm_read_file(
    uint8_t *p_buf,
    uint32_t& buf_size,
    char *p_file_name,
    bool is_full_path)
{
    ae_error_t ret = AE_FAILURE;
    FILE* p_file = NULL;
    char p_full_path[MAX_PATH]= {0};
    if(is_full_path){
        if(strnlen(p_file_name,MAX_PATH)>=MAX_PATH){
            ret = OAL_PATHNAME_BUFFER_OVERFLOW_ERROR;
            goto CLEANUP;
        }
        (void)strcpy(p_full_path, p_file_name);
    }else{
        if((ret=aesm_get_data_path(p_file_name, p_full_path, MAX_PATH)) != AE_SUCCESS)
            goto CLEANUP;
    }
    if(NULL == (p_file = fopen(p_full_path, "rb"))){
        ret = OAL_FILE_ACCESS_ERROR;
        goto CLEANUP;
    }

    buf_size = (uint32_t)fread(p_buf, 1, buf_size, p_file);
    ret = AE_SUCCESS;

CLEANUP:
    if(p_file)
        fclose(p_file);
    return ret;
}

static ae_error_t aesm_remove_file(
    const char *p_file_name,
    bool is_full_path)
{
    ae_error_t ae_err = AE_FAILURE;
    char p_full_path[MAX_PATH] = { 0 };
    if (is_full_path){
        if(strnlen(p_file_name,MAX_PATH)>=MAX_PATH){
            ae_err = OAL_PATHNAME_BUFFER_OVERFLOW_ERROR;
            goto CLEANUP;
        }
        (void)strcpy(p_full_path, p_file_name);
    }
    else{
        if ((ae_err = aesm_get_data_path(p_file_name, p_full_path, MAX_PATH)) != AE_SUCCESS)
            goto CLEANUP;
    }
    if (remove(p_full_path)){
        if (errno == ENOENT)
            ae_err = AE_SUCCESS;
        else
            ae_err = OAL_FILE_ACCESS_ERROR;
        goto CLEANUP;
    }
    ae_err = AE_SUCCESS;

CLEANUP:
    return ae_err;
}

#define UPBOUND_OF_FORMAT 40
ae_error_t aesm_get_pathname(aesm_data_type_t type, aesm_data_id_t id, char *buf, uint32_t buf_size, uint32_t xgid)
{
    const persistent_storage_info_t *info = get_persistent_storage_info(id);
    int num_bytes = 0;
    if(info == NULL)
        return OAL_PARAMETER_ERROR;
    if(info->type != type)
        return OAL_PARAMETER_ERROR;
    if(info->type == FT_ENCLAVE_NAME){
        char local_info_name[MAX_PATH];
        if (xgid != INVALID_EGID){
            return AE_FAILURE;
        }
        if(strnlen(info->name, MAX_PATH)>=MAX_PATH-UPBOUND_OF_FORMAT){
            return AE_FAILURE;//info->name is a constant string and the length of it should not be too long so that the defense in depth codition here should never be triggered.
        }
        num_bytes = snprintf(local_info_name,MAX_PATH, "libsgx_%s.signed.so",info->name);
        if(num_bytes<0||num_bytes>=MAX_PATH){
            return AE_FAILURE;
        }
        return aesm_get_path(local_info_name, buf, buf_size);
    }else if(info->loc == AESM_LOCATION_DATA){
        if (xgid != INVALID_EGID){
            return AE_FAILURE;
        }
        return aesm_get_data_path(info->name, buf, buf_size);
    }
    else if (info->loc == AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA){
        char name[MAX_PATH];
        ae_error_t ae_err;
        if (xgid == INVALID_EGID){//INVALID_EGID should not be used for file to support multi extended_epid_group
            return AE_FAILURE;
        }
        if(strnlen(info->name,MAX_PATH)>=MAX_PATH-UPBOUND_OF_FORMAT){
            return AE_FAILURE;//defense in depth. info->name is a constant string and its size should be small
        }
        if ((num_bytes=snprintf(name, MAX_PATH, "%s.%08X", info->name, xgid)) < 0|| num_bytes>=MAX_PATH){
            return AE_FAILURE;
        }
        if ((ae_err = aesm_get_data_path(name, buf, buf_size)) != AE_SUCCESS)
            return ae_err;
        return AE_SUCCESS;
    }else{//info->loc == AESM_LOCATION_EXE_FOLDER
        if (xgid != INVALID_EGID){
            return AE_FAILURE;
        }
        return aesm_get_path(info->name, buf, buf_size);
    }
}

//alias function for aesm_get_pathname
ae_error_t aesm_get_cpathname(aesm_data_type_t type, aesm_data_id_t id, char *buf, uint32_t buf_size, uint32_t xgid)
{
    return aesm_get_pathname(type, id, buf, buf_size, xgid);
}

ae_error_t aesm_query_data_size(aesm_data_type_t type, aesm_data_id_t data_id, uint32_t *p_size, uint32_t xgid)
{
    char pathname[MAX_PATH];
    ae_error_t ret = AE_SUCCESS;

    const persistent_storage_info_t *info = get_persistent_storage_info(data_id);
    if(info == NULL)
        return OAL_PARAMETER_ERROR;
    if(info->access == AESM_FILE_ACCESS_PATH_ONLY)
        return OAL_PARAMETER_ERROR;

    ret = aesm_get_pathname(type, data_id, pathname, MAX_PATH, xgid);//currently all in file
    if(ret != AE_SUCCESS)
        return ret;

    struct stat stat_info;

    if(stat(pathname, &stat_info)!=0){//Maybe file has not been created
        *p_size = 0;
        return AE_SUCCESS;
    }
    *p_size = (uint32_t)stat_info.st_size;
    return AE_SUCCESS;
}

ae_error_t aesm_read_data(aesm_data_type_t type, aesm_data_id_t data_id, uint8_t *buf, uint32_t *p_size, uint32_t xgid)
{
    char pathname[MAX_PATH];
    ae_error_t ret = AE_SUCCESS;
    const persistent_storage_info_t *info = get_persistent_storage_info(data_id);
    if(info == NULL)
        return OAL_PARAMETER_ERROR;
    if(info->access == AESM_FILE_ACCESS_PATH_ONLY)
        return OAL_PARAMETER_ERROR;
    ret = aesm_get_pathname(type, data_id, pathname, MAX_PATH, xgid);//currently all in file
    if(ret != AE_SUCCESS)
        return ret;

    if((ret=aesm_read_file(buf, *p_size, pathname, true))!=AE_SUCCESS)
        return ret;
    return AE_SUCCESS;
}

ae_error_t aesm_write_data(aesm_data_type_t type, aesm_data_id_t data_id, const uint8_t *buf, uint32_t size, uint32_t xgid)
{
    char pathname[MAX_PATH];
    ae_error_t ret = AE_SUCCESS;

    const persistent_storage_info_t *info = get_persistent_storage_info(data_id);
    if(info == NULL)
        return OAL_PARAMETER_ERROR;
    if(info->access != AESM_FILE_ACCESS_ALL)
        return OAL_PARAMETER_ERROR;

    ret = aesm_get_pathname(type, data_id, pathname, MAX_PATH, xgid);//currently all in file
    if(ret != AE_SUCCESS)
        return ret;
    if((ret=aesm_write_file(buf, size, pathname, true))!=AE_SUCCESS)
        return ret;
    return AE_SUCCESS;
}

ae_error_t aesm_remove_data(aesm_data_type_t type, aesm_data_id_t data_id, uint32_t xgid)
{
    char pathname[MAX_PATH];
    ae_error_t ret = AE_SUCCESS;

    const persistent_storage_info_t *info = get_persistent_storage_info(data_id);
    if (info == NULL)
        return OAL_PARAMETER_ERROR;
    if (info->access != AESM_FILE_ACCESS_ALL)
        return OAL_PARAMETER_ERROR;

    ret = aesm_get_pathname(type, data_id, pathname, MAX_PATH, xgid);//currently all in file
    if (ret != AE_SUCCESS){
        return ret;
    }
    if ((ret = aesm_remove_file(pathname, true)) != AE_SUCCESS)
        return ret;
    return AE_SUCCESS;
}
