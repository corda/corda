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
#include <stdint.h>
#include <dlfcn.h>
#include "oal/oal.h"
#include "jhi.h"
#include "jhi_proxy.h"

const static char JHI_PATH[] = "libjhi.so";

SharedLibProxy::SharedLibProxy():handle(NULL), library_path(JHI_PATH)
{
}


SharedLibProxy::~SharedLibProxy(void)
{
    unload();
}

void SharedLibProxy::load(void)
{
    handle = dlopen(library_path, RTLD_LAZY);
    if (!isLoaded())
    {
        const char* err = dlerror();
        AESM_DBG_ERROR("Load JHI library failed: %s", err); (void)(err);
    }
}



void SharedLibProxy::unload()
{
    if (isLoaded())
    {
        dlclose(handle);
        handle = 0;
    }
}

bool SharedLibProxy::findSymbol(const char* name, void** function)
{
    if (!isLoaded())
    {
        load();
    }
    if (isLoaded())
    {
        *function = dlsym(handle, name);
        return *function != NULL;
    }
    return false;
}

JHI_EXPORT
JHI_Initialize(
    OUT JHI_HANDLE* ppHandle,
    IN  PVOID       context,
    IN  UINT32      flags
)
{
    JHI_RET (*p_JHI_Initialize)(
        OUT JHI_HANDLE* ppHandle,
        IN  PVOID       context,
        IN  UINT32      flags)  = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_Initialize))
    {
        return p_JHI_Initialize(ppHandle, context, flags);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT JHI_Deinit(IN JHI_HANDLE handle)
{
    JHI_RET(*p_JHI_Deinit)(IN JHI_HANDLE handle) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_Deinit))
    {
        return p_JHI_Deinit(handle);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_SendAndRecv2(
    IN JHI_HANDLE       handle,
    IN JHI_SESSION_HANDLE SessionHandle,
    IN INT32			nCommandId,
    INOUT JVM_COMM_BUFFER* pComm,
    OUT INT32* responseCode)
{
    JHI_RET(*p_JHI_SendAndRecv2)(
        IN JHI_HANDLE       handle,
        IN JHI_SESSION_HANDLE SessionHandle,
        IN INT32			nCommandId,
        INOUT JVM_COMM_BUFFER* pComm,
        OUT INT32* responseCode) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_SendAndRecv2))
    {
        return p_JHI_SendAndRecv2(handle, SessionHandle, nCommandId, pComm, responseCode);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_Install2(
    IN const JHI_HANDLE handle,
    IN const char*      AppId,
    IN const FILECHAR*   srcFile
)
{
    JHI_RET(*p_JHI_Install2)(
        IN const JHI_HANDLE handle,
        IN const char*      AppId,
        IN const FILECHAR*   srcFile) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_Install2))
    {
        return p_JHI_Install2(handle, AppId, srcFile);
    }
    return JHI_SERVICE_UNAVAILABLE;

}

