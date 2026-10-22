-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class **$CREATOR {
    *;
}
-keepclassmembers class * implements androidx.media3.common.Bundleable {
    public static final ** CREATOR;
}

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-dontwarn androidx.media3.**
-dontwarn android.support.v4.media.**

-dontwarn org.schabi.newpipe.extractor.**
-keep class org.schabi.newpipe.extractor.** { <init>(...); }
-dontwarn com.grack.nanojson.**
-dontwarn org.jsoup.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn com.google.protobuf.**

-dontwarn okhttp3.**
-dontwarn okio.**

-keep class com.zai.avora.data.model.** { *; }
-keep class com.zai.avora.data.auth.UserAccount { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
