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
#ifndef EPID_COMMON_TYPES_H_
#define EPID_COMMON_TYPES_H_

#include <limits.h>  // for CHAR_BIT

/*!
 * \file
 * \brief SDK data types.
 * \defgroup EpidTypes types
 * \ingroup EpidCommon
 * @{
 */

/// Recognized hash algorithms
typedef enum {
  /// SHA-256
  kSha256 = 0,
  /// SHA-384
  kSha384 = 1,
  /// SHA-512
  kSha512 = 2,
  /// SHA-512/256
  kSha512_256 = 3,
  /// Reserved for SHA3/256
  kSha3_256 = 4,
  /// Reserved for SHA3/384
  kSha3_384 = 5,
  /// Reserved for SHA3/512
  kSha3_512 = 6,
} HashAlg;

#pragma pack(1)
/// 8 bit octet string
typedef struct OctStr8 {
  unsigned char data[8 / CHAR_BIT];  ///< 8 bit data
} OctStr8;
/// 32 bit octet string
typedef struct OctStr32 {
  unsigned char data[32 / CHAR_BIT];  ///< 32 bit data
} OctStr32;
/// 64 bit octet string
typedef struct OctStr64 {
  unsigned char data[64 / CHAR_BIT];  ///< 64 bit data
} OctStr64;
/// 128 bit octet string
typedef struct OctStr128 {
  unsigned char data[128 / CHAR_BIT];  ///< 128 bit data
} OctStr128;
/// 256 bit octet string
typedef struct OctStr256 {
  unsigned char data[256 / CHAR_BIT];  ///< 256 bit data
} OctStr256;

/// Serialized BigNum
typedef struct BigNumStr {
  OctStr256 data;  ///< 256 bit octet string
} BigNumStr;
/// a number in [0, p-1]
typedef struct FpElemStr {
  OctStr256 data;  ///< 256 bit octet string
} FpElemStr;
/// a number in [0, q-1]
typedef struct FqElemStr {
  OctStr256 data;  ///< 256 bit octet string
} FqElemStr;

/// Serialized G1 element
typedef struct G1ElemStr {
  FqElemStr x;  ///< an integer between [0, q-1]
  FqElemStr y;  ///< an integer between [0, q-1]
} G1ElemStr;

/// Serialized G2 element
typedef struct G2ElemStr {
  FqElemStr x[2];  ///< an integer between [0, q-1]
  FqElemStr y[2];  ///< an integer between [0, q-1]
} G2ElemStr;

/// Serialized GT element
typedef struct GTElemStr {
  FqElemStr x[12];  ///< an integer between [0, q-1]
} GTElemStr;

typedef FpElemStr PElemStr;
typedef OctStr32 RLver_t;
typedef OctStr32 RLCount;

/// Intel(R) EPID 2.0 Parameters.
/*!
 * INTEL(R) EPID 2.0 parameters: (p, q, b, t, neg, beta, xi0, xi1,
 * g1, g2)
 */
typedef struct Epid2Params {
  BigNumStr p;      ///< a prime
  BigNumStr q;      ///< a prime
  FqElemStr b;      ///< an integer between [0, q-1]
  OctStr64 t;       ///< an integer
  OctStr8 neg;      ///< a boolean
  FqElemStr beta;   ///< an integer between [0, q-1]
  FqElemStr xi[2];  ///< array of integers between [0, q-1]
  G1ElemStr g1;     ///<  a generator (an element) of G1
  G2ElemStr g2;     ///<  a generator (an element) of G2
} EPID2Params;

/// group ID
typedef OctStr32 GroupID;

/// Intel(R) EPID 2.0 group public key
/*!
 * Group public key: (gid, h1, h2, w)
 */
typedef struct GroupPubKey {
  GroupID gid;   ///< group ID
  G1ElemStr h1;  ///< an element in G1
  G1ElemStr h2;  ///< an element in G1
  G2ElemStr w;   ///< an element in G2
} GroupPubKey;

/// Intel(R) EPID 2.0 issuing private key
/*!
 * Issuing private key: (gid, gamma)
 */
typedef struct IPrivKey {
  GroupID gid;      ///< group ID
  FpElemStr gamma;  ///< an integer between [0, p-1]
} IPrivKey;

/// Intel(R) EPID 2.0 private key
/*!
 * Private key: (gid, A, x, f)
 */
typedef struct PrivKey {
  GroupID gid;  ///< group ID
  G1ElemStr A;  ///< an element in G1
  FpElemStr x;  ///< an integer between [0, p-1]
  FpElemStr f;  ///< an integer between [0, p-1]
} PrivKey;

/// 256 bit seed derived from fuse key
typedef OctStr256 Seed;

/// Compressed private key
/*!
 * Compressed Private key: (gid, A.x, seed)
 */
typedef struct CompressedPrivKey {
  GroupID gid;   ///< group ID
  FqElemStr ax;  ///< an integer between [0, p-1]
  Seed seed;     ///< 256 bit rekey seed
} CompressedPrivKey;

/// Membership credential
/*!
 * Membership credential: (gid, A, x)
 */
typedef struct MembershipCredential {
  GroupID gid;  ///< group ID
  G1ElemStr A;  ///< an element in G1
  FpElemStr x;  ///< an integer between [0, p-1]
} MembershipCredential;

/// 256 bit nonce chosen by issuer
typedef OctStr256 IssuerNonce;

