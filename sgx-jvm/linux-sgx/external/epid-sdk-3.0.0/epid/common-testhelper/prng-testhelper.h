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
 * \brief Pseudo random number generator interface.
 */
#ifndef EPID_COMMON_TESTHELPER_PRNG_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_PRNG_TESTHELPER_H_

#if defined(_WIN32) || defined(_WIN64)
#define __STDCALL __stdcall
#else
#define __STDCALL
#endif
#include <limits.h>  // for CHAR_BIT
#include <random>

/// Return status for Prng Generate function
typedef enum {
  kPrngNoErr = 0,   //!< no error
  kPrngErr = -999,  //!< unspecified error
  kPrngNotImpl,     //!< not implemented error
  kPrngBadArgErr    //!< incorrect arg to function
} PrngStatus;

/// Pseudo random number generator (prng) class.
class Prng {
 public:
  Prng() : seed_(1) { set_seed(seed_); }
  ~Prng() {}
  /// Retrieve seed
  unsigned int get_seed() const { return seed_; }
  /// Set seed for random number generator
  void set_seed(unsigned int val) {
    seed_ = val;
    generator_.seed(seed_);
  }
  /// Generates random number
  static int __STDCALL Generate(unsigned int* random_data, int num_bits,
                                void* user_data) {
    unsigned int num_bytes = num_bits / CHAR_BIT;
    unsigned int num_words = num_bytes / sizeof(unsigned int);
    unsigned int extra_bytes = num_bytes % sizeof(unsigned int);
    if (!random_data) {
      return kPrngBadArgErr;
    }
    if (num_bits <= 0) {
      return kPrngBadArgErr;
    }
    Prng* myprng = (Prng*)user_data;
    std::uniform_int_distribution<> dis(0x0, 0xffff);
    if (num_words > 0) {
      for (unsigned int n = 0; n < num_words; n++) {
        random_data[n] =
            (dis(myprng->generator_) << 16) + dis(myprng->generator_);
      }
    }
    if (extra_bytes > 0) {
      unsigned int data =
          (dis(myprng->generator_) << 16) + dis(myprng->generator_);
      unsigned char* byte_data = (unsigned char*)&data;
      unsigned char* random_bytes = (unsigned char*)&random_data[num_words];
      for (unsigned int n = 0; n < extra_bytes; n++) {
        random_bytes[n] = byte_data[n];
      }
    }

    return kPrngNoErr;
  }

 private:
  unsigned int seed_;
  std::mt19937 generator_;
};

#endif  // EPID_COMMON_TESTHELPER_PRNG_TESTHELPER_H_
