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

#include "helper.h"
#include <Buffer.h>
#include "aeerror.h"
#include "pse_pr_sigma_common_defs.h"
#include "oal/oal.h"
#include "se_wrapper.h"
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <cstddef>
#ifndef UINT16_MAX
#define UINT16_MAX 0xFFFF
#endif
#ifndef INT32_MAX
#define INT32_MAX 0x7FFFFFFF
#endif

#define CERT_FILENAME_POSTFIX_FORMAT     "%02d.cer"

#define TOKEN_SEPARATOR         ';'

#define SAFE_DELETE_ARRAY(x) if (x) { delete[] (unsigned char*)(x); (x) = NULL; }

static char* get_next_token(char* _str, const char _delim, char** _context)
{
    char* result;
    if (NULL == _context)
        return NULL;
    if (NULL == _str && NULL == *_context)
        return NULL;

    if (NULL == _str)
        _str = *_context;

    char *p = strchr(_str, _delim);
    if (NULL == p)
    {
        result = _str;
        *_context = NULL;
    }
    else
    {
        *p = '\0';
        result = _str;
        *_context = result + strlen(result) + 1;
    }

    return result;
}


bool Helper::noPseCert()
{
    std::list<upse::Buffer> certChain;
    return AE_FAILED(Helper::LoadCertificateChain(certChain));
}

bool Helper::noLtpBlob()
{
    upse::Buffer pairing_blob;
    return AE_FAILED(read_ltp_blob(pairing_blob));
}

ae_error_t Helper::read_ltp_blob(upse::Buffer& pairing_blob)
{
    return upsePersistentStorage::Read(PSE_PR_LT_PAIRING_FID, pairing_blob);
}

ae_error_t Helper::read_ltp_blob(pairing_blob_t& pairing_blob)
{
    upse::Buffer buffer_pairing_blob;
    memset(&pairing_blob, 0, sizeof(pairing_blob));
    ae_error_t status = read_ltp_blob(buffer_pairing_blob);   
    if (AE_SUCCESS != status)
        return status;

    if (sizeof(pairing_blob) != buffer_pairing_blob.getSize())
        return AE_FAILURE;

    memcpy_s(&pairing_blob, sizeof(pairing_blob), buffer_pairing_blob.getData(), buffer_pairing_blob.getSize());
    return AE_SUCCESS;
}

ae_error_t Helper::write_ltp_blob(upse::Buffer& pairing_blob)
{
    return upsePersistentStorage::Write(PSE_PR_LT_PAIRING_FID, pairing_blob);
}

ae_error_t Helper::delete_ltp_blob()
{
    return upsePersistentStorage::Delete(PSE_PR_LT_PAIRING_FID);
}

ae_error_t Helper::read_ocsp_response_vlr(upse::Buffer& ocsp_response_vlr)
{
    return upsePersistentStorage::Read(PSE_PR_OCSPRESP_FID, ocsp_response_vlr);
}

ae_error_t Helper::write_ocsp_response_vlr(upse::Buffer& ocsp_response_vlr)
{
    return upsePersistentStorage::Write(PSE_PR_OCSPRESP_FID, ocsp_response_vlr);
}

ae_error_t Helper::delete_ocsp_response_vlr()
{
    return upsePersistentStorage::Delete(PSE_PR_OCSPRESP_FID);
}



uint32_t Helper::ltpBlobPsdaSvn(const pairing_blob_t& pairing_blob)
{
    uint32_t retval = pairing_blob.plaintext.cse_sec_prop.ps_hw_sec_info.psdaSvn;
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retval, retval);
    return retval;
}


uint32_t Helper::ltpBlobCseGid(const pairing_blob_t& pairing_blob)
{
    return *const_cast<uint32_t*>(&pairing_blob.plaintext.cse_sec_prop.ps_hw_gid);
}


#define REQUIRED_PADDING_DWORD_ALIGNMENT(x) ((x % 4) ? (4 - (x%4)) : 0)

