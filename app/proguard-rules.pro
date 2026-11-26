# Alt Player Release ProGuard / R8 Keep Rules

# Preserve stack trace line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin Parcelize & Annotations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepnames class * implements android.os.Parcelable
-keepclassmembers class ** {
    @kotlinx.parcelize.Parcelize *;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# AndroidX DataStore Preferences & Protobuf runtime
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# AndroidX Media3 (ExoPlayer, Common, Session, UI)
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.ui.** { *; }
-keepclassmembers class * implements androidx.media3.common.Bundleable {
    public static final androidx.media3.common.Bundleable$Creator *;
}
-dontwarn androidx.media3.**

# Preserve playback service & MediaSession binder entrypoints
-keep public class com.example.player.PlaybackService
-keepclassmembers class com.example.player.PlaybackService { *; }
-keepclassmembers class com.example.player.PlaybackEngine { *; }
-keepclassmembers class * extends androidx.media3.session.MediaSessionService { *; }
