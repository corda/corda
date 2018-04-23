/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */


/**
@file   X509AlgoType.h
@author Kapil Anantharaman
@brief  This file contains the algo types id's for differeent Signing and Public Key algorithms
*/

#ifndef _X509ALGOTYPE_H_
#define _X509ALGOTYPE_H_

/* All algorithm OIDs used */
typedef enum{
  
   /* OID = 1 2 840 113549 1 1 2 */
   X509_md2withRSAEncryption = 0,

   /* OID = 1 2 840 113549 1 1 3 */
   X509_md4withRSAEncryption,

   /* OID = 1 2 840 113549 1 1 4 */
   X509_md5withRSAEncryption,

   /* OID = 1 2 840 113549 1 1 5 */
   X509_sha1withRSAEncryption,

   /* OID = 1 2 840 113549 1 1 7 */
   X509_rsaOAEP,

   /* OID = 1 2 840 113549 1 1 8 */
   X509_pkcs1_MGF,

   /* OID = 1 2 840 113549 1 1 9 */
   X509_rsaOAEP_pSpecified,

   /* OID = 1 2 840 113549 1 1 10 */
   X509_rsaPSS,

   /* OID = 1 2 840 113549 1 1 11 */
   X509_sha256WithRSAEncryption,

   /* OID = 1 2 840 113549 1 1 12 */
   X509_sha384WithRSAEncryption,

   /* OID = 1 2 840 113549 1 1 13 */
   X509_sha512WithRSAEncryption,

   /* OID = 1 2 840 113549 1 1 14 */
   X509_sha224WithRSAEncryption,
      
   /* OID = 1 2 840 10045 4 1 */
   X509_ecdsa_with_SHA1,

   /* OID = 1 2 840 10045 4 3 2 */
   X509_ecdsa_with_SHA256,
   
   X509_Max_Signature_Algorithms_supported
   
} X509SignAlgoType;

/* what algorithm is the subject's public key */
typedef enum{
   
   /* OID = 1 2 840 113549 1 1 1 */
   X509_rsaPublicKey = 0,
   
   /* OID = 1 2 840 10045 2 1 */
   X509_ecdsaPublicKey,
   
   /* OID = 1 2 840 113741 1 9 4 1 */
   X509_intel_sigma_epidGroupPublicKey_epid10,
   
   /* OID = 1 2 840 113741 1 9 4 2 */
   X509_intel_sigma_epidGroupPublicKey_epid11,
   
   /* OID = 1 2 840 113741 1 9 4 3 */
   X509_intel_sigma_epidGroupPublicKey_epid20  ,

   X509_Max_PublicKey_Algorithms_supported
} X509PublicKeyAlgoType;

#endif
