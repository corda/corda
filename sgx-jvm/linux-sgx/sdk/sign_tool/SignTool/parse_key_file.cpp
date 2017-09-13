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

/**
* File:
*     parse_key_file.cpp
* Description:
*     Parse the RSA key file that user inputs
* to get the key type and rsa structure.
*/

#include "parse_key_file.h"
#include "se_trace.h"
#include "arch.h"
#include "util_st.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <limits.h>

#include <string>
#include <algorithm>
#include <fstream>


//N_SIZE+E_SIZE+D_SIZE+P_SIZE+Q_SIZE+DMP1_SIZE+DMQ1_SIZE+sizeof(inverseQ)
#define PRI_COMPONENTS_SIZE  (N_SIZE_IN_BYTES + E_SIZE_IN_BYTES + D_SIZE_IN_BYTES + P_SIZE_IN_BYTES *5)
#define PUB_CONPONENTS_SIZE  (N_SIZE_IN_BYTES + E_SIZE_IN_BYTES) //N_SIZE+E_SIZE
#define SEQUENCE_TAG_VALUE    0x30
#define INTEGER_TAG_VALUE     0x02
#define BIT_STRING_TAG_VALUE  0x03
#define NULL_TAG_VALUE        0x05
#define OID_TAG_VALUE         0x06


#define     CHECK_RETRUN(value)     {if(0 == (value)) return 0;}


//base64Decode
//     to decode the base64 string and put it to the result.
//Parameters
//     [IN]  aSrc: the source string coded in base64
//           srcLen: length of the source string
//     [OUT] result: point to the decoded string
//Return Value
//     int---The length of the decoded string
static int base64_decode(const unsigned char* aSrc, size_t srcLen, unsigned char* result)
{ //two reason will cause the function return 0: 1- The input parameters are wrong, 2- srcLen<4
    static char   index_64[256]   =   {
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   62,   64,   64,   64,   63,
        52,   53,   54,   55,   56,   57,   58,   59,   60,   61,   64,   64,   64,   64,   64,   64,
        64,   0,    1,    2,    3,    4,    5,    6,    7,    8,    9,    10,   11,   12,   13,   14,
        15,   16,   17,   18,   19,   20,   21,   22,   23,   24,   25,   64,   64,   64,   64,   64,
        64,   26,   27,   28,   29,   30,   31,   32,   33,   34,   35,   36,   37,   38,   39,   40,
        41,   42,   43,   44,   45,   46,   47,   48,   49,   50,   51,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,
        64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64,   64
    };

    CHECK_RETRUN(aSrc);
    CHECK_RETRUN(srcLen);
    CHECK_RETRUN(result);


    unsigned char ch1 = 0, ch2 = 0, ch3 = 0, ch4 = 0;
    unsigned char *ptr = result;

    for (unsigned int i = 0; i < srcLen; ++i)
    {
        ch1 = index_64[aSrc[i]];
        if(ch1 == 64)
            continue;
        ch2 = index_64[aSrc[++i]];
        if(ch2 == 64)
            continue;
        *(ptr++) = (unsigned char)(ch1<<2 | ch2>>4);
        ch3 = index_64[aSrc[++i]];
        if(aSrc[i] == '=' || ch3 == 64)
            continue;
        *(ptr++) = (unsigned char)(ch2<<4 | ch3>>2);
        ch4 = index_64[aSrc[++i]];
        if(aSrc[i] == '=' || ch4 == 64)
            continue;
        *(ptr++) = (unsigned char)(ch3<<6 | ch4);
    }
    return (int)(ptr - result);
}

static void convert_string(unsigned char *str, int len)
{
    assert(str != NULL&&len>0);
    char temp = 0;
    for(int i=0; i<len/2; i++)
    {
        temp = str[i];
        str[i] = str[len-1-i];
        str[len-1-i] = temp;
    }
}

