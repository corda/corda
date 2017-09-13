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


#include "aesm_config.h"
#include "aesm_proxy_type.h"
#include "oal.h"
#include "default_url_info.hh"
#include <sys/types.h>
#include <regex.h>
#include <stdio.h>

#define AESM_CONFIG_FILE "/etc/aesmd.conf"
#define MAX_LINE 1024
#define URL_PATTERN "[[:blank:]]*(http[s]?://[^[:blank:]]*)[[:blank:]]*"  //pattern used to match a URL which should be started with http:// or https://
#define OPTION_COMMENT "(#.*)?"

enum _config_value_t{
    config_comment,
    config_space,
    config_white_list_url,
    config_aesm_proxy_url,
    config_aesm_proxy_type,
    config_value_nums
};

struct _config_patterns_t{
    enum _config_value_t id;
    const char *pattern;
}config_patterns[]={
    {config_comment, "^[[:blank:]]*#"},   //matching a line with comments only (It is started by #)
    {config_space, "^[[:blank:]]*$"},   //matching empty line
    {config_white_list_url, "^[[:blank:]]*whitelist[[:blank:]]*url[[:blank:]]*=" URL_PATTERN OPTION_COMMENT "$"}, //matching line in format: whilelist url = ....
    {config_aesm_proxy_url,"^[[:blank:]]*aesm[[:blank:]]*proxy[[:blank:]]*=" URL_PATTERN OPTION_COMMENT "$"}, //matching line in format: aesm proxy = ...
    {config_aesm_proxy_type, "^[[:blank:]]*proxy[[:blank:]]*type[[:blank:]]*=[[:blank:]]([^[:blank:]]+)[[:blank:]]*" OPTION_COMMENT "$"}//matching line in format: proxy type = [direct|default|manual]
};

#define NUM_CONFIG_PATTERNS (sizeof(config_patterns)/sizeof(config_patterns[0]))

typedef struct _config_entry_t{
    bool initialized;
    regex_t reg;
} config_entry_t;

//static function to initialize all regular expression pattern
static void init_config_patterns(config_entry_t entries[])
{
    uint32_t i;
    for(i=0;i<NUM_CONFIG_PATTERNS;++i){
       uint32_t entry_id = config_patterns[i].id;
       if(entry_id>=config_value_nums){
          AESM_DBG_ERROR("config id %d is too large", entry_id);
          continue;
       }
       if(entries[entry_id].initialized){
          AESM_DBG_ERROR("duplicated item for config id %d",entry_id);
          continue;
       }
       if(regcomp(&entries[entry_id].reg,config_patterns[i].pattern, REG_EXTENDED|REG_ICASE)!=0){
          AESM_DBG_ERROR("Invalid config pattern %s", config_patterns[i].pattern);
          continue;
       }
       entries[entry_id].initialized=true;
    }
}

static void release_config_patterns(config_entry_t entries[])
{
    uint32_t i;
    for(i=0;i<config_value_nums;++i){
        if(entries[i].initialized){
             entries[i].initialized=false;
             regfree(&entries[i].reg);
        }
    }
}

static const char *proxy_type_name[]={
    "direct",
    "default",
    "manual"
};
#define NUM_PROXY_TYPE (sizeof(proxy_type_name)/sizeof(proxy_type_name[0]))

//function to decode proxy type from string to integer value
static uint32_t read_aesm_proxy_type(const char *string, uint32_t len)
{
     uint32_t i;
     for(i=0;i<NUM_PROXY_TYPE;++i){
        if(strncasecmp(proxy_type_name[i],string,len)==0){
            return i;
        }
     }
     AESM_DBG_TRACE("Invalid proxy type %.*s",len,string);
     return (uint32_t)NUM_PROXY_TYPE;
}

#define MAX_MATCHED_REG_EXP 3
//Function to processing one line in config file
//  If any pattern is matched, get the correspondent data and set it into the output parameter 'infos'
static bool config_process_one_line(const char *line, config_entry_t entries[], aesm_config_infos_t& infos)
{
    uint32_t i;
    regmatch_t matches[MAX_MATCHED_REG_EXP];
    for(i=0;i<config_value_nums;++i){
        if(!entries[i].initialized){
            continue;
        }
        if(regexec(&entries[i].reg, line, MAX_MATCHED_REG_EXP, matches, 0)==0){
            switch(i){
            case config_comment:
            case config_space:
                 //ignore comment and space only line
                 break;
            case config_white_list_url://Matching White List URL setting
                 if(matches[1].rm_eo-matches[1].rm_so>=MAX_PATH){
                     AESM_DBG_ERROR("too long white list url in config file");
                 }else{
                     memcpy(infos.white_list_url, line+matches[1].rm_so,matches[1].rm_eo-matches[1].rm_so);
                     infos.white_list_url[matches[1].rm_eo-matches[1].rm_so]='\0';
                 }
                 break;
           case config_aesm_proxy_url:
                 if(matches[1].rm_eo-matches[1].rm_so>=MAX_PATH){
                     AESM_DBG_ERROR("too long aesm proxy url in config file");
                 }else{
                     memcpy(infos.aesm_proxy, line+matches[1].rm_so,matches[1].rm_eo-matches[1].rm_so);
                     infos.aesm_proxy[matches[1].rm_eo-matches[1].rm_so]='\0';
                 }
                 break;
            case config_aesm_proxy_type://It is a proxy type, we need change the string to integer by calling function read_aesm_proxy_type
                 infos.proxy_type = read_aesm_proxy_type(line+matches[1].rm_so, matches[1].rm_eo-matches[1].rm_so);
                 break;
            default:
                 AESM_DBG_ERROR("reg exp type %d not processed", i);
                 break;
            }
            break;
        }
    }
    if(i>=config_value_nums){//the line matching nothing
        AESM_DBG_ERROR("aesm config file error: invalid line[%s]",line);
        return false;
    }
    return true;
}

bool read_aesm_config(aesm_config_infos_t& infos)
{
    char line[MAX_LINE];
    int line_no=0;
    bool ret = true;
    config_entry_t entries[config_value_nums];
    memset(&entries,0,sizeof(entries));
    memset(&infos, 0, sizeof(aesm_config_infos_t));
    strcpy(infos.white_list_url, DEFAULT_WHITE_LIST_URL);
    
    infos.proxy_type = AESM_PROXY_TYPE_DEFAULT_PROXY;
    FILE *f =fopen(AESM_CONFIG_FILE, "r");
    if(f==NULL){
         AESM_DBG_ERROR("Cannnot read aesm config file %s",AESM_CONFIG_FILE);
         return false;
    }
    init_config_patterns(entries);
    while(fgets(line, MAX_LINE, f)!=NULL){
        size_t len=strlen(line);
        if(len>0&&line[len-1]=='\n')line[len-1]='\0';//remove the line ending
        line_no++;
        if(!config_process_one_line(line, entries, infos)){
            AESM_LOG_WARN("format error in file %s:%d [%s]",AESM_CONFIG_FILE, line_no, line);
            ret = false;//continue process the file but save the error status
        }
    }
    release_config_patterns(entries);
    fclose(f);
    if(infos.proxy_type>=NUM_PROXY_TYPE||
          (infos.proxy_type==AESM_PROXY_TYPE_MANUAL_PROXY&&infos.aesm_proxy[0]=='\0')){
            AESM_DBG_WARN("Invalid proxy type %d",infos.proxy_type);
            infos.proxy_type = AESM_PROXY_TYPE_DIRECT_ACCESS;
            ret = false;
    }
    return ret;
}

