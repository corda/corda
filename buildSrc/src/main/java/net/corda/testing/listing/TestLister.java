package net.corda.testing.listing;

import java.util.stream.Stream;

public interface TestLister {
    Stream<String> getAllTestsDiscovered();
}