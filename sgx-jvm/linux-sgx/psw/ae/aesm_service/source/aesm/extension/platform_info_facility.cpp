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

#include "platform_info_logic.h"
#include "byte_order.h"
#include <assert.h>
#include "sgx_profile.h"

ae_error_t PlatformInfoLogic::get_sgx_epid_group_flags(const platform_info_blob_wrapper_t* p_platform_info_blob, uint8_t* pflags)
{
	ae_error_t retval = AE_SUCCESS;
	if (NULL != pflags && NULL != p_platform_info_blob && p_platform_info_blob->valid_info_blob) {
		*pflags = p_platform_info_blob->platform_info_blob.sgx_epid_group_flags;
	}
	else {
		retval = AE_INVALID_PARAMETER;
	}
	return retval;
}

ae_error_t PlatformInfoLogic::get_sgx_tcb_evaluation_flags(const platform_info_blob_wrapper_t* p_platform_info_blob, uint16_t* pflags)
{
	ae_error_t retval = AE_SUCCESS;
	if (NULL != pflags && NULL != p_platform_info_blob && p_platform_info_blob->valid_info_blob) {
		const uint16_t* p = reinterpret_cast<const uint16_t*>(p_platform_info_blob->platform_info_blob.sgx_tcb_evaluation_flags);
		*pflags = lv_ntohs(*p);
	}
	else {
		retval = AE_INVALID_PARAMETER;
	}
	return retval;
}

bool PlatformInfoLogic::sgx_gid_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
	uint8_t flags = 0;
	bool retVal = false;
	ae_error_t getflagsError = get_sgx_epid_group_flags(p_platform_info_blob, &flags);
	if (AE_SUCCESS == getflagsError) {
		retVal = (0 != (QE_EPID_GROUP_OUT_OF_DATE & flags));
	}
	SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);

	return retVal;
}

bool PlatformInfoLogic::performance_rekey_available(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
	//
	// return whether platform info blob says PR is available
	// the group associated with PR that's returned corresponds to the group
	// that we'll be in **after** executing PR
	//
	bool retVal = false;
	uint8_t flags;
	ae_error_t getflagsError = get_sgx_epid_group_flags(p_platform_info_blob, &flags);
	if (AE_SUCCESS == getflagsError) {
		retVal = static_cast<bool>(flags & PERF_REKEY_FOR_QE_EPID_GROUP_AVAILABLE);
	}
	SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);
	return retVal;
}
bool PlatformInfoLogic::qe_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
	uint16_t flags = 0;
	bool retVal = true;
	ae_error_t getflagsError = get_sgx_tcb_evaluation_flags(p_platform_info_blob, &flags);
	if (AE_SUCCESS == getflagsError) {
		retVal = (0 != (QUOTE_ISVSVN_QE_OUT_OF_DATE & flags));
	}
	SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);
	return retVal;
}

bool PlatformInfoLogic::pce_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
	uint16_t flags = 0;
	bool retVal = true;
	ae_error_t getflagsError = get_sgx_tcb_evaluation_flags(p_platform_info_blob, &flags);
	if (AE_SUCCESS == getflagsError) {
		retVal = (0 != (QUOTE_ISVSVN_PCE_OUT_OF_DATE & flags));
	}
	SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);
	return retVal;
}

bool PlatformInfoLogic::cpu_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
	uint16_t flags = 0;
	bool retVal = false;
	ae_error_t getflagsError = get_sgx_tcb_evaluation_flags(p_platform_info_blob, &flags);
	if (AE_SUCCESS == getflagsError) {
		retVal = (0 != (QUOTE_CPUSVN_OUT_OF_DATE & flags));
	}
	SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);

	return retVal;
}
ae_error_t PlatformInfoLogic::need_epid_provisioning(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
	ae_error_t status = AESM_NEP_DONT_NEED_EPID_PROVISIONING;
	if (sgx_gid_out_of_date(p_platform_info_blob) &&
		!qe_svn_out_of_date(p_platform_info_blob) &&
		!cpu_svn_out_of_date(p_platform_info_blob) &&
		!pce_svn_out_of_date(p_platform_info_blob))
	{
		status = AESM_NEP_DONT_NEED_UPDATE_PVEQE;      // don't need update, but need epid provisioning
	}
	else if (!sgx_gid_out_of_date(p_platform_info_blob) && performance_rekey_available(p_platform_info_blob))
	{
		status = AESM_NEP_PERFORMANCE_REKEY;
	}
	SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", status, status);
	return status;
}