ae_error_t Helper::RemoveCertificateChain()
{
    ae_error_t status = AESM_PSE_PR_CERT_DELETE_ERROR;

    char* szParseString = NULL;
    int nError = 0;

    do
    {
        // Read the delimited file of certificate names
        upse::Buffer certChainListBuffer;
        if (AE_SUCCESS != upsePersistentStorage::Read(PSE_PR_CERTIFICATE_CHAIN_FID, certChainListBuffer))
        {
            break;
        }

        szParseString = (char*) calloc(1, certChainListBuffer.getSize() + 1);
        if (NULL == szParseString)
        {
            break;
        }

        memcpy_s(szParseString, certChainListBuffer.getSize(), certChainListBuffer.getData(), certChainListBuffer.getSize());
        szParseString[certChainListBuffer.getSize()] = '\0';

        char* nextToken = NULL;
        char* szCertificateNamePostfix = get_next_token(szParseString, TOKEN_SEPARATOR, &nextToken); 

        aesm_data_id_t fileid = PSE_PR_CERTIFICATE_FID;
        // For each certificate name, delete the file
        while (NULL != szCertificateNamePostfix)
        {
            if (AE_SUCCESS != upsePersistentStorage::Delete(fileid++))
            {
                ++nError;
            }
            if (PSE_PR_CERTIFICATE_FID_MAX == fileid)
            {
                break;
            }
            szCertificateNamePostfix = get_next_token(NULL, TOKEN_SEPARATOR, &nextToken);
        }

        if (PSE_PR_CERTIFICATE_FID_MAX == fileid)
        {
            break;
        }
        if (AE_SUCCESS != upsePersistentStorage::Delete(PSE_PR_CERTIFICATE_CHAIN_FID))
        {
            ++nError;
        }

        if (0 != nError)
        {
            break;
        }

        status = AE_SUCCESS;
    } while (0);

    if (NULL != szParseString)
    {
        free(szParseString);
    }

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);

    return status;
}

ae_error_t Helper::SaveCertificateChain( /*in */ std::list<upse::Buffer>& certChain)
{
    ae_error_t status = AESM_PSE_PR_CERT_SAVE_ERROR;

    char* szParseString = NULL; 
    int nLen, pos=0;

    do
    {
        RemoveCertificateChain();

        // Allocate enough space for the list of names and separator character
        if(((size_t)(INT32_MAX-1))/sizeof(CERT_FILENAME_POSTFIX_FORMAT) < certChain.size() )
            break;
        int nBytes = static_cast<int>(certChain.size() * sizeof(CERT_FILENAME_POSTFIX_FORMAT)) + 1;
        szParseString = (char*)calloc(1, nBytes);
        if (NULL == szParseString)
        {
            break;
        }

        char* szNextCertNamePostfix = szParseString;

        int fileNo = 0;
        char szFilenamePostfix[80];
        std::list<upse::Buffer>::iterator it = certChain.begin();
        aesm_data_id_t fileid = PSE_PR_CERTIFICATE_FID;
        while (it != certChain.end())
        {
            nLen = sprintf_s(szFilenamePostfix, sizeof(szFilenamePostfix), CERT_FILENAME_POSTFIX_FORMAT, ++fileNo);
            if (AE_SUCCESS != upsePersistentStorage::Write(fileid++, *it))
            {
                break;
            }
            if (PSE_PR_CERTIFICATE_FID_MAX == fileid)
            {
                break;
            }

            strcpy_s(szNextCertNamePostfix, nBytes-pos, szFilenamePostfix);
            pos += nLen;
            szNextCertNamePostfix += nLen;

            ++it;
            if (it != certChain.end())
            {
                if (pos >= nBytes)
                {
                    break;
                }
                *szNextCertNamePostfix = ';';
                pos++;
                ++szNextCertNamePostfix;
            }
        }

        if ((szNextCertNamePostfix - szParseString) > nBytes)
        {
            break;
        }
        nBytes = static_cast<int>(szNextCertNamePostfix - szParseString);
        upse::Buffer nameListBuffer;
        if (AE_FAILED(nameListBuffer.Alloc(nBytes)))
            break;
        upse::BufferWriter bw(nameListBuffer);
        bw.writeRaw((uint8_t*)szParseString, nBytes);
        if (AE_SUCCESS != upsePersistentStorage::Write(PSE_PR_CERTIFICATE_CHAIN_FID, nameListBuffer))
        {
            break;
        }

        status = AE_SUCCESS;

    } while (0);

    if (NULL != szParseString)
    {
        free(szParseString);
    }

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);

    return status;
}


