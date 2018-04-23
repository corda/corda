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


#include "persistent_storage_info.h"
#include "util.h"

//The ordering of the array must be same as the enumerartion aesm_data_id_t
static const persistent_storage_info_t psinfos[]={
#ifdef REF_LE
    { FT_ENCLAVE_NAME, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "ref_le" },//LE_ENCLAVE_FID
#else
    { FT_ENCLAVE_NAME, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "le" },//LE_ENCLAVE_FID
#endif // REF_LE
    {FT_ENCLAVE_NAME, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "qe"},//QE_ENCLAVE_FID
    {FT_ENCLAVE_NAME, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "pve"},//PVE_ENCLAVE_FID
    {FT_ENCLAVE_NAME, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "pse_op"},//PSE_OP_ENCLAVE_FID
    {FT_ENCLAVE_NAME, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "pse_pr"},//PSE_PR_ENCLAVE_FID
    {FT_ENCLAVE_NAME, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "pce"}, //PCE_ENCLAVE_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "le_prod_css.bin"},//LE_PROD_SIG_STRUCT_FID
    { FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "active_extended_epid_group_id" }, // EXTENDED_EPID_GROUP_ID_FID
    { FT_PERSISTENT_STORAGE, AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA, AESM_FILE_ACCESS_READ_ONLY, "extended_epid_group.blob" }, //EXTENDED_EPID_GROUP_BLOB_INFO_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "endpoint_selection_info.blob"},//PROVISION_PEK_BLOB_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "epid_data.blob"},//EPID_DATA_BLOB_FID
    { FT_PERSISTENT_STORAGE, AESM_LOCATION_MULTI_EXTENDED_EPID_GROUP_DATA, AESM_FILE_ACCESS_READ_ONLY, "aesm_server_url.blob" },//AESM_SERVER_URL_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_PATH_ONLY, "pse_vmc.db"},//VMC_DATABASE_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_PATH_ONLY, "backup_pse_vmc.db"},//VMC_DATABASE_BK_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_PATH_ONLY, "prebuild_pse_vmc.db"},//VMC_DATABASE_PREBUILD_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_EXE_FOLDER, AESM_FILE_ACCESS_PATH_ONLY, "PSDA.dalp"}, //PSDA_FID, path only information?
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "aesm_network_setting.blob"},//NETWORK_SETTING_FID
#ifdef DBG_LOG
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "internal_log.txt"}, //AESM_DBG_LOG_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "internal_log_cfg.xml"}, //AESM_DBG_LOG_CFG_FID
#endif
#ifdef _PROFILE_
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "perf_time.csv"}, //AESM_PERF_DATA_FID
#endif
#ifdef REF_LE
    { FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "ref_white_list.bin" },//AESM_WHITE_LIST_CERT_FID
#else
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "white_list_cert.bin"},//AESM_WHITE_LIST_CERT_FID
#endif // REF_LE
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "white_list_cert_to_be_verify.bin"},//AESM_WHITE_LIST_CERT_TO_BE_VERIFY_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "OcspResponseVLR.dat"}, //PSE_PR_OCSPRESP_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "LTPairing.blob"}, //PSE_PR_LT_PAIRING_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "CertificateChain.list"},//PSE_PR_CERTIFICATE_CHAIN_FID
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "Certificate.cer"},//PSE_PR_CERTIFICATE_FID, user may add some postfix after name retrieved
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "Certificate2.cer"},
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "Certificate3.cer"},
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "Certificate4.cer"},
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "Certificate5.cer"},
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "Certificate6.cer"},
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_ALL, "CertificateMax.cer"},
    {FT_PERSISTENT_STORAGE, AESM_LOCATION_DATA, AESM_FILE_ACCESS_PATH_ONLY, ""},//PSE_PR_SIGRL_FID, user may add some postfix after name retrieved
};

se_static_assert(sizeof(psinfos)/sizeof(persistent_storage_info_t) == NUMBER_OF_FIDS);

const persistent_storage_info_t* get_persistent_storage_info(aesm_data_id_t id)
{
    if(id<0||id>=NUMBER_OF_FIDS)
        return NULL;
    return &psinfos[id];
}

aesm_data_id_t operator++(aesm_data_id_t& id, int)
{
	aesm_data_id_t retid = id;
	switch (id)
	{
	case PSE_PR_CERTIFICATE_FID:
		id = PSE_PR_CERTIFICATE_FID2;
		break;
	case PSE_PR_CERTIFICATE_FID2:
		id = PSE_PR_CERTIFICATE_FID3;
		break;
	case PSE_PR_CERTIFICATE_FID3:
		id = PSE_PR_CERTIFICATE_FID4;
		break;
	case PSE_PR_CERTIFICATE_FID4:
		id = PSE_PR_CERTIFICATE_FID5;
		break;
	case PSE_PR_CERTIFICATE_FID5:
		id = PSE_PR_CERTIFICATE_FID6;
		break;
	case PSE_PR_CERTIFICATE_FID6:
		id = PSE_PR_CERTIFICATE_FID_MAX;
		break;
	default:
		id = NUMBER_OF_FIDS;
		break;
	}

	return retid;
}

