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



#include "enclave.h"
#include "rts.h"
#include "routine.h"
#include "cpu_features.h"
#include "enclave_creator_hw.h"
#include "se_error_internal.h"
#include "prd_css_util.h"
#include "se_memcpy.h"

#define EDMM_ENABLE_BIT 0x1ULL

bool EnclaveCreatorHW::use_se_hw() const
{
    return true;
}

int EnclaveCreatorHW::initialize(sgx_enclave_id_t enclave_id)
{
    system_features_t info;
    info.system_feature_set[0] = (uint64_t)1 << SYS_FEATURE_MSb;

    CEnclave *enclave= CEnclavePool::instance()->get_enclave(enclave_id);

    if(enclave == NULL)
        return SGX_ERROR_INVALID_ENCLAVE_ID;

    //Since CPUID instruction is NOT supported within enclave, we enumerate the cpu features here and send to tRTS.
    info.cpu_features = 0;
    get_cpu_features(&info.cpu_features);
    info.version = (sdk_version_t)MIN((uint32_t)SDK_VERSION_2_0, enclave->get_enclave_version());
    if (is_EDMM_supported(enclave_id))
            info.system_feature_set[0] |= EDMM_ENABLE_BIT;


    int status = enclave->ecall(ECMD_INIT_ENCLAVE, NULL, reinterpret_cast<void *>(&info));
    //free the tcs used by initialization;
    enclave->get_thread_pool()->reset();

    //Enclave initialization may fail caused by power transition.
    //The upper layer code will re-create enclave based on SGX_ERROR_ENCLAVE_LOST.
    if(SGX_SUCCESS == status || SGX_ERROR_ENCLAVE_LOST == status)
    {
        return status;
    }
    else
    {
        //For other error code, may be caused by tRTS bug, or caused by attacker,
        //so we just return SGX_ERROR_UNEXPECTED.
        SE_TRACE(SE_TRACE_WARNING, "initialize enclave failed\n");
        return SGX_ERROR_UNEXPECTED;
    }
}

int EnclaveCreatorHW::get_misc_attr(sgx_misc_attribute_t *sgx_misc_attr, metadata_t *metadata, SGXLaunchToken * const lc, uint32_t debug_flag)
{
    sgx_attributes_t *required_attr = &metadata->attributes;
    enclave_css_t *enclave_css = &metadata->enclave_css;
    sgx_attributes_t *secs_attr = &sgx_misc_attr->secs_attr;
    //fp, sse must be set.
    uint64_t tmp = required_attr->xfrm & SGX_XFRM_LEGACY;

    if(SGX_XFRM_LEGACY != tmp)
    {
        SE_TRACE(SE_TRACE_WARNING, "fp/sse attributes is a must in attributes\n");
        return SGX_ERROR_INVALID_ATTRIBUTE;
    }

    //step 1, set enclave properties
    sgx_misc_attribute_t se_cap;
    if(!get_plat_cap(&se_cap))
        return SGX_ERROR_NO_DEVICE;

    if(debug_flag)
    {
        //If enclave is signed as product enclave, but is launched as debug enclave, we need report specific error code.
        if((enclave_css->body.attribute_mask.flags & SGX_FLAGS_DEBUG)
                && !(enclave_css->body.attributes.flags & SGX_FLAGS_DEBUG)
          )
        {
            return SGX_ERROR_NDEBUG_ENCLAVE;
        }
        required_attr->flags |= SGX_FLAGS_DEBUG;
    }
    else
        required_attr->flags &= (~SGX_FLAGS_DEBUG);

    secs_attr->flags = required_attr->flags & se_cap.secs_attr.flags;
    secs_attr->xfrm = required_attr->xfrm & se_cap.secs_attr.xfrm;
    
    
    

    //step 3, evaluate the encalve attributes in secs.

    //check the signature structure xfrm attribute restrictions.
    if((enclave_css->body.attribute_mask.xfrm & secs_attr->xfrm)
            != (enclave_css->body.attribute_mask.xfrm & enclave_css->body.attributes.xfrm))
    {
        SE_TRACE(SE_TRACE_WARNING, "secs attributes.xfrm does NOT match signature attributes.xfrm\n");
        return SGX_ERROR_INVALID_ATTRIBUTE;
    }
    //Debug bit has been checked before. For other attributes, check the signature structure flags attribute restrictions.
    if((enclave_css->body.attribute_mask.flags & secs_attr->flags)
            != (enclave_css->body.attribute_mask.flags & enclave_css->body.attributes.flags))
    {
        SE_TRACE(SE_TRACE_WARNING, "secs attributes.flag does NOT match signature attributes.flag\n");
        return SGX_ERROR_INVALID_ATTRIBUTE;
    }

    // Check misc_select/misc_mask
    // enclave_css->body.misc_select & enclave_css->body.misc_mask must be a subset of se_cap.misc_select
    if(~(se_cap.misc_select) & (enclave_css->body.misc_select & enclave_css->body.misc_mask))
        return SGX_ERROR_INVALID_MISC;

    // try to use maximum ablity of cpu
    sgx_misc_attr->misc_select = se_cap.misc_select & enclave_css->body.misc_select;

    if(lc != NULL)
    {
        // Read launch token from lc
        sgx_launch_token_t token;
        memset(&token, 0, sizeof(token));
        if(lc->get_launch_token(&token) != SGX_SUCCESS)
            return SGX_ERROR_UNEXPECTED;
        token_t *launch = (token_t *)token;
        if(1 == launch->body.valid)
        {
            //debug launch enclave cannot launch production enclave
            if( !(secs_attr->flags & SGX_FLAGS_DEBUG)
                    && (launch->attributes_le.flags & SGX_FLAGS_DEBUG) )
            {
                SE_TRACE(SE_TRACE_WARNING, "secs attributes is non-debug, \n");
                return SE_ERROR_INVALID_LAUNCH_TOKEN;
            }
            //verify attributes in lictoken are the same as the enclave
            if(memcmp(&launch->body.attributes, secs_attr, sizeof(sgx_attributes_t)))
            {
                SE_TRACE(SE_TRACE_WARNING, "secs attributes does NOT match launch token attributes\n");
                return SGX_ERROR_INVALID_ATTRIBUTE;
            }
        }
    }

    return SGX_SUCCESS;
}

