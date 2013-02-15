/* Copyright (c) 2008-2011, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef ARCH_H
#define ARCH_H

class Test {
private:
  Test* next;
  static Test* first;
  static Test** last;

  friend int main(int argc, char** argv);

  void print(uintptr_t value) {
    fprintf(stderr, "%p", reinterpret_cast<void*>(value));
  }

  void print(bool value) {
    fprintf(stderr, "%s", value ? "true" : "false");
  }

  void print(uint8_t value) {
    fprintf(stderr, "0x%02x", value);
  }

  void print(uint64_t value) {
    fprintf(stderr, "0x%" LLD, value);
  }

  int failures;
  int runs;

protected:
  template<class T>
  void assertEqual(T expected, T actual) {
    if(expected != actual) {
      fprintf(stderr, "assertion failure, expected: ");
      print(expected);
      fprintf(stderr, ", actual: ");
      print(actual);
      fprintf(stderr, "\n");
      failures++;
    }
    runs++;
  }
  
  template<class T>
  void assertNotEqual(T expected, T actual) {
    if(expected == actual) {
      fprintf(stderr, "assertion failure, expected: not ");
      print(expected);
      fprintf(stderr, ", actual: ");
      print(actual);
      fprintf(stderr, "\n");
      failures++;
    }
    runs++;
  }

  void assertTrue(bool value) {
    assertEqual(true, value);
  }

  void assertFalse(bool value) {
    assertEqual(false, value);
  }

public:
  const char* const name;
  Test(const char* name);

  virtual void run() = 0;

  static bool runAll();
};

#endif