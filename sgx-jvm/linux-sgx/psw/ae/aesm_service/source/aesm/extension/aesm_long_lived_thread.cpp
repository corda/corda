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


#include "aesm_long_lived_thread.h"
#include "pve_logic.h"
#include "pse_op_logic.h"
#include "platform_info_logic.h"
#include "oal/internal_log.h"
#include "se_time.h"
#include "se_wrapper.h"
#include <time.h>
#include <assert.h>
#include <list>
#include "LEClass.h"

enum _thread_state
{
    ths_idle,
    ths_busy,
    ths_stop//The thread is to be stopped and no new job will be accepted
};

enum _io_cache_state
{
    ioc_idle, //thread has been finished
    ioc_busy, //thread not finished yet
    ioc_stop  //thread stop required
};

#define MAX_OUTPUT_CACHE 50
#define THREAD_INFINITE_TICK_COUNT 0xFFFFFFFFFFFFFFFFLL
class ThreadStatus;
class BaseThreadIOCache;
typedef ae_error_t (*long_lived_thread_func_t)(BaseThreadIOCache *cache);

//Base class for cached data of each thread to fork
class BaseThreadIOCache:private Uncopyable{
    time_t timeout; //The data will timeout after the time if the state is not busy
    int ref_count; //ref_count is used to track how many threads are currently referencing the data
    _io_cache_state status;
    //handle of the thread, some thread will be waited by other threads so that we could not 
    //   free the handle until all other threads have got notification that the thread is terminated
    aesm_thread_t thread_handle;
    friend class ThreadStatus;
protected:
    ae_error_t ae_ret;
    BaseThreadIOCache():ref_count(0),status(ioc_busy){
        timeout=0;
        thread_handle=NULL;
        ae_ret = AE_FAILURE;
    }
    virtual ThreadStatus& get_thread()=0;
public:
    virtual ae_error_t entry(void)=0;
    virtual bool operator==(const BaseThreadIOCache& oc)const=0;
    ae_error_t start(BaseThreadIOCache *&out_ioc, uint32_t timeout=THREAD_TIMEOUT);
    void deref(void);
    void set_status_finish();
public:
    virtual ~BaseThreadIOCache(){}
};

class ThreadStatus: private Uncopyable
{
private:
    AESMLogicMutex thread_mutex;
    _thread_state thread_state;
    uint64_t    status_clock;
    BaseThreadIOCache *cur_iocache;
    std::list<BaseThreadIOCache *>output_cache;
protected:
    friend class BaseThreadIOCache;
    //function to look up cached output, there will be no real thread associated with the input ioc
    //If a match is found the input parameter will be free automatically and the matched value is returned
    //return true if a thread will be forked for the out_ioc
    bool find_or_insert_iocache(BaseThreadIOCache* ioc, BaseThreadIOCache *&out_ioc)
    {
        AESMLogicLock locker(thread_mutex);
        std::list<BaseThreadIOCache *>::reverse_iterator it;
        out_ioc=NULL;
        if(thread_state == ths_stop){
            AESM_DBG_TRACE("thread %p has been stopped and ioc %p not inserted", this,ioc);
            delete ioc;
            return false;//never visit any item after thread is stopped
        }
        time_t cur=time(NULL);
        AESM_DBG_TRACE("cache size %d",(int)output_cache.size());
        BaseThreadIOCache *remove_candidate = NULL;
        for(it=output_cache.rbegin();it!=output_cache.rend();++it){//visit the cache in reverse order so that the newest item will be visited firstly
            BaseThreadIOCache *pioc=*it;
            if((pioc->status==ioc_idle)&&(pioc->timeout<cur)){
                if(pioc->ref_count==0&&remove_candidate==NULL){
                    remove_candidate = pioc;
                }
                continue;//value timeout
            }
            if(*pioc==*ioc){//matched value find
                pioc->ref_count++;//reference it
                AESM_DBG_TRACE("IOC %p matching input IOC %p (ref_count:%d,status:%d,timeout:%d) in thread %p",pioc, ioc,(int)pioc->ref_count,(int)pioc->status, (int)pioc->timeout, this);
                out_ioc= pioc;
                delete ioc;
                return false;
            }
        }
        if(thread_state == ths_busy){//It is not permitted to insert in busy status
            AESM_DBG_TRACE("thread busy when trying insert input ioc %p",ioc);
            delete ioc;
            return false;
        }
        if(remove_candidate!=NULL){
            output_cache.remove(remove_candidate);
            delete remove_candidate;
        }
        if(output_cache.size()>=MAX_OUTPUT_CACHE){
            std::list<BaseThreadIOCache *>::iterator fit;
            bool erased=false;
            for(fit = output_cache.begin(); fit!=output_cache.end();++fit){
                BaseThreadIOCache *pioc=*fit;
                if(pioc->ref_count==0){//find a not timeout item to remove
                    assert(pioc->status==ioc_idle);
                    AESM_DBG_TRACE("erase idle ioc %p", pioc);
                    output_cache.erase(fit);
                    erased = true;
                    AESM_DBG_TRACE("thread %p cache size %d",this, output_cache.size());
                    delete pioc;
                    break;
                }
            }
            if(!erased){//no item could be removed
                AESM_DBG_TRACE("no free ioc found and cannot insert ioc %p",ioc);
                delete ioc;
                return false;//similar as busy status
            }
        }
        output_cache.push_back(ioc);
        out_ioc = cur_iocache = ioc;
        cur_iocache->ref_count=2;//initialize to be refenced by parent thread and the thread itself
        thread_state = ths_busy;//mark thread to be busy that the thread to be started
        AESM_DBG_TRACE("successfully add ioc %p (status=%d,timeout=%d) into thread %p",out_ioc, (int)out_ioc->status, (int)out_ioc->timeout, this);
        return true;
    }

public:
    ThreadStatus():output_cache()
    {
        thread_state = ths_idle;
        status_clock = 0;
        cur_iocache = NULL;
    }
    void set_status_finish(BaseThreadIOCache* ioc);//only called at the end of aesm_long_lived_thread_entry
    void deref(BaseThreadIOCache* iocache);
    ae_error_t wait_iocache_timeout(BaseThreadIOCache* ioc, uint64_t stop_tick_count);

