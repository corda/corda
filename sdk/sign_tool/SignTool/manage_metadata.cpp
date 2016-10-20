/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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

/**
* File:
*     manage_metadata.cpp
* Description:
*     Parse the xml file to get the metadata and generate the output DLL
* with metadata.
*/

#include "metadata.h"
#include "tinyxml.h"
#include "manage_metadata.h"
#include "se_trace.h"
#include "util_st.h"
#include  "section.h"
#include "se_page_attr.h"
#include "elf_util.h"

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <assert.h>
#include <iostream>


#define ALIGN_SIZE 0x1000

static bool traverser_parameter(const char *temp_name, const char *temp_text, xml_parameter_t *parameter, int parameter_count)
{
    assert(temp_name != NULL && parameter != NULL);
    uint64_t temp_value=0;
    if(temp_text == NULL)
    {
        se_trace(SE_TRACE_ERROR, LACK_VALUE_FOR_ELEMENT_ERROR, temp_name);
        return false;
    }
    else
    {
        if(strchr(temp_text, '-'))
        {
            se_trace(SE_TRACE_ERROR, INVALID_VALUE_FOR_ELEMENT_ERROR, temp_name);
            return false;
        }

        errno = 0;
        char* endptr = NULL;
        temp_value = (uint64_t)strtoull(temp_text, &endptr, 0);
        if(*endptr!='\0'||errno!=0)   //Invalid value or valid value but out of the representable range
        {
            se_trace(SE_TRACE_ERROR, INVALID_VALUE_FOR_ELEMENT_ERROR, temp_name);
            return false;
        }
    }

    //Look for the matched one
    int i=0;
    for(;i<parameter_count&&STRCMP(temp_name,parameter[i].name);i++);
    if(i>=parameter_count) //no matched, return false
    {
        se_trace(SE_TRACE_ERROR, UNREC_ELEMENT_ERROR, temp_name);
        return false;
    }
    //found one matched
    if(parameter[i].flag==1)  //repeated definition of XML element, return false
    {
        se_trace(SE_TRACE_ERROR, REPEATED_DEFINE_ERROR, temp_name);
        return false;
    }
    parameter[i].flag = 1;
    if((temp_value<parameter[i].min_value)||
        (temp_value>parameter[i].max_value)) // the value is invalid, return false
    {
        se_trace(SE_TRACE_ERROR, VALUE_OUT_OF_RANGE_ERROR, temp_name);
        return false;
    }
    parameter[i].value = temp_value;
    return true;
}

bool parse_metadata_file(const char *xmlpath, xml_parameter_t *parameter, int parameter_count)
{
    const char* temp_name=NULL;

    assert(parameter != NULL);
    if(xmlpath == NULL) // user didn't define the metadata xml file.
    {
        se_trace(SE_TRACE_NOTICE, "Use default metadata...\n");
        return true;
    }
    //use the metadata file that user gives us. parse xml file
    TiXmlDocument doc(xmlpath);
    bool loadOkay = doc.LoadFile();
    if(!loadOkay)
    {
        if(doc.ErrorId() == TiXmlBase::TIXML_ERROR_OPENING_FILE)
        {
            se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, xmlpath);
        }
        else
        {
            se_trace(SE_TRACE_ERROR, XML_FORMAT_ERROR);
        }
        return false;
    }
    doc.Print();//Write the document to standard out using formatted printing ("pretty print").

    TiXmlNode *pmetadata_node = doc.FirstChild("EnclaveConfiguration");
    if(!pmetadata_node)
    {
        se_trace(SE_TRACE_ERROR, XML_FORMAT_ERROR);
        return false;
    }
    TiXmlNode *sub_node = NULL;
    sub_node = pmetadata_node->FirstChild();
    const char *temp_text = NULL;

    while(sub_node)//parse xml node
    {
        switch(sub_node->Type())
        {
        case TiXmlNode::TINYXML_ELEMENT:
            if(sub_node->ToElement()->FirstAttribute() != NULL)
            {
                se_trace(SE_TRACE_ERROR, XML_FORMAT_ERROR);
                return false;
            }

            temp_name = sub_node->ToElement()->Value();
            temp_text = sub_node->ToElement()->GetText();

            //traverse every node. Compare with the default value.
            if(traverser_parameter(temp_name, temp_text, parameter, parameter_count) == false)
            {
                se_trace(SE_TRACE_ERROR, XML_FORMAT_ERROR);
                return false;
            }
            break;
        case TiXmlNode::TINYXML_DECLARATION:
        case TiXmlNode::TINYXML_COMMENT:
            break;

        default:
            se_trace(SE_TRACE_ERROR, XML_FORMAT_ERROR); 
            return false;
        }
        sub_node=pmetadata_node->IterateChildren(sub_node);
    }

    return true;
}