static bool parse_tag_and_length(const unsigned char *begin, const unsigned char *end, uint8_t expect_tag, size_t *len_bytes, size_t *value_bytes)
{
    assert(NULL != begin && NULL != end && NULL != len_bytes && NULL != value_bytes);
    if (begin[0] != expect_tag)
        return false;
    size_t tvb = 0, tlb = 0; // 'temporary value bytes' and 'temporary length bytes'
    const unsigned char *lbegin = begin + 1;
    if (!(lbegin[0] & 0x80))
    {
        // Value bytes <= 127
        tvb = lbegin[0];
        tlb += 1; // length is only one bytes
    }
    else if (lbegin[0] == 0x81)
    {
        tlb += (lbegin[0] & 0x7F) + 1; // + 1byte Length + 1byte to contain the value bytes
        if (tlb != 2)
            return false;
        tvb = lbegin[1];
    }
    else if (lbegin[0] == 0x82)
    {
        tlb += (lbegin[0] & 0x7F) + 1; // + 1byte Length + 2bytes to contain the value bytes
        if (tlb != 3)
            return false;
        tvb = (lbegin[1] << 8) + lbegin[2];
    }
    else
    {
        // Only the 3072bits RSA key is acceptable, for which we only need 2bytes to store the value bytes.
        // Therefore, return failure if the length of the value bytes is greater than 2bytes.
        return false;
    }

    if ((tlb < UINT_MAX - tvb) && (size_t)(lbegin + tvb + tlb) > tvb + tlb)
    {
        // In the input begin/end, the key_header/end has been removed. 
        // Therefore, we should not check the entire key length.
        if (expect_tag != SEQUENCE_TAG_VALUE && lbegin + tvb + tlb > end)
            return false;
        else
        {
            *value_bytes = tvb;
            *len_bytes = tlb + 1; // + size of tag
            return true;
        }
    }
    return false;
}

static bool parse_tlv_integer(const unsigned char **begin, const unsigned char *end, unsigned int *values, size_t values_length)
{
    assert(NULL != begin && NULL != *begin && NULL != end && NULL != values && values_length > 0);
    size_t value_bytes = 0, len_bytes = 0;
    const unsigned char *psrc = *begin;
    if (parse_tag_and_length(psrc, end, INTEGER_TAG_VALUE, &len_bytes, &value_bytes) == false)
        return false;
    psrc += len_bytes;

    if (value_bytes < values_length)
        return false;

    else if (value_bytes > values_length)
    {
        for (unsigned int i = 0; i < value_bytes - values_length; i++)
        {
            if (*(psrc + i) != 0x00)
                return false;
        }
        // There are some padding 0x00s which need to skip
        psrc += value_bytes - values_length;
    }

    if (values != NULL)
    {
        memcpy_s(values, values_length, psrc, values_length);
    }
    psrc += values_length;
    *begin = psrc;

    return true;
}

static bool convert_from_pri_key(const unsigned char *psrc, unsigned int slen, rsa_params_t *rsa)
{
    assert(NULL != psrc && NULL != rsa);
    if(slen<PRI_COMPONENTS_SIZE)
    {
        return false;
    }

    size_t value_bytes = 0, len_bytes = 0;
    const unsigned char *end = psrc + slen;
    if (parse_tag_and_length(psrc, end, SEQUENCE_TAG_VALUE, &len_bytes, &value_bytes) == false)
    {
        return false;
    }
    psrc += len_bytes;
    // Version
    if (parse_tag_and_length(psrc, end, INTEGER_TAG_VALUE, &len_bytes, &value_bytes) == false)
    {
        return false;
    }
    psrc += len_bytes;
    if (value_bytes != 0x01 || *psrc != 0x00) // Version should be 0x00
    {
        return false;
    }
    psrc += value_bytes;

    memset(rsa, 0, sizeof(rsa_params_t));
    // N
    if (parse_tlv_integer(&psrc, end, rsa->n, N_SIZE_IN_BYTES) == false)
    {
        return false;
    }
    convert_string((unsigned char *)rsa->n, sizeof(rsa->n));

    // E
    if (parse_tlv_integer(&psrc, end, rsa->e, E_SIZE_IN_UINT) == false)
    {
        return false;
    }
    if (rsa->e[0] != 0x03)
    {
        se_trace(SE_TRACE_ERROR, "Only '3' is accepted as the Exponent value.\n");
        return false;
    }

    // D
    if (parse_tlv_integer(&psrc, end, rsa->d, D_SIZE_IN_BYTES) == false)
    {
        return false;
    }
    convert_string((unsigned char *)rsa->d, sizeof(rsa->d));

    // P
    if (parse_tlv_integer(&psrc, end, rsa->p, P_SIZE_IN_BYTES) == false)
    {
        return false;
    }
    convert_string((unsigned char *)rsa->p, sizeof(rsa->p));
    // Q
    if (parse_tlv_integer(&psrc, end, rsa->q, Q_SIZE_IN_BYTES) == false)
    {
        return false;
    }
    convert_string((unsigned char *)rsa->q, sizeof(rsa->q));
    // DMP1
    if (parse_tlv_integer(&psrc, end, rsa->dmp1, DMP1_SIZE_IN_BYTES) == false)
    {
        return false;
    }
    convert_string((unsigned char *)rsa->dmp1, sizeof(rsa->dmp1));
    // DMQ1
    if (parse_tlv_integer(&psrc, end, rsa->dmq1, DMQ1_SIZE_IN_BYTES) == false)
    {
        return false;
    }
    convert_string((unsigned char *)rsa->dmq1, sizeof(rsa->dmq1));
    // IQMP
    if (parse_tlv_integer(&psrc, end, rsa->iqmp, IQMP_SIZE_IN_BYTES) == false)
    {
        return false;
    }
    convert_string((unsigned char *)rsa->iqmp, sizeof(rsa->iqmp));
    return true;
}

