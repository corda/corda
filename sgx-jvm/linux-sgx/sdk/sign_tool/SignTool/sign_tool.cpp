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


// SignTool.cpp : Defines the entry point for the console application.
//

/** 
* File: 
*     sign_tool.cpp
*Description: 
*     Defines the entry point for the application.
* 
*/

#include <openssl/bio.h>
#include <openssl/bn.h>
#include <openssl/sha.h>
#include <openssl/rsa.h>
#include <openssl/evp.h>
#include <openssl/err.h>

#include "metadata.h"
#include "manage_metadata.h"
#include "parse_key_file.h"
#include "enclave_creator_sign.h"
#include "util_st.h"

#include "se_trace.h"
#include "sgx_error.h"

#include "se_map.h"
#include "loader.h"
#include "parserfactory.h"
#include "elf_helper.h"
#include "crypto_wrapper.h"

#include <unistd.h>

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#include <string>
#include <memory>
#include <sstream>

#define SIGNATURE_SIZE 384

typedef enum _file_path_t
{
    DLL = 0,
    XML = 1,
    KEY,
    OUTPUT,
    SIG,
    UNSIGNED,
    DUMPFILE
} file_path_t;


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


static bool get_enclave_info(BinParser *parser, bin_fmt_t *bf, uint64_t * meta_offset, bool is_dump_mode = false)
{
    uint64_t meta_rva = parser->get_metadata_offset();
    const uint8_t *base_addr = parser->get_start_addr();
    metadata_t *metadata = GET_PTR(metadata_t, base_addr, meta_rva);

    if(metadata->magic_num == METADATA_MAGIC && is_dump_mode == false)
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
static bool measure_enclave(uint8_t *hash, const char *dllpath, const xml_parameter_t *parameter, bool ignore_rel_error, metadata_t *metadata, uint64_t *meta_offset)
{
    assert(hash && dllpath && metadata && meta_offset);
    bool res = false;
    uint32_t file_size = 0;
    uint64_t quota = 0;
    bin_fmt_t bin_fmt = BF_UNKNOWN;

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
    if(get_enclave_info(parser.get(), &bin_fmt, meta_offset) == false)
    {
        close_handle(fh);
        return false;
    }
    bool no_rel = false;
    if (bin_fmt == BF_ELF64)
    {
        no_rel = ElfHelper<64>::dump_textrels(parser.get());
    }
    else
    {
        no_rel = ElfHelper<32>::dump_textrels(parser.get());
    }
    if(no_rel == false && ignore_rel_error == false)
    {
        close_handle(fh);
        se_trace(SE_TRACE_ERROR, TEXT_REL_ERROR);
        return false;
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
        ret = dynamic_cast<EnclaveCreatorST*>(get_enclave_creator())->get_enclave_info(hash, SGX_HASH_SIZE, &quota);
        if(ret != SGX_SUCCESS)
        {
            res = false;
            break;
        }
        se_trace(SE_TRACE_ERROR, REQUIRED_ENCLAVE_SIZE, quota);
        res = true;
        break;
    default:
        res = false;
        break;
    }

    return res;
}


//fill_enclave_css()
//       fill the enclave_css_t structure with enclave_hash
//       If the 'rsa' is not null, fill the key part
//       If the path[UNSIGNED] != NULL, update the header.date(CATSIG mode)
static bool fill_enclave_css(const RSA *rsa, const char **path, 
                             const uint8_t *enclave_hash, enclave_css_t *css)
{
    assert(enclave_hash != NULL && path != NULL && css != NULL);

    //if rsa is not NULL, fill the public key part
    if(rsa)
    {
        int exponent_size = BN_num_bytes(rsa->e);
        int modulus_size = BN_num_bytes(rsa->n);
        
        if(modulus_size > SE_KEY_SIZE)
            return false;
        unsigned char *modulus = (unsigned char *)malloc(SE_KEY_SIZE);
        if(modulus == NULL)
        {
            return false;
        }
        memset(modulus, 0, SE_KEY_SIZE);
        
        exponent_size = (uint32_t)(ROUND_TO(exponent_size, sizeof(uint32_t)) / sizeof(uint32_t));
        modulus_size = (uint32_t)(ROUND_TO(modulus_size, sizeof(uint32_t)) / sizeof(uint32_t));
        
        if(BN_bn2bin(rsa->n, modulus) != SE_KEY_SIZE)
        {
            free(modulus);
            return false;
        }
        if(BN_bn2bin(rsa->e, (unsigned char *)&css->key.exponent) != 1)
        {
            free(modulus);
            return false;
        }
        for(unsigned int i = 0; i < SE_KEY_SIZE; i++)
        {
            css->key.modulus[i] = modulus[SE_KEY_SIZE -i - 1];
        }
        free(modulus);
        assert(css->key.exponent[0] == 0x03);
        assert(exponent_size == 0x1);
        assert(modulus_size == 0x60);
    }

    // fill the enclave hash 
    memcpy_s(&css->body.enclave_hash, sizeof(css->body.enclave_hash), enclave_hash, SGX_HASH_SIZE);

    if(path[UNSIGNED] != NULL)
    {
        // In catsig mode, update the header.date as the time when the unsigned file is generated.
        enclave_css_t enclave_css;
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
            delete [] buf;
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

static bool calc_RSAq1q2(int length_s, const uint8_t *data_s, int length_m, const uint8_t *data_m, 
    uint8_t *data_q1, uint8_t *data_q2)
{
    assert(data_s && data_m && data_q1 && data_q2);
    bool ret = false;
    BIGNUM *ptemp1=NULL, *ptemp2=NULL, *pQ1=NULL, *pQ2=NULL, *pM=NULL, *pS = NULL;
    unsigned char *q1 = NULL, *q2= NULL;
    BN_CTX *ctx = NULL;
    
    do{
        if((ptemp1 = BN_new()) == NULL)
            break;
        if((ptemp2 = BN_new()) == NULL)
            break;
        if((pQ1 = BN_new()) == NULL)
            break;
        if((pQ2 = BN_new()) == NULL)
            break;
        if((pM = BN_new()) == NULL)
            break;
        if((pS = BN_new()) == NULL)
            break;
    
        if(BN_bin2bn((const unsigned char *)data_m, length_m, pM) == NULL)
            break;
        if(BN_bin2bn((const unsigned char *)data_s, length_s, pS) == NULL)
            break;
        if((ctx = BN_CTX_new()) == NULL)
            break;
    
        //q1 = floor(signature*signature/modulus)
        //q2 = floor((signature*signature.signature - q1*signature*Modulus)/Modulus)
        if(BN_mul(ptemp1, pS, pS, ctx) != 1) 
            break; 
        if(BN_div(pQ1, ptemp2, ptemp1, pM, ctx) !=1)
            break;
        if(BN_mul(ptemp1, pS, ptemp2, ctx) !=1)
            break;
        if(BN_div(pQ2, ptemp2, ptemp1, pM, ctx) !=1)
            break;

        int q1_len = BN_num_bytes(pQ1);
        int q2_len = BN_num_bytes(pQ2);
        if((q1 = (unsigned char *)malloc(q1_len)) == NULL)
            break;
        if((q2 = (unsigned char *)malloc(q2_len)) == NULL)
            break;
        if(q1_len != BN_bn2bin(pQ1, (unsigned char *)q1))
            break;
        if(q2_len != BN_bn2bin(pQ2, (unsigned char *)q2))
            break;
        int size_q1 = (q1_len < SE_KEY_SIZE) ? q1_len : SE_KEY_SIZE;
        int size_q2 = (q2_len < SE_KEY_SIZE) ? q2_len : SE_KEY_SIZE;
        for(int i = 0; i < size_q1; i++)
        {
            data_q1[i] = q1[size_q1 - i -1];
        }
        for(int i = 0; i < size_q2; i++)
        {
            data_q2[i] = q2[size_q2 - i -1];
        }
        ret = true;
    }while(0);

    if(q1)
        free(q1);
    if(q2)
        free(q2);
    if(ptemp1)
        BN_clear_free(ptemp1);
    if(ptemp2)
        BN_clear_free(ptemp2);
    if(pQ1)
        BN_clear_free(pQ1);
    if(pQ2)
        BN_clear_free(pQ2);
    if(pS)
        BN_clear_free(pS);
    if(pM)
        BN_clear_free(pM);
    if(ctx)
        BN_CTX_free(ctx);
    return ret;
}


static bool create_signature(const RSA *rsa, const char *sigpath, enclave_css_t *enclave_css)
{
    assert(enclave_css != NULL);
    assert(!(rsa == NULL && sigpath == NULL) && !(rsa != NULL && sigpath != NULL));

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
        uint8_t * temp_buffer = (uint8_t *)malloc(buffer_size * sizeof(char));
        if(NULL == temp_buffer)
        {
            se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
            return false;
        }
        memcpy_s(temp_buffer, buffer_size, &enclave_css->header, sizeof(enclave_css->header));
        memcpy_s(temp_buffer + sizeof(enclave_css->header), buffer_size - sizeof(enclave_css->header),
            &enclave_css->body, sizeof(enclave_css->body));

        uint8_t hash[SGX_HASH_SIZE] = {0};
        unsigned int hash_size = SGX_HASH_SIZE;
        if(SGX_SUCCESS != sgx_EVP_Digest(EVP_sha256(), temp_buffer, (unsigned int)buffer_size, hash, &hash_size))
        {
            free(temp_buffer);
            return false;
        }

        size_t siglen;
        int ret = RSA_sign(NID_sha256, hash, hash_size, signature, (unsigned int *)&siglen, const_cast<RSA *>(rsa));
        free(temp_buffer);
        if(ret != 1)
            return false;
    }
    for(int i = 0; i<SIGNATURE_SIZE; i++)
    {
        (enclave_css->key.signature)[i] = signature[SIGNATURE_SIZE-1-i];
    }

    //************************calculate q1 and q2*********************
    uint8_t modulus[SE_KEY_SIZE];
    for(int i = 0; i<SE_KEY_SIZE; i++)
    {
        modulus[i] = enclave_css->key.modulus[SE_KEY_SIZE-1-i];
    }
    bool res = calc_RSAq1q2(sizeof(enclave_css->key.signature), 
        (const uint8_t *)signature, 
        sizeof(enclave_css->key.modulus), 
        (const uint8_t *)modulus, 
        (uint8_t *)enclave_css->buffer.q1, 
        (uint8_t *)enclave_css->buffer.q2);
    return res;
}

static bool verify_signature(const RSA *rsa, const enclave_css_t *enclave_css)
{
    assert(rsa != NULL && enclave_css != NULL);
    size_t buffer_size = sizeof(enclave_css->header) + sizeof(enclave_css->body);
    uint8_t *temp_buffer = (uint8_t *)malloc(buffer_size * sizeof(char));
    if(NULL == temp_buffer)
    {
        se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
        return false;
    }
    memcpy_s(temp_buffer, buffer_size, &enclave_css->header, sizeof(enclave_css->header));
    memcpy_s(temp_buffer + sizeof(enclave_css->header), buffer_size-sizeof(enclave_css->header), 
        &enclave_css->body, sizeof(enclave_css->body));

    uint8_t hash[SGX_HASH_SIZE] = {0};
    unsigned int hash_size = SGX_HASH_SIZE;
    if(SGX_SUCCESS != sgx_EVP_Digest(EVP_sha256(), temp_buffer, (unsigned int)buffer_size, hash, &hash_size))
    {
        free(temp_buffer);
        return false;
    }
    free(temp_buffer);

    uint8_t signature[SIGNATURE_SIZE];
    for(int i=0; i<SIGNATURE_SIZE; i++)
    {
        signature[i] = enclave_css->key.signature[SIGNATURE_SIZE-1-i];
    }
    if(1 != RSA_verify(NID_sha256, hash, hash_size, signature, SIGNATURE_SIZE, const_cast<RSA *>(rsa)))
    {
        return false;
    }
    return true;
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

static bool cmdline_parse(unsigned int argc, char *argv[], int *mode, const char **path, bool *ignore_rel_error)
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
        {"-review_enclave", NULL, PAR_INVALID},
        {"-dumpfile", NULL, PAR_OPTIONAL}};
    param_struct_t params_gendata[] = {
        {"-enclave", NULL, PAR_REQUIRED},
        {"-config", NULL, PAR_OPTIONAL},
        {"-key", NULL, PAR_INVALID},
        {"-out", NULL, PAR_REQUIRED},
        {"-sig", NULL, PAR_INVALID},
        {"-unsigned", NULL, PAR_INVALID},
        {"-review_enclave", NULL, PAR_INVALID},
        {"-dumpfile", NULL, PAR_INVALID}};
    param_struct_t params_catsig[] = {
        {"-enclave", NULL, PAR_REQUIRED},
        {"-config", NULL, PAR_OPTIONAL},
        {"-key", NULL, PAR_REQUIRED},
        {"-out", NULL, PAR_REQUIRED},
        {"-sig", NULL, PAR_REQUIRED},
        {"-unsigned", NULL, PAR_REQUIRED},
        {"-review_enclave", NULL, PAR_INVALID},
        {"-dumpfile", NULL, PAR_OPTIONAL}};
    param_struct_t params_dump[] = {
        {"-enclave", NULL, PAR_REQUIRED},
        {"-config", NULL, PAR_INVALID},
        {"-key", NULL, PAR_INVALID},
        {"-out", NULL, PAR_INVALID},
        {"-sig", NULL, PAR_INVALID},
        {"-unsigned", NULL, PAR_INVALID},
        {"-review_enclave", NULL, PAR_INVALID},
        {"-dumpfile", NULL, PAR_REQUIRED}};


    const char *mode_m[] ={"sign", "gendata","catsig", "dump"};
    param_struct_t *params[] = {params_sign, params_gendata, params_catsig, params_dump};
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
    unsigned int err_idx = 2;
    for(; err_idx < argc; err_idx++)
    {
        if(!STRCMP(argv[err_idx], "-ignore-rel-error"))
            break;
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
    if(err_idx != argc)
        additional_param++;
    if(argc<params_count_min * 2 + additional_param)
        return false;
    if(argc>params_count_max * 2 + additional_param)
        return false;

    for(unsigned int i=2; i<argc; i=i+2)
    {
        if(i == err_idx)
        {
            i++;
            continue;
        }
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
    if(err_idx != argc)
       *ignore_rel_error = true; 
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
static bool generate_output(int mode, int ktype, const uint8_t *enclave_hash, const RSA *rsa, metadata_t *metadata, 
                            const char **path)
{
    assert(enclave_hash != NULL && metadata != NULL && path != NULL);

    switch(mode)
    {
    case SIGN:
        {
            if(ktype != PRIVATE_KEY || !rsa)
            {
                se_trace(SE_TRACE_ERROR, LACK_PRI_KEY_ERROR);
                return false;
            }

            if(false == fill_enclave_css(rsa, path, enclave_hash, &(metadata->enclave_css)))
            {
                return false;
            }
            if(false == create_signature(rsa, NULL, &(metadata->enclave_css)))
            {
                return false;
            }

            break;
        }
    case GENDATA:
        {
            if(false == fill_enclave_css(NULL, path, enclave_hash, &(metadata->enclave_css)))
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
            if(ktype != PUBLIC_KEY || !rsa)
            {
                se_trace(SE_TRACE_ERROR, LACK_PUB_KEY_ERROR);
                return false;
            }

            if(false == fill_enclave_css(rsa, path, enclave_hash, &(metadata->enclave_css)))
            {
                return false;
            }

            if(false == create_signature(NULL, path[SIG], &(metadata->enclave_css)))
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




#include "se_page_attr.h"
static void metadata_cleanup(metadata_t *metadata, uint32_t size_to_reduce)
{
    metadata->dirs[DIR_LAYOUT].size -= size_to_reduce;
    metadata->size -= size_to_reduce;

    //if there exists LAYOUT_ID_HEAP_MAX, modify it so that it won't be included in the MRENCLAVE
    layout_t *start = GET_PTR(layout_t, metadata, metadata->dirs[DIR_LAYOUT].offset);
    layout_t *end = GET_PTR(layout_t, start, metadata->dirs[DIR_LAYOUT].size);
    for (layout_t *l = start; l < end; l++)
    {
        if (l->entry.id == LAYOUT_ID_HEAP_MAX)
        {
            l->entry.si_flags = SI_FLAG_NONE;
            l->entry.attributes &=  (uint16_t)(~PAGE_ATTR_POST_ADD);
            break;
        }
    }
    //remove the PAGE_ATTR_POST_ADD attribute so that dynamic range won't be
    //created during enclave loading time
    for (layout_t *l = start; l < end; l++)
    {
        if (l->entry.id == LAYOUT_ID_HEAP_INIT)
        {
            l->entry.attributes &=  (uint16_t)(~PAGE_ATTR_POST_ADD);
            break;
        }
    }
}

static bool append_compatible_metadata(metadata_t *compat_metadata, metadata_t *metadata)
{
    metadata_t *dest_meta = metadata;
    uint32_t size = 0;
    do{
        if(dest_meta->magic_num != METADATA_MAGIC || dest_meta->size == 0)
            break;

        size += dest_meta->size;
        if(size < dest_meta->size)
            return false;
        dest_meta = (metadata_t *)((size_t)dest_meta + dest_meta->size);

    } while(size < METADATA_SIZE);

    if(size + compat_metadata->size < size ||
            size + compat_metadata->size < compat_metadata->size ||
            size + compat_metadata->size > METADATA_SIZE)
        return false;

    if(memcpy_s(dest_meta, METADATA_SIZE - size , compat_metadata, compat_metadata->size))
        return false;
    return true;
}

static bool generate_compatible_metadata(metadata_t *metadata)
{
    metadata_t *metadata2 = (metadata_t *)malloc(metadata->size);
    if(!metadata2)
    {
        se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
        return false;
    }
    memcpy(metadata2, metadata, metadata->size);
    metadata2->version = META_DATA_MAKE_VERSION(SGX_1_9_MAJOR_VERSION,SGX_1_9_MINOR_VERSION );
    layout_t *start = GET_PTR(layout_t, metadata2, metadata2->dirs[DIR_LAYOUT].offset);
    layout_t *end = GET_PTR(layout_t, start, metadata2->dirs[DIR_LAYOUT].size);
    layout_t tmp_layout, *first_dyn_entry = NULL, *first = NULL, *utility_td = NULL;
    uint32_t size_to_reduce = 0;
    bool ret = false;

    for (layout_t *l = start; l < end; l++)
    {
        if ((l->entry.id == LAYOUT_ID_STACK_DYN_MAX) ||
            (l->entry.id == LAYOUT_ID_STACK_DYN_MIN))
        {
            first_dyn_entry = l;
            break;
        }
    }

    if (first_dyn_entry == NULL)
    {
        ret = append_compatible_metadata(metadata2, metadata);
        free(metadata2);
        return ret;
    }

    //sizeof(layout_t) for the guard page before LAYOUT_ID_STACK_DYN_MAX
    size_to_reduce = (uint32_t)((size_t)end - (size_t)first_dyn_entry + sizeof(layout_t));

    layout_t *last = &first_dyn_entry[-2];

    for (layout_t *l = start; l <= last; l++)
    {
        if (l->entry.id == LAYOUT_ID_TD)
        {
            utility_td = l;
            break;
        }
    }
    assert(utility_td != NULL);

    //Besides dynamic threads, there's only a single utility thread
    if (utility_td == last)
    {
        metadata_cleanup(metadata2, size_to_reduce);
        ret = append_compatible_metadata(metadata2, metadata);
        free(metadata2);
        return ret;
    }

    //We have some static threads
    first = &utility_td[1];

    //utility thread | thread group for min pool
    if (first == last)
    {
        metadata_cleanup(metadata2, size_to_reduce);
        ret = append_compatible_metadata(metadata2, metadata);
        free(metadata2);
        return ret;
    }

    if (first->group.id == LAYOUT_ID_THREAD_GROUP)
    {
        //utility thread | thread group for min pool | eremove thread | eremove thread group
        if (last->group.id == LAYOUT_ID_THREAD_GROUP)
        {
            first->group.load_times += last->group.load_times + 1;
        }
        //utility thread | thread group for min pool | eremove thread
        else
        {
            first->group.load_times += 1;
        }
        size_to_reduce += (uint32_t)((size_t)last - (size_t)first);
    }
    else
    {
        memset(&tmp_layout, 0, sizeof(tmp_layout));
        tmp_layout.group.id = LAYOUT_ID_THREAD_GROUP;

        //utility thread | eremove thread | eremove thread group
        if (last->group.id == LAYOUT_ID_THREAD_GROUP)
        {
            tmp_layout.group.entry_count = (uint16_t)(((size_t)last - (size_t)first) / sizeof(layout_t));
            tmp_layout.group.load_times = last->group.load_times + 1;
        }
        //utility thread | eremove thread
        else
        {
            tmp_layout.group.entry_count = (uint16_t)(((size_t)last - (size_t)first) / sizeof(layout_t) + 1);
            tmp_layout.group.load_times = 1; 
        }

        for (uint32_t i = 0; i < tmp_layout.group.entry_count; i++) 
        {
            tmp_layout.group.load_step += (((uint64_t)first[i].entry.page_count) << SE_PAGE_SHIFT);
        }
        memcpy_s(first, sizeof(layout_t), &tmp_layout, sizeof(layout_t));
        size_to_reduce += (uint32_t)((size_t)last - (size_t)first);
    }
    
    metadata_cleanup(metadata2, size_to_reduce);
    ret = append_compatible_metadata(metadata2, metadata);
    free(metadata2);
    return ret;
}

static bool dump_enclave_metadata(const char *enclave_path, const char *dumpfile_path)
{
    assert(enclave_path != NULL && dumpfile_path != NULL);
    
    uint64_t meta_offset = 0;
    bin_fmt_t bin_fmt = BF_UNKNOWN;
    uint32_t file_size = 0;

    se_file_handle_t fh = open_file(enclave_path);
    if (fh == THE_INVALID_HANDLE)
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, enclave_path);
        return false;
    }

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
    // Collect enclave info
    if(get_enclave_info(parser.get(), &bin_fmt, &meta_offset, true) == false)
    {
        close_handle(fh);
        return false;
    }
    const metadata_t *metadata = GET_PTR(metadata_t, mh->base_addr, meta_offset); 
    if(print_metadata(dumpfile_path, metadata) == false)
    {
        close_handle(fh);
        remove(dumpfile_path);
        return false;
    }

    close_handle(fh);
    return true;
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
                                   {"TCSNum",0xFFFFFFFF,TCS_NUM_MIN,TCS_NUM_MIN,0},
                                   {"TCSMaxNum",0xFFFFFFFF,TCS_NUM_MIN,TCS_NUM_MIN,0},
                                   {"TCSMinPool",0xFFFFFFFF,0,TCS_NUM_MIN,0},
                                   {"TCSPolicy",TCS_POLICY_UNBIND,TCS_POLICY_BIND,TCS_POLICY_UNBIND,0},
                                   {"StackMaxSize",0x1FFFFFFFFF,STACK_SIZE_MIN,STACK_SIZE_MAX,0},
                                   {"StackMinSize",0x1FFFFFFFFF,STACK_SIZE_MIN,STACK_SIZE_MIN,0},
                                   {"HeapMaxSize",0x1FFFFFFFFF,0,HEAP_SIZE_MAX,0},
                                   {"HeapMinSize",0x1FFFFFFFFF,0,HEAP_SIZE_MIN,0},
                                   {"HeapInitSize",0x1FFFFFFFFF,0,HEAP_SIZE_MIN,0},
                                   {"HeapExecutable",1,0,0,0},
                                   {"MiscSelect", 0xFFFFFFFF, 0, DEFAULT_MISC_SELECT, 0},
                                   {"MiscMask", 0xFFFFFFFF, 0, DEFAULT_MISC_MASK, 0}};

    const char *path[8] = {NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL};
    uint8_t enclave_hash[SGX_HASH_SIZE] = {0};
    uint8_t metadata_raw[METADATA_SIZE];
    metadata_t *metadata = (metadata_t*)metadata_raw;
    int res = -1, mode = -1;
    int key_type = UNIDENTIFIABLE_KEY; //indicate the type of the input key file
    size_t parameter_count = sizeof(parameter)/sizeof(parameter[0]);
    uint64_t meta_offset = 0;
    bool ignore_rel_error = false;
    RSA *rsa = NULL;
    memset(&metadata_raw, 0, sizeof(metadata_raw));

    OpenSSL_add_all_algorithms();
    ERR_load_crypto_strings();


    //Parse command line
    if(cmdline_parse(argc, argv, &mode, path, &ignore_rel_error) == false)
    {
        se_trace(SE_TRACE_ERROR, USAGE_STRING);
        goto clear_return;
    }
    if(mode == -1) // User only wants to get the help info
    {
        res = 0;
        goto clear_return;
    }
    else if(mode == DUMP)
    {
        // dump metadata info
        if(dump_enclave_metadata(path[DLL], path[DUMPFILE]) == false)
        {
            se_trace(SE_TRACE_ERROR, DUMP_METADATA_ERROR, path[DUMPFILE]);
            goto clear_return;
        }
        else
        {
            se_trace(SE_TRACE_ERROR, SUCCESS_EXIT);
            res = 0;
            goto clear_return;
        }
    }
    
    //Other modes
    //
    //Parse the xml file to get the metadata
    if(parse_metadata_file(path[XML], parameter, (int)parameter_count) == false)
    {
        goto clear_return;
    }
    //Parse the key file
    if(parse_key_file(mode, path[KEY], &rsa, &key_type) == false && key_type != NO_KEY) 
    {
        goto clear_return;
    }
    if(copy_file(path[DLL], path[OUTPUT]) == false)
    {
        se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
        goto clear_return;
    }

    ignore_rel_error = true;
    if(measure_enclave(enclave_hash, path[OUTPUT], parameter, ignore_rel_error, metadata, &meta_offset) == false)
    {
        se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
        goto clear_return;
    }
    if((generate_output(mode, key_type, enclave_hash, rsa, metadata, path)) == false)
    {
        se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
        goto clear_return;
    }

    //to verify
    if(mode == SIGN || mode == CATSIG)
    {
        if(verify_signature(rsa, &(metadata->enclave_css)) == false)
        {
            se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
            goto clear_return;
        }
        if(false == generate_compatible_metadata(metadata))
        {
            se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
            goto clear_return;
        }
        if(false == update_metadata(path[OUTPUT], metadata, meta_offset))
        {
            se_trace(SE_TRACE_ERROR, OVERALL_ERROR);
            goto clear_return;
        }
    }
    
    if(path[DUMPFILE] != NULL)
    {
        if(print_metadata(path[DUMPFILE], metadata) == false)
        {
            se_trace(SE_TRACE_ERROR, DUMP_METADATA_ERROR, path[DUMPFILE]);
            goto clear_return;
        }
    }
    se_trace(SE_TRACE_ERROR, SUCCESS_EXIT);
    res = 0;

clear_return:
    if(rsa)
        RSA_free(rsa);
    if(res == -1 && path[OUTPUT])
        remove(path[OUTPUT]);
    if(res == -1 && path[DUMPFILE])
        remove(path[DUMPFILE]);
    
    EVP_cleanup();
    CRYPTO_cleanup_all_ex_data();
    ERR_remove_thread_state(NULL);
    ERR_free_strings();
    
    return res;
}
