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

#ifndef _SGX_ERROR_H_
#define _SGX_ERROR_H_

#define SGX_MK_ERROR(x)              (0x00000000|(x))

typedef enum _status_t
{
    SGX_SUCCESS                  = SGX_MK_ERROR(0x0000),

    SGX_ERROR_UNEXPECTED         = SGX_MK_ERROR(0x0001),      /* Unexpected error */
    SGX_ERROR_INVALID_PARAMETER  = SGX_MK_ERROR(0x0002),      /* The parameter is incorrect */
    SGX_ERROR_OUT_OF_MEMORY      = SGX_MK_ERROR(0x0003),      /* Not enough memory is available to complete this operation */
    SGX_ERROR_ENCLAVE_LOST       = SGX_MK_ERROR(0x0004),      /* Enclave lost after power transition or used in child process created by linux:fork() */
    SGX_ERROR_INVALID_STATE      = SGX_MK_ERROR(0x0005),      /* SGX API is invoked in incorrect order or state */

    SGX_ERROR_INVALID_FUNCTION   = SGX_MK_ERROR(0x1001),      /* The ecall/ocall index is invalid */
    SGX_ERROR_OUT_OF_TCS         = SGX_MK_ERROR(0x1003),      /* The enclave is out of TCS */
    SGX_ERROR_ENCLAVE_CRASHED    = SGX_MK_ERROR(0x1006),      /* The enclave is crashed */
    SGX_ERROR_ECALL_NOT_ALLOWED  = SGX_MK_ERROR(0x1007),      /* The ECALL is not allowed at this time, e.g. ecall is blocked by the dynamic entry table, or nested ecall is not allowed during initialization */
    SGX_ERROR_OCALL_NOT_ALLOWED  = SGX_MK_ERROR(0x1008),      /* The OCALL is not allowed at this time, e.g. ocall is not allowed during exception handling */
    SGX_ERROR_STACK_OVERRUN      = SGX_MK_ERROR(0x1009),      /* The enclave is running out of stack */

    SGX_ERROR_UNDEFINED_SYMBOL   = SGX_MK_ERROR(0x2000),      /* The enclave image has undefined symbol. */
    SGX_ERROR_INVALID_ENCLAVE    = SGX_MK_ERROR(0x2001),      /* The enclave image is not correct. */
    SGX_ERROR_INVALID_ENCLAVE_ID = SGX_MK_ERROR(0x2002),      /* The enclave id is invalid */
    SGX_ERROR_INVALID_SIGNATURE  = SGX_MK_ERROR(0x2003),      /* The signature is invalid */
    SGX_ERROR_NDEBUG_ENCLAVE     = SGX_MK_ERROR(0x2004),      /* The enclave is signed as product enclave, and can not be created as debuggable enclave. */
    SGX_ERROR_OUT_OF_EPC         = SGX_MK_ERROR(0x2005),      /* Not enough EPC is available to load the enclave */
    SGX_ERROR_NO_DEVICE          = SGX_MK_ERROR(0x2006),      /* Can't open SGX device */
    SGX_ERROR_MEMORY_MAP_CONFLICT= SGX_MK_ERROR(0x2007),      /* Page mapping failed in driver */
    SGX_ERROR_INVALID_METADATA   = SGX_MK_ERROR(0x2009),      /* The metadata is incorrect. */
    SGX_ERROR_DEVICE_BUSY        = SGX_MK_ERROR(0x200c),      /* Device is busy, mostly EINIT failed. */
    SGX_ERROR_INVALID_VERSION    = SGX_MK_ERROR(0x200d),      /* Metadata version is inconsistent between uRTS and sgx_sign or uRTS is incompatible with current platform. */
    SGX_ERROR_MODE_INCOMPATIBLE  = SGX_MK_ERROR(0x200e),      /* The target enclave 32/64 bit mode or sim/hw mode is incompatible with the mode of current uRTS. */
    SGX_ERROR_ENCLAVE_FILE_ACCESS = SGX_MK_ERROR(0x200f),     /* Can't open enclave file. */
    SGX_ERROR_INVALID_MISC        = SGX_MK_ERROR(0x2010),     /* The MiscSelct/MiscMask settings are not correct.*/
    SGX_ERROR_INVALID_LAUNCH_TOKEN = SGX_MK_ERROR(0x2011),    /* The launch token is not correct.*/

    SGX_ERROR_MAC_MISMATCH       = SGX_MK_ERROR(0x3001),      /* Indicates verification error for reports, sealed datas, etc */
    SGX_ERROR_INVALID_ATTRIBUTE  = SGX_MK_ERROR(0x3002),      /* The enclave is not authorized */
    SGX_ERROR_INVALID_CPUSVN     = SGX_MK_ERROR(0x3003),      /* The cpu svn is beyond platform's cpu svn value */
    SGX_ERROR_INVALID_ISVSVN     = SGX_MK_ERROR(0x3004),      /* The isv svn is greater than the enclave's isv svn */
    SGX_ERROR_INVALID_KEYNAME    = SGX_MK_ERROR(0x3005),      /* The key name is an unsupported value */

    SGX_ERROR_SERVICE_UNAVAILABLE       = SGX_MK_ERROR(0x4001),   /* Indicates aesm didn't respond or the requested service is not supported */
    SGX_ERROR_SERVICE_TIMEOUT           = SGX_MK_ERROR(0x4002),   /* The request to aesm timed out */
    SGX_ERROR_AE_INVALID_EPIDBLOB       = SGX_MK_ERROR(0x4003),   /* Indicates epid blob verification error */
    SGX_ERROR_SERVICE_INVALID_PRIVILEGE = SGX_MK_ERROR(0x4004),   /* Enclave has no privilege to get launch token */
    SGX_ERROR_EPID_MEMBER_REVOKED       = SGX_MK_ERROR(0x4005),   /* The EPID group membership is revoked. */
    SGX_ERROR_UPDATE_NEEDED             = SGX_MK_ERROR(0x4006),   /* SGX needs to be updated */
    SGX_ERROR_NETWORK_FAILURE           = SGX_MK_ERROR(0x4007),   /* Network connecting or proxy setting issue is encountered */
    SGX_ERROR_AE_SESSION_INVALID        = SGX_MK_ERROR(0x4008),   /* Session is invalid or ended by server */
    SGX_ERROR_BUSY                      = SGX_MK_ERROR(0x400a),   /* The requested service is temporarily not availabe */
    SGX_ERROR_MC_NOT_FOUND              = SGX_MK_ERROR(0x400c),   /* The Monotonic Counter doesn't exist or has been invalided */
    SGX_ERROR_MC_NO_ACCESS_RIGHT        = SGX_MK_ERROR(0x400d),   /* Caller doesn't have the access right to specified VMC */
    SGX_ERROR_MC_USED_UP                = SGX_MK_ERROR(0x400e),   /* Monotonic counters are used out */
    SGX_ERROR_MC_OVER_QUOTA             = SGX_MK_ERROR(0x400f),   /* Monotonic counters exceeds quota limitation */
    SGX_ERROR_KDF_MISMATCH              = SGX_MK_ERROR(0x4011),   /* Key derivation function doesn't match during key exchange */
    SGX_ERROR_UNRECOGNIZED_PLATFORM     = SGX_MK_ERROR(0x4012),   /* EPID Provisioning failed due to platform not recognized by backend server*/

    SGX_ERROR_NO_PRIVILEGE              = SGX_MK_ERROR(0x5002),   /* Not enough privilege to perform the operation */

    
    /* SGX errors are only used in the file API when there is no appropriate EXXX (EINVAL, EIO etc.) error code */
    SGX_ERROR_FILE_BAD_STATUS               = SGX_MK_ERROR(0x7001),	/* The file is in bad status, run sgx_clearerr to try and fix it */
    SGX_ERROR_FILE_NO_KEY_ID                = SGX_MK_ERROR(0x7002),	/* The Key ID field is all zeros, can't re-generate the encryption key */
    SGX_ERROR_FILE_NAME_MISMATCH            = SGX_MK_ERROR(0x7003),	/* The current file name is different then the original file name (not allowed, substitution attack) */
    SGX_ERROR_FILE_NOT_SGX_FILE             = SGX_MK_ERROR(0x7004), /* The file is not an SGX file */
    SGX_ERROR_FILE_CANT_OPEN_RECOVERY_FILE  = SGX_MK_ERROR(0x7005),	/* A recovery file can't be opened, so flush operation can't continue (only used when no EXXX is returned)  */
    SGX_ERROR_FILE_CANT_WRITE_RECOVERY_FILE = SGX_MK_ERROR(0x7006), /* A recovery file can't be written, so flush operation can't continue (only used when no EXXX is returned)  */
    SGX_ERROR_FILE_RECOVERY_NEEDED          = SGX_MK_ERROR(0x7007),	/* When openeing the file, recovery is needed, but the recovery process failed */
    SGX_ERROR_FILE_FLUSH_FAILED             = SGX_MK_ERROR(0x7008),	/* fflush operation (to disk) failed (only used when no EXXX is returned) */
    SGX_ERROR_FILE_CLOSE_FAILED             = SGX_MK_ERROR(0x7009),	/* fclose operation (to disk) failed (only used when no EXXX is returned) */

} sgx_status_t;

#endif
