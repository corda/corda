#ifndef CORDACPP_CORDA_STD_SERIALISERS_H
#define CORDACPP_CORDA_STD_SERIALISERS_H

#include "corda.h"

// Standard serialisers that don't follow the regular AMQP format for various reasons.

namespace java {
namespace security {

class PublicKey {
public:
    proton::binary x509_bits;

    explicit PublicKey(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "java.security.PublicKey", "net.corda:java.security.PublicKey", 0);
        decoder >> x509_bits;

    }
};

}  // security

namespace time {

class Instant {
public:
    int64_t epoch_seconds;
    int32_t nanos;

    Instant() : epoch_seconds(0), nanos(0) { }

    explicit Instant(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard d(decoder, "java.time.Instant", "net.corda:java.time.Instant", 2);
        decoder >> epoch_seconds;
        decoder >> nanos;
    }
};

}

}

#endif //CORDACPP_CORDA_STD_SERIALISERS_H