CMetadata::CMetadata(metadata_t *metadata, BinParser *parser)
    : m_metadata(metadata)
    , m_parser(parser)
{
    memset(m_metadata, 0, sizeof(metadata_t));
    memset(&m_create_param, 0, sizeof(m_create_param));
}
CMetadata::~CMetadata()
{
}
bool CMetadata::build_metadata(const xml_parameter_t *parameter)
{
    if(!modify_metadata(parameter))
    {
        return false;
    }
    // layout table
    if(!build_layout_table())
    {
        return false;
    }
    // patch table
    if(!build_patch_table())
    {
        return false;
    }
    return true;
}
bool CMetadata::modify_metadata(const xml_parameter_t *parameter)
{
    assert(parameter != NULL);
    m_metadata->version = META_DATA_MAKE_VERSION(MAJOR_VERSION,MINOR_VERSION );
    m_metadata->size = offsetof(metadata_t, data);
    m_metadata->tcs_policy = (uint32_t)parameter[TCSPOLICY].value;
    m_metadata->ssa_frame_size = SSA_FRAME_SIZE;
    //stack/heap must be page-align
    if(parameter[STACKMAXSIZE].value % ALIGN_SIZE)
    {
        se_trace(SE_TRACE_ERROR, SET_STACK_SIZE_ERROR);
        return false;
    }
    if(parameter[HEAPMAXSIZE].value % ALIGN_SIZE)
    {
        se_trace(SE_TRACE_ERROR, SET_HEAP_SIZE_ERROR);
        return false;
    }
    // LE setting:  HW != 0, Licensekey = 1
    // Other enclave setting: HW = 0, Licensekey = 0
    if((parameter[HW].value == 0 && parameter[LAUNCHKEY].value != 0) ||
        (parameter[HW].value != 0 && parameter[LAUNCHKEY].value == 0))
    {
        se_trace(SE_TRACE_ERROR, SET_HW_LE_ERROR);
        return false;
    }

    m_metadata->max_save_buffer_size = MAX_SAVE_BUF_SIZE;
    m_metadata->magic_num = METADATA_MAGIC;
    m_metadata->desired_misc_select = 0;
    m_metadata->enclave_css.body.misc_select = (uint32_t)parameter[MISCSELECT].value;
    m_metadata->enclave_css.body.misc_mask =  (uint32_t)parameter[MISCMASK].value;

    m_create_param.heap_max_size = parameter[HEAPMAXSIZE].value;
    m_create_param.ssa_frame_size = SSA_FRAME_SIZE;
    m_create_param.stack_max_size = parameter[STACKMAXSIZE].value;
    m_create_param.tcs_max_num = (uint32_t)parameter[TCSNUM].value;
    m_create_param.tcs_policy = m_metadata->tcs_policy;
    return true;
}

void *CMetadata::alloc_buffer_from_metadata(uint32_t size)
{
    void *addr = GET_PTR(void, m_metadata, m_metadata->size);
    m_metadata->size += size;
    if((m_metadata->size < size) || (m_metadata->size > METADATA_SIZE))
    {
        return NULL;
    }
    return addr;
}

