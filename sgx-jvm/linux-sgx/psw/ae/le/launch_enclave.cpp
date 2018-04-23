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

#include <stdlib.h>
#include "launch_enclave.h"
#include "byte_order.h"
#include "sgx_utils.h"

#include "launch_enclave_t.c"
#include "wl_pub.hh"
#include "launch_enclave_mrsigner.hh"
#include "service_enclave_mrsigner.hh"

#if !defined(SWAP_ENDIAN_DW)
#define SWAP_ENDIAN_DW(dw)    ((((dw) & 0x000000ff) << 24)                  \
    | (((dw) & 0x0000ff00) << 8)                                            \
    | (((dw) & 0x00ff0000) >> 8)                                            \
    | (((dw) & 0xff000000) >> 24))
#endif

#if !defined(SWAP_ENDIAN_32B)
#define SWAP_ENDIAN_8X32B(ptr)                                              \
{                                                                           \
    uint32_t temp = 0;                                                      \
    temp = SWAP_ENDIAN_DW(((uint32_t*)(ptr))[0]);                           \
    ((uint32_t*)(ptr))[0] = SWAP_ENDIAN_DW(((uint32_t*)(ptr))[7]);          \
    ((uint32_t*)(ptr))[7] = temp;                                           \
    temp = SWAP_ENDIAN_DW(((uint32_t*)(ptr))[1]);                           \
    ((uint32_t*)(ptr))[1] = SWAP_ENDIAN_DW(((uint32_t*)(ptr))[6]);          \
    ((uint32_t*)(ptr))[6] = temp;                                           \
    temp = SWAP_ENDIAN_DW(((uint32_t*)(ptr))[2]);                           \
    ((uint32_t*)(ptr))[2] = SWAP_ENDIAN_DW(((uint32_t*)(ptr))[5]);          \
    ((uint32_t*)(ptr))[5] = temp;                                           \
    temp = SWAP_ENDIAN_DW(((uint32_t*)(ptr))[3]);                           \
    ((uint32_t*)(ptr))[3] = SWAP_ENDIAN_DW(((uint32_t*)(ptr))[4]);          \
    ((uint32_t*)(ptr))[4] = temp;                                           \
}
#endif

#define LE_MAX_MRSIGNER_NUMBER  2048
// Macro used to get mac wl cert size, signature is not included
#define LE_MAX_WL_CERT_SIZE     (sizeof(wl_cert_t) + LE_MAX_MRSIGNER_NUMBER \
                                 * sizeof(sgx_measurement_t))

#define WL_CERT_VERSION                 0x0100
#define WL_CERT_TYPE                    0x0100
#define WL_CERT_PROVIDER_ID             0
#define WL_PROVIDER_CERT_VERSION        0x0100
#define WL_PROVIDER_CERT_TYPE           0
#define WL_PROVIDER_CERT_PROVIDER_ID    0
#define WL_PROVIDER_CERT_ROOT_ID        0


static uint8_t g_wl_cert_buf[LE_MAX_WL_CERT_SIZE] = {0};

static void reverse_byte_array(uint8_t *array, size_t size)
{
    size_t i = 0;
    for(i = 0; i < size / 2; i++)
    {
        uint8_t temp = array[i];
        array[i] = array[size - i - 1];
        array[size - i - 1] = temp;
    }
}

