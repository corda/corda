//===------------------------- mutex.cpp ----------------------------------===//
//
//                     The LLVM Compiler Infrastructure
//
// This file is dual licensed under the MIT and the University of Illinois Open
// Source Licenses. See LICENSE.TXT for details.
//
//===----------------------------------------------------------------------===//

#define _LIBCPP_BUILDING_MUTEX
#include "mutex"
#include "limits"
#include "system_error"
#include "cassert"
#include "include/atomic_support.h"

_LIBCPP_BEGIN_NAMESPACE_STD

const defer_lock_t  defer_lock = {};
const try_to_lock_t try_to_lock = {};
const adopt_lock_t  adopt_lock = {};

mutex::~mutex()
{
    sgx_thread_mutex_destroy(&__m_);
}

void
mutex::lock()
{
    int ec = sgx_thread_mutex_lock(&__m_);
    if (ec)
        __throw_system_error(ec, "mutex lock failed");
}

bool
mutex::try_lock() _NOEXCEPT
{
    return sgx_thread_mutex_trylock(&__m_) == 0;
}

void
mutex::unlock() _NOEXCEPT
{
    int ec = sgx_thread_mutex_unlock(&__m_);
    (void)ec;
    assert(ec == 0);
}

// recursive_mutex

recursive_mutex::recursive_mutex()
{
    __m_ = SGX_THREAD_RECURSIVE_MUTEX_INITIALIZER;
}

recursive_mutex::~recursive_mutex()
{
    int e = sgx_thread_mutex_destroy(&__m_);
    (void)e;
    assert(e == 0);
}

void
recursive_mutex::lock()
{
    int ec = sgx_thread_mutex_lock(&__m_);
    if (ec)
        __throw_system_error(ec, "recursive_mutex lock failed");
}

void
recursive_mutex::unlock() _NOEXCEPT
{
    int e = sgx_thread_mutex_unlock(&__m_);
    (void)e;
    assert(e == 0);
}

bool
recursive_mutex::try_lock() _NOEXCEPT
{
    return sgx_thread_mutex_trylock(&__m_) == 0;
}


// If dispatch_once_f ever handles C++ exceptions, and if one can get to it
// without illegal macros (unexpected macros not beginning with _UpperCase or
// __lowercase), and if it stops spinning waiting threads, then call_once should
// call into dispatch_once_f instead of here. Relevant radar this code needs to
// keep in sync with:  7741191.


#if defined(_LIBCPP_SGX_HAS_CXX_ATOMIC)

static sgx_thread_mutex_t mut = SGX_THREAD_MUTEX_INITIALIZER;
static sgx_thread_cond_t  cv  = SGX_THREAD_COND_INITIALIZER;

/// NOTE: Changes to flag are done via relaxed atomic stores
///       even though the accesses are protected by a mutex because threads
///       just entering 'call_once' concurrently read from flag.
void
__call_once(volatile unsigned long & flag, void* arg, void(*func)(void*))
{
    sgx_thread_mutex_lock(&mut);
    while (flag == 1)
        sgx_thread_cond_wait(&cv, &mut);
    if (flag == 0)
    {
        try
        {
            __libcpp_relaxed_store(&flag, 1ul);
            sgx_thread_mutex_unlock(&mut);
            func(arg);
            sgx_thread_mutex_lock(&mut);
            __libcpp_relaxed_store(&flag, ~0ul);
            sgx_thread_mutex_unlock(&mut);
            sgx_thread_cond_broadcast(&cv);
        }
        catch (...)
        {
            sgx_thread_mutex_lock(&mut);
            __libcpp_relaxed_store(&flag, 0ul);
            sgx_thread_mutex_unlock(&mut);
            sgx_thread_cond_broadcast(&cv);
            throw;
        }
    }
    else
        sgx_thread_mutex_unlock(&mut);

}

#endif // defined(_LIBCPP_SGX_HAS_CXX_ATOMIC)

_LIBCPP_END_NAMESPACE_STD
