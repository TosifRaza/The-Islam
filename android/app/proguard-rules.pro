# IKP Android — R8 rules (M3)
# Gson reads DTO fields reflectively → wire models must survive obfuscation.
-keep class com.ikp.dev.app.data.remote.dto.** { *; }
-keep class com.ikp.dev.app.core.network.ApiResponse { <fields>; }
-keep class com.ikp.dev.app.core.network.ApiResponse$* { *; }
-keep class com.ikp.dev.app.core.error.ErrorMapper$Problem { <fields>; }

-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, Exceptions

# Retrofit interface methods (annotation-reflective invocation)
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn retrofit2.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**
-dontwarn kotlin.**
-dontwarn okio.**

# Gson runtime
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Room / WorkManager ship consumer rules inside their AARs — nothing needed here.
