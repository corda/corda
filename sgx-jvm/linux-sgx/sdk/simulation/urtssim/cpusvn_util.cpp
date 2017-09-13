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


#include "cpusvn_util.h"
#include "se_wrapper.h"
#include "sgx_error.h"
#include "rts_sim.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>
#include <assert.h>
#include <errno.h>

#define MAX_FILE_PATH 260

extern "C" bool get_file_path(char *config_path, uint32_t length)
{  
    if(config_path == NULL || length == 0)
    {
    	return false;
    }

    char *local_path = getenv(ENV_PAR);
    if(local_path == NULL)
        return false;
    snprintf(config_path, length, "%s%s", local_path, FILE_NAME);

   return true;
}


extern "C" bool write_cpusvn_file(const char *file_path, const sgx_cpu_svn_t *cpusvn)
{
    if(file_path == NULL || cpusvn == NULL)
    {
        return false;
    }
    FILE *fp = NULL;
    if(NULL == (fp = fopen(file_path, "wb")))
    {
        return false;
    }
    if(fwrite(cpusvn, 1, sizeof(sgx_cpu_svn_t), fp) != sizeof(sgx_cpu_svn_t))
    {
        fclose(fp);
        return false;
    }
    fclose(fp);
    return true;
}


extern "C" bool read_cpusvn_file(const char *config_path, sgx_cpu_svn_t *cpusvn_ptr)
{
    if(config_path == NULL || cpusvn_ptr == NULL)
        return false;

    FILE *fp = NULL;
    long fsize = 0;
    sgx_cpu_svn_t temp_cpusvn = {{0}};
    size_t result = 0;

    if(NULL == (fp = fopen(config_path, "rb")))
    {
        SE_TRACE(SE_TRACE_DEBUG, "Couldn't find/open the configuration file %s.\n", config_path);
        memcpy_s(cpusvn_ptr, sizeof(sgx_cpu_svn_t), &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN));
        return true;
    }

    //check and read configure file format
    if(fseek(fp, 0, SEEK_END))
    {
        memcpy_s(&temp_cpusvn, sizeof(temp_cpusvn), &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN));
        goto clean_return;
    }

    fsize = ftell(fp);
    rewind(fp);

    if(fsize != sizeof(sgx_cpu_svn_t))
    {
        SE_TRACE(SE_TRACE_DEBUG, "The configuration file format is not correct. Using default CPUSVN value.\n");
        memcpy_s(&temp_cpusvn, sizeof(temp_cpusvn), &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN));
        goto clean_return;
    }

    result = fread(&temp_cpusvn, 1, fsize, fp);
    if(result != (size_t)fsize)
    {
        SE_TRACE(SE_TRACE_DEBUG, "Failed to read configuration file. Using default CPUSVN value.\n");
        memcpy_s(&temp_cpusvn, sizeof(temp_cpusvn), &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN));
        goto clean_return;
    }

    if(memcmp(&temp_cpusvn, &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN)) &&
        memcmp(&temp_cpusvn, &UPGRADED_CPUSVN, sizeof(UPGRADED_CPUSVN)) &&
        memcmp(&temp_cpusvn, &DOWNGRADED_CPUSVN, sizeof(DOWNGRADED_CPUSVN)))
    {
        SE_TRACE(SE_TRACE_DEBUG, "The configuration file format is not correct. Using default CPUSVN value.\n");
        memcpy_s(&temp_cpusvn, sizeof(temp_cpusvn), &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN));
        goto clean_return;
    }

clean_return:
    fclose(fp);
    memcpy_s(cpusvn_ptr, sizeof(*cpusvn_ptr), &temp_cpusvn, sizeof(temp_cpusvn));
    return true;
 }


extern "C" int get_cpusvn(sgx_cpu_svn_t *cpu_svn)
{
    if( cpu_svn == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    sgx_cpu_svn_t temp_cpusvn = {{0}};

    char config_path[MAX_FILE_PATH];
    memset(config_path, 0, MAX_FILE_PATH);

    if((get_file_path(config_path, MAX_FILE_PATH)) == false)
    {
        SE_TRACE(SE_TRACE_DEBUG, "Get configuration file path failed. Using default CPUSVN value\n");
        memcpy_s(cpu_svn, sizeof(*cpu_svn), &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN));
        return SGX_SUCCESS;
    }

    bool r = read_cpusvn_file(config_path, &temp_cpusvn);
    (void)r, assert (r);

    memcpy_s(cpu_svn, sizeof(*cpu_svn), &temp_cpusvn, sizeof(temp_cpusvn));

    return SGX_SUCCESS;
}

