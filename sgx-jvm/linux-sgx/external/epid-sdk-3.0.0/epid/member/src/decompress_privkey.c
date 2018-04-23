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
 * \brief EpidDecompressPrivKey implementation.
 */

#include "epid/member/api.h"

#include "epid/common/src/memory.h"
#include "epid/common/src/epid2params.h"
#include "epid/common/math/src/bignum-internal.h"
#include "epid/common/math/hash.h"
#include "epid/member/src/privkey.h"

/// Handle Intel(R) EPID Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

/// Implements the derivation method used by private key decompression
/// Derives two integers x, f between [1, p-1] from the seed value
static EpidStatus DeriveXF(FpElemStr* x, FpElemStr* f, Seed const* seed,
                           FpElemStr const* p);

EpidStatus EpidDecompressPrivKey(GroupPubKey const* pub_key,
                                 CompressedPrivKey const* compressed_privkey,
                                 PrivKey* priv_key) {
  EpidStatus result = kEpidErr;
  Epid2Params_* epid2_params = 0;
  PrivKey_ priv_key_ = {{{0}}, 0, 0, 0};
  FfElement* Ax = 0;
  EcPoint* t1 = 0;
  EcPoint* t2 = 0;
  FfElement* t3 = 0;
  FfElement* t4 = 0;
  BigNum* bn_pminus1 = 0;
  BigNum* bn_one = 0;
  EcPoint* h1 = 0;
  EcPoint* w = 0;

  // check parameters
  if (!pub_key || !compressed_privkey || !priv_key) {
    return kEpidBadArgErr;
  }

  // Internal representation of Epid2Params
  result = CreateEpid2Params(&epid2_params);
  if (kEpidNoErr != result) {
    return result;
  }

  do {
    uint8_t bn_one_str = 1;
    FpElemStr p_str = {0};
    bool is_valid = false;
    // shortcuts
    EcGroup* G1 = epid2_params->G1;
    EcGroup* G2 = epid2_params->G2;
    FiniteField* GT = epid2_params->GT;
    EcPoint* g1 = epid2_params->g1;
    EcPoint* g2 = epid2_params->g2;
    PairingState* ps_ctx = epid2_params->pairing_state;
    FiniteField* Fp = epid2_params->Fp;
    FiniteField* Fq = epid2_params->Fq;
    BigNum* p = epid2_params->p;

    // In the following process, temporary variables t1 (an element of
    // G2), t2 (an element of G1), t3, t4 (elements of GT) are used.
    // Let the compressed private key be (gid, A.x, seed). Let the
    // Intel(R) EPID public key be (gid, h1, h2, w).

    // Create a new Priv Key
    result = NewEcPoint(G1, &priv_key_.A);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(Fp, &priv_key_.x);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(Fp, &priv_key_.f);
    BREAK_ON_EPID_ERROR(result);

    result = NewFfElement(Fq, &Ax);
    BREAK_ON_EPID_ERROR(result);
    result = NewEcPoint(G2, &t1);
    BREAK_ON_EPID_ERROR(result);
    result = NewEcPoint(G1, &t2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(GT, &t3);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(GT, &t4);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(sizeof(BigNumStr), &bn_pminus1);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(sizeof(bn_one_str), &bn_one);
    BREAK_ON_EPID_ERROR(result);

    result = NewEcPoint(G1, &h1);
    BREAK_ON_EPID_ERROR(result);
    result = ReadEcPoint(G1, &(pub_key->h1), sizeof(pub_key->h1), h1);
    BREAK_ON_EPID_ERROR(result);
    result = NewEcPoint(G2, &w);
    BREAK_ON_EPID_ERROR(result);
    result = ReadEcPoint(G2, &(pub_key->w), sizeof(pub_key->w), w);
    BREAK_ON_EPID_ERROR(result);

    result = WriteBigNum(p, sizeof(p_str), &p_str);
    BREAK_ON_EPID_ERROR(result);

    result = ReadBigNum(&bn_one_str, sizeof(bn_one_str), bn_one);
    BREAK_ON_EPID_ERROR(result);

    // 1. The member derives x and f from seed. The derivation
    //    function must be the same as the one used in the key
    //    generation above. This step is out of scope of this
    //    specification.
    result =
        DeriveXF(&priv_key->x, &priv_key->f, &compressed_privkey->seed, &p_str);
    BREAK_ON_EPID_ERROR(result);
    // 2. The member computes A = G1.makePoint(A.x).
    result = ReadFfElement(Fq, &compressed_privkey->ax,
                           sizeof(compressed_privkey->ax), Ax);
    BREAK_ON_EPID_ERROR(result);
    result = EcMakePoint(G1, Ax, priv_key_.A);
    BREAK_ON_EPID_ERROR(result);
    // 3. The member tests whether (A, x, f) is a valid Intel(R) EPID
    //    private key as follows:
    //   a. It computes t1 = G2.sscmExp(g2, x).
    result = EcSscmExp(G2, g2, (BigNumStr const*)&priv_key->x, t1);
    BREAK_ON_EPID_ERROR(result);
    //   b. It computes t1 = G2.mul(t1, w).
    result = EcMul(G2, t1, w, t1);
    BREAK_ON_EPID_ERROR(result);
    //   c. It computes t3 = pairing(A, t1).
    result = Pairing(ps_ctx, t3, priv_key_.A, t1);
    BREAK_ON_EPID_ERROR(result);
    //   d. It computes t2 = G1.sscmExp(h1, f).
    result = EcSscmExp(G1, h1, (BigNumStr const*)&priv_key->f, t2);
    BREAK_ON_EPID_ERROR(result);
    //   e. It computes t2 = G1.mul(t2, g1).
    result = EcMul(G1, t2, g1, t2);
    BREAK_ON_EPID_ERROR(result);
    //   f. It computes t4 = pairing(t2, g2).
    result = Pairing(ps_ctx, t4, t2, g2);
    BREAK_ON_EPID_ERROR(result);
    //   g. If GT.isEqual(t3, t4) = false
    result = FfIsEqual(GT, t3, t4, &is_valid);
    BREAK_ON_EPID_ERROR(result);
    if (!is_valid) {
      //   i.   It computes t3 = GT.exp(t3, p-1).
      result = BigNumSub(p, bn_one, bn_pminus1);
      BREAK_ON_EPID_ERROR(result);
      result = FfExp(GT, t3, bn_pminus1, t3);
      BREAK_ON_EPID_ERROR(result);
      //   ii.  If GT.isEqual(t3, t4) = false again, it reports bad
      //        Intel(R) EPID private key and exits.
      result = FfIsEqual(GT, t3, t4, &is_valid);
      BREAK_ON_EPID_ERROR(result);
      if (!is_valid) {
        result = kEpidBadArgErr;  // Invalid Member key
        break;
      }
      //   iii. It sets A = G1.inverse(A).
      result = EcInverse(G1, priv_key_.A, priv_key_.A);
      BREAK_ON_EPID_ERROR(result);
      //   NOTE A is modified here in this step.
    }
    // 4. The decompressed Intel(R) EPID private key is (gid, A, x, f).
    // x, f already filled in.
    priv_key->gid = pub_key->gid;
    result = WriteEcPoint(G1, priv_key_.A, &priv_key->A, sizeof(priv_key->A));
    BREAK_ON_EPID_ERROR(result);

    result = kEpidNoErr;
  } while (0);

  DeleteEcPoint(&priv_key_.A);
  DeleteFfElement(&priv_key_.x);
  DeleteFfElement(&priv_key_.f);
  DeleteFfElement(&Ax);
  DeleteEcPoint(&t1);
  DeleteEcPoint(&t2);
  DeleteFfElement(&t3);
  DeleteFfElement(&t4);
  DeleteBigNum(&bn_pminus1);
  DeleteBigNum(&bn_one);
  DeleteEcPoint(&h1);
  DeleteEcPoint(&w);
  DeleteEpid2Params(&epid2_params);

  return result;
}

/// Hash message buffer
typedef struct HashMsg {
  /// Message to be hashed
  char data[11];
} HashMsg;

static EpidStatus DeriveXF(FpElemStr* x, FpElemStr* f, Seed const* seed,
                           FpElemStr const* p) {
  EpidStatus result = kEpidErr;

  BigNum* bn_x = 0;
  BigNum* bn_f = 0;
  BigNum* bn_p = 0;

  do {
    HashMsg msgstr = {{
        0x00, 0x45, 0x43, 0x43, 0x2d, 0x53, 0x61, 0x66, 0x65, 0x49, 0x44,
    }};
#pragma pack(1)
    struct {
      Seed seed;
      HashMsg msg;
    } hashbuf;
#pragma pack()

    Sha256Digest digest[2];
    Ipp8u str512[512 / 8];

    result = NewBigNum(sizeof(*p), &bn_p);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(p, sizeof(*p), bn_p);
    BREAK_ON_EPID_ERROR(result);

    result = NewBigNum(sizeof(digest), &bn_x);
    BREAK_ON_EPID_ERROR(result);
    result = NewBigNum(sizeof(digest), &bn_f);
    BREAK_ON_EPID_ERROR(result);

    // compute x
    hashbuf.seed = *seed;
    hashbuf.msg = msgstr;
    hashbuf.msg.data[0] = 0x06;
    result = Sha256MessageDigest(&hashbuf, sizeof(hashbuf), &digest[0]);
    BREAK_ON_EPID_ERROR(result);
    hashbuf.msg.data[0] = 0x07;
    result = Sha256MessageDigest(&hashbuf, sizeof(hashbuf), &digest[1]);
    BREAK_ON_EPID_ERROR(result);

    result = ReadBigNum(&digest, sizeof(digest), bn_x);
    BREAK_ON_EPID_ERROR(result);

    result = BigNumMod(bn_x, bn_p, bn_x);
    BREAK_ON_EPID_ERROR(result);

    result = WriteBigNum(bn_x, sizeof(str512), str512);
    BREAK_ON_EPID_ERROR(result);

    *x = *(FpElemStr*)&str512[sizeof(str512) / 2];

    // compute f
    hashbuf.seed = *seed;
    hashbuf.msg = msgstr;
    hashbuf.msg.data[0] = 0x08;
    result = Sha256MessageDigest(&hashbuf, sizeof(hashbuf), &digest[0]);
    BREAK_ON_EPID_ERROR(result);
    hashbuf.msg.data[0] = 0x09;
    result = Sha256MessageDigest(&hashbuf, sizeof(hashbuf), &digest[1]);
    BREAK_ON_EPID_ERROR(result);

    result = ReadBigNum(&digest, sizeof(digest), bn_f);
    BREAK_ON_EPID_ERROR(result);

    result = BigNumMod(bn_f, bn_p, bn_f);
    BREAK_ON_EPID_ERROR(result);

    result = WriteBigNum(bn_f, sizeof(str512), str512);
    BREAK_ON_EPID_ERROR(result);

    *f = *(FpElemStr*)&str512[sizeof(str512) / 2];

    result = kEpidNoErr;
  } while (0);

  DeleteBigNum(&bn_x);
  DeleteBigNum(&bn_f);
  DeleteBigNum(&bn_p);

  return result;
}
