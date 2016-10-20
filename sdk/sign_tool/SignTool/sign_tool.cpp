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


// SignTool.cpp : Defines the entry point for the console application.
//

/** 
* File: 
*     sign_tool.cpp
*Description: 
*     Defines the entry point for the application.
* 
*/

#include "ippcp.h"
#include "ippcore.h"

#include "metadata.h"
#include "manage_metadata.h"
#include "ipp_wrapper.h"
#include "parse_key_file.h"
#include "enclave_creator_sign.h"
#include "util_st.h"

#include "se_trace.h"
#include "sgx_error.h"

#include "se_map.h"
#include "loader.h"
#include "parserfactory.h"
#include "elf_helper.h"

#include <unistd.h>

#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <assert.h>

#include <string>
#include <memory>
#include <sstream>

#define SIGNATURE_SIZE 384

typedef enum _command_mode_t
{
    SIGN = 0,
    GENDATA,
    CATSIG,
    COMPARE
} command_mode_t;

typedef enum _file_path_t
{
    DLL = 0,
    XML = 1,
    KEY,
    OUTPUT,
    SIG,
    UNSIGNED,
    REVIEW_ENCLAVE
} file_path_t;

static bool get_time(uint32_t *date)
{
    assert(date != NULL);

    time_t rawtime = 0;
    if(time( &rawtime) == -1)
        return false;
    struct tm *timeinfo = gmtime(&rawtime);
    if(timeinfo  == NULL)
        return false;
    uint32_t tmp_date = (timeinfo->tm_year+1900)*10000 + (timeinfo->tm_mon+1)*100 + timeinfo->tm_mday;
    stringstream ss;
    ss<<"0x"<<tmp_date;
    ss>>hex>>tmp_date;
    *date = tmp_date;
    return true;
}

static int load_enclave(BinParser *parser, metadata_t *metadata)
{
    std::unique_ptr<CLoader> ploader(new CLoader(const_cast<uint8_t *>(parser->get_start_addr()), *parser));
    return ploader->load_enclave_ex(NULL, 0, metadata, NULL);
}


#define THE_INVALID_HANDLE (-1)

static int open_file(const char* dllpath)
{
    FILE *fp = fopen(dllpath, "rb");
    if (fp == NULL)
    return THE_INVALID_HANDLE;

    return fileno(fp);
}

static void close_handle(int fd)
{
    close(fd);
}


static bool get_enclave_info(BinParser *parser, bin_fmt_t *bf, uint64_t * meta_offset)
{
    uint64_t meta_rva = parser->get_metadata_offset();
    const uint8_t *base_addr = parser->get_start_addr();
    metadata_t *metadata = GET_PTR(metadata_t, base_addr, meta_rva);

    if(metadata->magic_num == METADATA_MAGIC)
    {
        se_trace(SE_TRACE_ERROR, ENCLAVE_ALREADY_SIGNED_ERROR);
        return false;
    }

    *bf = parser->get_bin_format();
    *meta_offset = meta_rva;
    return true;
}

// measure_enclave():
//    1. Get the enclave hash by loading enclave
//    2. Get the enclave info - metadata offset and enclave file format
static bool measure_enclave(uint8_t *hash, const char *dllpath, const xml_parameter_t *parameter, metadata_t *metadata, bin_fmt_t *bin_fmt, uint64_t *meta_offset)
{
    assert(hash && dllpath && metadata && bin_fmt && meta_offset);
    bool res = false;
    uint32_t file_size = 0;

    se_file_handle_t fh = open_file(dllpath);
    if (fh == THE_INVALID_HANDLE)
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, dllpath);
        return false;
    }

    // Probably we can use `decltype' if all major supported compilers support that.
    std::unique_ptr<map_handle_t, void (*)(map_handle_t*)> mh(map_file(fh, &file_size), unmap_file);
    if (!mh)
    {
        close_handle(fh);
        return false;
    }
    // Parse enclave
    std::unique_ptr<BinParser> parser(binparser::get_parser(mh->base_addr, (size_t)file_size));
    assert(parser != NULL);

    sgx_status_t status = parser->run_parser();
    if (status != SGX_SUCCESS)
    {
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR);
        close_handle(fh);
        return false;
    }

    // generate metadata
    CMetadata meta(metadata, parser.get());
    if(meta.build_metadata(parameter) == false)
    {
        close_handle(fh);
        return false;
    }

    // Collect enclave info
    if(get_enclave_info(parser.get(), bin_fmt, meta_offset) == false)
    {
        close_handle(fh);
        return false;
    }

    if (*bin_fmt == BF_ELF64)
    {
        ElfHelper<64>::dump_textrels(parser.get());
    }
    else if (*bin_fmt == BF_ELF32)
    {
        ElfHelper<32>::dump_textrels(parser.get());
    }

    // Load enclave to get enclave hash
    int ret = load_enclave(parser.release(), metadata);
    close_handle(fh);

    switch(ret)
    {
    case SGX_ERROR_INVALID_METADATA:
        se_trace(SE_TRACE_ERROR, OUT_OF_EPC_ERROR);
        res = false;
        break;
    case SGX_ERROR_INVALID_VERSION:
        se_trace(SE_TRACE_ERROR, META_VERSION_ERROR);
        res = false;
        break;
    case SGX_ERROR_INVALID_ENCLAVE:
        se_trace(SE_TRACE_ERROR, INVALID_ENCLAVE_ERROR);
        res = false;
        break;
    case SGX_SUCCESS:
        ret = dynamic_cast<EnclaveCreatorST*>(get_enclave_creator())->get_enclave_info(hash, SGX_HASH_SIZE);
        if(ret != SGX_SUCCESS)
        {
            res = false;
            break;
        }
        res = true;
        break;
    default:
        res = false;
        break;
    }

    return res;
}

