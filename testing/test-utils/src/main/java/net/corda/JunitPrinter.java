package net.corda;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class JunitPrinter {

    public static void main(String[] args) throws IOException {

        String pathToWriteFileTo = args[0];

        ClassGraph classGraph = new ClassGraph();
        classGraph.enableAnnotationInfo();
        classGraph.enableMethodInfo();
        classGraph.enableClassInfo();
        ClassInfoList classesWithAnnotatedMethods = classGraph.scan().getClassesWithMethodAnnotation(Test.class.getCanonicalName());
        try (PrintWriter bw = (new PrintWriter(new FileWriter(new File(new File(pathToWriteFileTo), "tests-print.out"))))) {

            classesWithAnnotatedMethods.forEach(classInfo -> {
                bw.println(classInfo.getName() + ".*");
            });

//            classesWithAnnotatedMethods.forEach(classInfo -> {
//                classInfo.getDeclaredMethodInfo().forEach(methodInfo -> {
//                    if (methodInfo.hasAnnotation(Test.class.getCanonicalName())) {
//                        bw.println(classInfo.getName() + "." + methodInfo.getName());
//                    }
//                });
//            });
        }
    }

}
