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


#include "string.h"

#include "monotonic_counter_database_types.h"
#include "monotonic_counter_database_sqlite_access_hw_mc.h"
#include "monotonic_counter_database_sqlite_cache.h"
#include "monotonic_counter_database_sqlite_bin_hash_tree_utility.h"

#include <stdlib.h>
#include <assert.h>

//#define DEBUG_WITHOUT_CACHE

// will cache a maximum number of 256 leaves
#define MAX_LEAF_CACHE_NUM 256     

// define an empty hash tree node pointer array in PSE's memory to cache all hash nodes. 
static tree_node_cache_t* g_hash_tree_nodes[INIT_MAX_LEAF_NODE_ID] = {0};
// cache IDs of leaves, to limit the size of the whole cache
static leaf_cache_t g_leaf_cache; 

/*******************************************************************
**  Function name: flush_hash_tree_cache
**  Descrption: free memory allocated by cache tree.
**  
*******************************************************************/
void flush_hash_tree_cache()
{
#ifndef DEBUG_WITHOUT_CACHE
    for(uint32_t index=0; index < INIT_MAX_LEAF_NODE_ID; index++)
    {
        SAFE_FREE(g_hash_tree_nodes[index]);
    }

    // clear cached leaves list
    while (g_leaf_cache.list)
    {
        leaf_cache_node_t* node = g_leaf_cache.list;
        g_leaf_cache.list = g_leaf_cache.list->next;
        SAFE_FREE(node);
    }
    g_leaf_cache.size = 0;
#endif
}

/*******************************************************************
**  Function name: cache_helper
**  Descrption: helper function to insert node to cache or retrieve node from cache
**  Returns: OP_SUCCESS when success
**              or OP_ERROR_CACHE_MISS when CACHE_OP_READ failed
**              or OP_ERROR_MALLOC for  when CACHE_OP_UPDATE failed
*******************************************************************/
static pse_op_error_t cache_helper(const cache_op_t cache_op,      //  [IN] Read/Update cache
                                   tree_node_cache_t** tree_node,  // [IN,OUT] pointer to a single tree_node_cache_t instance
                                   const uint32_t tree_node_sz,    //  [IN] size of tree_node->node
                                   uint8_t* data,                  //  [IN, OUT] pointer to bufer that stores the value of the  (tree_node->node)[]
                                   const uint32_t data_sz)         //  [IN] size of data[]
{
    assert(tree_node != NULL && data != NULL);

    if(NULL == *tree_node) // this node has not been allocated.
    {
        if(cache_op == CACHE_OP_UPDATE) // for insert a new node to cache
        {
            *tree_node = (tree_node_cache_t*)malloc(tree_node_sz); // allocate memory
            if(NULL == *tree_node)
            {
                return OP_ERROR_MALLOC;
            }
            (*tree_node)->ref_counter = 0; // initialize the ref_counter with 0
        }
        else // the node is not cached
        {
            return OP_ERROR_CACHE_MISS;
        }
    }

    if(cache_op == CACHE_OP_UPDATE) // update cache
    {
        memcpy((*tree_node)->node, data, data_sz);
    }
    else  // retrieve data from cahce
    {
        memcpy(data, (*tree_node)->node, data_sz);
    }
    
    return OP_SUCCESS;
}

static inline void update_node_ref_counter(const uint32_t node_index, const int value_to_add)
{
    // The node should not be NULL
    assert(g_hash_tree_nodes[node_index-1] != NULL);

    // Increase or decrease ref counter
    g_hash_tree_nodes[node_index-1]->ref_counter += value_to_add;

    // The ref counter cannot < 0 
    assert(g_hash_tree_nodes[node_index-1]->ref_counter <= 8192);

    // free memory only when ref_counter is 0.
    if(g_hash_tree_nodes[node_index-1]->ref_counter == 0)
    {
        SAFE_FREE(g_hash_tree_nodes[node_index-1]);
    }
}

/*******************************************************************
**  Function name: update_related_nodes_ref_count
**  Descrption: Increase or decrease the reference counter of all related nodes.
**              If ref counter reaches zero, release the cached node.
**  
*******************************************************************/
static void update_related_nodes_ref_counter(const uint32_t leaf_id, const int value_to_add)
{
#ifdef DEBUG_WITHOUT_CACHE
    return;
#else
    assert(value_to_add == 1 || value_to_add == -1);

    uint32_t ancestor_index = leaf_id;
    uint32_t i = 0;

    // update leaf's ref counter
    update_node_ref_counter(leaf_id, value_to_add);
    // update brother's ref counter
    update_node_ref_counter(IS_LEFT_CHILD(leaf_id) ? (leaf_id+1) : (leaf_id-1), value_to_add);

    // update ancestors and brothers' ref counter
    ancestor_index = ( ancestor_index - ancestor_index%2 ) >> 1 ;
    while (ancestor_index != 1)
    {
        update_node_ref_counter(ancestor_index, value_to_add);
        update_node_ref_counter(IS_LEFT_CHILD(ancestor_index) ? (ancestor_index+1) : (ancestor_index-1),
            value_to_add);
        ancestor_index = ( ancestor_index - ancestor_index%2 ) >> 1 ;
        i++;
    }
#endif
}

