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
#include "thread_data.h"
#include "util.h"
#include "se_trace.h"
#include "se_memory.h"
#include <unistd.h>
#include <sys/ptrace.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdio.h>
#include <sys/user.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <elf.h>
#include <assert.h>

#include <signal.h>
#include <sys/wait.h>

//NOTE: Need align with thread_data_t in RTS.
#define ELF32_SSA_FS_OFFSET 0x34

#ifdef __x86_64__
#define SSA2USER_REG(to, from, name) to->r##name = from.r##name
#define USER_REG2SSA(to, from, name) to.r##name = from->r##name
#else
#define SSA2USER_REG(to, from, name) to->e##name = from.e##name
#define USER_REG2SSA(to, from, name) to.e##name = from->e##name
#endif

#define XSTATE_MAX_SIZE 832

typedef enum _direction_t
{
    FORWARD,
    BACKWARD
} direction_t;


typedef long int (* ptrace_t)(enum __ptrace_request request, pid_t pid,
                              void *addr, void *data);
typedef pid_t (*waitpid_t)(pid_t pid, int *status, int options);

static ptrace_t g_sys_ptrace = NULL;
static waitpid_t g_sys_waitpid = NULL;
__attribute__((constructor)) void init()
{
    g_sys_ptrace = (ptrace_t)dlsym(RTLD_NEXT, "ptrace");
    g_sys_waitpid = (waitpid_t)dlsym(RTLD_NEXT, "waitpid");
}

#ifdef SE_DEBUG
static void dump_ssa_gregs(ssa_gpr_t* gpr) __attribute__((unused));
void dump_ssa_gregs(ssa_gpr_t* gpr)
{
    SE_TRACE(SE_TRACE_DEBUG, "ssa generic registers:\n");
    SE_TRACE(SE_TRACE_DEBUG, "xbx = %#lx\t", gpr->REG(bx));
    SE_TRACE(SE_TRACE_DEBUG, "xcx = %#lx\t", gpr->REG(cx));
    SE_TRACE(SE_TRACE_DEBUG, "xdx = %#lx\t", gpr->REG(dx));
    SE_TRACE(SE_TRACE_DEBUG, "xsi = %#lx\t", gpr->REG(si));
    SE_TRACE(SE_TRACE_DEBUG, "xdi = %#lx\t", gpr->REG(di));
    SE_TRACE(SE_TRACE_DEBUG, "xbp = %#lx\t", gpr->REG(bp));
    SE_TRACE(SE_TRACE_DEBUG, "xax = %#lx\t", gpr->REG(ax));
    SE_TRACE(SE_TRACE_DEBUG, "xip = %#lx\t", gpr->REG(ip));
    SE_TRACE(SE_TRACE_DEBUG, "xflags = %#lx\t", gpr->REG(flags));
    SE_TRACE(SE_TRACE_DEBUG, "xsp = %#lx\t", gpr->REG(sp));
}

static void dump_regs(struct user_regs_struct *regs) __attribute__((unused));
void dump_regs(struct user_regs_struct *regs)
{
    SE_TRACE(SE_TRACE_DEBUG, "user regisers:\n");
    SE_TRACE(SE_TRACE_DEBUG, "xbx = %#x\t", regs->REG(bx));
    SE_TRACE(SE_TRACE_DEBUG, "xcx = %#x\t", regs->REG(cx));
    SE_TRACE(SE_TRACE_DEBUG, "xdx = %#x\t", regs->REG(dx));
    SE_TRACE(SE_TRACE_DEBUG, "xsi = %#x\t", regs->REG(si));
    SE_TRACE(SE_TRACE_DEBUG, "xdi = %#x\t", regs->REG(di));
    SE_TRACE(SE_TRACE_DEBUG, "xbp = %#x\t", regs->REG(bp));
    SE_TRACE(SE_TRACE_DEBUG, "xax = %#x\t", regs->REG(ax));
    SE_TRACE(SE_TRACE_DEBUG, "xip = %#x\t", regs->REG(ip));
    SE_TRACE(SE_TRACE_DEBUG, "xflags = %#x\t", regs->eflags);
    SE_TRACE(SE_TRACE_DEBUG, "xsp = %#x\t", regs->REG(sp));
}

