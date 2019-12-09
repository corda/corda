#pragma once

/******************************************************************************/

#include <map>
#include <memory>

/******************************************************************************/

#include "AMQPDescriptor.h"

/******************************************************************************/


/******************************************************************************/

/**
 *
 */
namespace amqp::internal {

    extern std::map<uint64_t, std::shared_ptr<internal::schema::descriptors::AMQPDescriptor>> AMQPDescriptorRegistory;

}

/******************************************************************************
 *
 * Some basic utlility functions
 *
 ******************************************************************************/

namespace amqp {

    /**
     * the top 32 bits of a Corda AMQP descriptor is the assigned CORDA identifier.
     *
     * Utility function to strip that off and return a simple integer that maps
     * to our described types.
     */
    uint32_t stripCorda (uint64_t id);

    std::string describedToString (uint64_t);
    std::string describedToString (uint32_t);
}

/******************************************************************************/

