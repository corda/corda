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


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <malloc.h>
#include <stdint.h>
#include <math.h>
#include <assert.h>
#include "se_wrapper.h"

#include "se_memcpy.h"
#include "sqlite3.h"
#include "monotonic_counter_database_types.h"
#include "oal/oal.h"

#include "sgx_profile.h"

#define ANCESTOR_ID(x) ((x)>>1)
#define BROTHER_ID(x) (((x)%2 == 0)?((x)+1):((x)-1))

#define HASH_TREE_NODE_TYPE_UNKNOWN  0
#define HASH_TREE_NODE_TYPE_ROOT     1
#define HASH_TREE_NODE_TYPE_INTERNAL 2
#define HASH_TREE_NODE_TYPE_LEAF     3

#define HASH_TREE_LEAF_NODE_GET      0
#define HASH_TREE_LEAF_NODE_PUT      1
#define HASH_TREE_LEAF_NODE_UPDATE   2

#define EXIT_IFNOT_SQLITE_OK(rc, lable)  if(SQLITE_OK!=(rc)){ret=OP_ERROR_SQLITE_INTERNAL;goto lable;}

static pse_vmc_db_state_t pse_vmc_db_state = PSE_VMC_DB_STATE_DOWN;

// layout of node_index_array is : Leaf Node ID + ancestor Node IDs(12 nodes) + Brother Node IDs(13 nodes).
static void find_all_related_node_index(uint32_t leaf_node_index, uint32_t node_index_array[])
{
    uint32_t leaf_index = leaf_node_index;
    uint32_t ancestor_index, brother_index;
    uint32_t i = 0;
    uint32_t* ancestor_node_index_array = node_index_array + 1; // node_index_array[0] is for the leaf node
    uint32_t* brother_node_index_array = ancestor_node_index_array + INIT_TOTAL_ANCESTORS_NODE_NUMBER;

    assert(leaf_node_index >= INIT_MIN_LEAF_NODE_ID && leaf_node_index <= INIT_MAX_LEAF_NODE_ID);

    node_index_array[0] = leaf_node_index;
    do{
        assert(i <= INIT_TOTAL_ANCESTORS_NODE_NUMBER);

        brother_index = BROTHER_ID(leaf_index);
        brother_node_index_array[i] = brother_index;
        ancestor_index = ANCESTOR_ID(leaf_index);

        if(1 != ancestor_index)
        {
            ancestor_node_index_array[i] = ancestor_index ;
            leaf_index = ancestor_index ;
            i++;
    	}
    }while(1 != ancestor_index);        // 1 is root node index
}


static pse_op_error_t sqlite_open_db(sqlite3 **db)
{
    int rc = 0;
    pse_op_error_t ret = OP_SUCCESS;
    char vmc_db_path[MAX_PATH] = {0};

    pse_vmc_db_state = PSE_VMC_DB_STATE_WORKABLE;

    if(aesm_get_cpathname(FT_PERSISTENT_STORAGE, VMC_DATABASE_FID, vmc_db_path, MAX_PATH) != AE_SUCCESS) 
    {
        *db = NULL;
        return OP_ERROR_INTERNAL;
    }

    rc = sqlite3_open_v2(vmc_db_path, db, SQLITE_OPEN_READWRITE, NULL);
    if( SQLITE_OK != rc )
    {
        // Can't open database
        sqlite3_close_v2(*db);
        *db = NULL;
        if(SQLITE_ERROR == rc || SQLITE_CORRUPT == rc || SQLITE_CANTOPEN == rc)
        {
            ret = OP_ERROR_DATABASE_FATAL;
        }
        else
        {
            ret = OP_ERROR_SQLITE_INTERNAL;
        }

        pse_vmc_db_state = PSE_VMC_DB_STATE_DOWN;
    }

    return ret;
}

