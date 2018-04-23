/* ========================================================================
 *
 * SAMPLE SOURCE CODE - SUBJECT TO THE TERMS OF END-USER LICENSE AGREEMENT
 * FOR INTEL(R) ADVISOR XE 2016.
 *
 * Copyright (c) 2009-2015 Intel Corporation. All rights reserved.
 *
 * THIS FILE IS PROVIDED "AS IS" WITH NO WARRANTIES, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT OF INTELLECTUAL
 * PROPERTY RIGHTS.
 *
 * ========================================================================
 */

/* This file defines macros and inline functions used by
 * the Intel(R) Advisor XE "Dependencies Modeling" and
 * "Suitability Modeling" analysis, and are in described
 * in the "Annotations" section of the help.
 *
 * Expansion Options
 *
 * There are several options you can used to control how advisor-annotate.h
 * is included.  To use these, define the option prior to including
 * advisor-annotate.h.  e.g.
 *  #define ANNOTATE_DECLARE
 *  #include "advisor-annotate.h"
 *
 * Controlling inclusion of windows.h
 *
 *  windows.h is included for declarations for LoadLibrary, GetProcSymbol,
 *  but this can have interactions with user code, such as conflicting
 *  definitions of types.  There are two general approaches to work around
 *  this if this triggers problems building your application:
 *
 *  1. Reduce the amount declared by windows.h by using the following: 
 *      #define NOMINMAX
 *      #define WIN32_LEAN_AND_MEAN
 *     prior to including advisor-annotate.h in your code.
 *     The first avoids problems with STL min/max in particular
 *     This is sufficient in some cases, and may be the easiest.
 *
 *  2. Use a declaration/definition approach, where all uses of advisor-annotate.h
 *     other than one, generate a set of declarations, and windows.h is only
 *     needed in a single implementation module.  In this model, all includes
 *     of advisor-annotate.h except one specify ANNOTATE_DECLARE, which causes
 *     advisor-annotate.h to declare an external routine, and not include
 *     windows.h.  A final include of advisor-annotate.h than specifies
 *     ANNOTATE_DEFINE, to actually define the global routine to resolve
 *     the external reference.  This one include is the only one that winds up
 *     using windows.h.  If necessary, this can be placed in a file by itself.
 *
 *     An example using this mechanism:
 * 
 *      ...
 *      // Some header(s) used in places in your system where you want
 *      // to be able to use annotations
 *      #define ANNOTATE_DECLARE
 *      #include "advisor-annotate.h"
 *      ...
 *         // annotation uses
 *         ANNOTATE_SITE_BEGIN(MySite1)
 *         ...
 *         ANNOTATE_SITE_END(MySite1)
 *      ...
 *
 *      ...
 *      // A single implementation file (.cpp/.cxx) causes windows.h
 *      // to be included, and the support routine to be defined as a
 *      // global routine called from the various annotation uses.
 *      #define ANNOTATE_DEFINE
 *      #include "advisor-annotate.h"
 *      ...
 *
 * Null expansion of annotations
 *
 *  Some people may find it useful to have no expansion for annotations,
 *  if you have a project that you want to build without any annotation
 *  effects at all.  (e.g. if you have a project where you want to have
 *  some annotations in a shared source pool, but only particular
 *  developers are actually building with the annotations enabled.)
 *  Defining ANNOTATE_EXPAND_NULL avoids declaring comdat routines,
 *  and avoids any textual expansion for annotation macros.
 */

#ifndef _ADVISOR_ANNOTATE_H_
#define _ADVISOR_ANNOTATE_H_

/* Version of the annotations.
 * The presence of this macro serves to idetify the annotation definition
 * file and the form of annotations.
 */
#define INTEL_ADVISOR_ANNOTATION_VERSION 1.0

#ifdef ANNOTATE_EXPAND_NULL