    //create thread and wait at most 'timeout' for the thread to be finished
    // It will first look up whether there is a previous run with same input before starting the thread
    // we should not delete ioc after calling to this function
    ae_error_t set_thread_start(BaseThreadIOCache* ioc,  BaseThreadIOCache *&out_ioc, uint32_t timeout=THREAD_TIMEOUT);

    void stop_thread(uint64_t stop_milli_second);//We need wait for thread to be terminated and all thread_handle in list to be closed

    ~ThreadStatus(){stop_thread(THREAD_INFINITE_TICK_COUNT);}//ThreadStatus instance should be global object. Otherwise, it is possible that the object is destroyed before a thread waiting for and IOCache got notified and causing exception

    ae_error_t wait_for_cur_thread(uint64_t millisecond);

    //function to query whether current thread is idle,
    //if it is idle, return true and reset clock to current clock value
    bool query_status_and_reset_clock(void);
};

ae_error_t BaseThreadIOCache::start(BaseThreadIOCache *&out_ioc, uint32_t timeout_value)
{
    return get_thread().set_thread_start(this, out_ioc, timeout_value);
}

void BaseThreadIOCache::deref(void)
{
    get_thread().deref(this);
}

void BaseThreadIOCache::set_status_finish(void)
{
    get_thread().set_status_finish(this);
}

//This is thread entry wrapper for all threads
static ae_error_t aesm_long_lived_thread_entry(aesm_thread_arg_type_t arg)
{
    BaseThreadIOCache *cache=(BaseThreadIOCache *)arg;
    ae_error_t ae_err = cache->entry();
    cache->set_status_finish();
    return ae_err;
}

void ThreadStatus::stop_thread(uint64_t stop_tick_count)
{
    //change state to stop
    thread_mutex.lock();
    thread_state = ths_stop;
    
    do{
        std::list<BaseThreadIOCache *>::iterator it;
        for(it=output_cache.begin(); it!=output_cache.end();++it){
           BaseThreadIOCache *p=*it;
           if(p->status != ioc_stop){//It has not been processed
               p->status = ioc_stop;
               break;
           }
        }
        if(it!=output_cache.end()){//found item to stop
           BaseThreadIOCache *p=*it;
           p->ref_count++;
           thread_mutex.unlock();
           wait_iocache_timeout(p, stop_tick_count);
           thread_mutex.lock();
        }else{
            break;
        }
    }while(1);

    thread_mutex.unlock();
    //This function should only be called at AESM exit
    //Leave memory leak here is OK and all pointer to BaseThreadIOCache will not be released
}

