include_directories (descriptors)
include_directories (schema)
include_directories (reader)
include_directories (.)

set (amqp_sources
        CompositeFactory.cxx
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
        reader/RestrictedReader.cxx
        reader/CompositeReader.cxx
        reader/PropertyReader.cxx
        reader/property-readers/StringPropertyReader.cxx
        reader/property-readers/IntPropertyReader.cxx
        reader/property-readers/DoublePropertyReader.cxx
        reader/property-readers/LongPropertyReader.cxx
        reader/property-readers/BoolPropertyReader.cxx
        reader/restricted-readers/ListReader.cxx
)

ADD_LIBRARY ( amqp ${amqp_sources} )

ADD_SUBDIRECTORY (test)
