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

#include <stdint.h>
#include "sgx_tkey_exchange.h"
#include "sgx_trts.h"
#include "sgx_utils.h"
#include "ecp_interface.h"
#include "util.h"
#include "string.h"
#include "stdlib.h"
#include "sgx_spinlock.h"
#include "sgx_tkey_exchange_t.h"
#include "simple_vector.h"
#include "se_cdefs.h"

// Add a version to tkey_exchange.
SGX_ACCESS_VERSION(tkey_exchange, 1)

#define ERROR_BREAK(sgx_status)  if(SGX_SUCCESS!=sgx_status){break;}
#define SAFE_FREE(ptr) {if (NULL != (ptr)) {free(ptr); (ptr)=NULL;}}

#pragma pack(push, 1)

// any call to sgx_ra_init will reset the input pubkey related ra_db_item_t.ra_state to ra_inited
// only sgx_ra_get_ga can change ra_inited to ra_get_gaed
// only sgx_ra_proc_msg2_trusted can change ra_get_gaed to ra_proc_msg2ed
// sgx_ra_get_msg3_trusted and sgx_ra_get_keys will check ra_state whether to be ra_proc_msg2ed

typedef enum _ra_state
{
    ra_inited= 0,
    ra_get_gaed,
    ra_proc_msg2ed
}ra_state;

typedef struct _ra_db_item_t
{
    sgx_ec256_public_t          g_a;
    sgx_ec256_public_t          g_b;
    sgx_ec_key_128bit_t         vk_key;
    sgx_ec256_public_t          sp_pubkey;
    sgx_ec256_private_t         a;
    sgx_ps_sec_prop_desc_t      ps_sec_prop;
    sgx_ec_key_128bit_t         mk_key;
    sgx_ec_key_128bit_t         sk_key;
    sgx_ec_key_128bit_t         smk_key;
    sgx_quote_nonce_t           quote_nonce; //to verify quote report data
    sgx_target_info_t           qe_target; //to verify quote report
    ra_state                    state;
    sgx_spinlock_t              item_lock;
    uintptr_t                   derive_key_cb;
}ra_db_item_t;

#pragma pack(pop)

static simple_vector g_ra_db = {0, 0, NULL};
static sgx_spinlock_t g_ra_db_lock = SGX_SPINLOCK_INITIALIZER;
static uintptr_t g_kdf_cookie = 0;
#define ENC_KDF_POINTER(x)  (uintptr_t)(x) ^ g_kdf_cookie
#define DEC_KDF_POINTER(x)  (sgx_ra_derive_secret_keys_t)((x) ^ g_kdf_cookie)

extern "C" sgx_status_t sgx_ra_get_ga(
    sgx_ra_context_t context,
    sgx_ec256_public_t *g_a)
{
    sgx_status_t se_ret;
    if(vector_size(&g_ra_db) <= context||!g_a)
        return SGX_ERROR_INVALID_PARAMETER;
    ra_db_item_t* item = NULL;
    if(0 != vector_get(&g_ra_db, context, reinterpret_cast<void**>(&item)) || item == NULL )
        return SGX_ERROR_INVALID_PARAMETER;


    sgx_ecc_state_handle_t ecc_state = NULL;
    sgx_ec256_public_t pub_key;
    sgx_ec256_private_t priv_key;

    memset(&pub_key, 0, sizeof(pub_key));
    memset(&priv_key, 0, sizeof(priv_key));


    sgx_spin_lock(&item->item_lock);
    do
    {
        //sgx_ra_init must have been called
        if (item->state != ra_inited)
        {
            se_ret = SGX_ERROR_INVALID_STATE;
            break;
        }
        // ecc_state should be closed when exit.
        se_ret = sgx_ecc256_open_context(&ecc_state);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }
        se_ret = sgx_ecc256_create_key_pair(&priv_key, &pub_key, ecc_state);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }
        memcpy(&item->a, &priv_key, sizeof(item->a));
        memcpy(&item->g_a, &pub_key, sizeof(item->g_a));
        memcpy(g_a, &pub_key, sizeof(sgx_ec256_public_t));
        item->state = ra_get_gaed;
        //clear local private key to defense in depth
        memset_s(&priv_key,sizeof(priv_key),0,sizeof(sgx_ec256_private_t));
    }while(0);
    sgx_spin_unlock(&item->item_lock);
    if(ecc_state!=NULL)
        sgx_ecc256_close_context(ecc_state);
    return se_ret;
}

