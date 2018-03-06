package net.corda.example.method;

public class HasVisibleMethod {
    @InvisibleAnnotation
    @LocalInvisibleAnnotation
    public void hasInvisibleAnnotations() {
        System.out.println("VISIBLE METHOD, INVISIBLE ANNOTATIONS");
    }
}
