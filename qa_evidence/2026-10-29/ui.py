import re, subprocess, sys

D = "ea8bd6e0"
mode = sys.argv[1] if len(sys.argv) > 1 else "show"

def sh(*args):
    return subprocess.run(["adb", "-s", D] + list(args), capture_output=True, text=True).stdout

if mode in ("show", "dump"):
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = sh("shell", "cat", "/sdcard/ui.xml")
else:
    xml = open(mode).read()

node_re = re.compile(r"<node[^>]*/?>")
attr = lambda tag, name: (re.search(name + r'="([^"]*)"', tag) or [None, ""])[1]

for tag in node_re.findall(xml):
    text = attr(tag, "text")
    desc = attr(tag, "content-desc")
    cls = attr(tag, "class")
    bounds = attr(tag, "bounds")
    clickable = attr(tag, "clickable")
    if text.strip() or desc.strip():
        kind = "BTN" if clickable == "true" else ("EDIT" if "EditText" in cls else "")
        print(f"{kind:4} {text!r:45} desc={desc!r:22} {bounds}")