static void set_meta_attributes(metadata_t *meta)
{
    assert(meta != NULL);
    //set metadata.attributes
    //low 64 bit: it's the same as enclave_css
    memset(&meta->attributes, 0, sizeof(sgx_attributes_t));
    meta->attributes.flags = meta->enclave_css.body.attributes.flags; 
    //high 64 bit
    //set bits that will not be checked
    meta->attributes.xfrm = ~meta->enclave_css.body.attribute_mask.xfrm;
    //set bits that have been set '1' and need to be checked
    meta->attributes.xfrm |= (meta->enclave_css.body.attributes.xfrm & meta->enclave_css.body.attribute_mask.xfrm);
    return;
}

//fill_enclave_css()
//       file the enclave_css_t structure with the parameter, enclave_hash
//       If the RSA_key is not null, fill the key part
//       If RSA_key == NULL, fill the header and body(GENDATA mode)
//       If the path[UNSIGNED] != NULL, update the header.date(CATSIG mode)
static bool fill_enclave_css(const IppsRSAPublicKeyState *pub_key, const xml_parameter_t *para, const uint8_t *enclave_hash, 
                             const char **path, enclave_css_t *css, bin_fmt_t bf)
{
    assert(para != NULL && enclave_hash != NULL && path != NULL && css != NULL);

    enclave_css_t enclave_css;
    memset(&enclave_css, 0, sizeof(enclave_css_t));

    uint32_t date = 0;
    if(false == get_time(&date))
        return false;

    //*****fill the header*******************
    uint8_t header[12] = {6, 0, 0, 0, 0xE1, 0, 0, 0, 0, 0, 1, 0};
    uint8_t header2[16] = {1, 1, 0, 0, 0x60, 0, 0, 0, 0x60, 0, 0, 0, 1, 0, 0, 0};
    memcpy_s(&enclave_css.header.header, sizeof(enclave_css.header.header), &header, sizeof(header));
    memcpy_s(&enclave_css.header.header2, sizeof(enclave_css.header.header2), &header2, sizeof(header2));

    // For 'type', signing tool clears the bit 31 for product enclaves 
    // and set the bit 31 for debug enclaves
    enclave_css.header.type = (para[RELEASETYPE].value & 0x01) ? (1<<31) : 0;
    enclave_css.header.module_vendor = (para[INTELSIGNED].value&0x01) ? 0x8086 : 0;
    enclave_css.header.date = date;

    //if pub_key is not NULL, fill the key part
    if(pub_key)
    {
        int exponent_size = 0;
        int modulus_size = 0;
        IppStatus error_code = get_pub_key(pub_key, &exponent_size, 
            (Ipp32u *)&enclave_css.key.exponent, 
            &modulus_size, 
            (Ipp32u *)&enclave_css.key.modulus);
        if(error_code != ippStsNoErr)
        {
            return false;
        }
        exponent_size = (uint32_t)(ROUND_TO(exponent_size, sizeof(Ipp32u)) / sizeof(Ipp32u));
        modulus_size = (uint32_t)(ROUND_TO(modulus_size, sizeof(Ipp32u)) / sizeof(Ipp32u));
        assert(enclave_css.key.exponent[0] == 0x03);
        assert(exponent_size == 0x1);
        assert(modulus_size == 0x60);
    }

    //hardware version
    enclave_css.header.hw_version = (uint32_t)para[HW].value;

    //****************************fill the body***********************
    // Misc_select/Misc_mask
    enclave_css.body.misc_select = (uint32_t)para[MISCSELECT].value;
    enclave_css.body.misc_mask = (uint32_t)para[MISCMASK].value;
    //low 64 bit
    enclave_css.body.attributes.flags = 0;
    enclave_css.body.attribute_mask.flags = ~SGX_FLAGS_DEBUG;
    if(para[DISABLEDEBUG].value == 1)
    {
        enclave_css.body.attributes.flags &=  ~SGX_FLAGS_DEBUG;
        enclave_css.body.attribute_mask.flags |= SGX_FLAGS_DEBUG;
    }
    if(para[PROVISIONKEY].value == 1)
    {
        enclave_css.body.attributes.flags |= SGX_FLAGS_PROVISION_KEY;
        enclave_css.body.attribute_mask.flags |= SGX_FLAGS_PROVISION_KEY;
    }
    if(para[LAUNCHKEY].value == 1)
    {
        enclave_css.body.attributes.flags |= SGX_FLAGS_LICENSE_KEY;
        enclave_css.body.attribute_mask.flags |= SGX_FLAGS_LICENSE_KEY;
    }
    if(bf == BF_PE64 || bf == BF_ELF64)
    {
        enclave_css.body.attributes.flags |= SGX_FLAGS_MODE64BIT;
        enclave_css.body.attribute_mask.flags |= SGX_FLAGS_MODE64BIT;
    }
    // high 64 bit
    //default setting
    enclave_css.body.attributes.xfrm = SGX_XFRM_LEGACY;
    enclave_css.body.attribute_mask.xfrm = SGX_XFRM_LEGACY | SGX_XFRM_RESERVED; // LEGACY and reserved bits would be checked.

    memcpy_s(&enclave_css.body.enclave_hash, sizeof(enclave_css.body.enclave_hash), enclave_hash, SGX_HASH_SIZE);
    enclave_css.body.isv_prod_id = (uint16_t)para[PRODID].value; 
    enclave_css.body.isv_svn = (uint16_t)para[ISVSVN].value;

    //Copy the css to output css buffer
    memcpy_s(css, sizeof(enclave_css_t), &enclave_css, sizeof(enclave_css_t));

    if(path[UNSIGNED] != NULL)
    {
        // In catsig mode, update the header.date as the time when the unsigned file is generated.
        memset(&enclave_css, 0, sizeof(enclave_css));
        size_t fsize = get_file_size(path[UNSIGNED]);
        if(fsize != sizeof(enclave_css.header) + sizeof(enclave_css.body))
        {
          se_trace(SE_TRACE_ERROR, UNSIGNED_FILE_ERROR, path[UNSIGNED]);
          return false;
        }
        uint8_t *buf = new uint8_t[fsize];
        memset(buf, 0, fsize);
        if(read_file_to_buf(path[UNSIGNED], buf, fsize) == false)
        {
            se_trace(SE_TRACE_ERROR, READ_FILE_ERROR, path[UNSIGNED]);
            return false;
        }
        memcpy_s(&enclave_css.header, sizeof(enclave_css.header), buf, sizeof(enclave_css.header));
        memcpy_s(&enclave_css.body, sizeof(enclave_css.body), buf + sizeof(enclave_css.header), fsize - sizeof(enclave_css.header));
        delete [] buf;
        css->header.date = enclave_css.header.date;
        // Verify the header and body read from the unsigned file to make sure it's  the same as that generated from xml file
        if(memcmp(&enclave_css.header, &css->header, sizeof(enclave_css.header)) || memcmp(&enclave_css.body, &css->body, sizeof(enclave_css.body)))
        {
            se_trace(SE_TRACE_ERROR, UNSIGNED_FILE_XML_MISMATCH);
            return false;
        }
    }   
    return true;
}

