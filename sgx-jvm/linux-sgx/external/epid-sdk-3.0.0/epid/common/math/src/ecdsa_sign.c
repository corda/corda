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
 * \brief EcdsaSignBuffer implementation.
 */

#include "epid/common/math/ecdsa.h"
#include "epid/common/math/bignum.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/src/memory.h"
#include "ext/ipp/include/ippcp.h"

/// The number of attempts to generate ephemeral key pair
#define EPHKEYGEN_WATCHDOG (10)

EpidStatus EcdsaSignBuffer(void const* buf, size_t buf_len,
                           EcdsaPrivateKey const* privkey, BitSupplier rnd_func,
                           void* rnd_param, EcdsaSignature* sig) {
  EpidStatus result = kEpidMathErr;

  IppsECCPState* ec_ctx = NULL;
  BigNum* bn_ec_order = NULL;

  BigNum* bn_hash = NULL;

  BigNum* bn_reg_private = NULL;
  BigNum* bn_eph_private = NULL;
  IppsECCPPointState* ecp_eph_public = NULL;

  BigNum* bn_sig_x = NULL;
  BigNum* bn_sig_y = NULL;

  do {
    EpidStatus epid_status = kEpidNoErr;
    IppStatus sts = ippStsNoErr;
    int ctxsize = 0;
    // order of EC secp256r1
    const uint8_t secp256r1_r[32] = {
        0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xBC, 0xE6, 0xFA, 0xAD, 0xA7, 0x17,
        0x9E, 0x84, 0xF3, 0xB9, 0xCA, 0xC2, 0xFC, 0x63, 0x25, 0x51};
    Ipp8u hash[IPP_SHA256_DIGEST_BITSIZE / 8] = {0};
    unsigned int gen_loop_count = EPHKEYGEN_WATCHDOG;
    Ipp32u cmp0 = IS_ZERO;
    Ipp32u cmp_order = IS_ZERO;

    if ((0 != buf_len && !buf) || !privkey || !rnd_func || !sig) {
      result = kEpidBadArgErr;
      break;
    }
    if (buf_len > INT_MAX) {
      result = kEpidBadArgErr;
      break;
    }

    // Define standard elliptic curve secp256r1
    sts = ippsECCPGetSizeStd256r1(&ctxsize);
    if (ippStsNoErr != sts) break;
    ec_ctx = (IppsECCPState*)SAFE_ALLOC(ctxsize);
    if (!ec_ctx) {
      result = kEpidMemAllocErr;
      break;
    }
    sts = ippsECCPInitStd256r1(ec_ctx);
    if (ippStsNoErr != sts) break;
    sts = ippsECCPSetStd256r1(ec_ctx);
    if (ippStsNoErr != sts) break;

    // Create big number for order of elliptic curve secp256r1
    epid_status = NewBigNum(sizeof(secp256r1_r), &bn_ec_order);
    if (kEpidMemAllocErr == epid_status) {
      result = kEpidMemAllocErr;
      break;
    }
    if (kEpidNoErr != epid_status) break;
    epid_status = ReadBigNum(secp256r1_r, sizeof(secp256r1_r), bn_ec_order);
    if (kEpidNoErr != epid_status) break;

    // Calculate hash for input message
    sts = ippsSHA256MessageDigest(buf, (int)buf_len, hash);
    if (ippStsNoErr != sts) break;

    // Create big number for hash
    epid_status = NewBigNum(sizeof(hash), &bn_hash);
    if (kEpidMemAllocErr == epid_status) {
      result = kEpidMemAllocErr;
      break;
    }
    if (kEpidNoErr != epid_status) break;
    epid_status = ReadBigNum(hash, sizeof(hash), bn_hash);
    if (kEpidNoErr != epid_status) break;
    sts = ippsMod_BN(bn_hash->ipp_bn, bn_ec_order->ipp_bn, bn_hash->ipp_bn);
    if (ippStsNoErr != sts) break;

    // Create big number for regular private key
    epid_status = NewBigNum(sizeof(*privkey), &bn_reg_private);
    if (kEpidMemAllocErr == epid_status) {
      result = kEpidMemAllocErr;
      break;
    }
    if (kEpidNoErr != epid_status) break;
    epid_status = ReadBigNum(privkey, sizeof(*privkey), bn_reg_private);
    if (kEpidNoErr != epid_status) break;

    // Validate private key is in range [1, bn_ec_order-1]
    sts = ippsCmpZero_BN(bn_reg_private->ipp_bn, &cmp0);
    if (ippStsNoErr != sts) break;
    sts = ippsCmp_BN(bn_reg_private->ipp_bn, bn_ec_order->ipp_bn, &cmp_order);
    if (ippStsNoErr != sts) break;
    if (IS_ZERO == cmp0 || LESS_THAN_ZERO != cmp_order) {
      result = kEpidBadArgErr;
      break;
    }

    // Create big number for ephemeral private key
    epid_status = NewBigNum(sizeof(secp256r1_r), &bn_eph_private);
    if (kEpidMemAllocErr == epid_status) {
      result = kEpidMemAllocErr;
      break;
    }
    if (kEpidNoErr != epid_status) break;

    // Create EC point for ephemeral public key
    sts = ippsECCPPointGetSize(256, &ctxsize);
    if (ippStsNoErr != sts) break;
    ecp_eph_public = (IppsECCPPointState*)SAFE_ALLOC(ctxsize);
    if (!ecp_eph_public) {
      result = kEpidMemAllocErr;
      break;
    }
    sts = ippsECCPPointInit(256, ecp_eph_public);
    if (ippStsNoErr != sts) break;

    // Create big numbers for signature
    epid_status = NewBigNum(sizeof(secp256r1_r), &bn_sig_x);
    if (kEpidMemAllocErr == epid_status) {
      result = kEpidMemAllocErr;
      break;
    }
    if (kEpidNoErr != epid_status) break;
    epid_status = NewBigNum(sizeof(secp256r1_r), &bn_sig_y);
    if (kEpidMemAllocErr == epid_status) {
      result = kEpidMemAllocErr;
      break;
    }
    if (kEpidNoErr != epid_status) break;

    do {
      // Generate ephemeral key pair
      sts = ippsECCPGenKeyPair(bn_eph_private->ipp_bn, ecp_eph_public, ec_ctx,
                               (IppBitSupplier)rnd_func, rnd_param);
      if (ippStsNoErr != sts) break;

      // Set ephemeral key pair
      sts = ippsECCPSetKeyPair(bn_eph_private->ipp_bn, ecp_eph_public, ippFalse,
                               ec_ctx);
      if (ippStsNoErr != sts) break;

      // Compute signature
      sts = ippsECCPSignDSA(bn_hash->ipp_bn, bn_reg_private->ipp_bn,
                            bn_sig_x->ipp_bn, bn_sig_y->ipp_bn, ec_ctx);
      if (ippStsEphemeralKeyErr != sts) break;
    } while (--gen_loop_count);
    if (ippStsEphemeralKeyErr == sts) {
      result = kEpidRandMaxIterErr;
      break;
    }
    if (ippStsNoErr != sts) break;

    sts = ippsGetOctString_BN(sig->x.data, sizeof(sig->x), bn_sig_x->ipp_bn);
    if (ippStsNoErr != sts) break;
    sts = ippsGetOctString_BN(sig->y.data, sizeof(sig->y), bn_sig_y->ipp_bn);
    if (ippStsNoErr != sts) break;

    result = kEpidNoErr;
  } while (0);

  DeleteBigNum(&bn_ec_order);
  DeleteBigNum(&bn_hash);
  DeleteBigNum(&bn_reg_private);
  DeleteBigNum(&bn_eph_private);
  DeleteBigNum(&bn_sig_x);
  DeleteBigNum(&bn_sig_y);

  SAFE_FREE(ec_ctx);
  SAFE_FREE(ecp_eph_public);

  return result;
}