extern "C" sgx_status_t sgx_ra_proc_msg2_trusted(
    sgx_ra_context_t context,
    const sgx_ra_msg2_t *p_msg2,            //(g_b||spid||quote_type|| KDF_ID ||sign_gb_ga||cmac||sig_rl_size||sig_rl)
    const sgx_target_info_t *p_qe_target,
    sgx_report_t *p_report,
    sgx_quote_nonce_t* p_nonce)
{
    sgx_status_t se_ret = SGX_ERROR_UNEXPECTED;
    //p_msg2[in] p_qe_target[in] p_report[out] p_nonce[out] in EDL file
    if(vector_size(&g_ra_db) <= context
       || !p_msg2
       || !p_qe_target
       || !p_report
       || !p_nonce)
        return SGX_ERROR_INVALID_PARAMETER;

    ra_db_item_t* item = NULL;
    if(0 != vector_get(&g_ra_db, context, reinterpret_cast<void**>(&item)) || item == NULL )
        return SGX_ERROR_INVALID_PARAMETER;

    sgx_ec256_private_t a;
    memset(&a, 0, sizeof(a));
    // Create gb_ga
    sgx_ec256_public_t gb_ga[2];
    sgx_ec256_public_t sp_pubkey;
    sgx_ec_key_128bit_t smkey = {0};
    sgx_ec_key_128bit_t skey = {0};
    sgx_ec_key_128bit_t mkey = {0};
    sgx_ec_key_128bit_t vkey = {0};
    sgx_ra_derive_secret_keys_t ra_key_cb = NULL;

    memset(&gb_ga[0], 0, sizeof(gb_ga));
    sgx_spin_lock(&item->item_lock);
    //sgx_ra_get_ga must have been called
    if (item->state != ra_get_gaed)
    {
        sgx_spin_unlock(&item->item_lock);
        return SGX_ERROR_INVALID_STATE;
    }
    memcpy(&a, &item->a, sizeof(a));
    memcpy(&gb_ga[1], &item->g_a, sizeof(gb_ga[1]));
    memcpy(&sp_pubkey, &item->sp_pubkey, sizeof(sp_pubkey));
    ra_key_cb = DEC_KDF_POINTER(item->derive_key_cb);
    sgx_spin_unlock(&item->item_lock);
    memcpy(&gb_ga[0], &p_msg2->g_b, sizeof(gb_ga[0]));

    sgx_ecc_state_handle_t ecc_state = NULL;

    // ecc_state need to be freed when exit.
    se_ret = sgx_ecc256_open_context(&ecc_state);
    if (SGX_SUCCESS != se_ret)
    {
        if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
            se_ret = SGX_ERROR_UNEXPECTED;
        return se_ret;
    }

    sgx_ec256_dh_shared_t dh_key;
    memset(&dh_key, 0, sizeof(dh_key));
    sgx_ec256_public_t* p_msg2_g_b = const_cast<sgx_ec256_public_t*>(&p_msg2->g_b);
    se_ret = sgx_ecc256_compute_shared_dhkey(&a,
        (sgx_ec256_public_t*)p_msg2_g_b,
        &dh_key, ecc_state);
    if(SGX_SUCCESS != se_ret)
    {
        if (SGX_ERROR_OUT_OF_MEMORY != se_ret)
            se_ret = SGX_ERROR_UNEXPECTED;
        sgx_ecc256_close_context(ecc_state);
        return se_ret;
    }
    // Verify signature of gb_ga
    uint8_t result;
    sgx_ec256_signature_t* p_msg2_sign_gb_ga = const_cast<sgx_ec256_signature_t*>(&p_msg2->sign_gb_ga);
    se_ret = sgx_ecdsa_verify((uint8_t *)&gb_ga, sizeof(gb_ga),
        &sp_pubkey,
        p_msg2_sign_gb_ga,
        &result, ecc_state);
    if(SGX_SUCCESS != se_ret)
    {
        if (SGX_ERROR_OUT_OF_MEMORY != se_ret)
            se_ret = SGX_ERROR_UNEXPECTED;
        sgx_ecc256_close_context(ecc_state);
        return se_ret;
    }
    if(SGX_EC_VALID != result)
    {
        sgx_ecc256_close_context(ecc_state);
        return SGX_ERROR_INVALID_SIGNATURE;
    }

    do
    {
        if(NULL != ra_key_cb)
        {
            se_ret = ra_key_cb(&dh_key,
                               p_msg2->kdf_id,
                               &smkey,
                               &skey,
                               &mkey,
                               &vkey);
            if (SGX_SUCCESS != se_ret)
            {
                if(SGX_ERROR_OUT_OF_MEMORY != se_ret &&
                    SGX_ERROR_INVALID_PARAMETER != se_ret &&
                    SGX_ERROR_KDF_MISMATCH != se_ret)
                    se_ret = SGX_ERROR_UNEXPECTED;
                break;
            }
        }
        else if (p_msg2->kdf_id == 0x0001)
        {
            se_ret = derive_key(&dh_key, "SMK", (uint32_t)(sizeof("SMK") -1), &smkey);
            if (SGX_SUCCESS != se_ret)
            {
                if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                    se_ret = SGX_ERROR_UNEXPECTED;
                break;
            }
            se_ret = derive_key(&dh_key, "SK", (uint32_t)(sizeof("SK") -1), &skey);
            if (SGX_SUCCESS != se_ret)
            {
                if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                    se_ret = SGX_ERROR_UNEXPECTED;
                break;
            }

            se_ret = derive_key(&dh_key, "MK", (uint32_t)(sizeof("MK") -1), &mkey);
            if (SGX_SUCCESS != se_ret)
            {
                if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                    se_ret = SGX_ERROR_UNEXPECTED;
                break;
            }

            se_ret = derive_key(&dh_key, "VK", (uint32_t)(sizeof("VK") -1), &vkey);
            if (SGX_SUCCESS != se_ret)
            {
                if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                    se_ret = SGX_ERROR_UNEXPECTED;
                break;
            }
        }
        else
        {
            se_ret = SGX_ERROR_KDF_MISMATCH;
            break;
        }

        sgx_cmac_128bit_tag_t mac;
        uint32_t maced_size = offsetof(sgx_ra_msg2_t, mac);

        se_ret = sgx_rijndael128_cmac_msg(&smkey, (const uint8_t *)p_msg2, maced_size, &mac);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }
        //Check mac
        if(0 == consttime_memequal(mac, p_msg2->mac, sizeof(mac)))
        {
            se_ret = SGX_ERROR_MAC_MISMATCH;
            break;
        }

        //create a nonce
        se_ret =sgx_read_rand((uint8_t*)p_nonce, sizeof(sgx_quote_nonce_t));
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }

        sgx_spin_lock(&item->item_lock);
        //sgx_ra_get_ga must have been called
        if (item->state != ra_get_gaed)
        {
            se_ret = SGX_ERROR_INVALID_STATE;
            sgx_spin_unlock(&item->item_lock);
            break;
        }
        memcpy(&item->g_b, &p_msg2->g_b, sizeof(item->g_b));
        memcpy(&item->smk_key, smkey, sizeof(item->smk_key));
        memcpy(&item->sk_key, skey, sizeof(item->sk_key));
        memcpy(&item->mk_key, mkey, sizeof(item->mk_key));
        memcpy(&item->vk_key, vkey, sizeof(item->vk_key));
        memcpy(&item->qe_target, p_qe_target, sizeof(sgx_target_info_t));
        memcpy(&item->quote_nonce, p_nonce, sizeof(sgx_quote_nonce_t));
        sgx_report_data_t report_data = {{0}};
        se_static_assert(sizeof(sgx_report_data_t)>=sizeof(sgx_sha256_hash_t));
        // H = SHA256(ga || gb || VK_CMAC)
        uint32_t sha256ed_size = offsetof(ra_db_item_t, sp_pubkey);
        //report_data is 512bits, H is 256bits. The H is in the lower 256 bits of report data while the higher 256 bits are all zeros.
        se_ret = sgx_sha256_msg((uint8_t *)&item->g_a, sha256ed_size,
                                (sgx_sha256_hash_t *)&report_data);
        if(SGX_SUCCESS != se_ret)
        {
            if (SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            sgx_spin_unlock(&item->item_lock);
            break;
        }
        //REPORTDATA = H
        se_ret = sgx_create_report(p_qe_target, &report_data, p_report);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            sgx_spin_unlock(&item->item_lock);
            break;
        }
        item->state = ra_proc_msg2ed;
        sgx_spin_unlock(&item->item_lock);
    }while(0);
    memset_s(&dh_key, sizeof(dh_key), 0, sizeof(dh_key));
    sgx_ecc256_close_context(ecc_state);
    memset_s(&a, sizeof(sgx_ec256_private_t),0, sizeof(sgx_ec256_private_t));
    memset_s(smkey, sizeof(sgx_ec_key_128bit_t),0, sizeof(sgx_ec_key_128bit_t));
    memset_s(skey, sizeof(sgx_ec_key_128bit_t),0, sizeof(sgx_ec_key_128bit_t));
    memset_s(mkey, sizeof(sgx_ec_key_128bit_t),0, sizeof(sgx_ec_key_128bit_t));
    memset_s(vkey, sizeof(sgx_ec_key_128bit_t),0, sizeof(sgx_ec_key_128bit_t));
    return se_ret;
}


