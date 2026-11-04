# Issue 45: preserve host isolated-service instance names

The new report uses launcher 4.6.0, Blackmoor 2 (`com.fourfats.blackmoor2`)
2608.26 / 260826, Samsung SM-A166P, Android 16/API 36, arm64-v8a,
4096-byte pages. The selected game PID is 5093, started at 1793029371676.
Android records reason 9 with `binder_limit_reported` at 1793029698498.
Supporting helper PID 5139 reaches 8473 local Binder objects, with 42800 bind
and 42782 unbind calls. Sampled callers include `WV.sp.b` and `WV.sp.c`.
These are shared-helper observations, not proof of retained-object ownership.

Report: https://github.com/BenigJester/Jester-Mods-Launcher/issues/45
(reporter's October 26, 2026 reply; attached session 1793029370130).

Independent source defect: `bindServiceInstance` and `bindIsolatedService`
cleared argument 6 before resolving the target. This was intended for BlackBox's
ordinary, non-isolated proxy services, but also altered native host WebView
service requests. Open system packages resolve through ActiveServices to their
original intent when no virtual service exists. Their isolated renderer instance
names must survive that pass-through path.

The adjustment now happens only beside the existing external-service flag
adjustment, after the resolved intent is known to target the launcher package.
Both hook entry points leave arguments untouched until that decision. Ordinary
bindService calling-package arguments and host external-service flags remain
unchanged. No forced retry, helper restart, GC, connection-count threshold,
WebView setting change, or save/account modification is introduced.

Platform background:
https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/renderer/README.md
describes WebView's external/isolated renderer service contract. This supports
preserving host isolation; it does not establish why this Samsung device reaches
its Binder limit.

Validation: `BlackBoxServiceInstanceTest` executes the production argument-routing
block in an isolated Java fixture with null/unresolved, two host WebView, and
launcher proxy targets, both instance-method names, integer/long flags, and an
ordinary bindService request. Both new checks and the three existing
`BinderDelegateCleanupTest` checks pass; BlackBox Java and the Non-root debug
launcher compile. Source checks ensure neither entry point clears
the instance first. These checks do not execute Android Binder or WebView.

Affected-device confirmation is still required. The report is insufficient to
claim that preserving instance names fixes all repeated binds or issue 45's
termination. No device installation or GitHub publication is part of this change.