static IppStatus calc_RSAq1q2(int length_s, const Ipp32u *data_s, int length_m, const Ipp32u *data_m, 
    int *length_q1, Ipp32u *data_q1, int *length_q2, Ipp32u *data_q2)
{
    IppStatus error_code = ippStsSAReservedErr1;
    IppsBigNumState *pM=0, *pS=0, *pQ1=0, *pQ2=0, *ptemp1=0, *ptemp2=0;
    IppsBigNumSGN sgn = IppsBigNumPOS;
    int length_in_bit = 0;
    Ipp32u *pdata = NULL;

    //create 6 big number

    if(!data_s || !data_m || !length_q1 || !data_q1 || !length_q2 
        || !data_q2 || length_s <= 0 || length_m <= 0)
    {
        error_code = ippStsBadArgErr;
        goto clean_return;
    }

    error_code = newBN(data_s, length_s, &pS);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    error_code = newBN(data_m, length_m, &pM);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    error_code = newBN(0, length_m, &pQ1);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    error_code = newBN(0, length_m, &pQ2);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    error_code = newBN(0, length_m*2, &ptemp1);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    error_code = newBN(0, length_m, &ptemp2);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }

    //signed big number operation
    //multiplies pS and pS, ptemp1 is the multiplication result
    error_code = ippsMul_BN(pS, pS, ptemp1);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }

    //ptemp1: dividend, pM: divisor, pQ1: qutient, ptemp2: remainder
    error_code = ippsDiv_BN(ptemp1, pM, pQ1, ptemp2);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    error_code = ippsMul_BN(pS, ptemp2, ptemp1);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    error_code = ippsDiv_BN(ptemp1, pM, pQ2, ptemp2);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    //extract the sign and value of the integer big number from the input structure(pQ1)

    error_code = ippsRef_BN(&sgn, &length_in_bit, &pdata, pQ1);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    *length_q1 = ROUND_TO(length_in_bit, 8)/8;
    memset(data_q1, 0, *length_q1);
    memcpy_s(data_q1, *length_q1, pdata, *length_q1);

    error_code = ippsRef_BN(&sgn, &length_in_bit, &pdata, pQ2);
    if(error_code != ippStsNoErr)
    {
        goto clean_return;
    }
    *length_q2 = ROUND_TO(length_in_bit, 8)/8;
    memset(data_q2, 0, *length_q2);
    memcpy_s(data_q2, *length_q2, pdata, *length_q2);
    goto clean_return;

clean_return:
    secure_free_BN(pM, length_m);
    secure_free_BN(pS, length_s);
    secure_free_BN(pQ1, length_m);
    secure_free_BN(pQ2, length_m);
    secure_free_BN(ptemp1, length_m*2);
    secure_free_BN(ptemp2, length_m);

    return error_code;
}