/* the caller is supposed to fill the quote field in emp_msg3 before calling
 * this function.*/
extern "C" sgx_status_t sgx_ra_get_msg3_trusted(
    sgx_ra_context_t context,
    uint32_t quote_size,
    sgx_report_t* qe_report,
    sgx_ra_msg3_t *emp_msg3,    //(mac||g_a||ps_sec_prop||quote)
    uint32_t msg3_size)
{
    if(vector_size(&g_ra_db) <= context ||!quote_size || !qe_report || !emp_msg3)
        return SGX_ERROR_INVALID_PARAMETER;

    ra_db_item_t* item = NULL;
    if(0 != vector_get(&g_ra_db, context, reinterpret_cast<void**>(&item)) || item == NULL )
        return SGX_ERROR_INVALID_PARAMETER;

    //check integer overflow of msg3_size and quote_size
    if (UINTPTR_MAX - reinterpret_cast<uintptr_t>(emp_msg3) < msg3_size ||
        UINT32_MAX - quote_size < sizeof(sgx_ra_msg3_t) ||
        sizeof(sgx_ra_msg3_t) + quote_size != msg3_size)
        return SGX_ERROR_INVALID_PARAMETER;

    if (!sgx_is_outside_enclave(emp_msg3, msg3_size))
        return SGX_ERROR_INVALID_PARAMETER;

    sgx_status_t se_ret = SGX_ERROR_UNEXPECTED;

    //verify qe report
    se_ret = sgx_verify_report(qe_report);
    if(se_ret != SGX_SUCCESS)
    {
        if (SGX_ERROR_MAC_MISMATCH != se_ret &&
            SGX_ERROR_OUT_OF_MEMORY != se_ret)
            se_ret = SGX_ERROR_UNEXPECTED;
        return se_ret;
    }

    sgx_spin_lock(&item->item_lock);
    //sgx_ra_proc_msg2_trusted must have been called
    if (item->state != ra_proc_msg2ed)
    {
        sgx_spin_unlock(&item->item_lock);
        return SGX_ERROR_INVALID_STATE;
    }
    //verify qe_report attributes and mr_enclave same as quoting enclave
    if( memcmp( &qe_report->body.attributes, &item->qe_target.attributes, sizeof(sgx_attributes_t)) ||
        memcmp( &qe_report->body.mr_enclave, &item->qe_target.mr_enclave, sizeof(sgx_measurement_t)) )
    {
        sgx_spin_unlock(&item->item_lock);
        return SGX_ERROR_INVALID_PARAMETER;
    }

    sgx_ra_msg3_t msg3_except_quote_in;
    sgx_cmac_128bit_key_t smk_key;
    memcpy(&msg3_except_quote_in.g_a, &item->g_a, sizeof(msg3_except_quote_in.g_a));
    memcpy(&msg3_except_quote_in.ps_sec_prop, &item->ps_sec_prop,
        sizeof(msg3_except_quote_in.ps_sec_prop));
    memcpy(&smk_key, &item->smk_key, sizeof(smk_key));
    sgx_spin_unlock(&item->item_lock);

    sgx_sha_state_handle_t sha_handle = NULL;
    sgx_cmac_state_handle_t cmac_handle = NULL;


    //SHA256(NONCE || emp_quote)
    sgx_sha256_hash_t hash = {0};
    se_ret = sgx_sha256_init(&sha_handle);
    if (SGX_SUCCESS != se_ret)
    {
        if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
            se_ret = SGX_ERROR_UNEXPECTED;
        return se_ret;
    }
    if (NULL == sha_handle)
        {
            return SGX_ERROR_UNEXPECTED;
        }
    do
    {
        se_ret = sgx_sha256_update((uint8_t *)&item->quote_nonce,
            sizeof(item->quote_nonce),
            sha_handle);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }

         //cmac   M := ga || PS_SEC_PROP_DESC(all zero if unused) ||emp_quote
        sgx_cmac_128bit_tag_t mac;
        se_ret = sgx_cmac128_init(&smk_key, &cmac_handle);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }
        if (NULL == cmac_handle)
        {
            se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }
        se_ret = sgx_cmac128_update((uint8_t*)&msg3_except_quote_in.g_a,
            sizeof(msg3_except_quote_in.g_a), cmac_handle);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }
        se_ret = sgx_cmac128_update((uint8_t*)&msg3_except_quote_in.ps_sec_prop,
            sizeof(msg3_except_quote_in.ps_sec_prop), cmac_handle);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }

        // sha256 and cmac quote
        uint8_t quote_piece[32];
        const uint8_t* emp_quote_piecemeal = emp_msg3->quote;
        uint32_t quote_piece_size = static_cast<uint32_t>(sizeof(quote_piece));

        while (emp_quote_piecemeal < emp_msg3->quote + quote_size)
        {
            //calculate size of one piece, the size of them are sizeof(quote_piece) except for the last one.
            if (static_cast<uint32_t>(emp_msg3->quote + quote_size - emp_quote_piecemeal) < quote_piece_size)
                quote_piece_size = static_cast<uint32_t>(emp_msg3->quote - emp_quote_piecemeal) + quote_size ;
            memcpy(quote_piece, emp_quote_piecemeal, quote_piece_size);
            se_ret = sgx_sha256_update(quote_piece,
                                    quote_piece_size,
                                    sha_handle);
           if (SGX_SUCCESS != se_ret)
           {
               if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                   se_ret = SGX_ERROR_UNEXPECTED;
              break;
           }
           se_ret = sgx_cmac128_update(quote_piece,
                                    quote_piece_size,
                                    cmac_handle);
           if (SGX_SUCCESS != se_ret)
          {
              if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                  se_ret = SGX_ERROR_UNEXPECTED;
              break;
          }
           emp_quote_piecemeal += sizeof(quote_piece);
        }
        ERROR_BREAK(se_ret);

        //get sha256 hash value
        se_ret = sgx_sha256_get_hash(sha_handle, &hash);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }

        //get cmac value
        se_ret = sgx_cmac128_final(cmac_handle, &mac);
        if (SGX_SUCCESS != se_ret)
        {
            if(SGX_ERROR_OUT_OF_MEMORY != se_ret)
                se_ret = SGX_ERROR_UNEXPECTED;
            break;
        }

        //verify qe_report->body.report_data == SHA256(NONCE || emp_quote)
        if(0 != memcmp(&qe_report->body.report_data, &hash, sizeof(hash)))
        {
            se_ret = SGX_ERROR_MAC_MISMATCH;
            break;
        }

        memcpy(&msg3_except_quote_in.mac, mac, sizeof(mac));
        memcpy(emp_msg3, &msg3_except_quote_in, offsetof(sgx_ra_msg3_t, quote));
        se_ret = SGX_SUCCESS;
    }while(0);
    memset_s(&smk_key, sizeof(smk_key), 0, sizeof(smk_key));
    (void)sgx_sha256_close(sha_handle);
    if(cmac_handle != NULL)
        sgx_cmac128_close(cmac_handle);
    return se_ret;
}

