# Hermetic ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @dagger.hilt.android.internal.lifecycle.HiltViewModelMap *;
}
