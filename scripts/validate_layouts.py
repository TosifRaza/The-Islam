#!/usr/bin/env python3
"""Static layout validator: every element in a layout XML that produces
LayoutParams inside a ViewGroup must have layout_width/layout_height —
either inline, or defined by its referenced style. Catches the
'You must supply a layout_width attribute' InflateException class.
"""
import os, re, sys
import xml.etree.ElementTree as ET

RES = "/home/z/my-project/download/platform-dev/android/app/src/main/res"
S = "{http://schemas.android.com/apk/res/android}"

def style_dims():
    """style name -> (has_width, has_height, parent_name)"""
    dims = {}
    for f in ["styles.xml", "themes.xml"]:
        p = os.path.join(RES, "values", f)
        if not os.path.exists(p):
            continue
        try:
            root = ET.parse(p).getroot()
        except ET.ParseError:
            continue
        for st in root.iter("style"):
            name = st.get("name", "")
            has_w = has_h = False
            for it in st.iter("item"):
                n = it.get("name", "")
                if n == "android:layout_width":
                    has_w = True
                if n == "android:layout_height":
                    has_h = True
            parent = st.get("parent")  # explicit parent= inheritance
            if name in dims:
                pw, ph, _ = dims[name]
                has_w = has_w or pw
                has_h = has_h or ph
            dims[name] = (has_w, has_h, parent)
    # follow explicit parent= chain, then dotted-name chain
    def lookup(name, which):
        seen = set()
        while name and name not in seen:
            seen.add(name)
            w, h, parent = dims.get(name, (False, False, None))
            if (which == "w" and w) or (which == "h" and h):
                return True
            if parent and parent in dims:
                name = parent
                continue
            dotted = name.rsplit(".", 1)[0] if "." in name else None
            name = dotted if dotted in dims else None
        return False
    return lookup

def check_file(path, lookup):
    problems = []
    try:
        tree = ET.parse(path)
    except ET.ParseError as e:
        return [f"PARSE ERROR: {e}"]
    for el in tree.iter():
        tag = el.tag.split(".")[-1]
        if tag in ("layout", "include", "merge"):
            continue
        line = getattr(el, "sourceline", 0) if hasattr(el, "sourceline") else 0
        style = el.get("style")
        w = el.get(S + "layout_width")
        h = el.get(S + "layout_height")
        if w is not None and h is not None:
            continue
        # style may supply them (strip "@style/" prefix)
        sw = lookup(style.replace("@style/", ""), "w") if style else False
        sh = lookup(style.replace("@style/", ""), "h") if style else False
        if sw and sh:
            continue
        # root element of a fragment layout also needs them (inflated into container)
        if not sw or not sh:
            missing = []
            if not w and not sw:
                missing.append("layout_width")
            if not h and not sh:
                missing.append("layout_height")
            if missing:
                problems.append(f"line {line}: <{tag}> missing {', '.join(missing)} (style={style})")
    return problems

lookup = style_dims()
bad = 0
for root_dir, _, files in os.walk(RES):
    for fn in sorted(files):
        if fn.endswith(".xml") and (fn.startswith("fragment_") or fn.startswith("activity_") or
                                     fn.startswith("item_") or fn.startswith("view_") or
                                     fn.startswith("sheet_") or fn.startswith("dialog_")):
            p = os.path.join(root_dir, fn)
            probs = check_file(p, lookup)
            if probs:
                bad += 1
                print(f"== {os.path.relpath(p, RES)} ==")
                for x in probs:
                    print("   ", x)
print("RESULT:", "FAIL" if bad else "PASS", f"({bad} file(s) with problems)")
sys.exit(1 if bad else 0)
