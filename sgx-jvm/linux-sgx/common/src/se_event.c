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


#include "se_event.h"

#include <linux/futex.h>

se_handle_t se_event_init(void)
{
    return calloc(1, sizeof(int)); 
}

void se_event_destroy(se_handle_t se_event)
{
    if (se_event != NULL)
        free(se_event); 
}

int se_event_wait(se_handle_t se_event)
{
    if (se_event == NULL)
        return SE_MUTEX_INVALID;

    if (__sync_fetch_and_add((int*)se_event, -1) == 0)
        syscall(__NR_futex, se_event, FUTEX_WAIT, -1, NULL, NULL, 0);

    return SE_MUTEX_SUCCESS;
}

int se_event_wake(se_handle_t se_event)
{
    if (se_event == NULL)
        return SE_MUTEX_INVALID;

    if (__sync_fetch_and_add((int*)se_event, 1) != 0)
        syscall(__NR_futex, se_event, FUTEX_WAKE, 1, NULL, NULL, 0);

    return SE_MUTEX_SUCCESS;
}
