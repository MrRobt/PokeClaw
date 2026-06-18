# Debug builds are minified to match release startup behavior, but the
# instrumentation runner loads Kotlin-backed AndroidX test storage classes from
# the target process. Keep Kotlin runtime in debug APKs so connected tests can
# start on-device.
-keep class kotlin.** { *; }
-dontwarn kotlin.**