int EnclaveCreatorHW::init_enclave(sgx_enclave_id_t enclave_id, enclave_css_t *enclave_css, SGXLaunchToken * lc, le_prd_css_file_t *prd_css_file)
{
    unsigned int ret = 0;
    sgx_launch_token_t token;
    memset(token, 0, sizeof(sgx_launch_token_t));

    enclave_css_t css;
    memcpy_s(&css, sizeof(enclave_css_t),  enclave_css, sizeof(enclave_css_t));

    for(int i = 0; i < 2; i++)
    {
        if(SGX_SUCCESS != (ret = lc->get_launch_token(&token)))
            return ret;

        ret = try_init_enclave(enclave_id, &css, reinterpret_cast<token_t *>(token));

        if(i > 0)
            return ret;
        if(true == is_le(lc, &css))
        {
            // LE is loaded with the interface sgx_create_le.
            // Read the input prd css file and use it to init again.
            if(SGX_ERROR_INVALID_ATTRIBUTE == ret && prd_css_file != NULL) {
                if((ret = read_prd_css(prd_css_file->prd_css_name, &css)) != SGX_SUCCESS)
                {
                    return ret;
                }

                prd_css_file->is_used = true;
                continue;
            }

            // LE is loaded with the normal interface, or LE is loaded with sgx_create_le but EINIT returns other error code
            // No need to get launch token and retry, so just return error code.
            return ret;
        }

        //If current launch token does NOT match the platform, then update the launch token.
        //If the hash of signer (public key) in signature does not match launch token, EINIT will return SE_INVALID_MEASUREMENT
        else if(!lc->is_launch_updated() && (SE_ERROR_INVALID_LAUNCH_TOKEN == ret || SGX_ERROR_INVALID_CPUSVN == ret || SE_ERROR_INVALID_MEASUREMENT == ret || SE_ERROR_INVALID_ISVSVNLE == ret))
        {
            if(SGX_SUCCESS != (ret = lc->update_launch_token(true)))
            {
                return ret;
            }
            else
            {
                continue;
            }
        }
        else
            break;
    }
    return ret;

}

