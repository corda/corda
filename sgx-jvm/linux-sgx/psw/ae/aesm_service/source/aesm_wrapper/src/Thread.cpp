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
#include "Thread.h"
#include <stdlib.h>
#include <assert.h>
#include <oal/error_report.h>
#include <new>
#include "util.h"


/*--------------------------------------------------------------------------------------------------*/

Thread::Thread()
        : m_shutDown(false), m_thread(0)
{
}

Thread::~Thread()
{
}

void Thread::start()
{
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);

    int rc = pthread_create(&m_thread, &attr, Thread::doWork, (void*)this);
    assert(rc == 0);
    UNUSED(rc);

    pthread_attr_destroy(&attr);
}

void Thread::stop()
{
    m_shutDown = true;
}

bool Thread::isStopped()
{
    return m_shutDown;
}

/*virtual*/
void  Thread::run()
{
}

/*static*/
void* Thread::doWork(void* param)
{
    try
    {
        Thread* thread = static_cast<Thread*>(param);
        thread->run();
    }
    catch(std::bad_alloc& allocationException)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Unable to allocate memory");
        throw;
    }

    return NULL;
};


void Thread::join()
{
    void* res;
    pthread_join(m_thread, &res);
}

/*--------------------------------------------------------------------------------------------------*/
