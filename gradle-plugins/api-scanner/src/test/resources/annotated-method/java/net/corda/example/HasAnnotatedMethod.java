package net.corda.example;

public class HasAnnotatedMethod {
    @B @C @A
    public void hasAnnotation() {
        System.out.println("VISIBLE ANNOTATIONS");
    }
}