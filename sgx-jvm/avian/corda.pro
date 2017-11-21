# proguard include file (http://proguard.sourceforge.net)

# We need these for Corda deserialisation:
-keep class sun.security.ec.ECPublicKeyImpl
-keep class sun.security.ec.ECPrivateKeyImpl
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateCrtKey

-keep class java.lang.invoke.SerializedLambda {
   private java.lang.Object readResolve();
}

-keep class com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl

