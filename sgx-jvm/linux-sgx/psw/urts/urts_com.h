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

#ifndef _URTS_COM_H_
#define _URTS_COM_H_


#include "arch.h"
#include "sgx_error.h"
#include "se_error_internal.h"
#include "se_trace.h"
#include "file.h"
#include "sgx_eid.h"
#include "se_map.h"
#include "launch_checker.h"
#include "debugger_support.h"
#include "loader.h"
#include "binparser.h"
#include "cpuid.h"
#include "se_macro.h"
#include "prd_css_util.h"
#include "se_detect.h"
#include "rts.h"
#include "enclave_creator_hw.h"
#include <sys/mman.h>
#ifndef PARSER
#include "elfparser.h"
#define PARSER ElfParser
#endif
#include "xsave.h"

#include "ittnotify.h"
#include "ittnotify_config.h"
#include "ittnotify_types.h"

extern "C" int __itt_init_ittlib(const char*, __itt_group_id);
extern "C" __itt_global* __itt_get_ittapi_global();


#define HSW_C0  0x306c3
#define GPR_A0  0x406e0
#define GPR_B0  0x406e1
#define GPR_P0  0x506e0

#ifndef SE_SIM
static int validate_platform()
{
    int cpu_info[4] = {0, 0, 0, 0};

    __cpuid(cpu_info, 1);

    // The compatibility between SDK and PSW is checked by the metadata version.
    // Below check the compatibility between the platform and uRTS only.

    
    // It is HSW users' responsibility to make the uRTS version to consistent with the HSW patch.
    if(cpu_info[0] == HSW_C0)
    {
        return SGX_SUCCESS;
    }

    // GPR region
    else if(cpu_info[0] == GPR_A0 || cpu_info[0] == GPR_B0 || cpu_info[0] == GPR_P0)
    {
        SE_TRACE(SE_TRACE_ERROR, "ERROR: The enclave cannot be launched on current platform.\n");
        return SGX_ERROR_INVALID_VERSION;
    }
    
    return SGX_SUCCESS;
}
#endif

static sgx_status_t get_metadata(BinParser *parser, const int debug, metadata_t **metadata, sgx_misc_attribute_t *sgx_misc_attr)
{
    assert(parser != NULL && metadata != NULL && sgx_misc_attr != NULL);
    uint64_t meta_rva = parser->get_metadata_offset();
    const uint8_t *base_addr = parser->get_start_addr();

    uint64_t supported_metadata_version_list[] = {
        META_DATA_MAKE_VERSION(MAJOR_VERSION,MINOR_VERSION ),
        META_DATA_MAKE_VERSION(SGX_1_9_MAJOR_VERSION,SGX_1_9_MINOR_VERSION ),
        META_DATA_MAKE_VERSION(SGX_1_5_MAJOR_VERSION,SGX_1_5_MINOR_VERSION ),
        0
    };

    uint64_t *pmetadata_version = &supported_metadata_version_list[0];

#ifndef SE_SIM
    EnclaveCreatorHW *enclave_creator = static_cast<EnclaveCreatorHW *>(get_enclave_creator());
    if (!(enclave_creator->is_cpu_edmm()) || !(enclave_creator->is_driver_compatible()))
    {
        // cannot support EDMM, adjust the possibly highest metadata version supported
        pmetadata_version = &supported_metadata_version_list[1];
    }
#else
    //for simulation, use the metadata of 1.9
    pmetadata_version = &supported_metadata_version_list[1];
#endif

    //scan multiple metadata list in sgx_metadata section
    for (; *pmetadata_version != 0; pmetadata_version++)
    {
        meta_rva = parser->get_metadata_offset();
        //scan multiple metadata list in sgx_metadata section
        do {
            *metadata = GET_PTR(metadata_t, base_addr, meta_rva);
            if(metadata == NULL)
            {
                return SGX_ERROR_INVALID_METADATA;
            }
            if((*metadata)->magic_num != METADATA_MAGIC)
                break;
            //check metadata version
            if(*pmetadata_version == (*metadata)->version)
                goto find_metadata;  //find metadata

            if(0 == (*metadata)->size)
            {
                SE_TRACE(SE_TRACE_ERROR, "ERROR: metadata's size can't be zero.\n");
                return SGX_ERROR_INVALID_METADATA;
            }
            meta_rva += (*metadata)->size; /*goto next metadata offset*/
        }while(1);
    }

    if(*pmetadata_version == 0)
        return SGX_ERROR_INVALID_METADATA;

find_metadata:
    return (sgx_status_t)get_enclave_creator()->get_misc_attr(sgx_misc_attr, *metadata, NULL, debug);
}