static pse_op_error_t sqlite_query_int_value(sqlite3 *db, const char* sql_sentence, int* value)
{
    pse_op_error_t ret = OP_SUCCESS;
    int rc;
    sqlite3_stmt *stat = NULL;

    rc = sqlite3_prepare_v2(db, sql_sentence, -1, &stat, 0);
    if(SQLITE_OK != rc)
    {
        ret = OP_ERROR_SQLITE_INTERNAL;
        goto exit;
    }

    rc = sqlite3_step(stat);
    if (SQLITE_DONE == rc)
    {
        *value = 0;
		ret = OP_ERROR_SQLITE_NOT_FOUND;
        goto exit;
    }
    if(SQLITE_ROW != rc)
    {
        ret = OP_ERROR_SQLITE_INTERNAL;
        goto exit;
    }

    rc = sqlite3_column_type(stat, 0);
    if(SQLITE_INTEGER != rc)
    {
        if(SQLITE_NULL == rc)
        {
            ret = OP_ERROR_SQLITE_NOT_FOUND; // SQlite returned a NULL record that indicates "NOT FOUND"
        }
        else
        {
            ret = OP_ERROR_INVALID_VMC_DB; // this will trigger DB re-initialization
        }
        goto exit;
    }

    *value = sqlite3_column_int(stat, 0);

exit:
    sqlite3_finalize(stat); 
    return ret;
}

#if 0
inline ULARGE_INTEGER get_system_time()
{
    SYSTEMTIME    systemtime;
    FILETIME      filetime;
    ULARGE_INTEGER current_time = {0};

    // record time for performance measurement
    GetSystemTime(&systemtime);
    if (FALSE == SystemTimeToFileTime(&systemtime, &filetime))
    {
        return current_time;
    }
    memcpy(&current_time, &filetime, sizeof(ULARGE_INTEGER));
    return current_time;
}
#endif

static pse_op_error_t sqlite_update_node(sqlite3_stmt* stat, const uint8_t* blob, uint32_t blob_size, uint32_t id)
{
    pse_op_error_t ret = OP_SUCCESS;
    int rc;

    rc = sqlite3_bind_blob(stat, 1, blob, blob_size, NULL); 
    EXIT_IFNOT_SQLITE_OK(rc, error)

    rc = sqlite3_bind_int(stat, 2, id);
    EXIT_IFNOT_SQLITE_OK(rc, error)

    rc = sqlite3_step(stat); 
    if(rc != SQLITE_DONE)
    {
        ret = OP_ERROR_SQLITE_INTERNAL;
        goto error;
    }

    rc = sqlite3_clear_bindings(stat);
    EXIT_IFNOT_SQLITE_OK(rc, error)

    rc = sqlite3_reset(stat);
    EXIT_IFNOT_SQLITE_OK(rc, error)

    return OP_SUCCESS;

error:
    return ret;
}

static pse_op_error_t backup_vmc_db_file()
{
    char backup_vmc_db_path[MAX_PATH] = {0};
    char vmc_db_path[MAX_PATH] = {0};

    if(aesm_get_pathname(FT_PERSISTENT_STORAGE, VMC_DATABASE_BK_FID, backup_vmc_db_path, MAX_PATH) != AE_SUCCESS) 
    {
        return OP_ERROR_BACKUP_CURRENT_DB;
    }

    if(aesm_get_pathname(FT_PERSISTENT_STORAGE, VMC_DATABASE_FID, vmc_db_path, MAX_PATH) != AE_SUCCESS) 
    {
        return OP_ERROR_BACKUP_CURRENT_DB;
    }

    if(se_copy_file(backup_vmc_db_path, vmc_db_path)!=0){
        return OP_ERROR_BACKUP_CURRENT_DB;
    }

    return OP_SUCCESS;
}

pse_op_error_t sqlite_rollback_db_file()
{
    char backup_vmc_db_path[MAX_PATH] = {0};
    char vmc_db_path[MAX_PATH] = {0};

    if(aesm_get_pathname(FT_PERSISTENT_STORAGE, VMC_DATABASE_BK_FID, backup_vmc_db_path, MAX_PATH) != AE_SUCCESS) 
    {
        return OP_ERROR_BACKUP_CURRENT_DB;
    }

    if(aesm_get_pathname(FT_PERSISTENT_STORAGE, VMC_DATABASE_FID, vmc_db_path, MAX_PATH) != AE_SUCCESS) 
    {
        return OP_ERROR_BACKUP_CURRENT_DB;
    }

    if(se_copy_file(vmc_db_path, backup_vmc_db_path)!=0){
        return OP_ERROR_BACKUP_CURRENT_DB;
    }

    return OP_SUCCESS;
}

