#include "FieldDescriptor.h"

#include "debug.h"
#include "types.h"

#include "proton/proton_wrapper.h"

#include "amqp/schema/field-types/Field.h"

#include <sstream>

/******************************************************************************
 *
 * amqp::internal::schema::descriptors::FieldDescriptor
 *
 ******************************************************************************/

amqp::internal::schema::descriptors::
FieldDescriptor::FieldDescriptor (
    std::string symbol_,
    int val_
) : AMQPDescriptor (std::move (symbol_), val_) {

}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
FieldDescriptor::build (pn_data_t * data_) const {
    DBG ("FIELD" << std::endl); // NOLINT

    validateAndNext (data_);

    proton::auto_enter ae (data_);

    /* name: String */
    auto name = proton::get_string (data_);

    DBG ("FIELD::name: \"" << name << "\"" << std::endl); // NOLINT

    pn_data_next (data_);

    /* type: String */
    auto type = proton::get_string (data_);

    DBG ("FIELD::type: \"" << type << "\"" << std::endl); // NOLINT

    pn_data_next (data_);

    /* requires: List<String> */
    std::list<std::string> requires;
    {
        proton::auto_list_enter ale (data_);
        while (pn_data_next(data_)) {
            requires.push_back (proton::get_string(data_));
        }
    }

    pn_data_next (data_);

    /* default: String? */
    auto def = proton::get_string (data_, true);

    pn_data_next (data_);

    /* label: String? */
    auto label = proton::get_string (data_, true);

    pn_data_next (data_);

    /* mandatory: Boolean - copes with the Kotlin concept of nullability.
       If something is mandatory then it cannot be null */
    auto mandatory = proton::get_boolean (data_);

    pn_data_next (data_);

    /* multiple: Boolean */
    auto multiple = proton::get_boolean(data_);

    return schema::Field::make (
            name, type, requires, def, label, mandatory, multiple);
}

/******************************************************************************/

void
amqp::internal::schema::descriptors::
FieldDescriptor::read (
        pn_data_t * data_,
        std::stringstream & ss_,
        const AutoIndent & ai_
) const  {
    proton::is_list (data_);

    proton::auto_list_enter ale (data_, true);
    AutoIndent ai { ai_ };

    ss_ << ai << "1/7] String: Name: "
        << proton::get_string ((pn_data_t *)proton::auto_next (data_))
        << std::endl;
    ss_ << ai << "2/7] String: Type: "
        << proton::get_string ((pn_data_t *)proton::auto_next (data_))
        << std::endl;

    {
        proton::auto_list_enter ale2 (data_);

        ss_ << ai << "3/7] List: Requires: elements " << ale2.elements()
            << std::endl;

        AutoIndent ai2 { ai };

        while (pn_data_next(data_)) {
            ss_ << ai2 << proton::get_string (data_) << std::endl;
        }
    }

    pn_data_next (data_);

    proton::is_string (data_, true);

    ss_ << ai << "4/7] String: Default: "
        << proton::get_string ((pn_data_t *)proton::auto_next (data_), true)
        << std::endl;
    ss_ << ai << "5/7] String: Label: "
        << proton::get_string ((pn_data_t *)proton::auto_next (data_), true)
        << std::endl;
    ss_ << ai << "6/7] Boolean: Mandatory: "
        << proton::get_boolean ((pn_data_t *)proton::auto_next (data_))
        << std::endl;
    ss_ << ai << "7/7] Boolean: Multiple: "
        << proton::get_boolean ((pn_data_t *)proton::auto_next (data_))
        << std::endl;
}

/******************************************************************************/
