# Keep only the host-facing names and members used across the dynamic module boundary.
-keep,allowoptimization interface com.moodtools.hub.modules.MenuPlugin {
    public void onLaunch(com.moodtools.hub.modules.PluginContext);
}
-keep,allowoptimization class com.moodtools.hub.modules.PluginContext {
    public <init>(android.content.Context, java.lang.String, java.lang.String, java.lang.String);
    public android.content.Context getHostContext();
    public java.lang.String getPackageName();
    public java.lang.String getModuleDirectory();
    public java.lang.String getExecutionMode();
}

# JNI downcalls resolve these exact generated class and method names.
-keepclasseswithmembernames,includedescriptorclasses class com.moodtools.hub.nativebridge.NativeLinker {
    native <methods>;
}

# BlackBox invokes this Application and its lifecycle callbacks through framework/library APIs.
-keep,allowoptimization class com.moodtools.hub.HubApplication {
    public <init>();
    protected void attachBaseContext(android.content.Context);
    public void onCreate();
}

# Retain line/source metadata only in the separately stored R8 mapping, not local variable tables.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
