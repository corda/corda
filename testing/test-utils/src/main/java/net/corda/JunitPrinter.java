package net.corda;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import org.junit.Test;

public class JunitPrinter {

    public static void main(String[] args) {
        ClassGraph classGraph = new ClassGraph();
        classGraph.enableAnnotationInfo();
        classGraph.enableMethodInfo();
        classGraph.enableClassInfo();
        ClassInfoList classesWithAnnotatedMethods = classGraph.scan().getClassesWithMethodAnnotation(Test.class.getCanonicalName());

        classesWithAnnotatedMethods.forEach(classInfo -> {
            classInfo.getDeclaredMethodInfo().forEach(methodInfo -> {
                if (methodInfo.hasAnnotation(Test.class.getCanonicalName())) {
                    System.out.println(classInfo.getName() + "." + methodInfo.getName());
                }
            });
        });

    }

}