#else

#define dump_ssa_gregs(gpr)
#define dump_regs(regs)

#endif

#ifdef __x86_64__
static int get_exec_class(pid_t pid)
{
    char filename[64];
    int fd = -1;
    unsigned char e_ident[EI_NIDENT];

    snprintf(filename, 64, "/proc/%d/exe", pid);
    fd = open(filename, O_RDONLY | O_LARGEFILE);
    if(fd == -1)
        return ELFCLASSNONE;
    if(-1 == read(fd, e_ident, EI_NIDENT))
    {
        close(fd);
        return ELFCLASSNONE;
    }

    close(fd);

    return e_ident[EI_CLASS];
}
#endif

static inline uint32_t get_ssa_frame_size(pid_t pid, thread_data_t* td)
{
    uint32_t ssa_frame_size = ROUND_TO_PAGE(td->xsave_size) >> SE_PAGE_SHIFT;
#ifdef __x86_64__
    //on x64, we may debug elf32 enclave, we need refer to different offset in td field.
    if(ELFCLASS32 == get_exec_class(pid))
    {
        ssa_frame_size = *GET_PTR(uint32_t, td, ELF32_SSA_FS_OFFSET);
    }
#else
    UNUSED(pid);
#endif

    //When debug trts, ssa_frame_size in TD is not initialized, so the value will be 0.
    //It is a limitation to debug trts. As work around, the default size is 1 page, so
    //we can debug enclave from the start of enclave_entry.
    if(0 == ssa_frame_size)
        ssa_frame_size = 1;

    return ssa_frame_size;
}

/*
 *This function get the position/offset with SSA
 * @pid, process id
 * @tcs_addr, TCS start address
 * @dir, calculate the position from start of SSA or from the end of SSA
 * @offset, offset from the start
 * @size, size of data from the postion that is going to be accessed
 * @pos, the result of postion that the function output
 * @return, TRUE on success, FALSE on fail. The result is copied to parameter pos
 * */
static int get_ssa_pos(pid_t pid, long tcs_addr, direction_t dir, long offset, long size, long *pos)
{
    tcs_t tcs;
    thread_data_t td;
    uint32_t ssa_frame_size = 0;
    long addr = 0;

    //read TCS;
    if(!se_read_process_mem(pid, (void *)tcs_addr, (void *)&tcs, 72, NULL))
        return FALSE;

    //Align with RTS. We assume TD is next to TCS
    long ssa_start = tcs_addr + TCS_SIZE;
    //ossa point to the start address of SSA, and fs/gs point to the start address of TD.
    long td_start = ssa_start - tcs.ossa + tcs.ofs_base;
    //Read thread data; On x64, sizeof(thread_data_t) of elf64 is larger than elf32,
    //so it won't miss any field if it is elf32 executable;
    if(!se_read_process_mem(pid, (void *)td_start, (void *)&td, sizeof(thread_data_t), NULL))
        return FALSE;
    ssa_frame_size = get_ssa_frame_size(pid, &td);
    //The request should not exceed ssa frame boundary.
    if((offset + size) > (long)ssa_frame_size * SE_PAGE_SIZE)
        return  FALSE;

    assert(tcs.cssa > 0);
    //If it is required to calculate from the start of SSA
    if(FORWARD == dir)
    {
        addr = ssa_start + (tcs.cssa - 1) * ssa_frame_size * SE_PAGE_SIZE + offset;

    }
    //If it is required to calculate from the end of SSA
    else if(BACKWARD == dir)
    {
        addr = ssa_start + tcs.cssa * ssa_frame_size * SE_PAGE_SIZE - offset;
    }
    else
        return FALSE;

    *pos = addr;
    return TRUE;
}

static inline int read_ssa(pid_t pid, long tcs_addr, direction_t dir, long offset, long size, void *buf)
{
    long addr = 0;

    if(!get_ssa_pos(pid, tcs_addr, dir, offset, size, &addr))
        return FALSE;

    //read the content of ssa
    if(!se_read_process_mem(pid, (void *)addr, buf, size, NULL))
        return FALSE;

    return TRUE;
}

