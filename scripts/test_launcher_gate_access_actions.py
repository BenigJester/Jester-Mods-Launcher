"""Guard the locked gate's primary route and opt-in manual key entry."""
from pathlib import Path
import re

source = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/moodtools/hub/LauncherGateScreen.kt").read_text(encoding="utf-8")
gate = source.split("private fun DefaultGateContent(", 1)[1].split("private fun GateBrandHeader", 1)[0]
assert "var accessKeyExpanded by rememberSaveable { mutableStateOf(false) }" in gate
assert re.search(r"LauncherGatePresentation.Locked -> Button\(\s+onClick = onUnlock", gate)
route = gate.index('Text("Use free 1-day Linkvertise access"')
toggle = gate.index("onClick = { accessKeyExpanded = !accessKeyExpanded }")
form = gate.index("visible = locked && accessKeyExpanded")
field = gate.index("OutlinedTextField(")
tutorial = gate.index('Text("Linkvertise Tutorial"')
assert route < tutorial < toggle < form < field
assert 'Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/VhDykReio3E"))' in gate
assert "onClick = onRedeemAccessKey" in gate[field:]
assert "onClick = onCopySupportCode" in gate[field:]
transition = gate[form:field]
assert "expandFrom = Alignment.Top" in transition
assert "shrinkTowards = Alignment.Top" in transition
assert transition.count("tween(300, easing = FastOutSlowInEasing)") == 2
print("Locked gate: Linkvertise primary, manual key entry collapsed by default.")
