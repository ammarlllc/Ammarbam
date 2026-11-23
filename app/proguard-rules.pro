# Fast Share ProGuard / R8 Keep Rules
-keep class com.example.model.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.transfer.** { *; }
-keep class com.example.ui.AmmarShareViewModel { <init>(android.app.Application); *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Keep CameraX providers and reflection-loaded config classes
-keep class androidx.camera.** { *; }
-keep class * implements androidx.camera.core.CameraXConfig$Provider { *; }
-keep class * extends androidx.camera.core.impl.CameraConfigProvider { *; }
-keepclassmembers class * implements androidx.camera.core.CameraXConfig$Provider { *; }
-keepclassmembers class * extends androidx.camera.core.impl.CameraConfigProvider { *; }
-keep interface com.google.common.util.concurrent.ListenableFuture { *; }
-keep class androidx.concurrent.** { *; }
-keep class com.google.zxing.** { *; }

-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