static inline int write_ssa(pid_t pid, long tcs_addr, direction_t dir, long offset, long size, void *buf)
{
    long addr = 0;

    if(!get_ssa_pos(pid, tcs_addr, dir, offset, size, &addr))
        return FALSE;

    //write the content of ssa
    if(!se_write_process_mem(pid, (void *)addr, buf, size, NULL))
        return FALSE;

    return TRUE;
}

static inline int get_ssa_gpr(pid_t pid, long tcs_addr, ssa_gpr_t* gpr)
{
    //read general registers. ssa_gpr_t is elf32/elf64 independent.
    return read_ssa(pid, tcs_addr, BACKWARD, sizeof(ssa_gpr_t), sizeof(ssa_gpr_t), (void *)gpr);
}

static inline int set_ssa_gpr(pid_t pid, long tcs_addr, ssa_gpr_t* gpr)
{
    //read general registers. ssa_gpr_t is elf32/elf64 independent.
    return write_ssa(pid, tcs_addr, BACKWARD, sizeof(ssa_gpr_t), sizeof(ssa_gpr_t), (void *)gpr);
}

static inline int get_ssa_fpregs(pid_t pid, long tcs_addr, struct user_fpregs_struct* fpregs)
{
    return read_ssa(pid, tcs_addr, FORWARD, 0, sizeof(struct user_fpregs_struct), (void *)fpregs);
}

static inline int set_ssa_fpregs(pid_t pid, long tcs_addr, struct user_fpregs_struct* fpregs)
{
    return write_ssa(pid, tcs_addr, FORWARD, 0, sizeof(struct user_fpregs_struct), (void *)fpregs);
}

#if !defined(__x86_64__) && !defined(__x86_64)
static inline int get_ssa_fpxregs(pid_t pid, long tcs_addr, struct user_fpxregs_struct* fpxregs)
{
    return read_ssa(pid, tcs_addr, FORWARD, 0, sizeof(struct user_fpxregs_struct), (void *)fpxregs);
}

static inline int set_ssa_fpxregs(pid_t pid, long tcs_addr, struct user_fpxregs_struct* fpxregs)
{
    return write_ssa(pid, tcs_addr, FORWARD, 0, sizeof(struct user_fpxregs_struct), (void *)fpxregs);
}
#else
#define get_ssa_fpxregs get_ssa_fpregs
#define set_ssa_fpxregs set_ssa_fpregs
#define user_fpxregs_struct user_fpregs_struct
#endif

static inline int get_ssa_xstate(pid_t pid, long tcs_addr, int len, char *buf)
{
    return read_ssa(pid, tcs_addr, FORWARD, 0, len, buf);
}

static inline int set_ssa_xstate(pid_t pid, long tcs_addr, int len, char *buf)
{
    return write_ssa(pid, tcs_addr, FORWARD, 0, len, buf);
}

static int get_enclave_gregs(pid_t pid, struct user_regs_struct *regs, long tcs_addr)
{
    ssa_gpr_t gpr;
    if(!get_ssa_gpr(pid, tcs_addr, &gpr))
        return -1;

    //convert gpr to user_regs_struct.
    SSA2USER_REG(regs, gpr, bx);
    SSA2USER_REG(regs, gpr, cx);
    SSA2USER_REG(regs, gpr, dx);
    SSA2USER_REG(regs, gpr, si);
    SSA2USER_REG(regs, gpr, di);
    SSA2USER_REG(regs, gpr, bp);
    SSA2USER_REG(regs, gpr, ax);
    SSA2USER_REG(regs, gpr, ip);
    regs->eflags = gpr.REG(flags);
    SSA2USER_REG(regs, gpr, sp);
#ifdef __x86_64__
    SSA2USER_REG(regs, gpr, 8);
    SSA2USER_REG(regs, gpr, 9);
    SSA2USER_REG(regs, gpr, 10);
    SSA2USER_REG(regs, gpr, 11);
    SSA2USER_REG(regs, gpr, 12);
    SSA2USER_REG(regs, gpr, 13);
    SSA2USER_REG(regs, gpr, 14);
    SSA2USER_REG(regs, gpr, 15);
#endif
    return 0;
}