static bool create_signature(const IppsRSAPrivateKeyState *pri_key1, const char *sigpath, enclave_css_t *enclave_css)
{
    IppStatus error_code = ippStsNoErr;
    assert(enclave_css != NULL);
    assert(!(pri_key1 == NULL && sigpath == NULL) && !(pri_key1 != NULL && sigpath != NULL));

    uint8_t signature[SIGNATURE_SIZE];    // keep the signature in big endian
    memset(signature, 0, SIGNATURE_SIZE);
    //**********get the signature*********
    if(sigpath != NULL)//CATSIG mode
    {
        if(get_file_size(sigpath) != SIGNATURE_SIZE)
        {
            se_trace(SE_TRACE_ERROR, SIG_FILE_ERROR, sigpath);
            return false;
        }
        if(read_file_to_buf(sigpath, signature, SIGNATURE_SIZE) == false)
        {
            se_trace(SE_TRACE_ERROR, READ_FILE_ERROR, sigpath);
            return false;
        }
    }
    else  //SIGN mode
    {   
        size_t buffer_size = sizeof(enclave_css->header) + sizeof(enclave_css->body);
        Ipp8u * temp_buffer = (Ipp8u *)malloc(buffer_size * sizeof(char));
        if(NULL == temp_buffer)
        {
            se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
            return false;
        }
        memcpy_s(temp_buffer, buffer_size, &enclave_css->header, sizeof(enclave_css->header));
        memcpy_s(temp_buffer + sizeof(enclave_css->header), buffer_size - sizeof(enclave_css->header),
            &enclave_css->body, sizeof(enclave_css->body));
        int pri1_size = 0;
        if(ippsRSA_GetBufferSizePrivateKey(&pri1_size, pri_key1) != ippStsNoErr)
        {
            free(temp_buffer);
            return false;
        }
        Ipp8u *scratch_buf = (Ipp8u *)malloc(pri1_size);
        if(NULL == scratch_buf)
        {
            se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
            free(temp_buffer);
            return false;
        }
        memset(scratch_buf, 0, pri1_size);
        error_code = ippsRSASign_PKCS1v15((const Ipp8u *)temp_buffer, (int)buffer_size, (Ipp8u *)signature, pri_key1, NULL, ippHashAlg_SHA256, scratch_buf);
        free(scratch_buf);
        free(temp_buffer);

        if(error_code != ippStsNoErr)
        {
            return false;
        }
    }
    for(int i = 0; i<SIGNATURE_SIZE; i++)
    {
        (enclave_css->key.signature)[i] = signature[SIGNATURE_SIZE-1-i];
    }
    //************************calculate q1 and q2*********************
    int length_q1 = 0, length_q2 = 0;
    error_code = calc_RSAq1q2(sizeof(enclave_css->key.signature), 
        (Ipp32u *)&enclave_css->key.signature, 
        sizeof(enclave_css->key.modulus), 
        (Ipp32u *)&enclave_css->key.modulus, 
        &length_q1, 
        (Ipp32u *)&enclave_css->buffer.q1, 
        &length_q2, 
        (Ipp32u *)&enclave_css->buffer.q2);
    if(error_code != ippStsNoErr)
    {
        return false;
    }
    return true;
}

static bool verify_signature(const rsa_params_t *rsa, const enclave_css_t *enclave_css,  int *signature_verified)
{
    assert(rsa != NULL && enclave_css != NULL && signature_verified != NULL);
    IppsRSAPublicKeyState *pub_key = NULL;
    IppStatus error_code = create_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, rsa->n, rsa->e, &pub_key);
    if(error_code != ippStsNoErr)
    {
        return false;
    }
    size_t buffer_size = sizeof(enclave_css->header) + sizeof(enclave_css->body);
    Ipp8u * temp_buffer = (Ipp8u *)malloc(buffer_size * sizeof(char));
    if(NULL == temp_buffer)
    {
        se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
        secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);
        return false;
    }
    memcpy_s(temp_buffer, buffer_size, &enclave_css->header, sizeof(enclave_css->header));
    memcpy_s(temp_buffer + sizeof(enclave_css->header), buffer_size-sizeof(enclave_css->header), 
        &enclave_css->body, sizeof(enclave_css->body));
    uint8_t signature[SIGNATURE_SIZE];
    for(int i=0; i<SIGNATURE_SIZE; i++)
    {
        signature[i] = enclave_css->key.signature[SIGNATURE_SIZE-1-i];
    }
    int pub_size = 0;
    if(ippsRSA_GetBufferSizePublicKey(&pub_size, pub_key) != ippStsNoErr)
    {
        secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);
        free(temp_buffer);
        return false;
    }
    Ipp8u *scratch_buf = (Ipp8u *)malloc(pub_size);
    if(NULL == scratch_buf)
    {
        se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
        secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);
        free(temp_buffer);
        return false;
    }
    memset(scratch_buf, 0, pub_size);
    error_code = ippsRSAVerify_PKCS1v15((const Ipp8u *)temp_buffer, (int)buffer_size, (Ipp8u *)signature, signature_verified, pub_key, ippHashAlg_SHA256, scratch_buf);
    free(temp_buffer);
    free(scratch_buf);
    secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);

    if(error_code != ippStsNoErr)
    {
        se_trace(SE_TRACE_DEBUG, "ippsRSASSAVerify_SHA256_PKCSv15() returns failure. The ipperrorCode is %d \n", error_code);     
        return false;
    }
    else
    {
        se_trace(SE_TRACE_DEBUG, "RSAVerify() returns success. The signature_verified is %d\n", *signature_verified);
        return true;
    }
}

