Network Permissioning - Distinguished Names
===========================================

In order to be compatible with other implementations (particularly TLS implementations), we constrain the allowed X.500
name attribute types to a subset of the minimum supported set for X.509 certificates (specified in RFC 3280), plus the
locality attribute:

* Organization (O)
* State (ST)
* Locality (L)
* Country (C)
* Organizational-unit (OU)
* Common name (CN) (only used for service identities)

Note that serial number is intentionally excluded in order to minimise scope for uncertainty in distinguished name format.
Distinguished name qualifier has been removed due to technical issues; consideration was given to "Corda" as qualifier,
however the qualifier needs to reflect directory manager, not the technology involved. There may be many Corda directories,
but only one R3 directory on Corda. The ordering of attributes is important.

State should be avoided unless required to differentiate from other localities with the same or similar names at the
country level. For example, London (GB) would not need a state, but St Ives would (there are two, one in Cornwall, one
in Cambridgeshire). As legal entities in Corda are likely to be located in major cities, this is not expected to be
present in the majority of names, but is an option for the cases which require it.

The name must also obey the following constraints:

* The organisation, locality and country attributes are present

    * The state, organisational-unit and common name attributes are optional

* The fields of the name have the following maximum character lengths:

    * Common name: 64
    * Organisation: 128
    * Organisation unit: 64
    * Locality: 64
    * State: 64

* The country attribute is a valid ISO 3166-1 two letter code in upper-case

* All attributes must obey the following constraints:

    * Upper-case first letter
    * Has at least two letters
    * No leading or trailing whitespace
    * Does not include the following characters: ``,`` , ``=`` , ``$`` , ``"`` , ``'`` , ``\``
    * Is in NFKC normalization form
    * Does not contain the null character
    * Only the latin, common and inherited unicode scripts are supported

* The organisation field of the name also obeys the following constraints:

    * No double-spacing
    * Does not contain the words "node" or "server"

        * This is to avoid right-to-left issues, debugging issues when we can't pronounce names over the phone, and
          character confusability attacks

## External Identifiers

For mappings to external identifiers such as Companies House nos., LEI, BIC, etc. these should be stored in custom X.509
certificate extensions. These values may change due to operational reasons, without the identity they're associated with
necessarily changing, and their inclusion in the distinguished name would cause significant logistical complications.
OID and format for these extensions will be described in a further specification.
