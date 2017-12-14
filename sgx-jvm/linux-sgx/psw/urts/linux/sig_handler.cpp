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


#include "arch.h"
#include "sgx_error.h"
#include "tcs.h"
#include "se_trace.h"
#include "rts.h"
#include "enclave.h"
#include <assert.h>
#include <signal.h>
#include <string.h>
#include <errno.h>


typedef struct _ecall_param_t
{
    tcs_t *tcs;
    long   fn;              //long because we need register bandwith align on stack, refer to enter_enclave.h;
    void *ocall_table;
    void *ms;
    CTrustThread *trust_thread;
} ecall_param_t;

#ifdef __x86_64__
#define REG_XIP REG_RIP
#define REG_XAX REG_RAX
#define REG_XBX REG_RBX
#define REG_XSI REG_RSI
#define REG_XBP REG_RBP
/*
 * refer to enter_enclave.h
 * stack high address <-------------
 * |rip|rbp|rbx|r10|r13|r14|r15|r8|rcx|rdx|rsi|rdi|
 *         ^                     ^
 *         | <-rbp               | <-param4
 */
#define ECALL_PARAM (reinterpret_cast<ecall_param_t*>(context->uc_mcontext.gregs[REG_RBP] - 10 * 8))
#else
#define REG_XIP REG_EIP
#define REG_XAX REG_EAX
#define REG_XBX REG_EBX
#define REG_XSI REG_ESI
#define REG_XBP REG_EBP
/*
 * refer to enter_enclave.h
 * stack high address <-------------
 * |param4|param3|param2|param2|param0|eip|ebp|
 *                                            ^
 *                                            | <-ebp
 */
#define ECALL_PARAM (reinterpret_cast<ecall_param_t*>(context->uc_mcontext.gregs[REG_EBP] + 2 * 4))
#endif

extern "C" void *get_aep();
extern "C" void *get_eenterp();
extern "C" void *get_eretp();
static struct sigaction g_old_sigact[_NSIG];

void reg_sig_handler();

void sig_handler(int signum, siginfo_t* siginfo, void *priv)
{
    SE_TRACE(SE_TRACE_DEBUG, "signal handler is triggered\n");
    ucontext_t* context = reinterpret_cast<ucontext_t *>(priv);
    unsigned int *xip = reinterpret_cast<unsigned int *>(context->uc_mcontext.gregs[REG_XIP]);
    size_t xax = context->uc_mcontext.gregs[REG_XAX];
#ifndef NDEBUG
    /* `xbx' is only used in assertions. */
    size_t xbx = context->uc_mcontext.gregs[REG_XBX];
#endif
    ecall_param_t *param = ECALL_PARAM;

    //the case of exception on ERESUME or within enclave.
    //We can't distinguish ERESUME exception from exception within enclave. We assume it is the exception within enclave.
    //If it is ERESUME exception, it will raise another exception in ecall and ecall will return error.
    if(xip == get_aep()
            && SE_ERESUME == xax)
    {
        assert(ENCLU == (*xip & 0xffffff));
        //suppose the exception is within enclave.
        SE_TRACE(SE_TRACE_NOTICE, "exception on ERESUME\n");
        //The ecall looks recursively, but it will not cause infinite call.
        //If exception is raised in trts again and again, the SSA will overflow, and finally it is EENTER exception.
        assert(reinterpret_cast<tcs_t *>(xbx) == param->tcs);
        CEnclave *enclave = param->trust_thread->get_enclave();
        unsigned int ret = enclave->ecall(ECMD_EXCEPT, param->ocall_table, NULL);
        if(SGX_SUCCESS == ret)
        {
            //ERESUME execute
            return;
        }
        //If the exception is caused by enclave lost or internal stack overrun, then return the error code to ecall caller elegantly.
        else if(SGX_ERROR_ENCLAVE_LOST == ret || SGX_ERROR_STACK_OVERRUN == ret)
        {
            //enter_enlcave function will return with ret which is from tRTS;
            context->uc_mcontext.gregs[REG_XIP] = reinterpret_cast<greg_t>(get_eretp());
            context->uc_mcontext.gregs[REG_XSI] = ret;
            return;
        }
        //If we can't fix the exception within enclave, then give the handle to other signal hanlder.
        //Call the previous signal handler. The default signal handler should terminate the application.
        
        enclave->rdunlock();
        CEnclavePool::instance()->unref_enclave(enclave);
    }
    //the case of exception on EENTER instruction.
    else if(xip == get_eenterp()
            && SE_EENTER == xax)
    {
        assert(reinterpret_cast<tcs_t *>(xbx) == param->tcs);
        assert(ENCLU == (*xip & 0xffffff));
        SE_TRACE(SE_TRACE_NOTICE, "exception on EENTER\n");
        //enter_enlcave function will return with SE_ERROR_ENCLAVE_LOST
        context->uc_mcontext.gregs[REG_XIP] = reinterpret_cast<greg_t>(get_eretp());
        context->uc_mcontext.gregs[REG_XSI] = SGX_ERROR_ENCLAVE_LOST;
        return;
    }

    SE_TRACE(SE_TRACE_DEBUG, "NOT enclave signal\n");
    //it is not SE exception. if the old signal handler is default signal handler, we reset signal handler.
    //raise the signal again, and the default signal handler will be called.
    if(SIG_DFL == g_old_sigact[signum].sa_handler)
    {
        signal(signum, SIG_DFL);
        raise(signum);
    }
    //if there is old signal handler, we need transfer the signal to the old signal handler;
    else
    {
        if(!(g_old_sigact[signum].sa_flags & SA_NODEFER))
            sigaddset(&g_old_sigact[signum].sa_mask, signum);

        sigset_t cur_set;
        pthread_sigmask(SIG_SETMASK, &g_old_sigact[signum].sa_mask, &cur_set);

        if(g_old_sigact[signum].sa_flags & SA_SIGINFO)
        {
            g_old_sigact[signum].sa_sigaction(signum, siginfo, priv);
        }
        else
        {
            g_old_sigact[signum].sa_handler(signum);
        }

        pthread_sigmask(SIG_SETMASK, &cur_set, NULL);

        //If the g_old_sigact set SA_RESETHAND, it will break the chain which means
        //g_old_sigact->next_old_sigact will not be called. Our signal handler does not
        //responsable for that. We just follow what os do on SA_RESETHAND.
        if(g_old_sigact[signum].sa_flags & SA_RESETHAND)
            g_old_sigact[signum].sa_handler = SIG_DFL;
    }
}

