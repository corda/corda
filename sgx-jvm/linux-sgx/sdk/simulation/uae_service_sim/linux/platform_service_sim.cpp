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
#include "../uae_service_sim.h"

#define SE_DATA_FOLDER1 "intel/"
#define SE_DATA_FOLDER2 "intelsgxpsw/"
#define MAX_PATH 260
#include <sys/stat.h>
#include <sys/types.h>
#include <stdlib.h>
#include <pwd.h>
#define __STDC_FORMAT_MACROS
#include <inttypes.h>

static Mutex g_pse_sim_lock;

static char g_vmc_base_path[] = "/var/tmp/";


sgx_status_t get_counter_id(vmc_sim_t *p_vmc_sim)
{
	uint32_t i = 0;
	int random_int = 0;
	unsigned int a, d;
	asm volatile("rdtsc" : "=a" (a), "=d" (d));
	for(i = 0; i < sizeof(p_vmc_sim->counter_id); i++, random_int >>= 8)
	{
		if(!(i % 4))
		{
			random_int = rand_r(&a);
		}
		p_vmc_sim->counter_id[i] = (uint8_t)(random_int & 0xFF);
	}
	for(i = 0; i < sizeof(p_vmc_sim->nonce); i++, random_int >>= 8)
	{
		if(!(i % 4))
		{
			random_int = rand_r(&a);
		}
		p_vmc_sim->nonce[i] = (uint8_t)(random_int & 0xFF);
	}
    return SGX_SUCCESS;
}

sgx_status_t del_vmc_sim(const vmc_sim_t *p_vmc_sim)
{
    char path[MAX_PATH] = {0};
    int ret = 0;

    uint64_t temp_value = 0;
    memcpy_s(&temp_value, sizeof(temp_value),
             p_vmc_sim->nonce, sizeof(temp_value));

    char *p_buf = g_vmc_base_path;

    ret = snprintf(path, sizeof(path), "%s%s%s%" PRIx64".dat",
                   p_buf, SE_DATA_FOLDER1, SE_DATA_FOLDER2, temp_value);
    if(-1 == ret){
        return SGX_ERROR_UNEXPECTED;
    }
    if(remove(path)){
        return SGX_ERROR_MC_NOT_FOUND;
    }

    return SGX_SUCCESS;
}

sgx_status_t store_vmc_sim(const vmc_sim_t *p_vmc_sim)
{
    char path[MAX_PATH] = {0};
    int ret = 0;

    uint64_t temp_value = 0;
    memcpy_s(&temp_value, sizeof(temp_value),
             p_vmc_sim->nonce, sizeof(temp_value));

    char *p_buf = g_vmc_base_path;

    ret = snprintf(path, sizeof(path), "%s%s",
                   p_buf,
                   SE_DATA_FOLDER1);
    if(-1 == ret){
        return SGX_ERROR_UNEXPECTED;
    }

    g_pse_sim_lock.lock();
    ret = mkdir(path, S_IRUSR|S_IWUSR|S_IXUSR);
    if(0 == ret){
        ret = chmod(path, S_IRUSR|S_IWUSR|S_IXUSR
                          |S_IRGRP|S_IWGRP|S_IXGRP
                          |S_IROTH|S_IWOTH|S_IXOTH);
        if(0 != ret){
            g_pse_sim_lock.unlock();
            return SGX_ERROR_UNEXPECTED;
        }
    }else if(EEXIST != errno){
        g_pse_sim_lock.unlock();
        return SGX_ERROR_UNEXPECTED;
    }
    g_pse_sim_lock.unlock();

    ret = snprintf(path, sizeof(path), "%s%s%s",
                   p_buf,
                   SE_DATA_FOLDER1,
                   SE_DATA_FOLDER2);
    if(-1 == ret){
        return SGX_ERROR_UNEXPECTED;
    }

    g_pse_sim_lock.lock();
    ret = mkdir(path, S_IRUSR|S_IWUSR|S_IXUSR);
    if(0 == ret){
        ret = chmod(path, S_IRUSR|S_IWUSR|S_IXUSR
                          |S_IRGRP|S_IWGRP|S_IXGRP
                          |S_IROTH|S_IWOTH|S_IXOTH);
        if(0 != ret){
            g_pse_sim_lock.unlock();
            return SGX_ERROR_UNEXPECTED;
        }
    }else if(EEXIST != errno){
        g_pse_sim_lock.unlock();
        return SGX_ERROR_UNEXPECTED;
    }
    g_pse_sim_lock.unlock();

    ret = snprintf(path, sizeof(path), "%s%s%s%" PRIx64".dat",
                   p_buf, SE_DATA_FOLDER1, SE_DATA_FOLDER2, temp_value);
    if(-1 == ret){
        return SGX_ERROR_UNEXPECTED;
    }
    FILE *fp = NULL;
    fp = fopen(path, "wb");
    if(!fp){
        return SGX_ERROR_MC_NOT_FOUND;
    }
    size_t wt_count = 0;
    wt_count = fwrite(p_vmc_sim, sizeof(vmc_sim_t), 1, fp);
    if(!wt_count){
        fclose(fp);
        return SGX_ERROR_UNEXPECTED;
    }
    fclose(fp);
    return SGX_SUCCESS;
}

sgx_status_t load_vmc_sim(vmc_sim_t *p_vmc_sim)
{
    char path[MAX_PATH] = {0};
    int ret = 0;

    uint64_t temp_value = 0;
    memcpy_s(&temp_value, sizeof(temp_value),
             p_vmc_sim->nonce, sizeof(temp_value));

    char *p_buf = g_vmc_base_path;

    ret = snprintf(path, sizeof(path), "%s%s%s%" PRIx64".dat",
                   p_buf, SE_DATA_FOLDER1, SE_DATA_FOLDER2, temp_value);
    if(-1 == ret){
        return SGX_ERROR_UNEXPECTED;
    }
    FILE *fp = NULL;
    fp = fopen(path, "rb");
    if(!fp){
        return SGX_ERROR_MC_NOT_FOUND;
    }
    size_t rd_count = 0;
    rd_count = fread(p_vmc_sim, sizeof(vmc_sim_t), 1, fp);
    if(!rd_count){
        fclose(fp);
        return SGX_ERROR_UNEXPECTED;
    }
    fclose(fp);
    return SGX_SUCCESS;
}
