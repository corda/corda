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



#include "ReplayProtectedDRM.h"
#include "TimeBasedDRM.h"
#include <iostream>
using namespace std;


#include <string.h>
#include <unistd.h>
#define memcpy_s(dst,dst_size,src,max_count)  memcpy(dst,src,max_count)
#define Sleep(n) usleep((n)*1000)

uint32_t test_replay_protected_drm_operation()
{
    cout<<endl<<"\tReplay Protected DRM operation:"<<endl;
    uint32_t result = 0;
    ReplayProtectedDRM DRM;
    result = DRM.init();
    if(result)
    {
        cerr<<"Initialization the DRM failed."<<endl;
        return result;
    }
    else 
        cout<<"Successfully initialized the DRM."<<endl;

    do{
        result = DRM.perform_function();
        if(result)
        {
            cerr<<"Performing the DRM functions failed."<<endl;
            break;
        }
        else
            cout<<"Successfully performed the DRM functions."<<endl;

        result = DRM.update_secret();
        if(result)
        {
            cerr<<"Updating the DRM secret failed."<<endl;
            break;
        }
        else
            cout<<"Successfully updated the DRM secret."<<endl;

        result = DRM.perform_function();
        if(result)
        {
            cerr<<"Performing the DRM functions failed."<<endl;
            break;
        }
        else
            cout<<"Successfully performed the DRM functions."<<endl;

    }while(0);

    if(DRM.delete_secret())
    {
        cerr<<"Deleting the DRM secret failed."<<endl;
        return result;
    }
    else
        cout<<"Successfully deleted the DRM secret."<<endl;
    return result;
}

uint32_t test_replay_protected_drm_update_limitation()
{
    cout<<endl<<"\tReplay Protected DRM update limitation:"<<endl;
    uint32_t result = 0;
    ReplayProtectedDRM DRM;
    result = DRM.init();
    if(result)
    {
        cerr<<"Initialization the DRM failed."<<endl;
        return result;
    }
    else 
        cout<<"Successfully initialized the DRM."<<endl;
    do{
        result = DRM.perform_function();
        if(result)
        {
            cerr<<"Performing the DRM functions fail."<<endl;
            break;
        }
        else
            cout<<"Successfully performed the DRM functions."<<endl;
        for (int i = 0; i <= REPLAY_PROTECTED_PAY_LOAD_MAX_RELEASE_VERSION; i++)
        {
            result = DRM.update_secret();
            if(result == MAX_RELEASE_REACHED &&
                i>=REPLAY_PROTECTED_PAY_LOAD_MAX_RELEASE_VERSION)
            {
                cout<<"\tExpected failure."<<endl
                    <<"\tThe DRM secret update limitation reached."<<endl;
                result = 0;
                return result;
            }
            else if(result)
            {
                cerr<<"Updating the DRM secret failed."<<endl;
                break;
            }
            else 
                cout<<"Successfully updated the DRM secret."<<endl;

            result = DRM.perform_function();
            if(result)
            {
                cerr<<"Performing the DRM functions failed."<<endl;
                break;
            }
            else
                cout<<"Successfully performed the DRM functions."<<endl;
        }
        if(!result)
        {
            result = 1;
            cerr<<"\tUnexpected success."<<endl
                <<"\tFailed to catch update limitation."<<endl;
        }
    }while(0);

    if(DRM.delete_secret())
    {
        cerr<<"Deleting the DRM secret failed."<<endl;
        return result;
    }
    else
        cout<<"Successfully deleted the DRM secret."<<endl;
    return result;
}

uint32_t test_replay_protected_drm_replay_attack_protection()
{
    cout<<endl<<"\tReplay Protected DRM replay attack protection:"<<endl;
    uint32_t result = 0;
    uint8_t sealed_log[ReplayProtectedDRM::sealed_activity_log_length];
    ReplayProtectedDRM DRM;
    result = DRM.init(sealed_log);
    if(result)
    {
        cerr<<"Initialization the DRM failed."<<endl;
        return result;
    }
    else
        cout<<"Successfully initialized the DRM."<<endl;

    /* store a valid log for replay attack */
    uint8_t replay_log[ReplayProtectedDRM::sealed_activity_log_length];
    memcpy_s(replay_log,ReplayProtectedDRM::sealed_activity_log_length,
        sealed_log,ReplayProtectedDRM::sealed_activity_log_length);
    do{
        result = DRM.update_secret(sealed_log);
        if(result)
        {
            cerr<<"Updating the DRM secret functions failed."<<endl;
            break;
        }
        else
            cout<<"Successfully updated the DRM functions."<<endl;


        result = DRM.perform_function(replay_log);
        if(result == REPLAY_DETECTED)
            cout<<"\tExpected failure."<<endl
            <<"\tReplay attack to DRM functions is caught."<<endl;
        else
        {
            cerr<<"\tUnexpected success."<<endl
            <<"\tReplay attack to DRM functions is NOT caught."<<endl;
            result = 1;
            break;
        }

        result = DRM.perform_function(sealed_log);
        if(result)
        {
            cerr<<"Performing the DRM functions failed."<<endl;
            break;
        }
        else
            cout<<"Successfully performed the DRM functions ."<<endl;

    }while(0);

    if(DRM.delete_secret(sealed_log))
    {
        cerr<<"Deleting the DRM secret failed."<<endl;
        return result;
    }
    else
        cout<<"Successfully deleted the DRM secret."<<endl;
    return result;
}

uint32_t test_time_based_policy_operation()
{
    cout<<endl<<"\tTime based policy operation:"<<endl;
    TimeBasedDRM DRM;
    uint32_t result = 0;
    result = DRM.init();
    if(result)
    {
        cerr<<"Initialization the time based policy failed."<<endl;
        return result;
    }
    else
        cout<<"Successfully initialized the time based policy."<<endl;

    result = DRM.perform_function();
    if(result)
    {
        cerr<<"Performing the time based policy functions failed."<<endl;
        return result;
    }
    else
        cout<<"Successfully performed the time based policy functions."<<endl;
    return 0;
}

uint32_t test_time_based_policy_expiration()
{
    cout<<endl<<"\tTime based policy expiration:"<<endl;
    TimeBasedDRM DRM;
    uint32_t result = 0;
    result = DRM.init();
    if(result)
    {
        cerr<<"Initialization the time based policy failed."<<endl;
        return result;
    }
    else
        cout<<"Successfully initialized the time based policy."<<endl;

    /* wait for time based DRM expiring */
    Sleep((TIME_BASED_LEASE_DURATION_SECOND+1)*1000);
    result = DRM.perform_function();
    if(result== LEASE_EXPIRED)
    {
        cout<<"\tExpected failure."<<endl
            <<"\tTime based policy has expired."<<endl;
        return 0;
    }
    else
    {
        cerr<<"\tUnexpected success."<<endl
            <<"\tTime based policy failed to catch expiration."<<endl;
        return 1;
    }
}

#define _T(x) x
int main(int argc, char* argv[])
{
    argc; /* unused parameter */
    argv; /* unused parameter */
    uint32_t result;
    /* normal operation */
    result = test_replay_protected_drm_operation();
    /* trigger update limitation */
    result = test_replay_protected_drm_update_limitation();
    /* replay attack */
    result = test_replay_protected_drm_replay_attack_protection();
    /* normal operation */
    result = test_time_based_policy_operation();
    /* trigger expiration */
    result = test_time_based_policy_expiration();
    
    printf("Enter a character before exit ...\n");
    getchar();
    return 0;
}

