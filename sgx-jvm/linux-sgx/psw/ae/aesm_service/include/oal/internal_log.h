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

#ifndef __OAL_INTERNAL_LOG_H__
#define __OAL_INTERNAL_LOG_H__
#ifdef DBG_LOG
#include <stdint.h>
#define FATAL_LOG_LEVEL  0 /*report critical internal error*/
#define ERROR_LOG_LEVEL  1 /*report internal error message*/
#define WARN_LOG_LEVEL   2 /*report internal warning messages*/
#define INFO_LOG_LEVEL   3 /*aesm high level trace information, usually information for each components*/
#define DEBUG_LOG_LEVEL  4 /*aesm middle level trace information, usually information for each function*/
#define TRACE_LOG_LEVEL  5 /*aesm low level trace information, usually inside a control flow*/
#ifdef __cplusplus
extern "C" {
#endif/*__cplusplus*/
    void aesm_internal_log(const char *filename, int line_no, const char *funname, int level, const char *format, ...);
    void aesm_set_log_level(int level);
    void aesm_dbg_format_hex(const uint8_t *data, uint32_t data_len, char *out_buf, uint32_t buf_size);
    int enter_module(int module_index);
    void leave_module(int module_index);
#ifdef __cplusplus
};
#endif/*__cplusplus*/

#define AESM_DBG_FATAL(format, args...) aesm_internal_log(__FILE__, __LINE__, __FUNCTION__, FATAL_LOG_LEVEL, format, ## args)
#define AESM_DBG_ERROR(format, args...) aesm_internal_log(__FILE__, __LINE__, __FUNCTION__, ERROR_LOG_LEVEL, format, ## args)
#define AESM_DBG_WARN(format, args...)  aesm_internal_log(__FILE__, __LINE__, __FUNCTION__, WARN_LOG_LEVEL, format, ## args)
#define AESM_DBG_INFO(format, args...)  aesm_internal_log(__FILE__, __LINE__, __FUNCTION__, INFO_LOG_LEVEL, format, ## args)
#define AESM_DBG_DEBUG(format, args...) aesm_internal_log(__FILE__, __LINE__, __FUNCTION__, DEBUG_LOG_LEVEL, format, ## args)
#define AESM_DBG_TRACE(format, args...) aesm_internal_log(__FILE__, __LINE__, __FUNCTION__, TRACE_LOG_LEVEL, format, ## args)

#define AESM_SET_DBG_LEVEL(level) aesm_set_log_level(level)
#else
#define AESM_DBG_FATAL(args...)
#define AESM_DBG_ERROR(args...)
#define AESM_DBG_WARN(args...)
#define AESM_DBG_INFO(args...)
#define AESM_DBG_DEBUG(args...)
#define AESM_DBG_TRACE(args...)
#define AESM_SET_DBG_LEVEL(level)
#endif/*DBG_LOG*/

#define SGX_DBGPRINT_ONE_STRING_ONE_INT(x,y)                AESM_DBG_WARN("%s %d", x, y)
#define SGX_DBGPRINT_ONE_STRING_ONE_INT_LTP(x,y)            AESM_DBG_WARN("LTP: %s %d", x, y)
#define SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(x,y)      AESM_DBG_WARN("%s returned %d",x,y)
#define SGX_DBGPRINT_PRINT_TWO_STRINGS(x,y)                 AESM_DBG_WARN("%s %s",x, y)
#define SGX_DBGPRINT_PRINT_TWO_STRINGS_ONE_INT(x,y,z)       AESM_DBG_WARN("%s %s %d", x,y,z)
#define SGX_DBGPRINT_PRINT_FIVE_STRINGS(a,b,c,d,e)          AESM_DBG_WARN("%s %s %s %s %s",a,b,c,d,e)
#define SGX_DBGPRINT_PRINT_ANSI_STRING(x)                   AESM_DBG_WARN("%s",x)
#define SGX_DBGPRINT_PRINT_STRING_LTP(x)                    AESM_DBG_WARN("%s",x)
#define SGX_DBGPRINT_PRINT_STRING(x)                        AESM_DBG_WARN("%s",x)

#define SGX_DBGPRINT_ONE_STRING_ONE_INT_CERT(x,y)           AESM_DBG_WARN("cert: %s %d", x, y)
#define SGX_DBGPRINT_ONE_STRING_ONE_INT_OCSP(x,y)           AESM_DBG_WARN("OCSP: %s %d", x, y)

#define SGX_DBGPRINT_ONE_STRING_TWO_INTS_EPH(x,y,z)
#define SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(x,y,z)
#define SGX_DBGPRINT_ONE_STRING_TWO_INTS_ENDPOINT_SELECTION(x,y,z)

#endif/*__OAL_INTERNAL_LOG_H__*/

