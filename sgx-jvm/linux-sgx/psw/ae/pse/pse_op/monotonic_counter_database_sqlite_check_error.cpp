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


#include "monotonic_counter_database_sqlite_check_error.h"
#include "monotonic_counter_database_sqlite_bin_hash_tree_utility.h"
#include "monotonic_counter_database_sqlite_access_hw_mc.h"
#include "pse_op_t.h"
#include "sgx_tcrypto.h"
#include "session_mgr.h"
#include "sgx_sha256_128.h"

#define CHECK_ERROR_CODE(ret, lable) if(OP_SUCCESS != ret) { ret = OP_ERROR_INTERNAL; goto lable; }
/*******************************************************************
**  Function name: verify_root_node
**  Descrption: Update or verify the root node of the VMC hash tree by using specified 
**  MC value "mc".
**  
*******************************************************************/
static pse_op_error_t verify_root_node(pse_vmc_children_of_root_t* children)
{
    sgx_status_t stat;
    pse_op_error_t ret;
    cal_root_hash_buf_t cal_root_hash_buf;
    uint8_t root_hash[ROOT_HASH_SIZE] = {0};
    uint8_t cached_root_hash[ROOT_HASH_SIZE] = {0};
    memset(&cal_root_hash_buf, 0, sizeof(cal_root_hash_buf_t));

    // children_hash[left_child_hash, right_child_hash], key_id, vmc_entry_nr, pairing_nonce
    memcpy(cal_root_hash_buf.children_hash, &children->left_child.internal, HASH_VALUE_SIZE);
    memcpy(cal_root_hash_buf.children_hash + HASH_VALUE_SIZE, &children->rigth_child.internal, HASH_VALUE_SIZE);

    // copy pairing nonce
    if (!copy_global_pairing_nonce(&cal_root_hash_buf.pairing_nonce[0]))
        return OP_ERROR_INTERNAL;

    ret = get_cached_rpepoch(&cal_root_hash_buf.rp_epoch);
    if(OP_SUCCESS != ret)
    {
        return OP_ERROR_INTERNAL;
    }
    ret = get_cached_roothash(cached_root_hash);
    if(OP_SUCCESS != ret)
    {
        return OP_ERROR_INTERNAL;
    }

    // calculate SHA256-128 for root node
    stat = sgx_sha256_128_msg((const uint8_t *)&cal_root_hash_buf, sizeof(cal_root_hash_buf_t), (sgx_sha256_128_hash_t*)root_hash);
    if (stat != SGX_SUCCESS)
    {
        return OP_ERROR_INTERNAL;
    }

    // compare root hash saved in PSDA's RPDATA and the calculated  one
    if (0 != memcmp(cached_root_hash, root_hash, ROOT_HASH_SIZE) )
    {
        // root of the vmc database is invalid
        return OP_ERROR_INVALID_VMC_DB;
    }
    else
    {
        // root of the vmc database is valid
        return OP_SUCCESS;
    }

}

/*******************************************************************
**  Function name: pse_vmc_database_check_error
**  Descrption: check the integrity of the existed VMC Database, and try to fix fixable 
**  errors. The integrity check will only be taken on the root node of the VMC hash tree. 
**  
*******************************************************************/
pse_op_error_t pse_vmc_database_check_error()
{
    pse_vmc_children_of_root_t children;
    pse_op_error_t rc = OP_SUCCESS;

    memset(&children, 0, sizeof(pse_vmc_children_of_root_t));

    // Read children nodes of the vmc hash tree root node from SQLite DB via OCALL
    rc = get_db_children_of_root(&children);
    if(rc != OP_SUCCESS)
    {
        goto clean_up;
    }

    // Calculate the root hash by children nodes and then compare it with 
    // the root hash read from DB and the root hash read from rpdata of PSDA.
    rc = verify_root_node(&children);
    if(OP_SUCCESS != rc)
    {
        if(OP_ERROR_INVALID_VMC_DB == rc)
        {
            // try to rollback
            rc = rollback_db_file();
            if(rc != OP_SUCCESS)
            {
                // If rollback recovery failed, should return theoriginal rc to upper layer, which will attempt to re-initialize the DB.
                rc = OP_ERROR_INVALID_VMC_DB;
                goto clean_up;
            }
            // get root and its chilren again 
            memset(&children, 0, sizeof(pse_vmc_children_of_root_t));
            rc = get_db_children_of_root(&children);
            if(rc != OP_SUCCESS)
            {
                goto clean_up;
            }
            rc = verify_root_node(&children);
            if(rc != OP_SUCCESS)
            {
                goto clean_up;
            }
        }
    }

clean_up:
    return rc;
}
