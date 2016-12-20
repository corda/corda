# Sample Issuer Material

This folder contains sample issuer material for use with the Intel(R)
EPID SDK. All data files are in binary format.

## Directory Structure

    data
    |__ groupa
    |   |__ member0
    |   |   |__ mprivkey.dat
    |   |
    |   |__ member1
    |   |   |__ mprivkey.dat
    |   |
    |   |__ privrevokedmember0
    |   |   |__ mprivkey.dat
    |   |
    |   |__ privrevokedmember1
    |   |   |__ mprivkey.dat
    |   |
    |   |__ privrevokedmember2
    |   |   |__ mprivkey.dat
    |   |
    |   |__ sigrevokedmember0
    |   |   |__ mprivkey.dat
    |   |
    |   |__ sigrevokedmember1
    |   |   |__ mprivkey.dat
    |   |
    |   |__ sigrevokedmember2
    |   |   |__ mprivkey.dat
    |   |
    |   |__ privrl.bin
    |   |__ pubkey.bin
    |   |__ sigrl.bin
    |
    |__ groupb
    |   |__ member0
    |   |   |__ mprivkey.dat
    |   |
    |   |__ member1
    |   |   |__ mprivkey.dat
    |   |
    |   |__ privrevokedmember0
    |   |   |__ mprivkey.dat
    |   |
    |   |__ sigrevokedmember0
    |   |   |__ mprivkey.dat
    |   |
    |   |__ privrl.bin
    |   |__ pubkey.bin
    |   |__ sigrl.bin
    |
    |__ grprl.bin
    |__ grprl_empty.bin
    |__ mprivkey.dat
    |__ pubkey.bin
    |__ cacert.bin


## Description

There are 2 groups

- **groupa**

- **groupb**


_Note: No compressed key sample material is included in the package._

### Group A

**groupa** contains 8 members. Each member has a member private key
`mprivkey.dat`. Here are the members:

- **member0** - a member in good standing

- **member1** - a member in good standing

- **privrevokedmember0** - a member revoked using its private key

- **privrevokedmember1** - a member revoked using its private key

- **privrevokedmember2** - a member revoked using its private key

- **sigrevokedmember0** - a member revoked using a signature

- **sigrevokedmember1** - a member revoked using a signature

- **sigrevokedmember2** - a member revoked using a signature


In addition, **groupa** contain the following revocation lists:

- `pubkey.bin` - group public key

- `privrl.bin` - private key based revocation list with 3 entries -
  **privrevokedmember0**, **privrevokedmember1** and
  **privrevokedmember2**

- `sigrl.bin` - signature based revocation list with 3 entries -
  **sigrevokedmember0**, **sigrevokedmember2** and
  **sigrevokedmember2**


### Group B

**groupb** contains 3 members. Each member has a member private key
`mprivkey.dat`. Here are the members:

- **member0** - a member in good standing

- **privrevokedmember0** - a member whose private key is revoked

- **sigrevokedmember0** - a member whose signature is revoked


In addition, **groupb** contain the following revocation lists:

- `pubkey.bin` - group public key

- `privrl.bin` - private key based revocation list with 1 entry -
  **privrevokedmember0**

- `sigrl.bin` - signature based revocation list with 1 entries -
  **sigrevokedmember0**


### Default files

- `/data/cacert.bin` - CA certificate used as default input to signmsg
  and verifysig

- `/data/grprl.bin` - group revocation list with one entry **groupb** used
  as default input to verifysig

- `/data/pubkey.bin` - public key of a group A used as default input
  to signmsg and verifysig

- `/data/mprivkey.dat` - private key of a member0 in the group A above
  used as default input to signmsg


### Group revocation lists

There are 2 group revocation lists:

- `grprl.bin` - group revocation list with 1 entry - **groupb**

- `grprl_empty.bin` - group revocation list with 0 entry


### IoT EPID Issuing CA certificate

- `/data/cacert.bin` - CA certificate used to check that revocation
  lists and group public keys are authorized by the issuer, e.g.,
  signed by the issuer


