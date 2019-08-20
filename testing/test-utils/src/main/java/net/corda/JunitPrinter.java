package net.corda;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Random;

public class JunitPrinter {

    static class DummyItem {
        @Test
        public void testMethod() {

        }
    }

    public static void main(String[] args) throws IOException {

        String pathToWriteFileTo = args[0];
        String gitSha = args[1];

        BigInteger parsedSha = new BigInteger(gitSha, 36);
        Random shuffler = new Random(parsedSha.longValue());

        ClassGraph classGraph = new ClassGraph();
        classGraph.enableAnnotationInfo();
        classGraph.enableMethodInfo();
        classGraph.enableClassInfo();
        ClassInfoList classesWithAnnotatedMethods = classGraph.scan().getClassesWithMethodAnnotation(Test.class.getCanonicalName());
        PrintWriter outputWriter = new PrintWriter(new FileWriter(new File(new File(pathToWriteFileTo), "tests-print.out")));
        try (PrintWriter bw = outputWriter) {
            Collections.shuffle(classesWithAnnotatedMethods, shuffler);
            classesWithAnnotatedMethods.forEach(classInfo -> {
                bw.println(classInfo.getName() + ".*");
            });
        }
    }

}
