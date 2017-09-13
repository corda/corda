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


#include "monotonic_counter_database_types.h"
#include "monotonic_counter_database_sqlite_access_hw_mc.h"
#include "psda_service.h"
#include "pse_op_t.h"
#include <assert.h>
#include <limits.h>

static cse_rpdata_t global_cached_rpdata;
static bool is_rpdata_cache_initialized = false;

pse_op_error_t read_rpdata()
{
    uint8_t rpdata[ROOT_HASH_SIZE];
    uint32_t rp_epoch;
    pse_op_error_t rc;

    rc = psda_read_rpdata(rpdata, &rp_epoch);
    if(OP_SUCCESS == rc)
    {
        // Check epoch first
        if(is_rpdata_cache_initialized && global_cached_rpdata.rpdata_epoch != rp_epoch)
        {
            // RPEPOCH has changed
            return OP_ERROR_INTERNAL;
        }

        // update rpdata cache
        global_cached_rpdata.rpdata_epoch = rp_epoch;
        memcpy(global_cached_rpdata.rpdata_roothash, rpdata, sizeof(global_cached_rpdata.rpdata_roothash));
        is_rpdata_cache_initialized  = true;
	}

    return rc;
}

pse_op_error_t update_rpdata(uint8_t* rpdata_new)
{
    assert(rpdata_new!=NULL);
    assert(false != is_rpdata_cache_initialized);

    uint32_t rp_epoch;
    pse_op_error_t rc;
    rc = psda_update_rpdata(global_cached_rpdata.rpdata_roothash, rpdata_new, &rp_epoch);
    if(OP_SUCCESS == rc)
    {
        // Check epoch
        if(is_rpdata_cache_initialized && global_cached_rpdata.rpdata_epoch != rp_epoch)
        {
            // RPEPOCH has changed
            return OP_ERROR_INTERNAL;
        }
		// update rpdata cache
        global_cached_rpdata.rpdata_epoch = rp_epoch;
        memcpy(global_cached_rpdata.rpdata_roothash, rpdata_new, sizeof(global_cached_rpdata.rpdata_roothash));
        is_rpdata_cache_initialized  = true;
    }

    return rc;
}

pse_op_error_t reset_rpdata()
{
    assert(false != is_rpdata_cache_initialized);

    uint8_t rpdata_new[SGX_RPDATA_SIZE];
    uint32_t rp_epoch;
    pse_op_error_t rc;
    rc = psda_reset_rpdata(global_cached_rpdata.rpdata_roothash, rpdata_new, &rp_epoch);
    if(OP_SUCCESS == rc)
    {
        // update rpdata cache
        global_cached_rpdata.rpdata_epoch = rp_epoch;
        memcpy(global_cached_rpdata.rpdata_roothash, rpdata_new, sizeof(global_cached_rpdata.rpdata_roothash));
        is_rpdata_cache_initialized  = true;
    }

    return rc;
}


pse_op_error_t get_cached_roothash(uint8_t *roothash)
{
    assert(roothash);
    assert(is_rpdata_cache_initialized);

    if(!is_rpdata_cache_initialized)  // RPDATA hasn't been cached in PSE-OP
    {
        return OP_ERROR_INTERNAL;
    }

    memcpy(roothash, global_cached_rpdata.rpdata_roothash, ROOT_HASH_SIZE);

    return OP_SUCCESS;
}

pse_op_error_t get_cached_rpepoch(uint32_t *rpepoch)
{
    assert(rpepoch);
    assert(is_rpdata_cache_initialized);

    if(!is_rpdata_cache_initialized)
    {
        return OP_ERROR_INTERNAL;
    }

    *rpepoch = global_cached_rpdata.rpdata_epoch;

    return OP_SUCCESS;
}

// clear cached RPDATA
void clear_cached_rpdata()
{
    is_rpdata_cache_initialized = false;
    memset(&global_cached_rpdata, 0, sizeof(global_cached_rpdata));
}