ae_error_t ThreadStatus::wait_for_cur_thread(uint64_t millisecond)
{
    BaseThreadIOCache *ioc=NULL;
    uint64_t stop_tick_count;
    if(millisecond == AESM_THREAD_INFINITE){
        stop_tick_count = THREAD_INFINITE_TICK_COUNT;
    }else{
        stop_tick_count = se_get_tick_count() + (millisecond*se_get_tick_count_freq()+500)/1000;
    }
    thread_mutex.lock();
    if(cur_iocache!=NULL){
        ioc = cur_iocache;
        ioc->ref_count++;
    }
    thread_mutex.unlock();
    if(ioc!=NULL){
        return wait_iocache_timeout(ioc, stop_tick_count);
    }
    return AE_SUCCESS;
}

ae_error_t ThreadStatus::wait_iocache_timeout(BaseThreadIOCache* ioc, uint64_t stop_tick_count)
{
    ae_error_t ae_ret=AE_SUCCESS;
    uint64_t cur_tick_count = se_get_tick_count();
    uint64_t freq = se_get_tick_count_freq();
    bool need_wait=false;
    aesm_thread_t handle=NULL;
    thread_mutex.lock();
    if(ioc->thread_handle!=NULL&&(cur_tick_count<stop_tick_count||stop_tick_count==THREAD_INFINITE_TICK_COUNT)){
        AESM_DBG_TRACE("wait for busy ioc %p(refcount=%d)",ioc,ioc->ref_count);
        need_wait = true;
        handle = ioc->thread_handle;
    }
    thread_mutex.unlock();
    if(need_wait){
        unsigned long diff_time;
        if(stop_tick_count == THREAD_INFINITE_TICK_COUNT){
            diff_time = AESM_THREAD_INFINITE;
        }else{
            double wtime=(double)(stop_tick_count-cur_tick_count)*1000.0/(double)freq;
            diff_time = (unsigned long)(wtime+0.5);
        }
        ae_ret= aesm_wait_thread(handle, &ae_ret, diff_time);
    }
    deref(ioc);
    return ae_ret;
}

void ThreadStatus::deref(BaseThreadIOCache *ioc)
{
    aesm_thread_t handle = NULL;
    time_t cur=time(NULL);
    {
        AESMLogicLock locker(thread_mutex);
        AESM_DBG_TRACE("deref ioc %p (ref_count=%d,status=%d,timeout=%d) of thread %p",ioc,(int)ioc->ref_count,(int)ioc->status,(int)ioc->timeout, this);
        --ioc->ref_count;
        if(ioc->ref_count == 0){//try free the thread handle now
            handle = ioc->thread_handle;
            ioc->thread_handle = NULL;
            if(ioc->status == ioc_busy){
                ioc->status = ioc_idle;
            }
            AESM_DBG_TRACE("free thread handle for ioc %p",ioc);
        }
        if(ioc->ref_count==0 &&(ioc->status==ioc_stop||ioc->timeout<cur)){
            AESM_DBG_TRACE("free ioc %p",ioc);
            output_cache.remove(ioc);
            AESM_DBG_TRACE("thread %p cache's size is %d",this, (int)output_cache.size());
            delete ioc;
        }
    }
    if(handle!=NULL){
        aesm_free_thread(handle);
    }
}

