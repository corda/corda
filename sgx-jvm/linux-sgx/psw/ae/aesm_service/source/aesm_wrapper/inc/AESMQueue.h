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
#ifndef AESM_QUEUE_H
#define AESM_QUEUE_H

#include <queue>
#include <pthread.h>
#include "RequestData.h"
#include "IAERequest.h"
#include "IAESMQueue.h"
#include <oal/error_report.h>

template <typename T>
class AESMQueue : public IAESMQueue<T> {
    public:
        AESMQueue();
        ~AESMQueue();

        void push(T*);
        T* blockingPop();
        void close();

    private:
        std::queue<T*>  m_queue;
        pthread_cond_t            m_queueCond;
        pthread_mutex_t           m_queueMutex;
        volatile  uint8_t         m_events;
    private:
        AESMQueue& operator=(const AESMQueue&);
        AESMQueue(const AESMQueue&);
};

#define QUEUE_EVENT_CLOSE    1

template<typename T>
AESMQueue<T>::AESMQueue() :
    m_queue()
{
    int rc;

    rc = pthread_mutex_init(&m_queueMutex, NULL);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to initialize mutex");
        exit(-1);
    }

    rc = pthread_cond_init(&m_queueCond, NULL);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to initialize a condition variable");
        exit(-1);
    }
 
    m_events = 0;
}

template<typename T>
AESMQueue<T>::~AESMQueue()
{
    int rc;

    rc = pthread_mutex_destroy(&m_queueMutex);
    if (rc != 0)
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to destroy mutex");

    rc = pthread_cond_destroy(&m_queueCond);
    if (rc != 0)
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to destory a condition variable");

}

template<typename T>
void AESMQueue<T>::push(T* value)
{
    int rc;

    rc = pthread_mutex_lock(&m_queueMutex);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to acquire mutex");
        exit(-1);
    }

    m_queue.push(value);

    // m_events |= QUEUE_EVENT_INSERT;
    rc = pthread_cond_signal(&m_queueCond);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to signal condition");
        exit(-1);
    }

    rc = pthread_mutex_unlock(&m_queueMutex);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to unlock mutex");
        exit(-1);
    }
}

template<typename T> 
T* AESMQueue<T>::blockingPop()
{
    int rc;
    T* value = NULL;

    rc = pthread_mutex_lock(&m_queueMutex);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to acquire mutex");
        exit(-1);
    }

    while(1) {
        // check CLOSE event
        if (m_events & QUEUE_EVENT_CLOSE) {
            //epmty the queue
            while (!m_queue.empty())
            {
                value = m_queue.front();
                m_queue.pop();
                delete value;
            }
            value = NULL;
            break;
        }
        if (!m_queue.empty()) {
            // queue is not empty
            value = m_queue.front();
            m_queue.pop();
            break;
        }
        else {
            rc = pthread_cond_wait(&m_queueCond, &m_queueMutex);

            if (rc != 0)
            {
                aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed wait on a condition");
                exit(-1);
            }
        }
    }

    rc = pthread_mutex_unlock(&m_queueMutex);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to unlock mutex");
        exit(-1);
    }

    return value;
}

template<typename T>
void AESMQueue<T>::close()
{
    int rc;

    rc = pthread_mutex_lock(&m_queueMutex);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to acquire mutex");
        exit(-1);
    }

    m_events |= QUEUE_EVENT_CLOSE;
    rc = pthread_cond_signal(&m_queueCond);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to signal a condition");
        exit(-1);
    }

    rc = pthread_mutex_unlock(&m_queueMutex);
    if (rc != 0)
    {
        aesm_log_report(AESM_LOG_REPORT_ERROR, "Failed to unlock mutex");
        exit(-1);
    }
}

#endif
