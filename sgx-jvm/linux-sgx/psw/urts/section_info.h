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

#ifndef _SECTION_INFO_H_
#define _SECTION_INFO_H_

#include "arch.h"
#include "util.h"
#include <vector>

using namespace std;

typedef struct _section_info_t
{
    const uint8_t *raw_data;          //The file pointer to the first page of the section.
    uint64_t raw_data_size;     //The size of the section or the size of the initialized section on disk.
    uint64_t rva;               //The address of the first byte of the section relative to the image base when section is loaded into memory.
    uint64_t virtual_size;      //The total size of the section when loaded into memory.
    si_flags_t flag;            //the attribute of memory region.
    vector<uint8_t> *bitmap;    //bitmap of the total image page, if bit is 1, the page should be writable.
    //the first bit of scetion in the bitmap is bitmap[(rva >> PAGE_SHIFT) / 8] & (1 << ((rva >> PAGE_SHIFT) % 8))
    //if the bitmap is NULL, then no restrict on page attribute.
} section_info_t;

#endif
