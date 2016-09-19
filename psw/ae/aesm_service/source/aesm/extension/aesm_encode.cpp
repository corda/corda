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


#include "aesm_encode.h"
#include "se_memcpy.h"
#include "AEClass.h"
#include "PVEClass.h"
#include "tlv_common.h"
#include <openssl/evp.h>
#include <openssl/bio.h>
#include <openssl/buffer.h>
#include <openssl/x509v3.h>
#include <list>
/**
* Method converts byte containing value from 0x00-0x0F into its corresponding ASCII code, 
* e.g. converts 0x00 to '0', 0x0A to 'A'. 
* Note: This is mainly a helper method for internal use in byte_array_to_hex_string().
*
* @param in byte to be converted (allowed values: 0x00-0x0F)
*
* @return ASCII code representation of the byte or 0 if method failed (e.g input value was not in provided range).
*/
static uint8_t convert_value_to_ascii(uint8_t in)
{
    if(in <= 0x09)
    {
        return (uint8_t)(in + '0');
    }
    else if(in <= 0x0F)
    {
        return (uint8_t)(in - 10 + 'A');
    }

    return 0;
}

/**
* Method converts char containing ASCII code into its corresponding value, 
* e.g. converts '0' to 0x00, 'A' to 0x0A. 
*
* @param in char containing ASCII code (allowed values: '0-9', 'a-f', 'A-F')
* @param val output parameter containing converted value, if method succeeds.
*
* @return true if conversion succeeds, false otherwise
*/
static bool convert_ascii_to_value(uint8_t in, uint8_t& val)
{
    if(in >= '0' && in <= '9')
    {
        val = static_cast<uint8_t>(in - '0');
    }
    else if(in >= 'A' && in <= 'F')
    {
        val = static_cast<uint8_t>(in - 'A'+10);
    }
    else if(in >= 'a' && in <= 'f')
    {
        val = static_cast<uint8_t>(in - 'a'+10);
    }
    else
    {
        return false;
    }

    return true;
}

//Function to do HEX encoding of array of bytes
//@param in_buf, bytes array whose length is in_size
//       out_buf, output the HEX encoding of in_buf on success. 
//@return true on success and false on error
//The out_size must always be 2*in_size since each byte into encoded by 2 characters
static bool byte_array_to_hex_string(const uint8_t *in_buf, uint32_t in_size, uint8_t *out_buf, uint32_t out_size)
{
    if(in_size>UINT32_MAX/2)return false;
    if(in_buf==NULL||out_buf==NULL|| out_size!=in_size*2 )return false;

    for(uint32_t i=0; i< in_size; i++)
    {
        *out_buf++ = convert_value_to_ascii( static_cast<uint8_t>(*in_buf >> 4));
        *out_buf++ = convert_value_to_ascii( static_cast<uint8_t>(*in_buf & 0xf));
        in_buf++;
    }
    return true;
}

//Function to do HEX decoding
//@param in_buf, character strings which are HEX encoding of a byte array
//       out_buf, output the decode byte array on success
//@return true on success and false on error
//The in_size must be even number and equals 2*out_size
static bool hex_string_to_byte_array(const uint8_t *in_buf, uint32_t in_size, uint8_t *out_buf, uint32_t out_size)
{
    if(out_size>UINT32_MAX/2)return false;
    if(in_buf==NULL||out_buf==NULL||out_size*2!=in_size)return false;

    for(uint32_t i=0;i<out_size;i++)
    {
        uint8_t value_first, value_second;
        if(!convert_ascii_to_value(in_buf[i*2], value_first))
            return false;
        if(!convert_ascii_to_value(in_buf[i*2+1], value_second))
            return false;
        out_buf[i] = static_cast<uint8_t>(value_second+ (value_first<<4));
    }
    return true;
}

//Function to use openssl to do BASE64 encoding
static bool base_64_encode(const uint8_t *in_buf, uint32_t in_size, uint8_t *out_buf, uint32_t *out_size)
{
    BIO* bioMem = NULL;
    bool ret = false;
    BIO *bio64 = NULL;
    BIO_METHOD *bm = BIO_f_base64();
    if(bm == NULL)
        goto ret_point;
    bio64 = BIO_new(bm);
    if(bio64 == NULL) 
        goto ret_point;
    BIO_set_flags(bio64, BIO_FLAGS_BASE64_NO_NL);
    bm = BIO_s_mem();
    if(bm == NULL)
        goto ret_point;
    bioMem = BIO_new(bm);
    if(bioMem == NULL)
        goto ret_point;
   (void)BIO_push(bio64, bioMem);

    if(BIO_write(bio64, in_buf, in_size) != (int)in_size){
        goto ret_point;
    }
    (void)BIO_flush(bio64);

    BUF_MEM *bptr;
    BIO_get_mem_ptr(bio64, &bptr);
    if(bptr==NULL){
        goto ret_point;
    }
    if(*out_size < bptr->length){
        goto ret_point;
    }
    if(memcpy_s(out_buf, *out_size,bptr->data, bptr->length)!=0)
        goto ret_point;
        
    *out_size = static_cast<uint32_t>(bptr->length);
    ret = true;
ret_point:
    BIO_free_all(bio64);//we need not free bioMem too because the free_all has free it.
    return ret;
}