static pse_op_error_t copy_prebuild_vmc_db()
{
    char prebuild_vmc_db_path[MAX_PATH] = {0};
    char vmc_db_path[MAX_PATH] = {0};

    if(aesm_get_pathname(FT_PERSISTENT_STORAGE, VMC_DATABASE_PREBUILD_FID, prebuild_vmc_db_path, MAX_PATH) != AE_SUCCESS) 
    {
        return OP_ERROR_COPY_PREBUILD_DB;
    }

    if(aesm_get_pathname(FT_PERSISTENT_STORAGE, VMC_DATABASE_FID, vmc_db_path, MAX_PATH) != AE_SUCCESS) 
    {
        return OP_ERROR_COPY_PREBUILD_DB;
    }

    if(se_copy_file(vmc_db_path, prebuild_vmc_db_path)!=0){
        return OP_ERROR_COPY_PREBUILD_DB;
    }

    return OP_SUCCESS;
}

pse_op_error_t sqlite_read_children_of_root(pse_vmc_children_of_root_t* children)
{
    PROFILE_START("sqlite_read_children_of_root");

    int            rc;
    pse_op_error_t ret = OP_SUCCESS;
    sqlite3_stmt*  stat = NULL;
    char           sql_sentence[1024] = {0};
    int            result;
    uint32_t       node_id;
    const void*    ptr_blob_content;
    uint32_t       blob_len;
    uint32_t       record_count = 0;
    uint32_t       read_list_array[2] = {2,3};
    sqlite3        *db = NULL;

    assert(children != NULL);

    ret = sqlite_open_db(&db);
    if(OP_SUCCESS != ret) 
    {
        pse_vmc_db_state = PSE_VMC_DB_STATE_DOWN;
        PROFILE_END("sqlite_read_children_of_root");
        return ret;
    }

    // prepare sql statement
    if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "select * from HASH_TREE_NODE_TABLE where ID IN (2,3) order by ID asc") < 0)
    {
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }
    
    // prepare sql statement
    rc = sqlite3_prepare_v2(db, sql_sentence, -1, &stat, 0);
    EXIT_IFNOT_SQLITE_OK(rc, clean_up)

    // query
    while ((result = sqlite3_step(stat)) == SQLITE_ROW)
    {
        // to calculate number of records returned 
        record_count++;
        if (record_count > 2)
        {
            ret = OP_ERROR_INVALID_VMC_DB;
            goto clean_up;
        }

        node_id = sqlite3_column_int(stat, 0);
        // The array read_list_array[] contains {2,3}, and the node id read from DB must be 2 or 3. 
        if (node_id != read_list_array[record_count-1])
        {
            ret = OP_ERROR_INVALID_VMC_DB;
            goto clean_up;
        }

        ptr_blob_content = sqlite3_column_blob(stat, 1);
        if(!ptr_blob_content)
        {
            ret = OP_ERROR_INVALID_VMC_DB;
            goto clean_up;
        }
        blob_len = sqlite3_column_bytes(stat, 1);

        // Child Node
        if(blob_len != INTERNAL_NODE_SIZE)
        {
            ret = OP_ERROR_INVALID_VMC_DB;
            goto clean_up;
        }

        // Copy children
        hash_tree_internal_node_t* internal_node_ptr = NULL;
        if (node_id == 2) 
        {
            internal_node_ptr = &children->left_child.internal;
        }
        else 
        {
            internal_node_ptr = &children->rigth_child.internal;
        }

            if(0 != memcpy_s(internal_node_ptr, 
                            blob_len, 
                            ptr_blob_content, 
                            blob_len))
        {
            ret = OP_ERROR_INTERNAL;
            goto clean_up;
        }

    }

    if (record_count != 2)
    {
        ret = OP_ERROR_INVALID_VMC_DB;
        goto clean_up;
    }

    if (result != SQLITE_DONE)
    {
        ret = OP_ERROR_SQLITE_INTERNAL;
    }
    
clean_up:
    sqlite3_finalize(stat);
    assert(db != NULL);
    sqlite3_close_v2(db);

    PROFILE_END("sqlite_read_children_of_root");
    return ret;
}

