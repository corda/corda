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

#include "sigma_helper.h"
#include "uecall_bridge.h"
#include "helper.h"
#include <Buffer.h>
#include "pse_pr_sigma_common_defs.h"
#include "se_time.h"
#include "endpoint_select_info.h"

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wredundant-decls"

#include "openssl/ocsp.h"
#include "openssl/ssl.h"

#pragma GCC diagnostic pop 


#include "interface_ocsp.h"
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string>
#include <memory.h>

#include "provision_msg.h"
#include "aesm_logic.h"
#include "oal/oal.h"
#include "network_encoding_wrapper.h"

#ifndef UINT16_MAX
#define UINT16_MAX 0xFFFF
#endif

#define MAX_OCSP_BUSY_RETRIES   (3)
#define OCSP_BUSY_RETRY_SLEEP_MILLISECONDS  (50)

upse::Buffer SigmaHelper::m_gid;

#include "ivk_ca_root_der.hh"


ae_error_t SigmaHelper::SetGID(upse::Buffer& gid)
{
    return m_gid.Clone(gid);
}

ae_error_t SigmaHelper::GetRLsFromServer
    (   /*out*/ upse::Buffer& sigRlOut,
    /*out*/ upse::Buffer& privRlOut
    )
{

    //
    // iKGF serves up binary (legacy) versions of EPID 1.1 RLs
    // all we need to do is convey the GID in the URL itself
    // for example, https://trustedservices.intel.com/content/crl/Signature_<GID>.crl
    // so, we get url out of config file and concatenate with filename that's
    // specific to the type of RL

    ae_error_t sigRetValue = AE_FAILURE;
    ae_error_t privRetValue = AE_FAILURE;

    const char *url = EndpointSelectionInfo::instance().get_server_url(REVOCATION_LIST_RETRIEVAL);
    if (url == NULL)
    {
        return OAL_CONFIG_FILE_ERROR;
    }

    uint8_t* p1 = const_cast<uint8_t*>(m_gid.getData());

    do {

        if ((m_gid.getSize() < 1) || (m_gid.getSize() > 4)) break;
        char msg[9];
        for (unsigned i = 0; i < m_gid.getSize(); i++)
        {
            
            sprintf_s((char*) msg+2*i, 3, "%02X", *(p1+m_gid.getSize()-1-i));
        }
        std::string gidString(msg);

        unsigned numLeading0s = 8 - static_cast<unsigned>(gidString.length());

        for (unsigned i = 0; i < numLeading0s; i++)
        {
            gidString = '0' + gidString;
        }

        //if config file entry doesn't have trailing "/" , add it.
        std::string s_url = url;
        if(s_url.size()>0&&s_url[s_url.size()-1]!='/')
            s_url+='/';
        std::string stringUrl = s_url + "Signature_" + gidString + ".crl";

        uint8_t *recv=NULL;
        uint32_t recv_size = 0;
        sigRetValue = AESMNetworkEncoding::aesm_send_recv_msg(stringUrl.c_str(), NULL, 0, recv, recv_size, GET, false);

        if (AE_SUCCESS != sigRetValue)
        {
            sigRlOut.Alloc(0);
        }
        else
        {
            sigRlOut.Alloc(recv_size);
            upse::BufferWriter bw(sigRlOut);
            bw.writeRaw(recv, recv_size);
            AESMNetworkEncoding::aesm_free_response_msg(recv);
        }

        stringUrl = s_url + "Product_" + gidString + ".crl";
        recv=NULL;
        recv_size = 0;
        privRetValue = AESMNetworkEncoding::aesm_send_recv_msg(stringUrl.c_str(), NULL, 0, recv, recv_size, GET, false);

        if (AE_SUCCESS != privRetValue)
        {
            privRlOut.Alloc(0);
        }
        else
        {
            privRlOut.Alloc(recv_size);
            upse::BufferWriter bw(privRlOut);
            bw.writeRaw(recv, recv_size);
            aesm_free_network_response_buffer(recv);
        }
    } while (0);

    if (AE_FAILED(privRetValue))
    {
        SGX_DBGPRINT_PRINT_STRING_LTP("PrivRL not retrieved: continuing without PrivRL");
    }
    if (AE_FAILED(sigRetValue))
    {
        SGX_DBGPRINT_PRINT_STRING_LTP("SigRL not retrieved: continuing without SigRL");
    }

    if ((privRetValue == AE_SUCCESS) && (sigRetValue == AE_SUCCESS)) {
        return AE_SUCCESS;
    }
    else if (sigRetValue != AE_SUCCESS) {
        return AESM_PSE_PR_GET_SIGRL_ERROR;
    }
    else {
        return AESM_PSE_PR_GET_PRIVRL_ERROR;
    }
}

void SigmaHelper::GetRootCA(upse::Buffer& b)
{
    b.Alloc(sizeof(caRootDER));
    upse::BufferWriter bw(b);
    bw.writeRaw(caRootDER, sizeof(caRootDER));
}