static int set_enclave_gregs(pid_t pid, struct user_regs_struct *regs, long tcs_addr)
{
    ssa_gpr_t gpr;

    //Since there is some field won't be written, we need save it first
    if(!get_ssa_gpr(pid, tcs_addr, &gpr))
        return -1;

    //convert gpr to user_regs_struct.
    USER_REG2SSA(gpr, regs, bx);
    USER_REG2SSA(gpr, regs, cx);
    USER_REG2SSA(gpr, regs, dx);
    USER_REG2SSA(gpr, regs, si);
    USER_REG2SSA(gpr, regs, di);
    USER_REG2SSA(gpr, regs, bp);
    USER_REG2SSA(gpr, regs, ax);
    USER_REG2SSA(gpr, regs, ip);
    gpr.REG(flags) = regs->eflags;
    USER_REG2SSA(gpr, regs, sp);
#ifdef __x86_64__
    USER_REG2SSA(gpr, regs, 8);
    USER_REG2SSA(gpr, regs, 9);
    USER_REG2SSA(gpr, regs, 10);
    USER_REG2SSA(gpr, regs, 11);
    USER_REG2SSA(gpr, regs, 12);
    USER_REG2SSA(gpr, regs, 13);
    USER_REG2SSA(gpr, regs, 14);
    USER_REG2SSA(gpr, regs, 15);
#endif

    //write general registers to ssa
    if(!set_ssa_gpr(pid, tcs_addr, &gpr))
        return -1;

    return 0;
}

static int is_eresume(pid_t pid, struct user_regs_struct *regs)
{
    unsigned int instr;

    if(!se_read_process_mem(pid, (void *)regs->REG(ip), (char *)&instr, sizeof(instr), NULL))
        return FALSE;
    if((ENCLU == (instr & 0xffffff))
            && (SE_ERESUME == regs->REG(ax)))
        return TRUE;
    return FALSE;
}

static long int get_regs(pid_t pid, void* addr, void* data)
{
    int ret = 0;

    if(!data)
        return -1;
    struct user_regs_struct *regs = (struct user_regs_struct *)data;
    if(-1 == (ret = g_sys_ptrace(PTRACE_GETREGS, pid, addr, data)))
        return -1;
    if(is_eresume(pid, regs))
    {
        //If it is ERESUME instruction, set the real register value
        if(-1 == get_enclave_gregs(pid, regs, regs->REG(bx)))
            return -1;
        else
        {
            return ret;
        }
    }

    return ret;
}

typedef struct _thread_status_t {
    pid_t pid;
    int inside_out;
    int singlestep;
    struct user_regs_struct aep_regs;
    struct _thread_status_t *next;
} thread_status_t;

static thread_status_t * g_thread_status = NULL;

/*
 *get the thread info by pid
 *return the status point if the thread info already cached
 *otherwise return NULL
 *
 */
static thread_status_t * get_thread_status(pid_t pid)
{
    thread_status_t * thread_status = g_thread_status;

    while(thread_status)
    {
        if(thread_status->pid == pid)
            break;
        else
            thread_status = thread_status->next;
    }

    return thread_status;
}

/*
 *add thread status cache
 *return the cache point
 */
static thread_status_t * add_thread_status(pid_t pid)
{
    thread_status_t * thread_status = (thread_status_t *)malloc(sizeof(thread_status_t));
    if (thread_status == NULL)
        return NULL;

    memset(thread_status, 0, sizeof(thread_status_t));

    thread_status->pid = pid;
    thread_status->next = g_thread_status;
    g_thread_status = thread_status;

    return thread_status;
}
/*
 *remove the thread status cache by pid
 *
 */
static void remove_thread_status(pid_t pid)
{
    thread_status_t * thread_status = g_thread_status;
    thread_status_t * previous_link = NULL;

    while(thread_status)
    {
        if(thread_status->pid == pid)
            break;
        else
        {
            previous_link = thread_status;
            thread_status = thread_status->next;
        }
    }

    if (thread_status != NULL)
    {
        if (previous_link == NULL)
        {
            g_thread_status = thread_status->next;
        } else {
            previous_link->next = thread_status->next;
        }

        free(thread_status);
    }
}