#define ANNOTATE_SITE_BEGIN(_SITE)
#define ANNOTATE_SITE_END(...)
#define ANNOTATE_TASK_BEGIN(_TASK)
#define ANNOTATE_TASK_END(...)
#define ANNOTATE_ITERATION_TASK(_TASK)
#define ANNOTATE_LOCK_ACQUIRE(_ADDR)
#define ANNOTATE_LOCK_RELEASE(_ADDR)
#define ANNOTATE_RECORD_ALLOCATION(_ADDR, _SIZE)
#define ANNOTATE_RECORD_DEALLOCATION(_ADDR)
#define ANNOTATE_INDUCTION_USES(_ADDR, _SIZE)
#define ANNOTATE_REDUCTION_USES(_ADDR, _SIZE)
#define ANNOTATE_OBSERVE_USES(_ADDR, _SIZE)
#define ANNOTATE_CLEAR_USES(_ADDR)
#define ANNOTATE_DISABLE_OBSERVATION_PUSH
#define ANNOTATE_DISABLE_OBSERVATION_POP
#define ANNOTATE_DISABLE_COLLECTION_PUSH
#define ANNOTATE_DISABLE_COLLECTION_POP
#define ANNOTATE_AGGREGATE_TASK(_COUNT)

#else /* ANNOTATE_EXPAND_NULL */

#if defined(WIN32) || defined(_WIN32)

#define ANNOTATEAPI __cdecl

#ifndef ANNOTATE_DECLARE
#include <windows.h>

typedef HMODULE lib_t;

#define __itt_get_proc(lib, name) GetProcAddress(lib, name)
#define __itt_load_lib(name)      LoadLibraryA(name)
#define __itt_unload_lib(handle)  FreeLibrary(handle)
#define __itt_system_error()      (int)GetLastError()
#endif /* ANNOTATE_DECLARE */

#else /* defined(WIN32) || defined(_WIN32) */

#if defined _M_IX86 || __i386__
#   define ANNOTATEAPI __attribute__ ((cdecl))
#else
#   define ANNOTATEAPI /* actual only on x86 platform */
#endif


#ifndef ANNOTATE_DECLARE
#include <pthread.h>
#include <dlfcn.h>
#include <errno.h>

typedef void* lib_t;

#define __itt_get_proc(lib, name) dlsym(lib, name)
#define __itt_load_lib(name)      dlopen(name, RTLD_LAZY)
#define __itt_unload_lib(handle)  dlclose(handle)
#define __itt_system_error()      errno
#endif /* ANNOTATE_DECLARE */

#endif /* defined(WIN32) || defined(_WIN32) */

#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifndef _ITTNOTIFY_H_ 

/* Handles for sites and tasks.
 */
typedef void* __itt_model_site;             /* handle for lexical site */
typedef void* __itt_model_site_instance;    /* handle for dynamic instance */
typedef void* __itt_model_task;             /* handle for lexical site */
typedef void* __itt_model_task_instance;    /* handle for dynamic instance */

typedef enum {
    __itt_model_disable_observation,
    __itt_model_disable_collection
} __itt_model_disable;

#endif /* _ITTNOTIFY_H_  */

/*** Use the routines in libittnotify.dll. ***/

/* Basic approach:
 * For the case of calling the dll, there is an __annotate_routine function
 * declared as a comdat in each compilation unit with annotations present.
 * That routine in turn has an internal static structure that is initialized
 * once to contain the address of functions occuring in libittnotify.dll.
 * Each time an annotation macro is invoked, that causes a call to the
 * __annotate_routine function to get addresses of the routines, followed
 * by calling the specific routine, provided the address is non-null.
 */

/* This set of macros generates calls that are part of application images,
 * which call the __itt_model_xxx routines in the dynamically loaded
 * libittnotify.dll.
 */
#ifndef _ITTNOTIFY_H_ 
#define ITT_NOTIFY_DECL(_text) _text
#else
#define ITT_NOTIFY_DECL(_text)
#endif

/* For C++, a static initialization is used */
#if defined(__cplusplus) && defined(WIN32)
#define _ANNOTATE_ROUTINES_ADDR __annotate_routines_s
#else
#define _ANNOTATE_ROUTINES_ADDR __annotate_routines_init( __annotate_routines() )
#endif /* __cplusplus */