JHI_EXPORT
JHI_Uninstall(
    IN JHI_HANDLE handle,
    IN const char* AppId
)
{
    JHI_RET(*p_JHI_Uninstall)(
        IN JHI_HANDLE handle,
        IN const char* AppId) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_Uninstall))
    {
        return p_JHI_Uninstall(handle, AppId);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_GetAppletProperty(
    IN    JHI_HANDLE        handle,
    IN    const char*             AppId,
    INOUT JVM_COMM_BUFFER* pComm
)
{
    JHI_RET(*p_JHI_GetAppletProperty)(
        IN    JHI_HANDLE        handle,
        IN    const char*             AppId,
        INOUT JVM_COMM_BUFFER* pComm) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_GetAppletProperty))
    {
        return p_JHI_GetAppletProperty(handle, AppId, pComm);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_CreateSession(
    IN const JHI_HANDLE handle,
    IN const char* AppId,
    IN  UINT32 flags,
    IN DATA_BUFFER* initBuffer,
    OUT JHI_SESSION_HANDLE* pSessionHandle
)
{
    JHI_RET(*p_JHI_CreateSession)(
        IN const JHI_HANDLE handle,
        IN const char* AppId,
        IN  UINT32 flags,
        IN DATA_BUFFER* initBuffer,
        OUT JHI_SESSION_HANDLE* pSessionHandle) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_CreateSession))
    {
        return p_JHI_CreateSession(handle, AppId, flags, initBuffer, pSessionHandle);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_GetSessionsCount(
    IN const JHI_HANDLE handle,
    IN const char* AppId,
    OUT UINT32* SessionsCount
)
{
    JHI_RET(*p_JHI_GetSessionsCount)(
        IN const JHI_HANDLE handle,
        IN const char* AppId,
        OUT UINT32* SessionsCount) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_GetSessionsCount))
    {
        return p_JHI_GetSessionsCount(handle, AppId, SessionsCount);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_CloseSession(
    IN const JHI_HANDLE handle,
    IN JHI_SESSION_HANDLE* pSessionHandle
)
{
    JHI_RET(*p_JHI_CloseSession)(
        IN const JHI_HANDLE handle,
        IN JHI_SESSION_HANDLE* pSessionHandle) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_CloseSession))
    {
        return p_JHI_CloseSession(handle, pSessionHandle);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_ForceCloseSession(
    IN const JHI_HANDLE handle,
    IN JHI_SESSION_HANDLE* pSessionHandle
)
{
    JHI_RET(*p_JHI_ForceCloseSession)(
        IN const JHI_HANDLE handle,
        IN JHI_SESSION_HANDLE* pSessionHandle) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_ForceCloseSession))
    {
        return p_JHI_ForceCloseSession(handle, pSessionHandle);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_GetSessionInfo(
    IN const JHI_HANDLE handle,
    IN JHI_SESSION_HANDLE SessionHandle,
    OUT JHI_SESSION_INFO* SessionInfo
)
{
    JHI_RET(*p_JHI_GetSessionInfo)(
        IN const JHI_HANDLE handle,
        IN JHI_SESSION_HANDLE SessionHandle,
        OUT JHI_SESSION_INFO* SessionInfo) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_GetSessionInfo))
    {
        return p_JHI_GetSessionInfo(handle, SessionHandle, SessionInfo);
    }
    return JHI_SERVICE_UNAVAILABLE;
}


JHI_EXPORT
JHI_RegisterEvents(
    IN const JHI_HANDLE handle,
    IN JHI_SESSION_HANDLE SessionHandle,
    IN JHI_EventFunc pEventFunction)
{
    JHI_RET(*p_JHI_RegisterEvents)(
            IN const JHI_HANDLE handle,
            IN JHI_SESSION_HANDLE SessionHandle,
            IN JHI_EventFunc pEventFunction) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_RegisterEvents))
    {
        return p_JHI_RegisterEvents(handle, SessionHandle, pEventFunction);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_UnRegisterEvents(
    IN const JHI_HANDLE handle,
    IN JHI_SESSION_HANDLE SessionHandle)
{
    JHI_RET(*p_JHI_UnRegisterEvents)(
        IN const JHI_HANDLE handle,
        IN JHI_SESSION_HANDLE SessionHandle) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_UnRegisterEvents))
    {
        return p_JHI_UnRegisterEvents(handle, SessionHandle);
    }
    return JHI_SERVICE_UNAVAILABLE;
}

JHI_EXPORT
JHI_GetVersionInfo(
    IN const JHI_HANDLE handle,
    OUT JHI_VERSION_INFO* pVersionInfo)
{
    JHI_RET(*p_JHI_GetVersionInfo)(
        IN const JHI_HANDLE handle,
        OUT JHI_VERSION_INFO* pVersionInfo) = NULL;
    if (SharedLibProxy::instance().findSymbol(__FUNCTION__, (void**)&p_JHI_GetVersionInfo))
    {
        return p_JHI_GetVersionInfo(handle, pVersionInfo);
    }
    return JHI_SERVICE_UNAVAILABLE;
}
