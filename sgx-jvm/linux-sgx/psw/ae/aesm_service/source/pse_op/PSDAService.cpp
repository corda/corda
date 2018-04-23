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
#include "PSDAService.h"
#include <exception>
#include <limits.h>
#include "util.h"
#include "se_string.h"

#define PSDA_FILE_NAME "PSDA.dalp"

static const char* g_psda_id = "cbede6f96ce4439ca1c76e2087786616";

PSDAService::PSDAService(void)
{
    jhi_handle = NULL;
    psda_session_handle = NULL;
    psda_svn = 0;
    csme_gid = 0;
}

PSDAService::~PSDAService(void)
{
    stop_service();
}

bool PSDAService::start_service()
{
    // session is active
    if (is_session_active())
        return true;

    for (int i = 0; i < AESM_RETRY_COUNT; i++)
    {
        if (!start_service_internal()) 
        {
            continue;
        }
        else
        {
            // start service successfully
            return true;
        }
    }

    return false;
}

bool PSDAService::install_psda()
{
    // get PSDA full path
    TCHAR psda_path[MAX_PATH] = { 0 };
    if (aesm_get_pathname(FT_PERSISTENT_STORAGE, PSDA_FID, psda_path, MAX_PATH) != AE_SUCCESS)
    {
        return false;
    }
    else
    {
        // install the PSDA 
        JHI_RET jhi_ret = JHI_Install2(jhi_handle, g_psda_id, psda_path);
        if (jhi_ret != JHI_SUCCESS)
        {
            AESM_DBG_ERROR("Failed to install PSDA. JHI_Install2() returned %d", jhi_ret);
            return false;
        }
        // get the psda svn and keep it in memory
        if (!save_current_psda_svn())
        {
            AESM_DBG_ERROR("Failed to get PSDA SVN.");
            return false;
        }

        return true;
    }
}