bool CMetadata::build_layout_entries(vector<layout_t> &layouts)
{
    uint32_t size = (uint32_t)(layouts.size() * sizeof(layout_t));
    layout_t *layout_table = (layout_t *) alloc_buffer_from_metadata(size);
    if(layout_table == NULL)
    {
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR); 
        return false;
    }
    m_metadata->dirs[DIR_LAYOUT].offset = (uint32_t)PTR_DIFF(layout_table, m_metadata);
    m_metadata->dirs[DIR_LAYOUT].size = size;

    uint64_t rva = calculate_sections_size();
    for(uint32_t i = 0; i < layouts.size(); i++)
    {
        memcpy_s(layout_table, sizeof(layout_t), &layouts[i], sizeof(layout_t));

        if(!IS_GROUP_ID(layouts[i].entry.id))
        {
            layout_table->entry.rva = rva;
            rva += (uint64_t)layouts[i].entry.page_count << SE_PAGE_SHIFT;
        }
        else
        {
            for (uint32_t j = 0; j < layouts[i].group.entry_count; j++)
            {
                layout_table->group.load_step += (uint64_t)layouts[i-j-1].entry.page_count << SE_PAGE_SHIFT;
            }
            rva += layouts[i].group.load_times * layout_table->group.load_step;
        }
        layout_table++;
    }
    // enclave virtual size
    m_metadata->enclave_size = calculate_enclave_size(rva);
    if(m_metadata->enclave_size == (uint64_t)-1)
    {
        se_trace(SE_TRACE_ERROR, OUT_OF_EPC_ERROR); 
        return false;
    }
    // the last guard page entry to round the enclave size to power of 2
    if(m_metadata->enclave_size - rva > 0)
    {
        layout_table = (layout_t *)alloc_buffer_from_metadata(sizeof(layout_t));
        if(layout_table == NULL)
        {
            se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR); 
            return false;
        }
        layout_table->entry.id = LAYOUT_ID_GUARD;
        layout_table->entry.rva = rva;
        layout_table->entry.page_count = (uint32_t)((m_metadata->enclave_size - rva) >> SE_PAGE_SHIFT);
        m_metadata->dirs[DIR_LAYOUT].size += (uint32_t)sizeof(layout_t);
    }
    return true;
}

