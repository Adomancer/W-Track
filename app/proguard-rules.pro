# Preserve debug metadata & annotations required by DI, serialization, and Room.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Entry points & custom application/service/worker components
-keep public class wtrack.app.WtrackApplication { *; }
-keep public class wtrack.app.MainActivity { *; }
-keep public class wtrack.app.data.ingestion.WtrackNotificationListenerService { *; }

# Hilt & WorkManager (@HiltWorker)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.WorkerParameters { *; }
-keepclassmembers class * {
    @androidx.hilt.work.HiltWorker <init>(...);
}

# Room & generated database implementations (ADR-005 / DATA-05)
-keep class wtrack.app.data.local.WtrackDatabase { *; }
-keep class wtrack.app.data.local.WtrackDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# SQLCipher native bindings & Java entry points
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# kotlinx.serialization (local payload JSON, rules, encrypted backup payloads)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keep,includedescriptorclasses class wtrack.app.**$$serializer { *; }
-keepclassmembers class wtrack.app.** {
    *** Companion;
}
-keepclasseswithmembers class wtrack.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class wtrack.integration.**$$serializer { *; }
-keepclassmembers class wtrack.integration.** {
    *** Companion;
}
-keepclasseswithmembers class wtrack.integration.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# BouncyCastle Argon2id (ADR-006)
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters$Builder { *; }

# Provider parser registry & contracts
-keep class wtrack.integration.** { *; }
-keep class wtrack.app.domain.parser.** { *; }

# AndroidX Biometric prompt & CryptoObject callbacks
-keep class androidx.biometric.** { *; }

