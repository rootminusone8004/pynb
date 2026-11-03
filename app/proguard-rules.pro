# Optimization and obfuscation rules for pynb
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep data models serialized via Gson and Serializable
-keep class com.pynb.app.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * { *; }

# Markwon & Commonmark rules
-dontwarn io.noties.markwon.**
-keep class io.noties.markwon.** { *; }
-dontwarn org.commonmark.**
-keep class org.commonmark.** { *; }

# Gson rules
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# JLatexMath rules
-dontwarn ru.noties.jlatexmath.**
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**
-keep class org.scilab.forge.jlatexmath.** { *; }

# Mermaid diagrams: methods called from JavaScript must keep their names
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# JSch (SSH kernels) instantiates its ciphers, KEX and signature classes by name
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.net.unix.**
# BouncyCastle classes used by JSch's ed25519 / X25519 implementations
-keep class org.bouncycastle.crypto.params.Ed25519** { *; }
-keep class org.bouncycastle.crypto.params.X25519** { *; }
-dontwarn org.bouncycastle.**