static bool  convert_from_pub_key(const unsigned char *psrc, unsigned int slen, rsa_params_t *rsa)
{
    assert(NULL != psrc && NULL != rsa);
    if(slen<PUB_CONPONENTS_SIZE)
    {
        return false;
    }
    const unsigned char *end = psrc + slen;
    size_t len_bytes = 0, value_bytes = 0;

    unsigned char OID_str[] = { 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01 };
    // SEQUENCE
    if (parse_tag_and_length(psrc, end, SEQUENCE_TAG_VALUE, &len_bytes, &value_bytes) == false)
    {
        return false;
    }
    psrc += len_bytes;
    // SEQUENCE
    if (parse_tag_and_length(psrc, end, SEQUENCE_TAG_VALUE, &len_bytes, &value_bytes) == false)
        return false;
    psrc += len_bytes;
    // OBJECT_ID
    if (parse_tag_and_length(psrc, end, OID_TAG_VALUE, &len_bytes, &value_bytes) == false)
        return false;
    psrc += len_bytes;
    if (value_bytes != sizeof(OID_str) || memcmp(psrc, OID_str, sizeof(OID_str)) != 0)
        return false;
    psrc += value_bytes;
    // NULL
    if (parse_tag_and_length(psrc, end, NULL_TAG_VALUE, &len_bytes, &value_bytes) == false || value_bytes != 0)
        return false;
    psrc += len_bytes;
    // BIT STRING
    if (parse_tag_and_length(psrc, end, BIT_STRING_TAG_VALUE, &len_bytes, &value_bytes) == false)
        return false;
    psrc += len_bytes;
    if (*psrc == 0) // Specifies the number of unused bits that exist in the last content byte
        psrc++;
    // SEQUENCE
    if (parse_tag_and_length(psrc, end, SEQUENCE_TAG_VALUE, &len_bytes, &value_bytes) == false)
        return false;
    psrc += len_bytes;
    memset(rsa, 0, sizeof(rsa_params_t));
    // N
    if (parse_tlv_integer(&psrc, end, rsa->n, N_SIZE_IN_BYTES) == false)
    {
        return false;
    }
    convert_string((unsigned char *)rsa->n, sizeof(rsa->n));
    // E
    if (parse_tlv_integer(&psrc, end, rsa->e, E_SIZE_IN_UINT) == false)
    {
        return false;
    }
    if (rsa->e[0] != 0x03)
    {
        se_trace(SE_TRACE_ERROR, "Only '3' is accepted as the Exponent value.\n");
        return false;
    }
    return true;
}

