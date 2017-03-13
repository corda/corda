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
 * \brief Bignum C++ wrapper implementation.
 */
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/bignum_wrapper-testhelper.h"
#include "epid/common/math/bignum.h"

/// bignum deleter type
struct BigNumDeleter {
  /// bignum deleter
  void operator()(BigNum* bn) {
    if (bn) {
      DeleteBigNum(&bn);
    }
  }
};

/// bignum deleter singlton
BigNumDeleter bignum_deleter;

/// Internal state of the bignum wrapper
struct BigNumObj::State {
  /// size of the stored BigNum
  size_t size;

  /// The stored BigNum
  std::shared_ptr<BigNum> bn_;

  /// Default initializing constructor
  State() : size(0), bn_() {}

  /// write a new value
  void write(unsigned char const* buf, size_t buflen, size_t len) {
    bool orig_has_data = (buf != nullptr) && (buflen > 0);
    std::shared_ptr<BigNum> bn;
    BigNum* bn_ptr = nullptr;
    THROW_ON_EPIDERR(NewBigNum(len, &bn_ptr));
    bn.reset(bn_ptr, bignum_deleter);
    size = len;
    if (orig_has_data) {
      THROW_ON_EPIDERR(ReadBigNum(buf, buflen, bn.get()));
    }
    bn_ = bn;
  }
};

BigNumObj::BigNumObj() : state_(new State) {
  state_->write(nullptr, 0, sizeof(BigNumStr));
}

BigNumObj::BigNumObj(BigNumObj const& other) : state_(new State) {
  bool orig_has_data = other.state_->bn_.get() != nullptr;
  std::vector<unsigned char> buf;
  if (orig_has_data) {
    buf.resize(other.state_->size);
    THROW_ON_EPIDERR(WriteBigNum(other.state_->bn_.get(), buf.size(), &buf[0]));
  }
  state_->write(&buf[0], other.state_->size, buf.size());
}

BigNumObj& BigNumObj::operator=(BigNumObj const& other) {
  bool orig_has_data = other.state_->bn_.get() != nullptr;
  std::vector<unsigned char> buf;
  if (orig_has_data) {
    buf.resize(other.state_->size);
    THROW_ON_EPIDERR(WriteBigNum(other.state_->bn_.get(), buf.size(), &buf[0]));
  }
  state_->write(&buf[0], other.state_->size, buf.size());
  return *this;
}

BigNumObj::BigNumObj(size_t data_size_bytes) : state_(new State) {
  state_->write(nullptr, 0, data_size_bytes);
}

BigNumObj::BigNumObj(size_t data_size_bytes,
                     std::vector<unsigned char> const& bytes)
    : state_(new State) {
  state_->write(&bytes[0], bytes.size(), data_size_bytes);
}

BigNumObj::BigNumObj(size_t data_size_bytes, BigNumStr const& bytes)
    : state_(new State) {
  state_->write((unsigned char const*)&bytes, sizeof(BigNumStr),
                data_size_bytes);
}

BigNumObj::BigNumObj(std::vector<unsigned char> const& bytes)
    : state_(new State) {
  state_->write(&bytes[0], bytes.size(), bytes.size());
}

BigNumObj::BigNumObj(BigNumStr const& bytes) : state_(new State) {
  state_->write((unsigned char const*)&bytes, sizeof(BigNumStr),
                sizeof(BigNumStr));
}

BigNumObj::~BigNumObj() {}

BigNumObj::operator BigNum*() { return state_->bn_.get(); }

BigNumObj::operator const BigNum*() const { return state_->bn_.get(); }

BigNum* BigNumObj::get() { return state_->bn_.get(); }

BigNum const* BigNumObj::getc() const { return state_->bn_.get(); }
