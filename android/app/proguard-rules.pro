# The Anthropic Java SDK (Bitter's cloud brain) serializes with Jackson via reflection.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn com.anthropic.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.reflect.jvm.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
# The SDK's JSON-schema helper (structured outputs, unused here) references desktop-Java reflection.
-dontwarn com.github.victools.jsonschema.**
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
