#include "RestrictedDescriptor.h"

#include "types.h"
#include "debug.h"

#include "amqp/schema/restricted-types/Restricted.h"
#include "amqp/descriptors/AMQPDescriptors.h"

#include <sstream>

/******************************************************************************
 *
 * Restricted types represent lists and maps
 *
 * NOTE: The Corda serialization scheme doesn't support all container classes
 * as it has the requiremnt that iteration order be deterministic for purposes
 * of signing over data.
 *
 *      name : String
 *      label : String?
 *      provides : List<String>
 *      source : String
 *      descriptor : Descriptor
 *      choices : List<Choice>
 *
 ******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
RestrictedDescriptor::build (pn_data_t * data_) const {
    DBG ("RESTRICTED" << std::endl); // NOLINT
    validateAndNext(data_);

    proton::auto_enter ae (data_);

    auto name  = proton::readAndNext<std::string>(data_);
    auto label = proton::readAndNext<std::string>(data_, true);

    std::vector<std::string> provides;
    {
        proton::auto_list_enter ae2 (data_);
        while (pn_data_next(data_)) {
            provides.push_back (proton::get_string (data_));
        }
    }

    pn_data_next (data_);

    auto source = proton::readAndNext<std::string> (data_);
    auto descriptor = descriptors::dispatchDescribed<schema::Descriptor> (data_);

    // SKIP the choices section **FOR NOW**

    return schema::Restricted::make (descriptor, name,
                                     label, provides, source);
}

/******************************************************************************/

void
amqp::internal::
RestrictedDescriptor::read (
        pn_data_t * data_,
        std::stringstream & ss_,
        const AutoIndent & ai_
) const {
    proton::is_list (data_);
    proton::auto_enter ae (data_);
    AutoIndent ai { ai_ };

    ss_ << ai << "1] String: Name: "
        << proton::readAndNext<std::string> (data_)
        << std::endl;
    ss_ << ai << "2] String: Label: "
        << proton::readAndNext<std::string> (data_, true)
        << std::endl;
    ss_ << ai << "3] List: Provides: [ ";

    {
        proton::auto_list_enter ae2 (data_);
        while (pn_data_next(data_)) {
            ss_ << proton::get_string (data_) << " ";
        }
        ss_ << "]" << std::endl;
    }

    pn_data_next (data_);
    ss_ << ai << "4] String: Source: "
        << proton::readAndNext<std::string> (data_)
        << std::endl;

    ss_ << ai << "5] Descriptor:" << std::endl;

    AMQPDescriptorRegistory[pn_data_type(data_)]->read (
            (pn_data_t *)proton::auto_next(data_), ss_, AutoIndent { ai });
}

/******************************************************************************/
