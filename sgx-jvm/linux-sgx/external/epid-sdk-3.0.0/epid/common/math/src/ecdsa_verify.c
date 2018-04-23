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
 * \brief EcdsaVerifyBuffer implementation.
 */

#include "epid/common/math/ecdsa.h"

#include "epid/common/math/bignum.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/src/memory.h"
#include "ext/ipp/include/ippcp.h"

/// Handle Ipp Errors with Break
#define BREAK_ON_IPP_ERROR(sts, ret) \
  {                                  \
    IppStatus temp_sts = (sts);      \
    if (ippStsNoErr != temp_sts) {   \
      (ret) = kEpidMathErr;          \
      break;                         \
    }                                \
  }

static EpidStatus NewSecp256r1Curve(IppsECCPState** ec);

static void DeleteSecp256r1Curve(IppsECCPState** ec);

static EpidStatus NewCurvePoint(IppsECCPState const* ec,
                                IppsECCPPointState** p);

static EpidStatus ReadCurvePoint(IppsECCPState* ec,
                                 EcdsaPublicKey const* pubkey,
                                 IppsECCPPointState* p);

static EpidStatus CalcHashBn(void const* buf, size_t buf_len,
                             BigNum* bn_digest);

static void DeleteCurvePoint(IppsECCPPointState** p);

static EpidStatus ValidateSignature(BigNum const* bn_sig_x,
                                    BigNum const* bn_sig_y);

EpidStatus EcdsaVerifyBuffer(void const* buf, size_t buf_len,
                             EcdsaPublicKey const* pubkey,
                             EcdsaSignature const* sig) {
  EpidStatus result = kEpidErr;
  IppsECCPState* ec_state = NULL;
  IppsECCPPointState* ecp_pubkey = NULL;
  BigNum* bn_sig_x = NULL;
  BigNum* bn_sig_y = NULL;
  BigNum* bn_digest = NULL;

  if (!pubkey || !sig || (!buf && (0 != buf_len))) return kEpidBadArgErr;
  if (INT_MAX < buf_len) return kEpidBadArgErr;

  do {
    EpidStatus epid_status = kEpidNoErr;
    IppStatus ipp_status = ippStsNoErr;
    IppECResult ec_result = ippECValid;

    epid_status = NewBigNum(sizeof(sig->x), &bn_sig_x);
    if (kEpidNoErr != epid_status) break;

    epid_status = ReadBigNum(&sig->x, sizeof(sig->x), bn_sig_x);
    if (kEpidNoErr != epid_status) break;

    epid_status = NewBigNum(sizeof(sig->y), &bn_sig_y);
    if (kEpidNoErr != epid_status) break;

    epid_status = ReadBigNum(&sig->y, sizeof(sig->y), bn_sig_y);
    if (kEpidNoErr != epid_status) break;

    // check for invalid signature
    epid_status = ValidateSignature(bn_sig_x, bn_sig_y);
    if (kEpidSigValid != epid_status) {
      if (kEpidSigInvalid == epid_status) {
        result = kEpidBadArgErr;
      } else {
        result = epid_status;
      }
      break;
    }

    // setup curve
    epid_status = NewSecp256r1Curve(&ec_state);
    if (kEpidNoErr != epid_status) break;

    // load pubkey
    epid_status = NewCurvePoint(ec_state, &ecp_pubkey);
    if (kEpidNoErr != epid_status) break;
    epid_status = ReadCurvePoint(ec_state, pubkey, ecp_pubkey);
    if (kEpidNoErr != epid_status) break;

    // check for invalid pubkey
    ipp_status = ippsECCPCheckPoint(ecp_pubkey, &ec_result, ec_state);
    BREAK_ON_IPP_ERROR(ipp_status, result);
    if (ippECValid != ec_result) {
      result = kEpidBadArgErr;
      break;
    }

    // hash message
    epid_status = NewBigNum(IPP_SHA256_DIGEST_BITSIZE / 8, &bn_digest);
    if (kEpidNoErr != epid_status) break;
    epid_status = CalcHashBn(buf, buf_len, bn_digest);
    if (kEpidNoErr != epid_status) break;

    // configure key
    ipp_status = ippsECCPSetKeyPair(NULL, ecp_pubkey, ippTrue, ec_state);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    // verify message
    ipp_status = ippsECCPVerifyDSA(bn_digest->ipp_bn, bn_sig_x->ipp_bn,
                                   bn_sig_y->ipp_bn, &ec_result, ec_state);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    if (ippECValid == ec_result)
      result = kEpidSigValid;
    else
      result = kEpidSigInvalid;
  } while (0);

  DeleteSecp256r1Curve(&ec_state);
  DeleteCurvePoint(&ecp_pubkey);
  DeleteBigNum(&bn_digest);
  DeleteBigNum(&bn_sig_x);
  DeleteBigNum(&bn_sig_y);

  return result;
}

