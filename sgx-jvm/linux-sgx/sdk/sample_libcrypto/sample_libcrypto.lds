/***
* See http://sourceware.org/binutils/docs/ld/VERSION.html#VERSION for more info.
* // Comments don't work in this file.
***/

{
global:
    sample_ecc256_close_context;
    sample_ecc256_compute_shared_dhkey;
    sample_ecc256_create_key_pair;
    sample_ecc256_open_context;
    sample_ecdsa_sign;
    sample_rijndael128GCM_encrypt;
    sample_rijndael128_cmac_msg;
    sample_sha256_close;
    sample_sha256_get_hash;
    sample_sha256_init;
    sample_sha256_update;

local:
    *;
};

