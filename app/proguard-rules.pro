# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Keep all custom exceptions for better crash reports
-keep public class * extends java.lang.Exception

# ============================================================
# Android Car API - используется через reflection
# ============================================================

# Сохраняем все классы и методы android.car.*
-keep class android.car.** { *; }
-keepclassmembers class android.car.** { *; }

# Сохраняем VehicleProperty с его константами
-keep class android.hardware.automotive.vehicle.** { *; }
-keepclassmembers class android.hardware.automotive.vehicle.** {
    public static final int *;
}

# ============================================================
# Наши сервисы и компоненты
# ============================================================

# Все Service классы
-keep public class * extends android.app.Service {
    public <init>(...);
    public void onCreate();
    public int onStartCommand(android.content.Intent, int, int);
    public void onDestroy();
}

# Все BroadcastReceiver классы
-keep public class * extends android.content.BroadcastReceiver {
    public <init>(...);
    public void onReceive(android.content.Context, android.content.Intent);
}

# Worker классы для WorkManager
-keep public class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
    public androidx.work.ListenableWorker$Result doWork();
}

# CarCoreService - используется reflection
-keep class com.bjornfree.drivemode.core.CarCoreService { *; }
-keepclassmembers class com.bjornfree.drivemode.core.CarCoreService { *; }

# Сохраняем static методы в DriveModeService
-keepclassmembers class com.bjornfree.drivemode.core.DriveModeService {
    public static *** logConsole(...);
    public static *** consoleSnapshot();
}

# ============================================================
# Kotlin и Coroutines
# ============================================================

# Сохраняем все для Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep companion objects
-keepclassmembers class ** {
    *** Companion;
}
-keepclassmembers class **$Companion {
    public <fields>;
    public <methods>;
}

# ============================================================
# Jetpack Compose и AndroidX
# ============================================================

-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.work.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# ============================================================
# Общие правила для reflection
# ============================================================

# Сохраняем конструкторы для reflection
-keepclassmembers class * {
    public <init>(...);
}

# Не обфусцировать enum'ы
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Serializable классы
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
# Оптимизация
# ============================================================

# Разрешить оптимизацию, но с осторожностью
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Не предупреждать о недостающих классах Car API (они есть только на automotive устройствах)
-dontwarn android.car.**
-dontwarn android.hardware.automotive.**

# Не предупреждать о других optional зависимостях
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