//calculate launch token. key_id, attributes_le and then mac is updated.
//return AE_SUCCESS on success
static ae_error_t le_calc_lic_token(token_t* lictoken)
{
    //calculate launch token

    sgx_key_request_t key_request;
    sgx_key_128bit_t launch_key;

    if(SGX_SUCCESS != sgx_read_rand((uint8_t*)&lictoken->key_id,
                                    sizeof(sgx_key_id_t)))
    {
        return LE_UNEXPECTED_ERROR;
    }
    // Create Key Request
    memset(&key_request, 0, sizeof(key_request));

    //setup key_request parameters to derive launch key
    key_request.key_name = SGX_KEYSELECT_EINITTOKEN;

    memcpy(&key_request.key_id, &lictoken->key_id,
           sizeof(key_request.key_id));

    memcpy(&key_request.cpu_svn, &(lictoken->cpu_svn_le),
           sizeof(key_request.cpu_svn));

    memcpy(&key_request.isv_svn, &(lictoken->isv_svn_le),
           sizeof(key_request.isv_svn));


    key_request.attribute_mask.xfrm = 0;
    //0xFFFFFFFFFFFFFFFB: ~SGX_FLAGS_MODE64BIT
    key_request.attribute_mask.flags = ~SGX_FLAGS_MODE64BIT;
    key_request.misc_mask = 0xFFFFFFFF;

    lictoken->masked_misc_select_le &= key_request.misc_mask;



    lictoken->attributes_le.flags = (lictoken->attributes_le.flags)
                                    & (key_request.attribute_mask.flags);
    lictoken->attributes_le.xfrm = (lictoken->attributes_le.xfrm)
                                    & (key_request.attribute_mask.xfrm);

    // EGETKEY
    sgx_status_t sgx_ret = sgx_get_key(&key_request,&launch_key);
    if(SGX_SUCCESS != sgx_ret)
    {
        return LE_GET_EINITTOKEN_KEY_ERROR;
    }

    sgx_cmac_state_handle_t p_cmac_handle = NULL;
    do{
        sgx_ret = sgx_cmac128_init(&launch_key, &p_cmac_handle);
        if(SGX_SUCCESS != sgx_ret)
        {
            break;
        }
        sgx_ret = sgx_cmac128_update((uint8_t*)&lictoken->body,
                                    sizeof(lictoken->body),
                                    p_cmac_handle);
        if(SGX_SUCCESS != sgx_ret)
        {
            break;
        }
        sgx_ret = sgx_cmac128_final(p_cmac_handle,
                                   (sgx_cmac_128bit_tag_t*)&lictoken->mac);
    }while(0);
    if (p_cmac_handle != NULL)
    {
        sgx_cmac128_close(p_cmac_handle);
    }

    //clear launch_key after being used
    memset_s(launch_key,sizeof(launch_key), 0, sizeof(launch_key));
    if (SGX_SUCCESS != sgx_ret)
    {
        return AE_FAILURE;
    }

    return AE_SUCCESS;
}