bool PSDAService::start_service_internal()
{
    bool retVal = false;

    SGX_DBGPRINT_PRINT_ANSI_STRING(__FUNCTION__);

    JHI_RET jhi_ret = JHI_UNKNOWN_ERROR;
    __try {
        do {
            // Close JHI session
            if (jhi_handle != NULL && psda_session_handle != NULL)
            {
                JHI_CloseSession(jhi_handle, &psda_session_handle);
                psda_session_handle = NULL;
            }

            if (jhi_handle == NULL)
            {
                // Initialize PSDA
                if ((jhi_ret = JHI_Initialize(&jhi_handle, NULL, 0)) != JHI_SUCCESS)
                {
                    AESM_DBG_ERROR("JHI_Initialize() failed. The return value is %d", jhi_ret);
                    break;
                }
                else if(!install_psda()) 
                {
                    break;
                }
            }

            // Create JHI session
            if ((jhi_ret = JHI_CreateSession(jhi_handle, g_psda_id, 0, NULL, &psda_session_handle)) != JHI_SUCCESS) 
            {
                if (jhi_ret == JHI_APPID_NOT_EXIST)
                {
                    // if the system resumed from hibernate or fast startup after RTC is cleared, JHI_CreateSession would 
                    // return JHI_APPID_NOT_EXIST and we need to re-install PSDA and call JHI_CreateSession again
                    if (!install_psda() || (jhi_ret = JHI_CreateSession(jhi_handle, g_psda_id, 0, NULL, &psda_session_handle)) != JHI_SUCCESS)
                    {
                        AESM_DBG_ERROR("Failed to install psda or create session. Returned %d", jhi_ret);
                        break;
                    }
                }
                else
                {
                    AESM_DBG_ERROR("Failed to create session. JHI_CreateSession() returned %d", jhi_ret);
                    break;
                }
            }

            retVal = true;

#if defined(DAL_DIAGNOSTICS)

            JVM_COMM_BUFFER appletProperty;
            char rxBuf[1000];

            appletProperty.RxBuf->buffer = rxBuf;
            appletProperty.RxBuf->length = sizeof(rxBuf);

            //
            // all this to get rid of const-ness of g_psda_id,
            // required by JHI_GetAppletProperty
            //
            unsigned len = strlen(g_psda_id) + 1;
            char* tempId = (char*) malloc(len);
            if (NULL != tempId)
            {
                strcpy_s(tempId, len, g_psda_id);
                char const * txBuf = "security.version";
                appletProperty.TxBuf->buffer = (PVOID)txBuf;
                appletProperty.TxBuf->length = sizeof(*txBuf)*(strlen(txBuf)+1);
                JHI_RET jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);

                long tempSvn = strtol(rxBuf, NULL, 10);
                if (!(LONG_MIN == tempSvn || LONG_MAX == tempSvn || 0 == tempSvn))
                {
                    SGX_DBGPRINT_ONE_STRING_ONE_INT("psdaSvn = ", tempSvn);
                }

                memset(rxBuf, 0xCC, sizeof(rxBuf));

                txBuf = "applet.name";
                appletProperty.TxBuf->buffer = txBuf;
                appletProperty.TxBuf->length = sizeof(*txBuf)*(strlen(txBuf)+1);
                appletProperty.RxBuf->length = sizeof(rxBuf);
                jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);
                memset(rxBuf, 0xCC, sizeof(rxBuf));

                txBuf = "applet.vendor";
                appletProperty.TxBuf->buffer = txBuf;
                appletProperty.TxBuf->length = sizeof(*txBuf)*(strlen(txBuf)+1);
                appletProperty.RxBuf->length = sizeof(rxBuf);
                jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);
                memset(rxBuf, 0xCC, sizeof(rxBuf));

                txBuf = "applet.description";
                appletProperty.TxBuf->buffer = txBuf;
                appletProperty.TxBuf->length = sizeof(*txBuf)*(strlen(txBuf)+1);
                appletProperty.RxBuf->length = sizeof(rxBuf);
                jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);
                memset(rxBuf, 0xCC, sizeof(rxBuf));

                txBuf = "applet.version";
                appletProperty.TxBuf->buffer = txBuf;
                appletProperty.TxBuf->length = sizeof(*txBuf)*(strlen(txBuf)+1);
                appletProperty.RxBuf->length = sizeof(rxBuf);
                jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);
                memset(rxBuf, 0xCC, sizeof(rxBuf));

                txBuf = "applet.flash.quota";
                appletProperty.TxBuf->buffer = txBuf;
                appletProperty.TxBuf->length = sizeof(*txBuf)*(strlen(txBuf)+1);
                appletProperty.RxBuf->length = sizeof(rxBuf);
                jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);
                memset(rxBuf, 0xCC, sizeof(rxBuf));

                txBuf = "applet.debug.enable";
                appletProperty.TxBuf->buffer = txBuf;
                appletProperty.TxBuf->length = sizeof(*txBuf)*(strlen(txBuf)+1);
                appletProperty.RxBuf->length = sizeof(rxBuf);
                jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);
                memset(rxBuf, 0xCC, sizeof(rxBuf));

                txBuf = "applet.platform";
                appletProperty.TxBuf->buffer = txBuf;
                appletProperty.TxBuf->length = sizeof(*txBuf)*(strlen(txBuf)+1);
                appletProperty.RxBuf->length = sizeof(rxBuf);
                jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);
                memset(rxBuf, 0xCC, sizeof(rxBuf));

            }

#endif
        }
        while(false);
    }
    __except(1) {
        // On windows 7, if JHI.dll cannot be found, an SEH exception will be raised 
        return false;
    }

    SGX_DBGPRINT_PRINT_ANSI_STRING("PSDAService::start_service_internal() exit");

    return retVal;

}

