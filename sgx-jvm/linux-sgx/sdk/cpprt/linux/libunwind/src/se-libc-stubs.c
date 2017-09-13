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

#ifdef _GNU_SOURCE
#undef _GNU_SOURCE
#endif

#include "arch.h"
#include "sgx_trts.h"

#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>


/**
 * In SE, the page size is defined by macro `SE_PAGE_SIZE'.
 */
int getpagesize(void)
{
    return SE_PAGE_SIZE;
}

int mincore(void *addr, size_t length, unsigned char *vec)
{
    assert(sgx_is_within_enclave(addr, length));

    return 0;
}

char *strdup(const char *s)
{
    size_t len = strlen(s) + 1;
    void  *mem = malloc(len);

    if (mem == NULL)
        return mem;

    return memcpy(mem, s, len);
}

/* When optimization is turned on (even with -O), a call to
 * strdup() will be replaced by __strdup(), wich GCC.
 */
char *
__strdup(const char* s) __attribute__((weak, alias("strdup")));

#if defined(__x86_64__) || defined(__x86_64) || defined(__amd64)
int msync(void *addr, size_t length, int flags)
{
    return 0;
}
#endif

#ifndef NDEBUG
/* FIXME: remove __assert_fail()
 * Currently libunwind is built with glibc headers, to improve it
 * we need to build it with SE tlibc headers.
 */
void __assert_fail (const char *__assertion, const char *__file,
        unsigned int __line, __const char *__function)
{
    abort();
}
#endif

#endif
/* vim: set ts=4 sw=4 cin et: */
