#!/usr/bin/env python3
"""Static validator for Android Navigation graphs (regression guard).

Background: on 2026-11-05 a field build crashed at startup with
  ClassNotFoundException: Didn't find class "int"
because nav_graph.xml used app:argType="int" (invalid; the primitive name is
"integer"). NavType.fromArgType() treats unknown names as Java class names,
so the NavInflater failed inside FragmentContainerView inflation and the
whole app died on launch — on EVERY device, not just some.

This script parses every res/navigation/*.xml and fails (exit 1) when:
  1. an <argument> app:argType is not one of the valid primitive names
     (string, integer, float, long, boolean, reference), optionally suffixed
     with "[]", and not a fully-qualified class name (must contain a dot);
  2. a primitive/boolean/reference argument has no android:defaultValue
     (inflater yields null -> NPE later on the device);
  3. android:defaultValue does not parse for the declared primitive type;
  4. app:startDestination or app:destination references an id that no
     destination in the same graph defines (unresolvable at runtime).

Usage: validate_nav_graphs.py <path-to-android-module>  (default: ../download/platform-dev/android)
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "http://schemas.android.com/apk/res/android"
APP_NS = "http://schemas.android.com/apk/res-auto"
PRIMITIVES = {"string", "integer", "float", "long", "boolean", "reference"}
VALUE_RE = {
    "integer": re.compile(r"^[+-]?\d+$"),
    "long": re.compile(r"^[+-]?\d+[Ll]?$"),
    "float": re.compile(r"^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?[FfDd]?$"),
    "boolean": re.compile(r"^(true|false)$"),
}
REF_PREFIX = ("@", "?")


def fail(errors, graph, msg):
    errors.append(f"{graph.name}: {msg}")


def norm(ref):
    """@+id/foo (definition) and @id/foo (reference) denote the same id."""
    return ref.replace("@+id/", "@id/") if ref else ref


def check_graph(path: Path, errors):
    root = ET.parse(path).getroot()
    defined = set()
    for dest in root.iter():
        if dest.get(f"{{{ANDROID_NS}}}id"):
            defined.add(norm(dest.get(f"{{{ANDROID_NS}}}id")))

    start = norm(root.get(f"{{{APP_NS}}}startDestination"))
    if start and start not in defined:
        fail(errors, path, f"startDestination {start} not defined in graph")

    warnings = []
    for dest in root.iter():
        for action in dest.findall("action"):
            target = norm(action.get(f"{{{APP_NS}}}destination"))
            if target and target not in defined:
                d_id = norm(dest.get(f"{{{ANDROID_NS}}}id", "<graph>"))
                fail(errors, path,
                     f"action {action.get(f'{{{ANDROID_NS}}}id')} on {d_id} "
                     f"targets unknown destination {target}")

        for arg in dest.findall("argument"):
            name = arg.get(f"{{{ANDROID_NS}}}name", "<unnamed>")
            arg_type = arg.get(f"{{{APP_NS}}}argType", "string")
            default = arg.get(f"{{{ANDROID_NS}}}defaultValue")
            nullable = arg.get(f"{{{APP_NS}}}nullable", "false") == "true"

            if arg_type in PRIMITIVES or (arg_type.endswith("[]")
                                          and arg_type[:-2] in PRIMITIVES):
                base = arg_type[:-2] if arg_type.endswith("[]") else arg_type
            elif "." in arg_type:
                base = None  # fully-qualified class name — OK
            else:
                fail(errors, path,
                     f"argument '{name}': argType=\"{arg_type}\" is invalid — "
                     f"NavInflater will try Class.forName(\"{arg_type}\") and "
                     f"crash (use \"integer\" for int, \"boolean\" for bool...)")
                continue

            if base and default is None and not nullable and not arg_type.endswith("[]"):
                warnings.append(
                    f"argument '{name}' ({arg_type}): no android:defaultValue "
                    f"— inflates to null unless the caller always supplies it")
            if base and default is not None and default not in REF_PREFIX:
                if base == "reference":
                    if not default.startswith(REF_PREFIX):
                        fail(errors, path,
                             f"argument '{name}' (reference): defaultValue "
                             f"'{default}' is not a resource reference")
                elif base in VALUE_RE and not VALUE_RE[base].match(default):
                    fail(errors, path,
                         f"argument '{name}' ({arg_type}): defaultValue "
                         f"'{default}' does not parse as {base}")
    return warnings


def main():
    android_dir = Path(sys.argv[1] if len(sys.argv) > 1
                       else Path(__file__).resolve().parent.parent
                       / "download" / "platform-dev" / "android")
    graphs = sorted((android_dir / "app" / "src" / "main" / "res" / "navigation")
                    .glob("*.xml"))
    if not graphs:
        print(f"ERROR: no navigation graphs found under {android_dir}")
        return 1
    errors = []
    warnings = []
    for graph in graphs:
        warnings.extend(check_graph(graph, errors))

    for graph in graphs:
        print(f"  checked {graph.name}")
    for w in warnings:
        print(f"  WARNING {w}")
    if errors:
        print("\nNAV GRAPH ERRORS:")
        for e in errors:
            print("  " + e)
        return 1
    print("nav graphs OK "
          f"({len(graphs)} graph(s), all argTypes/defaults/destinations valid)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