static EpidStatus NewSecp256r1Curve(IppsECCPState** ec) {
  EpidStatus result = kEpidNoErr;
  IppsECCPState* ec_state = NULL;

  if (!ec) return kEpidBadArgErr;

  do {
    int size = 0;
    IppStatus ipp_status = ippStsNoErr;
    ipp_status = ippsECCPGetSizeStd256r1(&size);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    ec_state = (IppsECCPState*)SAFE_ALLOC(size);
    if (!ec_state) {
      result = kEpidMemAllocErr;
      break;
    }

    ipp_status = ippsECCPInitStd256r1(ec_state);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    ipp_status = ippsECCPSetStd256r1(ec_state);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    *ec = ec_state;
  } while (0);
  if (kEpidNoErr != result) {
    SAFE_FREE(ec_state);
  }
  return result;
}

static void DeleteSecp256r1Curve(IppsECCPState** ec) {
  if (!ec || !(*ec)) {
    return;
  }
  SAFE_FREE(*ec);
  *ec = NULL;
}

static EpidStatus NewCurvePoint(IppsECCPState const* ec,
                                IppsECCPPointState** p) {
  EpidStatus result = kEpidNoErr;
  IppsECCPPointState* point = NULL;

  if (!ec || !p) return kEpidBadArgErr;

  do {
    const int kFeBitSize = 256;
    IppStatus ipp_status = ippStsNoErr;
    int size = 0;

    ipp_status = ippsECCPPointGetSize(kFeBitSize, &size);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    point = (IppsECCPPointState*)SAFE_ALLOC(size);
    if (!point) {
      result = kEpidMemAllocErr;
      break;
    }

    ipp_status = ippsECCPPointInit(kFeBitSize, point);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    *p = point;
  } while (0);
  if (kEpidNoErr != result) {
    SAFE_FREE(point);
  }
  return result;
}
static void DeleteCurvePoint(IppsECCPPointState** p) {
  if (!p || !(*p)) {
    return;
  }
  SAFE_FREE(*p);
  *p = NULL;
}

static EpidStatus ReadCurvePoint(IppsECCPState* ec,
                                 EcdsaPublicKey const* pubkey,
                                 IppsECCPPointState* p) {
  EpidStatus result = kEpidNoErr;
  BigNum* bn_pubkey_x = NULL;
  BigNum* bn_pubkey_y = NULL;

  if (!ec || !pubkey || !p) return kEpidBadArgErr;

  do {
    IppStatus ipp_status = ippStsNoErr;

    result = NewBigNum(sizeof(pubkey->x), &bn_pubkey_x);
    if (kEpidNoErr != result) break;

    result = ReadBigNum(&pubkey->x, sizeof(pubkey->x), bn_pubkey_x);
    if (kEpidNoErr != result) break;

    result = NewBigNum(sizeof(pubkey->y), &bn_pubkey_y);
    if (kEpidNoErr != result) break;

    result = ReadBigNum(&pubkey->y, sizeof(pubkey->y), bn_pubkey_y);
    if (kEpidNoErr != result) break;

    ipp_status =
        ippsECCPSetPoint(bn_pubkey_x->ipp_bn, bn_pubkey_y->ipp_bn, p, ec);
    BREAK_ON_IPP_ERROR(ipp_status, result);
  } while (0);

  DeleteBigNum(&bn_pubkey_x);
  DeleteBigNum(&bn_pubkey_y);

  return result;
}

