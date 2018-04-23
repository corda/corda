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
 * \brief Test data.
 *
 * Type    : Intel(R) EPID 1.1 group revocation list grpX revoked first entry
 * 0               32              64              96              128  Bit
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   RLVer = 3   |    n3 = 3     | gid[1] = 0x7b | gid[2] = 0x12 |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * 128             160  Bit
 * +-+-+-+-+-+-+-+-+
 * | gid[2] = 0x5f |
 * +-+-+-+-+-+-+-+-+
 *
 * 0x7b is revoked gid
 */
//unsigned char grprl_dat[] = {
  0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x03,
  0x00, 0x00, 0x00, 0x7b, 0x00, 0x00, 0x00, 0x12,
  0x00, 0x00, 0x00, 0x5f
//};
//unsigned int grprl_dat_len = 20;
