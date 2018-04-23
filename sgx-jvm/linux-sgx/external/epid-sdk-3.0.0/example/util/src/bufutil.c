/*############################################################################
  # Copyright 2016 Intel Corporation
  #
  # Licensed under the Apache License, Version 2.0 (the "License");
  # you may not use this file except in compliance with the License.
  # You may obtain a copy of the License at
  #
  #     http://www.apache.org/licenses/LICENSE-2.0
  #
  # Unless required by applicable law or agreed to in writing, software
  # distributed under the License is distributed on an "AS IS" BASIS,
  # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  # See the License for the specific language governing permissions and
  # limitations under the License.
  ############################################################################*/

/*!
 * \file
 * \brief Buffer handling utilities implementation.
 */

#include <util/buffutil.h>

#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include "util/envutil.h"

/// file static variable that indicates verbose logging
static bool g_bufutil_verbose = false;

bool ToggleVerbosity() {
  g_bufutil_verbose = (g_bufutil_verbose) ? false : true;
  return g_bufutil_verbose;
}

bool FileExists(char const* filename) {
  FILE* fp = NULL;
  if (!filename || !filename[0]) {
    return false;
  }
  fp = fopen(filename, "rb");
  if (fp) {
    fclose(fp);
    return true;
  }
  return false;
}

size_t GetFileSize(char const* filename) {
  size_t file_length = 0;
  FILE* fp = fopen(filename, "rb");
  if (fp) {
    fseek(fp, 0, SEEK_END);
    file_length = ftell(fp);
    fclose(fp);
  }
  return file_length;
}

size_t GetFileSize_S(char const* filename, size_t max_size) {
  size_t size = GetFileSize(filename);
  if (size > max_size) {
    return 0;
  } else {
    return size;
  }
}

void* AllocBuffer(size_t size) {
  void* buffer = NULL;
  if (size) {
    buffer = malloc(size);
  }
  if (!buffer) {
    log_error("failed to allocate memory");
  }
  return buffer;
}

void* NewBufferFromFile(const char* filename, size_t* size) {
  void* buffer = NULL;

  do {
    size_t len = 0;

    if (!FileExists(filename)) {
      log_error("cannot access '%s'", filename);
      break;
    }

    len = GetFileSize_S(filename, SIZE_MAX);
    if (len == 0) {
      log_error("cannot load empty file '%s'", filename);
      break;
    }

    buffer = AllocBuffer(len);

    if (buffer) {
      if (0 != ReadLoud(filename, buffer, len)) {
        free(buffer);
        buffer = NULL;
        break;
      }
    }

    if (size) {
      *size = len;
    }
  } while (0);
  return buffer;
}

int ReadBufferFromFile(const char* filename, void* buffer, size_t size) {
  int result = 0;
  FILE* file = NULL;
  do {
    size_t bytes_read = 0;
    size_t file_size = 0;
    file = fopen(filename, "rb");
    if (!file) {
      result = -1;
      break;
    }
    fseek(file, 0, SEEK_END);
    file_size = ftell(file);
    fseek(file, 0, SEEK_SET);
    if ((size_t)file_size != size) {
      result = -1;
      break;
    }

    if (buffer && (0 != size)) {
      bytes_read = fread(buffer, 1, size, file);
      if (bytes_read != size) {
        result = -1;
        break;
      }
    }
  } while (0);

  if (file) {
    fclose(file);
  }

  return result;
}

int WriteBufferToFile(const void* buffer, size_t size, const char* filename) {
  int result = 0;
  FILE* file = NULL;
  do {
    size_t bytes_written = 0;

    file = fopen(filename, "wb");
    if (!file) {
      result = -1;
      break;
    }
    bytes_written = fwrite(buffer, 1, size, file);
    if (bytes_written != size) {
      result = -1;
      break;
    }
  } while (0);

  if (file) {
    fclose(file);
  }

  return result;
}

int ReadLoud(char const* filename, void* buf, size_t size) {
  int result;

  if (!buf || 0 == size) {
    log_error("internal error: invalid buffer to ReadLoud");
    return -1;
  }

  if (g_bufutil_verbose) {
    log_msg("reading %s", filename);
  }

  if (!FileExists(filename)) {
    log_error("cannot access '%s' for reading", filename);
    return -1;
  }

  if (size != GetFileSize(filename)) {
    log_error("unexpected file size for '%s'. Expected: %d; got: %d", filename,
              (int)size, (int)GetFileSize(filename));
    return -1;
  }

  result = ReadBufferFromFile(filename, buf, size);
  if (0 != result) {
    log_error("failed to read from `%s`", filename);
    return result;
  }

  if (g_bufutil_verbose) {
    PrintBuffer(buf, size);
  }

  return result;
}

