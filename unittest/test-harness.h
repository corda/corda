/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef TEST_HARNESS_H
#define TEST_HARNESS_H

#include "avian/common.h"
#include <stdio.h>

class Test {
 private:
  Test* next;
  static Test* first;
  static Test** last;

  friend int main(int argc, char** argv);

  void print(uint64_t value)
  {
    fprintf(stderr, "%p", reinterpret_cast<void*>(value));
  }

  void print(uint32_t value)
  {
    fprintf(stderr, "%p", reinterpret_cast<void*>(value));
  }

  void print(uint8_t value)
  {
    print(static_cast<uint32_t>(value));
  }

  void print(bool value)
  {
    fprintf(stderr, "%s", value ? "true" : "false");
  }

  int failures;
  int runs;

 protected:
  template <class T>
  void assertEqual(T expected, T actual)
  {
    if (expected != actual) {
      fprintf(stderr, "assertion failure, expected: ");
      print(expected);
      fprintf(stderr, ", actual: ");
      print(actual);
      fprintf(stderr, "\n");
      failures++;
    }
    runs++;
  }

  void assertEqual(const char* expected, const char* actual)
  {
    if ((expected == 0 && actual != 0) || (expected != 0 && actual == 0)
        || strcmp(expected, actual) != 0) {
      fprintf(stderr,
              "assertion failure, expected: \"%s\", actual: \"%s\"\n",
              expected,
              actual);
      failures++;
    }
    runs++;
  }

  template <class T>
  void assertNotEqual(T expected, T actual)
  {
    if (expected == actual) {
      fprintf(stderr, "assertion failure, expected: not ");
      print(expected);
      fprintf(stderr, ", actual: ");
      print(actual);
      fprintf(stderr, "\n");
      failures++;
    }
    runs++;
  }

  void assertTrue(bool value)
  {
    assertEqual(true, value);
  }

  void assertFalse(bool value)
  {
    assertEqual(false, value);
  }

 public:
  const char* const name;
  Test(const char* name);

  virtual void run() = 0;

  static bool runAll();
};

#define TEST(name)                      \
  class name##TestClass : public Test { \
   public:                              \
    name##TestClass() : Test(#name)     \
    {                                   \
    }                                   \
    virtual void run();                 \
  } name##TestInstance;                 \
  void name##TestClass::run()

#endif  // TEST_HARNESS_H