#define MAX_LEN 256
static bool is_SGX_DBG_OPTIN_variable_set()
{
    const char sgx_dbg_optin[] = "SGX_DBG_OPTIN";
    const char sgx_dbg_optin_expect_val[] = "1";
    char *sgx_dbg_optin_val = getenv(sgx_dbg_optin);

    if(sgx_dbg_optin_val == NULL)
    {
        return false;
    }
    size_t expect_len = strnlen_s(sgx_dbg_optin_expect_val, MAX_LEN);
    size_t len = strnlen_s(sgx_dbg_optin_val, MAX_LEN);
    if(len != expect_len || strncmp(sgx_dbg_optin_expect_val, sgx_dbg_optin_val, expect_len) != 0)
    {
        return false;
    }
    return true;
}


static int __create_enclave(BinParser &parser, uint8_t* base_addr, const metadata_t *metadata, se_file_t& file, const bool debug, SGXLaunchToken *lc, le_prd_css_file_t *prd_css_file, sgx_enclave_id_t *enclave_id, sgx_misc_attribute_t *misc_attr)
{
    // The "parser" will be registered into "loader" and "loader" will be registered into "enclave".
    // After enclave is created, "parser" and "loader" are not needed any more.
    debug_enclave_info_t *debug_info = NULL;
    int ret = SGX_SUCCESS;
    CLoader loader(base_addr, parser);

    ret = loader.load_enclave_ex(lc, debug, metadata, prd_css_file, misc_attr);
    if (ret != SGX_SUCCESS)
    {
        return ret;
    }

    CEnclave* enclave = new CEnclave(loader);
    uint32_t enclave_version = SDK_VERSION_1_5;
    // metadata->version has already been validated during load_encalve_ex()
    if (metadata->version == META_DATA_MAKE_VERSION(MAJOR_VERSION,MINOR_VERSION))
        enclave_version = SDK_VERSION_2_0;
    else if (metadata->version == META_DATA_MAKE_VERSION(SGX_1_5_MAJOR_VERSION,SGX_1_5_MINOR_VERSION))
        enclave_version = SDK_VERSION_1_5;

    // initialize the enclave object
    ret = enclave->initialize(file,
                              loader.get_enclave_id(),
                              const_cast<void*>(loader.get_start_addr()),
                              metadata->enclave_size,
                              metadata->tcs_policy,
                              enclave_version,
                              metadata->tcs_min_pool);

    if (ret != SGX_SUCCESS)
    {
        loader.destroy_enclave();
        delete enclave; // The `enclave' object owns the `loader' object.
        return ret;
    }


    // It is accurate to get debug flag from secs
    enclave->set_dbg_flag(!!(loader.get_secs().attributes.flags & SGX_FLAGS_DEBUG));

    debug_info = const_cast<debug_enclave_info_t *>(enclave->get_debug_info());

    enclave->set_extra_debug_info(const_cast<secs_t &>(loader.get_secs()));

    //add enclave to enclave pool before init_enclave because in simualtion
    //mode init_enclave will rely on CEnclavePool to get Enclave instance.
    if (FALSE == CEnclavePool::instance()->add_enclave(enclave))
    {
        loader.destroy_enclave();
        delete enclave;
        return SGX_ERROR_UNEXPECTED;
    }

    std::vector<std::pair<tcs_t *, bool>> tcs_list = loader.get_tcs_list();
    for (unsigned idx = 0; idx < tcs_list.size(); ++idx)
    {
        enclave->add_thread(tcs_list[idx].first, tcs_list[idx].second);
        SE_TRACE(SE_TRACE_DEBUG, "add tcs %p\n", tcs_list[idx].first);
    }
    
    if(debug)
        debug_info->enclave_type |= ET_DEBUG;
    if (!(get_enclave_creator()->use_se_hw()))
        debug_info->enclave_type |= ET_SIM;

    if(debug || !(get_enclave_creator()->use_se_hw()))
    {
        SE_TRACE(SE_TRACE_DEBUG, "Debug enclave. Checking if VTune is profiling or SGX_DBG_OPTIN is set\n");

        __itt_init_ittlib(NULL, __itt_group_none);
        bool isVTuneProfiling;
        if(__itt_get_ittapi_global()->api_initialized && __itt_get_ittapi_global()->lib)
            isVTuneProfiling = true;
        else
            isVTuneProfiling = false;

        bool is_SGX_DBG_OPTIN_set = false;
        is_SGX_DBG_OPTIN_set = is_SGX_DBG_OPTIN_variable_set();
        if (isVTuneProfiling || is_SGX_DBG_OPTIN_set)
        {
            SE_TRACE(SE_TRACE_DEBUG, "VTune is profiling or SGX_DBG_OPTIN is set\n");

            bool thread_updated;
            thread_updated = enclave->update_debug_flag(1);

            if(thread_updated == false)
            {
                SE_TRACE(SE_TRACE_DEBUG, "Failed to update debug OPTIN bit\n");
            }
            else
            {
                SE_TRACE(SE_TRACE_DEBUG, "Updated debug OPTIN bit\n");
            }

            if (isVTuneProfiling)
            {
                uint64_t enclave_start_addr;
                uint64_t enclave_end_addr;
                const char* enclave_path;
                enclave_start_addr = (uint64_t) loader.get_start_addr();
                enclave_end_addr = enclave_start_addr + (uint64_t) metadata->enclave_size;

                SE_TRACE(SE_TRACE_DEBUG, "Invoking VTune's module mapping API __itt_module_load \n");
                SE_TRACE(SE_TRACE_DEBUG, "Enclave_start_addr==0x%llx\n", enclave_start_addr);
                SE_TRACE(SE_TRACE_DEBUG, "Enclave_end_addr==0x%llx\n", enclave_end_addr);

                enclave_path = (const char*)file.name;
                SE_TRACE(SE_TRACE_DEBUG, "Enclave_path==%s\n",  enclave_path);
                __itt_module_load((void*)enclave_start_addr, (void*) enclave_end_addr, enclave_path);
            }
        }
        else
        {
            SE_TRACE(SE_TRACE_DEBUG, "VTune is not profiling and SGX_DBG_OPTIN is not set. TCS Debug OPTIN bit not set and API to do module mapping not invoked\n");
        }
    }

    //send debug event to debugger when enclave is debug mode or release mode
    //set struct version
    debug_info->struct_version = enclave->get_debug_info()->struct_version;
    //generate load debug event after EINIT
    generate_enclave_debug_event(URTS_EXCEPTION_POSTINITENCLAVE, debug_info);

    if (get_enclave_creator()->is_EDMM_supported(loader.get_enclave_id()))
    {
        layout_t *layout_start = GET_PTR(layout_t, metadata, metadata->dirs[DIR_LAYOUT].offset);
        layout_t *layout_end = GET_PTR(layout_t, metadata, metadata->dirs[DIR_LAYOUT].offset + metadata->dirs[DIR_LAYOUT].size);
        if (SGX_SUCCESS != (ret = loader.post_init_action(layout_start, layout_end, 0)))
        {
            SE_TRACE(SE_TRACE_ERROR, "trim range error.\n");
            sgx_status_t status = SGX_SUCCESS;
            CEnclavePool::instance()->remove_enclave(loader.get_enclave_id(), status);
            goto fail;
        }
    }

    //call trts to do some intialization
    if(SGX_SUCCESS != (ret = get_enclave_creator()->initialize(loader.get_enclave_id())))
    {
        sgx_status_t status = SGX_SUCCESS;
        CEnclavePool::instance()->remove_enclave(loader.get_enclave_id(), status);
        goto fail;
    }

    if (get_enclave_creator()->is_EDMM_supported(loader.get_enclave_id()))
    {
        
        layout_t *layout_start = GET_PTR(layout_t, metadata, metadata->dirs[DIR_LAYOUT].offset);
        layout_t *layout_end = GET_PTR(layout_t, metadata, metadata->dirs[DIR_LAYOUT].offset + metadata->dirs[DIR_LAYOUT].size);
        if (SGX_SUCCESS != (ret = loader.post_init_action_commit(layout_start, layout_end, 0)))
        {
            SE_TRACE(SE_TRACE_ERROR, "trim page commit error.\n");
            sgx_status_t status = SGX_SUCCESS;
            CEnclavePool::instance()->remove_enclave(loader.get_enclave_id(), status);
            goto fail;
        }
    }

    //fill tcs mini pool
    if (get_enclave_creator()->is_EDMM_supported(loader.get_enclave_id()))
    {
        ret = enclave->fill_tcs_mini_pool_fn();
        if (ret != SGX_SUCCESS)
        {
            SE_TRACE(SE_TRACE_ERROR, "fill_tcs_mini_pool error.\n");
            sgx_status_t status = SGX_SUCCESS;
            CEnclavePool::instance()->remove_enclave(loader.get_enclave_id(), status);
            goto fail;
        }
    }
        
    if(SGX_SUCCESS != (ret = loader.set_memory_protection()))
    {
        sgx_status_t status = SGX_SUCCESS;
        CEnclavePool::instance()->remove_enclave(loader.get_enclave_id(), status);
        goto fail;
    }

    *enclave_id = loader.get_enclave_id();
    return SGX_SUCCESS;

fail:
    loader.destroy_enclave();
    delete enclave;
    return ret;
}