ae_error_t le_generate_launch_token(
    const sgx_measurement_t* mrenclave,
    const sgx_measurement_t* mrsigner,
    const sgx_attributes_t* se_attributes,
    token_t* lictoken)
{
    uint32_t i = 0;
    bool is_production = false;
    sgx_status_t sgx_ret = SGX_ERROR_UNEXPECTED;
    ae_error_t ae_ret = AE_FAILURE;
    wl_cert_t *p_wl_cert_cache = (wl_cert_t *)g_wl_cert_buf;
    sgx_measurement_t empty_mrsigner;
    sgx_report_t report;

    // se_attributes must have no reserved bit set.
    // urts(finally EINIT instruction)rejects EINIT Token with SGX_FLAGS_INITTED
    // set. So LE doesn't need to check it here.
    if((se_attributes->flags) & SGX_FLAGS_RESERVED)
    {
        return LE_INVALID_ATTRIBUTE;
    }

    memset(&report, 0, sizeof(report));
    // Create report to get current cpu_svn and isv_svn.
    sgx_ret = sgx_create_report(NULL, NULL, &report);
    if(SGX_SUCCESS != sgx_ret)
    {
        return LE_UNEXPECTED_ERROR;
    }
    
    for(i = 0; i < (sizeof(g_le_mrsigner) / sizeof(g_le_mrsigner[0])); i++)
    {
        if(0 == memcmp(&(g_le_mrsigner[i]), &(report.body.mr_signer),
                       sizeof(g_le_mrsigner[0])))
        {
            is_production = true;
            break;
        }
    }

    if(true == is_production)
    {
        // Only Provision Enclave is allowed to be EINITed with the privilege
        // to access the PROVISIONKEY, which is signed with fixed signing key
        if((se_attributes->flags & SGX_FLAGS_PROVISION_KEY))
        {
            for(i = 0; i < (sizeof(G_SERVICE_ENCLAVE_MRSIGNER) / sizeof(G_SERVICE_ENCLAVE_MRSIGNER[0]));
                i++)
            {
                if(0 == memcmp(&G_SERVICE_ENCLAVE_MRSIGNER[i], mrsigner,
                               sizeof(G_SERVICE_ENCLAVE_MRSIGNER[0])))
                {
                    break;
                }
            }
            if(i == sizeof(G_SERVICE_ENCLAVE_MRSIGNER) / sizeof(G_SERVICE_ENCLAVE_MRSIGNER[0]))
            {
                return LE_INVALID_ATTRIBUTE;
            }
        }
    }

    // on "production" system, enclaves to be launched in "non-enclave-debug"
    // mode are subjected to Enclave Signing Key White Listing control.
    if(((se_attributes->flags & SGX_FLAGS_DEBUG) == 0)
        && (true == is_production))
    {
        // Check whether the wl is initialized
        if(p_wl_cert_cache->version == 0)
        {
            return LE_WHITELIST_UNINITIALIZED_ERROR;
        }

        // Create an empty mrsigner
        memset(&empty_mrsigner, 0, sizeof(empty_mrsigner));
        // Check if p_wl_cert_cache->mr_signer_list[0] is empty.
        // If mr_signer_list[0] = 0, a "wild card" white list cert is in-use,
        // meant to allow any enclave to launch.
        if(0 != memcmp(&(p_wl_cert_cache->mr_signer_list[0]), &empty_mrsigner,
                       sizeof(p_wl_cert_cache->mr_signer_list[0])))
        {
            for(i = 0; i < p_wl_cert_cache->entry_number; i++)
            {
                if(0 == memcmp(&(p_wl_cert_cache->mr_signer_list[i]),
                               mrsigner,
                               sizeof(p_wl_cert_cache->mr_signer_list[i])))
                {
                    break;
                }
            }
            if(i == p_wl_cert_cache->entry_number)
            {
                return LE_INVALID_PRIVILEGE_ERROR;
            }
        }
    }


    //initial EINIT Token and set 0 for all reserved area
    memset(lictoken, 0, sizeof(*lictoken));

    //set the EINIT Token valid
    lictoken->body.valid = 1;

    //set EINIT Token mrenclave
    memcpy(&lictoken->body.mr_enclave, mrenclave,
           sizeof(lictoken->body.mr_enclave));

    //set EINIT Token mrsigner
    memcpy(&lictoken->body.mr_signer, mrsigner,
           sizeof(lictoken->body.mr_signer));

    //set EINIT Token attributes
    memcpy(&lictoken->body.attributes, se_attributes,
           sizeof(lictoken->body.attributes));

    //set EINIT Token with platform information from ereport of LE
    memcpy(&lictoken->cpu_svn_le, &report.body.cpu_svn, sizeof(sgx_cpu_svn_t));
    lictoken->isv_svn_le = report.body.isv_svn;
    lictoken->isv_prod_id_le = report.body.isv_prod_id;

    //will mask attributes in le_calc_lic_token
    memcpy(&lictoken->attributes_le, &report.body.attributes,
           sizeof(lictoken->attributes_le));

    //will mask misc_select_le in le_calc_lic_token
    lictoken->masked_misc_select_le = report.body.misc_select;

    //calculate EINIT Token
    ae_ret = le_calc_lic_token(lictoken);
    //if failure, clear EINIT Token
    if (ae_ret != AE_SUCCESS)
    {
        memset_s(lictoken,sizeof(*lictoken), 0, sizeof(*lictoken));
    }
    return ae_ret;

}


int le_get_launch_token_wrapper(
    const sgx_measurement_t* mrenclave,
    const sgx_measurement_t* mrsigner,
    const sgx_attributes_t* se_attributes,
    token_t* lictoken)
{
    // Security assumption is that the edgr8r generated trusted bridge code
    // makes sure mrenclave, mrsigner, se_attributes, lictoken buffers are all
    // inside enclave. check all input and output pointers, defense in depth
    if (NULL == mrenclave ||
        NULL == mrsigner ||
        NULL == se_attributes ||
        NULL == lictoken)
    {
        return LE_INVALID_PARAMETER;
    }

    return le_generate_launch_token(mrenclave, mrsigner, se_attributes,
                                     lictoken);
}


/*
 * Internal function used to init white list. It will check the content of the
 * cert chain, and verify the signature of input cert chains. If no problem,
 * it will cache the input white list into EPC.
 *
 * @param p_wl_cert_chain[in] Pointer to the white list cert chain.
 * @param entry_number[in] The entry number within the white list.
 * @param wl_cert_chain_size[in] The size of white list cert chain, in bytes.
 * @return uint32_t AE_SUCCESS for success, otherwise for errors.
 */
