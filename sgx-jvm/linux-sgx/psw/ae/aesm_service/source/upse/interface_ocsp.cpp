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
#include "aeerror.h"
#include <cstddef>

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wredundant-decls"

#include "openssl/ocsp.h"

#pragma GCC diagnostic pop 


#include "oal/oal.h"
#include "pse_pr_sigma_common_defs.h"
#include "Buffer.h"
#include "network_encoding_wrapper.h"
#include "helper.h"

#undef OCSP_CLEAN
#undef _OPENSSL_FULL_INIT
#undef USE_CERTID_STACK


extern "C" int get_ocsp_req_size(BIO* reqbio, OCSP_REQUEST *pOcspReq)
{
#if defined(OCSP_CLEAN)
    
    
    return i2d_OCSP_REQUEST_bio(reqbio, pOcspReq);
#else
    return ASN1_i2d_bio(CHECKED_I2D_OF(OCSP_REQUEST, i2d_OCSP_REQUEST), reqbio, (unsigned char*)pOcspReq);
#endif

}

//
// we should 1) make sure this function is right, for the AESM and all its uses of OpenSSL and
// 2) move it to a more central/global location. 1 may require adding the so-called OpenSSL
// thread callbacks. Also, what about all the config settings? Are the defaults okay for us?
//
void OpenSSL_init()
{

#ifdef CRYPTO_malloc_init
    CRYPTO_malloc_init(); // OpenSSL 1.0 - Initialize malloc, free, etc for OpenSSL's use
#else
    OPENSSL_malloc_init(); // OpenSSL 1.1 - Initialize malloc, free, etc for OpenSSL's use
#endif

#if defined(_OPENSSL_FULL_INIT)
    
    SSL_library_init(); // Initialize OpenSSL's SSL libraries
    SSL_load_error_strings(); // Load SSL error strings
    ERR_load_BIO_strings(); // Load BIO error strings
    OpenSSL_add_all_algorithms(); // Load all available encryption algorithms
#else
    //
    // This needed by openssl, else OCSP_basic_verify fails
    //
    EVP_add_digest(EVP_sha1());
#endif

}

