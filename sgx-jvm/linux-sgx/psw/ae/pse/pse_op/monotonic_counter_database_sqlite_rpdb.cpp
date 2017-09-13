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


#include "monotonic_counter_database_sqlite_rpdb.h"
#include "monotonic_counter_database_sqlite_access_hw_mc.h"
#include "monotonic_counter_database_sqlite_bin_hash_tree_utility.h"
#include "monotonic_counter_database_sqlite_cache.h"
#include "pse_op_t.h"
#include "util.h"
#include "utility.h"
#include "sgx_tcrypto.h"

#define CHECK_ERROR_CODE(ret, lable) if(OP_SUCCESS != ret) { ret = OP_ERROR_INTERNAL; goto lable; }

/*******************************************************************
**  Function name: calculate_owner_id
**  Descrption: 
**  Calculate VMC Access Control Information. An ID of the creator of a VMC is defined as:
**    MR=[];
**    If (OwnerPolicy.MRSIGNER==1) MR=MR||REPORT(creator).MRSIGNER;
**    If (OwnerPolicy.MREnclave==1) MR=MR||REPORT(creator).MRENCLAVE;
**    MaskedAttrs = REPORT(creator).ATTRIBUTES & OwnerAttrMask
**    OwnerID =SHA256(MR||MaskedAttrs|| REPORT(creator).ProdID));
*******************************************************************/
static sgx_status_t calculate_owner_id(
    const isv_attributes_t &owner_attributes, // [IN] ISV's attributes info
    const uint16_t mc_policy,               // [IN] user's access control policy
    const uint8_t* mc_att_mask,             // [IN] attribute mask
    void* owner_id)                         // [OUT] ID of the creator of VMC
{
    sgx_sha_state_handle_t ctx = NULL;
    sgx_status_t sgx_ret = SGX_SUCCESS;

    assert(owner_id != NULL && mc_att_mask != NULL);

    do
    {
        sgx_ret = sgx_sha256_init(&ctx);
        BREAK_ON_ERROR(sgx_ret);
        if (mc_policy & MC_POLICY_SIGNER)
        {
            sgx_ret = sgx_sha256_update((const uint8_t *)(&owner_attributes.mr_signer),
                                        sizeof(owner_attributes.mr_signer), 
                                        ctx);
            BREAK_ON_ERROR(sgx_ret);
        }
        if (mc_policy & MC_POLICY_ENCLAVE)
        {
            sgx_ret = sgx_sha256_update((const uint8_t *)(&owner_attributes.mr_enclave),
                                         sizeof(owner_attributes.mr_enclave), 
                                         ctx);
            BREAK_ON_ERROR(sgx_ret);
        }

        uint64_t masked_att_flags = owner_attributes.attribute.flags & *((const uint64_t*)mc_att_mask);
        sgx_ret = sgx_sha256_update((const uint8_t *)&masked_att_flags, sizeof(masked_att_flags), ctx);
        BREAK_ON_ERROR(sgx_ret);
        uint64_t masked_att_xfrm = owner_attributes.attribute.xfrm & *((const uint64_t*)(mc_att_mask+8));
        sgx_ret = sgx_sha256_update((const uint8_t *)&masked_att_xfrm, sizeof(masked_att_xfrm), ctx);
        BREAK_ON_ERROR(sgx_ret);
        sgx_ret = sgx_sha256_update((const uint8_t *)(&owner_attributes.isv_prod_id), sizeof(sgx_prod_id_t), ctx);
        BREAK_ON_ERROR(sgx_ret);
        sgx_ret = sgx_sha256_get_hash(ctx, (sgx_sha256_hash_t*)owner_id);
        BREAK_ON_ERROR(sgx_ret);
    } while (0);

    if(ctx)
    {
        sgx_status_t ret = sgx_sha256_close(ctx);
        sgx_ret = (sgx_ret != SGX_SUCCESS)? sgx_ret : ret;
    }

    return sgx_ret;
}

