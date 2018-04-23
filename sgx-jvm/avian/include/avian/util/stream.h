/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef STREAM_H
#define STREAM_H

#include "avian/common.h"

namespace vm {

class AbstractStream {
 public:
  class Client {
   public:
    virtual void handleError() = 0;
  };

  AbstractStream(Client* client, unsigned size)
      : client(client), size(size), position_(0)
  {
  }

  unsigned position()
  {
    return position_;
  }

  void setPosition(unsigned p)
  {
    position_ = p;
  }

  void skip(unsigned size)
  {
    if (size > this->size - position_) {
      client->handleError();
    } else {
      position_ += size;
    }
  }

  void read(uint8_t* dst, unsigned size)
  {
    if (size > this->size - position_) {
      memset(dst, 0, size);

      client->handleError();
    } else {
      copy(dst, position_, size);
      position_ += size;
    }
  }

  uint8_t read1()
  {
    uint8_t v;
    read(&v, 1);
    return v;
  }

  uint16_t read2()
  {
    uint16_t a = read1();
    uint16_t b = read1();
    return (a << 8) | b;
  }

  uint32_t read4()
  {
    uint32_t a = read2();
    uint32_t b = read2();
    return (a << 16) | b;
  }

  uint64_t read8()
  {
    uint64_t a = read4();
    uint64_t b = read4();
    return (a << 32) | b;
  }

  uint32_t readFloat()
  {
    return read4();
  }

  uint64_t readDouble()
  {
    return read8();
  }

 protected:
  virtual void copy(uint8_t* dst, unsigned offset, unsigned size) = 0;

 private:
  Client* client;
  unsigned size;
  unsigned position_;
};

class Stream : public AbstractStream {
 public:
  Stream(Client* client, const uint8_t* data, unsigned size)
      : AbstractStream(client, size), data(data)
  {
  }

 private:
  virtual void copy(uint8_t* dst, unsigned offset, unsigned size)
  {
    memcpy(dst, data + offset, size);
  }

  const uint8_t* data;
};

}  // namespace vm

#endif  // STREAM_H
