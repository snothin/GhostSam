-keep class com.snothin.ghostsam.companion.StageReceiver { *; }

-keep class com.snothin.ghostsam.companion.GsdfrNative { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