pse_op_error_t sqlite_read_db(uint32_t leaf_id, pse_vmc_hash_tree_cache_t* cache)
{
    PROFILE_START("sqlite_read_db");
   
    int            rc;
    pse_op_error_t ret = OP_SUCCESS;
    sqlite3_stmt*  stat = NULL;
    char           sql_sentence[1024] = {0};
    int            result;
    uint32_t       node_id;
    const void*    ptr_blob_content;
    uint32_t       blob_len;
    uint32_t       record_count = 0;
    uint32_t       read_list_array[INIT_TOTAL_NODE_NUMBER_FOR_READING] = {0};
    sqlite3        *db = NULL;
    uint8_t*       node_ptr = NULL;
    uint32_t       child_node_id = leaf_id;
    uint32_t       internal_index = 0;
    uint32_t       count = 0;

    if( !cache)
    {
        PROFILE_END("sqlite_read_db");
        return OP_ERROR_INVALID_PARAMETER;
    }

    if(leaf_id < INIT_MIN_LEAF_NODE_ID || leaf_id > INIT_MAX_LEAF_NODE_ID)
    {
        PROFILE_END("sqlite_read_db");
        return OP_ERROR_INVALID_PARAMETER;
    }

    ret = sqlite_open_db(&db);
    if(OP_SUCCESS != ret) 
    {
        pse_vmc_db_state = PSE_VMC_DB_STATE_DOWN;
        PROFILE_END("sqlite_read_db");
        return ret;
    }

    // layout of read_list_array is : Leaf Node ID + ancestor Node IDs + Brother Node IDs.
    find_all_related_node_index(leaf_id, read_list_array);

    // prepare sql statement
    if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "select * from HASH_TREE_NODE_TABLE where ID IN (") < 0)
    {
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }
    
    for(uint32_t index=0; index < INIT_TOTAL_NODE_NUMBER_FOR_READING; index++)
    {
        char id[10];

        if (_snprintf_s(id, sizeof(id), "%u", read_list_array[index]) < 0) 
        {
            ret = OP_ERROR_INTERNAL;
            goto clean_up;
        }

        if (strncat_s(sql_sentence, sizeof(sql_sentence), id, strnlen_s(id, 10)) != 0)
        {
            ret = OP_ERROR_INTERNAL;
            goto clean_up;
        }

        if (index != INIT_TOTAL_NODE_NUMBER_FOR_READING - 1)
        {
            if (strncat_s(sql_sentence, sizeof(sql_sentence), ",", 1) != 0)
            {
                ret = OP_ERROR_INTERNAL;
                goto clean_up;
            }
        }
    }
    if (strcat_s(sql_sentence, sizeof(sql_sentence), ") order by ID desc") != 0)
    {
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }

    // prepare sql statement
    rc = sqlite3_prepare_v2(db, sql_sentence, -1, &stat, 0);
    EXIT_IFNOT_SQLITE_OK(rc, clean_up)

    // query
    // the result set are sorted, from leaf to up layers
    while ((result = sqlite3_step(stat)) == SQLITE_ROW)
    {
        // to calculate number of records returned 
        record_count++;

        node_id = sqlite3_column_int(stat, 0);
        ptr_blob_content = sqlite3_column_blob(stat, 1);
        if(!ptr_blob_content)
        {
            ret = OP_ERROR_INVALID_VMC_DB;
            goto clean_up;
        }
        blob_len = sqlite3_column_bytes(stat, 1);

        if(1 == node_id)
        {
            assert(0);
            ret = OP_ERROR_INVALID_VMC_DB;
            goto clean_up;
        }
        else if(INIT_MIN_LEAF_NODE_ID <= node_id)
        {
            // node_id has already been checked and 
            // it will not exceed INIT_MAX_LEAF_NODE_ID
            assert(node_id <= INIT_MAX_LEAF_NODE_ID);

            // leaf node
            if(blob_len != LEAF_NODE_SIZE)
            {
                ret = OP_ERROR_INVALID_VMC_DB;
                goto clean_up;
            }

            if (node_id == leaf_id)
            {
                cache->self.node_id = node_id;
                node_ptr = (uint8_t*)&cache->self.leaf;
            }
            else
            {
                cache->brother.node_id = node_id;
                node_ptr = (uint8_t*)&cache->brother.leaf;
            }
        }
        else 
        {
            assert(node_id <= INIT_MAX_LEAF_NODE_ID);

            // internal nodes
            if(blob_len != INTERNAL_NODE_SIZE)
            {
                ret = OP_ERROR_INVALID_VMC_DB;
                goto clean_up;
            }

            if (node_id == child_node_id / 2)
            {
                // this is ancestor node
                node_ptr = (uint8_t*)&cache->ancestors[internal_index].internal;
                cache->ancestors[internal_index].node_id = node_id;
            }
            else 
            {
                // this is brother of ancestors
                node_ptr = (uint8_t*)&cache->brother_of_ancestors[internal_index].internal;
                cache->brother_of_ancestors[internal_index].node_id = node_id;
            }

            count++;
            if (count == 2)
            {
                // internal nodes are arranged as pairs, one is ancestor, another is the brother of the ancestor
                count = 0;
                internal_index++;   // go up a level
                child_node_id = node_id; // now current node becomes child
            }
        }

        // copy blob data to output buffer
        if(0 != memcpy_s(node_ptr, 
                        blob_len, 
                        ptr_blob_content, 
                        blob_len))
        {
            ret = OP_ERROR_INTERNAL;
            goto clean_up;
        }
    }

    if (record_count != INIT_TOTAL_NODE_NUMBER_FOR_READING)
    {
        ret = OP_ERROR_INVALID_VMC_DB;
        goto clean_up;
    }

    if (result != SQLITE_DONE)
    {
        ret = OP_ERROR_SQLITE_INTERNAL;
    }
    
