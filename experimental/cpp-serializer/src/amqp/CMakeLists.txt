include_directories (descriptors)
include_directories (schema)
include_directories (reader)
include_directories (.)

set (amqp_sources
    descriptors/AMQPDescriptors.cxx
    descriptors/AMQPDescriptorRegistory.cxx
    schema/Schema.cxx
    schema/Field.cxx
    schema/Envelope.cxx
    schema/Composite.cxx
    schema/Descriptor.cxx
    schema/restricted-types/Restricted.cxx
    schema/restricted-types/List.cxx
    schema/AMQPTypeNotation.cxx
        reader/Reader.cxx
        reader/PropertyReader.cxx
        reader/RestrictedReader.cxx
        reader/CompositeReader.cxx
    CompositeFactory.cxx
)

ADD_LIBRARY ( amqp ${amqp_sources} )

ADD_SUBDIRECTORY (test)
