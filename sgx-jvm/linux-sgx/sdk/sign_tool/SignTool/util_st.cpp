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


#include "util_st.h"
#include "se_trace.h"
#include <assert.h>
#include <fstream>

extern "C" bool write_data_to_file(const char *filename, std::ios_base::openmode mode, 
                                   uint8_t *buf, size_t bsize, long offset)
{
    if(filename == NULL || (buf == NULL && bsize != 0) || (buf != NULL && bsize == 0))
        return false;
    std::ofstream ofs(filename, mode);
    if(!ofs.good())
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, filename);
        return false;
    }
    ofs.seekp(offset, std::ios::beg);
    ofs.write(reinterpret_cast<char*>(buf), bsize);
    if(ofs.fail())
    {
        se_trace(SE_TRACE_ERROR, WRITE_FILE_ERROR, filename);
        return false;
    }
    return true;
}

extern "C" size_t get_file_size(const char *filename)
{
    std::ifstream ifs(filename, std::ios::in | std::ios::binary);
    if(!ifs.good())
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, filename);
        return -1;
    }
    ifs.seekg(0, std::ios::end);
    size_t size = (size_t)ifs.tellg();
    return size;  
}

extern "C" bool read_file_to_buf(const char *filename, uint8_t *buf, size_t bsize)
{
    if(filename == NULL || buf == NULL || bsize == 0)
        return false;
    std::ifstream ifs(filename, std::ios::binary|std::ios::in);
    if(!ifs.good())
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, filename);
        return false;
    }
    ifs.read(reinterpret_cast<char *> (buf), bsize);
    if(ifs.fail())
    {
        return false;
    }
    return true;

}

extern "C" bool copy_file(const char *source_path, const char *dest_path)
{
    std::ifstream ifs(source_path, std::ios::binary|std::ios::in);
    if(!ifs.good())
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, source_path);
        return false;
    }

    std::ofstream ofs(dest_path, std::ios::binary|std::ios::out);
    if(!ofs.good())
    {
        se_trace(SE_TRACE_ERROR, OPEN_FILE_ERROR, dest_path);
        return false;
    }
    ofs << ifs.rdbuf();
    return true;
}