clean_up:
    assert(db != NULL);
    sqlite3_finalize(stat);
    sqlite3_close_v2(db);

    PROFILE_END("sqlite_read_db");
    return ret;
}

pse_op_error_t sqlite_write_db(pse_vmc_hash_tree_cache_t* cache,
                               uint8_t is_for_update_flag,
                               op_leafnode_flag_t* op_flag_info)
{
    PROFILE_START("sqlite_write_db");

    int rc;
    pse_op_error_t ret = OP_SUCCESS;
    sqlite3_stmt* stat = NULL;
    sqlite3       *db = NULL;
    char          sql_sentence[512] = {0};
    int refid = 0;

    if( !cache)
    {
        PROFILE_END("sqlite_write_db");
        return OP_ERROR_INVALID_PARAMETER;
    }

    if(is_for_update_flag && !op_flag_info)
    {
        PROFILE_END("sqlite_write_db");
        return OP_ERROR_INVALID_PARAMETER;
    }

    // Backup DB first
    ret = backup_vmc_db_file();
    if(OP_SUCCESS != ret)
    {
        pse_vmc_db_state = PSE_VMC_DB_STATE_DOWN;
        PROFILE_END("sqlite_write_db");
        return ret;
    }

    ret = sqlite_open_db(&db);
    if(OP_SUCCESS != ret)
    {
        pse_vmc_db_state = PSE_VMC_DB_STATE_DOWN;
        PROFILE_END("sqlite_write_db");
        return ret;
    }

    rc = sqlite3_exec(db, "BEGIN TRANSACTION;", NULL, NULL, NULL);
    EXIT_IFNOT_SQLITE_OK(rc, error)

    if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "update HASH_TREE_NODE_TABLE set node_content=? where ID=?") < 0)
    {
        ret = OP_ERROR_INTERNAL;
        goto error;
    }

    rc = sqlite3_prepare_v2(db, sql_sentence, -1, &stat, 0);
    EXIT_IFNOT_SQLITE_OK(rc, error)

    // update internal nodes
    for (uint32_t index = 0; index < INIT_INTERNAL_NODE_NR; index++)
    {
        // update ancestors
        if ((ret = sqlite_update_node(stat, 
            (uint8_t*)&cache->ancestors[index].internal, 
            INTERNAL_NODE_SIZE, 
            cache->ancestors[index].node_id)) != OP_SUCCESS)
            goto error;

        // update brothers of ancestors
        if ((ret = sqlite_update_node(stat, 
            (uint8_t*)&cache->brother_of_ancestors[index].internal, 
            INTERNAL_NODE_SIZE, 
            cache->brother_of_ancestors[index].node_id)) != OP_SUCCESS)
            goto error;
    }

    // update leaf and its brother
    if ((ret = sqlite_update_node(stat, 
        (uint8_t*)&cache->self.leaf, 
        LEAF_NODE_SIZE, 
        cache->self.node_id)) != OP_SUCCESS)
        goto error;
    if ((ret = sqlite_update_node(stat, 
        (uint8_t*)&cache->brother.leaf, 
        LEAF_NODE_SIZE, 
        cache->brother.node_id)) != OP_SUCCESS)
        goto error;

    if(is_for_update_flag)
    {
        // update USED flag and QUOTA record
        char mrsigner[65] = {0};
        // convert mr_signer to hex string
        for(uint32_t i=0; i < sizeof(sgx_measurement_t); i++)
        {
            char tmp[3];
            if(_snprintf_s(tmp, sizeof(tmp), "%02x", ((uint8_t*)(&op_flag_info->mr_signer))[i]) < 0)
            {
                ret = OP_ERROR_INTERNAL;
                goto error;
            }

            if(0 != strncat_s(mrsigner, sizeof(mrsigner), tmp, sizeof(tmp)))
            {
                ret = OP_ERROR_INTERNAL;
                goto error;
            }
        }

        switch(op_flag_info->op_type)
        {
            case CLR_LEAFNODE_FLAG:
                // read REFID saved in HASH_TREE_NODE_TABLE before deleting the record.
                if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "select REFID from HASH_TREE_NODE_TABLE where ID=%d;", cache->self.node_id) < 0)
                {
                    ret = OP_ERROR_INTERNAL;
                    goto error;
                }
                ret = sqlite_query_int_value(db, sql_sentence, &refid);
                if(OP_SUCCESS != ret && OP_ERROR_SQLITE_NOT_FOUND != ret)
                {
                    goto error;
                }
                // clear REFID and USED flag
                if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "update HASH_TREE_NODE_TABLE set USED=0 and REFID=0 where ID=%d;", cache->self.node_id) < 0)
                {
                    ret = OP_ERROR_INTERNAL;
                    goto error;
                }
                rc = sqlite3_exec(db, sql_sentence, NULL, NULL, NULL);
                if(SQLITE_OK != rc)
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                if(1 != sqlite3_changes(db))
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }

                // update QUOTA, counter--;
                if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "update VMC_QUOTA_TABLE set COUNTER=COUNTER-1 where ID=%d and COUNTER>0;", refid) < 0)
                {
                    ret = OP_ERROR_INTERNAL;
                    goto error;
                }
                rc = sqlite3_exec(db, sql_sentence, NULL, NULL, NULL);
                if(SQLITE_OK != rc)
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                if(1 != sqlite3_changes(db))
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                break;
            case SET_LEAFNODE_FLAG:
                // update QUOTA, counter++;
                if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "update VMC_QUOTA_TABLE set COUNTER=COUNTER+1 where MRSIGNER='%s';", mrsigner) < 0)
                {
                    ret = OP_ERROR_INTERNAL;
                    goto error;
                }
			    rc = sqlite3_exec(db, sql_sentence, NULL, NULL, NULL);
                if(SQLITE_OK != rc)
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                rc = sqlite3_changes(db);
                if(0 == rc)
                {
                    // the mrsigner isn't in quota table yet, so insert it.
                    if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "insert into VMC_QUOTA_TABLE(MRSIGNER,COUNTER) values('%s', 1);", mrsigner) < 0)
                    {
                        ret = OP_ERROR_INTERNAL;
                        goto error;
                    }
                    rc = sqlite3_exec(db, sql_sentence, NULL, NULL, NULL);
                    if(SQLITE_OK != rc)
                    {
                        ret = OP_ERROR_SQLITE_INTERNAL;
                        goto error;
                    }
                    if(1 != sqlite3_changes(db))
                    {
                        ret = OP_ERROR_SQLITE_INTERNAL;
                        goto error;
                    }
                }
                else if(1 == rc)
                {
                    // the mrsigner has been in quota table
                }
                else
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                // read refid
                if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "select ID from VMC_QUOTA_TABLE where MRSIGNER='%s';", mrsigner) < 0)
                {
                    ret = OP_ERROR_INTERNAL;
                    goto error;
                }

                ret = sqlite_query_int_value(db, sql_sentence, &refid);
                if(OP_SUCCESS != ret && OP_ERROR_SQLITE_NOT_FOUND != ret)
                {
                    goto error;
                }

                // Update HASH_TREE_NODE_TABLE
                if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "update HASH_TREE_NODE_TABLE set USED=1 where ID=%d;", cache->self.node_id) < 0)
                {
                    ret = OP_ERROR_INTERNAL;
                    goto error;
                }
                rc = sqlite3_exec(db, sql_sentence, NULL, NULL, NULL);
                if(SQLITE_OK != rc)
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                if(1 != sqlite3_changes(db))
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "update HASH_TREE_NODE_TABLE set REFID=%d where ID=%d;", refid, cache->self.node_id) < 0)
                {
                    ret = OP_ERROR_INTERNAL;
                    goto error;
                }
                rc = sqlite3_exec(db, sql_sentence, NULL, NULL, NULL);
                if(SQLITE_OK != rc)
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                if(1 != sqlite3_changes(db))
                {
                    ret = OP_ERROR_SQLITE_INTERNAL;
                    goto error;
                }
                break;

            default:
                ret = OP_ERROR_INVALID_PARAMETER;
                goto error;
        }
    }

    rc = sqlite3_exec(db, "END TRANSACTION;", NULL, NULL, NULL);
    EXIT_IFNOT_SQLITE_OK(rc, error)

    sqlite3_finalize(stat);
    sqlite3_close_v2(db);

    PROFILE_END("sqlite_write_db");

    return OP_SUCCESS;

