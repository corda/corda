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

#ifndef _METADATA_H_
#define _METADATA_H_
#include "arch.h"
#include "se_macro.h"

#pragma pack(1)

 /* version of metadata */
#define MAJOR_VERSION 2                 //MAJOR_VERSION should not larger than 0ffffffff
#define MINOR_VERSION 1                 //MINOR_VERSION should not larger than 0ffffffff

#define SGX_1_9_MAJOR_VERSION 1         //MAJOR_VERSION should not larger than 0ffffffff
#define SGX_1_9_MINOR_VERSION 4         //MINOR_VERSION should not larger than 0ffffffff

#define SGX_1_5_MAJOR_VERSION 1         //MAJOR_VERSION should not larger than 0ffffffff
#define SGX_1_5_MINOR_VERSION 3         //MINOR_VERSION should not larger than 0ffffffff


#define META_DATA_MAKE_VERSION(major, minor) (((uint64_t)major)<<32 | minor)

#define METADATA_MAGIC 0x86A80294635D0E4CULL
#define METADATA_SIZE 0x3000
#define TCS_TEMPLATE_SIZE 72

/* TCS Policy bit masks */
#define TCS_POLICY_BIND     0x00000000  /* If set, the TCS is bound to the application thread */
#define TCS_POLICY_UNBIND   0x00000001

#define MAX_SAVE_BUF_SIZE 2632    

#define TCS_NUM_MIN 1
#define SSA_NUM_MIN 2
#define SSA_FRAME_SIZE_MIN 1
#define SSA_FRAME_SIZE_MAX 2
#define STACK_SIZE_MIN 0x1000
#define STACK_SIZE_MAX 0x40000
#define HEAP_SIZE_MIN 0x1000
#define HEAP_SIZE_MAX 0x1000000
#define DEFAULT_MISC_SELECT 0
#define DEFAULT_MISC_MASK 0xFFFFFFFF

typedef struct _data_directory_t
{
    uint32_t    offset;
    uint32_t    size;
} data_directory_t;

typedef enum
{
    DIR_PATCH,
    DIR_LAYOUT,
    DIR_NUM,
} dir_index_t;

#define GROUP_FLAG              (1<<12)
#define GROUP_ID(x)             (GROUP_FLAG | x)
#define IS_GROUP_ID(x)          !!((x) & GROUP_FLAG)
#define LAYOUT_ID_HEAP_MIN      1
#define LAYOUT_ID_HEAP_INIT     2
#define LAYOUT_ID_HEAP_MAX      3
#define LAYOUT_ID_TCS           4
#define LAYOUT_ID_TD            5
#define LAYOUT_ID_SSA           6
#define LAYOUT_ID_STACK_MAX     7
#define LAYOUT_ID_STACK_MIN     8
#define LAYOUT_ID_THREAD_GROUP  GROUP_ID(9)
#define LAYOUT_ID_GUARD         10
#define LAYOUT_ID_HEAP_DYN_MIN  11
#define LAYOUT_ID_HEAP_DYN_INIT 12
#define LAYOUT_ID_HEAP_DYN_MAX  13
#define LAYOUT_ID_TCS_DYN       14
#define LAYOUT_ID_TD_DYN        15
#define LAYOUT_ID_SSA_DYN       16
#define LAYOUT_ID_STACK_DYN_MAX 17
#define LAYOUT_ID_STACK_DYN_MIN 18
#define LAYOUT_ID_THREAD_GROUP_DYN GROUP_ID(19)



/* 
**    layout table example
**    entry0 - entry1 - entry2 - group3 (entry_count=2, load_times=3) ...
**    the load sequence should be:
**    entry0 - entry1 - entry2 - entry1 - entry2 - entry1 - entry2 - entry1 - entry2 ...
**                               --------------    --------------    --------------
**                               group3 1st time   group3 2nd time   group3 3rd time
*/
typedef struct _layout_entry_t
{
    uint16_t    id;             /* unique ID to identify the purpose for this entry */
    uint16_t    attributes;     /* EADD/EEXTEND/EREMOVE... */
    uint32_t    page_count;     /* map size in page. Biggest chunk = 2^32 pages = 2^44 bytes. */
    uint64_t    rva;            /* map offset, relative to encalve base */
    uint32_t    content_size;   /* if content_offset = 0, content_size is the initial data to fill the whole page. */
    uint32_t    content_offset; /* offset to the initial content, relative to metadata */
    si_flags_t  si_flags;       /* security info, R/W/X, SECS/TCS/REG/VA */
} layout_entry_t;

typedef struct _layout_group_t
{
    uint16_t    id;             /* unique ID to identify the purpose for this entry */
    uint16_t    entry_count;    /* reversely count entry_count entries for the group loading. */
    uint32_t    load_times;     /* the repeated times of loading */
    uint64_t    load_step;      /* the group size. the entry load rva should be adjusted with the load_step */
                                /* rva = entry.rva + group.load_step * load_times */
    uint32_t    reserved[4];
} layout_group_t;

typedef union _layout_t
{
    layout_entry_t entry;
    layout_group_t group;
} layout_t;

typedef struct _patch_entry_t
{
    uint64_t dst;               /* relative to enclave file base */
    uint32_t src;               /* relative to metadata base */
    uint32_t size;              /* patched size */
    uint32_t reserved[4];
} patch_entry_t;

typedef struct _metadata_t 
{
    uint64_t            magic_num;             /* The magic number identifying the file as a signed enclave image */
    uint64_t            version;               /* The metadata version */
    uint32_t            size;                  /* The size of this structure */
    uint32_t            tcs_policy;            /* TCS management policy */
    uint32_t            ssa_frame_size;        /* The size of SSA frame in page */
    uint32_t            max_save_buffer_size;  /* Max buffer size is 2632 */
    uint32_t            desired_misc_select;
    uint32_t            tcs_min_pool;          /* TCS min pool*/         
    uint64_t            enclave_size;          /* enclave virtual size */
    sgx_attributes_t    attributes;            /* XFeatureMask to be set in SECS. */
    enclave_css_t       enclave_css;           /* The enclave signature */
    data_directory_t    dirs[DIR_NUM];
    uint8_t             data[10400];
}metadata_t;

se_static_assert(sizeof(metadata_t) == METADATA_SIZE);

#pragma pack()

#endif
