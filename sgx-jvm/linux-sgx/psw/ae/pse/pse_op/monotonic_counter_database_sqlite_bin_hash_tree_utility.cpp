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


#include "monotonic_counter_database_sqlite_bin_hash_tree_utility.h"
#include "monotonic_counter_database_sqlite_access_hw_mc.h"
#include "pse_op_t.h"
#include "monotonic_counter_database_sqlite_cache.h"
#include "util.h"
#include "t_pairing_blob.h"
#include "monotonic_counter_database_sqlite_check_error.h"
#include "psda_service.h"
#include "sgx_tcrypto.h"
#include "sgx_sha256_128.h"

#define CHECK_ERROR_CODE(ret, lable) if(OP_SUCCESS != ret) { ret = OP_ERROR_INTERNAL; goto lable; }
#define ASSEMBLE_SELF_BROTHER(id,buf,self,brother,size) \
    if(IS_LEFT_CHILD(id)) {memcpy(buf,self,size); memcpy(((uint8_t*)buf)+(size),brother,size);} \
    else {memcpy(buf,brother,size); memcpy(((uint8_t*)buf)+(size),self,size);}

static pse_op_error_t g_mc_service_status = OP_ERROR_INTERNAL; // indicates VMC service status

/*******************************************************************
**  Function name: get_mc_service_status
**  Descrption: get monotonic counter service status
**  
*******************************************************************/
pse_op_error_t get_mc_service_status()
{
    return g_mc_service_status;
}

/*******************************************************************
**  Function name: set_related_nodes_ids
**  Descrption: set all related nodes id into the cache structure
**  
*******************************************************************/
void set_related_nodes_ids(uint32_t leaf_node_index, pse_vmc_hash_tree_cache_t* cache)
{
    assert(cache != NULL);

    uint32_t node_index = leaf_node_index;
    uint32_t ancestor_index = leaf_node_index;
    uint32_t i = 0;

    cache->self.node_id = leaf_node_index;
    cache->brother.node_id = IS_LEFT_CHILD(node_index) ? (node_index+1) : (node_index-1);;

    ancestor_index = ( ancestor_index - ancestor_index%2 ) >> 1 ;
    while (ancestor_index != 1)
    {
        cache->ancestors[i].node_id = ancestor_index;
        cache->brother_of_ancestors[i].node_id = 
            IS_LEFT_CHILD(ancestor_index) ? (ancestor_index+1) : (ancestor_index-1);
        ancestor_index = ( ancestor_index - ancestor_index%2 ) >> 1 ;
        i++;
    }
}