error:
    assert(db != NULL);
    sqlite3_finalize(stat);
    sqlite3_exec(db, "ROLLBACK TRANSACTION;", NULL, NULL, NULL);
    sqlite3_close_v2(db);

    PROFILE_END("sqlite_write_db");
    return ret;
}

pse_op_error_t sqlite_get_empty_leafnode(int* leaf_node_id, sgx_measurement_t* mr_signer)
{
    PROFILE_START("sqlite_get_empty_leafnode");

    char mrsigner[65] = {0};
    char sql_sentence[512] = {0};
    int counter = 0;
    pse_op_error_t ret = OP_SUCCESS;
    sqlite3 *db = NULL;

    if(!leaf_node_id || !mr_signer)
    {
        PROFILE_END("sqlite_get_empty_leafnode");
        return OP_ERROR_INVALID_PARAMETER;
    }

    // convert mr_signer to hex string
    for(uint32_t i=0; i < sizeof(sgx_measurement_t); i++)
    {
        char tmp[3];
        if(_snprintf_s(tmp, sizeof(tmp), "%02x", ((uint8_t*)mr_signer)[i]) < 0)
        {
            ret = OP_ERROR_INTERNAL;
            goto clean_up;
        }

        if(0 != strncat_s(mrsigner, sizeof(mrsigner), tmp, sizeof(tmp)))
        {
            ret = OP_ERROR_INTERNAL;
            goto clean_up;
        }

    }

    ret = sqlite_open_db(&db);
    if(OP_SUCCESS != ret)
    {
        pse_vmc_db_state = PSE_VMC_DB_STATE_DOWN;
        PROFILE_END("sqlite_get_empty_leafnode");
        return ret;
    }

    // check QUOTA
    if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "select COUNTER from VMC_QUOTA_TABLE where MRSIGNER='%s';", mrsigner) < 0)
    {
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }

    ret = sqlite_query_int_value(db, sql_sentence, &counter);
    if(OP_SUCCESS != ret && OP_ERROR_SQLITE_NOT_FOUND != ret)
    {
        goto clean_up;
    }

    if(PSE_VMC_QUOTA_SIZE <= counter)
    {
        // exceeds quota, return error.
        ret = OP_ERROR_DATABASE_OVER_QUOTA;
        goto clean_up;
    }

    // the specified MR SIGNER doesn't exceed quota.
    *leaf_node_id = 0;
    if (_snprintf_s(sql_sentence, sizeof(sql_sentence), "select min(ID) from HASH_TREE_NODE_TABLE where USED=0 and ID>%d and ID<%d;", 
        INIT_MIN_LEAF_NODE_ID-1, 
        INIT_MAX_LEAF_NODE_ID+1) < 0)
    {
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }
    ret = sqlite_query_int_value(db, sql_sentence, leaf_node_id);
    if(OP_ERROR_SQLITE_NOT_FOUND == ret)
    {
        ret = OP_ERROR_DATABASE_FULL;
        goto clean_up;
    }

clean_up:
    if(db)
    {
        sqlite3_close_v2(db);
    }

    PROFILE_END("sqlite_get_empty_leafnode");
    return ret;
}

pse_op_error_t sqlite_db_init_hash_tree_table()
{
    pse_op_error_t ret;

    ret = copy_prebuild_vmc_db();
    if(ret != OP_SUCCESS)
    {
        pse_vmc_db_state = PSE_VMC_DB_STATE_DOWN;
        return ret;
    }

    return OP_SUCCESS;
}
