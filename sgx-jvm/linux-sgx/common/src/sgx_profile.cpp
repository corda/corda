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


#ifdef _PROFILE_

#include <iostream>
#include <vector>
#include <fstream>
#include "sgx_profile.h"
#include "se_time.h"
#include <string.h>
using namespace std;

typedef struct _profile_item_t{
    const char *str;  /* tag */
    int  flag;        /* 0: start, 1: end */
    long long time;   /* current time */
} profile_item_t;

static vector<profile_item_t> profile_items;
static int alloc_size;
static int used_size;
const int MALLOC_SIZE = 1000;
static long long freq = {0};

#define MALLOC_TAG "PROFILE_MALLOC_CONSUMED_TIME"

extern "C" void profile_init()
{
    freq = se_get_tick_count_freq();
    profile_items.resize(MALLOC_SIZE);
    alloc_size = MALLOC_SIZE;
    used_size = 0;
}

static void profile_add_info(const char *str, int flag)
{
    long long cur_time = se_get_tick_count();
    if(used_size==alloc_size){
        alloc_size+=MALLOC_SIZE;
        profile_items.resize(alloc_size);
        profile_items[used_size].flag = PRO_START;
        profile_items[used_size].str = MALLOC_TAG;
        profile_items[used_size].time = cur_time;
        cur_time = se_get_tick_count();
        used_size++;
        profile_items[used_size].flag = PRO_END;
        profile_items[used_size].str = MALLOC_TAG;
        profile_items[used_size].time = cur_time;
        used_size++;
    }
    profile_items[used_size].flag = flag;
    profile_items[used_size].str = str;
    profile_items[used_size].time = cur_time;
    used_size++;
}

extern "C" void profile_start(const char* str)
{
    profile_add_info(str, PRO_START);
}

extern "C" void profile_end(const char * str)
{
    profile_add_info(str, PRO_END);
}

#include <string>
std::string get_prof_fun_name(const char *s)
{
    std::string input(s);
    size_t end = input.find("(");
    size_t begin = input.substr(0,end).rfind(" ")+1;
    end = end - begin;
    return input.substr(begin,end);
}

extern "C" void profile_output(const char* filename)
{
    int i,j;

    ofstream fs;
    fs.open(filename); /* do not overwritten previous value */

    fs << "freq: " << freq <<endl;
    fs << "tag" << "," << "start_cycle" << "," << "end_cycle" << endl;

    for(i=0; i<used_size; i ++)
    {
        if(profile_items[i].flag!=PRO_START)
            continue;
        for(j = i + 1 ; j <used_size; j++)
        {
            if(strcmp(profile_items[i].str, profile_items[j].str) == 0)
            {
                if(profile_items[j].flag == PRO_END)
                    break;
                else
                {
                    /* cout << "Error: find another start for " << it->str << endl; */
                    return;
                }
            }
        }

        if(j == used_size)
        {
            /* cout << "Error: not find end for " << it->str << endl; */
            return;
        }

        fs << get_prof_fun_name(profile_items[i].str) << "," << profile_items[i].time << "," << profile_items[j].time << endl;
    }
    profile_items.clear();
    used_size=0;
    alloc_size=0;
    fs.close();

}
#endif
