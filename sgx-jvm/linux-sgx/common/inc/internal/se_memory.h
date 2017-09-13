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

#ifndef _SE_MEMORY_H_
#define _SE_MEMORY_H_

#define _FILE_OFFSET_BITS 64
#define _LARGEFILE64_SOURCE 1
#include <unistd.h>
#include <sys/mman.h>
#include <string.h>
#include <errno.h>

#ifndef MEM_COMMIT
#define MEM_COMMIT 0x1000
#endif

#ifndef MEM_RESERVE
#define MEM_RESERVE 0x2000
#endif

#ifdef MEM_RELEASE
#warning "MEM_RELEASE define conflict"
#else
#define MEM_RELEASE 0x8000
#endif

#ifdef MEM_DECOMMIT
#warning "MEM_DECOMMIT define conflict"
#else
#define MEM_DECOMMIT 0x4000
#endif

#include "se_types.h"
#include "arch.h"
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
Reserves or commits a region of pages in the virtual address space of the calling process.
Memory allocated by this function is automatically initialized to zero, unless MEM_RESET is specified.
@address:	the starting address of the region to allocate.
@size:	size of region in bytes.
@type:	Only MEM_COMMIT accepted.
        MEM_COMMIT - Allocates memory charges for the specified reserved memory pages. 
        Actual physical pages are not allocated until the virtual addresses are actually accessed.
        The function initializes the memory to zero.
@return value:	If the function succeeds, the return value is the base address of the allocated region of pages.
        If the function fails, the return value is NULL. 
*/
void* se_virtual_alloc(void* address, size_t size, uint32_t type);
/*
Releases, decommits, or releases and decommits a region of pages within the virtual address space of the calling process.
@address:A pointer to the base address of the region of pages to be freed. If the dwFreeType parameter is MEM_RELEASE,
        this parameter must be the base address returned by the se_virtual_alloc function when the region of pages is reserved.
@size:	The size of the region of memory to be freed, in bytes. 
@type:	Only MEM_RELEASE accepted
        MEM_RELEASE - releases the specified region of pages. After this operation, the pages are in the free state.
@return value:If the function succeeds, the return value is nonzero.If the function fails, the return value is zero. 
*/
int se_virtual_free(void* address, size_t size, uint32_t type);
/*
Locks the specified region of the process's virtual address space into physical memory, ensuring that subsequent access to the region will not incur a page fault.
@address:	A pointer to the base address of the region of pages to be locked.
            The region of affected pages includes all pages that contain one or more bytes in the range from the address parameter to (address+size). 
@size:		The size of the region to be locked, in bytes.
@return value:	If the function succeeds, the return value is nonzero. If the function fails, the return value is zero. 
*/
int se_virtual_lock(void* address, size_t size);
/*
Changes the protection on a region of committed pages in the virtual address space of the calling process.
@address:	A pointer an address that describes the starting page of the region of pages whose access protection attributes are to be changed.
@size:		The size of the region whose access protection attributes are to be changed, in bytes.
@prot:		The memory protection option. The option can be SI_FLAG_R, SI_FLAG_W, SI_FLAG_X.
@return value:	If the function succeeds, the return value is nonzero.If the function fails, the return value is zero.
*/

#define SGX_PROT_NONE PROT_NONE

int se_virtual_protect(void* address, size_t size, uint32_t prot);


#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
typedef pid_t se_proc_t;

/*
@return value: on success, return TRUE else return FALSE 
*/
se_proc_t get_self_proc(void);
/*
** If the function succeeds, the return value is nonzero.
** If the function fails, the return value is zero. 
*/
int put_self_proc(se_proc_t proc);
int se_read_process_mem(se_proc_t proc, void* base_addr, void* buffer, size_t size, size_t* read_nr);
int se_write_process_mem(se_proc_t proc, void* base_addr, void* buffer, size_t size, size_t* write_ndr);

#ifdef __cplusplus
}
#endif

#endif
