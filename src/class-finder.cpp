

#include "sys/mman.h"
#include "sys/types.h"
#include "sys/stat.h"
#include "fcntl.h"



#include "system.h"
#include "class-finder.h"

using namespace vm;

namespace {

const char*
append(System* s, const char* a, const char* b, const char* c,
       const char* d)
{
  unsigned al = strlen(a);
  unsigned bl = strlen(b);
  unsigned cl = strlen(c);
  unsigned dl = strlen(d);
  char* p = static_cast<char*>(s->allocate(al + bl + cl + dl + 1));
  memcpy(p, a, al);
  memcpy(p + al, b, bl);
  memcpy(p + al + bl, c, cl);
  memcpy(p + al + bl + cl, d, dl + 1);
  return p;
}

const char**
parsePath(System* s, const char* path)
{
  class Tokenizer {
   public:
    class Token {
     public:
      Token(const char* s, unsigned length): s(s), length(length) { }

      const char* s;
      unsigned length;
    };

    Tokenizer(const char* s, char delimiter): s(s), delimiter(delimiter) { }

    bool hasMore() {
      while (*s == delimiter) ++s;
      return *s;
    }

    Token next() {
      const char* p = s;
      while (*s and *s != delimiter) ++s;
      return Token(p, s - p);
    }

    const char* s;
    char delimiter;
  };

  unsigned count = 0;
  for (Tokenizer t(path, ':'); t.hasMore(); t.next()) ++ count;

  const char** v = static_cast<const char**>
    (s->allocate((count + 1) * sizeof(const char*)));

  unsigned i = 0;
  for (Tokenizer t(path, ':'); t.hasMore(); ++i) {
    Tokenizer::Token token(t.next());
    char* p = static_cast<char*>(s->allocate(token.length + 1));
    memcpy(p, token.s, token.length);
    p[token.length] = 0;
    v[i] = p;
  }

  v[i] = 0;

  return v;
}

class MyClassFinder: public ClassFinder {
 public:
  MyClassFinder(System* system, const char* path):
    system(system),
    path(parsePath(system, path))
  { }

  class Data: public ClassFinder::Data {
   public:
    Data(System* system, uint8_t* start, size_t length):
      system(system),
      start_(start),
      length_(length)
    { }

    virtual const uint8_t* start() {
      return start_;
    }

    virtual size_t length() {
      return length_;
    }

    virtual void dispose() {
      if (start_) {
        munmap(start_, length_);
      }
      system->free(this);
    }

    System* system;
    uint8_t* start_;
    size_t length_;
  };

  virtual Data* find(const char* className) {
    Data* d = new (system->allocate(sizeof(Data))) Data(system, 0, 0);

    for (const char** p = path; *p; ++p) {
      const char* file = append(system, *p, "/", className, ".class");
      int fd = open(file, O_RDONLY);
      system->free(file);

      if (fd != -1) {
        struct stat s;
        int r = fstat(fd, &s);
        if (r != -1) {
          void* data = mmap(0, s.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
          if (data) {
            d->start_ = static_cast<uint8_t*>(data);
            d->length_ = s.st_size;
            return d;
          }
        }
      }
    }
    
    system->free(d);
    return 0;
  }

  virtual void dispose() {
    for (const char** p = path; *p; ++p) {
      system->free(*p);
    }
    system->free(path);

    system->free(this);
  }

  System* system;
  const char** path;
};

} // namespace

namespace vm {

ClassFinder*
makeClassFinder(System* s, const char* path)
{
  return new (s->allocate(sizeof(MyClassFinder))) MyClassFinder(s, path);
}

} // namespace vm