/*******************************************************************
**  Function name: rpdb_accessible
**  Descrption: check permission of accessing to a VMC entry, according to UUID and ISV's attributes.
**  Return Value:
**    OP_SUCCESS --- the ISV has access right of the VMC
**    else ERROR    --- the ISV doesn't have access right of the VMC
**  
*******************************************************************/
static pse_op_error_t rpdb_accessible(
    const isv_attributes_t &owner_attributes,  //[in] ISV's attributes
    const vmc_data_blob_t &vmc)  // [in] Points to VMC data blob buffer read from SQLite Database
{
    uint8_t sha256_value[32] = {0};

    // calculate ID of ISV
    if(SGX_SUCCESS!= calculate_owner_id(owner_attributes, vmc.owner_policy, (const uint8_t*)(vmc.owner_attr_mask), sha256_value))
    {
        return OP_ERROR_INTERNAL;
    }
    // compare the ID of ISV and the ID of creator of the VMC entry.
    if(memcmp(sha256_value, vmc.owner_id, sizeof(sha256_value)))
    {
        return OP_ERROR_INVALID_OWNER;
    }
    // if ISV's SVN is less than the SVN of the VMC's creator, deny access. 
    if (vmc.owner_svn > owner_attributes.isv_svn)
    {
        return OP_ERROR_INVALID_OWNER;
    }
    // PASS permission check.
    return OP_SUCCESS;
}

