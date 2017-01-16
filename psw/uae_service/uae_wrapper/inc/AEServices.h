/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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
#ifndef _AE_SERVICES_H
#define _AE_SERVICES_H

#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include <sgx_error.h>
#include <aesm_error.h>
#include <oal/uae_oal_api.h>
#include <Config.h>

struct PlainData
{
    uint32_t length;
    uint8_t* data;
    uint32_t errorCode;
    uae_oal_status_t uaeStatus;

#ifdef __cplusplus
    PlainData() : length(0), data(NULL), errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED)  {}
    ~PlainData(){
        if (data != NULL) delete [] data;
        data = NULL;
    }

    bool operator==(const PlainData& other) const {
        if (this == &other)                 return true;

        if (length != other.length || errorCode != other.errorCode)
             return false;
        if (data == NULL && other.data == NULL) return true;

        if (data != NULL && other.data != NULL)
            return (memcmp(data, other.data, other.length) == 0);
        else
            return false;
    }

    void copyFields(const PlainData& other) {
        length = other.length;
        errorCode = other.errorCode;

        if(other.data == NULL)
            data = NULL;
        else {
            data = new uint8_t[length];

            memcpy(data, other.data, length);
        }
    }

    PlainData& operator=(const PlainData& other) {
        if (this == &other)
            return *this;

        if (data != NULL)
            delete [] data;

        copyFields(other);

        return *this;
    }

    PlainData(const PlainData& other):length(0), data(NULL), errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED) {
        copyFields(other);
    }
#endif
};

typedef PlainData Quote;
typedef PlainData Report;
typedef PlainData TargetInfo;

typedef PlainData PlatformGID;
typedef PlainData SignatureRevocationList;
typedef PlainData Nonce;
typedef PlainData SPID;

typedef PlainData LaunchToken;
typedef PlainData EnclaveMeasurement;
typedef PlainData Signature;
typedef PlainData SEAttributes;
typedef PlainData PSEMessage;

typedef PlainData WhiteList;

typedef PlainData PlatformInfo;
typedef PlainData UpdateInfo;

struct AttestationInformation
{
    uint32_t errorCode;
    uae_oal_status_t uaeStatus;
    TargetInfo* quotingTarget;
    PlatformGID* platformGID;

    AttestationInformation(): errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED), quotingTarget(NULL),platformGID(NULL) {}
    ~AttestationInformation()
    {
        delete quotingTarget;
        delete platformGID;
    }

    bool operator==(const AttestationInformation& other) const{
        if (this == &other) return true;
        if (errorCode != other.errorCode) return false;
        if (*quotingTarget == *other.quotingTarget &&
                *platformGID == *other.platformGID)
            return true;
        return false;
    }
    private:
        AttestationInformation(const AttestationInformation&);                 // Prevent copy-construction
        AttestationInformation& operator=(const AttestationInformation&);      // Prevent assignment


};

struct QuoteInfo
{
    uint32_t errorCode;
    uae_oal_status_t uaeStatus;
    Quote* quote;
    Report* qeReport;

    QuoteInfo(): errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED), quote(NULL), qeReport(NULL) {}
    ~QuoteInfo()
    {
        delete quote;
        delete qeReport;
    }

    bool operator==(const QuoteInfo& other) const{
        if (this == &other) return true;
        if (errorCode != other.errorCode) return false;
        if (*quote == *other.quote &&
            *qeReport == *other.qeReport)
                return true;
        return false;
    }
    private:
        QuoteInfo(const QuoteInfo&);                 // Prevent copy-construction
        QuoteInfo& operator=(const QuoteInfo&);      // Prevent assignment
};

struct CreateSessionInformation
{
    uint32_t errorCode;
    uae_oal_status_t uaeStatus;
    unsigned int sessionId;
    PlainData* dh_msg1;

    CreateSessionInformation(): errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED), sessionId(0),dh_msg1(NULL) {}
    ~CreateSessionInformation()
    {
        delete dh_msg1;
    }

    bool operator==(const CreateSessionInformation& other) const
    {
        if (this == &other) return true;
        if (sessionId != other.sessionId || errorCode != other.errorCode)
                return false;
        return *dh_msg1 == *other.dh_msg1;
    }

    private:
        CreateSessionInformation(const CreateSessionInformation&);                 // Prevent copy-construction
        CreateSessionInformation& operator=(const CreateSessionInformation&);      // Prevent assignment
    
};

struct PsCap
{
    uint32_t errorCode;
    uae_oal_status_t uaeStatus;
    uint64_t ps_cap;

    PsCap(): errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED), ps_cap(0){}
    ~PsCap()
    {
    }

    bool operator==(const PsCap& other) const
    {
        if (this == &other) return true;
        return ps_cap == other.ps_cap;
    }
};

struct WhiteListSize
{
    uint32_t errorCode;
    uae_oal_status_t uaeStatus;
    uint32_t white_list_size;

    WhiteListSize(): errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED), white_list_size(0){}
    ~WhiteListSize()
    {
    }

    bool operator==(const WhiteListSize& other) const
    {
        if (this == &other) return true;
        return white_list_size == other.white_list_size;
    }
};

struct ExtendedEpidGroupId
{
    uint32_t errorCode;
    uae_oal_status_t uaeStatus;
    uint32_t x_group_id;

    ExtendedEpidGroupId(): errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED), x_group_id(0){}
    ~ExtendedEpidGroupId()
    {
    }

    bool operator==(const ExtendedEpidGroupId& other) const
    {
        if (this == &other) return true;
        return x_group_id == other.x_group_id;
    }
};

struct AttestationStatus
{
    uint32_t errorCode;
    uae_oal_status_t uaeStatus;
    UpdateInfo* updateInfo;

    AttestationStatus(): errorCode(AESM_UNEXPECTED_ERROR), uaeStatus(UAE_OAL_ERROR_UNEXPECTED), updateInfo(NULL) {}
    ~AttestationStatus()
    {
        delete updateInfo;
    }

    bool operator==(const AttestationStatus& other) const{
        if (this == &other) return true;
        if (errorCode != other.errorCode) return false;
        if (*updateInfo == *other.updateInfo)
            return true;
        return false;
    }
   private:
        AttestationStatus(const AttestationStatus&);                 // Prevent copy-construction
        AttestationStatus& operator=(const AttestationStatus&);      // Prevent assignment
};

class AEServices
{
    public:
        AEServices() {}
        virtual ~AEServices() {}

        virtual AttestationInformation* InitQuote(uint32_t timeout_msec=0) = 0;
        virtual QuoteInfo* GetQuote(const Report* report, const uint32_t quoteType, const SPID* spid, const Nonce* nonce,
                                const SignatureRevocationList* sig_rl, const uint32_t bufSize, const bool qe_report, 
                                uint32_t timeout_msec=0) = 0;
        virtual PsCap* GetPsCap(uint32_t timeout_msec=0) = 0;
        virtual AttestationStatus* ReportAttestationError(const PlatformInfo* platformInfo, uint32_t attestation_error_code, uint32_t updateInfoLength, uint32_t timeout_msec=0) = 0;

        virtual WhiteListSize* GetWhiteListSize(uint32_t timeout_msec=0) = 0;
        virtual PlainData* GetWhiteList(uint32_t white_list_size, uint32_t timeout = 0) =0;
        virtual ExtendedEpidGroupId* SGXGetExtendedEpidGroupId(uint32_t timeout_msec=0) =0;
        virtual PlainData* SGXSwitchExtendedEpidGroup(uint32_t x_group_id, uint32_t timeout = 0) =0;
};

#endif
