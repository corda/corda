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
#ifndef _PSDA_SERVICE_H_
#define _PSDA_SERVICE_H_
#include "AEClass.h"
#include "jhi.h"

#define PSDA_SUCCESS                    0
#define PSDA_INVALID_COMMAND            1
#define PSDA_BAD_PARAMETER              2
#define PSDA_INTERNAL_ERROR             3
#define PSDA_INVALID_SESSION_STATE      4
#define PSDA_INTEGRITY_ERROR            5
#define PSDA_SEQNO_CHECK_FAIL           6
#define PSDA_LT_PAIRING_NOT_EXIST       7
#define PSDA_NOT_PROVISIONED            8
#define PSDA_PROTOCOL_NOT_SUPPORTED     9
#define PSDA_PLATFORM_KEYS_REVOKED      10
#define PSDA_PERSISTENT_DATA_WRITE_THROTTLED 11
typedef enum _session_loss_retry_flag_t
{
    NO_RETRY_ON_SESSION_LOSS = 0,
    AUTO_RETRY_ON_SESSION_LOSS,
} session_loss_retry_flag_t;

class PSDAService : public Singleton<PSDAService>
{
public:
    PSDAService(void);
    ~PSDAService(void);

    bool start_service();
    void stop_service();
    bool is_session_active();
    ae_error_t send_and_recv(
        INT32            nCommandId,
        JVM_COMM_BUFFER* pComm,
        INT32* responseCode,
        session_loss_retry_flag_t flag);

    JHI_HANDLE jhi_handle;
    JHI_SESSION_HANDLE psda_session_handle;

    UINT32 csme_gid;
    unsigned psda_svn;
private:
    bool install_psda();
    bool start_service_internal();
    bool save_current_psda_svn();
};
#endif