/*******************************************************************
**  Function name: operate_vmc
**  Descrption: 
**  This function perform READ or WRITE operation on VMC SQLite Database, which depends 
**  on the parameter rpdb_op. 
**  
*******************************************************************/
static pse_op_error_t operate_vmc(const isv_attributes_t &owner_attributes,    // [IN] ISV's attributes that 
                                  const mc_rpdb_uuid_t &mc_rpdb_uuid,          // [IN] UUID of VMC
                                  vmc_data_blob_t &rpdb,                       // [IN,OUT] Pointer that points to VMC data blob
                                  rpdb_op_t rpdb_op,                           // [IN] operation type
                                  op_leafnode_flag_t* op_leafnode_flag_info)   // [IN] flag struture used to update USED flag in SQLite Database
{
    pse_op_error_t rc = OP_SUCCESS;
    sgx_status_t stat = SGX_SUCCESS;
    uint32_t entry_index = 0;
    uint32_t leaf_id;
    uint32_t invalid_node_id;
    pse_vmc_hash_tree_cache_t cache;
    bool is_read_from_cache = false;
    uint8_t rpdata_roothash[ROOT_HASH_SIZE] = {0}; 

    memset(&cache, 0, sizeof(cache));
    // For CREATE/DELETE operation, op_leafnode_flag_info must point to a valid flag struture, since 
    // the USED flag and QOUTA table in SQLite Database would be updated according to the flag info.
    assert(!((RPDB_OP_CREATE == rpdb_op || RPDB_OP_DELETE == rpdb_op) && !op_leafnode_flag_info));

    // get RPDB ID for UUID
    memcpy(&entry_index, mc_rpdb_uuid.entry_index, UUID_ENTRY_INDEX_SIZE);
    if(entry_index > (INIT_LEAF_NODE_ID_BASE-1))
    {
        return OP_ERROR_INVALID_COUNTER;
    }

    // Read the current RPDATA from CSME to check whether the cached vm db is not of date.
    // The newly read RPDATA_EPOCH must match the cached RPDATA_EPOCH.
    if ((rc = read_rpdata()) != OP_SUCCESS)
    {
        return rc;
    }

    if ((rc = get_cached_roothash(rpdata_roothash)) != OP_SUCCESS)
    {
        return rc;
    }

    // get LEAF ID from RPDB ID.
    leaf_id = entry_index + INIT_LEAF_NODE_ID_BASE;

    // set all related nodes ids
    set_related_nodes_ids(leaf_id, &cache);

    // check the cached vmc db root with current rpdata value
    // if the cached vmc db is not out of date, try to get all related nodes from cache
    rc = access_hash_tree_cache(rpdb_op, CACHE_OP_READ, &cache, rpdata_roothash);
    if(OP_SUCCESS == rc)
    {
        is_read_from_cache = true;
    }

    if(!is_read_from_cache)
    {
        // not found in the cache, need to read from VMC DB -- OCall
        stat = sqlite_read_db(&rc, 
                          leaf_id, 
                          &cache);
        if(OP_SUCCESS != rc || SGX_SUCCESS != stat)
        {
            if (stat != SGX_SUCCESS) 
            {
                rc = OP_ERROR_INTERNAL;
            }
            goto clean_up;
        }

        // node ids may be modified in untrusted domain, so reset them
        set_related_nodes_ids(leaf_id, &cache);

        // verify the integrition of the data
        rc = verify_related_nodes_of_leaf(&cache, &invalid_node_id);
        if(OP_SUCCESS != rc)
        {
            goto clean_up;
        }
    }

    if(RPDB_OP_READ == rpdb_op)
    {
        // first check the is_used flag, a VMC must have been created before being read.
        if(0 == cache.self.leaf.is_used)
        {
            rc = OP_ERROR_INVALID_COUNTER;
            goto clean_up;
        }
        if(0 != memcmp(mc_rpdb_uuid.nonce, cache.self.leaf.nonce, UUID_NONCE_SIZE))
        {
            rc = OP_ERROR_INVALID_COUNTER;
            goto clean_up;
        }

        memcpy(&rpdb, &cache.self.leaf, LEAF_NODE_SIZE);

        if(!is_read_from_cache) // need to update caculated root into cache buffer
        {
            if ((rc = get_cached_roothash(cache.root.hash)) != OP_SUCCESS)
            {
                goto clean_up;
            }
        }
        rc = OP_SUCCESS;
        // for READ operation, return here.
        goto clean_up;
    }

    // READING RPDB won't reach here.
    assert(RPDB_OP_READ != rpdb_op);

    if(rpdb_op == RPDB_OP_CREATE) // CREATING VMC
    {
        if(0 != cache.self.leaf.is_used)
        {
            rc = OP_ERROR_INTERNAL;
            goto clean_up;
        }
    }
    else // INCREMENTING and DELETING VMC need access right.
    {
        // first check the is_used flag, a VMC must have been created before being read.
        if(0 == cache.self.leaf.is_used)
        {
            rc = OP_ERROR_INVALID_COUNTER;
            goto clean_up;
        }
        // nonce must be the same with the value stored in the VMC entry
        if(memcmp(mc_rpdb_uuid.nonce, ((vmc_data_blob_t*)&cache.self.leaf)->nonce, UUID_NONCE_SIZE))
        {
            return OP_ERROR_INVALID_COUNTER;
        }
        // check access right
        rc = rpdb_accessible(owner_attributes, *((vmc_data_blob_t*)&cache.self.leaf));
        if(OP_SUCCESS != rc)
        {
            goto clean_up;
        }
    }

    if(RPDB_OP_INCREMENT == rpdb_op)
    {
        cache.self.leaf.value += 1;
        // output updated leaf node
        memcpy(&rpdb, &cache.self.leaf, LEAF_NODE_SIZE);
    }
    else // rpdb_op is RPDB_OP_CREATE or RPDB_OP_DELETE
    {
        memcpy(&cache.self.leaf, &rpdb, LEAF_NODE_SIZE);
    }

    // calculate all effected nodes new hash value and root hash
    
    rc = update_related_nodes_of_leaf(&cache, 
                                (RPDB_OP_CREATE == rpdb_op || RPDB_OP_DELETE == rpdb_op) ? op_leafnode_flag_info->op_type : NON_OP);
    if(OP_SUCCESS != rc)
    {
        goto clean_up;
    }

    // write back all ralated nodes to database
    stat = sqlite_write_db(&rc, 
                           &cache,
                           (RPDB_OP_CREATE == rpdb_op || RPDB_OP_DELETE == rpdb_op),
                           op_leafnode_flag_info);
    if(OP_SUCCESS != rc || SGX_SUCCESS != stat)
    {
        if (stat != SGX_SUCCESS) 
        {
            rc = OP_ERROR_INTERNAL;
        }
        goto clean_up;
    }

    rc = update_rpdata(cache.root.hash);

clean_up:
    if(OP_SUCCESS == rc)
    {
        // a successful access to VMC entry
        if(RPDB_OP_READ != rpdb_op || !is_read_from_cache)
        {
            // update cache
            access_hash_tree_cache(rpdb_op, CACHE_OP_UPDATE, &cache, NULL);
        }
    }
    
    return rc;
}