/*******************************************************************
**  Function name: update_related_nodes_of_leaf
**  Descrption: Update all related nodes of a leaf node 
**  
*******************************************************************/
pse_op_error_t update_related_nodes_of_leaf(pse_vmc_hash_tree_cache_t* cache,
                                            leafnode_flag_op_type flag_op)
{
    pse_op_error_t ret = OP_SUCCESS;
    uint8_t hash[HASH_VALUE_SIZE] = {0};
    uint8_t root_hash[ROOT_HASH_SIZE] = {0};
    hash_tree_leaf_node_t leaf[2];
    sgx_status_t stat = SGX_SUCCESS;
    uint32_t index = 0;
    cal_root_hash_buf_t tmp;

    if(SET_LEAFNODE_FLAG == flag_op)
    {
        cache->self.leaf.is_used = 1;
    }
    else if(CLR_LEAFNODE_FLAG == flag_op)
    {
        cache->self.leaf.is_used = 0;
    }

    //update hash value for the ancestor of the leaf node
    ASSEMBLE_SELF_BROTHER(cache->self.node_id, 
                        &leaf[0],
                        &cache->self.leaf, 
                        &cache->brother.leaf, 
                        LEAF_NODE_SIZE);
    stat = sgx_sha256_msg((uint8_t*)leaf,
                          2*LEAF_NODE_SIZE,
                          (sgx_sha256_hash_t*)hash);
    if(stat != SGX_SUCCESS)
    {
        assert(stat != SGX_ERROR_OUT_OF_MEMORY);
        ret = OP_ERROR_INTERNAL;
        goto end;
    }

    memcpy(cache->ancestors[0].internal.hash, hash, HASH_VALUE_SIZE);

    // update internal nodes
    uint8_t internal_node_hash[HASH_VALUE_SIZE*2];
    for(index=0; index<(INIT_INTERNAL_NODE_NR-1); index++)
    {
        ASSEMBLE_SELF_BROTHER(cache->ancestors[index].node_id, 
                &internal_node_hash[0],
                cache->ancestors[index].internal.hash,
                cache->brother_of_ancestors[index].internal.hash, 
                HASH_VALUE_SIZE);
        stat = sgx_sha256_msg((uint8_t*)internal_node_hash,
                              HASH_VALUE_SIZE*2,
                              (sgx_sha256_hash_t*)hash);
        if(stat != SGX_SUCCESS)
        {
            assert(stat != SGX_ERROR_OUT_OF_MEMORY);
            ret = OP_ERROR_INTERNAL;
            goto end;
        }

        memcpy(cache->ancestors[index+1].internal.hash, hash, HASH_VALUE_SIZE);
    }

    // update root node
    memset(&tmp, 0, sizeof(cal_root_hash_buf_t));
    // now,  ancestor_node points to ROOT and brother_node points to the child of ROOT
    // determine side of the node
    index = INIT_INTERNAL_NODE_NR-1;
    ASSEMBLE_SELF_BROTHER(cache->ancestors[index].node_id, 
                        &tmp.children_hash[0], 
                        cache->ancestors[index].internal.hash,
                        cache->brother_of_ancestors[index].internal.hash, 
                        HASH_VALUE_SIZE);

    if (!copy_global_pairing_nonce(&tmp.pairing_nonce[0]))
    {
        ret = OP_ERROR_INTERNAL;
        goto end;
    }

    ret = get_cached_rpepoch(&tmp.rp_epoch);
    CHECK_ERROR_CODE(ret, end)

    // calculate sha256-128 for root node
    stat = sgx_sha256_128_msg((const uint8_t *)&tmp, sizeof(cal_root_hash_buf_t), (sgx_sha256_128_hash_t*)root_hash);
    if (stat != SGX_SUCCESS)
    {
        assert(stat != SGX_ERROR_OUT_OF_MEMORY);
        ret = OP_ERROR_INTERNAL;
        goto end;
    }

    memcpy(cache->root.hash, root_hash, ROOT_HASH_SIZE);

end:
    return ret;
}

/********************************************************************************
 * @brief Verify the related nodes of a leaf node
 *
 * @param pse_vmc_hash_tree_cache_t          All related nodes of the leaf node, including the leaf node itself
 * @param invalid_node_id                    [out] To store invalid node id when verification fails.
 *
 * @return SGX_SUCCESS for a successful verification.
 ********************************************************************************/
 // split it to different levels
