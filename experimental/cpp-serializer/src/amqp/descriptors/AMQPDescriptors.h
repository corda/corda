#pragma once

/******************************************************************************/

#include <set>
#include <map>
#include <string>
#include <memory>
#include <iostream>

#include "amqp/AMQPDescribed.h"
#include "amqp/AMQPDescriptor.h"
#include "amqp/schema/Descriptor.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp::internal {

    class EnvelopeDescriptor : public AMQPDescriptor {
        public :
            EnvelopeDescriptor() : AMQPDescriptor() { }

            EnvelopeDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~EnvelopeDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class SchemaDescriptor : public AMQPDescriptor {
        public :
            SchemaDescriptor() : AMQPDescriptor() { }

            SchemaDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~SchemaDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class ObjectDescriptor : public AMQPDescriptor {
        public :
            ObjectDescriptor() : AMQPDescriptor() { }

            ObjectDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~ObjectDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class FieldDescriptor : public AMQPDescriptor {
        public :
            FieldDescriptor() : AMQPDescriptor() { }

            FieldDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~FieldDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class CompositeDescriptor : public AMQPDescriptor {
        public :
            CompositeDescriptor() : AMQPDescriptor() { }

            CompositeDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~CompositeDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class RestrictedDescriptor : public AMQPDescriptor {
        public :
            RestrictedDescriptor() : AMQPDescriptor() { }

            RestrictedDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~RestrictedDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class ChoiceDescriptor : public AMQPDescriptor {
        public :
            ChoiceDescriptor() : AMQPDescriptor() { }

            ChoiceDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~ChoiceDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class ReferencedObjectDescriptor : public AMQPDescriptor {
        public :
            ReferencedObjectDescriptor() : AMQPDescriptor() { }

            ReferencedObjectDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~ReferencedObjectDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class TransformSchemaDescriptor : public AMQPDescriptor {
        public :
            TransformSchemaDescriptor() : AMQPDescriptor() { }

            TransformSchemaDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~TransformSchemaDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class TransformElementDescriptor : public AMQPDescriptor {
        public :
            TransformElementDescriptor() : AMQPDescriptor() { }

            TransformElementDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~TransformElementDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/

namespace amqp::internal {

    class TransformElementKeyDescriptor : public AMQPDescriptor {
        public :
            TransformElementKeyDescriptor() : AMQPDescriptor() { }

            TransformElementKeyDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
            { }

            ~TransformElementKeyDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/
