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


#ifndef _MANAGE_METADATA_H_
#define _MANAGE_METADATA_H_

#include "metadata.h"
#include "uncopyable.h"
#include "loader.h"
#include "binparser.h"

#define MAX_BUFFER_SIZE 4096

#define STRCMP strcmp
#define STRNCMP strncmp

#define SSA_NUM        2
#define SSA_FRAME_SIZE 1

typedef enum _para_type_t
{
    PRODID = 0,
    ISVSVN,
    RELEASETYPE,
    INTELSIGNED,
    PROVISIONKEY,
    LAUNCHKEY,
    DISABLEDEBUG,
    HW,
    TCSNUM,
    TCSMAXNUM,
    TCSMINPOOL,
    TCSPOLICY,
    STACKMAXSIZE,
    STACKMINSIZE,
    HEAPMAXSIZE,
    HEAPMINSIZE,
    HEAPINITSIZE,
    HEAPEXECUTABLE,
    MISCSELECT,
    MISCMASK
} para_type_t;

typedef struct _xml_parameter_t
{
    const char* name;       //the element name
    uint64_t max_value;
    uint64_t min_value;
    uint64_t value;         //parameter value. Initialized with the default value.
    uint32_t flag;          //Show whether it has been matched
} xml_parameter_t;


bool parse_metadata_file(const char *xmlpath, xml_parameter_t *parameter, int parameter_count);
bool update_metadata(const char *path, const metadata_t *metadata, uint64_t meta_offset);
bool print_metadata(const char *path, const metadata_t *metadata);

class CMetadata: private Uncopyable
{
public:
    CMetadata(metadata_t *metadata, BinParser *parser);
    ~CMetadata();
    bool build_metadata(const xml_parameter_t *parameter);
private:
    bool get_time(uint32_t *date);
    bool modify_metadata(const xml_parameter_t *parameter);
    bool check_xml_parameter(const xml_parameter_t *parameter);
    bool fill_enclave_css(const xml_parameter_t *parameter);
    void *alloc_buffer_from_metadata(uint32_t size);
    bool get_xsave_size(uint64_t xfrm, uint32_t *xsave_size);
    bool build_layout_table();
    bool build_patch_table();
    bool update_layout_entries();
    bool build_layout_entries();
    bool build_patch_entries(vector<patch_entry_t> &patches);

    layout_entry_t *get_entry_by_id(uint16_t id);
    bool build_tcs_template(tcs_t *tcs);
    bool build_gd_template(uint8_t *data, uint32_t *data_size);

    uint64_t calculate_sections_size();
    uint64_t calculate_enclave_size(uint64_t size);
    void* get_rawdata_by_rva(uint64_t rva);

    metadata_t *m_metadata;
    BinParser *m_parser;
    create_param_t m_create_param;
    vector <layout_t> m_layouts;
    uint64_t m_rva;
    uint32_t m_gd_size;
    uint8_t *m_gd_template;
    bool m_heap_executable;
};
#endif
