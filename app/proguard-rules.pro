# Nexa AI release rules.
#
# The app talks to a self-hosted OpenCode gateway using Retrofit + kotlinx.serialization.
# Keep serializable model names intact: the server contract depends on them.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class com.nexa.ai.data.remote.dto.** { *; }

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlinx.serialization.**

# OkHttp platform probes
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