static bool gen_enclave_signing_file(const enclave_css_t *enclave_css, const char *outpath)
{
    assert(enclave_css != NULL);
    size_t size = sizeof(enclave_css->header) + sizeof(enclave_css->body);
    uint8_t *buffer = (uint8_t *)malloc(size);
    if(buffer == NULL)
    {
        se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
        return false;
    }
    memcpy_s(buffer, sizeof(enclave_css->header), &enclave_css->header, sizeof(enclave_css->header));
    memcpy_s(buffer + sizeof(enclave_css->header), sizeof(enclave_css->body), &enclave_css->body, sizeof(enclave_css->body));

    if(write_data_to_file(outpath, std::ios::out|std::ios::binary, buffer, size) == false)
    {
        free(buffer);
        return false;
    }
    free(buffer);
    return true;
}

static bool cmdline_parse(unsigned int argc, char *argv[], int *mode, const char **path)
{
    assert(mode!=NULL && path != NULL);
    if(argc<2)
    {
        se_trace(SE_TRACE_ERROR, LACK_PARA_ERROR);
        return false;
    }
    if(argc == 2 && !STRCMP(argv[1], "-help"))
    {
         se_trace(SE_TRACE_ERROR, USAGE_STRING);
         *mode = -1;
         return true;
    }

    enum { PAR_REQUIRED, PAR_OPTIONAL, PAR_INVALID };
    typedef struct _param_struct_{
        const char *name;          //options
        char *value;               //keep the path
        int flag;                  //indicate this parameter is required(0), optional(1) or invalid(2) 
    }param_struct_t;               //keep the parameter pairs

    param_struct_t params_sign[] = {
        {"-enclave", NULL, PAR_REQUIRED},
        {"-config", NULL, PAR_OPTIONAL},
        {"-key", NULL, PAR_REQUIRED},
        {"-out", NULL, PAR_REQUIRED},
        {"-sig", NULL, PAR_INVALID},
        {"-unsigned", NULL, PAR_INVALID},
        {"-review_enclave", NULL, PAR_INVALID}};
    param_struct_t params_gendata[] = {
        {"-enclave", NULL, PAR_REQUIRED},
        {"-config", NULL, PAR_OPTIONAL},
        {"-key", NULL, PAR_INVALID},
        {"-out", NULL, PAR_REQUIRED},
        {"-sig", NULL, PAR_INVALID},
        {"-unsigned", NULL, PAR_INVALID},
        {"-review_enclave", NULL, PAR_INVALID}};
    param_struct_t params_catsig[] = {
        {"-enclave", NULL, PAR_REQUIRED},
        {"-config", NULL, PAR_OPTIONAL},
        {"-key", NULL, PAR_REQUIRED},
        {"-out", NULL, PAR_REQUIRED},
        {"-sig", NULL, PAR_REQUIRED},
        {"-unsigned", NULL, PAR_REQUIRED},
        {"-review_enclave", NULL, PAR_INVALID}};
    param_struct_t params_compare[] = {
        {"-enclave", NULL, PAR_REQUIRED},
        {"-config", NULL, PAR_OPTIONAL},
        {"-key", NULL, PAR_INVALID},
        {"-out", NULL, PAR_INVALID},
        {"-sig", NULL, PAR_INVALID},
        {"-unsigned", NULL, PAR_REQUIRED},
        {"-review_enclave", NULL, PAR_REQUIRED}};


    const char *mode_m[] ={"sign", "gendata","catsig", "compare"};
    param_struct_t *params[] = {params_sign, params_gendata, params_catsig, params_compare};
    unsigned int tempidx=0;
    for(; tempidx<sizeof(mode_m)/sizeof(mode_m[0]); tempidx++)
    {
        if(!STRCMP(mode_m[tempidx], argv[1]))//match
        {
            break;
        }
    }
    unsigned int tempmode = tempidx;
    if(tempmode>=sizeof(mode_m)/sizeof(mode_m[0]))
    {
        se_trace(SE_TRACE_ERROR, UNREC_CMD_ERROR, argv[1]);
        return false;
    }
    
    unsigned int params_count = (unsigned)(sizeof(params_sign)/sizeof(params_sign[0]));
    unsigned int params_count_min = 0;
    unsigned int params_count_max =0;
    for(unsigned int i=0; i< params_count; i++)
    {
        params_count_max ++;
        if(params[tempmode][i].flag == PAR_REQUIRED)
            params_count_min ++;
    }
    unsigned int additional_param = 2;
    if(argc<params_count_min * 2 + additional_param)
        return false;
    if(argc>params_count_max * 2 + additional_param)
        return false;

    for(unsigned int i=2; i<argc; i=i+2)
    {
        unsigned int j=0;
        for(; j<params_count; j++)
        {
            if(STRCMP(argv[i], params[tempmode][j].name)==0) //match
            {
                if((i<argc-1)&&(STRNCMP(argv[i+1],"-", 1)))  // assuming pathname doesn't contain "-"
                {
                    if(params[tempmode][j].value != NULL)
                    {
                        se_trace(SE_TRACE_ERROR, REPEAT_OPTION_ERROR, params[tempmode][j].name);
                        return false;
                    }
                    params[tempmode][j].value = argv[i+1];
                    break;
                }
                else     //didn't match: 1) no path parameter behind option parameter 2) parameters format error. 
                {
                    se_trace(SE_TRACE_ERROR, INVALID_FILE_NAME_ERROR, params[tempmode][j].name);
                    return false;
                }
            }
        }
        if(j>=params_count_max)
        {
            return false;
        }
    }
    for(unsigned int i = 0; i < params_count; i ++)
    {
        if(params[tempmode][i].flag == PAR_REQUIRED && params[tempmode][i].value == NULL)
        {
            se_trace(SE_TRACE_ERROR, LACK_REQUIRED_OPTION_ERROR, params[tempmode][i].name, mode_m[tempmode]);
            return false;
        }
        if(params[tempmode][i].flag == PAR_INVALID && params[tempmode][i].value != NULL)
        {
            se_trace(SE_TRACE_ERROR, GIVE_INVALID_OPTION_ERROR, params[tempmode][i].name, mode_m[tempmode]);
            return false;
        }
    }
    for(unsigned int i = 0; i < params_count; i++)
    {
        path[i] = params[tempmode][i].value;
    }
    *mode = tempmode;
    return true;

}

