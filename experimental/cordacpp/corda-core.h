#ifndef CORDACPP_CORDA_STD_SERIALISERS_H
#define CORDACPP_CORDA_STD_SERIALISERS_H

#include "corda.h"

namespace net {
namespace corda {

/**
 * An enum, for which each property corresponds to a transaction component group. The position in the enum class
 * declaration (ordinal) is used for component-leaf ordering when computing the Merkle tree.
 */
enum ComponentGroupEnum {
    INPUTS, // ordinal = 0.
    OUTPUTS, // ordinal = 1.
    COMMANDS, // ordinal = 2.
    ATTACHMENTS, // ordinal = 3.
    NOTARY, // ordinal = 4.
    TIMEWINDOW, // ordinal = 5.
    SIGNERS, // ordinal = 6.
    REFERENCES // ordinal = 7.
};

namespace core {

namespace utilities {

// ByteSequence is a weird class and it currently defeats proper handling of inherited types.
// TODO: Dig in to the apparent issue with abstract classes in LocalTypeInformationBuilder.kt

class ByteSequence : public net::corda::Any {
public:
    ByteSequence() = default;

    explicit ByteSequence(proton::codec::decoder &decoder) {
    }
};

class OpaqueBytes : public net::corda::core::utilities::ByteSequence {
public:
    OpaqueBytes() = default;

    explicit OpaqueBytes(proton::codec::decoder &decoder) : net::corda::core::utilities::ByteSequence(decoder) {}
};

}  // namespace utilities

}

}  // namespace corda
}  // namespace net

// Standard serialisers that don't follow the regular AMQP format for various reasons.

namespace java {

namespace lang {

template <class T>
class Class : public net::corda::Any {
public:
    std::string name;

    explicit Class(proton::codec::decoder &decoder) {
        decoder >> name;
    }
};

}

namespace security {

class PublicKey : public net::corda::Any {
public:
    proton::binary x509_bits;

    explicit PublicKey(proton::codec::decoder &decoder) {
        decoder >> x509_bits;
    }
};


}  // security

namespace time {

class Instant : public net::corda::Any {
public:
    int64_t epoch_seconds;
    int32_t nanos;

    Instant() : epoch_seconds(0), nanos(0) {}

    explicit Instant(proton::codec::decoder &decoder) : epoch_seconds(0), nanos(0) {
        decoder >> epoch_seconds;
        decoder >> nanos;
    }
};

class LocalDate : public net::corda::Any {
public:
    uint32_t year;
    uint8_t month;
    uint8_t day;

    LocalDate() = default;

    explicit LocalDate(proton::codec::decoder &decoder) {
        decoder >> year;
        decoder >> month;
        decoder >> day;
    }
};

}
}


#endif //CORDACPP_CORDA_STD_SERIALISERS_H