/// Join request
/*!
 * Join request: (F, c, s)
 */
typedef struct JoinRequest {
  G1ElemStr F;  ///< an element in G1
  FpElemStr c;  ///< an integer between [0, p-1]
  FpElemStr s;  ///< an integer between [0, p-1]
} JoinRequest;

////////////////////////

/// Intel(R) EPID 2.0 basic signature.
/*!
 * Basic signature: (B, K, T, c, sx, sf, sa, sb)
 */
typedef struct BasicSignature {
  G1ElemStr B;   ///< an element in G1
  G1ElemStr K;   ///< an element in G1
  G1ElemStr T;   ///< an element in G1
  FpElemStr c;   ///< an integer between [0, p-1]
  FpElemStr sx;  ///< an integer between [0, p-1]
  FpElemStr sf;  ///< an integer between [0, p-1]
  FpElemStr sa;  ///< an integer between [0, p-1]
  FpElemStr sb;  ///< an integer between [0, p-1]
} BasicSignature;

///
/*!
 * \brief
 * non-revoked Proof.
 *
 * Non-revoked Proof: (T, c, smu, snu)
 */
typedef struct NrProof {
  G1ElemStr T;    ///< an element in G1
  FpElemStr c;    ///< an integer between [0, p-1]
  FpElemStr smu;  ///< an integer between [0, p-1]
  FpElemStr snu;  ///< an integer between [0, p-1]
} NRProof;

/// Intel(R) EPID 2.0 Signature
/*!
 * Signature: (sigma0, RLver, n2, sigma[0], ..., sigma[n2-1])
 */
typedef struct EpidSignature {
  BasicSignature sigma0;  ///< basic signature
  OctStr32 rl_ver;        ///< revocation list version number
  OctStr32 n2;            ///< number of entries in SigRL
  NRProof sigma[1];       ///< array of non-revoked proofs (flexible array)
} EPIDSignature;

/// private-key based revocation list.
/*!
 * Private-key based revocation list PrivRL: (gid, RLver, n1, f[0],
 * ..., f[n1-1])
 */
typedef struct PrivRl {
  GroupID gid;       ///< group ID
  OctStr32 version;  ///< revocation list version number
  OctStr32 n1;       ///< number of entries in PrivRL
  FpElemStr f[1];    ///< integers between [1, p-1]  (flexible array)
} PrivRl;

/// entry in SigRL (B,K)
typedef struct SigRlEntry {
  G1ElemStr b;  ///< an element of G1
  G1ElemStr k;  ///< an element of G1
} SigRLEntry;

/// signature based revocation list
/*!
 * Signature based revocation list SigRL: (gid, RLver, n2, B[0],
 * K[0], ..., B[n2-1], K[n2-1])
 */
typedef struct SigRl {
  GroupID gid;       ///< group ID
  OctStr32 RLver;  ///< revocation list version number
  OctStr32 n2;       ///< number of entries in SigRL
  SigRLEntry BK[1];  ///< revoked  Bs and Ks (flexible array)
} SigRL;

/// group revocation list
/*!
 * Group revocation list GroupRL: (RLver, n3, gid[0], ...,
 * gid[n3-1])
 */
typedef struct GroupRl {
  OctStr32 version;  ///< revocation list version number
  OctStr32 n3;       ///< number of entries in GroupRL
  GroupID gid[1];    ///< revoked group IDs (flexible array)
} GroupRl;

/*! verifier revocation list
 * Verifier revocation list VerifierRL: (gid, B, RLver, n4, K[0],
 * ..., K[n4-1])
 */
typedef struct VerifierRl {
  GroupID gid;       ///< group ID
  G1ElemStr B;       ///< an element in G1
  OctStr32 version;  ///< revocation list version number
  OctStr32 n4;       ///< number of entries in VerifierRL
  G1ElemStr K[1];    ///< elements in G1 (flexible array)
} VerifierRl;

/*!
* \brief
* member pre-computation blob
*
* \note e12 = 0 implies that this blob is not valid
*/
typedef struct _MemberPrecomp {
  GTElemStr e12;  ///< an element in GT
  GTElemStr e22;  ///< an element in GT
  GTElemStr e2w;  ///< an element in GT
  GTElemStr ea2;  ///< an element in GT
} MemberPrecomp;

/*!
 * \brief
 * verifier pre-computation blob
 *
 * \note e12 = 0 implies that this blob is not valid
*/
typedef struct _VerifierPrecomp {
  GTElemStr e12;  ///< an element in GT
  GTElemStr e22;  ///< an element in GT
  GTElemStr e2w;  ///< an element in GT
  GTElemStr eg12;  ///< an element in GT
} VerifierPrecomp;

/// element to store seed values for later rekey
typedef G1ElemStr ReKeySeed;

/// Serialized Fq2 element
typedef struct Fq2ElemStr {
  FqElemStr a[2];  ///< polynomial coefficient
} Fq2ElemStr;

/// Serialized Fq2^3 element
typedef struct Fq6ElemStr {
  Fq2ElemStr a[3];  ///< polynomial coefficient
} Fq6ElemStr;

/// Serialized Fq2^3^2 element
typedef struct Fq12ElemStr {
  Fq6ElemStr a[2];  ///< polynomial coefficient
} Fq12ElemStr;
#pragma pack()

/*! @} */
#endif  // EPID_COMMON_TYPES_H_
