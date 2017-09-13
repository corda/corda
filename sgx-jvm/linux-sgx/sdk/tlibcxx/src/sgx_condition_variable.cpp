//===-------------------- condition_variable.cpp --------------------------===//
//
//                     The LLVM Compiler Infrastructure
//
// This file is dual licensed under the MIT and the University of Illinois Open
// Source Licenses. See LICENSE.TXT for details.
//
//===----------------------------------------------------------------------===//

#include "__config"

#include "condition_variable"
#include "system_error"
#include "cassert"

_LIBCPP_BEGIN_NAMESPACE_STD

condition_variable::~condition_variable()
{
    sgx_thread_cond_destroy(&__cv_);
}

void
condition_variable::notify_one() _NOEXCEPT
{
    sgx_thread_cond_signal(&__cv_);
}

void
condition_variable::notify_all() _NOEXCEPT
{
    sgx_thread_cond_broadcast(&__cv_);
}

void
condition_variable::wait(unique_lock<mutex>& lk) _NOEXCEPT
{
    if (!lk.owns_lock())
        __throw_system_error(EPERM,
                                  "condition_variable::wait: mutex not locked");
    int ec = sgx_thread_cond_wait(&__cv_, lk.mutex()->native_handle());
    if (ec)
        __throw_system_error(ec, "condition_variable wait failed");
}

_LIBCPP_END_NAMESPACE_STD
