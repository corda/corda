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
#ifdef HAVE_SGX

#include "global_data.h"
#include <string.h>

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <link.h>

/**
 * This function is commonly provided by glibc for application to walk
 * through list of shared objects.  It is needed inside Enclave so that
 * the libunwind code can work correctly.
 */
int dl_iterate_phdr(
        int (*callback) (struct dl_phdr_info *info,
                         size_t size, void *data),
        void *data)
{
    struct dl_phdr_info info;
    ElfW(Ehdr)         *ehdr;

    memset(&info, 0, sizeof(info));
    ehdr = (ElfW(Ehdr) *) &__ImageBase;

    info.dlpi_addr   = (ElfW(Addr)) ehdr;
    info.dlpi_name  = "";
    info.dlpi_phdr  = (ElfW(Phdr) *) ((char *)ehdr + ehdr->e_phoff);
    info.dlpi_phnum = ehdr->e_phnum;

    /* No iteration here - the Enclave is merely one shared object. */
    return callback(&info, sizeof(info), data);
}

#endif