void reg_sig_handler()
{
    int ret = 0;
    struct sigaction sig_act;
    SE_TRACE(SE_TRACE_DEBUG, "signal handler is registered\n");

    memset(&sig_act, 0, sizeof(sig_act));
    sig_act.sa_sigaction = sig_handler;
    sig_act.sa_flags = SA_SIGINFO | SA_NODEFER | SA_RESTART;
    sigemptyset(&sig_act.sa_mask);
    if(sigprocmask(SIG_SETMASK, NULL, &sig_act.sa_mask))
    {
        SE_TRACE(SE_TRACE_WARNING, "%s\n", strerror(errno));
    }
    else
    {
        sigdelset(&sig_act.sa_mask, SIGSEGV);
        sigdelset(&sig_act.sa_mask, SIGFPE);
        sigdelset(&sig_act.sa_mask, SIGILL);
        sigdelset(&sig_act.sa_mask, SIGBUS);
        sigdelset(&sig_act.sa_mask, SIGTRAP);
    }

    ret = sigaction(SIGSEGV, &sig_act, &g_old_sigact[SIGSEGV]);
    if (0 != ret) abort();
    ret = sigaction(SIGFPE, &sig_act, &g_old_sigact[SIGFPE]);
    if (0 != ret) abort();
    ret = sigaction(SIGILL, &sig_act, &g_old_sigact[SIGILL]);
    if (0 != ret) abort();
    ret = sigaction(SIGBUS, &sig_act, &g_old_sigact[SIGBUS]);
    if (0 != ret) abort();
    ret = sigaction(SIGTRAP, &sig_act, &g_old_sigact[SIGTRAP]);
    if (0 != ret) abort();
}

//trust_thread is saved at stack for ocall.
#define enter_enclave __morestack

extern "C" int enter_enclave(const tcs_t *tcs, const long fn, const void *ocall_table, const void *ms, CTrustThread *trust_thread);


int do_ecall(const int fn, const void *ocall_table, const void *ms, CTrustThread *trust_thread)
{
    int status = SGX_ERROR_UNEXPECTED;

#ifdef SE_SIM
    CEnclave* enclave = trust_thread->get_enclave();
    //check if it is current pid, it is to simulate fork() scenario on HW
    sgx_enclave_id_t eid = enclave->get_enclave_id();
    if((pid_t)(eid >> 32) != getpid())
        return SGX_ERROR_ENCLAVE_LOST;
#endif

    tcs_t *tcs = trust_thread->get_tcs();

    status = enter_enclave(tcs, fn, ocall_table, ms, trust_thread);

    return status;
}

int do_ocall(const bridge_fn_t bridge, void *ms)
{
    int error = SGX_ERROR_UNEXPECTED;

    error = bridge(ms);

    return error;
}
