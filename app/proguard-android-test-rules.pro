# AndroidJUnitRunner and androidx.test.services use Kotlin runtime classes.
# The debug variant is minified, so keep the runtime in the androidTest APK.
-keep class kotlin.** { *; }
-keep class kotlin.jvm.** { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.**