uint32_t le_init_white_list(
    const wl_cert_chain_t *p_wl_cert_chain,
    uint32_t entry_number,
    uint32_t wl_cert_chain_size)
{
    sgx_status_t sgx_ret = SGX_SUCCESS;
    uint32_t ret = AE_SUCCESS;
    uint32_t new_wl_version = 0;
    uint8_t verify_result = 0;
    int valid = 0;
    const uint8_t *buf = NULL;
    uint32_t buf_size = 0;
    sgx_prod_id_t wl_prod_id = 0;
    sgx_ecc_state_handle_t ecc_handle = NULL;
    wl_cert_t *p_wl_cert_cache = (wl_cert_t *)g_wl_cert_buf;
    sgx_report_t report;
    sgx_ec256_signature_t wl_signature;
    sgx_ec256_public_t wl_pubkey;


    // Check fields of provider cert
    // Format version should be 1 (big endian)
    if(p_wl_cert_chain->wl_provider_cert.version != WL_PROVIDER_CERT_VERSION)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }
    // For Enclave Signing Key White List Cert, must be 0
    if(p_wl_cert_chain->wl_provider_cert.cert_type != WL_PROVIDER_CERT_TYPE)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }
    // only one White List Provider is approved:
    // WLProviderID: ISecG = 0
    if(p_wl_cert_chain->wl_provider_cert.provider_id != WL_PROVIDER_CERT_PROVIDER_ID)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }
    // only one WLRootID is valid: WLRootID-iKGF-Key-0 = 0
    if(p_wl_cert_chain->wl_provider_cert.root_id != WL_PROVIDER_CERT_ROOT_ID)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }

    // Check fields of wl cert
    // only valid version is 1
    if(p_wl_cert_chain->wl_cert.version != WL_CERT_VERSION)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }
    // For Enclave Signing Key White List Cert, must be 1
    if(p_wl_cert_chain->wl_cert.cert_type != WL_CERT_TYPE)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }
    // only one White List Provider is approved:
    // WLProviderID: ISecG = 0
    if(p_wl_cert_chain->wl_cert.provider_id != WL_CERT_PROVIDER_ID)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }

    // If cache exists
    new_wl_version = p_wl_cert_chain->wl_cert.wl_version;
    new_wl_version = _ntohl(new_wl_version);
    if(p_wl_cert_cache->version != 0)
    {
        // the logic will be needed to support more than
        // one providers in the future.
        //if(p_wl_cert_chain->wl_cert.provider_id
        //   != p_wl_cert_cache->provider_id)
        //{
        //    ret = LE_INVALID_PARAMETER;
        //    goto CLEANUP;
        //}
        if(new_wl_version <= p_wl_cert_cache->wl_version)
        {
            ret = LE_WHITE_LIST_ALREADY_UPDATED;
            goto CLEANUP;
        }
    }

    sgx_ret = sgx_ecc256_open_context(&ecc_handle);
    if (SGX_SUCCESS != sgx_ret)
    {
        ret = LE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    memset(&wl_signature, 0, sizeof(wl_signature));
    // Convert the signature of provider cert into little endian
    memcpy(&wl_signature,
           &(p_wl_cert_chain->wl_provider_cert.signature),
           sizeof(wl_signature));
    SWAP_ENDIAN_8X32B(wl_signature.x);
    SWAP_ENDIAN_8X32B(wl_signature.y);

    // Verify the wl provider cert
    buf = (const uint8_t *)&(p_wl_cert_chain->wl_provider_cert);
    buf_size = static_cast<uint32_t>(sizeof(p_wl_cert_chain->wl_provider_cert)
               - sizeof(p_wl_cert_chain->wl_provider_cert.signature));
    sgx_ret = sgx_ecdsa_verify(buf, buf_size,
                               &g_wl_root_pubkey,
                               &wl_signature,
                               &verify_result,
                               ecc_handle);
    if (SGX_SUCCESS != sgx_ret)
    {
        ret = LE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }
    if(SGX_EC_VALID != verify_result)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }

    // Convert the signature of wl cert into little endian
    buf = (const uint8_t *)p_wl_cert_chain + wl_cert_chain_size
          - sizeof(wl_signature);
    memcpy(&wl_signature, buf, sizeof(wl_signature));
    SWAP_ENDIAN_8X32B(wl_signature.x);
    SWAP_ENDIAN_8X32B(wl_signature.y);

    // Convert the pubkey into little endian
    memset(&wl_pubkey, 0, sizeof(wl_pubkey));
    memcpy(&wl_pubkey,
           &(p_wl_cert_chain->wl_provider_cert.pub_key),
           sizeof(wl_pubkey));
    reverse_byte_array(wl_pubkey.gx, sizeof(wl_pubkey.gx));
    reverse_byte_array(wl_pubkey.gy, sizeof(wl_pubkey.gy));

    // Check whether the pubkey is valid first.
    sgx_ret = sgx_ecc256_check_point(&wl_pubkey, ecc_handle, &valid);
    if(SGX_SUCCESS != sgx_ret)
    {
        ret = LE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }
    if(!valid)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }

    // Verify the wl_cert
    buf = (const uint8_t *)&(p_wl_cert_chain->wl_cert);
    buf_size = wl_cert_chain_size - static_cast<uint32_t>(sizeof(wl_provider_cert_t) + sizeof(sgx_ec256_signature_t));
    sgx_ret = sgx_ecdsa_verify(buf, buf_size,
                               &wl_pubkey,
                               &wl_signature,
                               &verify_result,
                               ecc_handle);
    if (SGX_SUCCESS != sgx_ret)
    {
        ret = LE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }
    if(SGX_EC_VALID != verify_result)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }

    memset(&report, 0, sizeof(report));
    // Create report to get current mrsigner.
    sgx_ret = sgx_create_report(NULL, NULL, &report);
    if(SGX_SUCCESS != sgx_ret)
    {
        ret = LE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    // Convert the big endian prod id to little endian.
    wl_prod_id = p_wl_cert_chain->wl_cert.le_prod_id;
    wl_prod_id = _ntohs(wl_prod_id);
    if(report.body.isv_prod_id != wl_prod_id)
    {
        ret = LE_INVALID_PARAMETER;
        goto CLEANUP;
    }

    // Cache the wl cert
    memset(g_wl_cert_buf, 0, sizeof(g_wl_cert_buf));
    memcpy(g_wl_cert_buf, &(p_wl_cert_chain->wl_cert), buf_size);
    // Change entry_number and wl_version to little endian, so we don't need to
    // convert them next time.
    p_wl_cert_cache->entry_number = entry_number;
    p_wl_cert_cache->wl_version = new_wl_version;

CLEANUP:
    if(ecc_handle != NULL)
    {
        sgx_ecc256_close_context(ecc_handle);
    }
    return ret;

}

