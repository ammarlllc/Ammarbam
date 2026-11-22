# Ammar Share ProGuard / R8 Keep Rules
-keep class com.example.model.** { *; }
-keepclassmembers class com.example.ui.AmmarShareViewModel { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
