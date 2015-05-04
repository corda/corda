#!/bin/bash

unzip -l /Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home/jre/lib/rt.jar | \
  grep java/lang/invoke | \
  grep -E '\$' | \
  awk '{print $4}' | \
  sed -E -e 's/\.class//g' | \
  while read path; do
    echo $path
    name=$(echo $path | sed -e 's|/|.|g')

    if javap -public $name | grep -q "public.*interface"; then
      javap -public $name | grep -v "Compiled from" | \
        sed -E \
          -e 's/java.lang.invoke.//g' \
          -e 's/java.lang.Object/Object o/g' \
          -e 's/java.lang.reflect.Method/Method o/g' \
          -e 's/java.lang.String/String s/g' \
          -e 's/java.lang.Throwable/Throwable/g' \
          -e 's/int/int i/g' \
          -e 's/byte/byte b/g' \
          -e 's/boolean/boolean b/g' \
          -e 's/MethodType/MethodType mt/g' \
          -e 's/MethodHandle/MethodHandle mh/g' \
          -e 's/java.lang.Class\<\?\>/Class\<\?\> c/g' \
          -e 's/;/ { throw new RuntimeException(); }/g' \
          -e 's/public String s/public String/g' \
          -e 's/public static String s/public static String/g' \
          -e 's/public abstract MethodHandle mh/public abstract MethodHandle/g' \
          -e 's/public abstract MethodHandle mh/public abstract MethodHandle/g' \
          -e 's/public boolean b/public boolean/g' \
          -e 's/public int i/public int/g' \
          -e 's/public static final int i/public static final int/g' \
          -e 's/public byte b/public byte/g' \
          -e 's/public Object o/public Object/g' \
          -e 's/public MethodType mt/public MethodType/g' \
          -e 's/public MethodHandle mh/public MethodHandle/g' \
          -e 's/Object o\.\.\./Object... o/g' \
          -e 's/public final native Object o/public final native Object/g' \
          -e 's/public Class<?> c/public Class<?>/g' \
        > classpath/${path}.java
    fi
  done