/*
 * External function used to init white list. It will check whether the input
 * buffer is correctly copied into EPC, and check the size of the buffer.
 *
 * @param wl_cert_chain[in] Pointer to the white list cert chain.
 * @param wl_cert_chain_size[in] The size of white list cert chain, in bytes.
 * @return uint32_t AE_SUCCESS for success, otherwise for errors.
 */
uint32_t le_init_white_list_wrapper(
    const uint8_t *wl_cert_chain,
    uint32_t wl_cert_chain_size)
{
    const wl_cert_chain_t *p_wl_cert_chain = NULL;
    uint32_t entry_number = 0;
    uint32_t temp_size = 0;

    if(wl_cert_chain == NULL)
    {
        return LE_INVALID_PARAMETER;
    }
    if(!sgx_is_within_enclave(wl_cert_chain, wl_cert_chain_size))
        return LE_INVALID_PARAMETER;
    p_wl_cert_chain = (const wl_cert_chain_t *)wl_cert_chain;
    // First compare wl_cert_chain_size with the minimal size of cert chain.
    // It should have at least one entry of mrsigner.
    if(wl_cert_chain_size < sizeof(wl_cert_chain_t)
                            + sizeof(sgx_measurement_t)
                            + sizeof(sgx_ec256_signature_t))
    {
        return LE_INVALID_PARAMETER;
    }
    entry_number = p_wl_cert_chain->wl_cert.entry_number;
    entry_number = _ntohl(entry_number);
    // limits max MRSIGNER entry number in
    // WL Cert to be <= 2048
    if(entry_number > LE_MAX_MRSIGNER_NUMBER)
    {
        return LE_INVALID_PARAMETER;
    }
    temp_size =  static_cast<uint32_t>(sizeof(wl_cert_chain_t)
                 + sizeof(sgx_ec256_signature_t)
                 + (sizeof(sgx_measurement_t) * entry_number));
    if(wl_cert_chain_size != temp_size)
    {
        return LE_INVALID_PARAMETER;
    }
    return le_init_white_list(p_wl_cert_chain, entry_number, wl_cert_chain_size);
}

