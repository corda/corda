package net.corda.nodeapi.internal.serialization.amqp;

import net.corda.core.serialization.SerializedBytes;
import net.corda.nodeapi.internal.serialization.AllWhitelist;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.NotSerializableException;

public class SetterConstructorTests {

    static class C {
        private int a;
        private int b;
        private int c;

        public int getA() { return a; }
        public int getB() { return b; }
        public int getC() { return c; }

        public void setA(int a) { this.a = a; }
        public void setB(int b) { this.b = b; }
        public void setC(int c) { this.c = c; }
    }

    static class C2 {
        private int a;
        private int b;
        private int c;

        public int getA() { return a; }
        public int getB() { return b; }
        public int getC() { return c; }

        public void setA(int a) { this.a = a; }
        public void setB(int b) { this.b = b; }
    }

    static class C3 {
        private int a;
        private int b;
        private int c;

        public int getA() { return a; }
        public int getC() { return c; }

        public void setA(int a) { this.a = a; }
        public void setB(int b) { this.b = b; }
        public void setC(int c) { this.c = c; }
    }

    static class C4 {
        private int a;
        private int b;
        private int c;

        public int getA() { return a; }
        protected int getB() { return b; }
        public int getC() { return c; }

        private void setA(int a) { this.a = a; }
        public void setB(int b) { this.b = b; }
        public void setC(int c) { this.c = c; }
    }

    static class Inner1 {
        private String a;

        public Inner1(String a) { this.a = a; }
        public String getA() { return this.a; }
    }

    static class Inner2 {
        private Double a;

        public Double getA() { return this.a; }
        public void setA(Double a) { this.a = a; }
    }

    static class Outer {
        private Inner1 a;
        private String b;
        private Inner2 c;

        public Inner1 getA() { return a; }
        public String getB() { return b; }
        public Inner2 getC() { return c; }

        public void setA(Inner1 a) { this.a = a; }
        public void setB(String b) { this.b = b; }
        public void setC(Inner2 c) { this.c = c; }
    }

    static class TypeMismatch {
        private Integer a;

        public void setA(Integer a) { this.a = a; }
        public String getA() { return this.a.toString(); }
    }

    static class TypeMismatch2 {
        private Integer a;

        public void setA(String a) { this.a = Integer.parseInt(a); }
        public Integer getA() { return this.a; }
    }

    // despite having no constructor we should still be able to serialise an instance of C
    @Test
    @Ignore
    public void serialiseC() throws NotSerializableException {
        EvolutionSerializerGetterBase evolutionSerialiserGetter = new EvolutionSerializerGetter();
        SerializerFactory factory1 = new SerializerFactory(
                AllWhitelist.INSTANCE,
                ClassLoader.getSystemClassLoader(),
                evolutionSerialiserGetter);

        SerializationOutput ser = new SerializationOutput(factory1);

        C c1 = new C();
        c1.setA(1);
        c1.setB(2);
        c1.setC(3);
        Schema schemas = ser.serializeAndReturnSchema(c1).component2();
        assertEquals(1, schemas.component1().size());
        assertEquals(this.getClass().getName() + "$C", schemas.component1().get(0).getName());

        CompositeType ct = (CompositeType) schemas.component1().get(0);

        assertEquals(3, ct.getFields().size());
        assertEquals("a", ct.getFields().get(0).getName());
        assertEquals("b", ct.getFields().get(1).getName());
        assertEquals("c", ct.getFields().get(2).getName());

        // No setter for c so should only serialise two properties
        C2 c2 = new C2();
        c2.setA(1);
        c2.setB(2);
        schemas = ser.serializeAndReturnSchema(c2).component2();

        assertEquals(1, schemas.component1().size());
        assertEquals(this.getClass().getName() + "$C2", schemas.component1().get(0).getName());

        ct = (CompositeType) schemas.component1().get(0);

        // With no setter for c we should only serialise a and b into the stream
        assertEquals(2, ct.getFields().size());
        assertEquals("a", ct.getFields().get(0).getName());
        assertEquals("b", ct.getFields().get(1).getName());

        // no getter for b so shouldn't serialise it,thus only a and c should apper in the envelope
        C3 c3 = new C3();
        c3.setA(1);
        c3.setB(2);
        c3.setC(3);
        schemas = ser.serializeAndReturnSchema(c3).component2();

        assertEquals(1, schemas.component1().size());
        assertEquals(this.getClass().getName() + "$C3", schemas.component1().get(0).getName());

        ct = (CompositeType) schemas.component1().get(0);

        // With no setter for c we should only serialise a and b into the stream
        assertEquals(2, ct.getFields().size());
        assertEquals("a", ct.getFields().get(0).getName());
        assertEquals("c", ct.getFields().get(1).getName());

        C4 c4 = new C4();
        c4.setA(1);
        c4.setB(2);
        c4.setC(3);
        schemas = ser.serializeAndReturnSchema(c4).component2();

        assertEquals(1, schemas.component1().size());
        assertEquals(this.getClass().getName() + "$C4", schemas.component1().get(0).getName());

        ct = (CompositeType) schemas.component1().get(0);

        // With non public visibility on a setter and getter for a and b, only c should be serialised
        assertEquals(1, ct.getFields().size());
        assertEquals("c", ct.getFields().get(0).getName());
    }