pse_op_error_t verify_related_nodes_of_leaf(const pse_vmc_hash_tree_cache_t* cache,
                                            uint32_t* invalid_node_id)
{
    sgx_status_t stat = SGX_SUCCESS;
    pse_op_error_t ret = OP_SUCCESS;
    uint8_t rpdata_roothash[ROOT_HASH_SIZE] = {0};
    uint8_t hash[HASH_VALUE_SIZE] = {0};
    uint8_t root_hash[ROOT_HASH_SIZE] = {0};
    hash_tree_leaf_node_t leaf[2];
    uint32_t index = 0;
    cal_root_hash_buf_t tmp;

    assert(cache != NULL);
    assert(invalid_node_id != NULL);

    ////////////////////////////////////////////
    //   FINISH TO CHECK THE INPUT PARAMETERS //
    //   FRIST, VERIFY THE ROOT NODE          //
    ////////////////////////////////////////////

    memset(&tmp, 0, sizeof(cal_root_hash_buf_t));

    // need to determine the left child of the root is in bro or par node array
    index = INIT_INTERNAL_NODE_NR - 1;
    ASSEMBLE_SELF_BROTHER(cache->ancestors[index].node_id, 
                        &tmp.children_hash[0], 
                        cache->ancestors[index].internal.hash,
                        cache->brother_of_ancestors[index].internal.hash, 
                        HASH_VALUE_SIZE);

    ret = get_cached_roothash(rpdata_roothash);	
    CHECK_ERROR_CODE(ret, end)

    ret = get_cached_rpepoch(&tmp.rp_epoch);
    CHECK_ERROR_CODE(ret, end)

    if (!copy_global_pairing_nonce(&tmp.pairing_nonce[0]))
    {
        ret = OP_ERROR_INTERNAL;
        goto end;
    }

    // calculate SHA256-128 for root node
    stat = sgx_sha256_128_msg((const uint8_t *)&tmp, sizeof(cal_root_hash_buf_t), (sgx_sha256_128_hash_t*)root_hash);
    if(stat != SGX_SUCCESS)
    {
        assert(stat != SGX_ERROR_OUT_OF_MEMORY);
        ret = OP_ERROR_INTERNAL;
        goto end;
    }

    if(0 != memcmp(root_hash, rpdata_roothash, ROOT_HASH_SIZE))
    {
        // this indicates that the whole hash tree is invalid.
        ret = OP_ERROR_INVALID_VMC_DB;
        *invalid_node_id = 1; // ROOT
        goto end;
    }

    ////////////////////////////////////////////////////////////////////
    //                     Second, VERIFY THE INTERNAL NODES          //
    ////////////////////////////////////////////////////////////////////
    uint8_t internal_nodes_hash[HASH_VALUE_SIZE*2];
    for (index = INIT_INTERNAL_NODE_NR - 1; index > 0; index--) 
    {
        ASSEMBLE_SELF_BROTHER(cache->ancestors[index-1].node_id, 
                &internal_nodes_hash[0],
                cache->ancestors[index-1].internal.hash,
                cache->brother_of_ancestors[index-1].internal.hash, 
                HASH_VALUE_SIZE);
        // calculate hash value
        stat = sgx_sha256_msg((uint8_t*)internal_nodes_hash,
                              HASH_VALUE_SIZE*2,
                              (sgx_sha256_hash_t*)hash);
        if(stat != SGX_SUCCESS)
        {
            assert(stat != SGX_ERROR_OUT_OF_MEMORY);
            ret = OP_ERROR_INTERNAL;
            goto end;
        }

        if(0 != memcmp(hash, cache->ancestors[index].internal.hash, HASH_VALUE_SIZE))
        {
            ret = OP_ERROR_INVALID_VMC_DB;
            *invalid_node_id = cache->ancestors[index].node_id;
            goto end;
        }
    }

    ////////////////////////////////////////////////////////////////////
    //                     Last, VERIFY THE LEAF NODE                 //
    ////////////////////////////////////////////////////////////////////
    ASSEMBLE_SELF_BROTHER(cache->self.node_id, 
                        &leaf[0],
                        &cache->self.leaf, 
                        &cache->brother.leaf, 
                        LEAF_NODE_SIZE);
    stat = sgx_sha256_msg((uint8_t*)leaf,
                          2*LEAF_NODE_SIZE,
                          (sgx_sha256_hash_t*)hash);
    if(stat != SGX_SUCCESS)
    {
        assert(stat != SGX_ERROR_OUT_OF_MEMORY);
        ret = OP_ERROR_INTERNAL;
        goto end;
    }

    if(0 != memcmp(hash, cache->ancestors[0].internal.hash, HASH_VALUE_SIZE))
    {
       ret = OP_ERROR_INVALID_VMC_DB;
       *invalid_node_id = cache->ancestors[0].node_id;
       goto end;
    }

end:
    return ret;
}

/*******************************************************************
**  Function name: get_db_children_of_root
**  Descrption: read children nodes of the root from SQLite VMC Database.
**  
*******************************************************************/
pse_op_error_t get_db_children_of_root(pse_vmc_children_of_root_t* children)
{
    pse_op_error_t retval;
    sgx_status_t stat = SGX_SUCCESS;

    assert(children != NULL);

    // OCALL
    stat = sqlite_read_children_of_root(&retval, children);
    if (stat != SGX_SUCCESS) 
    {
        return OP_ERROR_INTERNAL;
    }

    return retval;
}

