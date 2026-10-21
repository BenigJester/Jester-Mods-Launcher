# Launcher follow-up: issues 57 and 61

## Candy Crush / issue 57

The attached 4.4.5 report identifies Candy Crush 1.338.0.2 / 13380020,
Samsung SM-S921U1, Android 16/API 36, WebView 155.0.8059.43. The main
process resumes its Activity, then records an uncaught RuntimeException in
`org.chromium.android_webview.AwDataDirLock`. The report does not capture the
lock owner or exception message; contention is a supported lead, not a proven
complete root cause. There is no evidence here of a native module-hook crash.

BlackBox already configures a user/package/process WebView suffix before guest
Application creation. The additional process-lifetime ownership lease prevents
two cooperating guest processes from selecting that same profile concurrently.
The normal case retains the old suffix and stored browser data. A concurrent
owner uses one of three stable alternative profiles; all four busy or I/O/setup
failure fails explicitly, without continuing in an unconfigured shared profile.
The timeline records primary/alternate selection or setup failure. The lease is
not Chromium's lock: no Chromium locks, cookies, databases or saves are deleted.
Alternative profiles have their own cookies/login state; they do not copy the
busy profile. This is a contention mitigation, not a confirmed device fix.

Limitations: an older launcher process does not participate in this lease. The
guard cannot repair inaccessible WebView storage, already-initialized WebView,
or an app replacing the configured suffix. Confirm on the affected Samsung:
cold launch, exit/reopen and concurrent-helper startup, with a fresh report.
Root and standalone direct-patched game initialization are unchanged.

Reference contracts:
- https://developer.android.com/reference/android/webkit/WebView#setDataDirectorySuffix(java.lang.String)
- https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/java/src/org/chromium/android_webview/AwDataDirLock.java

## Soul Knight / issue 61

The attached 4.4.5 report fails at byte zero downloading the 698348799-byte
Soul Knight 8.7.0 APKS on API 35, with only IllegalArgumentException. It does
not establish whether URL identity, redirect metadata or response size failed.

Download diagnostics now report fixed check codes for source URL, redirect
status/missing target/URL/package/size, response size, byte-range metadata and
oversized bodies. HTTP failures retain their numeric status even in minified
builds. Terminal details reach the diagnostic callback before the exception is
wrapped. No arbitrary exception messages, URLs, request headers, account text or
tokens are added. Existing HTTPS/host/package/size/hash/signature checks remain
in force; mismatches are not retried as transient network errors.

This improves evidence for the next reproduction; it does not claim to repair
the original download or installation. No new UI or translations are needed.
