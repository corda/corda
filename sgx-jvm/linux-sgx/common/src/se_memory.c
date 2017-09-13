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



#include "se_memory.h"
#include "se_trace.h"
#include "util.h"

void* se_virtual_alloc(void* address, size_t size, uint32_t type)
{
    UNUSED(type);
    void* pRet = mmap(address, size, PROT_READ | PROT_WRITE, MAP_PRIVATE |  MAP_ANONYMOUS, -1, 0);
    if(MAP_FAILED == pRet)
        return NULL;
    return pRet;
}

int se_virtual_free(void* address, size_t size, uint32_t type)
{
    UNUSED(type);

    return !(munmap(address, size));

}

int se_virtual_lock(void* address, size_t size)
{
    return !mlock(address, size);
}

static unsigned int get_prot(uint64_t flags)
{
    if ((flags & SI_FLAG_PT_MASK) == SI_FLAG_TCS)
        return PROT_READ|PROT_WRITE|PROT_EXEC;

    switch (flags & (SI_FLAG_R | SI_FLAG_W | SI_FLAG_X))
    {
    case SI_FLAG_X:				return PROT_EXEC;			break;
    case SI_FLAG_R | SI_FLAG_X:		return PROT_READ|PROT_EXEC;		break;
    case SI_FLAG_R | SI_FLAG_W | SI_FLAG_X:	return PROT_READ|PROT_WRITE|PROT_EXEC;	break;
    case SI_FLAG_R:				return PROT_READ;			break;
    case SI_FLAG_R | SI_FLAG_W:		return PROT_READ|PROT_WRITE;		break;
        /* This covers no access, W and WX */
    default:				return PROT_NONE;			break;
    }

}
int se_virtual_protect(void* address, size_t size, uint32_t prot)
{
    return !mprotect(address, size, (int)get_prot(prot));
}

se_proc_t get_self_proc()
{
    return getpid();
}

int put_self_proc(se_proc_t proc)
{
    UNUSED(proc);
    return 1;
}

int se_read_process_mem(se_proc_t proc, void* base_addr, void* buffer, size_t size, size_t* read_nr)
{
    char filename[64];
    int fd = -1;
    int ret = FALSE;
    ssize_t len = 0;
    off64_t offset = (off64_t)(size_t) base_addr;

    snprintf (filename, 64, "/proc/%d/mem", (int)proc); 
    fd = open(filename, O_RDONLY | O_LARGEFILE);
    if(fd == -1)
        return FALSE;

    if(lseek64(fd, offset, SEEK_SET) == -1)
    {
        goto out;
    }
    if((len = read(fd, buffer, size)) < 0)
    {
        goto out;
    }
    else if(read_nr)
        *read_nr = (size_t)len; /* len is a non-negative number */

    ret = TRUE;

out:
    close (fd);
    return ret;
}

int se_write_process_mem(se_proc_t proc, void* base_addr, void* buffer, size_t size, size_t* write_nr)
{
    char filename[64];
    int fd = -1;
    int ret = FALSE;
    ssize_t len = 0;
    off64_t offset = (off64_t)(size_t)base_addr;

    snprintf (filename, 64, "/proc/%d/mem", (int)proc); 
    fd = open(filename, O_RDWR | O_LARGEFILE);
    if(fd == -1)
        return FALSE;

    if(lseek64(fd, offset, SEEK_SET) == -1)
    {
        goto out;
    }
    if((len = write(fd, buffer, size)) < 0)
    {
        goto out;
    }
    else if(write_nr)
        *write_nr = (size_t)len; /* len is a non-negative number */

    ret = TRUE;
out:
    close (fd);
    return ret;
}

