#ifndef CLASS_FINDER_H
#define CLASS_FINDER_H

#include "common.h"

class ClassFinder {
 public:
  virtual ~ClassFinder() { }
  virtual const uint8_t* find(const char* className) = 0;
  virtual void free(const uint8_t* class_) = 0;
};

#endif//CLASS_FINDER_H