/*******************************************************************
**  Function name: create_vmc
**  Descrption: create a VMC in SQLite Database and return UUID of the VMC to the caller.
**  
*******************************************************************/
pse_op_error_t create_vmc(const isv_attributes_t &owner_attributes,  // [IN]  ISV's attributes
                            vmc_data_blob_t &data,         //  [IN,OUT]  VMC blob data
                            mc_rpdb_uuid_t &mc_rpdb_uuid)  //  [OUT]  UUID of VMC
{
    pse_op_error_t rc = OP_SUCCESS;
    sgx_status_t stat = SGX_SUCCESS;

    uint32_t tmp_rpdb_id = {0};
    int leaf_node_id = 0;
    op_leafnode_flag_t op_leafnode_flag_info;
    int retry_times = 1;

    // Check MC Service Availablity Status
    if((rc = get_mc_service_status()) != OP_SUCCESS)
    {
        if ((rc = initialize_sqlite_database_file(false)) != OP_SUCCESS)
            return rc;
    }

    // Calculate Owner ID and copy to "data->owner_id"
    if(SGX_SUCCESS != calculate_owner_id(owner_attributes,
                                         data.owner_policy,
                                         data.owner_attr_mask,
                                         data.owner_id))
    {
        rc = OP_ERROR_INTERNAL;
        goto end;
    }
    // Copy Owner's SVN
    data.owner_svn = owner_attributes.isv_svn;
    
    // read sgx random number for uuid->nonce.
    stat = sgx_read_rand(mc_rpdb_uuid.nonce, UUID_NONCE_SIZE);
    if( SGX_SUCCESS != stat )
    {
        rc = OP_ERROR_INTERNAL;
        goto end;
    }

    do{
        // get an empty database leaf node and return coressponding ID as rpdb ID to caller
        stat = sqlite_get_empty_leafnode(&rc, &leaf_node_id, const_cast<sgx_measurement_t*>(&owner_attributes.mr_signer));
        if(stat == SGX_SUCCESS)  // OCALL success
        {
            if (rc == OP_SUCCESS) 
            {
                // check leaf_node_id
                if (leaf_node_id < INIT_LEAF_NODE_ID_BASE || leaf_node_id > INIT_MAX_LEAF_NODE_ID)
                {
                    // Invalid leaf node id, valid range must be [INIT_LEAF_NODE_ID_BASE, INIT_LEAF_NODE_ID_BASE*2-1]
                    rc = OP_ERROR_INTERNAL;
                    break;
                }
            }
            else if( (rc == OP_ERROR_DATABASE_FATAL || rc == OP_ERROR_INVALID_VMC_DB) && retry_times > 0)
            {
                // try to re-initialize vmc db
                if (OP_SUCCESS != (rc = initialize_sqlite_database_file(true)))
                {
                    break;
                }
                else 
                {
                    // if successful, try to create again
                    continue;
                }
            }
            else 
            {
                // other errors 
                break;
            }
        }
        else  // OCALL failure
        {
            rc = OP_ERROR_INTERNAL;
            break;
        }

        // RPDB ID = LEAD ID - OFFSET(which is INIT_LEAF_NODE_ID_BASE)
        //  Valid range of RPDB ID is from 0 to (INIT_LEAF_NODE_ID_BASE-1).
        tmp_rpdb_id = leaf_node_id - INIT_LEAF_NODE_ID_BASE;

        memcpy(mc_rpdb_uuid.entry_index, &tmp_rpdb_id, UUID_ENTRY_INDEX_SIZE);
        memcpy(data.nonce, mc_rpdb_uuid.nonce, UUID_NONCE_SIZE);

        // mark the flag as USED
        data.is_used = 1;

        // copy creator's mrsigner
        memcpy(&op_leafnode_flag_info.mr_signer, &owner_attributes.mr_signer, sizeof(sgx_measurement_t));

        //  will set the USED flag in SQLite Database
        op_leafnode_flag_info.op_type = SET_LEAFNODE_FLAG;

        // call operate_vmc to store VMC creation info into SQLite Database.
        rc = operate_vmc(owner_attributes,
                        mc_rpdb_uuid, 
                        data, 
                        RPDB_OP_CREATE,
                        &op_leafnode_flag_info);
        if((OP_ERROR_INVALID_VMC_DB == rc || OP_ERROR_DATABASE_FATAL == rc) 
            && retry_times > 0)
        {
            // try to re-initialize vmc db
            if (OP_SUCCESS != (rc = initialize_sqlite_database_file(true)) )
            {
                break;
            }
            else 
            {
                // if successful, try to create vmc again
                continue;
            }
        }
        else 
        {
            break;
        }
    }while(retry_times--);

end:
    if(OP_SUCCESS != rc)
    {
        memset(mc_rpdb_uuid.entry_index, 0xFF, UUID_ENTRY_INDEX_SIZE);
        memset(mc_rpdb_uuid.nonce, 0x0, UUID_NONCE_SIZE);
    }
    return rc;
}

