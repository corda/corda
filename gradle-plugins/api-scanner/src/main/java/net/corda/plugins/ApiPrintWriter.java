package net.corda.plugins;

import io.github.lukehutch.fastclasspathscanner.scanner.ClassInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.FieldInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.MethodInfo;
import io.github.lukehutch.fastclasspathscanner.typesignature.TypeSignature;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

public class ApiPrintWriter extends PrintWriter {
    ApiPrintWriter(File file, String encoding) throws FileNotFoundException, UnsupportedEncodingException {
        super(file, encoding);
    }

    public void println(ClassInfo classInfo, int modifierMask, List<String> filteredAnnotations) {
        append(asAnnotations(filteredAnnotations, ""));
        append(Modifier.toString(classInfo.getClassRef().getModifiers() & modifierMask));
        if (classInfo.isAnnotation()) {
            /*
             * Annotation declaration.
             */
            append(" @interface ").print(classInfo.getClassName());
        } else if (classInfo.isStandardClass()) {
            /*
             * Class declaration.
             */
            append(" class ").print(classInfo.getClassName());
            Set<ClassInfo> superclasses = classInfo.getDirectSuperclasses();
            if (!superclasses.isEmpty()) {
                append(" extends ").print(stringOf(superclasses));
            }
            Set<ClassInfo> interfaces = classInfo.getDirectlyImplementedInterfaces();
            if (!interfaces.isEmpty()) {
                append(" implements ").print(stringOf(interfaces));
            }
        } else {
            /*
             * Interface declaration.
             */
            append(" interface ").print(classInfo.getClassName());
            Set<ClassInfo> superinterfaces = classInfo.getDirectSuperinterfaces();
            if (!superinterfaces.isEmpty()) {
                append(" extends ").print(stringOf(superinterfaces));
            }
        }
        println();
    }

    public void println(MethodInfo method, String indentation) {
        append(asAnnotations(method.getAnnotationNames(), indentation));
        append(indentation);
        if (method.getModifiersStr() != null) {
            append(method.getModifiersStr()).append(' ');
        }
        if (!method.isConstructor()) {
            append(removeQualifierFromBaseTypes(method.getResultTypeStr())).append(' ');
        }
        append(method.getMethodName()).append('(');
        LinkedList<String> paramTypes = method
            .getTypeSignature()
            .getParameterTypeSignatures()
            .stream()
            .map(ApiPrintWriter::removeQualifierFromBaseTypes)
            .collect(toCollection(LinkedList::new));
        //if parameter is varargs, remove the array [] qualifier and replace with ellipsis
        if (method.isVarArgs() && !paramTypes.isEmpty()) {
            String vararg = paramTypes.removeLast();
            paramTypes.add(vararg.substring(0, vararg.length() - 2) + "...");
        }
        append(paramTypes.stream().collect(joining(", ")));
        println(')');
    }

    public void println(FieldInfo field, String indentation) {
        append(asAnnotations(field.getAnnotationNames(), indentation))
            .append(indentation)
            .append(field.getModifierStr())
            .append(' ')
            .append(removeQualifierFromBaseTypes(field.getTypeStr()))
            .append(' ')
            .append(field.getFieldName());
        if (field.getConstFinalValue() != null) {
            append(" = ");
            if (field.getConstFinalValue() instanceof String) {
                append('"').append(field.getConstFinalValue().toString()).append('"');
            } else if (field.getConstFinalValue() instanceof Character) {
                append('\'').append(field.getConstFinalValue().toString()).append('\'');
            } else {
                append(field.getConstFinalValue().toString());
            }
        }
        println();
    }

    private static String asAnnotations(Collection<String> items, String indentation) {
        if (items.isEmpty()) {
            return "";
        }
        return items.stream().map(ApiPrintWriter::removePackageName).collect(joining(System.lineSeparator() + indentation + '@', indentation + "@", System.lineSeparator()));
    }

    private static String removePackageName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String stringOf(Collection<ClassInfo> items) {
        return items.stream().map(ClassInfo::getClassName).collect(joining(", "));
    }

    private static String removeQualifierFromBaseTypes(String className) {
        return className.replace("java.lang.", "");
    }

    private static String removeQualifierFromBaseTypes(TypeSignature typeSignature) {
        return removeQualifierFromBaseTypes(typeSignature.toString());
    }
}
