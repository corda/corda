#ifndef __SGX_TYPES_H__
#define __SGX_TYPES_H__

#pragma pack(push, 1)

#define ROUND_TO(x, align) \
    (((x) + ((align)-1)) & ~((align)-1))

#define MRE_SIZE        32                          // Size of MRENCLAVE (in bytes)
#define SE_KEY_SIZE     384                         // Size of keys (in bytes)
#define SE_EXP_SIZE     4                           // RSA public key exponent size in bytes

typedef struct {
    uint64_t            flags;
    uint64_t            xfrm;
} sgx_attributes_t;

typedef uint32_t        sgx_misc_select_t;

typedef struct {
    sgx_attributes_t    secs_attr;
    sgx_misc_select_t   misc_select;
} sgx_misc_attribute_t;

typedef struct {                                    // 128 bytes
    uint8_t             header[12];                 // (0) must be (06000000E100000000000100H)
    uint32_t            type;                       // (12) bit 31: 0 = prod, 1 = debug; Bit 30-0: Must be zero
    uint32_t            module_vendor;              // (16) Intel=0x8086, ISV=0x0000
    uint32_t            date;                       // (20) build date as yyyymmdd
    uint8_t             header2[16];                // (24) must be (01010000600000006000000001000000H)
    uint32_t            hw_version;                 // (40) For Launch Enclaves: HWVERSION != 0. Others, HWVERSION = 0
    uint8_t             reserved[84];               // (44) Must be 0
} css_header_t;

typedef struct {                                    // 772 bytes
    uint8_t             modulus[SE_KEY_SIZE];       // (128) Module Public Key (keylength=3072 bits)
    uint8_t             exponent[SE_EXP_SIZE];      // (512) RSA Exponent = 3
    uint8_t             signature[SE_KEY_SIZE];     // (516) Signature over Header and Body
} css_key_t;

typedef struct {                                    // 128 bytes
    sgx_misc_select_t   misc_select;                // (900) The MISCSELECT that must be set
    sgx_misc_select_t   misc_mask;                  // (904) Mask of MISCSELECT to enforce
    uint8_t             reserved[20];               // (908) Reserved. Must be 0.
    sgx_attributes_t    attributes;                 // (928) Enclave Attributes that must be set
    sgx_attributes_t    attribute_mask;             // (944) Mask of Attributes to Enforce
    uint8_t             enclave_hash[MRE_SIZE];     // (960) MRENCLAVE - (32 bytes)
    uint8_t             reserved2[32];              // (992) Must be 0
    uint16_t            isv_prod_id;                // (1024) ISV assigned Product ID
    uint16_t            isv_svn;                    // (1026) ISV assigned SVN
} css_body_t;

typedef struct {                                    // 780 bytes
    uint8_t             reserved[12];               // (1028) Must be 0
    uint8_t             q1[SE_KEY_SIZE];            // (1040) Q1 value for RSA Signature Verification
    uint8_t             q2[SE_KEY_SIZE];            // (1424) Q2 value for RSA Signature Verification
} css_buffer_t;

typedef struct {                                    // 1808 bytes
    css_header_t        header;                     // (0)
    css_key_t           key;                        // (128)
    css_body_t          body;                       // (900)
    css_buffer_t        buffer;                     // (1028)
} enclave_css_t;

typedef struct {
    uint64_t            magic_num;                  // The magic number identifying the file as a signed enclave image
    uint64_t            version;                    // The metadata version
    uint32_t            size;                       // The size of this structure
    uint32_t            tcs_policy;                 // TCS management policy
    uint32_t            ssa_frame_size;             // The size of SSA frame in page
    uint32_t            max_save_buffer_size;       // Max buffer size is 2632
    uint32_t            desired_misc_select;
    uint32_t            tcs_min_pool;               // TCS min pool*/
    uint64_t            enclave_size;               // enclave virtual size
    sgx_attributes_t    attributes;                 // XFeatureMask to be set in SECS.
    enclave_css_t       enclave_css;                // The enclave signature
} metadata_t;


#endif /* __SGX_TYPES_H__ */