/*******************************************************************
**  Function name: initialize_sqlite_database_file
**  Descrption: Initialize VMC database. If vmc db already exists, this function loads
**              and verifies the root node. It also tries to recover the vmc db from an
**              unstable state. If vmc db doesn't exist or is_for_empty_db_creation is true
**              this function will create a new vmc database.
*******************************************************************/
pse_op_error_t initialize_sqlite_database_file(bool is_for_empty_db_creation)
{
    pse_op_error_t ret = OP_ERROR_INTERNAL;
    sgx_status_t stat = SGX_SUCCESS;
    cal_root_hash_buf_t cal_root_hash_buf;
    hash_tree_internal_node_t internal_node;
    hash_tree_root_node_t pre_calculated_root_node;

    memset(&cal_root_hash_buf, 0, sizeof(cal_root_hash_buf));
    memset(&pre_calculated_root_node, 0, sizeof(pre_calculated_root_node));

    flush_hash_tree_cache(); // remove all cached hash tree nodes from cache memory 

    // Read RPDATA from CSE.
    if ((ret = read_rpdata()) != OP_SUCCESS)
    {
        g_mc_service_status = ret;
        return ret;
    }

    // After reading CSE RPDATA, it's safe to use cached rpdata and rpepoch from now on

    if(!is_for_empty_db_creation)
    {
        // check if DB exists and verify root node by root hash
        ret = pse_vmc_database_check_error();

        if(OP_ERROR_DATABASE_FATAL == ret || OP_ERROR_INVALID_VMC_DB == ret)
        {
            // Database is broken, proceed to re-initialization process
        }
        else 
        {
            g_mc_service_status = ret;
            return ret;
        }
    }

    // Clear PSDA's RPDATA first
    //
    //If reset_rpdata() caused the RPEPCOH change in CSME but the full operation (reporting the success back to PSE-Op) somehow failed, 
    //the mismatch between the RPEPOCH cache and the RPEPOCH in CSME will flag potential attack on all RPDATA operation. 
    //To allow recovery from RPEPOCH mismatch resulted from partial reset_rpdata() operation, 
    //reset the RPEPOCH/RPDATA cache. 
    //It's safe to clear the RPEPOCH cache in the VMC service initialization flow, 
    //because no VMC operation can proceed without a successful VMC service initialization.

    if ((ret = reset_rpdata()) != OP_SUCCESS)
    {
        // clear RPDATA cache to handle the situation that the reset_rpdata() caused the RPEPCOH change in CSME 
        // but the full operation (reporting the success back to PSE-Op) somehow failed.
        clear_cached_rpdata();
        goto clean_up;
    }
    // find children's hash value of the root node from precalculatated internal_node_hash_value_table
    memcpy(internal_node.hash, &(internal_node_hash_value_table[INIT_MAX_HASH_TREE_LAYER-3][0]), HASH_VALUE_SIZE);
    memcpy(cal_root_hash_buf.children_hash, internal_node.hash, HASH_VALUE_SIZE);
    memcpy(cal_root_hash_buf.children_hash + HASH_VALUE_SIZE, internal_node.hash, HASH_VALUE_SIZE);

    // copy epoch
    ret = get_cached_rpepoch(&cal_root_hash_buf.rp_epoch);
    CHECK_ERROR_CODE(ret, clean_up)

    // copy pairing NONCE
    if (!copy_global_pairing_nonce(&cal_root_hash_buf.pairing_nonce[0]))
    {
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }

    // calculate sha256-128 form root node
    stat = sgx_sha256_128_msg((const uint8_t *)&cal_root_hash_buf, sizeof(cal_root_hash_buf_t), (sgx_sha256_128_hash_t *)pre_calculated_root_node.hash);
	
    if (stat != SGX_SUCCESS)
    {
        assert(stat != SGX_ERROR_OUT_OF_MEMORY);
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }

    // update PSDA's rpdata
    ret = update_rpdata(pre_calculated_root_node.hash);
    if(OP_SUCCESS != ret)
    {
        goto clean_up;
    }
	

    // OCALL db reinit
    stat = sqlite_db_init_hash_tree_table(&ret);
    if (stat != SGX_SUCCESS || ret != OP_SUCCESS)
    {
        ret = OP_ERROR_INTERNAL;
    }

clean_up:

    g_mc_service_status = ret;
    return ret;
}

pse_op_error_t rollback_db_file()
{
    // recovery from backuped DB file
    pse_op_error_t ret = OP_ERROR_INTERNAL;
    sgx_status_t stat = SGX_SUCCESS;

    // OCALL db recovery
    stat = sqlite_rollback_db_file(&ret);

    if (stat != SGX_SUCCESS || ret != OP_SUCCESS)
    {
        return OP_ERROR_INTERNAL;
    }

    return OP_SUCCESS;
}

