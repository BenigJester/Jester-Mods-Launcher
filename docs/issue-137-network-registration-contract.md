# Issue 137: preserve network callback registration state

https://github.com/BenigJester/Jester-Mods-Launcher/issues/137

Kingdom Adventures 2.6.5/265, Non-root launcher 4.6.2, Infinix X6885,
Android 16/API 36, ARM64. Selected PID 29004 starts at 1793101413147.
The short report captures IllegalArgumentException during activity teardown,
classified as network_callback_cleanup. It explicitly does not establish what
triggered shutdown. Only the short report is supplied; the requested full
report has not been provided as of this investigation.

Independent source defect: the requestNetwork hook forwarded the guest package
unchanged, unlike listenForNetwork, then caught all reflection exceptions and
returned null. Android's registration code stores the returned request on the
callback. A failed registration disguised as null can leave cleanup attempting
to unregister a callback that was never registered. Android performs this
validation in ConnectivityManager before calling its remote release method;
broad suppression in the IConnectivityManager unregister hook cannot fix it.

The registration hook now reuses MethodParameterUtils.replaceFirstAppPkg for
requestNetwork/listenForNetwork and the existing OEM registration names. It
forwards once and returns the original value, including a legitimate null.
InvocationTargetException is unwrapped so the original Android rejection reaches
the caller instead of becoming fake success. The duplicated registerNetworkCallback
annotation/implementation is removed; all four names use one ProxyMethods hook.

No request IDs or request objects are fabricated. Callback identity, network
permissions, capability flags, attribution strings, service release, networking
fallbacks outside registration and activity shutdown behavior are unchanged.
This is a registration-contract fix, not a verified fix for the game's exit.

Platform background:
https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/heads/main/framework/src/android/net/ConnectivityManager.java
https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/heads/main/service/src/com/android/server/ConnectivityService.java
The registration path supplies a calling package and stores the returned request;
unregisterNetworkCallback rejects a callback whose stored request is null.
ConnectivityService validates supplied package ownership against the Binder UID.
OEM and exact module versions may differ; these sources do not prove the
reporter's original shutdown cause.

Validation: NetworkCallbackRegistrationTest compiles and executes the production
hook body, real MethodParameterUtils, BzArrayUtils, MethodHook and ProxyMethods
against isolated service/BlackBox fakes. It checks all four hook names, guest,
host and unrelated package arguments, request identity, attribution/flag/token
preservation, null results, legacy/no-argument signatures, exception identity,
and single service invocation on error. PASS (one test). BlackBox Java and
Non-root debug launcher compilation pass. Log: ignored
temp/issue137-network-registration-check.log.

These isolated tests do not establish live Android callback delivery, package
permissions, game startup or shutdown behavior on the affected Infinix device.
No device installation, save/account change, issue reply, version bump, commit,
push or release is performed in this step. A full/fresh affected-device report
and test are still required before calling issue 137 resolved.
