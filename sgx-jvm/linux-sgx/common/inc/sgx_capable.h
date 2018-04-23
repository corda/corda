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

#pragma once

#ifndef _SGX_CAPABLE_H_
#define _SGX_CAPABLE_H_

#include "sgx_error.h"
#include "sgx_defs.h"

typedef enum _sgx_device_status_t {
    SGX_ENABLED,
    SGX_DISABLED_REBOOT_REQUIRED, /* A reboot is required to finish enabling SGX */
    SGX_DISABLED_LEGACY_OS, /* SGX is disabled and a Software Control Interface is not available to enable it */
    SGX_DISABLED, /* SGX is not enabled on this platform. More details are unavailable. */
    SGX_DISABLED_SCI_AVAILABLE, /* SGX is disabled, but a Software Control Interface is available to enable it */
    SGX_DISABLED_MANUAL_ENABLE, /* SGX is disabled, but can be enabled manually in the BIOS setup */
    SGX_DISABLED_HYPERV_ENABLED, /* Detected an unsupported version of Windows* 10 with Hyper-V enabled */
    SGX_DISABLED_UNSUPPORTED_CPU, /* SGX is not supported by this CPU */
} sgx_device_status_t;

#ifdef  __cplusplus
extern "C" {
#endif

/*
 * Function to check if the client platform is SGX enabled.
 *
 * @param sgx_capable[out] The SGX capable status of the client platform.
 *          1 - Platform is SGX enabled or the Software Control Interface is available to configure SGX
 *          0 - SGX not available
 * @return If the function succeeds, return SGX_SUCCESS, any other value indicates an error.
 */
sgx_status_t sgx_is_capable(int* sgx_capable);

/*
 * Function used to enable SGX device through EFI.
 *
 * @param sgx_device_status[out] The status of SGX device.
 * @return If the function succeeds, return SGX_SUCCESS, any other value indicates an error.
 */
sgx_status_t sgx_cap_enable_device(sgx_device_status_t* sgx_device_status);

/*
* Function used to query SGX device status.
*
* @param sgx_device_status[out] The status of SGX device. 
* @return If the function succeeds, return SGX_SUCCESS, any other value indicates an error.
*/
sgx_status_t SGXAPI sgx_cap_get_status(sgx_device_status_t* sgx_device_status);

#ifdef  __cplusplus
}
#endif

#endif
