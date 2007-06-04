#include <stdio.h>
#include <assert.h>

#ifndef OUTPUT_H
#define OUTPUT_H

#define UNUSED __attribute__((unused))

// output /////////////////////////////////////////////////////////////////////

class Output {
 public:
  virtual ~Output() { }
  
  virtual void dispose() = 0;

  virtual void write(const char* s) = 0;

  void write(int i) {
    static const int Size = 32;
    char s[Size];
    int c UNUSED = snprintf(s, Size, "%d", i);
    assert(c > 0 and c < Size);
    write(s);
  }
};

// file output ////////////////////////////////////////////////////////////////

class FileOutput : public Output {
 public:
  const char* file;
  FILE* stream;
  bool close;

  FileOutput(const char* file, FILE* stream = 0, bool close = true):
    file(file), stream(stream), close(close)
  { }

  virtual ~FileOutput() {
    dispose();
  }

  virtual void dispose() {
    if (stream and close) {
      fclose(stream);
      stream = 0;
    }
  }

  virtual void write(const char* s) {
    fputs(s, stream);
  }

  const char* filename() {
    return file;
  }
};

#endif//OUTPUT_H