static bool fill_meta_without_signature(const IppsRSAPublicKeyState *pub_key, const char **path, const uint8_t *enclave_hash, 
                                        const xml_parameter_t *para, metadata_t *metadata, bin_fmt_t bf)
{
    assert(path && enclave_hash && para && metadata);
    if(false == fill_enclave_css(pub_key, para, enclave_hash, path, &metadata->enclave_css, bf))
    {
        return false;
    }
    set_meta_attributes(metadata);

    return true;
}

//generate_output: 
//    To generate the final output file
//    SIGN-    need to fill the enclave_css_t(key part included), sign the header and body and
//             update the metadata in the out file
//    GENDATA- need to fill the enclave_css_t(key part excluded), get the body and header, 
//             and then write the whole out file with body+header+hash
//    CATSIG-  need to fill the enclave_css_t(include key), read the signature from the sigpath, 
//             and then update the metadata in the out file
static bool generate_output(int mode, int ktype, const uint8_t *enclave_hash, const xml_parameter_t *para, const rsa_params_t *rsa, metadata_t *metadata, 
                            const char **path, bin_fmt_t bf, uint64_t meta_offset)
{
    assert(enclave_hash != NULL && para != NULL && metadata != NULL && path != NULL && rsa != NULL);
    IppsRSAPrivateKeyState *pri_key1 = NULL;
    IppsRSAPublicKeyState *pub_key = NULL;
    int validate_result = IS_INVALID_KEY;
    IppStatus error_code = ippStsNoErr;

    switch(mode)
    {
    case SIGN:
        {
            if(ktype != PRIVATE_KEY)
            {
                se_trace(SE_TRACE_ERROR, LACK_PRI_KEY_ERROR);
                return false;
            }

            error_code = create_validate_rsa_key_pair(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, rsa->n, rsa->d, rsa->e, 
                rsa->p, rsa->q, rsa->dmp1, rsa->dmq1, rsa->iqmp, &pri_key1, &pub_key, &validate_result);
            if(error_code != ippStsNoErr || validate_result != IS_VALID_KEY)
            {
                se_trace(SE_TRACE_ERROR, KEY_FORMAT_ERROR);
                secure_free_rsa_pri1_key(N_SIZE_IN_BYTES, D_SIZE_IN_BYTES, pri_key1);
                secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);
                return false;
            }

            if(false == fill_meta_without_signature(pub_key, path, enclave_hash, para, metadata, bf))
            {
                secure_free_rsa_pri1_key(N_SIZE_IN_BYTES, D_SIZE_IN_BYTES, pri_key1);
                secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);
                return false;
            }
            if(false == create_signature(pri_key1, NULL, &(metadata->enclave_css)))
            {
                secure_free_rsa_pri1_key(N_SIZE_IN_BYTES, D_SIZE_IN_BYTES, pri_key1);
                secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);
                return false;
            }
            secure_free_rsa_pri1_key(N_SIZE_IN_BYTES, D_SIZE_IN_BYTES, pri_key1);
            secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);

            if(false == update_metadata(path[OUTPUT], metadata, meta_offset))
            {
                return false;
            }
            break;
        }
    case GENDATA:
        {
            if(false == fill_meta_without_signature(NULL, path, enclave_hash, para, metadata, bf))
            {
                return false;
            }
            if(false == gen_enclave_signing_file(&(metadata->enclave_css), path[OUTPUT]))
            {
                return false;
            }
            break;
        }
    case CATSIG:
        {
            if(ktype != PUBLIC_KEY)
            {
                se_trace(SE_TRACE_ERROR, LACK_PUB_KEY_ERROR);
                return false;
            }

            if(create_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, rsa->n, rsa->e, &pub_key) != ippStsNoErr)
            {
                se_trace(SE_TRACE_ERROR, KEY_FORMAT_ERROR);
                return false;
            }
            if(false == fill_meta_without_signature(pub_key, path, enclave_hash, para, metadata, bf))
            {
                secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);
                return false;
            }
            secure_free_rsa_pub_key(N_SIZE_IN_BYTES, E_SIZE_IN_BYTES, pub_key);

            if(false == create_signature(NULL, path[SIG], &(metadata->enclave_css)))
            {   
                return false;
            }
            if(false == update_metadata(path[OUTPUT], metadata, meta_offset))
            {
                return false;
            }
            break;
        }
    default:
        {
            return false;
        }
    }
    return true;
}
//compare two enclaves
static bool compare_enclave(const char **path, const xml_parameter_t *para)
{
    assert(path != NULL && para != NULL);
    bool res = false;
    int ret = SGX_SUCCESS;
    sgx_status_t status1 = SGX_SUCCESS, status2 = SGX_SUCCESS;
    uint32_t file_size1 =0 , file_size2 = 0;
    size_t file_size = 0;
    bin_fmt_t bin_fmt1 = BF_UNKNOWN, bin_fmt2 = BF_UNKNOWN;
    uint8_t enclave_hash[SGX_HASH_SIZE] = {0};
    uint8_t *buf = NULL;
    CMetadata *meta = NULL;
    metadata_t metadata;
    enclave_diff_info_t enclave_diff_info1, enclave_diff_info2;
    enclave_css_t enclave_css;
    memset(&enclave_css, 0, sizeof(enclave_css_t));
    memset(&enclave_diff_info1, 0, sizeof(enclave_diff_info_t));
    memset(&enclave_diff_info2, 0, sizeof(enclave_diff_info_t));
    memset(&metadata, 0, sizeof(metadata_t));

    se_file_handle_t fh1 = open_file(path[DLL]);
    if (fh1 == THE_INVALID_HANDLE)
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, path[DLL]);
        return false;
    }

    se_file_handle_t fh2 = open_file(path[REVIEW_ENCLAVE]);
    if (fh2 == THE_INVALID_HANDLE)
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, path[REVIEW_ENCLAVE]);
        close_handle(fh1);
        return false;
    }


    std::unique_ptr<map_handle_t, void (*)(map_handle_t*)> mh1(map_file(fh1, &file_size1), unmap_file);
    if (!mh1)
    {
        close_handle(fh1);
        close_handle(fh2);
        return false;
    }
    std::unique_ptr<map_handle_t, void (*)(map_handle_t*)> mh2(map_file(fh2, &file_size2), unmap_file);
    if (!mh2)
    {
        close_handle(fh1);
        close_handle(fh2);
        return false;
    }

    //check if file_size is the same
    if(file_size1 != file_size2)
    {
        close_handle(fh1);
        close_handle(fh2);
        return false;
    }

    // Parse enclave
    std::unique_ptr<BinParser> parser1(binparser::get_parser(mh1->base_addr, (size_t)file_size1));
    assert(parser1 != NULL);
    std::unique_ptr<BinParser> parser2(binparser::get_parser(mh2->base_addr, (size_t)file_size2));
    assert(parser2 != NULL);

    status1 = parser1->run_parser();
    if (status1 != SGX_SUCCESS)
    {
        goto clear_return;
    }

    status2 = parser2->run_parser();
    if (status2 != SGX_SUCCESS)
    {
        goto clear_return;
    }

    // Collect enclave info
    bin_fmt1 = parser1->get_bin_format();
    bin_fmt2 = parser2->get_bin_format();
    //two enclave should have same format
    if(bin_fmt1 != bin_fmt2)
    {
        goto clear_return;
    }
  
    //modify some info of enclave: timestamp etc.
    status1 = parser1->get_info(&enclave_diff_info1);
    if(status1 != SGX_SUCCESS)
    {
        goto clear_return;
    }
    status2 = parser2->get_info(&enclave_diff_info2);
    if(status2 != SGX_SUCCESS)
    {
        goto clear_return;
    }
    status2 = parser2->modify_info(&enclave_diff_info1);
    if(status2 != SGX_SUCCESS)
    {
        goto clear_return;
    }
 
    //get enclave hash from unsigned file
    file_size = get_file_size(path[UNSIGNED]);
    if (file_size != sizeof(enclave_css.header) + sizeof(enclave_css.body) &&
        file_size != sizeof(enclave_css.header) + sizeof(enclave_css.body) + sizeof(enclave_css.key))
    {
        goto clear_return;
    }

    buf = (uint8_t *)malloc(file_size);
    if (buf == NULL)
    {
        goto clear_return;
    }
    memset(buf, 0, file_size);
    if(read_file_to_buf(path[UNSIGNED], buf, file_size) == false)
    {
        free(buf);
        goto clear_return;
    }
    memcpy_s(&enclave_css.header, sizeof(enclave_css.header), buf, sizeof(enclave_css.header));
    memcpy_s(&enclave_css.body, sizeof(enclave_css.body), buf + (file_size - sizeof(enclave_css.body)), sizeof(enclave_css.body));
    free(buf);

    // Load enclave to get enclave hash
    meta = new CMetadata(&metadata, parser2.get());
    if(meta->build_metadata(para) == false)
    {
        delete meta;
        goto clear_return;
    }
    delete meta;

    ret = load_enclave(parser2.release(), &metadata);
    if(ret != SGX_SUCCESS)
    {
        goto clear_return;
    }
    ret = dynamic_cast<EnclaveCreatorST*>(get_enclave_creator())->get_enclave_info(enclave_hash, SGX_HASH_SIZE);
    if(ret != SGX_SUCCESS)
    {
        goto clear_return;
    }
    
    //make path[UNSIGNED] = NULL, so fill_meta_without_signature won't treat it as catsig
    path[UNSIGNED] = NULL;
    if(false == fill_meta_without_signature(NULL, path, enclave_hash, para, &metadata, bin_fmt2))
    {
        goto clear_return;
    }

    //compare 
    metadata.enclave_css.header.date = 0;
    enclave_css.header.date = 0;
    if(memcmp(&metadata.enclave_css.header, &enclave_css.header, sizeof(enclave_css.header)) != 0)
    {
        goto clear_return;
    }
    if(memcmp(&metadata.enclave_css.body, &enclave_css.body, sizeof(enclave_css.body)) != 0)
    {
        goto clear_return;
    }
    
    res = true;