    @Test
    @Ignore
    public void deserialiseC() throws NotSerializableException {
        EvolutionSerializerGetterBase evolutionSerialiserGetter = new EvolutionSerializerGetter();
        SerializerFactory factory1 = new SerializerFactory(
                AllWhitelist.INSTANCE,
                ClassLoader.getSystemClassLoader(),
                evolutionSerialiserGetter);

        C cPre1 = new C();

        int a = 1;
        int b = 2;
        int c = 3;

        cPre1.setA(a);
        cPre1.setB(b);
        cPre1.setC(c);

        SerializedBytes bytes = new SerializationOutput(factory1).serialize(cPre1);

        C cPost1 = new DeserializationInput(factory1).deserialize(bytes, C.class);

        assertEquals(a, cPost1.a);
        assertEquals(b, cPost1.b);
        assertEquals(c, cPost1.c);

        C2 cPre2 = new C2();
        cPre2.setA(1);
        cPre2.setB(2);

        C2 cPost2 = new DeserializationInput(factory1).deserialize(new SerializationOutput(factory1).serialize(cPre2),
                C2.class);

        assertEquals(a, cPost2.a);
        assertEquals(b, cPost2.b);

        // no setter for c means nothing will be serialised and thus it will have the default value of zero
        // set
        assertEquals(0, cPost2.c);

        C3 cPre3 = new C3();
        cPre3.setA(1);
        cPre3.setB(2);
        cPre3.setC(3);

        C3 cPost3 = new DeserializationInput(factory1).deserialize(new SerializationOutput(factory1).serialize(cPre3),
                C3.class);

        assertEquals(a, cPost3.a);

        // no getter for b means, as before, it'll have been not set and will thus be defaulted to 0
        assertEquals(0, cPost3.b);
        assertEquals(c, cPost3.c);

        C4 cPre4 = new C4();
        cPre4.setA(1);
        cPre4.setB(2);
        cPre4.setC(3);

        C4 cPost4 = new DeserializationInput(factory1).deserialize(new SerializationOutput(factory1).serialize(cPre4),
                C4.class);

        assertEquals(0, cPost4.a);
        assertEquals(0, cPost4.b);
        assertEquals(c, cPost4.c);
    }

    @Test
    public void serialiseOuterAndInner() throws NotSerializableException {
        EvolutionSerializerGetterBase evolutionSerialiserGetter = new EvolutionSerializerGetter();
        SerializerFactory factory1 = new SerializerFactory(
                AllWhitelist.INSTANCE,
                ClassLoader.getSystemClassLoader(),
                evolutionSerialiserGetter);

        Inner1 i1 = new Inner1("Hello");
        Inner2 i2 = new Inner2();
        i2.setA(10.5);

        Outer o = new Outer();
        o.setA(i1);
        o.setB("World");
        o.setC(i2);

        Outer post = new DeserializationInput(factory1).deserialize(new SerializationOutput(factory1).serialize(o),
                Outer.class);

        assertEquals("Hello", post.a.a);
        assertEquals("World", post.b);
        assertEquals((Double)10.5, post.c.a);

    }

    @Test
    @Ignore
    public void typeMistmatch() throws NotSerializableException {
        EvolutionSerializerGetterBase evolutionSerialiserGetter = new EvolutionSerializerGetter();
        SerializerFactory factory1 = new SerializerFactory(
                AllWhitelist.INSTANCE,
                ClassLoader.getSystemClassLoader(),
                evolutionSerialiserGetter);

        TypeMismatch tm = new TypeMismatch();
        tm.setA(10);
        assertEquals("10", tm.getA());

        TypeMismatch post = new DeserializationInput(factory1).deserialize(new SerializationOutput(factory1).serialize(tm),
                TypeMismatch.class);

        // because there is a type mismatch in the class, it won't return that info as a BEAN property and thus
        // we won't serialise it and thus on deserialization it won't be initialized
        Assertions.assertThatThrownBy(() -> post.getA()).isInstanceOf(NullPointerException.class);
    }

    @Test
    @Ignore
    public void typeMistmatch2() throws NotSerializableException {
        EvolutionSerializerGetterBase evolutionSerialiserGetter = new EvolutionSerializerGetter();
        SerializerFactory factory1 = new SerializerFactory(
                AllWhitelist.INSTANCE,
                ClassLoader.getSystemClassLoader(),
                evolutionSerialiserGetter);

        TypeMismatch2 tm = new TypeMismatch2();
        tm.setA("10");
        assertEquals((Integer)10, tm.getA());

        TypeMismatch2 post = new DeserializationInput(factory1).deserialize(new SerializationOutput(factory1).serialize(tm),
                TypeMismatch2.class);

        // because there is a type mismatch in the class, it won't return that info as a BEAN property and thus
        // we won't serialise it and thus on deserialization it won't be initialized
        assertEquals(null, post.getA());
    }
}
