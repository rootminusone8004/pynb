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