bool CMetadata::build_layout_table()
{
    vector <layout_t> layouts;
    layout_t layout;
    memset(&layout, 0, sizeof(layout));

    layout_t guard_page;
    memset(&guard_page, 0, sizeof(guard_page));
    guard_page.entry.id = LAYOUT_ID_GUARD;
    guard_page.entry.page_count = SE_GUARD_PAGE_SIZE >> SE_PAGE_SHIFT;

    // heap
    layout.entry.id = LAYOUT_ID_HEAP;
    layout.entry.page_count = (uint32_t)(m_create_param.heap_max_size >> SE_PAGE_SHIFT);
    layout.entry.attributes = ADD_PAGE_ONLY;
    layout.entry.si_flags = SI_FLAGS_RW;
    layouts.push_back(layout);

    // thread context memory layout
    // guard page | stack | TCS | SSA | guard page | TLS
 
    // guard page
    layouts.push_back(guard_page);

    // stack 
    layout.entry.id = LAYOUT_ID_STACK;
    layout.entry.page_count = (uint32_t)(m_create_param.stack_max_size >> SE_PAGE_SHIFT);
    layout.entry.attributes = ADD_EXTEND_PAGE;
    layout.entry.si_flags = SI_FLAGS_RW;
    layout.entry.content_size = 0xCCCCCCCC;
    layouts.push_back(layout);

    // guard page
    layouts.push_back(guard_page);

    // tcs
    layout.entry.id = LAYOUT_ID_TCS;
    layout.entry.page_count = TCS_SIZE >> SE_PAGE_SHIFT;
    layout.entry.attributes = ADD_EXTEND_PAGE;
    layout.entry.si_flags = SI_FLAGS_TCS;
    tcs_t *tcs_template = (tcs_t *) alloc_buffer_from_metadata(TCS_TEMPLATE_SIZE);
    if(tcs_template == NULL)
    {
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR); 
        return false;
    }
    layout.entry.content_offset = (uint32_t)PTR_DIFF(tcs_template, m_metadata), 
    layout.entry.content_size = TCS_TEMPLATE_SIZE;
    layouts.push_back(layout);
    memset(&layout, 0, sizeof(layout));

    // ssa 
    layout.entry.id = LAYOUT_ID_SSA;
    layout.entry.page_count = SSA_FRAME_SIZE * SSA_NUM;
    layout.entry.attributes = ADD_EXTEND_PAGE;
    layout.entry.si_flags = SI_FLAGS_RW;
    layouts.push_back(layout);

    // guard page
    layouts.push_back(guard_page);

    // td
    layout.entry.id = LAYOUT_ID_TD;
    layout.entry.page_count = 1;
    const Section *section = m_parser->get_tls_section();
    if(section)
    {
        layout.entry.page_count += (uint32_t)(ROUND_TO_PAGE(section->virtual_size()) >> SE_PAGE_SHIFT);
    }
    layout.entry.attributes = ADD_EXTEND_PAGE;
    layout.entry.si_flags = SI_FLAGS_RW;
    layouts.push_back(layout);

    // group for thread context
    if (m_create_param.tcs_max_num > 1)
    {
        memset(&layout, 0, sizeof(layout));
        layout.group.id = LAYOUT_ID_THREAD_GROUP;
        layout.group.entry_count = (uint16_t) (layouts.size() - 1);
        layout.group.load_times = m_create_param.tcs_max_num-1;
        layouts.push_back(layout);
    }
   // build layout table
    if(false == build_layout_entries(layouts))
    {
        return false;
    }

    // tcs template
    if(false == build_tcs_template(tcs_template))
    {
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR); 
        return false;
    }
    return true;
}
bool CMetadata::build_patch_entries(vector<patch_entry_t> &patches)
{
    uint32_t size = (uint32_t)(patches.size() * sizeof(patch_entry_t));
    patch_entry_t *patch_table = (patch_entry_t *) alloc_buffer_from_metadata(size);
    if(patch_table == NULL)
    {
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR); 
        return false;
    }
    m_metadata->dirs[DIR_PATCH].offset = (uint32_t)PTR_DIFF(patch_table, m_metadata);
    m_metadata->dirs[DIR_PATCH].size = size;

    for(uint32_t i = 0; i < patches.size(); i++)
    {
        memcpy_s(patch_table, sizeof(patch_entry_t), &patches[i], sizeof(patch_entry_t));
        patch_table++;
    }
    return true;
}
bool CMetadata::build_patch_table()
{
    const uint8_t *base_addr = (const uint8_t *)m_parser->get_start_addr();
    vector<patch_entry_t> patches;
    patch_entry_t patch;
    memset(&patch, 0, sizeof(patch));

    // td template
    uint8_t buf[200];
    uint32_t size = 200;
    memset(buf, 0, size);
    if(false == build_gd_template(buf, &size))
    {
        return false;
    }
    uint8_t *gd_template = (uint8_t *)alloc_buffer_from_metadata(size);
    if(gd_template == NULL)
    {
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR); 
        return false;
    }
    memcpy_s(gd_template, size, buf, size);

    uint64_t rva = m_parser->get_symbol_rva("g_global_data");
    if(0 == rva)
    {
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR); 
         return false;
    }

    patch.dst = (uint64_t)PTR_DIFF(get_rawdata_by_rva(rva), base_addr);
    patch.src = (uint32_t)PTR_DIFF(gd_template, m_metadata);
    patch.size = size;
    patches.push_back(patch);

    // patch the image header
    uint64_t *zero = (uint64_t *)alloc_buffer_from_metadata(sizeof(*zero));
    if(zero == NULL)
    {
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR); 
        return false;
    }
    *zero = 0;
    bin_fmt_t bf = m_parser->get_bin_format();
    if(bf == BF_ELF32)
    {
        Elf32_Ehdr *elf_hdr = (Elf32_Ehdr *)base_addr;
        patch.dst = (uint64_t)PTR_DIFF(&elf_hdr->e_shnum, base_addr);
        patch.src = (uint32_t)PTR_DIFF(zero, m_metadata);
        patch.size = (uint32_t)sizeof(elf_hdr->e_shnum);
        patches.push_back(patch);

        patch.dst = (uint64_t)PTR_DIFF(&elf_hdr->e_shoff, base_addr);
        patch.src = (uint32_t)PTR_DIFF(zero, m_metadata);
        patch.size = (uint32_t)sizeof(elf_hdr->e_shoff);
        patches.push_back(patch);

        patch.dst = (uint64_t)PTR_DIFF(&elf_hdr->e_shstrndx, base_addr);
        patch.src = (uint32_t)PTR_DIFF(zero, m_metadata);
        patch.size = (uint32_t)sizeof(elf_hdr->e_shstrndx);
        patches.push_back(patch);
 
        // Modify GNU_RELRO info to eliminate the impact of enclave measurement.
        Elf32_Phdr *prg_hdr = GET_PTR(Elf32_Phdr, base_addr, elf_hdr->e_phoff);
        for (unsigned idx = 0; idx < elf_hdr->e_phnum; ++idx, ++prg_hdr)
        {
            if(prg_hdr->p_type == PT_GNU_RELRO)
            {
                patch.dst = (uint64_t)PTR_DIFF(prg_hdr, base_addr);
                patch.src = (uint32_t)PTR_DIFF(zero, m_metadata);
                patch.size = (uint32_t)sizeof(Elf32_Phdr);
                patches.push_back(patch);
                break;
            }
        }
    }
    else if(bf == BF_ELF64)
    {
        Elf64_Ehdr *elf_hdr = (Elf64_Ehdr *)base_addr;

        patch.dst = (uint64_t)PTR_DIFF(&elf_hdr->e_shnum, base_addr);
        patch.src = (uint32_t)PTR_DIFF(zero, m_metadata);
        patch.size = (uint32_t)sizeof(elf_hdr->e_shnum);
        patches.push_back(patch);

        patch.dst = (uint64_t)PTR_DIFF(&elf_hdr->e_shoff, base_addr);
        patch.src = (uint32_t)PTR_DIFF(zero, m_metadata);
        patch.size = (uint32_t)sizeof(elf_hdr->e_shoff);
        patches.push_back(patch);

        patch.dst = (uint64_t)PTR_DIFF(&elf_hdr->e_shstrndx, base_addr);
        patch.src = (uint32_t)PTR_DIFF(zero, m_metadata);
        patch.size = (uint32_t)sizeof(elf_hdr->e_shstrndx);
        patches.push_back(patch);
    }
    if(false == build_patch_entries(patches))
    {
        return false;
    }
    return true;
}
layout_entry_t *CMetadata::get_entry_by_id(uint16_t id)
{
    layout_entry_t *layout_start = GET_PTR(layout_entry_t, m_metadata, m_metadata->dirs[DIR_LAYOUT].offset);
    layout_entry_t *layout_end = GET_PTR(layout_entry_t, m_metadata, m_metadata->dirs[DIR_LAYOUT].offset + m_metadata->dirs[DIR_LAYOUT].size);
    for (layout_entry_t *layout = layout_start; layout < layout_end; layout++)
    {
        if(layout->id == id)
            return layout;
    }
    assert(false);
    return NULL;
}
bool CMetadata::build_gd_template(uint8_t *data, uint32_t *data_size)
{
    m_create_param.stack_limit_addr = get_entry_by_id(LAYOUT_ID_STACK)->rva - get_entry_by_id(LAYOUT_ID_TCS)->rva;
    m_create_param.stack_base_addr = ((uint64_t)get_entry_by_id(LAYOUT_ID_STACK)->page_count << SE_PAGE_SHIFT) + m_create_param.stack_limit_addr;
    m_create_param.first_ssa_gpr = get_entry_by_id(LAYOUT_ID_SSA)->rva - get_entry_by_id(LAYOUT_ID_TCS)->rva
                                    + SSA_FRAME_SIZE * SE_PAGE_SIZE - (uint64_t)sizeof(ssa_gpr_t);
    m_create_param.enclave_size = m_metadata->enclave_size;
    m_create_param.heap_offset = get_entry_by_id(LAYOUT_ID_HEAP)->rva;

    uint64_t tmp_tls_addr = get_entry_by_id(LAYOUT_ID_TD)->rva - get_entry_by_id(LAYOUT_ID_TCS)->rva;
    m_create_param.td_addr = tmp_tls_addr + (((uint64_t)get_entry_by_id(LAYOUT_ID_TD)->page_count - 1) << SE_PAGE_SHIFT);

    const Section *section = m_parser->get_tls_section();
    if(section)
    {
        /* adjust the tls_addr to be the pointer to the actual TLS data area */
        m_create_param.tls_addr = m_create_param.td_addr - section->virtual_size();
        assert(TRIM_TO_PAGE(m_create_param.tls_addr) == tmp_tls_addr);
    }
    else
        m_create_param.tls_addr = tmp_tls_addr;

    if(false == m_parser->update_global_data(&m_create_param, data, data_size))
    {
        se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);  // metadata structure doesnot have enough memory for global_data template
        return false;
    }
    return true;
}