// TKE interface for isv enclaves
sgx_status_t sgx_ra_init_ex(
    const sgx_ec256_public_t *p_pub_key,
    int b_pse,
    sgx_ra_derive_secret_keys_t derive_key_cb,
    sgx_ra_context_t *p_context)
{
    int valid = 0;
    sgx_status_t ret = SGX_SUCCESS;
    sgx_ecc_state_handle_t ecc_state = NULL;

    // initialize g_kdf_cookie for the first time sgx_ra_init_ex is called.
    if (unlikely(g_kdf_cookie == 0))
    {
        uintptr_t rand = 0;
        do
        {
            if (SGX_SUCCESS != sgx_read_rand((unsigned char *)&rand, sizeof(rand)))
            {
                return SGX_ERROR_UNEXPECTED;
            }
        } while (rand == 0);

        sgx_spin_lock(&g_ra_db_lock);
        if (g_kdf_cookie == 0)
        {
            g_kdf_cookie = rand;
            memset_s(&rand, sizeof(rand), 0, sizeof(rand));
        }
        sgx_spin_unlock(&g_ra_db_lock);
    }

    if(!p_pub_key || !p_context)
        return SGX_ERROR_INVALID_PARAMETER;

    if(!sgx_is_within_enclave(p_pub_key, sizeof(sgx_ec256_public_t)))
        return SGX_ERROR_INVALID_PARAMETER;

    //derive_key_cb can be NULL
    if (NULL != derive_key_cb &&
        !sgx_is_within_enclave((const void*)derive_key_cb, 0))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    ret = sgx_ecc256_open_context(&ecc_state);
    if(SGX_SUCCESS != ret)
    {
        if(SGX_ERROR_OUT_OF_MEMORY != ret)
            ret = SGX_ERROR_UNEXPECTED;
        return ret;
    }

    ret = sgx_ecc256_check_point((const sgx_ec256_public_t *)p_pub_key,
                                 ecc_state, &valid);
    if(SGX_SUCCESS != ret)
    {
        if(SGX_ERROR_OUT_OF_MEMORY != ret)
            ret = SGX_ERROR_UNEXPECTED;
        sgx_ecc256_close_context(ecc_state);
        return ret;
    }
    if(!valid)
    {
        sgx_ecc256_close_context(ecc_state);
        return SGX_ERROR_INVALID_PARAMETER;
    }
    sgx_ecc256_close_context(ecc_state);

    //add new item to g_ra_db
    ra_db_item_t* new_item = (ra_db_item_t*)malloc(sizeof(ra_db_item_t));
    if (!new_item)
    {
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    memset(new_item,0, sizeof(ra_db_item_t));
    memcpy(&new_item->sp_pubkey, p_pub_key, sizeof(new_item->sp_pubkey));
    if(b_pse)
    {
        //sgx_create_pse_session() must have been called
        ret = sgx_get_ps_sec_prop(&new_item->ps_sec_prop);
        if (ret!=SGX_SUCCESS)
        {
            SAFE_FREE(new_item);
            return ret;
        }
    }

    new_item->derive_key_cb = ENC_KDF_POINTER(derive_key_cb);
    new_item->state = ra_inited;

    //find first empty slot in g_ra_db
    int first_empty = -1;
    ra_db_item_t* item = NULL;
    sgx_spin_lock(&g_ra_db_lock);
    uint32_t size = vector_size(&g_ra_db);
    for (uint32_t i = 0; i < size; i++)
    {
        if(0 != vector_get(&g_ra_db, i, reinterpret_cast<void**>(&item)))
        {
            sgx_spin_unlock(&g_ra_db_lock);
            SAFE_FREE(new_item);
            return SGX_ERROR_UNEXPECTED;
        }
        if(item == NULL)
        {
            first_empty = i;
            break;
        }
    }
    //if there is a empty slot, use it
    if (first_empty >= 0)
    {
        errno_t vret = vector_set(&g_ra_db, first_empty, new_item);
        UNUSED(vret);
        assert(vret == 0);
        *p_context = first_empty;
    }
    //if there are no empty slots, add a new item to g_ra_db
    else
    {
        if(size >= INT32_MAX)
        {
            //overflow
            sgx_spin_unlock(&g_ra_db_lock);
            SAFE_FREE(new_item);
            return SGX_ERROR_OUT_OF_MEMORY;
        }
        if(0 != vector_push_back(&g_ra_db, new_item))
        {
            sgx_spin_unlock(&g_ra_db_lock);
            SAFE_FREE(new_item);
            return SGX_ERROR_OUT_OF_MEMORY;
        }
        *p_context = size;
    }
    sgx_spin_unlock(&g_ra_db_lock);
    return SGX_SUCCESS;
}

// TKE interface for isv enclaves
sgx_status_t sgx_ra_init(
    const sgx_ec256_public_t *p_pub_key,
    int b_pse,
    sgx_ra_context_t *p_context)
{

    return sgx_ra_init_ex(p_pub_key,
                      b_pse,
                      NULL,
                      p_context);
}

// TKE interface for isv enclaves
sgx_status_t sgx_ra_get_keys(
    sgx_ra_context_t context,
    sgx_ra_key_type_t type,
    sgx_ra_key_128_t *p_key)
{
    if(vector_size(&g_ra_db) <= context || !p_key)
        return SGX_ERROR_INVALID_PARAMETER;
    ra_db_item_t* item = NULL;
    if(0 != vector_get(&g_ra_db, context, reinterpret_cast<void**>(&item)) || item == NULL )
        return SGX_ERROR_INVALID_PARAMETER;

    if(!sgx_is_within_enclave(p_key, sizeof(sgx_ra_key_128_t)))
        return SGX_ERROR_INVALID_PARAMETER;

    sgx_status_t ret = SGX_SUCCESS;
    sgx_spin_lock(&item->item_lock);
    //sgx_ra_proc_msg2_trusted fill the keys, so keys are available after it's called.
    if (item->state != ra_proc_msg2ed)
        ret = SGX_ERROR_INVALID_STATE;
    else if(SGX_RA_KEY_MK == type)
        memcpy(p_key, item->mk_key, sizeof(sgx_ra_key_128_t));
    else if(SGX_RA_KEY_SK == type)
        memcpy(p_key, item->sk_key, sizeof(sgx_ra_key_128_t));
    else
        ret = SGX_ERROR_INVALID_PARAMETER;
    sgx_spin_unlock(&item->item_lock);
    return ret;
}


// TKE interface for isv enclaves
sgx_status_t SGXAPI sgx_ra_close(
    sgx_ra_context_t context)
{
    if(vector_size(&g_ra_db) <= context)
        return SGX_ERROR_INVALID_PARAMETER;
    ra_db_item_t* item = NULL;
    if(0 != vector_get(&g_ra_db, context, reinterpret_cast<void**>(&item)) || item == NULL )
        return SGX_ERROR_INVALID_PARAMETER;
    sgx_spin_lock(&g_ra_db_lock);
    //safe clear private key and RA key before free memory to defense in depth
    memset_s(&item->a,sizeof(item->a),0,sizeof(sgx_ec256_private_t));
    memset_s(&item->vk_key,sizeof(item->vk_key),0,sizeof(sgx_ec_key_128bit_t));
    memset_s(&item->mk_key,sizeof(item->mk_key),0,sizeof(sgx_ec_key_128bit_t));
    memset_s(&item->sk_key,sizeof(item->sk_key),0,sizeof(sgx_ec_key_128bit_t));
    memset_s(&item->smk_key,sizeof(item->smk_key),0,sizeof(sgx_ec_key_128bit_t));
    SAFE_FREE(item);
    vector_set(&g_ra_db, context, NULL);
    sgx_spin_unlock(&g_ra_db_lock);
    return SGX_SUCCESS;
}