static EpidStatus CalcHashBn(void const* buf, size_t buf_len,
                             BigNum* bn_digest) {
  EpidStatus result = kEpidErr;
  BigNum* bn_ec_order = NULL;

  if (!bn_digest || (!buf && (0 != buf_len))) return kEpidBadArgErr;

  do {
    IppStatus ipp_status = ippStsNoErr;
    Ipp8u digest[IPP_SHA256_DIGEST_BITSIZE / 8] = {0};

    const uint8_t secp256r1_r[] = {
        0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xBC, 0xE6, 0xFA, 0xAD, 0xA7, 0x17,
        0x9E, 0x84, 0xF3, 0xB9, 0xCA, 0xC2, 0xFC, 0x63, 0x25, 0x51};

    ipp_status = ippsSHA256MessageDigest(buf, (int)buf_len, digest);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    // convert hash to BigNum for use by ipp
    result = ReadBigNum(digest, sizeof(digest), bn_digest);
    if (kEpidNoErr != result) break;

    result = NewBigNum(sizeof(secp256r1_r), &bn_ec_order);
    if (kEpidNoErr != result) break;

    result = ReadBigNum(secp256r1_r, sizeof(secp256r1_r), bn_ec_order);
    if (kEpidNoErr != result) break;

    ipp_status =
        ippsMod_BN(bn_digest->ipp_bn, bn_ec_order->ipp_bn, bn_digest->ipp_bn);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    result = kEpidNoErr;
  } while (0);

  DeleteBigNum(&bn_ec_order);

  return result;
}

static EpidStatus ValidateSignature(BigNum const* bn_sig_x,
                                    BigNum const* bn_sig_y) {
  EpidStatus result = kEpidSigInvalid;

  BigNum* bn_ec_order = NULL;

  if (!bn_sig_x || !bn_sig_y) return kEpidBadArgErr;

  do {
    IppStatus ipp_status = ippStsNoErr;
    Ipp32u sig_x_cmp0 = IS_ZERO;
    Ipp32u sig_y_cmp0 = IS_ZERO;
    Ipp32u sig_x_cmp_order = IS_ZERO;
    Ipp32u sig_y_cmp_order = IS_ZERO;
    const uint8_t secp256r1_r[] = {
        0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xBC, 0xE6, 0xFA, 0xAD, 0xA7, 0x17,
        0x9E, 0x84, 0xF3, 0xB9, 0xCA, 0xC2, 0xFC, 0x63, 0x25, 0x51};

    result = NewBigNum(sizeof(secp256r1_r), &bn_ec_order);
    if (kEpidNoErr != result) break;

    result = ReadBigNum(secp256r1_r, sizeof(secp256r1_r), bn_ec_order);
    if (kEpidNoErr != result) break;

    ipp_status = ippsCmpZero_BN(bn_sig_x->ipp_bn, &sig_x_cmp0);
    BREAK_ON_IPP_ERROR(ipp_status, result);
    ipp_status = ippsCmpZero_BN(bn_sig_y->ipp_bn, &sig_y_cmp0);
    BREAK_ON_IPP_ERROR(ipp_status, result);
    ipp_status =
        ippsCmp_BN(bn_sig_x->ipp_bn, bn_ec_order->ipp_bn, &sig_x_cmp_order);
    BREAK_ON_IPP_ERROR(ipp_status, result);
    ipp_status =
        ippsCmp_BN(bn_sig_y->ipp_bn, bn_ec_order->ipp_bn, &sig_y_cmp_order);
    BREAK_ON_IPP_ERROR(ipp_status, result);

    if (IS_ZERO == sig_x_cmp0 || IS_ZERO == sig_y_cmp0 ||
        LESS_THAN_ZERO != sig_x_cmp_order ||
        LESS_THAN_ZERO != sig_y_cmp_order) {
      result = kEpidSigInvalid;
      break;
    } else {
      result = kEpidSigValid;
    }
  } while (0);

  DeleteBigNum(&bn_ec_order);

  return result;
}