ae_error_t ThreadStatus::set_thread_start(BaseThreadIOCache* ioc, BaseThreadIOCache *&out_ioc, uint32_t timeout)
{
    ae_error_t ae_ret = AE_SUCCESS;
    ae_error_t ret = AE_FAILURE;
    out_ioc=NULL;
    bool fork_required = find_or_insert_iocache(ioc, out_ioc);
    if(fork_required){
        ae_ret = aesm_create_thread(aesm_long_lived_thread_entry, (aesm_thread_arg_type_t)out_ioc, &out_ioc->thread_handle);
        if (ae_ret != AE_SUCCESS)
        {
            AESM_DBG_TRACE("fail to create thread for ioc %p",out_ioc);
            AESMLogicLock locker(thread_mutex);
            thread_state = ths_idle;
            out_ioc->status = ioc_idle;//set to finished status
            cur_iocache = NULL;
            deref(out_ioc);
            return ae_ret;
        }else{
            AESM_DBG_TRACE("succ create thread %p for ioc %p",this, out_ioc);
        }
    }

    if(out_ioc == NULL){
        AESM_DBG_TRACE("no ioc created for input ioc %p in thread %p",ioc, this);
        return OAL_THREAD_TIMEOUT_ERROR;
    }

    {//check whether thread has been finished
        AESMLogicLock locker(thread_mutex);
        if(out_ioc->status!=ioc_busy){//job is done
            AESM_DBG_TRACE("job done for ioc %p (status=%d,timeout=%d,ref_count=%d) in thread %p",out_ioc, (int)out_ioc->status,(int)out_ioc->timeout,(int)out_ioc->ref_count,this);
            return AE_SUCCESS;
        }
    }

    if(timeout >= AESM_THREAD_INFINITE ){
        ae_ret = aesm_join_thread(out_ioc->thread_handle, &ret);
    }else{
        uint64_t now = se_get_tick_count();
        double timediff = static_cast<double>(timeout) - (static_cast<double>(now - status_clock))/static_cast<double>(se_get_tick_count_freq()) *1000;
        if (timediff <= 0.0) {
            AESM_DBG_ERROR("long flow thread timeout");
            return OAL_THREAD_TIMEOUT_ERROR;
        }
        else{
            AESM_DBG_TRACE("timeout:%u,timediff: %f", timeout,timediff);
            ae_ret = aesm_wait_thread(out_ioc->thread_handle, &ret, (unsigned long)timediff); 
        }
    }
    AESM_DBG_TRACE("wait for ioc %p (status=%d,timeout=%d,ref_count=%d) result:%d",out_ioc,(int)out_ioc->status,(int)out_ioc->timeout,(int)out_ioc->ref_count, ae_ret);
    return ae_ret;
};

#define TIMEOUT_SHORT_TIME    60 
#define TIMEOUT_FOR_A_WHILE   (5*60)
#define TIMEOUT_LONG_TIME     (3600*24) //at most once every day
static time_t get_timeout_via_ae_error(ae_error_t ae)
{
    time_t cur=time(NULL);
    switch(ae){
    case AE_SUCCESS:
    case OAL_PROXY_SETTING_ASSIST:
    case OAL_NETWORK_RESEND_REQUIRED:
        return cur-1;//always timeout, the error code will never be reused
    case PVE_INTEGRITY_CHECK_ERROR:
    case PSE_OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR:
    case AESM_PSDA_LT_SESSION_INTEGRITY_ERROR:
    case OAL_NETWORK_UNAVAILABLE_ERROR:
    case OAL_NETWORK_BUSY:
    case PVE_SERVER_BUSY_ERROR:
        return cur+TIMEOUT_SHORT_TIME; //retry after short time
    case QE_REVOKED_ERROR:
    case PVE_REVOKED_ERROR:
    case PVE_MSG_ERROR:
    case PVE_PERFORMANCE_REKEY_NOT_SUPPORTED:
    case AESM_PSDA_PLATFORM_KEYS_REVOKED:
    case AESM_PSDA_PROTOCOL_NOT_SUPPORTED:
    case PSW_UPDATE_REQUIRED:
        return cur+TIMEOUT_LONG_TIME;
    default:
        return cur+TIMEOUT_SHORT_TIME;//retry quicky for unknown error
    }
}

void ThreadStatus::set_status_finish(BaseThreadIOCache* ioc)
{
    aesm_thread_t handle = NULL;
    {
        AESMLogicLock locker(thread_mutex);
        assert(thread_state==ths_busy||thread_state==ths_stop);
        assert(ioc->status == ioc_busy);
        AESM_DBG_TRACE("set finish status for ioc %p(status=%d,timeout=%d,ref_count=%d) of thread %p",ioc, (int)ioc->status,(int)ioc->timeout,(int)ioc->ref_count,this);
        if(thread_state==ths_busy){
            AESM_DBG_TRACE("set thread %p to idle", this);
            thread_state=ths_idle;
            cur_iocache = NULL;
        }
        ioc->status=ioc_idle;
        ioc->ref_count--;
        ioc->timeout = get_timeout_via_ae_error(ioc->ae_ret);
        if(ioc->ref_count==0){//try free thread handle
            handle = ioc->thread_handle;
            ioc->thread_handle = NULL;
            AESM_DBG_TRACE("thread handle release for ioc %p and status to idle of thread %p",ioc, this);
        }
    }
    if(handle!=NULL){
        aesm_free_thread(handle);
    }
}