bool CMetadata::build_tcs_template(tcs_t *tcs)
{
    tcs->oentry = m_parser->get_symbol_rva("enclave_entry");
    if(tcs->oentry == 0)
    {
        return false;
    }
    tcs->nssa = SSA_NUM;
    tcs->cssa = 0;
    tcs->ossa = get_entry_by_id(LAYOUT_ID_SSA)->rva - get_entry_by_id(LAYOUT_ID_TCS)->rva;
    //fs/gs pointer at TLS/TD
    tcs->ofs_base = tcs->ogs_base = get_entry_by_id(LAYOUT_ID_TD)->rva - get_entry_by_id(LAYOUT_ID_TCS)->rva + (((uint64_t)get_entry_by_id(LAYOUT_ID_TD)->page_count - 1) << SE_PAGE_SHIFT);
    tcs->ofs_limit = tcs->ogs_limit = (uint32_t)-1;
    return true;
}

void* CMetadata::get_rawdata_by_rva(uint64_t rva)
{
    std::vector<Section*> sections = m_parser->get_sections();

    for(unsigned int i = 0; i < sections.size() ; i++)
    {
        uint64_t start_rva = TRIM_TO_PAGE(sections[i]->get_rva());
        uint64_t end_rva = ROUND_TO_PAGE(sections[i]->get_rva() + sections[i]->virtual_size());
        if(start_rva <= rva && rva < end_rva)
        {
            uint64_t offset = rva - sections[i]->get_rva();
            if (offset > sections[i]->raw_data_size())
            {
                return 0;
            }
            return GET_PTR(void, sections[i]->raw_data(), offset);
        }
    }

    return 0;
}

