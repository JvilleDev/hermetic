# Gson model classes — fields are read via reflection
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep class com.hermetic.app.data.model.** { *; }

# Hilt
-keepclassmembers class * {
    @dagger.hilt.android.internal.lifecycle.HiltViewModelMap *;
}

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