static long int set_regs(pid_t pid, void* addr, void* data)
{
    int ret = 0;
    struct user_regs_struct aep_regs;

    if(!data)
        return -1;
    if(-1 == g_sys_ptrace(PTRACE_GETREGS, pid, 0, (void*)&aep_regs))
        return -1;
    if(is_eresume(pid, &aep_regs))
    {
        struct user_regs_struct *regs = (struct user_regs_struct *)data;
        //get tcs address
        if(-1 == (ret = set_enclave_gregs(pid, regs, aep_regs.REG(bx))))
            return -1;
        else
            return ret;
    }
    else
    {
        return g_sys_ptrace(PTRACE_SETREGS, pid, addr, data);
    }
}


static long int get_fpregs(pid_t pid, void* addr, void* data, int extend)
{
    int ret = 0;

    if(!data)
        return -1;
    struct user_regs_struct regs;
    if(-1 == (ret = g_sys_ptrace(PTRACE_GETREGS, pid, 0, &regs)))
        return -1;
    if(is_eresume(pid, &regs))
    {
        if(extend)
            ret = get_ssa_fpxregs(pid, regs.REG(bx), (struct user_fpxregs_struct *)data);
        else
            ret = get_ssa_fpregs(pid, regs.REG(bx), (struct user_fpregs_struct *)data);
        if(ret)
            return 0;
        else
            return -1;
    }
    else
    {
        return g_sys_ptrace(PTRACE_GETFPREGS, pid, addr, data);
    }
}

static long int set_fpregs(pid_t pid, void* addr, void* data, int extend)
{
    int ret = 0;

    if(!data)
        return -1;
    struct user_regs_struct regs;
    if(-1 == (ret = g_sys_ptrace(PTRACE_GETREGS, pid, 0, &regs)))
        return -1;
    if(is_eresume(pid, &regs))
    {
        if(extend)
            ret = set_ssa_fpxregs(pid, regs.REG(bx), (struct user_fpxregs_struct *)data);
        else
            ret = set_ssa_fpregs(pid, regs.REG(bx), (struct user_fpregs_struct *)data);
        if(ret)
            return 0;
        else
            return -1;
    }
    else
    {
        return g_sys_ptrace(PTRACE_GETFPREGS, pid, addr, data);
    }
}

static long int get_regset(pid_t pid, void* addr, void* data)
{
    int ret = 0;
    unsigned long type = (unsigned long)addr;

    if(!data)
        return -1;
    struct user_regs_struct regs;
    if(-1 == (ret = g_sys_ptrace(PTRACE_GETREGS, pid, 0, &regs)))
        return -1;

    if(is_eresume(pid, &regs))
    {
        if(NT_X86_XSTATE != type)
        {
            SE_TRACE(SE_TRACE_WARNING, "unexpected type for PTRACE_GETREGSET\n");
            return -1;
        }
        struct iovec *iov = (struct iovec *)data;
        if(iov->iov_base && iov->iov_len
                && get_ssa_xstate(pid, regs.REG(bx), iov->iov_len, (char *)iov->iov_base))
        {
            return 0;
        }
        else
            return -1;
    }
    else
    {
        return g_sys_ptrace(PTRACE_GETREGSET, pid, addr, data);
    }
}

static long int set_regset(pid_t pid, void* addr, void* data)
{
    int ret = 0;
    unsigned long type = (unsigned long)addr;

    if(!data)
        return -1;
    struct user_regs_struct regs;
    if(-1 == (ret = g_sys_ptrace(PTRACE_GETREGS, pid, 0, &regs)))
        return -1;

    if(is_eresume(pid, &regs))
    {
        if(NT_X86_XSTATE != type)
        {
            SE_TRACE(SE_TRACE_WARNING, "unexpected type for PTRACE_SETREGSET\n");
            return -1;
        }
        struct iovec *iov = (struct iovec *)data;
        if(iov->iov_base && iov->iov_len
                && set_ssa_xstate(pid, regs.REG(bx), iov->iov_len, (char *)iov->iov_base))
        {
            return 0;
        }
        else
            return -1;
    }
    else
    {
        return g_sys_ptrace(PTRACE_SETREGSET, pid, addr, data);
    }
}

