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


// Initialize a fixed size hash tree which has 8192 VMC entries.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <malloc.h>
#include <stdint.h>
#include <math.h>

#include "sqlite3.h"

#include "sgx_error.h"
#include "sgx_tcrypto.h"
#include "se_memcpy.h"
#include "monotonic_counter_database_types.h"

#define SQLITE_DB_FILE_NAME "prebuild_pse_vmc.db"
static sqlite3 *gDb = NULL;

#define EXIT_IFNOT_SQLITE_OK(rc)  if(SQLITE_OK!=(rc)){goto error;}

#define HASH_TREE_NODE_TYPE_UNKNOWN  0
#define HASH_TREE_NODE_TYPE_ROOT     1
#define HASH_TREE_NODE_TYPE_INTERNAL 2
#define HASH_TREE_NODE_TYPE_LEAF     3

// internal_node_hash_value_table[0] is calculated by two empty leaf nodes
// internal_node_hash_value_table[1] is calculated by two internal_node_hash_value_table[0]
// ...

int verify_precalculated_hash_table()
{
    sgx_status_t stat = SGX_SUCCESS;
    vmc_data_blob_t leaf[2] = {0};
    hash_tree_internal_node_t inter[2] = {0};
    hash_tree_internal_node_t inter_parent = {0};

    // inter = sha256(leaf|leaf) which should match internal_node_hash_value_table[0]
    stat = sgx_sha256_msg((uint8_t*)&leaf[0],
                          2*LEAF_NODE_SIZE,
                          (sgx_sha256_hash_t*)&inter[0]);
    if( stat != SGX_SUCCESS || 0 != memcmp(&inter[0], &internal_node_hash_value_table[0][0], HASH_VALUE_SIZE) )
    {
        goto error;
    }
    memcpy(inter+1, inter, sizeof(hash_tree_internal_node_t));

    // inter_parent = sha256(inter|inter) should match internal_node_hash_value_table[i]
    for(int i = 1; i<23 ; i++)
    {
        stat = sgx_sha256_msg((uint8_t*)&inter[0],
                          2*HASH_VALUE_SIZE,
                          (sgx_sha256_hash_t*)&inter_parent);
        if( stat != SGX_SUCCESS || 0 != memcmp(&inter_parent, &internal_node_hash_value_table[i][0], HASH_VALUE_SIZE) )
        {
            goto error;
        }
        memcpy(inter, &inter_parent, sizeof(hash_tree_internal_node_t));
        memcpy(inter+1, &inter_parent, sizeof(hash_tree_internal_node_t));
    }

    return 0;
error:
    return -1;
}