bool ThreadStatus::query_status_and_reset_clock(void)
{
    AESMLogicLock locker(thread_mutex);
    if(thread_state == ths_busy || thread_state == ths_stop)
        return false;
    status_clock = se_get_tick_count();
    return true;
}

//Code above implement logic of threads in the AESM Service
//Code below to define IOCache of each thread
static ThreadStatus epid_thread;

static ThreadStatus long_term_paring_thread;

static ThreadStatus white_list_thread;


class EpidProvIOCache:public BaseThreadIOCache{
    bool performance_rekey;//input
protected:
    EpidProvIOCache(bool perf_rekey){
        this->performance_rekey = perf_rekey;
    }
    virtual ae_error_t entry(void);
    virtual ThreadStatus& get_thread();
    friend ae_error_t start_epid_provision_thread(bool performance_rekey, unsigned long timeout);
public:
    virtual bool operator==(const BaseThreadIOCache& oc)const{
        const EpidProvIOCache *p=dynamic_cast<const EpidProvIOCache *>(&oc);
        if(p==NULL)return false;
        return performance_rekey==p->performance_rekey;//only compare input
    }
};

class WhiteListIOCache :public BaseThreadIOCache{
//no input to be cached for white list pulling
protected:
    WhiteListIOCache(void){
    }
    virtual ae_error_t entry(void);
    virtual ThreadStatus& get_thread();
    friend ae_error_t start_white_list_thread(unsigned long timeout);
public:
    virtual bool operator==(const BaseThreadIOCache& oc)const{
        const WhiteListIOCache *p = dynamic_cast<const WhiteListIOCache*>(&oc);
        if (p == NULL) return false;
        return true;
    }
};
class CheckLtpIOCache:public BaseThreadIOCache{
    bool is_new_pairing;//extra output
protected:
    CheckLtpIOCache(){
        is_new_pairing=false;
    }
    virtual ae_error_t entry();
    virtual ThreadStatus& get_thread();
    friend ae_error_t start_check_ltp_thread(bool& is_new_pairing, unsigned long timeout);
public:
    virtual bool operator==(const BaseThreadIOCache& oc)const{
        const CheckLtpIOCache *p=dynamic_cast<const CheckLtpIOCache *>(&oc);
        if(p==NULL)return false;
        return true;//no input, always equal
    }
};

class UpdatePseIOCache:public BaseThreadIOCache{
    platform_info_blob_wrapper_t pib;//input
    uint32_t attestation_status;//input
protected:
    UpdatePseIOCache(const platform_info_blob_wrapper_t& pib_info, uint32_t attst_status){
        (void)memcpy_s(&this->pib, sizeof(this->pib), &pib_info, sizeof(pib_info));
        attestation_status=attst_status;
    }
    virtual ae_error_t entry();
    virtual ThreadStatus& get_thread();
    friend ae_error_t start_update_pse_thread(const platform_info_blob_wrapper_t* update_blob, uint32_t attestation_status, unsigned long timeout);
public:
    virtual bool operator==(const BaseThreadIOCache& oc)const{
        const UpdatePseIOCache *p=dynamic_cast<const UpdatePseIOCache *>(&oc);
        if(p==NULL)return false;
        return attestation_status==p->attestation_status&&memcmp(&pib, &p->pib, sizeof(pib))==0;
    }
};

class CertProvLtpIOCache:public BaseThreadIOCache{
    bool is_new_pairing;//extra output
protected:
    CertProvLtpIOCache(){
        is_new_pairing = false;
    }
    virtual ae_error_t entry();
    virtual ThreadStatus& get_thread();
    friend ae_error_t start_long_term_pairing_thread(bool& is_new_paring, unsigned long timeout);
public:
    virtual bool operator==(const BaseThreadIOCache& oc)const{
        const CertProvLtpIOCache *p=dynamic_cast<const CertProvLtpIOCache *>(&oc);
        if(p==NULL)return false;
        return true;
    }
};

ThreadStatus& EpidProvIOCache::get_thread()
{
    return epid_thread;
}

ThreadStatus& CheckLtpIOCache::get_thread()
{
    return long_term_paring_thread;
}

