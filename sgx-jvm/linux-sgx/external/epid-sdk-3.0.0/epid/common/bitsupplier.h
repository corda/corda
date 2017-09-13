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
#ifndef EPID_COMMON_BITSUPPLIER_H_
#define EPID_COMMON_BITSUPPLIER_H_
/*!
 * \file
 * \brief Random data supplier interface.
 */

#if defined(_WIN32) || defined(_WIN64)
#define __STDCALL __stdcall
#else
#define __STDCALL
#endif

/// Generates random data.
/*!
 It is the responsibility of the caller of the SDK interfaces to
 implement a function of this prototype and to then pass a pointer
 to this function into methods that require it.

 \param[out] rand_data destination buffer
 \param[in] num_bits size of rand_data in bits
 \param[in] user_data user data passed through from api call.

 \returns zero on success and non-zero value on error.

 \ingroup EpidCommon
 */
typedef int(__STDCALL* BitSupplier)(unsigned int* rand_data, int num_bits,
                                    void* user_data);

#endif  // EPID_COMMON_BITSUPPLIER_H_