sgx_status_t _create_enclave(const bool debug, se_file_handle_t pfile, se_file_t& file, le_prd_css_file_t *prd_css_file, sgx_launch_token_t *launch, int *launch_updated, sgx_enclave_id_t *enclave_id, sgx_misc_attribute_t *misc_attr)
{
    unsigned int ret = SGX_SUCCESS;
    sgx_status_t lt_result = SGX_SUCCESS;
    uint32_t file_size = 0;
    map_handle_t* mh = NULL;
    sgx_misc_attribute_t sgx_misc_attr;
    metadata_t *metadata = NULL;
    SGXLaunchToken *lc = NULL;
    memset(&sgx_misc_attr, 0, sizeof(sgx_misc_attribute_t));

    if(NULL == launch || NULL == launch_updated || NULL == enclave_id)
        return SGX_ERROR_INVALID_PARAMETER;
#ifndef SE_SIM
    ret = validate_platform();
    if(ret != SGX_SUCCESS)
        return (sgx_status_t)ret;
#endif

    mh = map_file(pfile, &file_size);
    if (!mh)
        return SGX_ERROR_OUT_OF_MEMORY;

    PARSER parser(const_cast<uint8_t *>(mh->base_addr), (uint64_t)(file_size));
    if(SGX_SUCCESS != (ret = parser.run_parser()))
    {
        goto clean_return;
    }
    //Make sure HW uRTS won't load simulation enclave and vice verse.
    if(get_enclave_creator()->use_se_hw() != (!parser.get_symbol_rva("g_global_data_sim")))
    {
        SE_TRACE_WARNING("HW and Simulation mode incompatibility detected. The enclave is linked with the incorrect tRTS library.\n");
        ret = SGX_ERROR_MODE_INCOMPATIBLE;
        goto clean_return;
    }

    if(SGX_SUCCESS != (ret = get_metadata(&parser, debug,  &metadata, &sgx_misc_attr)))
    {
        goto clean_return;
    }

    *launch_updated = FALSE;

    lc = new SGXLaunchToken(&metadata->enclave_css, &sgx_misc_attr.secs_attr, launch);
    lt_result = lc->update_launch_token(false);
    if(SGX_SUCCESS != lt_result)
    {
        ret = lt_result;
        goto clean_return;
    }
#ifndef SE_SIM
    // Only LE allows the prd_css_file
    if(is_le(lc, &metadata->enclave_css) == false && prd_css_file != NULL)
    {
        ret = SGX_ERROR_INVALID_PARAMETER;
        goto clean_return;
    }
#endif

    // init xave global variables for xsave/xrstor
    init_xsave_info();


    //Need to set the whole misc_attr instead of just secs_attr.
    do {
        ret = __create_enclave(parser, mh->base_addr, metadata, file, debug, lc, prd_css_file, enclave_id,
                               misc_attr);
        //SGX_ERROR_ENCLAVE_LOST caused by initializing enclave while power transition occurs
    } while(SGX_ERROR_ENCLAVE_LOST == ret);

    if(SGX_ERROR_INVALID_CPUSVN == ret)
        ret = SGX_ERROR_UNEXPECTED;

    if(SE_ERROR_INVALID_LAUNCH_TOKEN == ret)
        ret = SGX_ERROR_INVALID_LAUNCH_TOKEN;
        
    // The launch token is updated, so the SE_INVALID_MEASUREMENT is only caused by signature.
    if(SE_ERROR_INVALID_MEASUREMENT == ret)
        ret = SGX_ERROR_INVALID_SIGNATURE;

    // The launch token is updated, so the SE_ERROR_INVALID_ISVSVNLE means user needs to update the LE image
    if (SE_ERROR_INVALID_ISVSVNLE == ret)
        ret = SGX_ERROR_UPDATE_NEEDED;

    if(SGX_SUCCESS != ret)
        goto clean_return;
    else if(lc->is_launch_updated())
    {
        *launch_updated = TRUE;
        ret = lc->get_launch_token(launch);
    }


clean_return:
    if(mh != NULL)
        unmap_file(mh);
    if(lc != NULL)
        delete lc;
    return (sgx_status_t)ret;
}

extern "C" sgx_status_t sgx_destroy_enclave(const sgx_enclave_id_t enclave_id)
{
    {
        CEnclave* enclave = CEnclavePool::instance()->ref_enclave(enclave_id);

        if(enclave)
        {
            debug_enclave_info_t *debug_info = const_cast<debug_enclave_info_t *>(enclave->get_debug_info());
            generate_enclave_debug_event(URTS_EXCEPTION_PREREMOVEENCLAVE, debug_info);
            enclave->ecall(ECMD_UNINIT_ENCLAVE, NULL, NULL);
            CEnclavePool::instance()->unref_enclave(enclave);
        }
    }

    sgx_status_t status = SGX_SUCCESS;
    CEnclave* enclave = CEnclavePool::instance()->remove_enclave(enclave_id, status);

    if (enclave)
    {
        delete enclave;
    }

    return status;
}
#endif
