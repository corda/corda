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
#ifndef EPID_MEMBER_API_H_
#define EPID_MEMBER_API_H_

#include <stddef.h>
#include "epid/common/stdtypes.h"
#include "epid/common/types.h"
#include "epid/common/errors.h"
#include "epid/common/bitsupplier.h"

#ifdef __cplusplus
extern "C"{
#endif

/*!
 * \file
 * \brief Intel(R) EPID SDK member API.
 */

/// Member functionality
/*!
  \defgroup EpidMemberModule member

  Defines the APIs needed by Intel(R) EPID members. Each member
  context (::MemberCtx) represents membership in a single group.

  \ingroup EpidModule
  @{
*/

/// Internal context of member.
typedef struct MemberCtx MemberCtx;

/// Pre-computed member settings.
/*!
 Serialized form of the information about a member that remains stable for
 a given set of keys.

 \note e12 = 0 implies that this data is not valid
 */
#pragma pack(1)
typedef struct MemberPrecomp {
  GtElemStr e12;  ///< an element in GT
  GtElemStr e22;  ///< an element in GT
  GtElemStr e2w;  ///< an element in GT
  GtElemStr ea2;  ///< an element in GT
} MemberPrecomp;

/// Pre-computed signature.
/*!
 Serialized form of an intermediate signature that does not depend on
 basename or message. This can be used to time-shift compute time needed to
 sign a message.
 */
typedef struct PreComputedSignature {
  G1ElemStr B;   ///< an element in G1
  G1ElemStr K;   ///< an element in G1
  G1ElemStr T;   ///< an element in G1
  G1ElemStr R1;  ///< an element in G1
  GtElemStr R2;  ///< an element in G1
  FpElemStr a;   ///< an integer between [0, p-1]
  FpElemStr b;   ///< an integer between [0, p-1]
  FpElemStr rx;  ///< an integer between [0, p-1]
  FpElemStr rf;  ///< an integer between [0, p-1]
  FpElemStr ra;  ///< an integer between [0, p-1]
  FpElemStr rb;  ///< an integer between [0, p-1]
} PreComputedSignature;
#pragma pack()

/// Creates a new member context.
/*!
 Must be called to create the member context that is used by
 other "Member" APIs.

 Allocates memory for the context, then initializes it.

 EpidMemberDelete() must be called to safely release the member context.

 \param[in] pub_key
 The group certificate.
 \param[in] priv_key
 The member private key.
 \param[in] precomp
 Optional pre-computed data. If NULL the value is computed internally and is
 readable using EpidMemberWritePrecomp().
 \param[in] rnd_func
 Random number generator.
 \param[in] rnd_param
 Pass through context data for rnd_func.
 \param[out] ctx
 Newly constructed member context.

 \returns ::EpidStatus

 \warning
 For security rnd_func should be a cryptographically secure random
 number generator.

 \note
 If the result is not ::kEpidNoErr the content of ctx is undefined.

 \see EpidMemberDelete
 \see EpidMemberWritePrecomp
 */
EpidStatus EpidMemberCreate(GroupPubKey const* pub_key, PrivKey const* priv_key,
                            MemberPrecomp const* precomp, BitSupplier rnd_func,
                            void* rnd_param, MemberCtx** ctx);

/// Deletes an existing member context.
/*!
 Must be called to safely release a member context created using
 EpidMemberCreate().

 De-initializes the context, frees memory used by the context, and sets the
 context pointer to NULL.

 \param[in,out] ctx
 The member context. Can be NULL.

 \see EpidMemberCreate
 */
void EpidMemberDelete(MemberCtx** ctx);

/// Serializes the pre-computed member settings.
/*!
 \param[in] ctx
 The member context.
 \param[out] precomp
 The Serialized pre-computed member settings.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr, the content of precomp is undefined.
 */
EpidStatus EpidMemberWritePrecomp(MemberCtx const* ctx, MemberPrecomp* precomp);

/// Sets the hash algorithm to be used by a member.
/*!
 \param[in] ctx
 The member context.
 \param[in] hash_alg
 The hash algorithm to use.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr, the hash algorithm used by the member is
 undefined.

 \see EpidMemberCreate
 \see ::HashAlg
 */
EpidStatus EpidMemberSetHashAlg(MemberCtx* ctx, HashAlg hash_alg);

/// Computes the size in bytes required for an Intel(R) EPID signature.
/*!
 \param[in] sig_rl
 The signature based revocation list that is used. NULL is treated as
 a zero length list.

 \returns
 Size in bytes of an Intel(R) EPID signature including proofs for each entry
 in the signature based revocation list.

 \see ::SigRl
*/
size_t EpidGetSigSize(SigRl const* sig_rl);

/// Writes an Intel(R) EPID signature.
/*!
 \param[in] ctx
 The member context.
 \param[in] msg
 The message to sign.
 \param[in] msg_len
 The length in bytes of message.
 \param[in] basename
 Optional basename. If basename is NULL a random basename is used.
 Signatures generated using random basenames are anonymous. Signatures
 generated using the same basename are linkable by the verifier. If a
 basename is provided, it must already be registered, or
 ::kEpidBadArgErr is returned.
 \param[in] basename_len
 The size of basename in bytes. Must be 0 basename is NULL.
 \param[in] sig_rl
 The signature based revocation list.
 \param[in] sig_rl_size
 The size in bytes of the signature based revocation list.
 \param[out] sig
 The generated signature
 \param[in] sig_len
 The size of signature in bytes. Must be equal to value returned by
 EpidGetSigSize().

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the content of sig is undefined.

 \see
 EpidMemberCreate
 \see
 EpidMemberSetHashAlg
 \see
 EpidGetSigSize
 */
EpidStatus EpidSign(MemberCtx const* ctx, void const* msg, size_t msg_len,
                    void const* basename, size_t basename_len,
                    SigRl const* sig_rl, size_t sig_rl_size, EpidSignature* sig,
                    size_t sig_len);

/// Registers a basename with a member.
/*!

 To prevent loss of privacy, the member keeps a list of basenames
 (corresponding to authorized verifiers). The member signs a message
 with a basename only if the basename is in the member's basename
 list.

 \warning
 The use of a name-based signature creates a platform unique
 pseudonymous identifier. Because it reduces the member's privacy, the
 user should be notified when it is used and should have control over
 its use.

 \param[in] ctx
 The member context.
 \param[in] basename
 The basename.
 \param[in] basename_len
 Length of the basename.

 \returns ::EpidStatus

 \retval ::kEpidDuplicateErr
 The basename was already registered.

 \note
 If the result is not ::kEpidNoErr or ::kEpidDuplicateErr it is undefined if the
 basename is registered.
 */
EpidStatus EpidRegisterBaseName(MemberCtx* ctx, void const* basename,
                                size_t basename_len);

/// Extends the member's pool of pre-computed signatures.
/*!
 Can either generate new pre-computed signatures or import existing ones.
 ::EpidWritePreSigs can be used to export pre-computed signatures.

 \param[in] ctx
 The member context.
 \param[in] number_presigs
 The number of pre-computed signatures to add to the internal pool.
 \param[in,out] presigs
 Optional array of valid pre-computed signatures to import. If presigs is not
 NULL it most contain at least number_presigs pre-computed signatures.

 \returns ::EpidStatus

 \note
 presigs buffer is zeroed out before return to prevent pre-computed
 signatures from being reused.

 \note
 If the result is not ::kEpidNoErr the state of the pre-computed signature
 pool, and of presigs, is undefined.

 \see ::EpidMemberCreate
 \see ::EpidWritePreSigs
 */
EpidStatus EpidAddPreSigs(MemberCtx* ctx, size_t number_presigs,
                          PreComputedSignature* presigs);

/// Gets the number of pre-computed signatures in the member's pool.
/*!
 \param[in] ctx
 The member context.

 \returns
 Number of remaining pre-computed signatures. Returns 0 if ctx is NULL.

 \see ::EpidMemberCreate
 \see ::EpidWritePreSigs
*/
size_t EpidGetNumPreSigs(MemberCtx const* ctx);

/// Serializes pre-computed signatures from the member's pool.
/*!
 Removes requested number of pre-computed signatures from member's pool and
 stores them in presigs array. Use ::EpidAddPreSigs to add pre-computed
 signatures to the pool.

 \param[in] ctx
 The member context.
 \param[out] presigs
 An existing buffer of pre-computed signatures.
 \param[in] number_presigs
 Number of pre-computed signatures to read. Number_presigs must not be greater
 than the value returned by ::EpidGetNumPreSigs.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the state of the pre-computed signature
 pool, and of presigs, is undefined.

 \see ::EpidMemberCreate
 \see ::EpidGetNumPreSigs
 \see ::EpidAddPreSigs
*/
EpidStatus EpidWritePreSigs(MemberCtx* ctx, PreComputedSignature* presigs,
                            size_t number_presigs);

/// Creates a request to join a group.
/*!
 The created request is part of the interaction with an issuer needed to join
 a group. This interaction with the issuer is outside the scope of this API.

 \param[in] pub_key
 The group certificate of group to join.
 \param[in] ni
 The nonce chosen by issuer as part of join protocol.
 \param[in] f
 A randomly selected integer in [1, p-1].
 \param[in] rnd_func
 Random number generator.
 \param[in] rnd_param
 Pass through context data for rnd_func.
 \param[in] hash_alg
 The hash algorithm to be used.
 \param[out] join_request
 The join request.

 \returns ::EpidStatus

 \warning
 For security rnd_func should be a cryptographically secure random
 number generator.

 \note
 The default hash algorithm in Member is SHA-512. This is the
 recommended option if you do not override the hash algorithm
 elsewhere.

 \note
 If the result is not ::kEpidNoErr, the content of join_request is undefined.

 \see ::HashAlg
 */
EpidStatus EpidRequestJoin(GroupPubKey const* pub_key, IssuerNonce const* ni,
                           FpElemStr const* f, BitSupplier rnd_func,
                           void* rnd_param, HashAlg hash_alg,
                           JoinRequest* join_request);

/// Creates a basic signature for use in constrained environment.
/*!
 Used in constrained environments where, due to limited memory, it may not
 be possible to process through a large and potentially unbounded revocation
 list.

 \param[in] ctx
 The member context.
 \param[in] msg
 The message.
 \param[in] msg_len
 The length of message in bytes.
 \param[in] basename
 Optional basename. If basename is NULL a random basename is used.
 Signatures generated using random basenames are anonymous. Signatures
 generated using the same basename are linkable by the verifier. If a
 basename is provided it must already be registered or
 ::kEpidBadArgErr is returned.
 \param[in] basename_len
 The size of basename in bytes. Must be 0 basename is NULL.
 \param[out] sig
 The generated basic signature

 \returns ::EpidStatus

 \note
 This function should be used in conjunction with EpidNrProve()

 \note
 If the result is not ::kEpidNoErr the content of sig, is undefined.

 \see EpidMemberCreate
 \see EpidNrProve
 */
EpidStatus EpidSignBasic(MemberCtx const* ctx, void const* msg, size_t msg_len,
                         void const* basename, size_t basename_len,
                         BasicSignature* sig);

/// Calculates a non-revoked proof for a single signature based revocation
/// list entry.
/*!
 Used in constrained environments where, due to limited memory, it may not
 be possible to process through a large and potentially unbounded revocation
 list.

 \param[in] ctx
 The member context.
 \param[in] msg
 The message.
 \param[in] msg_len
 The length of message in bytes.
 \param[in] sig
 The basic signature.
 \param[in] sigrl_entry
 The signature based revocation list entry.
 \param[out] proof
 The generated non-revoked proof.

 \returns ::EpidStatus

 \note
 This function should be used in conjunction with EpidSignBasic().

 \note
 If the result is not ::kEpidNoErr, the content of proof is undefined.

 \see EpidMemberCreate
 \see EpidSignBasic
 */
EpidStatus EpidNrProve(MemberCtx const* ctx, void const* msg, size_t msg_len,
                       BasicSignature const* sig, SigRlEntry const* sigrl_entry,
                       NrProof* proof);

/// Tests if a member private key is valid without checking revocation.
/*!
 Used to check that a member private key is a valid key for a group. This
 is useful as a cross check when creating a new member private key as part of
 the join process

 \param[in] pub_key
 The public key of the group.
 \param[in] priv_key
 The private key to check.

 \result bool

 \retval true
 if the private key is valid for the group of the public key
 \retval false
 if the private key is not valid for the group of the public key


 \see EpidRequestJoin
 */
bool EpidIsPrivKeyInGroup(GroupPubKey const* pub_key, PrivKey const* priv_key);

/// Decompresses compressed member private key.
/*!

  Converts a compressed member private key into a member
  private key for use by other member APIs.

  \param[in] pub_key
  The public key of the group.
  \param[in] compressed_privkey
  The compressed member private key to be decompressed.
  \param[out] priv_key
  The member private key.

  \returns ::EpidStatus
 */
EpidStatus EpidDecompressPrivKey(GroupPubKey const* pub_key,
                                 CompressedPrivKey const* compressed_privkey,
                                 PrivKey* priv_key);

/*! @} */

#ifdef __cplusplus
}
#endif

#endif  // EPID_MEMBER_API_H_
