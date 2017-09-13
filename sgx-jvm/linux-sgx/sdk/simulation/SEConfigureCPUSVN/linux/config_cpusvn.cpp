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
* File: config_cpusvn.cpp
* Description: 
*     Implemente the CPUSVN upgrade/downgrade/reset simulation
*/

#include "cpusvn_helper.h"
#include "cpusvn_util.h"
#include "se_trace.h"
#include "rts_sim.h"
#include "se_wrapper.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <string>
#include <sstream>

using namespace std;

static void convert_cpusvn_to_string(sgx_cpu_svn_t &cpusvn, string &str)
{
    uint32_t buffer[4];
    memcpy_s(&buffer, sizeof(uint32_t)*4, &cpusvn, sizeof(sgx_cpu_svn_t));
    stringstream ss;
    for(int i=0; i<4; i++)
    {
        buffer[i] = __builtin_bswap32(buffer[i]);
        ss<<hex<<buffer[i];
    }
    str = ss.str();
    return;
}

static bool initialize(char *file_path, uint32_t length, sgx_cpu_svn_t &cpusvn)
{
    assert(file_path != NULL);
    // set file path
    if(get_file_path(file_path, length) == false)
    {
        return false;
    }
    // set cpusvn
    return read_cpusvn_file(file_path, &cpusvn);
}

static bool modify_cpusvn(action_t act, const char* file_path, sgx_cpu_svn_t &cpusvn)
{
    assert(file_path != NULL);

    bool ret = false;
    string cpusvn_str = "";
    switch(act)
    {
    case ACTION_RESET:
        {
            memcpy_s(&cpusvn, sizeof(sgx_cpu_svn_t), &DEFAULT_CPUSVN, sizeof(sgx_cpu_svn_t));
            ret = true;
        }
        break;
    case ACTION_UPGRADE:
        {
            if(!memcmp(&cpusvn, &UPGRADED_CPUSVN, sizeof(sgx_cpu_svn_t)))
            {
                printf( "You have already upgraded CPUSVN.\n");
            }
            else if(!memcmp(&cpusvn, &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN)))
            {
                memcpy_s(&cpusvn, sizeof(sgx_cpu_svn_t), &UPGRADED_CPUSVN, sizeof(sgx_cpu_svn_t));
                ret = true;
            }
            else
            {
                memcpy_s(&cpusvn, sizeof(sgx_cpu_svn_t), &DEFAULT_CPUSVN, sizeof(sgx_cpu_svn_t));
                ret = true;
            }
        }
        break;
    case ACTION_DOWNGRADE:
        {
            if(!memcmp(&cpusvn, &DOWNGRADED_CPUSVN, sizeof(sgx_cpu_svn_t)))
            {
                printf("You have already downgraded CPUSVN.\n");
            }
            else if(!memcmp(&cpusvn, &DEFAULT_CPUSVN, sizeof(DEFAULT_CPUSVN)))
            {
                memcpy_s(&cpusvn, sizeof(sgx_cpu_svn_t), &DOWNGRADED_CPUSVN, sizeof(sgx_cpu_svn_t));
                ret = true;
            }
            else
            {
                memcpy_s(&cpusvn, sizeof(sgx_cpu_svn_t), &DEFAULT_CPUSVN, sizeof(sgx_cpu_svn_t));
                ret = true;
            }
        }
        break;
    default:
        {
            printf("Failed to configure the CPUSVN.\n");
        }
        break;
    }
    if(ret == true)
    {
        if(write_cpusvn_file(file_path, &cpusvn) == false)
        {
            printf("Failed to configure the CPUSVN.\n");
            return false;
        }
    }
    convert_cpusvn_to_string(cpusvn, cpusvn_str);
    printf("Current CPUSVN is: %s.\n", cpusvn_str.c_str());
    return ret;
}

int main(int argc, char *argv[])
{
    if(argc != 2)
    {
        printf("Invalid input parameters.\n%s\n", USAGE);
        return -1;
    }

    action_t act;
    if(!strcmp(argv[1], HELP))
    {
	printf("%s\n", USAGE);
        return 0;
    }
    else if(!strcmp(argv[1], UPGRADE))
    {
        act = ACTION_UPGRADE;
    }
    else if(!strcmp(argv[1], DOWNGRADE))
    {
        act = ACTION_DOWNGRADE;
    }
    else if(!strcmp(argv[1], RESET))
    {
        act = ACTION_RESET;
    }
    else
    {

        printf("Invalid input parameters.\n%s\n", USAGE);
        return -1;
    }
    char file_path[MAX_PATH];
    sgx_cpu_svn_t cpusvn;
    memset(file_path, 0, MAX_PATH);
    memset(&cpusvn, 0, sizeof(sgx_cpu_svn_t));
    if(initialize(file_path, MAX_PATH, cpusvn) == false)
    {     
        printf("Failed to get the CPUSVN.\n");
        return -1;
    }
    if(modify_cpusvn(act, file_path, cpusvn) == false)
    {
        return -1;
    }
    printf("SUCCESS.\n");
    return 0;
}