int sqlite_generate_prebuild_db()
{
    sqlite3* db = NULL;
    int      rc;
    char*    errmsg = NULL;
    int      node_type;
    int      start_id, end_id;
    sqlite3_stmt* stat = NULL;
    char     sql_sentence[512] = {0};
    uint8_t* ptr_buf = NULL;
    uint32_t bufflen;
    uint8_t* ptr_precalc_node_buff = NULL;
    int      layer;

    bufflen = (INIT_MAX_HASH_TREE_LAYER-2)*sizeof(hash_tree_internal_node_t) + 
              sizeof(hash_tree_leaf_node_t);

    ptr_precalc_node_buff = (uint8_t*)malloc(bufflen);
    if(NULL == ptr_precalc_node_buff)
    {
        return -1;
    }

    memset(ptr_precalc_node_buff, 0, bufflen);

    hash_tree_internal_node_t* internal_node = (hash_tree_internal_node_t*)ptr_precalc_node_buff;
    for(int index=INIT_MAX_HASH_TREE_LAYER-3; index>=0; index--)
    {
        if(memcpy_s(internal_node->hash, 
                    sizeof(internal_node->hash), 
                    &(internal_node_hash_value_table[index][0]), 
                    HASH_VALUE_SIZE))
        {
            free(ptr_precalc_node_buff);
            return -1;
        }
        internal_node++;
    }

    if(gDb)
    {
        db = gDb;
    }
    else
    {
        rc = sqlite3_open(SQLITE_DB_FILE_NAME, &gDb);
        if( SQLITE_OK != rc )
        {
            free(ptr_precalc_node_buff);
            return rc;
        }
		db = gDb;
    }

    rc = sqlite3_exec( db, 
                       "create table VMC_QUOTA_TABLE( ID integer primary key AUTOINCREMENT, MRSIGNER char(64), COUNTER integer)", 
                       NULL,
                       NULL,
                       &errmsg );
    EXIT_IFNOT_SQLITE_OK(rc)

    rc = sqlite3_exec( db, 
                       "create table HASH_TREE_NODE_TABLE( ID integer primary key, node_content blob, USED integer, REFID integer NULL REFERENCES VMC_QUOTA_TABLE(ID))", 
                       NULL,
                       NULL,
                       &errmsg );
    EXIT_IFNOT_SQLITE_OK(rc)

    rc = sqlite3_exec( db, 
                       "create table BACKUP_TABLE( ID integer primary key, node_content blob, USED integer, REFID integer)", 
                       NULL,
                       NULL,
                       &errmsg );
    EXIT_IFNOT_SQLITE_OK(rc)

    // all nodes in the same layer have the same precalculated value
    // the merkel hash tree has 12 layers including root layer

    rc = sqlite3_exec(db, "BEGIN TRANSACTION;", NULL, NULL, NULL);
    EXIT_IFNOT_SQLITE_OK(rc)

    sprintf(sql_sentence, "insert into HASH_TREE_NODE_TABLE( ID, node_content, USED, REFID) values( ?, ?, 0, NULL)");

    rc = sqlite3_prepare_v2(db, 
                         sql_sentence, 
                         -1, 
                         &stat, 
                         0);
    EXIT_IFNOT_SQLITE_OK(rc)

    layer = INIT_MAX_HASH_TREE_LAYER - 1;
    do{
        if(INIT_MAX_HASH_TREE_LAYER - 1 == layer)
        {
            node_type = HASH_TREE_NODE_TYPE_LEAF;
        }
        else
        {
            node_type = HASH_TREE_NODE_TYPE_INTERNAL;
        }

        start_id = (int)pow((double)2,layer);
        end_id = (int)pow((double)2,layer+1) - 1;

        for(int id = start_id; id <= end_id; id++)
        {
            rc =sqlite3_bind_int(stat, 1, id);
            EXIT_IFNOT_SQLITE_OK(rc)

            switch(node_type)
            {
                case HASH_TREE_NODE_TYPE_INTERNAL:
                    ptr_buf = ptr_precalc_node_buff;
                    rc = sqlite3_bind_blob(stat,
                                           2,
                                           (hash_tree_internal_node_t*)ptr_buf + layer - 1,
                                           sizeof(hash_tree_internal_node_t),
                                           NULL
                                           );
                    break;
                case HASH_TREE_NODE_TYPE_LEAF:
                    rc = sqlite3_bind_blob(stat,
                                           2,
                                           ptr_precalc_node_buff + (INIT_MAX_HASH_TREE_LAYER-2)*sizeof(hash_tree_internal_node_t),
                                           sizeof(hash_tree_leaf_node_t),
                                           NULL
                                           );

                    break;
                default:
                    goto error;
            }
            EXIT_IFNOT_SQLITE_OK(rc)

            rc = sqlite3_step(stat);
            if(rc != SQLITE_DONE)
            {
                goto error;
            }

            rc = sqlite3_clear_bindings(stat);
            EXIT_IFNOT_SQLITE_OK(rc)

            rc = sqlite3_reset(stat);
            EXIT_IFNOT_SQLITE_OK(rc)
        }
        layer--;
        if(layer&0x1)
            sqlite3_sleep(1);
    }while(layer>0);

    rc = sqlite3_exec(db, "END TRANSACTION;", NULL, NULL, NULL);
    EXIT_IFNOT_SQLITE_OK(rc)

    rc = sqlite3_finalize(stat);
    EXIT_IFNOT_SQLITE_OK(rc)

    stat = NULL;

    sqlite3_close_v2(db);
    gDb = NULL;

    free(ptr_precalc_node_buff);
    
    return 0;
error:
    free(ptr_precalc_node_buff);
    if(db)
    {
        sqlite3_finalize(stat);
        sqlite3_exec(db, "ROLLBACK TRANSACTION;", NULL, NULL, NULL);
        sqlite3_close_v2(db);
        gDb = NULL;
    }
    return -1;
}

int main()
{
    if(verify_precalculated_hash_table())
    {
        printf("failed to verify precalculated hash table.\n");
        return -1;
    }

    int ret = sqlite_generate_prebuild_db();
    if(0 != ret)
        printf("failed to generate VMC DB.\n");
    return ret;
}