ThreadStatus& UpdatePseIOCache::get_thread()
{
    return long_term_paring_thread;
}

ThreadStatus& CertProvLtpIOCache::get_thread()
{
    return long_term_paring_thread;
}
ThreadStatus& WhiteListIOCache::get_thread()
{
    return white_list_thread;
}

ae_error_t EpidProvIOCache::entry()
{
    return ae_ret = PvEAESMLogic::epid_provision_thread_func(performance_rekey); 
}
ae_error_t CheckLtpIOCache::entry()
{
    return ae_ret = PlatformInfoLogic::check_ltp_thread_func(is_new_pairing);
}

ae_error_t UpdatePseIOCache::entry()
{
    return ae_ret = PlatformInfoLogic::update_pse_thread_func(&pib, attestation_status);
}

ae_error_t CertProvLtpIOCache::entry()
{
    return ae_ret = PSEOPAESMLogic::certificate_provisioning_and_long_term_pairing_func(is_new_pairing);
}
ae_error_t WhiteListIOCache::entry()
{
    return ae_ret = CLEClass::update_white_list_by_url();
}


//start implementation of external functions

#define INIT_THREAD(cache_type, timeout, init_list) \
    BaseThreadIOCache *ioc = new cache_type init_list; \
    BaseThreadIOCache *out_ioc = NULL; \
    ae_error_t ae_ret = AE_FAILURE; \
    ae_ret = ioc->start(out_ioc, (uint32_t)(timeout)); \
    if(ae_ret != AE_SUCCESS){ \
        if(out_ioc!=NULL){out_ioc->deref();}\
        return ae_ret; \
    }\
    assert(out_ioc!=NULL);\
    cache_type *pioc = dynamic_cast<cache_type *>(out_ioc);\
    assert(pioc!=NULL);
    //now the thread has finished it's execution and we could read result without lock
#define COPY_OUTPUT(x)  x=pioc->x
#define FINI_THREAD() \
    ae_ret = pioc->ae_ret;\
    pioc->deref();/*derefence the cache object after usage of it*/ \
    return ae_ret;

//usage model
//INIT_THREAD(thread_used, cache_type, timeout, init_list)
// COPY_OUTPUT(is_new_pairing);// copy out output parameter except for return value from pioc object to output parameter, such as
//FINI_THREAD(thread_used)

ae_error_t start_epid_provision_thread(bool performance_rekey, unsigned long timeout)
{
    INIT_THREAD(EpidProvIOCache, timeout, (performance_rekey))
    FINI_THREAD()
}

ae_error_t start_white_list_thread(unsigned long timeout)
{
    INIT_THREAD(WhiteListIOCache, timeout, ())
    FINI_THREAD()
}
ae_error_t start_check_ltp_thread(bool& is_new_pairing, unsigned long timeout)
{
    INIT_THREAD(CheckLtpIOCache, timeout, ())
    COPY_OUTPUT(is_new_pairing);
    FINI_THREAD()
}

ae_error_t start_update_pse_thread(const platform_info_blob_wrapper_t* update_blob, uint32_t attestation_status, unsigned long timeout)
{
    INIT_THREAD(UpdatePseIOCache, timeout, (*update_blob, attestation_status))
    FINI_THREAD()
}

ae_error_t start_long_term_pairing_thread(bool& is_new_pairing, unsigned long timeout)
{
    INIT_THREAD(CertProvLtpIOCache, timeout, ())
    COPY_OUTPUT(is_new_pairing);
    FINI_THREAD()
}
bool query_pve_thread_status(void)
{
    return epid_thread.query_status_and_reset_clock();
}
bool query_pse_thread_status(void)
{
    return long_term_paring_thread.query_status_and_reset_clock();
}
ae_error_t wait_pve_thread(uint64_t time_out_milliseconds)
{
    return epid_thread.wait_for_cur_thread(time_out_milliseconds);
}

void stop_all_long_lived_threads(uint64_t time_out_milliseconds)
{
    uint64_t freq = se_get_tick_count_freq();
    uint64_t stop_tick_count = se_get_tick_count()+(time_out_milliseconds*freq+500)/1000;
    epid_thread.stop_thread(stop_tick_count);
    long_term_paring_thread.stop_thread(stop_tick_count);
    white_list_thread.stop_thread(stop_tick_count);
}

