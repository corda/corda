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

net::corda::TypeRegistration ByteSequenceRegistration("net.corda:0UvJuq940P0jrySmql4EPg==", [](proton::codec::decoder &decoder) { return new net::corda::core::utilities::ByteSequence(decoder); }); // NOLINT(cert-err58-cpp)
net::corda::TypeRegistration OpaqueBytesRegistration("net.corda:pgT0Kc3t/bvnzmgu/nb4Cg==", [](proton::codec::decoder &decoder) { return new net::corda::core::utilities::OpaqueBytes(decoder); }); // NOLINT(cert-err58-cpp)

}  // namespace corda
}  // namespace net



#endif //CORDACPP_CORDA_STD_SERIALISERS_H