ae_error_t Helper::LoadCertificateChain( /*out*/ std::list<upse::Buffer>& certChain)
{
    ae_error_t status = AESM_PSE_PR_CERT_LOAD_ERROR;

    char* szParseString = NULL;

    do
    {
        // Read the delimited file of certificate names
        upse::Buffer certChainListBuffer;
        if (AE_SUCCESS != upsePersistentStorage::Read(PSE_PR_CERTIFICATE_CHAIN_FID, certChainListBuffer))
            break;

        szParseString = (char*)calloc(1, certChainListBuffer.getSize() + 1);
        if (NULL == szParseString)
            break;

        memcpy_s(szParseString,  certChainListBuffer.getSize(), certChainListBuffer.getData(), certChainListBuffer.getSize());
        szParseString[certChainListBuffer.getSize()] = '\0';

        char* nextToken = NULL;
        char* szCertificateNamePostfix = get_next_token(szParseString, TOKEN_SEPARATOR, &nextToken); 

        // For each certificate name, read the certificate and save in list
        aesm_data_id_t fileid = PSE_PR_CERTIFICATE_FID;
        while (NULL != szCertificateNamePostfix)
        {
            upse::Buffer cert;
            if (AE_SUCCESS != upsePersistentStorage::Read(fileid++, cert))
            {
                break;
            }
            if (PSE_PR_CERTIFICATE_FID_MAX == fileid)
            {
                break;
            }

            certChain.push_back(cert);

            szCertificateNamePostfix = get_next_token(NULL, TOKEN_SEPARATOR, &nextToken);
        }

        if (NULL != szCertificateNamePostfix)
        {
            break;
        }

        status = AE_SUCCESS;

    } while (0);

    if (NULL != szParseString)
    {
        free(szParseString);
    }

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);

    return status;
}