#define _ANNOTATE_DECLARE_0(_BASENAME) \
typedef void (ANNOTATEAPI * __annotate_##_BASENAME##_t)(); \
static __inline void ANNOTATEAPI __annotate_##_BASENAME##_t_nop() { }; \
ITT_NOTIFY_DECL( extern void ANNOTATEAPI __itt_model_##_BASENAME(); )

#define _ANNOTATE_DECLARE_0_INT(_BASENAME) \
typedef int (ANNOTATEAPI * __annotate_##_BASENAME##_t)(); \
static __inline int ANNOTATEAPI __annotate_##_BASENAME##_t_nop() { return 0; }; \
ITT_NOTIFY_DECL( extern void ANNOTATEAPI __itt_model_##_BASENAME(); )

#define _ANNOTATE_CALL_0(_BASENAME) { _ANNOTATE_ROUTINES_ADDR->_BASENAME(); }

#define _ANNOTATE_DECLARE_1(_BASENAME, _P1TYPE) \
typedef void (ANNOTATEAPI * __annotate_##_BASENAME##_t)(_P1TYPE p1); \
static __inline void ANNOTATEAPI __annotate_##_BASENAME##_t_nop(_P1TYPE p1) { (void)p1; }; \
ITT_NOTIFY_DECL( extern void ANNOTATEAPI __itt_model_##_BASENAME(_P1TYPE p1); )

#define _ANNOTATE_CALL_1(_BASENAME, _P1) { _ANNOTATE_ROUTINES_ADDR->_BASENAME(_P1); }

#define _ANNOTATE_DECLARE_2(_BASENAME, _P1TYPE, _P2TYPE) \
typedef void (ANNOTATEAPI * __annotate_##_BASENAME##_t)(_P1TYPE p1, _P2TYPE p2); \
static __inline void ANNOTATEAPI __annotate_##_BASENAME##_t_nop(_P1TYPE p1, _P2TYPE p2) { (void)p1; (void)p2; }; \
ITT_NOTIFY_DECL( extern void ANNOTATEAPI __itt_model_##_BASENAME(_P1TYPE p1, _P2TYPE p2); )

#define _ANNOTATE_CALL_2(_BASENAME, _P1, _P2) { _ANNOTATE_ROUTINES_ADDR->_BASENAME((_P1), (_P2)); }

/*** Declare routines appropriately based on usage style ***/

/* Depending on above, this will either expand to comdats that are
 * used directly, or comdats that call routines in libittnotify.dll
 */
_ANNOTATE_DECLARE_1(site_beginA, const char *)
_ANNOTATE_DECLARE_0(site_end_2)
_ANNOTATE_DECLARE_1(task_beginA, const char *)
_ANNOTATE_DECLARE_0(task_end_2)
_ANNOTATE_DECLARE_1(iteration_taskA, const char *)
_ANNOTATE_DECLARE_1(lock_acquire_2, void *)
_ANNOTATE_DECLARE_1(lock_release_2, void *)
_ANNOTATE_DECLARE_2(record_allocation, void *, size_t)
_ANNOTATE_DECLARE_1(record_deallocation, void *)
_ANNOTATE_DECLARE_2(induction_uses, void *, size_t)
_ANNOTATE_DECLARE_2(reduction_uses, void *, size_t)
_ANNOTATE_DECLARE_2(observe_uses, void *, size_t)
_ANNOTATE_DECLARE_1(clear_uses, void *)
_ANNOTATE_DECLARE_1(disable_push, __itt_model_disable)
_ANNOTATE_DECLARE_0(disable_pop)
_ANNOTATE_DECLARE_1(aggregate_task, size_t)
_ANNOTATE_DECLARE_0_INT(is_collection_disabled)

/* All of the symbols potentially in the library
 */
struct __annotate_routines {
    volatile int                        initialized;
    __annotate_site_beginA_t            site_beginA;
    __annotate_site_end_2_t             site_end_2;
    __annotate_task_beginA_t            task_beginA;
    __annotate_task_end_2_t             task_end_2;
    __annotate_iteration_taskA_t        iteration_taskA;
    __annotate_lock_acquire_2_t         lock_acquire_2;
    __annotate_lock_release_2_t         lock_release_2;
    __annotate_record_allocation_t      record_allocation;
    __annotate_record_deallocation_t    record_deallocation;
    __annotate_induction_uses_t         induction_uses;
    __annotate_reduction_uses_t         reduction_uses;
    __annotate_observe_uses_t           observe_uses;
    __annotate_clear_uses_t             clear_uses;
    __annotate_disable_push_t           disable_push;
    __annotate_disable_pop_t            disable_pop;
    __annotate_aggregate_task_t         aggregate_task;
    __annotate_is_collection_disabled_t is_collection_disabled;
};

/* This comdat-ed routine means there is a single instance of the function pointer
 * structure per image
 */
static __inline struct __annotate_routines* __annotate_routines()
{
    static struct __annotate_routines __annotate_routines;
    return &__annotate_routines;
}

/* This routine is called to get the address of an initialized
 * set of function pointers for the annotation routines.
 */

#ifdef ANNOTATE_DECLARE
extern struct __annotate_routines* ANNOTATEAPI __annotate_routines_init(struct __annotate_routines* itt);
#else
#ifdef ANNOTATE_DEFINE
    /* */
#else
    static __inline 
#endif
struct __annotate_routines*
ANNOTATEAPI
__annotate_routines_init(struct __annotate_routines* itt) {

    if (itt->initialized) {
        return itt;
    } else {

        /* Initialized by first invocation
         * This assumes that the code here can be executed successfully
         * by multiple threads, should that ever happen.
         */
        int do_disable_pop = 0;
#if !(defined(WIN32) || defined(_WIN32))
        char* lib_name = NULL;
#endif

        lib_t itt_notify = 0;

#if defined(WIN32) || defined(_WIN32)
        itt_notify = __itt_load_lib("libittnotify.dll");
#else
        if (sizeof(void*) > 4) {
            lib_name = getenv("INTEL_LIBITTNOTIFY64");
        } else {
            lib_name = getenv("INTEL_LIBITTNOTIFY32");
        }
        if (lib_name) {
            itt_notify = __itt_load_lib(lib_name);
        }
#endif
        if (itt_notify != NULL) {

            /* The static variables initialized and itt are reported as race conditions
             * or inconsistent lock usage by Dependencies Modeling in some obscure cases
             * involving multiple dlls.  Ignoring this initialization phase gets rid of 
             * this problem. 
             */
            __annotate_disable_push_t disable_push;
            __annotate_is_collection_disabled_t is_collection_disabled;
            disable_push            = (__annotate_disable_push_t)       __itt_get_proc(itt_notify, "__itt_model_disable_push");
            is_collection_disabled  = (__annotate_is_collection_disabled_t) __itt_get_proc(itt_notify, "__itt_model_is_collection_disabled");
            if (disable_push) {
                if ( ! (is_collection_disabled && is_collection_disabled()) )
                {
                    // disable collection only if it is not disabled already (for example, started paused)
                    disable_push(__itt_model_disable_observation);
                    do_disable_pop = 1;
                }
            }
            itt->site_beginA         = (__annotate_site_beginA_t)        __itt_get_proc(itt_notify, "__itt_model_site_beginA");
            itt->site_end_2          = (__annotate_site_end_2_t)         __itt_get_proc(itt_notify, "__itt_model_site_end_2");
            itt->task_beginA         = (__annotate_task_beginA_t)        __itt_get_proc(itt_notify, "__itt_model_task_beginA");
            itt->task_end_2          = (__annotate_task_end_2_t)         __itt_get_proc(itt_notify, "__itt_model_task_end_2");
            itt->iteration_taskA     = (__annotate_iteration_taskA_t)    __itt_get_proc(itt_notify, "__itt_model_iteration_taskA");
            itt->lock_acquire_2      = (__annotate_lock_acquire_2_t)     __itt_get_proc(itt_notify, "__itt_model_lock_acquire_2");
            itt->lock_release_2      = (__annotate_lock_release_2_t)     __itt_get_proc(itt_notify, "__itt_model_lock_release_2");
            itt->record_allocation   = (__annotate_record_allocation_t)  __itt_get_proc(itt_notify, "__itt_model_record_allocation");
            itt->record_deallocation = (__annotate_record_deallocation_t)__itt_get_proc(itt_notify, "__itt_model_record_deallocation");
            itt->induction_uses      = (__annotate_induction_uses_t)     __itt_get_proc(itt_notify, "__itt_model_induction_uses");
            itt->reduction_uses      = (__annotate_reduction_uses_t)     __itt_get_proc(itt_notify, "__itt_model_reduction_uses");
            itt->observe_uses        = (__annotate_observe_uses_t)       __itt_get_proc(itt_notify, "__itt_model_observe_uses");
            itt->clear_uses          = (__annotate_clear_uses_t)         __itt_get_proc(itt_notify, "__itt_model_clear_uses");
            itt->disable_push        = disable_push;
            itt->disable_pop         = (__annotate_disable_pop_t)        __itt_get_proc(itt_notify, "__itt_model_disable_pop");
            itt->aggregate_task      = (__annotate_aggregate_task_t)     __itt_get_proc(itt_notify, "__itt_model_aggregate_task");
            itt->is_collection_disabled = is_collection_disabled;
        }
        /* No-op routine for any that didn't get resolved */
        if (!itt->site_beginA)         itt->site_beginA =       __annotate_site_beginA_t_nop;
        if (!itt->site_end_2)          itt->site_end_2 =        __annotate_site_end_2_t_nop;
        if (!itt->task_beginA)         itt->task_beginA =       __annotate_task_beginA_t_nop;
        if (!itt->task_end_2)          itt->task_end_2 =        __annotate_task_end_2_t_nop;
        if (!itt->iteration_taskA)     itt->iteration_taskA =   __annotate_iteration_taskA_t_nop;
        if (!itt->lock_acquire_2)      itt->lock_acquire_2 =    __annotate_lock_acquire_2_t_nop;
        if (!itt->lock_release_2)      itt->lock_release_2 =    __annotate_lock_release_2_t_nop;
        if (!itt->record_allocation)   itt->record_allocation = __annotate_record_allocation_t_nop;
        if (!itt->record_deallocation) itt->record_deallocation=__annotate_record_deallocation_t_nop;
        if (!itt->induction_uses)      itt->induction_uses =    __annotate_induction_uses_t_nop;
        if (!itt->reduction_uses)      itt->reduction_uses =    __annotate_reduction_uses_t_nop;
        if (!itt->observe_uses)        itt->observe_uses =      __annotate_observe_uses_t_nop;
        if (!itt->clear_uses)          itt->clear_uses =        __annotate_clear_uses_t_nop;
        if (!itt->disable_push)        itt->disable_push =      __annotate_disable_push_t_nop;
        if (!itt->disable_pop)         itt->disable_pop =       __annotate_disable_pop_t_nop;
        if (!itt->aggregate_task)      itt->aggregate_task =    __annotate_aggregate_task_t_nop;
        if (!itt->is_collection_disabled) itt->is_collection_disabled = __annotate_is_collection_disabled_t_nop;

        itt->initialized = 1;

        if (do_disable_pop) {
            itt->disable_pop();
        }
    }
    return itt;
}
#endif /* ANNOTATE_DECLARE */

/* For C++ only, use a class to force initialization */

#if defined(__cplusplus) && defined(WIN32)
/* Force one-shot initialization so individual calls don't need it */
static struct __annotate_routines* __annotate_routines_s = __annotate_routines_init( __annotate_routines() );
#endif

/* For C++, allow the Annotate::SiteBegin(x) form.  For Windows CLR, this is the default
 * expansion for the macros (with no-inline) to get the best call stacks in the tools. */
#if defined(__cplusplus)
/* Ensure this code is managed and non-inlinable */
#if defined(WIN32) && defined(__CLR_VER)
#pragma managed(push, on)
#define ANNOTATE_CLR_NOINLINE __declspec(noinline)
#else
#define ANNOTATE_CLR_NOINLINE
#endif
class Annotate {
public:
    static ANNOTATE_CLR_NOINLINE void SiteBegin(const char* site)      { _ANNOTATE_ROUTINES_ADDR->site_beginA(site); }
    static ANNOTATE_CLR_NOINLINE void SiteEnd()                        { _ANNOTATE_ROUTINES_ADDR->site_end_2(); }
    static ANNOTATE_CLR_NOINLINE void TaskBegin(const char* task)      { _ANNOTATE_ROUTINES_ADDR->task_beginA(task); }
    static ANNOTATE_CLR_NOINLINE void TaskEnd()                        { _ANNOTATE_ROUTINES_ADDR->task_end_2(); }
    static ANNOTATE_CLR_NOINLINE void IterationTask(const char* task)  { _ANNOTATE_ROUTINES_ADDR->iteration_taskA(task); }
    static ANNOTATE_CLR_NOINLINE void LockAcquire(void* lockId)        { _ANNOTATE_ROUTINES_ADDR->lock_acquire_2(lockId); }
    static ANNOTATE_CLR_NOINLINE void LockRelease(void* lockId)        { _ANNOTATE_ROUTINES_ADDR->lock_release_2(lockId); }
    static ANNOTATE_CLR_NOINLINE void RecordAllocation(void *p, size_t s) { _ANNOTATE_ROUTINES_ADDR->record_allocation(p, s); }
    static ANNOTATE_CLR_NOINLINE void RecordDeallocation(void *p)      { _ANNOTATE_ROUTINES_ADDR->record_deallocation(p); }
    static ANNOTATE_CLR_NOINLINE void InductionUses(void *p, size_t s) { _ANNOTATE_ROUTINES_ADDR->induction_uses(p, s); }
    static ANNOTATE_CLR_NOINLINE void ReductionUses(void *p, size_t s) { _ANNOTATE_ROUTINES_ADDR->reduction_uses(p, s); }
    static ANNOTATE_CLR_NOINLINE void ObserveUses(void *p, size_t s)   { _ANNOTATE_ROUTINES_ADDR->observe_uses(p, s); }
    static ANNOTATE_CLR_NOINLINE void ClearUses(void *p)               { _ANNOTATE_ROUTINES_ADDR->clear_uses(p); }
    static ANNOTATE_CLR_NOINLINE void DisablePush(__itt_model_disable d) { _ANNOTATE_ROUTINES_ADDR->disable_push(d); }
    static ANNOTATE_CLR_NOINLINE void DisablePop()                     { _ANNOTATE_ROUTINES_ADDR->disable_pop(); }
    static ANNOTATE_CLR_NOINLINE void AggregateTask(size_t c)          { _ANNOTATE_ROUTINES_ADDR->aggregate_task(c); }
};
#if defined(WIN32) && defined(__CLR_VER)
#pragma managed(pop)
#endif
#undef ANNOTATE_CLR_NOINLINE
#endif

#if defined(__cplusplus) && defined(WIN32) && defined(__CLR_VER)

#define ANNOTATE_SITE_BEGIN(_SITE) Annotate::SiteBegin(#_SITE)
#define ANNOTATE_SITE_END(...) Annotate::SiteEnd()
#define ANNOTATE_TASK_BEGIN(_TASK) Annotate::TaskBegin(#_TASK)
#define ANNOTATE_TASK_END(...) Annotate::TaskEnd()
#define ANNOTATE_ITERATION_TASK(_TASK) Annotate::IterationTask(#_TASK)
#define ANNOTATE_LOCK_ACQUIRE(_ADDR) Annotate::LockAcquire(_ADDR)
#define ANNOTATE_LOCK_RELEASE(_ADDR) Annotate::LockRelease(_ADDR)
#define ANNOTATE_RECORD_ALLOCATION(_ADDR, _SIZE) Annotate::RecordAllocation((_ADDR), (_SIZE))
#define ANNOTATE_RECORD_DEALLOCATION(_ADDR) Annotate::RecordDeallocation(_ADDR)
#define ANNOTATE_INDUCTION_USES(_ADDR, _SIZE) Annotate::InductionUses((_ADDR), (_SIZE))
#define ANNOTATE_REDUCTION_USES(_ADDR, _SIZE) Annotate::ReductionUses((_ADDR), (_SIZE))
#define ANNOTATE_OBSERVE_USES(_ADDR, _SIZE) Annotate::ObserveUses((_ADDR), (_SIZE))
#define ANNOTATE_CLEAR_USES(_ADDR) Annotate::ClearUses(_ADDR)
#define ANNOTATE_DISABLE_OBSERVATION_PUSH Annotate::DisablePush(itt_model_disable_observation)
#define ANNOTATE_DISABLE_OBSERVATION_POP Annotate::DisablePop()
#define ANNOTATE_DISABLE_COLLECTION_PUSH Annotate::DisablePush(__itt_model_disable_collection)
#define ANNOTATE_DISABLE_COLLECTION_POP Annotate::DisablePop()
#define ANNOTATE_AGGREGATE_TASK(_COUNT) Annotate::AggregateTask(_COUNT)

#else

/* Mark the start of a site (region) to be analyzed by the tool */
#define ANNOTATE_SITE_BEGIN(_SITE) _ANNOTATE_CALL_1(site_beginA, #_SITE)

/* Mark the end of a site (region) to be analyzed by the tool and
 * indicate a WaitForAll task synchronization */
#define ANNOTATE_SITE_END(...) _ANNOTATE_CALL_0(site_end_2)

/* Mark the beginning of a region of code that constitutes a task */
#define ANNOTATE_TASK_BEGIN(_TASK) _ANNOTATE_CALL_1(task_beginA, #_TASK)

/* Mark the end of a region of code that constitutes a task */
#define ANNOTATE_TASK_END(...) _ANNOTATE_CALL_0(task_end_2)

/* Mark the break between one task and the next task (a "split" description model
 * rather than a "begin/end" description model. */
#define ANNOTATE_ITERATION_TASK(_TASK) _ANNOTATE_CALL_1(iteration_taskA, #_TASK)

/* Acquire a lock identified by lockId */
#define ANNOTATE_LOCK_ACQUIRE(_ADDR) _ANNOTATE_CALL_1(lock_acquire_2, (_ADDR))

/* Release a lock identified by lockId */
#define ANNOTATE_LOCK_RELEASE(_ADDR) _ANNOTATE_CALL_1(lock_release_2, (_ADDR))

/* Record user allocation of memory */
#define ANNOTATE_RECORD_ALLOCATION(_ADDR, _SIZE) _ANNOTATE_CALL_2(record_allocation, (_ADDR), (_SIZE))

/* Record user deallocation of memory */
#define ANNOTATE_RECORD_DEALLOCATION(_ADDR) _ANNOTATE_CALL_1(record_deallocation, (_ADDR))

/* Denote storage as an inductive value */
#define ANNOTATE_INDUCTION_USES(_ADDR, _SIZE) _ANNOTATE_CALL_2(induction_uses, (_ADDR), (_SIZE))

/* Denote storage as a reduction */
#define ANNOTATE_REDUCTION_USES(_ADDR, _SIZE) _ANNOTATE_CALL_2(reduction_uses, (_ADDR), (_SIZE))

/* Record all observations of uses */
#define ANNOTATE_OBSERVE_USES(_ADDR, _SIZE) _ANNOTATE_CALL_2(observe_uses, (_ADDR), (_SIZE))

/* Clear handling of values */
#define ANNOTATE_CLEAR_USES(_ADDR) _ANNOTATE_CALL_1(clear_uses, (_ADDR))

/* Push disable of observations */
#define ANNOTATE_DISABLE_OBSERVATION_PUSH _ANNOTATE_CALL_1(disable_push, __itt_model_disable_observation)

/* Pop disable of observations */
#define ANNOTATE_DISABLE_OBSERVATION_POP _ANNOTATE_CALL_0(disable_pop)

/* Push disable of collection */
#define ANNOTATE_DISABLE_COLLECTION_PUSH _ANNOTATE_CALL_1(disable_push, __itt_model_disable_collection)

/* Pop disable of collection */
#define ANNOTATE_DISABLE_COLLECTION_POP _ANNOTATE_CALL_0(disable_pop)

/* Task aggregation */
#define ANNOTATE_AGGREGATE_TASK(_COUNT) _ANNOTATE_CALL_1(aggregate_task, (_COUNT))

#endif

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* ANNOTATE_EXPAND_NULL */

#endif /* _ADVISOR_ANNOTATE_H_ */