static unsigned char* decode_key_body(unsigned char *buffer, size_t slen, int *key_type, int *rlen)
{
    assert(buffer!=NULL && key_type!=NULL && rlen!=NULL);

    const char pri_key_header[] = "-----BEGINRSAPRIVATEKEY-----" ;
    const char pri_key_end[] = "-----ENDRSAPRIVATEKEY-----\n";
    const char pub_key_header[] = "-----BEGINPUBLICKEY-----";
    const char pub_key_end[] = "-----ENDPUBLICKEY-----\n";

    int ktype = UNIDENTIFIABLE_KEY;
    int offset_pri = (int)(slen - strlen(pri_key_end));
    int offset_pub = (int)(slen - strlen(pub_key_end));

    if(offset_pub<=0 || offset_pri<=0)
    {
        se_trace(SE_TRACE_ERROR, KEY_FORMAT_ERROR);
        *key_type = UNIDENTIFIABLE_KEY;
        return NULL;
    }

    //check the file header and footer to get the key type
    if(!strncmp((const char *)buffer, pri_key_header, strlen(pri_key_header)))
    {
        //make sure the key file isn't an emcrypted PEM private key file.
        if((!strncmp((const char *)(buffer+offset_pri), pri_key_end, strlen(pri_key_end))) &&
            !strstr((const char *)buffer, "Proc-Type: 4, ENCRYPTED"))
        {
            *(buffer+offset_pri-1) = '\0'; //remove the pri_key_end string
            ktype = PRIVATE_KEY;
        }
        else
        {
            ktype = UNIDENTIFIABLE_KEY;
        }
    }
    else if(!strncmp((const char *)buffer, pub_key_header, strlen(pub_key_header)))
    {
        if(!strncmp((const char *)(buffer+offset_pub), pub_key_end, strlen(pub_key_end)))
        {
            *(buffer + offset_pub-1) = '\0';
            ktype = PUBLIC_KEY;
        }
        else
        {
            ktype = UNIDENTIFIABLE_KEY;
        }
    }
    else
    {
        ktype = UNIDENTIFIABLE_KEY;
    }
    //get the body contents of the key file
    size_t body_size = 0, body_offset = 0;
    if(ktype == PRIVATE_KEY)
    {
        body_size = strlen((const char *)buffer) - strlen(pri_key_header);
        body_offset = strlen(pri_key_header)+1;
    }
    else if(ktype == PUBLIC_KEY)
    {
        body_size = strlen((const char *)buffer) - strlen(pub_key_header);
        body_offset = strlen(pub_key_header)+1;
    }
    else
    {
        se_trace(SE_TRACE_ERROR, KEY_FORMAT_ERROR);
        *key_type = ktype;
        return NULL;
    }
    unsigned char *decoded_string = (unsigned char *)malloc(sizeof(char)*body_size);
    if(!decoded_string)
    {
        se_trace(SE_TRACE_ERROR, NO_MEMORY_ERROR);
        *key_type = ktype;
        return NULL;
    }
    memset(decoded_string, 0, body_size);
    int retlen = base64_decode(buffer+body_offset, body_size, decoded_string);
    if(retlen == 0)
    {
        se_trace(SE_TRACE_ERROR, KEY_FORMAT_ERROR);
        *key_type = ktype;
        free(decoded_string);
        return NULL;
    }
    *key_type = ktype;
    *rlen = retlen;
    return decoded_string;
}

//read_key_file
//     read the input file line by line and trim the blank characters for each line
//Parameters
//     [IN] key_path: the file required to be read
static std::string read_key_file(const char *key_path)
{
    assert(key_path != NULL);

   std::ifstream ifs(key_path, std::ios::in | std::ios::binary);
   if(!ifs.good())
   {
       se_trace(SE_TRACE_ERROR, READ_FILE_ERROR, key_path);
       return "";
   }
   std::string file_content;
   std::string str;
   while(std::getline(ifs, str))
   {
       str.erase(std::remove_if(str.begin(), str.end(), ::isspace), str.end());
       if(str.length() != 0)
       {
           file_content += str;
           file_content += "\n"; // Add '\n' for each line
       }
   }
   ifs.close();
   return file_content;
}

//parse_key_file():
//       parse the RSA key file
//Parameters:
//      [IN] key_path: the key file name user inputs
//      [OUT] prsa: the rsa structure parsed from the key file
//            pkey_type: the key type
//Return Value:
//      true: success
//      false: fail
bool parse_key_file(const char *key_path, rsa_params_t *prsa, int *pkey_type)
{
    assert(prsa != NULL && pkey_type != NULL);

    if(key_path == NULL)
    {
        *pkey_type = NO_KEY;
        return false;
    }

    //read and trim the file content
    std::string file_content = read_key_file(key_path);
    if(file_content.empty() == true)
    {
        *pkey_type = UNIDENTIFIABLE_KEY;
        return false;
    }
    const unsigned char *buffer = (const unsigned char *)file_content.c_str();

    //decode the buffer to decoded_string
    size_t result = strlen((const char *)buffer);
    int retlen = 0;
    int key_type = UNIDENTIFIABLE_KEY;
    unsigned char *decoded_string = decode_key_body(const_cast<unsigned char*>(buffer), result, &key_type, &retlen);
    if(!decoded_string)
    {        
        *pkey_type = key_type;
        return false;
    }

    //get RSA from the decoded string
    bool ret = false;
    if(key_type == PRIVATE_KEY)
    {
        ret = convert_from_pri_key(decoded_string, retlen, prsa);
    }
    else
    {
        ret = convert_from_pub_key(decoded_string, retlen, prsa);
    }
    if(ret == false)
    {
        se_trace(SE_TRACE_ERROR, KEY_FORMAT_ERROR);
        free(decoded_string);
        *pkey_type = key_type;
        return false;
    }
    else
    {
        se_trace(SE_TRACE_DEBUG, "Parsing key file is OK.\n");
    }

    *pkey_type = key_type;
    free(decoded_string);
    return true;
}