ae_error_t Get_OCSPResponse
    (
    /*in */ const char* urlOcspResponder,
    /*in */ const SIGMA_NONCE* ocspNonce,
    /*in */ const upse::Buffer& verifierCertificateDER,
    /*in */ const upse::Buffer& issuerCertificateDER,
    /*out*/ upse::Buffer& OcspResponseDER
    )
{

    X509* verifierX509Cert = NULL;
    X509* issuerX509Cert = NULL;

    OCSP_REQUEST* pOcspReq = NULL;
    OCSP_CERTID* pCertID = NULL;
    OCSP_ONEREQ *pOneReq = NULL;
#if defined(USE_CERTID_STACK)
    STACK_OF(OCSP_CERTID) *ids = NULL;
#endif
    BIO *reqbio = NULL;
    const EVP_MD *cert_id_md = NULL;
    void* ocsp_response = NULL;
    uint32_t ocsp_response_size = 0;
    //OCSP_RESPONSE         *pOcspResponse = NULL;
    char* ocsp_request = NULL;

    ae_error_t status = AE_FAILURE;

    SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("Get_OCSPResponse: (int) nonce = ", *((int*)*ocspNonce));


    OpenSSL_init();

    SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("init'd", 0);

    do
    {
        const unsigned char* x509CertNextDER = NULL;

        // Convert verifier to internal X509 data
        x509CertNextDER = verifierCertificateDER.getData();
        if (NULL == d2i_X509(&verifierX509Cert, &x509CertNextDER, verifierCertificateDER.getSize())) 
        {
            Helper::RemoveCertificateChain();
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("converted verifier", 0);

        // Convert issuer to internal X509 data
        x509CertNextDER = issuerCertificateDER.getData();
        if (NULL == d2i_X509(&issuerX509Cert, &x509CertNextDER, issuerCertificateDER.getSize())) 
        {
            Helper::RemoveCertificateChain();
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("converted issuer", 0);

        // Populate OCSP Request
        pOcspReq = OCSP_REQUEST_new();
        if (NULL == pOcspReq) break;

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("created new request", 0);

#if defined(USE_CERTID_STACK)
        ids = sk_OCSP_CERTID_new_null();
        if (NULL == ids) break;
#endif

        cert_id_md = EVP_sha1();

        // Add Verifier cert and issuer to OCSP Request
        pCertID = OCSP_cert_to_id(cert_id_md, verifierX509Cert, issuerX509Cert);
        if (NULL == pCertID)
        {
            Helper::RemoveCertificateChain();
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("added cert and issuer to ocsp request", 0);

#if defined(USE_CERTID_STACK)
        if (NULL == sk_OCSP_CERTID_push(ids, pCertID)) break;
#endif

        pOneReq = OCSP_request_add0_id(pOcspReq, pCertID);
        if (NULL == pOneReq) 
        {
            Helper::RemoveCertificateChain();
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("added id", 0);

        // Add nonce
        int retVal = OCSP_request_add1_nonce(pOcspReq, (uint8_t*)const_cast<SIGMA_NONCE*>(ocspNonce), NONCE_LENGTH);
        if (retVal <= 0) 
        {
            Helper::RemoveCertificateChain();
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("added nonce", 0);
        reqbio = BIO_new(BIO_s_mem());
        if (NULL == reqbio) break;

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("created new mem bio for request", 0);

        //
        // go from internal OpenSSL representation of request
        // to mem bio to binary
        //
        retVal = get_ocsp_req_size(reqbio, pOcspReq);
        if (retVal <= 0) 
        {
            Helper::RemoveCertificateChain();
            break;
        }

        size_t reqbio_num_write = BIO_number_written(reqbio);

        ocsp_request = (char*) malloc(reqbio_num_write);
        if (NULL == ocsp_request) break;

        memset(ocsp_request, 0x0, reqbio_num_write);

        retVal = BIO_read(reqbio, ocsp_request, static_cast<int>(reqbio_num_write));
        if (retVal <= 0) 
        {
            Helper::RemoveCertificateChain();
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("convertd to binary", 0);

        ae_error_t netStatus = aesm_network_send_receive(   urlOcspResponder, 
            (const uint8_t *) ocsp_request, 
            static_cast<uint32_t>(reqbio_num_write),
            (uint8_t **) &ocsp_response, 
            &ocsp_response_size, 
            POST, 
            true);
        if (AE_SUCCESS != netStatus) {
            status = netStatus;
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("called network stack, ocsp_response_size = ", ocsp_response_size);


        BIO* respbio = BIO_new(BIO_s_mem());
        if (NULL == respbio) break;

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("created new mem bio for response", 0);

        //
        // reverse what we did for req above,
        // go from binary to mem bio to internal OpenSSL representation of response
        //
        retVal = BIO_write(respbio, (const char*) ocsp_response, ocsp_response_size);
        if (retVal <= 0) break;

        ae_error_t ocspRespError = AE_SUCCESS;
        OCSP_RESPONSE* pOcspResponse = d2i_OCSP_RESPONSE_bio(respbio, NULL);
        BIO_free(respbio);
        if(NULL == pOcspResponse)
        {
            status = AESM_PSE_PR_OCSP_RESPONSE_INTERNAL_ERROR;
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("converted ocsp response to internal format", 0);

        // 
        // even though cse verifies/checks the ocsp response,
        // we can save time by doing the easy checks here
        // we'll check:
        // status
        // nonce
        // relationships among fields
        //
        retVal = OCSP_response_status(pOcspResponse);
        while (retVal != OCSP_RESPONSE_STATUS_SUCCESSFUL)
        {
            switch(retVal)
            {
            case OCSP_RESPONSE_STATUS_MALFORMEDREQUEST:
                ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_STATUS_MALFORMEDREQUEST; break;
            case OCSP_RESPONSE_STATUS_INTERNALERROR:
                ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_STATUS_INTERNALERROR; break;
            case OCSP_RESPONSE_STATUS_TRYLATER:
                ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_STATUS_TRYLATER; break;
            case OCSP_RESPONSE_STATUS_SIGREQUIRED:
                ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_STATUS_SIGREQUIRED; break;
            case OCSP_RESPONSE_STATUS_UNAUTHORIZED:
                ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_STATUS_UNAUTHORIZED; break;
            default:
                ocspRespError = AESM_PSE_PR_NO_OCSP_RESPONSE_ERROR; break;
            }
            break;
        }

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("checked ocsp response status: ", ocspRespError);

        if ((AE_SUCCESS != ocspRespError) && (AESM_PSE_PR_OCSP_RESPONSE_STATUS_TRYLATER != ocspRespError)) {
            if (AESM_PSE_PR_OCSP_RESPONSE_STATUS_INTERNALERROR != ocspRespError) {
                // According to RFC6960, the response "internalError" indicates that the OCSP responder
                // reached an inconsistent internal state.The query should be retried,
                // potentially with another responder. So we don't delete cert chain here
                Helper::RemoveCertificateChain();
            }
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_OCSP_RESPONSE_ERROR]);
        }

        while (AE_SUCCESS == ocspRespError)
        {
            OCSP_BASICRESP* bs = OCSP_response_get1_basic(pOcspResponse);

            if (!bs)
            {
                ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_INTERNAL_ERROR;
                break;
            }

            {
                //
                // above status check is "external", doesn't include revoked or not
                //
                int respStatus, reason;

                ASN1_GENERALIZEDTIME *rev, *thisupd, *nextupd;

                if(!OCSP_resp_find_status(bs, pCertID, &respStatus, &reason, &rev, &thisupd, &nextupd))
                {
                    SGX_DBGPRINT_PRINT_STRING("OCSP: No status found.");
                }
                else if (V_OCSP_CERTSTATUS_REVOKED == respStatus) {
                    ocspRespError = AESM_LTP_PSE_CERT_REVOKED;
                    AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_PSE_CERT_REVOCATION]);
                    break;
                }

            }

            int i;
            if ((i = OCSP_check_nonce(pOcspReq, bs)) <= 0)
            {
                if (i == -1)
                {
                    ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_NO_NONCE_ERROR;
                }
                else
                {
                    ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_NONCE_VERIFY_ERROR;
                }
                break;
            }

            SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("checked nonce: ", ocspRespError);

            //
            // following checks relationships between fields in response, but does not verify signature
            // cse will do that (along with other checks we do above)
            //
            i = OCSP_basic_verify(bs, NULL, NULL, OCSP_NOCHECKS|OCSP_NOEXPLICIT|OCSP_NOVERIFY|OCSP_NOCHAIN|OCSP_NOSIGS);

            SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("verified ocsp response: ", i);

            if(i <= 0)
            {
                ocspRespError = AESM_PSE_PR_OCSP_RESPONSE_VERIFY_ERROR;
                break;
            }
            break;

        }
        if (AE_SUCCESS != ocspRespError)
        {
            status = ocspRespError;
            break;
        }
        if (AE_FAILED(OcspResponseDER.Alloc((uint8_t*)ocsp_response, ocsp_response_size))) break;

        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("created ocsp response in der format", 0);

        status = AE_SUCCESS;

    } while (0);

    if (ocsp_response) 
    {
        aesm_free_network_response_buffer((uint8_t*)ocsp_response);
        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("freed network response buffer", 0);
    }

    if (verifierX509Cert) 
    {
        X509_free(verifierX509Cert);
        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("freed verifier cert", 0);
    }

    if (issuerX509Cert) 
    {
        X509_free(issuerX509Cert);
        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("freed issuer cert", 0);
    }

    if (pOcspReq)
    {    
        OCSP_REQUEST_free(pOcspReq);
        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("freed ocsp request", 0);
    }

    if (NULL != reqbio)
    {
        BIO_free(reqbio);
        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("freed request bio", 0);
    }
    if (NULL != ocsp_request)
    {
        free(ocsp_request);
        SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP("freed binary ocsp request", 0);
    }
#if defined(USE_CERTID_STACK)
    if (ids) sk_OCSP_CERTID_free(ids);
#endif

    return status;
}