static void find_leaf_node_in_cache(const uint32_t leaf_id,
                                    leaf_cache_node_t** cached_leaf_node_prev,
                                    leaf_cache_node_t** cached_leaf_node)
{
    // look for the leaf node in the cached list
    *cached_leaf_node = g_leaf_cache.list;
    while (*cached_leaf_node != NULL) {
        if ((*cached_leaf_node)->leaf_id == leaf_id)
            break;
        *cached_leaf_node_prev = *cached_leaf_node;
        *cached_leaf_node = (*cached_leaf_node)->next;
    }
}

/*******************************************************************
**  Function name: update_cached_leaf_list
**  Descrption: update cached_leaf_list which maintains a list of all
**              leaves that are cached in memory. If there is no empty
**              slot to cache new leaf node, will remove the last one
**              in the cache list and put the new leaf at the head (FIFO)
*******************************************************************/
static pse_op_error_t update_cached_leaf_list(const uint32_t leaf_id)
{
    leaf_cache_node_t* cached_leaf_node_prev = NULL;
    leaf_cache_node_t* cached_leaf_node = NULL;

    // look for the leaf node in the cached list
    find_leaf_node_in_cache(leaf_id, &cached_leaf_node_prev, &cached_leaf_node);

    // leaf node not in the cache
    if (cached_leaf_node == NULL) 
    {
        // malloc cache node first
        leaf_cache_node_t* temp = (leaf_cache_node_t*)malloc(sizeof(leaf_cache_node_t));
        if (temp == NULL)
        {
            return OP_ERROR_MALLOC;
        }

        // increase ref counter for all related nodes of the leaf node
        update_related_nodes_ref_counter(leaf_id, 1);

        if (g_leaf_cache.size == MAX_LEAF_CACHE_NUM) // g_leaf_cache.size reaches the limitation. 
        {
            // cache is full, remove the tail node in the list
            leaf_cache_node_t* tail_prev = NULL;
            // check the pointer g_leaf_cache.list for defense in depth.
            if(NULL == g_leaf_cache.list)
            {
                // head of the list is NULL
                SAFE_FREE(temp);
                return OP_ERROR_INTERNAL;
            }
            leaf_cache_node_t* tail = g_leaf_cache.list; // head of the list
            while(tail->next != NULL)
            {
                tail_prev = tail;
                tail = tail->next;
            }

            // update reference counter for related nodes
            update_related_nodes_ref_counter(tail->leaf_id, -1);
            SAFE_FREE(tail);
            if(tail_prev != NULL)
            {
                tail_prev->next = NULL;
            }
            // update cached list length
            g_leaf_cache.size--;
        }

        // insert the new node at head

        temp->leaf_id = leaf_id;
        temp->next = g_leaf_cache.list;
        g_leaf_cache.list = temp;

        // update cache size
        g_leaf_cache.size++;
    }
    else
    {
        if (cached_leaf_node_prev == NULL) 
        {
            // already at head
            return OP_SUCCESS;
        }
        else 
        {
            // move the leaf to head
            cached_leaf_node_prev->next = cached_leaf_node->next;
            cached_leaf_node->next = g_leaf_cache.list;
            g_leaf_cache.list = cached_leaf_node;
        }
    }

    return OP_SUCCESS;
}

/*******************************************************************
**  Function name: remove_from_cached_leaf_list
**  Descrption: Remove specified leaf node from cached leaves list
*******************************************************************/
static void remove_from_cached_leaf_list(const uint32_t leaf_id)
{
#ifdef DEBUG_WITHOUT_CACHE
    return;
#else
    leaf_cache_node_t* cached_leaf_node_prev = NULL;
    leaf_cache_node_t* cached_leaf_node = NULL;

    // look for the leaf node in the cached list
    find_leaf_node_in_cache(leaf_id, &cached_leaf_node_prev, &cached_leaf_node);

    if (cached_leaf_node != NULL)
    {
        if (cached_leaf_node_prev != NULL)
        {
            cached_leaf_node_prev->next = cached_leaf_node->next;
        }
        else
        {
            // node is at the head
            g_leaf_cache.list = cached_leaf_node->next;
        }
        SAFE_FREE(cached_leaf_node);
        g_leaf_cache.size--;

        // update reference counter
        update_related_nodes_ref_counter(leaf_id, -1);
    }
#endif
}