//Function to use openssl to do BASE64 decoding
static bool base_64_decode(const uint8_t *in_buf, uint32_t in_size, uint8_t *out_buf, uint32_t *out_size)
{
  BIO *b64 =NULL, *bmem = NULL;
  bool ret = false;
  int read=0;
  memset(out_buf, 0, *out_size);
  BIO_METHOD *bm = BIO_f_base64();
  if(bm == NULL)
      goto ret_point;
  b64 = BIO_new(bm);
  if(b64 == NULL)
      goto ret_point;
  BIO_set_flags(b64, BIO_FLAGS_BASE64_NO_NL);
  bmem = BIO_new_mem_buf(const_cast<uint8_t *>(in_buf), in_size);
  if(bmem == NULL){
      goto ret_point;
  }
  bmem = BIO_push(b64, bmem);
  read = BIO_read(bmem, out_buf, *out_size);
  if(read < 0)
      goto ret_point;
  *out_size = read;
  ret = true;
ret_point:
  BIO_free_all(bmem);
  return ret;
}

//Function to give an upper bound of size of data after BASE64 decoding
//@param length: the length in bytes of BASE64 encoded data
//@return an upper bound of length in bytes of decoded data
static uint32_t get_unbase_64_length(uint32_t length)
{
    return (length * 3 / 4) + ((length * 3 % 4 > 0) ? 1 : 0 );
}

//Function to give an upper bound of size of data after BASR64 encoding
//@param length: the length in bytes of data to be encoded
//@return an upper bound of length in bytes of data after encoding
static uint32_t get_base_64_length_upbound(uint32_t length)
{
    uint32_t extra = (length+9)/10+50;//using enough extra memory
    return extra+(length*4+2)/3;
}

uint32_t get_request_encoding_length(const uint8_t *req)
{
    //adding 1 extra byte to hold '\0'
    return static_cast<uint32_t>(2*PROVISION_REQUEST_HEADER_SIZE+get_base_64_length_upbound(GET_BODY_SIZE_FROM_PROVISION_REQUEST(req))+1);
}

uint32_t get_response_decoding_length(uint32_t buf_len)
{
    if(buf_len<2*PROVISION_RESPONSE_HEADER_SIZE)
        return 0;
    return static_cast<uint32_t>(get_unbase_64_length(buf_len-2*static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE)) + PROVISION_RESPONSE_HEADER_SIZE);
}

bool encode_request(const uint8_t *req, uint32_t req_len, uint8_t *out_buf, uint32_t *out_len)
{
    uint32_t encoded_header_size = static_cast<uint32_t>(2*PROVISION_REQUEST_HEADER_SIZE);
    if(*out_len<encoded_header_size)
        return false;
    if(req_len<PROVISION_REQUEST_HEADER_SIZE)
        return false;
    if(!byte_array_to_hex_string(req, PROVISION_REQUEST_HEADER_SIZE, out_buf, encoded_header_size))
        return false;
    uint32_t left_size = *out_len - encoded_header_size;
    if(req_len != GET_SIZE_FROM_PROVISION_REQUEST(req))
        return false;//error in input message
    if(!base_64_encode(req+PROVISION_REQUEST_HEADER_SIZE, GET_BODY_SIZE_FROM_PROVISION_REQUEST(req), out_buf + encoded_header_size, &left_size))
        return false;
    *out_len = left_size + encoded_header_size;
    return true;
}

bool decode_response(const uint8_t *input_buf, uint32_t input_len, uint8_t *resp, uint32_t *out_len)
{
    if(input_len < 2*PROVISION_RESPONSE_HEADER_SIZE)
        return false;
    if(*out_len < PROVISION_RESPONSE_HEADER_SIZE)
        return false;
    if(!hex_string_to_byte_array(input_buf, static_cast<uint32_t>(2*PROVISION_RESPONSE_HEADER_SIZE), resp, static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE)))
        return false;
    if(*out_len<GET_SIZE_FROM_PROVISION_RESPONSE(resp))
        return false;
    *out_len -= static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE);
    if(!base_64_decode(input_buf+static_cast<uint32_t>(2*PROVISION_RESPONSE_HEADER_SIZE), input_len - static_cast<uint32_t>(2*PROVISION_RESPONSE_HEADER_SIZE),
        resp+static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE), out_len))
        return false;
    *out_len += static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE);
    if(*out_len != GET_SIZE_FROM_PROVISION_RESPONSE(resp))
        return false;
    return true;
}

