package top.niunaijun.blackbox.utils;

/** Fixed install categories only: no raw errors, file paths, package labels or user content. */
public final class InstallFailureDiagnostics {
    public static String exceptionKind(Throwable error) {
        String fallback = "other";
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof SecurityException) return "permission";
            if (error instanceof OutOfMemoryError) return "memory";
            if (error instanceof NoSuchFieldError) fallback = "missing_field";
            else if (error instanceof NoSuchMethodError) fallback = "missing_method";
            else if (error instanceof NoClassDefFoundError) fallback = "missing_class";
            else if (error instanceof LinkageError) fallback = "linkage";
            else if (error instanceof NullPointerException) fallback = "null_reference";
            else if (error instanceof ReflectiveOperationException) fallback = "reflection";
            else if (error instanceof java.util.zip.ZipException) fallback = "archive";
            else if (error instanceof java.io.IOException && fallback.equals("other")) fallback = "io";
        }
        return fallback;
    }

    public static String resultCategory(String message) {
        if (message == null) return "unclassified";
        if (message.matches("install_exception:(source|bundle|archive|abi|parse|split_metadata|host_metadata|package_settings|stop_process|copy|commit):(permission|memory|archive|io|missing_field|missing_method|missing_class|linkage|null_reference|reflection|other)")) return message;
        String text = message.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("remote exception") || text.contains("service unavailable") || text.contains("binder")) return "service_unavailable";
        if (text.contains("does not support 32-bit") || text.contains("does not support 64-bit")) return "abi_rejected";
        if (text.contains("getpackagearchiveinfo error")) return "archive_inspection_failed";
        if (text.contains("parser apk error")) return "package_parse_failed";
        if (text.contains("install apk error")) return "package_copy_failed";
        if (text.contains("no apk files") || text.contains("could not find base apk")) return "invalid_bundle";
        return "unclassified";
    }

    private InstallFailureDiagnostics() { }
}
