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
 * \brief Bignum C++ wrapper interface.
 */
#ifndef EPID_COMMON_TESTHELPER_BIGNUM_WRAPPER_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_BIGNUM_WRAPPER_TESTHELPER_H_

#include <memory>
#include <vector>

extern "C" {
#include "epid/common/math/bignum.h"
}

/*!
Wrapper class to provide Resource Allocation is Initialization handling
for BigNum
*/
class BigNumObj {
 public:
  /// Create a BigNum of default size ( sizeof(BigNumStr) )
  BigNumObj();
  /// copy constructor
  BigNumObj(BigNumObj const& other);
  /// assignment operator
  BigNumObj& operator=(BigNumObj const& other);
  /// Create a BigNum of specific size
  explicit BigNumObj(size_t data_size_bytes);
  /// Create a BigNum of specific size and initialize it to bytes
  BigNumObj(size_t data_size_bytes, std::vector<unsigned char> const& bytes);
  /// Create a BigNum of specific size and initialize it to bytes
  BigNumObj(size_t data_size_bytes, BigNumStr const& bytes);
  /// Create a BigNum the same size as bytes and initialize it to bytes
  explicit BigNumObj(std::vector<unsigned char> const& bytes);
  /// Create a BigNum the same size as bytes and initialize it to bytes
  explicit BigNumObj(BigNumStr const& bytes);
  /// Destroy the Bignum
  ~BigNumObj();
  /// cast operator to get the pointer to the stored BigNum
  operator BigNum*();
  /// const cast operator to get the pointer to the stored BigNum
  operator const BigNum*() const;
  /// Get the underlying pointer
  BigNum* get();
  /// Get the underlying pointer
  BigNum const* getc() const;

 private:
  struct State;
  std::unique_ptr<State> state_;
};

#endif  // EPID_COMMON_TESTHELPER_BIGNUM_WRAPPER_TESTHELPER_H_
