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
#ifndef _PROTECTED_OUTPUT_CERTIFICATES_H
#define _PROTECTED_OUTPUT_CERTIFICATES_H

#ifdef __cplusplus
extern "C" {
#endif

#pragma pack(push)
#pragma pack(1)

/*
 *	EC-DSA keys and signatures lengths
 */
#define ECDSA_PRIVKEY_LEN		32
#define ECDSA_PUBKEY_LEN		64
#define ECDSA_SECKEY_LEN		32
#define ECDSA_SIGNATURE_LEN		64
/* Data structures */
// EC-DSA Private Key
typedef unsigned char EcDsaPrivKey[ECDSA_PRIVKEY_LEN];
// EC-DSA Public Key
typedef unsigned char EcDsaPubKey[ECDSA_PUBKEY_LEN];
// EC-DSA Session Key
typedef unsigned char EcDsaSecKey[ECDSA_SECKEY_LEN];
// EC-DSA Signature
typedef unsigned char EcDsaSig[ECDSA_SIGNATURE_LEN];

/*
 *	3rd Party Certificate
 */
// Type of the certificate: PROTECTED_OUTPUT
#define PUBCERT3P_TYPE_PROTECTED_OUTPUT							0x00000000
#define PUBCERT3P_TYPE_MV_SRV						0x00000001

#define PUBCERT3P_TYPE_RESERVED						0x00000000
#define PUBCERT3P_TYPE_AACS_PLAYBACK				0x00000001
#define PUBCERT3P_TYPE_AACS_ADVANCED_USAGE			0x00000002
#define PUBCERT3P_TYPE_AACS_ISV_KEY_PROVISIONING	0x00000003

// Issuer id: Intel
#define PUBCERT3P_ISSUER_ID		0x00000000

// PROTECTED_OUTPUT 1.5 3rd Party Certificate
typedef struct _Cert3p {
	// 3rd Party signed part
	struct _SignBy3p {
		unsigned int		CertificateType;
		unsigned char		TimeValidStart[8];
		unsigned char		TimeValidEnd[8];
		unsigned int		Id3p;
		unsigned int		IssuerId;
		EcDsaPubKey			PubKey3p;
	} SignBy3p;
	EcDsaSig				Sign3p;

	// Intel signed part,
	struct _SignedByIntel
	{
		unsigned char		TimeValidStart[8];
		unsigned char		TimeValidEnd[8];
		EcDsaPubKey			PubKeyVerify3p;
	} SignByIntel;
	EcDsaSig				SignIntel;
} Cert3p;

//	3rd Party Certificate Signed By Intel structs, added after RCRs / MV1.0 in 2010
typedef struct _Cert3pIntelSigned {
		unsigned char		TimeValidStart[8];
		unsigned char		TimeValidEnd[8];
		EcDsaPubKey			PubKeyVerify3p;
		EcDsaSig			SignIntel;
} Cert3pIntelSigned;

typedef struct _Cert3pIntelSigned1 {
		unsigned short		IntelSignedVersion;
	    unsigned char		TimeValidStart[8];
		unsigned char		TimeValidEnd[8];
		unsigned short		IntelSignedCertificateType;
		EcDsaPubKey			PubKeyVerify3p;
		EcDsaSig			SignIntel;
} Cert3pIntelSigned1;

// PROTECTED_OUTPUT2.0/MV 1.0 3rd Party Certificate
typedef struct _Cert3pMV {
	// 3rd Party signed part
	struct _SignBy3p {
		unsigned int		CertificateType;
		unsigned char		TimeValidStart[8];
		unsigned char		TimeValidEnd[8];
		unsigned int		Id3p;
		unsigned int		IssuerId;
		EcDsaPubKey			PubKey3p;
	} SignBy3p;

	EcDsaSig				Sign3p;

	// Intel signed part, modified after RCRs
	union 
	{
		Cert3pIntelSigned	IntelSigned;
		Cert3pIntelSigned1	IntelSigned1;
	}						SignByIntel;
} Cert3pMV;

/*
 *	Cert3pType, enumerates type of new Cert3p type, added in RCR, define if MV command are allowed
 */

typedef enum _Cert3pType
{
	PROTECTED_OUTPUT_EPID_PUBCERT3P_TYPE_PROTECTED_OUTPUT15,	//IntelSignedVersion = 0, IntelSignedCertificateType = 0; 
	PROTECTED_OUTPUT_EPID_PUBCERT3P_TYPE_PROTECTED_OUTPUT20,	//IntelSignedVersion = 1, IntelSignedCertificateType = 0; 
	PROTECTED_OUTPUT_EPID_PUBCERT3P_TYPE_MV_APP,	//IntelSignedVersion = 1, IntelSignedCertificateType = 1; 
	PROTECTED_OUTPUT_EPID_PUBCERT3P_TYPE_MV_SRV		//IntelSignedVersion = 1, IntelSignedCertificateType = 1, CertificateType = 0x00000001 (Server) for Trusted Time
} Cert3pType;


/*
 *	SafeID Certificate
 */
// PCH SafeID Public Certificate
typedef struct _SafeIdCert {
	unsigned char		sver[2];
	unsigned char		blobid[2];
	unsigned int		Gid;
	unsigned char		h1[64];
	unsigned char		h2[64];
	unsigned char		w[192];
	EcDsaSig			SignIntel;
} SafeIdCert;

/*
 *	SafeID Standard Parameters (crtyptosystem context)
 */
typedef struct _SafeIdParams {
	unsigned char	sver[2];
	unsigned char	blobid[2];
	unsigned char	p[32];
	unsigned char	q[32];
	unsigned char	h[4];
	unsigned char	a[32];
	unsigned char	b[32];
	unsigned char	coeff0[32];
	unsigned char	coeff1[32];
	unsigned char	coeff2[32];
	unsigned char	qnr[32];
	unsigned char	orderG2[96];
	unsigned char	p_prim[32];
	unsigned char	q_prim[32];
	unsigned char	h_prim[4];
	unsigned char	a_prim[32];
	unsigned char	b_prim[32];
	unsigned char	g1[64];
	unsigned char	g2[192];
	unsigned char	g3[64];
	EcDsaSig		SignIntel;
} SafeIdParams;

/**
 *	SafeID Private Key Based Revocation List
 */
typedef struct _SafeIdPrivKeyRlHdr {
	unsigned char	sver[2];
	unsigned char	blobid[2];
	unsigned int	Gid;
	unsigned int	RlVer;
	unsigned int	n;
} SafeIdPrivKeyRlHdr;

#define SAFEID_F_KEY_SIZE		32
#define SAFEID_B_K_SIZE         128
#define SAFEID_SVER				0x0100
#define SAFEID_KEY_REV_LIST_BLOBID	0x0d00
#define SAFEID_SIG_REV_LIST_BLOBID	0x0e00
/*
 *	SafeId certificates, parameters and signatures lengths; application certificates length
 */
#define CERTIFICATE_3P_LEN		sizeof(Cert3p)
#define SAFEID_PARAM_LEN		sizeof(SafeIdParams)	// SafeID cryptosystem context length
#define SAFEID_CERT_LEN			sizeof(SafeIdCert)		// SafeID certificate length
#define SAFEID_SIG_LEN			569						// SafeID signature length
// SafeID Signature
typedef unsigned char SafeIdSig[SAFEID_SIG_LEN];

#pragma pack(pop)

#ifdef __cplusplus
}
#endif

#endif//_PROTECTED_OUTPUT_CERTIFICATES_H