ae_error_t SigmaHelper::GetOcspResponseFromServer
    (
    /*in */ const std::list<upse::Buffer>& certChain,
    /*in */ const OCSP_REQ& ocspReq,
    /*out*/ upse::Buffer& ocspResp
    )
{
    ae_error_t status = AE_FAILURE;

    int nPaddedBytes = 0;
    int nTotalOcspBytes = 0;

    do
    {
        if (ocspReq.ReqType == NO_OCSP)
        {
            status = AE_SUCCESS;
            break;
        }

        const char *url  = EndpointSelectionInfo::instance().get_server_url( PSE_OCSP);
        if (url == NULL){
            return OAL_CONFIG_FILE_ERROR;
        }

        // Load the root certificate into a local buffer
        upse::Buffer rootCert;
        SigmaHelper::GetRootCA(rootCert);

        std::list<upse::Buffer> ocspResponseList;

        // loop through chain and get an OCSP Response for each certificate/issuer pair
        bool fDone = false;
        //
        // certs were added leaf to root direction (assuming server functions according to spec)
        //
        std::list<upse::Buffer>::const_iterator itCertificate = certChain.begin();
        do
        {
            if (itCertificate == certChain.end())
            {
                status = AE_FAILURE;
                break;
            }

            upse::Buffer ocspResponse;
            const upse::Buffer& verifierCertificate = *itCertificate;

            ++itCertificate;

            int busy_loop = 0;
            do
            {
                if (itCertificate != certChain.end())
                {
                    const upse::Buffer& issuerCertificate = *itCertificate;
                    status = Get_OCSPResponse(url, &ocspReq.OcspNonce, verifierCertificate, issuerCertificate, ocspResponse);
                }
                else
                {
                    fDone = true;
                    const upse::Buffer& issuerCertificate = rootCert;
                    status = Get_OCSPResponse(url, &ocspReq.OcspNonce, verifierCertificate, issuerCertificate, ocspResponse);
                }

                if (AESM_PSE_PR_OCSP_RESPONSE_STATUS_TRYLATER != status)
                    break;

                se_sleep(OCSP_BUSY_RETRY_SLEEP_MILLISECONDS);

            } while (busy_loop++ < MAX_OCSP_BUSY_RETRIES);

            if (AE_FAILED(status))
                break;

            nPaddedBytes += REQUIRED_PADDING_DWORD_ALIGNMENT(ocspResponse.getSize());
            nTotalOcspBytes += ocspResponse.getSize();

            ocspResponseList.push_back(ocspResponse);

        } while (!fDone);

        if (AE_FAILED(status))
            break;

        if (0 == ocspResponseList.size())
        {
            status = AE_FAILURE;
            break;
        }


        nPaddedBytes = REQUIRED_PADDING_DWORD_ALIGNMENT(nTotalOcspBytes);
        if(UINT16_MAX-((int)sizeof(SIGMA_VLR_HEADER) + nPaddedBytes) < nTotalOcspBytes){
            status = AE_FAILURE;
            break;
        }
        int nLength = static_cast<int>(sizeof(SIGMA_VLR_HEADER)) + nPaddedBytes + nTotalOcspBytes;

        ocspResp.Alloc(nLength);

        upse::BufferWriter bw(ocspResp);
        uint8_t* p;
        status = bw.reserve(nLength, &p);
        if (AE_FAILED(status))
            break;
        OCSP_RESPONSE_VLR* pVLR = (OCSP_RESPONSE_VLR*)p;

        pVLR->VlrHeader.ID = OCSP_RESPONSE_VLR_ID;
        pVLR->VlrHeader.PaddedBytes = (UINT8)nPaddedBytes;
        pVLR->VlrHeader.Length = (UINT16)nLength;

        memset(pVLR->OcspResponse, 0, nPaddedBytes + nTotalOcspBytes);

        int nNext = 0;

        //
        // order above doesn't really matter since it's between verifier/host and ocsp responder
        // and each request/response is independent
        // spec basically says what's correct here
        // but we'll leave condition to show how to traverse in either order
        //
#if !defined(LEAFTOROOT)
#error LEAFTOROOT not defined
#endif

#if !LEAFTOROOT
        //
        // this clause adds responses from root to leaf
        //
        SGX_DBGPRINT_PRINT_STRING_LTP("root ocsp to leaf ocsp direction");
        std::list<upse::Buffer>::reverse_iterator itRespList = ocspResponseList.rbegin();
        for ( ; itRespList != ocspResponseList.rend(); ++itRespList)
        {
            const upse::Buffer& item = *itRespList;
            memcpy_s(pVLR->OcspResponse + nNext, item.getSize(), item.getData(), item.getSize());
            nNext += item.getSize();
        }
#else
        SGX_DBGPRINT_PRINT_STRING_LTP("leaf ocsp to root ocsp direction");

        //
        // this clause adds responses from leaf to root
        //
        std::list<upse::Buffer>::iterator itRespList = ocspResponseList.begin();
        for ( ; itRespList != ocspResponseList.end(); ++itRespList)
        {
            const upse::Buffer& item = *itRespList;
            memcpy_s(pVLR->OcspResponse + nNext, item.getSize(), item.getData(), item.getSize());
            nNext += item.getSize();
        }
#endif

        Helper::write_ocsp_response_vlr(ocspResp);

        status = AE_SUCCESS;

    } while (0);

    if (status == OAL_NETWORK_UNAVAILABLE_ERROR)
    {
        if (ocspReq.ReqType == CACHED && AE_SUCCEEDED(Helper::read_ocsp_response_vlr(ocspResp)))
        {
            status = AE_SUCCESS;
        }
    }

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);

    return status;
}