int WriteLoud(void* buf, size_t size, char const* filename) {
  int result = -1;

  if (!buf || 0 == size) {
    log_error("internal error: invalid buffer to WriteLoud");
    return -1;
  }

  if (g_bufutil_verbose) {
    log_msg("writing %s", filename);
  }

  result = WriteBufferToFile(buf, size, filename);

  if (0 != result) {
    log_error("failed to write to `%s`", filename);
    return result;
  }

  if (g_bufutil_verbose) {
    PrintBuffer(buf, size);
  }

  return result;
}

void PrintBuffer(const void* buffer, size_t size) {
  BufferPrintOptions opts;
  opts.show_header = true;
  opts.show_offset = true;
  opts.show_hex = true;
  opts.show_ascii = true;
  opts.bytes_per_group = 2;
  opts.groups_per_line = 8;
  PrintBufferOpt(buffer, size, opts);
}

void PrintBufferOpt(const void* buffer, size_t size, BufferPrintOptions opts) {
  unsigned char* bytes = (unsigned char*)buffer;
  size_t bytes_per_line = opts.bytes_per_group * opts.groups_per_line;
  size_t line_offset = 0;
  size_t byte_offset = 0;
  size_t byte_col = 0;
  if (opts.show_header) {
    if (opts.show_offset) {
      log_fmt("  offset");
      log_fmt(": ");
    }

    if (opts.show_hex) {
      byte_col = 0;
      while (byte_col < bytes_per_line) {
        log_fmt("%x%x", (int)byte_col, (int)byte_col);
        if (0 == (byte_col + 1) % opts.bytes_per_group) {
          log_fmt(" ");
        }
        byte_col += 1;
      }
    }

    if (opts.show_hex && opts.show_ascii) {
      log_fmt("| ");
    }

    if (opts.show_ascii) {
      byte_col = 0;
      while (byte_col < bytes_per_line) {
        log_fmt("%x", (int)byte_col);
        byte_col += 1;
      }
    }

    log_fmt("\n");

    if (opts.show_offset) {
      log_fmt("--------");
      log_fmt(": ");
    }

    if (opts.show_hex) {
      byte_col = 0;
      while (byte_col < bytes_per_line) {
        log_fmt("--");
        if (0 == (byte_col + 1) % opts.bytes_per_group) {
          log_fmt("-");
        }
        byte_col += 1;
      }
    }

    if (opts.show_hex && opts.show_ascii) {
      log_fmt("|-");
    }

    if (opts.show_ascii) {
      byte_col = 0;
      while (byte_col < bytes_per_line) {
        log_fmt("-");
        byte_col += 1;
      }
    }
    log_fmt("\n");
  }

  line_offset = 0;

  while (line_offset < size) {
    if (opts.show_offset) {
      log_fmt("%08x", (int)line_offset);
      log_fmt(": ");
    }

    if (opts.show_hex) {
      byte_col = 0;
      while (byte_col < bytes_per_line) {
        byte_offset = line_offset + byte_col;
        if (byte_offset < size) {
          log_fmt("%02x", (int)bytes[byte_offset]);
        } else {
          log_fmt("  ");
        }
        if (0 == (byte_col + 1) % opts.bytes_per_group) {
          log_fmt(" ");
        }
        byte_col += 1;
      }
    }

    if (opts.show_hex && opts.show_ascii) {
      log_fmt("| ");
    }

    if (opts.show_ascii) {
      byte_col = 0;
      while (byte_col < bytes_per_line) {
        byte_offset = line_offset + byte_col;
        if (byte_offset < size) {
          unsigned char ch = bytes[byte_offset];
          if (isprint(ch)) {
            log_fmt("%c", ch);
          } else {
            log_fmt(".");
          }
        } else {
          log_fmt("  ");
        }
        byte_col += 1;
      }
    }

    log_fmt("\n");
    line_offset += bytes_per_line;
  }
}