pse_op_error_t read_vmc(
    const isv_attributes_t &owner_attributes,  // [IN] ISV's attributes that 
    const mc_rpdb_uuid_t &mc_rpdb_uuid,  // [IN] UUID of VMC
    vmc_data_blob_t &rpdb)  // [IN,OUT] Pointer that points to VMC data blob
{
    pse_op_error_t op_ret = OP_SUCCESS;

    // Check MC Service Availablity Status
    if((op_ret = get_mc_service_status()) != OP_SUCCESS)
    {
        if ((op_ret = initialize_sqlite_database_file(false)) != OP_SUCCESS)
            return op_ret;
    }

    op_ret = operate_vmc(owner_attributes,
                        mc_rpdb_uuid, 
                        rpdb,  
                        RPDB_OP_READ,
                        NULL); // the op_leafnode_flag_info that is used to update USED flag in SQLite Database is not required for READ operation.
    if (OP_ERROR_INVALID_VMC_DB == op_ret || 
        OP_ERROR_DATABASE_FATAL == op_ret)
    {
        op_ret = initialize_sqlite_database_file(true);
        if(OP_SUCCESS == op_ret)
        {
            // If initilize_sqlite_database_file() is successful, return INVALId_VMC_ID, as the DB initialization cleared all existing VMCs.
            return OP_ERROR_INVALID_COUNTER;
        }
    }

    return op_ret;
}

pse_op_error_t inc_vmc(
    const isv_attributes_t &owner_attributes,  // [IN] ISV's attributes that 
    const mc_rpdb_uuid_t &mc_rpdb_uuid,  // [IN] UUID of VMC
    vmc_data_blob_t &rpdb)  // [IN,OUT] Pointer that points to VMC data blob
{
    pse_op_error_t op_ret = OP_SUCCESS;

    // Check MC Service Availablity Status
    if((op_ret = get_mc_service_status()) != OP_SUCCESS)
    {
        if ((op_ret = initialize_sqlite_database_file(false)) != OP_SUCCESS)
            return op_ret;
    }

    op_ret = operate_vmc(owner_attributes,
                        mc_rpdb_uuid, 
                        rpdb,  
                        RPDB_OP_INCREMENT,
                        NULL); // the op_leafnode_flag_info that is used to update USED flag in SQLite Database is not required for INC operation.
    if (OP_ERROR_INVALID_VMC_DB == op_ret || 
        OP_ERROR_DATABASE_FATAL == op_ret)
    {
        op_ret = initialize_sqlite_database_file(true);
        if(OP_SUCCESS == op_ret)
        {
            // If initilize_sqlite_database_file() is successful, return INVALId_VMC_ID, as the DB initialization cleared all existing VMCs.
            return OP_ERROR_INVALID_COUNTER;
        }
    }

    return op_ret;
}


/*******************************************************************
**  Function name: delete_vmc
**  Descrption: delete VMC from SQLite Database according to the specified UUID.
**  
*******************************************************************/
pse_op_error_t delete_vmc(const isv_attributes_t &owner_attributes,  // [IN] ISV's attributes
                            const mc_rpdb_uuid_t &mc_rpdb_uuid)  //  [IN]  UUID of VMC
{
    pse_op_error_t op_ret = OP_SUCCESS;
    hash_tree_leaf_node_t leaf_node;
    uint32_t rpdb_id = 0;
    op_leafnode_flag_t op_leafnode_flag_info;

    // Check MC Service Availablity Status
    if((op_ret = get_mc_service_status()) != OP_SUCCESS)
    {
        if ((op_ret = initialize_sqlite_database_file(false)) != OP_SUCCESS)
            return op_ret;
    }

    memset(&leaf_node, 0, sizeof(leaf_node));

    // get RPDB ID
    memcpy(&rpdb_id, mc_rpdb_uuid.entry_index, UUID_ENTRY_INDEX_SIZE);
    // check the RPDB ID
    if(rpdb_id>(INIT_LEAF_NODE_ID_BASE-1))
    {
        return OP_ERROR_INVALID_COUNTER;
    }

    // will clear the USED flag in SQLite Database
    op_leafnode_flag_info.op_type = CLR_LEAFNODE_FLAG;

    // call operate_vmc to delete VMC blob from SQLite Database.
    op_ret = operate_vmc(owner_attributes,
                        mc_rpdb_uuid, 
                        leaf_node, 
                        RPDB_OP_DELETE,
                        &op_leafnode_flag_info);
    if (OP_ERROR_INVALID_VMC_DB == op_ret || 
        OP_ERROR_DATABASE_FATAL == op_ret)
    {
        op_ret = initialize_sqlite_database_file(true);
    }

    return op_ret;
}

