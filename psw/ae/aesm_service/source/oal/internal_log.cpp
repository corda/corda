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


#ifdef DBG_LOG
#include "oal/oal.h"
#include "se_wrapper.h"
#include "se_stdio.h"
#include <time.h>
#include <stdio.h>
#include <stdarg.h>
#include <assert.h>
#include "se_thread.h"
static int aesm_trace_level = TRACE_LOG_LEVEL;
static int at_start=1;

ae_error_t load_log_config(void);
se_mutex_t cs;
static ae_error_t init_log_file(void)
{
    char filename[MAX_PATH];
    ae_error_t err = aesm_get_pathname(FT_PERSISTENT_STORAGE, AESM_DBG_LOG_FID, filename, MAX_PATH);
    if(err != AE_SUCCESS)
        return err;
    (void)load_log_config();
    return AE_SUCCESS;
}

#define TIME_BUF_SIZE 100
void aesm_internal_log(const char *file_name, int line_no, const char *funname, int level, const char *format, ...)
{
    if(level <= aesm_trace_level){
        if(at_start){
            at_start=0;
            se_mutex_init(&cs);
            init_log_file();
        }
        char filename[MAX_PATH];
        ae_error_t err = aesm_get_cpathname(FT_PERSISTENT_STORAGE, AESM_DBG_LOG_FID, filename, MAX_PATH);
        if(err != AE_SUCCESS)
            return;
        FILE *logfile = NULL;
        se_mutex_lock(&cs);
        errno_t err_code = fopen_s(&logfile, filename, "a+");
        if(err_code!=0){
            se_mutex_unlock(&cs);
            return;
        }
        time_t t;
        struct tm time_info;
        va_list varg;
        char time_buf[TIME_BUF_SIZE];
        time(&t);
        struct tm *temp_time_info;
        temp_time_info = localtime(&t);
        memcpy_s(&time_info, sizeof(time_info), temp_time_info, sizeof(*temp_time_info));
        if(strftime(time_buf, TIME_BUF_SIZE, "%c", &time_info)!=0){
           fprintf(logfile, "[%s|%d|%s|%s]",file_name, line_no, funname, time_buf);
        }else{
           fprintf(logfile, "[%s|%d|%s]",file_name, line_no, funname);
        }
        va_start(varg, format);
        vfprintf(logfile, format, varg);
        va_end(varg);
        fprintf(logfile, "\n");
        fflush(logfile);
        fclose(logfile);
        se_mutex_unlock(&cs);
    }
}


void aesm_set_log_level(int level)
{
    aesm_trace_level = level;
}

static char half_byte_to_char(int x)
{
    assert(0<=x&&x<=0xF);
    if(0<=x&&x<=9)return (char)('0'+x);
    else return (char)('A'+x-10);
}

void aesm_dbg_format_hex(const uint8_t *data, uint32_t data_len, char *out_buf, uint32_t buf_size)
{
    uint32_t i;
    assert(buf_size>0);
    if(data_len==0){
        out_buf[0]='\0';
        return;
    }
    if(buf_size/3>=data_len){
        for(i=0;i<data_len;i++){
            int low=data[i]&0xF;
            int high=(data[i]>>4)&0xF;
            out_buf[i*3]=half_byte_to_char(high);
            out_buf[i*3+1]=half_byte_to_char(low);
            out_buf[i*3+2]=' ';
        }
        out_buf[data_len*3-1]='\0';
    }else if(buf_size>10){
        uint32_t tcount=buf_size/3-1;
        uint32_t off;
        uint32_t ecount=tcount/2,bcount=tcount-ecount;
        for(i=0;i<bcount;i++){
            int low=data[i]&0xF;
            int high=(data[i]>>4)&0xF;
            out_buf[i*3]=half_byte_to_char(high);
            out_buf[i*3+1]=half_byte_to_char(low);
            out_buf[i*3+2]=' ';
        }
        out_buf[i*3]=out_buf[i*3+1]=out_buf[i*3+2]='.';
        off=i*3+3;
        for(i=0;i<ecount;i++){
            int low=data[data_len-ecount+i]&0xF;
            int high=(data[data_len-ecount+i]>>4)&0xF;
            out_buf[off+i*3]=half_byte_to_char(high);
            out_buf[off+i*3+1]=half_byte_to_char(low);
            out_buf[off+i*3+2]=' ';
        }
        out_buf[off+i*3-1]='\0';
    }else{
        for(i=0;/*i<data_len&&*/i<(buf_size-1)/3;i++){//checking for i<data_len is redundant since first if condition in the function has filtered it
            int low=data[i]&0xF;
            int high=(data[i]>>4)&0xF;
            out_buf[i*3]=half_byte_to_char(high);
            out_buf[i*3+1]=half_byte_to_char(low);
            out_buf[i*3+2]=' ';
        }
        out_buf[i*3]='\0';
    }
}

#include "tinyxml.h"

static const char *xml_get_child_text(TiXmlNode *parent, const char *name)
{
    if(parent == NULL) return NULL;
    TiXmlNode *sub_node = parent->FirstChild(name);
    if(sub_node == NULL) return NULL;
    TiXmlElement *elem = sub_node->ToElement();
    if(elem == NULL) return NULL;
    return elem->GetText();
}

static const char *dbg_level_str[]={
    "fatal",
    "error",
    "warning",
    "info",
    "debug",
    "trace"
};
#define DBG_LEVEL_COUNT (sizeof(dbg_level_str)/sizeof(dbg_level_str[0]))
static int find_dbg_level_str(const char *text_level)
{
    uint32_t i;
    size_t text_level_len = strlen(text_level);
    for(i=0;i<DBG_LEVEL_COUNT;i++){
        size_t cur_len = strlen(dbg_level_str[i]);
        if(cur_len>text_level_len)cur_len=text_level_len;
        if(_strnicmp(text_level, dbg_level_str[i], cur_len)==0){
            return (int)i;
        }
    }
    AESM_DBG_ERROR("unkown level %s",text_level);
    return -1;
}

ae_error_t load_log_config(void)
{
    char path_name[MAX_PATH];
    ae_error_t ae_err = AE_SUCCESS;
    if((ae_err=aesm_get_cpathname(FT_PERSISTENT_STORAGE, AESM_DBG_LOG_CFG_FID, path_name, MAX_PATH))!=AE_SUCCESS){
        AESM_DBG_ERROR("fail to read config path");
        return ae_err;
    }

    TiXmlDocument doc(path_name);
    bool load_ok = doc.LoadFile();
    if(!load_ok){
        AESM_DBG_ERROR("fail to load config file %s", path_name);
        return OAL_FILE_ACCESS_ERROR;
    }
    TiXmlNode *pmetadata_node = doc.FirstChild("DbgLog");
    const char *temp_text = xml_get_child_text(pmetadata_node, "level");
    if(temp_text!=NULL){
        if(isdigit(temp_text[0])){
            AESM_SET_DBG_LEVEL(atoi(temp_text));
        }else{
            int level = find_dbg_level_str(temp_text);
            if(level>=0){
                AESM_SET_DBG_LEVEL(level);
            }
        }
    }else{
        AESM_DBG_ERROR("fail to find level");
    }
    return AE_SUCCESS;
}

#endif

