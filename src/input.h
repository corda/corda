#include <stdio.h>

#ifndef INPUT_H
#define INPUT_H

// input //////////////////////////////////////////////////////////////////////

class Input {
 public:
  virtual ~Input() { }
  
  virtual void dispose() = 0;

  virtual int peek() = 0;

  virtual int read() = 0;

  virtual unsigned line() = 0;

  virtual unsigned column() = 0;

  void skipSpace() {
    bool quit = false;
    while (not quit) {
      int c = peek();
      switch (c) {
      case ' ': case '\t': case '\n':
        read();
        break;

      default: quit = true;
      }
    }
  }
};

// file input /////////////////////////////////////////////////////////////////

class FileInput : public Input {
 public:
  const char* file;
  FILE* stream;
  unsigned line_;
  unsigned column_;
  bool close;

  FileInput(const char* file, FILE* stream = 0, bool close = true):
    file(file), stream(stream), line_(1), column_(1), close(close)
  { }

  virtual ~FileInput() {
    dispose();
  }

  virtual void dispose() {
    if (stream and close) {
      fclose(stream);
      stream = 0;
    }
  }

  virtual int peek() {
    int c = getc(stream);
    ungetc(c, stream);
    return c;
  }

  virtual int read() {
    int c = getc(stream);
    if (c == '\n') {
      ++ line_;
      column_ = 1;
    } else {
      ++ column_;
    }
    return c;
  }

  virtual unsigned line() {
    return line_;
  }

  virtual unsigned column() {
    return column_;
  }
};

// string input ///////////////////////////////////////////////////////////////

class StringInput : public Input {
 public:
  const char* string;
  unsigned position;
  unsigned limit;
  unsigned line_;
  unsigned column_;

  StringInput(const char* string);

  virtual ~StringInput() { }

  virtual void dispose() {
    // do nothing
  }

  virtual int peek() {
    if (position == limit) return -1;
    return string[position];
  }

  virtual int read() {
    int c = peek();
    if (c >= 0) {
      if (c == '\n') {
        ++ line_;
        column_ = 1;
      } else {
        ++ column_;
      }
      ++ position;
    }
    return c;
  }

  virtual unsigned line() {
    return line_;
  }

  virtual unsigned column() {
    return column_;
  }
};

#endif//INPUT_H