/*******************************************************************
**  Function name: access_hash_tree_cache
**  Descrption: 
**  
*******************************************************************/
pse_op_error_t access_hash_tree_cache(const rpdb_op_t rpdb_op,   // vmc operation type
                                      const cache_op_t cache_op,         // read/update cache
                                      pse_vmc_hash_tree_cache_t *cache,  // buffer that stores tree nodes required by a VMC operation
                                      const uint8_t *root_hash)          // current root hash in rpdata read from PSDA
{
    assert(cache); 
    if(cache_op == CACHE_OP_READ)
    {
        assert(root_hash);
    }

#ifdef DEBUG_WITHOUT_CACHE
    return OP_ERROR_INTERNAL;
#else
    pse_op_error_t ret = OP_SUCCESS;

    // store ROOT node into cache if CACHE_OP_UPDATE == cache_op
    // or, retrieve ROOT node from cache if CACHE_OP_READ == cache_op
    ret = cache_helper(cache_op, 
                &(g_hash_tree_nodes[0]), 
                TREE_NODE_CACHE_SIZE + ROOT_NODE_SIZE, 
                (uint8_t*)&(cache->root), 
                ROOT_NODE_SIZE);
    if(OP_SUCCESS != ret)
    {
        goto end;
    }

    // the cached root hash must match the root hash retrieved from PSDA's RPDATA
    if(cache_op == CACHE_OP_READ && 0 != memcmp(cache->root.hash, root_hash, ROOT_HASH_SIZE))
    {
        // the cache is out of date. might be attacked.
        // drop the existed cache
        flush_hash_tree_cache();
        return OP_ERROR_CACHE_MISS;
    }

    // store internal node into cache if CACHE_OP_UPDATE == cache_op
    // or, retrieve internal node from cache if CACHE_OP_READ == cache_op
    for(uint32_t index = 0; index < INIT_INTERNAL_NODE_NR; index++)
    {
        // ancestor nodes
        ret = cache_helper(cache_op, 
                    &(g_hash_tree_nodes[cache->ancestors[index].node_id - 1]), 
                    TREE_NODE_CACHE_SIZE + INTERNAL_NODE_SIZE, 
                    (uint8_t*)&(cache->ancestors[index].internal), 
                    INTERNAL_NODE_SIZE);
        if(OP_SUCCESS != ret)
        {
            goto end;
        }
        // brothers of ancestors
        ret = cache_helper(cache_op, 
                    &(g_hash_tree_nodes[cache->brother_of_ancestors[index].node_id - 1]), 
                    TREE_NODE_CACHE_SIZE + INTERNAL_NODE_SIZE, 
                    (uint8_t*)&(cache->brother_of_ancestors[index].internal), 
                    INTERNAL_NODE_SIZE);
        if(OP_SUCCESS != ret)
        {
            goto end;
        }
    }

    // store leaf node into cache if CACHE_OP_UPDATE == cache_op
    // or, retrieve leaf node from cache if CACHE_OP_READ == cache_op
    ret = cache_helper(cache_op, 
                &(g_hash_tree_nodes[cache->self.node_id - 1]), 
                TREE_NODE_CACHE_SIZE + LEAF_NODE_SIZE, 
                (uint8_t*)&cache->self.leaf, 
                LEAF_NODE_SIZE);
    if(OP_SUCCESS != ret)
    {
        goto end;
    }
    ret = cache_helper(cache_op, 
                &(g_hash_tree_nodes[cache->brother.node_id - 1]), 
                TREE_NODE_CACHE_SIZE + LEAF_NODE_SIZE, 
                (uint8_t*)&cache->brother.leaf, 
                LEAF_NODE_SIZE);
    if(OP_SUCCESS != ret)
    {
        goto end;
    }

    if (rpdb_op != RPDB_OP_DELETE)
    {
        ret = update_cached_leaf_list(cache->self.node_id);
    }
    else if (cache_op == CACHE_OP_UPDATE)
    {
        // RPDB_OP_DELETE && CACHE_OP_UPDATE
        remove_from_cached_leaf_list(cache->self.node_id);
    }

end:
    if (OP_SUCCESS != ret)
    {
      switch(ret)
        {
            case OP_ERROR_MALLOC:/*only possible for CACHE_OP_UPDATE*/
            {
                flush_hash_tree_cache();
                break;
            }
            case OP_ERROR_CACHE_MISS:/*only possible for CAHE_OP_READ*/
            {
                break;
            }
            default:
                assert(0); /* should not happen*/     
        }
    }

    return ret;
#endif
}