clear_return:
    close_handle(fh1);
    close_handle(fh2);
    return res;
}

int main(int argc, char* argv[])
{
    xml_parameter_t parameter[] = {{"ProdID", 0xFFFF, 0, 0, 0},
                                   {"ISVSVN", 0xFFFF, 0, 0, 0},
                                   {"ReleaseType", 1, 0, 0, 0},
                                   {"IntelSigned", 1, 0, 0, 0},
                                   {"ProvisionKey",1,0,0,0},
                                   {"LaunchKey",1,0,0,0},
                                   {"DisableDebug",1,0,0,0},
                                   {"HW", 0x10,0,0,0},
                                   {"TCSNum",0xFFFFFFFF,TCS_NUM_MIN,1,0},
                                   {"TCSPolicy",TCS_POLICY_UNBIND,TCS_POLICY_BIND,TCS_POLICY_UNBIND,0},
                                   {"StackMaxSize",0x1FFFFFFFFF,STACK_SIZE_MIN,0x40000,0},
                                   {"HeapMaxSize",0x1FFFFFFFFF,HEAP_SIZE_MIN,0x100000,0},
                                   {"MiscSelect", 0xFFFFFFFF, 0, DEFAULT_MISC_SELECT, 0},
                                   {"MiscMask", 0xFFFFFFFF, 0, DEFAULT_MISC_MASK, 0}};

    const char *path[7] = {NULL, NULL, NULL, NULL, NULL, NULL, NULL};
    uint8_t enclave_hash[SGX_HASH_SIZE] = {0};
    metadata_t metadata;
    int res = -1, mode = -1;
    int key_type = UNIDENTIFIABLE_KEY; //indicate the type of the input key file
    size_t parameter_count = sizeof(parameter)/sizeof(parameter[0]);
    bin_fmt_t bin_fmt = BF_UNKNOWN;
    uint64_t meta_offset = 0;
    rsa_params_t rsa;

    memset(&rsa,      0, sizeof(rsa));
    memset(&metadata, 0, sizeof(metadata));


    //Parse command line
    if(cmdline_parse(argc, argv, &mode, path) == false)
    {
        se_trace(SE_TRACE_ERROR, USAGE_STRING);
        goto clear_return;
    }
    if(mode == -1) // User only wants to get the help info
    {
        return 0;
    }
    
    //Parse the xml file to get the metadata
    if(parse_metadata_file(path[XML], parameter, (int)parameter_count) == false)
    {
        goto clear_return;
    }
    //Parse the key file
    if(parse_key_file(path[KEY], &rsa, &key_type) == false && key_type != NO_KEY) 
    {
        goto clear_return;
    }
    //compare two enclave
    if(mode == COMPARE)
    {
        if(compare_enclave(path, parameter) == false)
        {
            se_trace(SE_TRACE_ERROR, "The two enclaves are not matched\n");
            return -1;
        }
        se_trace(SE_TRACE_ERROR, "The two enclaves are matched\n");
        return 0;
    }
    if(copy_file(path[DLL], path[OUTPUT]) == false)
    {
        se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
        goto clear_return;
    }

    if(measure_enclave(enclave_hash, path[OUTPUT], parameter, &metadata, &bin_fmt, &meta_offset) == false)
    {
        se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
        goto clear_return;
    }

    if((generate_output(mode, key_type, enclave_hash, parameter, &rsa, &metadata, path, bin_fmt, meta_offset)) == false)
    {
        se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
        goto clear_return;
    }
    //to verify
    if(mode == SIGN || mode == CATSIG)
    {
        int signature_verified = ippFalse;
        if(verify_signature(&rsa, &(metadata.enclave_css), &signature_verified) == false || signature_verified != ippTrue)
        {
            se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
            goto clear_return;
        }
    }

    se_trace(SE_TRACE_ERROR, SUCCESS_EXIT);
    res = 0;

clear_return:
    if(res == -1 && path[OUTPUT])
        remove(path[OUTPUT]);
    return res;
}
