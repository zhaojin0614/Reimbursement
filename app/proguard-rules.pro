# ProGuard rules
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.zhaojin.reimbursement.data.** { *; }
