# Non-root Facebook login

BlackBox hides native Facebook SSO from guest games because the physical Facebook
app sees the launcher's signing identity, not the game's. Browser login remains
owned and validated by the game's Facebook SDK.

Analysis input: Mini Militia 5.6.0 (327), `base.apk` SHA-256
`7C1A8920DDDAEFEF2D828EDE4F6B47F72248C674EEBD9002DF4A6758EBE20E3F`.
Smali confirms `CustomTabLoginMethodHandler` calls
`CustomTabUtils.getValidRedirectURI(String)` in both constructors.
`Validate.hasCustomTabRedirectActivity(Context,String)` queries with
`GET_RESOLVED_FILTER` and verifies every returned activity belongs to the caller
and is `com.facebook.CustomTabActivity`. The compatibility guard mirrors that
list check rather than `resolveActivity`, which can return null for tied matches.

When a guest probes `fb<digits>://authorize`, package-manager resolution returns no
activities only if that same virtual guest declares `com.facebook.CustomTabActivity`
for both the app-ID URI and `fbconnect://cct.<guest-package>`. This lets the SDK
select its standard package-based fallback. Explicit components, other packages,
other deep links, Facebook itself, and guests without that fallback retain their
original behavior. The physical original game's activity is not used as a fallback
for this suppressed probe. No Facebook application IDs are hardcoded.

The host manifest registers `fbconnect` with a wildcard authority, so Android can
offer Jester as an authority-matching callback handler. `FacebookCallbackActivity`
still validates the canonical `cct.<package>` authority, installed virtual guest,
and SDK activity. It forwards only to an existing live login transaction; the game
SDK retains its OAuth state and result validation. Callback tokens/URLs are not
added to diagnostic logs. A user-selected original-game default can still affect
Android's chooser; this does not intercept browser callbacks addressed explicitly
to another package or support arbitrary custom SDK redirect formats.

Verification: run `:app:testNonrootDebugUnitTest --tests
com.moodtools.hub.FacebookLoginCompatTest`, build the non-root release, then test
Mini Militia and Township browser login on-device. Confirm the SDK selects
`fbconnect` and the result returns to the BlackBox session rather than the original
installed game. Root launch is unaffected.

The focused two-test suite and non-root release build passed. On Infinix X6873 /
Android 16, the installed build resolves Jester and the original game with equal
authority-match specificity (`0x308000`) for both Mini Militia and Township's
package callbacks. After switching the guard to the full query list, Mini Militia's
live attempt used `fbconnect`, Android displayed Jester and the original game as
choices, and choosing Jester delivered the callback to the existing virtual
Facebook activity (relay, queued intent, recovered activity and delivered intent
logs confirmed). The foreground task remained the non-root BlackBox proxy. This
verifies routing, not account/save synchronization or every game's SDK variant.
