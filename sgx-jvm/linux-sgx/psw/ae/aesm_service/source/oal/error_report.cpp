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

#include <syslog.h>
#include <stdarg.h>
#include "oal/error_report.h"

void aesm_log_init(void)
{
    openlog("aesm_service", LOG_CONS|LOG_PID, LOG_USER);
}

void aesm_log_fini(void)
{
    closelog();
}

void aesm_log_report(int level, const char *format, ...)
{
    int priority = 0;
    va_list ap;
    va_start(ap, format);
    switch(level){
    case AESM_LOG_REPORT_FATAL:
       priority = LOG_CRIT;
       break;
    case AESM_LOG_REPORT_ERROR:
       priority = LOG_ERR;
       break;
   case AESM_LOG_REPORT_WARNING:
       priority = LOG_WARNING;
       break;
   case AESM_LOG_REPORT_INFO:
       priority = LOG_INFO;
       break;
   default:
      return;//ignore
    }
    vsyslog(priority, format, ap);
    va_end(ap);
}

