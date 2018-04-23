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
#ifndef EPID_COMMON_1_1_TYPES_H_
#define EPID_COMMON_1_1_TYPES_H_

/*!
* \file
* \brief SDK data types for Intel(R) EPID 1.1.
*/

#include <limits.h>  // for CHAR_BIT

#include "epid/common/types.h"

/// Intel(R) EPID 1.1 specific data types.
/*!
\defgroup Epid11Types EPID 1.1 specific types
Defines serialized data types used by the SDK. These data types
are only used by components that need to do Intel(R) EPID 1.1
verification.

\ingroup EpidTypes
\see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
@{
*/

#pragma pack(1)

/// 80 bit octet string
typedef struct OctStr80 {
  unsigned char data[80 / CHAR_BIT];  ///< 80 bit data
} OctStr80;

/// 600 bit octet string
typedef struct OctSt600 {
  unsigned char data[600 / CHAR_BIT];  ///< 600 bit data
} OctStr600;

/// 768 bit octet string
typedef struct OctStr768 {
  unsigned char data[768 / CHAR_BIT];  ///< 768 bit data
} OctStr768;

/// Serialized Fq3 element
typedef struct Fq3ElemStr {
  FqElemStr a[3];  ///< polynomial coefficient
} Fq3ElemStr;

/// Serialized Intel(R) EPID 1.1 G1 element
typedef G1ElemStr Epid11G1ElemStr;

/// Serialized Intel(R) EPID 1.1 G3 element
typedef G1ElemStr Epid11G3ElemStr;

/// Serialized Intel(R) EPID 1.1 G2 element
typedef struct Epid11G2ElemStr {
  FqElemStr x[3];  ///< an integer between [0, q-1]
  FqElemStr y[3];  ///< an integer between [0, q-1]
} Epid11G2ElemStr;

/// Serialized Intel(R) EPID 1.1 GT element
typedef struct Epid11GtElemStr {
  Fq3ElemStr a[2];  ///< an element in Fq3
} Epid11GtElemStr;

/// Intel(R) EPID 1.1 Parameters.
/*!
Intel(R) EPID 1.1 parameters are: (p, q, h, a, b, coeff, qnr, orderG2, p', q',
h', a', b', g1, g2, g3). The size of the Intel(R) EPID public parameters of G1,
G2, G3, and GT is 6464 bits or 808 bytes.
*/
typedef struct Epid11Params {
  BigNumStr p;  ///< a prime
  BigNumStr q;  ///< a prime
  OctStr32 h;   ///< a small integer, also denoted as cofactor
  FqElemStr a;  ///< an integer between [0, q-1]
  FqElemStr b;  ///< an integer between [0, q-1]
  /*! the coefficients of an irreducible polynomial
  coeff[0], coeff[1], coeff[2] are 256-bit integers between [0, q - 1]*/
  BigNumStr coeff[3];
  FqElemStr qnr;      ///< a quadratic non-residue (an integer between [0, q-1])
  OctStr768 orderG2;  ///< the total number of points in G2 elliptic curve
  BigNumStr p_tick;   ///< a prime
  BigNumStr q_tick;   ///< a prime
  OctStr32 h_tick;    ///< a small integer, also denoted as cofactor
  FqElemStr a_tick;   ///< an integer between [0, q-1]
  FqElemStr b_tick;   ///< an integer between [0, q-1]
  Epid11G1ElemStr g1;  ///< a generator(an element) of G1
  Epid11G2ElemStr g2;  ///<  a generator (an element) of G2
  Epid11G1ElemStr g3;  ///<  a generator (an element) of G3
} Epid11Params;

/// Intel(R) EPID 1.1 group ID
typedef OctStr32 Epid11GroupId;

/// Intel(R) EPID 1.1 group public key
/*!
* Group public key: (gid, h1, h2, w)
*/
typedef struct Epid11GroupPubKey {
  Epid11GroupId gid;   ///< group ID
  Epid11G1ElemStr h1;  ///< an element in G1
  Epid11G1ElemStr h2;  ///< an element in G1
  Epid11G2ElemStr w;   ///< an element in G2
} Epid11GroupPubKey;

