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


#include "node.h"
#include <cstddef>
#include "enclave.h"
#include "tcs.h"

template<class T1, class T2>
Node<T1, T2>::Node(const T1& k, const T2& v)
    : key(k), value(v), next(nullptr)
{
}

template<class T1, class T2>
bool Node<T1, T2>::InsertNext(Node<T1, T2> *p)
{
    if (this->Find(p->key) != NULL)
        return false;
    p->next = this->next;
    this->next = p;
    return true;
}

template<class T1, class T2>
Node<T1, T2>* Node<T1, T2>::Remove(const T1& k)
{
    Node<T1, T2> *c = this, *p = this;
    while (c != NULL) {
        if (c->key == k) {
            p->next = c->next;
            break;
        }
        p = c;
        c = c->next;
    }
    return c;
}

template<class T1, class T2>
Node<T1, T2>* Node<T1, T2>::Find(const T1& k)
{
    for(auto c= this; c != nullptr; c = c->next)
    {
        if (c->key == k)
            return c;
    }
    return nullptr;
}


template class Node<sgx_enclave_id_t, CEnclave*>;
template class Node<se_thread_id_t, CTrustThread *>;
