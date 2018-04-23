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
#ifndef EPID_COMMON_SRC_ENDIAN_CONVERT_H_
#define EPID_COMMON_SRC_ENDIAN_CONVERT_H_

#include <stdint.h>

/*!
 * \file
 * \brief Endianness conversion interface.
 * \addtogroup EpidCommon
 * @{
 */

#if !defined(ntohl)
/// Macro to transform oct str 32 into uint_32
#define ntohl(u32)                                    \
  ((uint32_t)(((((unsigned char*)&(u32))[0]) << 24) + \
              ((((unsigned char*)&(u32))[1]) << 16) + \
              ((((unsigned char*)&(u32))[2]) << 8) +  \
              (((unsigned char*)&(u32))[3])))
#endif

#if !defined(htonl)
/// Macro to transform uint_32 to network order
#define htonl(u32)                                   \
  (uint32_t)(((((uint32_t)(u32)) & 0xFF) << 24) |    \
             ((((uint32_t)(u32)) & 0xFF00) << 8) |   \
             ((((uint32_t)(u32)) & 0xFF0000) >> 8) | \
             ((((uint32_t)(u32)) & 0xFF000000) >> 24))
#endif

/*! @} */
#endif  // EPID_COMMON_SRC_ENDIAN_CONVERT_H_
