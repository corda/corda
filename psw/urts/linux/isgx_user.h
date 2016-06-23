/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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
#ifndef _X86_ISGX_USER_H
#define _X86_ISGX_USER_H

#include <linux/ioctl.h>
#include <linux/stddef.h>
#include <linux/types.h>

#define ISGX_IOCTL_ENCLAVE_CREATE   _IOWR('p', 0x02, struct isgx_create_param)
#define ISGX_IOCTL_ENCLAVE_ADD_PAGE _IOW('p', 0x03, struct isgx_add_param)
#define ISGX_IOCTL_ENCLAVE_INIT     _IOW('p', 0x04, struct isgx_init_param)
#define ISGX_IOCTL_ENCLAVE_DESTROY  _IOW('p', 0x06, struct isgx_destroy_param)

#define SECS_SIZE_OFFSET                0
#define SECS_BASE_OFFSET                (SECS_SIZE_OFFSET + 8)
#define SECS_FLAGS_OFFSET               (SECS_BASE_OFFSET + 8)
#define SECS_SSAFRAMESIZE_OFFSET        (SECS_SIZE_OFFSET + 164)

/* SGX leaf instruction return values */
#define ISGX_SUCCESS                0
#define ISGX_ERROR                  -1
#define ISGX_INVALID_SIG_STRUCT     0x1
#define ISGX_INVALID_ATTRIBUTE      0x2
#define ISGX_INVALID_MEASUREMENT    0x4
#define ISGX_INVALID_SIGNATIRE      0x8
#define ISGX_INVALID_LAUNCH_TOKEN   0x10
#define ISGX_INVALID_CPUSVN         0x20
#define ISGX_INVALID_ISVSVN         0x40
#define ISGX_UNMASKED_EVENT         0x80
#define ISGX_INVALID_KEYNAME        0x100

/* IOCTL return values */
#define ISGX_OUT_OF_EPC_PAGES       0xc0000001
#define ISGX_POWER_LOST_ENCLAVE     0xc0000002

/* SECINFO flags */
#define ISGX_SECINFO_R      0x1     /* Read Access */
#define ISGX_SECINFO_W      0x2     /* Write Access */
#define ISGX_SECINFO_X      0x4     /* Execute Access */
#define ISGX_SECINFO_SECS   0x000   /* SECS */
#define ISGX_SECINFO_TCS    0x100   /* TCS */
#define ISGX_SECINFO_REG    0x200   /* Regular Page */

struct isgx_secinfo {
    __u64 flags;
    __u64 reserved[7];
};

struct isgx_create_param {
    void *secs;
    unsigned long addr;
};

#define ISGX_ADD_SKIP_EEXTEND 0x1

struct isgx_add_param {
    unsigned long addr;
    unsigned long user_addr;
    void *secinfo;
    unsigned int flags;
};

struct isgx_init_param {
    unsigned long addr;
    void *sigstruct;
    void *einittoken;
};

struct isgx_destroy_param {
    unsigned long addr;
};

#endif /* _X86_ISGX_USER_H */
