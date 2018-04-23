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
#ifndef __X509_PARSER_STATUS_H__
#define __X509_PARSER_STATUS_H__

typedef enum{
    STATUS_SUCCESS                       = 0,
    X509_STATUS_SUCCESS                  = 0,
    X509_GENERAL_ERROR,
    X509_STATUS_INVALID_VERSION,
    X509_STATUS_UNSUPPORTED_ALGORITHM,
    X509_STATUS_ENCODING_ERROR,
    X509_STATUS_INVALID_ARGS,
    X509_STATUS_UNSUPPORTED_CRITICAL_EXTENSION,
    X509_STATUS_UNSUPPORTED_TYPE,
    X509_STATUS_OCSP_FAILURE,
    X509_INVALID_SIGNATURE,
    X509_STATUS_UNKNOWN_OID,
    X509_STATUS_NOT_FOUND,
    X509_STATUS_OCSP_VERIFICATION_FAILED,
    X509_STATUS_UNSUPPORTED_PARAMETER,
    X509_STATUS_EXPIRED_CERTIFICATE,
    X509_STATUS_INTERNAL_ERROR,
    X509_STATUS_BASIC_CONSTRAINTS_VIOLATION,
    X509_STATUS_MEMORY_ALLOCATION_ERROR,
    SESSMGR_STATUS_INTERNAL_ERROR,
    STATUS_INVALID_PARAMS,
    STATUS_FAILURE,
}X509_Parser_Error_codes;


typedef X509_Parser_Error_codes  X509_STATUS;

#endif