static long int do_singlestep(pid_t pid, void* addr, void* data)
{
    thread_status_t * thread_status = NULL;
    if ((thread_status = get_thread_status(pid)) == NULL)
        thread_status = add_thread_status(pid);

    if (thread_status != NULL)
	    thread_status->singlestep = 1;

    return g_sys_ptrace(PTRACE_SINGLESTEP, pid, addr, data);
}

long int ptrace (enum __ptrace_request __request, ...)
{
    pid_t pid;
    void *addr, *data;
    va_list ap;

    va_start(ap, __request);
    pid = va_arg(ap, pid_t);
    addr = va_arg(ap, void *);
    data = va_arg(ap, void *);
    va_end(ap);

    if(__request == PTRACE_GETREGS)
    {
        return get_regs(pid, addr, data);
    }
    else if(__request == PTRACE_SETREGS)
    {
        return set_regs(pid, addr, data);
    }
#if 0
    //some old system may require this command to get register
    else if(__request == PTRACE_PEEKUSER)
    {

    }
#endif
    else if(__request == PTRACE_GETFPREGS)
    {
        return get_fpregs(pid, addr, data, FALSE);
    }
    else if(__request == PTRACE_SETFPREGS)
    {
        return set_fpregs(pid, addr, data, FALSE);

    }
    else if(__request == PTRACE_GETFPXREGS)
    {
        return get_fpregs(pid, addr, data, TRUE);
    }
    else if(__request == PTRACE_SETFPXREGS)
    {
        return set_fpregs(pid, addr, data, TRUE);
    }

    //xstave for avx
    else if(__request == PTRACE_GETREGSET)
    {
        return get_regset(pid, addr, data);
    }
    else if(__request == PTRACE_SETREGSET)
    {
        return set_regset(pid, addr, data);
    }
    else if(__request == PTRACE_SINGLESTEP)
    {
        return do_singlestep(pid, addr, data);
    }
    //For other request just forward it to real ptrace call;
    return g_sys_ptrace(__request, pid, addr, data);
}

pid_t waitpid(pid_t pid, int *status, int options)
{
    pid_t ret_pid = g_sys_waitpid(pid, status, options);

    if (ret_pid == -1 || status == NULL)
        return ret_pid;

    if (WIFEXITED(*status) || WIFSIGNALED(*status))
    {
        remove_thread_status(ret_pid);
    }

    //if it is a TRAP, and inside enclave, fix the #BP info
    if(WIFSTOPPED(*status) &&
            WSTOPSIG(*status) == SIGTRAP)
    {
        struct user_regs_struct regs;
        thread_status_t * thread_status = get_thread_status(ret_pid);

        if(thread_status && thread_status->singlestep == 1)
        {
            thread_status->singlestep = 0;
        }
        else if(-1 == g_sys_ptrace(PTRACE_GETREGS, ret_pid, 0, &regs))
        {
            SE_TRACE(SE_TRACE_WARNING, "unexpected get context failed\n");
        }
        else if(is_eresume(ret_pid, &regs))
        {
            long tcs = regs.REG(bx);
            //If it is ERESUME instruction, set the real register value
            if(-1 != get_enclave_gregs(ret_pid, &regs, tcs))
            {
                uint8_t bp = 0;
                if(!se_read_process_mem(ret_pid, (void *)regs.REG(ip), (void *)&bp, 1, NULL))
                {
                    SE_TRACE(SE_TRACE_WARNING, "unexpected read memory failed\n");
                }
                else if (bp == 0xcc)
                {
                    regs.REG(ip)++;
                    if ( -1 == set_enclave_gregs(ret_pid, &regs, tcs))
                    {
                        SE_TRACE(SE_TRACE_WARNING, "unexpected set registers failed\n");
                    }
                }
            }
        }
    }

    return ret_pid;
}
