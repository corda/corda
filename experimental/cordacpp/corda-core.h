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

}  // namespace corda
}  // namespace net

#endif //CORDACPP_CORDA_STD_SERIALISERS_H