uint64_t CMetadata::calculate_sections_size()
{
    std::vector<Section*> sections = m_parser->get_sections();
    uint64_t max_rva = 0;
    Section *last_section = NULL;

    for(unsigned int i = 0; i < sections.size() ; i++)
    {
        if(sections[i]->get_rva() > max_rva) {
            max_rva = sections[i]->get_rva();
            last_section = sections[i];
        }
    }

    uint64_t size = (NULL == last_section) ? (0) : (last_section->get_rva() + last_section->virtual_size());
    size = ROUND_TO_PAGE(size);
    
    
    
    if(size < ROUND_TO_PAGE(last_section->get_rva() + ROUND_TO_PAGE(last_section->virtual_size())))
    {
        size += SE_PAGE_SIZE;
    }    

    return size;
}

uint64_t CMetadata::calculate_enclave_size(uint64_t size)
{
    uint64_t enclave_max_size = m_parser->get_enclave_max_size();

    if(size > enclave_max_size)
        return (uint64_t)-1;

    uint64_t round_size = 1;
    while (round_size < size)
    {
        round_size <<=1;
        if(!round_size)
            return (uint64_t)-1;
    }
    
    if(round_size > enclave_max_size)
        return (uint64_t)-1;

    return round_size;
}

bool update_metadata(const char *path, const metadata_t *metadata, uint64_t meta_offset)
{
    assert(path != NULL && metadata != NULL);

    return write_data_to_file(path, std::ios::in | std::ios::binary| std::ios::out, 
        reinterpret_cast<uint8_t *>(const_cast<metadata_t *>( metadata)), metadata->size, (long)meta_offset);
}