ae_error_t Helper::PrepareCertificateChainVLR( /*in*/ std::list<upse::Buffer>& certChain, /*out*/ upse::Buffer& certChainVLR)
{
    ae_error_t status = AESM_PSE_PR_LOAD_VERIFIER_CERT_ERROR;

    try
    {
        do
        {
            int nPaddedBytes = 0;
            int nCertChain = 0;

#if !defined(LEAFTOROOT)
#error LEAFTOROOT not #defined
#endif

            //
            // spec'd behavior is to receive certs in leaft to root order
            // then, it only makes sense to store them leaf to root
            // but sigma wants them root to leaf
            // we'll leave the #if here since, cumulatively, it shows how to traverse
            // in both directions
            //
#if !LEAFTOROOT
            SGX_DBGPRINT_PRINT_STRING_LTP("leaf cert to root cert direction, padding");

            std::list<upse::Buffer>::reverse_iterator it;
            for (it = certChain.rbegin(); it != certChain.rend(); ++it)
            {
                int nSize = (*it).getSize();
                nPaddedBytes += REQUIRED_PADDING_DWORD_ALIGNMENT(nSize);
                nCertChain += nSize;
            }
#else
            SGX_DBGPRINT_PRINT_STRING_LTP("root cert to leaf cert direction, padding");
            std::list<upse::Buffer>::iterator it;
            for (it = certChain.begin(); it != certChain.end(); ++it)
            {
                int nSize = (*it).getSize();
                nPaddedBytes += REQUIRED_PADDING_DWORD_ALIGNMENT(nSize);
                nCertChain += nSize;
            }
#endif

            SGX_DBGPRINT_PRINT_STRING_LTP("less cert padding");
            //NRG: This doesn't work, but should. It should replace the previous
            nPaddedBytes = REQUIRED_PADDING_DWORD_ALIGNMENT(nCertChain);

            if(UINT16_MAX - ((int)sizeof(SIGMA_VLR_HEADER) + nPaddedBytes) < nCertChain){
                break;
            }
            int nLength = static_cast<int>(sizeof(SIGMA_VLR_HEADER)) + nPaddedBytes + nCertChain;

            certChainVLR.Alloc(nLength);

            upse::BufferWriter bw(certChainVLR);
            VERIFIER_CERT_CHAIN_VLR* pVLR;
            uint8_t* p;
            if (AE_FAILED(bw.reserve(nLength, &p)))
                break;
            pVLR = (VERIFIER_CERT_CHAIN_VLR*)p;

            pVLR->VlrHeader.ID = VERIFIER_CERTIFICATE_CHAIN_VLR_ID;
            pVLR->VlrHeader.PaddedBytes = (UINT8)nPaddedBytes;
            pVLR->VlrHeader.Length = (UINT16)nLength;

            memset(pVLR->VerifierCertificateChain, 0, nPaddedBytes + nCertChain);
            int ndx = 0;

            //
            // see above 
            //
#if (!LEAFTOROOT)
            SGX_DBGPRINT_PRINT_STRING_LTP("leaf cert to root cert direction");
            for (it = certChain.rbegin(); it != certChain.rend(); ++it)
            {
                memcpy_s(pVLR->VerifierCertificateChain + ndx, (*it).getSize(), (*it).getData(), (*it).getSize());
                ndx += (*it).getSize();
            }
#else
            SGX_DBGPRINT_PRINT_STRING_LTP("root cert to leaf cert direction");
            for (it = certChain.begin(); it != certChain.end(); ++it)
            {
                memcpy_s(pVLR->VerifierCertificateChain + ndx, (*it).getSize(), (*it).getData(), (*it).getSize());
                ndx += (*it).getSize();
            }
#endif

            status = AE_SUCCESS;

        } while (0);
    } catch(...)
    {
    }

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);
    return status;
}


ae_error_t upsePersistentStorage::Delete(aesm_data_id_t data_id)
{
    ae_error_t status = AESM_PSE_PR_PERSISTENT_STORAGE_DELETE_ERROR;

    do {
        char filepath[MAX_PATH];
        ae_error_t delerror = aesm_get_pathname(FT_PERSISTENT_STORAGE, data_id, filepath, MAX_PATH);
        if (AE_SUCCESS != delerror)
        {
            break;
        }
        if (0 != se_delete_tfile(filepath))
        {
            break;
        }

        status = AE_SUCCESS;

    } while (0);

    return status;
}


ae_error_t upsePersistentStorage::Read(aesm_data_id_t data_id, upse::Buffer& data)
{
    ae_error_t status = AESM_PSE_PR_PERSISTENT_STORAGE_READ_ERROR;

    uint8_t* tempData = NULL;

    do {
        ae_error_t readError;
        uint32_t sizeInout;
        readError = aesm_query_data_size(FT_PERSISTENT_STORAGE, data_id, &sizeInout);
        if ((readError != AE_SUCCESS) || (sizeInout == 0))
        {
            break;
        }
        tempData = (uint8_t*) malloc(sizeInout);
        if (tempData == NULL)
        {
            break;
        }
        readError = aesm_read_data(FT_PERSISTENT_STORAGE, data_id, tempData, &sizeInout);
        if (AE_SUCCESS != readError)
        {
            break;
        }
        data.Alloc(sizeInout);
        upse::BufferWriter bw(data);
        bw.writeRaw(tempData, sizeInout);

        status = AE_SUCCESS;
    } while (0);

    if (NULL != tempData)
        free(tempData);

    return status;
}


ae_error_t upsePersistentStorage::Write(aesm_data_id_t data_id, upse::Buffer& data)
{
    ae_error_t status = AESM_PSE_PR_PERSISTENT_STORAGE_WRITE_ERROR;

    do
    {
        if (AE_FAILED(aesm_write_data(FT_PERSISTENT_STORAGE, data_id, data.getData(), data.getSize())))
            break;

        status = AE_SUCCESS;

    } while (0);

    return status;
}
