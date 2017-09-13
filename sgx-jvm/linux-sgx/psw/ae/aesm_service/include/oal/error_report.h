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

#ifndef __OAL_ERROR_REPORT_H__
#define __OAL_ERROR_REPORT_H__
#define AESM_LOG_REPORT_FATAL   0 
#define AESM_LOG_REPORT_ERROR   1
#define AESM_LOG_REPORT_WARNING 2
#define AESM_LOG_REPORT_INFO    3
#include "event_strings.h"
#ifdef __cplusplus
extern "C" {
#endif/*__cplusplus*/
    void aesm_log_report(int level, const char *format, ...);
    void aesm_log_init(void);
    void aesm_log_fini(void);
#ifdef __cplusplus
};
#endif/*__cplusplus*/

#define AESM_LOG_FATAL(format, args...) aesm_log_report(AESM_LOG_REPORT_FATAL, format, ## args)
#define AESM_LOG_ERROR(format, args...) aesm_log_report(AESM_LOG_REPORT_ERROR, format, ## args)
#define AESM_LOG_WARN(format, args...)  aesm_log_report(AESM_LOG_REPORT_WARNING, format, ## args)
#define AESM_LOG_INFO(format, args...)  aesm_log_report(AESM_LOG_REPORT_INFO, format, ## args)
#define AESM_LOG_FATAL_ADMIN(format, args...) aesm_log_report(AESM_LOG_REPORT_FATAL, "[ADMIN]" format, ## args)
#define AESM_LOG_ERROR_ADMIN(format, args...) aesm_log_report(AESM_LOG_REPORT_ERROR, "[ADMIN]" format, ## args)
#define AESM_LOG_WARN_ADMIN(format, args...) aesm_log_report(AESM_LOG_REPORT_WARNING, "[ADMIN]" format, ## args)
#define AESM_LOG_INFO_ADMIN(format, args...) aesm_log_report(AESM_LOG_REPORT_INFO, "[ADMIN]" format, ## args)
#define AESM_LOG_INIT() aesm_log_init()
#define AESM_LOG_FINI() aesm_log_fini()
#define AESM_LOG_ERROR_UNICODE AESM_LOG_ERROR

#endif/*__OAL_ERROR_REPORT_H__*/