void PSDAService::stop_service()
{
    JHI_RET jhi_ret = JHI_UNKNOWN_ERROR;
    try {
        if (jhi_handle != NULL)
        {
            if (psda_session_handle != NULL)
            {
                if ((jhi_ret = JHI_CloseSession(jhi_handle, &psda_session_handle)) != JHI_SUCCESS)
                {
                    AESM_DBG_ERROR("JHI_CloseSession returned %d", jhi_ret);
                }
            }
            if ((jhi_ret = JHI_Uninstall(jhi_handle, (char*)g_psda_id)) != JHI_SUCCESS)
            {
                AESM_DBG_ERROR("Failed to uninstall PSDA. The return value is %d ", jhi_ret);
            }
            if ((jhi_ret = JHI_Deinit(jhi_handle)) != JHI_SUCCESS)
            {
                AESM_DBG_ERROR("Failed to Deinit JHI. The return value is %d ", jhi_ret);
            }
        }

        psda_session_handle = NULL;
        jhi_handle = NULL;
    }
    catch (std::exception e)
    {
    }
}

ae_error_t PSDAService::send_and_recv(
    INT32   nCommandId,
    JVM_COMM_BUFFER* pComm,
    INT32* responseCode,
    session_loss_retry_flag_t flag)
{
    int retry = AESM_RETRY_COUNT;

    while (retry > 0) {
        JHI_RET ret = JHI_SendAndRecv2(this->jhi_handle,
                            this->psda_session_handle,
                            nCommandId,
                            pComm,
                            responseCode);
        if (ret != JHI_SUCCESS) {
            if (ret == JHI_SERVICE_UNAVAILABLE || ret == JHI_INVALID_SESSION_HANDLE) {
                // session is lost, create session anyway
                if (!start_service_internal()) {
                    return AESM_PSDA_NOT_AVAILABLE;
                }
                // 
                if (flag == NO_RETRY_ON_SESSION_LOSS) 
                    return AESM_PSDA_SESSION_LOST;
                else {
                    retry--;
                    continue;
                }
            }
            else {
                return AESM_PSDA_INTERNAL_ERROR;
            }
        }
        return AE_SUCCESS;
    }
    return AESM_PSDA_INTERNAL_ERROR;
}

bool PSDAService::is_session_active()
{
    try {
        if (jhi_handle != NULL && psda_session_handle != NULL)
        {
            JHI_SESSION_INFO session_info;
            if (JHI_GetSessionInfo(jhi_handle, psda_session_handle, &session_info) == JHI_SUCCESS
                && session_info.state == JHI_SESSION_STATE_ACTIVE)
            {
                    // session is valid
                    return true;
            }
        }
        return false;
    }
    catch (std::exception e)
    {
        return false;
    }
}

bool PSDAService::save_current_psda_svn()
{
    bool retVal = false;


    JVM_COMM_BUFFER appletProperty;
    char rxBuf[1000];

    appletProperty.RxBuf->buffer = rxBuf;
    appletProperty.RxBuf->length = sizeof(rxBuf);

    char const * txBuf = "security.version";
    appletProperty.TxBuf->buffer = (PVOID)txBuf;
    appletProperty.TxBuf->length = (UINT32)(sizeof(*txBuf)*(strlen(txBuf)+1));

    //
    // all this to get rid of const-ness of g_psda_id,
    // required by JHI_GetAppletProperty
    //
    unsigned len = (unsigned)strnlen_s(g_psda_id, 128) + 1;
    char* tempId = (char*) malloc(len);
    if (NULL != tempId)
    {
        strcpy_s(tempId, len, g_psda_id);
        JHI_RET jhiRet = JHI_GetAppletProperty(jhi_handle, tempId, &appletProperty);

        if (JHI_SUCCESS == jhiRet)
        {

            long tempSvn = strtol(rxBuf, NULL, 10);
            if (!(LONG_MIN == tempSvn || LONG_MAX == tempSvn || 0 == tempSvn))
            {
                retVal = true;
                psda_svn = (unsigned int)tempSvn;
                SGX_DBGPRINT_ONE_STRING_ONE_INT("psdaSvn = ", tempSvn);
            }
            else
            {
                AESM_DBG_ERROR("Invalid PSDA security.version.");
            }
        }
        else
        {
            AESM_DBG_ERROR("Failed to get PSDA security.version.");
        }
        free(tempId);
    }


    return retVal;


}

