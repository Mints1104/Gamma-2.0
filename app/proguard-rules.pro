# ProGuard / R8 configuration for com.mints.projectgammatwo

#################################################
# 1) Preserve all generic signatures & annotations
#################################################
-keepattributes Signature, *Annotation*, KotlinMetadata

#################################################
# 2) Keep your model/data classes
#################################################
-keep class com.mints.projectgammatwo.data.** { *; }

#################################################
# 3) Keep your Retrofit service interfaces
#    (adjust package to where your InvasionApi lives)
#################################################
-keep interface com.mints.projectgammatwo.data.* {
    @retrofit2.http.* <methods>;
}

#################################################
# 4) Retrofit-related attributes & warnings
#################################################
-keepattributes InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*


#################################################
# 5) Gson
#################################################
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Fix for TypeToken generic type information
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature

#################################################
# 6) OkHttp
#################################################
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

#################################################
# 7) Room
#################################################
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

#################################################
# 8) Coroutines
#################################################
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

#################################################
# 9) Debug info & source file renaming
#################################################
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

#################################################
# 10) Android components
#################################################
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * implements androidx.viewbinding.ViewBinding

#################################################
# 11) XML-referenced Views
#################################################
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

#################################################
# 12) Keep onClick handlers
#################################################
-keepclassmembers class * extends android.content.Context {
    public void *(android.view.View);
}