/// Intel(R) EPID 1.1 basic signature.
/*!
* Basic signature: (B, K, T1, T2, c, nd, sx, sy, sf, sa, sb, salpha, sbeta)
*/
typedef struct Epid11BasicSignature {
  OctStr32 bv;         ///For backward compatibility only
  Epid11G3ElemStr B;   ///< an element in G3
  Epid11G3ElemStr K;   ///< an element in G3
  Epid11G1ElemStr T1;  ///< an element in G1
  Epid11G1ElemStr T2;  ///< an element in G1
  OctStr256 c;         ///< a 256-bit integer
  OctStr80 nd;         ///< an 80-bit integer
  FpElemStr sx;        ///< an integer between [0, p-1]
  FpElemStr sy;        ///< an integer between [0, p-1]
  OctStr600 sf;        ///< a 593-bit integer
  FpElemStr sa;        ///< an integer between [0, p-1]
  FpElemStr sb;        ///< an integer between [0, p-1]
  FpElemStr salpha;    ///< an integer between [0, p-1]
  FpElemStr sbeta;     ///< an integer between [0, p-1]
} Epid11BasicSignature;

/// Intel(R) EPID 1.1 non-revoked Proof
/*!
* Non-revoked Proof: (T, c, smu, snu)
*/
typedef struct Epid11NrProof {
  Epid11G3ElemStr T;  ///< an element in G3
  OctStr256 c;        ///< a 256-bit integer
  FpElemStr smu;      ///< an integer between [0, p'-1]
  FpElemStr snu;      ///< an integer between [0, p'-1]
} Epid11NrProof;

/// Intel(R) EPID 1.1 Signature
/*!
* Signature: (sigma0, RLver, n2, sigma[0], ..., sigma[n2-1])
*/
typedef struct Epid11Signature {
  Epid11BasicSignature sigma0;  ///< basic signature
  OctStr32 rl_ver;              ///< revocation list version number
  OctStr32 n2;                  ///< number of entries in SigRL
  Epid11NrProof sigma[1];  ///< array of non-revoked proofs (flexible array)
} Epid11Signature;

/// Intel(R) EPID 1.1 private-key based revocation list
/*!
* Private-key based revocation list PrivRL: (gid, RLver, n1, f[0],
* ..., f[n1-1])
*/
typedef struct Epid11PrivRl {
  Epid11GroupId gid;  ///< group ID
  OctStr32 version;   ///< revocation list version number
  OctStr32 n1;        ///< number of entries in PrivRL
  FpElemStr f[1];     ///< integers between [1, p-1]  (flexible array)
} Epid11PrivRl;

/// Intel(R) EPID 1.1 entry in SigRL (B,K)
typedef struct Epid11SigRlEntry {
  Epid11G3ElemStr b;  ///< an element of G1
  Epid11G3ElemStr k;  ///< an element of G1
} Epid11SigRlEntry;

/// Intel(R) EPID 1.1 signature based revocation list
/*!
* Signature based revocation list SigRL: (gid, RLver, n2, B[0],
* K[0], ..., B[n2-1], K[n2-1])
*/
typedef struct Epid11SigRl {
  Epid11GroupId gid;       ///< group ID
  OctStr32 version;        ///< revocation list version number
  OctStr32 n2;             ///< number of entries in SigRL
  Epid11SigRlEntry bk[1];  ///< revoked  Bs and Ks (flexible array)
} Epid11SigRl;

/// Intel(R) EPID 1.1 group revocation list
/*!
* Group revocation list GroupRL: (RLver, n3, gid[0], ...,
* gid[n3-1])
*/
typedef struct Epid11GroupRl {
  OctStr32 version;      ///< revocation list version number
  OctStr32 n3;           ///< number of entries in GroupRL
  Epid11GroupId gid[1];  ///< revoked group IDs (flexible array)
} Epid11GroupRl;

#pragma pack()

/*! @} */
#endif  // EPID_COMMON_1_1_TYPES_H_
