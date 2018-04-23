/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <string>

#include "assert.h"

#ifndef AVIAN_TOOLS_TYPE_GENERATOR_IO_H
#define AVIAN_TOOLS_TYPE_GENERATOR_IO_H

namespace avian {
namespace tools {
namespace typegenerator {

class Input {
 public:
  virtual ~Input()
  {
  }

  virtual void dispose() = 0;

  virtual int peek() = 0;

  virtual int read() = 0;

  virtual unsigned line() = 0;

  virtual unsigned column() = 0;

  void skipSpace()
  {
    bool quit = false;
    while (not quit) {
      int c = peek();
      switch (c) {
      case ' ':
      case '\t':
      case '\n':
        read();
        break;

      default:
        quit = true;
      }
    }
  }
};

class FileInput : public Input {
 public:
  const char* file;
  FILE* stream;
  unsigned line_;
  unsigned column_;
  bool close;

  FileInput(const char* file, FILE* stream = 0, bool close = true)
      : file(file), stream(stream), line_(1), column_(1), close(close)
  {
  }

  virtual ~FileInput()
  {
    dispose();
  }

  virtual void dispose()
  {
    if (stream and close) {
      fclose(stream);
      stream = 0;
    }
  }

  virtual int peek()
  {
    int c = getc(stream);
    ungetc(c, stream);
    return c;
  }

  virtual int read()
  {
    int c = getc(stream);
    if (c == '\n') {
      ++line_;
      column_ = 1;
    } else {
      ++column_;
    }
    return c;
  }

  virtual unsigned line()
  {
    return line_;
  }

  virtual unsigned column()
  {
    return column_;
  }
};

class Output {
 public:
  virtual ~Output()
  {
  }

  virtual void dispose() = 0;

  virtual void write(const std::string& s) = 0;

  void write(int32_t i)
  {
    static const int Size = 32;
    char s[Size];
    int c UNUSED = vm::snprintf(s, Size, "%d", i);
    assert(c > 0 and c < Size);
    write(s);
  }

  void writeUnsigned(uint32_t i)
  {
    static const int Size = 32;
    char s[Size];
    int c UNUSED = vm::snprintf(s, Size, "%u", i);
    assert(c > 0 and c < Size);
    write(s);
  }
};

class FileOutput : public Output {
 public:
  const char* file;
  FILE* stream;
  bool close;

  FileOutput(const char* file, FILE* stream = 0, bool close = true)
      : file(file), stream(stream), close(close)
  {
  }

  virtual ~FileOutput()
  {
    dispose();
  }

  virtual void dispose()
  {
    if (stream and close) {
      fclose(stream);
      stream = 0;
    }
  }

  virtual void write(const std::string& s)
  {
    fputs(s.c_str(), stream);
  }

  const char* filename()
  {
    return file;
  }
};

}  // namespace typegenerator
}  // namespace tools
}  // namespace avian

#endif  // AVIAN_TOOLS_TYPE_GENERATOR_IO_H
