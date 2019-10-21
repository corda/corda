package net.corda.testing;

import org.junit.Assert;
import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;

public class TestsTest {
    @Test
    public void read() {
        final Tests tests = new Tests();
        Assert.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n";
        tests.addTests(Tests.read(new StringReader(s)));

        Assert.assertFalse(tests.isEmpty());
        Assert.assertEquals((long) tests.getDuration("hello"), 100);
    }

    @Test
    public void write() {
        final StringWriter writer = new StringWriter();
        final Tests tests = new Tests();
        Assert.assertTrue(tests.isEmpty());
        tests.addDuration("hello", 100);
        tests.write(writer);
        Assert.assertFalse(tests.isEmpty());

        final StringReader reader = new StringReader(writer.toString());
        final Tests otherTests = new Tests();
        otherTests.addTests(Tests.read(reader));

        Assert.assertFalse(tests.isEmpty());
        Assert.assertFalse(otherTests.isEmpty());
        Assert.assertEquals(tests.size(), otherTests.size());
        Assert.assertEquals(tests.getDuration("hello"), otherTests.getDuration("hello"));
    }

    @Test
    public void addingTestChangesMeanDuration() {
        final Tests tests = new Tests();
        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n";
        tests.addTests(Tests.read(new StringReader(s)));

        Assert.assertFalse(tests.isEmpty());
        // 400 total for 4 tests
        Assert.assertEquals((long) tests.getDuration("hello"), 100);

        // 1000 total for 5 tests = 200 mean
        tests.addDuration("hello", 600);
        Assert.assertEquals((long) tests.getDuration("hello"), 200);
    }

    @Test
    public void addTests() {
        final Tests tests = new Tests();
        Assert.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n"
                + "goodbye,200,4\n";

        tests.addTests(Tests.read(new StringReader(s)));
        Assert.assertFalse(tests.isEmpty());
        Assert.assertEquals(tests.size(), 2);
    }

    @Test
    public void getDuration() {
        final Tests tests = new Tests();
        Assert.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n"
                + "goodbye,200,4\n";

        tests.addTests(Tests.read(new StringReader(s)));
        Assert.assertFalse(tests.isEmpty());
        Assert.assertEquals(tests.size(), 2);

        Assert.assertEquals(100L, tests.getDuration("hello"));
        Assert.assertEquals(200L, tests.getDuration("goodbye"));
    }

    @Test
    public void addTestInfo() {
        final Tests tests = new Tests();
        Assert.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n"
                + "goodbye,200,4\n";

        tests.addTests(Tests.read(new StringReader(s)));
        Assert.assertFalse(tests.isEmpty());
        Assert.assertEquals(2, tests.size());

        tests.addDuration("foo", 55);
        tests.addDuration("bar", 33);
        Assert.assertEquals(4, tests.size());

        tests.addDuration("bar", 56);
        Assert.assertEquals(4, tests.size());
    }
}