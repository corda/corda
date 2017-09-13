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


#ifndef _VMC_SQLITE_HASHTREE_H
#define _VMC_SQLITE_HASHTREE_H

#include "pse_inc.h"
#include "pse_types.h"
#include "stdlib.h"
#include "sgx_utils.h"
#include "ae_ipp.h"
#include "math.h"
#include "monotonic_counter_database_types.h"

#define IS_LEFT_CHILD(id)  ((id)%2==0)

void set_related_nodes_ids(uint32_t leaf_node_index, pse_vmc_hash_tree_cache_t* cache);
pse_op_error_t initialize_sqlite_database_file(bool is_for_empty_db_creation);

pse_op_error_t verify_related_nodes_of_leaf(const pse_vmc_hash_tree_cache_t* cache, 
                                            uint32_t* invalid_node_id);
pse_op_error_t update_related_nodes_of_leaf(pse_vmc_hash_tree_cache_t* cache, 
                                            leafnode_flag_op_type flag_op);
pse_op_error_t get_db_children_of_root(pse_vmc_children_of_root_t* children);

pse_op_error_t get_mc_service_status();

pse_op_error_t rollback_db_file();

#endif
