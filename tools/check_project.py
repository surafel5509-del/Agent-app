#!/usr/bin/env python3
"""Static consistency checks for the Nexa AI Android project.

The sandbox has no Android SDK/Gradle, so this script approximates the compiler where it can:
brace balance, package/path agreement, duplicate declarations, string-resource usage,
internal import resolution, unresolved type & cross-package symbol checks, property-delegation
imports, XML/manifest validation, Gradle catalog keys, and secret-leak scanning.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main")
TEST = os.path.join(ROOT, "app", "src", "test")
errors = []
warnings = []

KOTLIN_DEFAULT_TYPES = {
    "Any", "Nothing", "Unit", "Boolean", "Byte", "Short", "Int", "Long", "Float", "Double",
    "Char", "String", "CharSequence", "Array", "ByteArray", "IntArray", "LongArray",
    "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap", "Collection", "Iterable",
    "Sequence", "Pair", "Triple", "Result", "Lazy", "Comparator", "Comparable",
    "Throwable", "Exception", "RuntimeException", "Error", "IllegalArgumentException",
    "IllegalStateException", "IndexOutOfBoundsException", "UnsupportedOperationException",
    "NumberFormatException", "NullPointerException",
    "StringBuilder", "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet",
    "System", "Thread", "Runnable", "Math", "Object", "Class",
    "Deprecated", "Suppress", "OptIn", "JvmStatic", "JvmOverloads", "JvmField", "Volatile",
    "Retention", "AnnotationRetention", "Target", "AnnotationTarget",
    "Charsets", "Regex", "MatchResult", "UInt", "ULong",
}


def strip_kotlin(src: str) -> str:
    """Replaces comments/strings with placeholders, preserving newlines for line numbers."""
    out = []
    i = 0
    n = len(src)
    NORMAL, RAW, STRING, CHAR = range(4)
    state = NORMAL

    def emit(text):
        for ch in text:
            out.append("\n" if ch == "\n" else " ")

    while i < n:
        if state == NORMAL:
            if src.startswith('"""', i):
                state = RAW
                i += 3
                continue
            if src[i] == '"':
                state = STRING
                i += 1
                continue
            if src[i] == "'":
                state = CHAR
                i += 1
                continue
            if src.startswith("//", i):
                j = src.find("\n", i)
                if j == -1:
                    j = n
                i = j
                continue
            if src.startswith("/*", i):
                depth = 1
                i += 2
                while i < n and depth:
                    if src.startswith("/*", i):
                        depth += 1
                        i += 2
                    elif src.startswith("*/", i):
                        depth -= 1
                        i += 2
                    else:
                        if src[i] == "\n":
                            out.append("\n")
                        i += 1
                out.append(" ")
                continue
            out.append(src[i])
            i += 1
        elif state == RAW:
            if src.startswith('"""', i):
                state = NORMAL
                i += 3
                out.append('""')
                continue
            emit(src[i])
            i += 1
        elif state == STRING:
            if src[i] == "\\":
                emit(src[i:i + 2])
                i += 2
                continue
            if src[i] == '"':
                state = NORMAL
                i += 1
                out.append('""')
                continue
            emit(src[i])
            i += 1
        else:  # CHAR
            if src[i] == "\\":
                emit(src[i:i + 2])
                i += 2
                continue
            if src[i] == "'":
                state = NORMAL
                i += 1
                out.append("''")
                continue
            emit(src[i])
            i += 1
    return "".join(out)


def check_balance(path, stripped):
    pairs = {"(": ")", "{": "}", "[": "]"}
    stack = []
    line = 1
    for ch in stripped:
        if ch == "\n":
            line += 1
        if ch in pairs:
            stack.append((ch, line))
        elif ch in pairs.values():
            if not stack:
                errors.append(f"{path}: unbalanced '{ch}' at line {line}")
                return
            open_ch, open_line = stack.pop()
            if pairs[open_ch] != ch:
                errors.append(
                    f"{path}: '{open_ch}' opened at line {open_line} closed by '{ch}' at line {line}"
                )
                return
    if stack:
        errors.append(f"{path}: unclosed '{stack[-1][0]}' opened at line {stack[-1][1]}")


def top_level_names(stripped: str):
    """Names of classes/objects/interfaces/functions/properties declared at brace depth 0."""
    names = []
    depth = 0
    for m in re.finditer(r"[{}]|\b(class|object|interface|fun|val|var)\s+(?:<[^>]+>\s+)?(?:[\w.<>, ?*]+\.)?([A-Za-z_]\w*)", stripped):
        token = m.group(0)
        if token == "{":
            depth += 1
        elif token == "}":
            depth -= 1
        elif depth == 0:
            keyword = m.group(1)
            at_column_zero = m.start() == 0 or stripped[m.start() - 1] == "\n"
            if keyword in ("val", "var") and not at_column_zero:
                continue
            names.append(m.group(2))
    return names


def kt_files(base):
    for dirpath, _, filenames in os.walk(base):
        for name in filenames:
            if name.endswith(".kt"):
                yield os.path.join(dirpath, name)


def rel(path):
    return os.path.relpath(path, ROOT)


# ---------------------------------------------------------------------------
# 1. Kotlin syntax & symbol checks
# ---------------------------------------------------------------------------
strings_xml = os.path.join(APP, "res", "values", "strings.xml")
tree = ET.parse(strings_xml)
string_names = {e.get("name") for e in tree.getroot().findall("string")}

packages = {}
declared = {}
pkg_symbols = {}
file_all_capitalized_decls = {}

all_kt_paths = sorted(list(kt_files(APP)) + list(kt_files(TEST)))

for path in all_kt_paths:
    src = open(path, encoding="utf-8").read()
    stripped = strip_kotlin(src)
    m = re.search(r"^\s*package\s+([\w.]+)", stripped, re.M)
    if m:
        pkg = m.group(1)
        packages[rel(path)] = pkg
        names = top_level_names(stripped)
        for name in names:
            key = (pkg, name)
            if key in declared and declared[key] != rel(path):
                errors.append(
                    f"duplicate declaration {pkg}.{name} in {declared[key]} and {rel(path)}"
                )
            declared.setdefault(key, rel(path))
            pkg_symbols.setdefault(pkg, set()).add(name)
        all_caps_decls = set(
            re.findall(r"\b(?:class|interface|object|typealias|fun|val)\s+([A-Z]\w*)", stripped)
        )
        file_all_capitalized_decls[rel(path)] = all_caps_decls
        pkg_symbols.setdefault(pkg, set()).update(all_caps_decls)

all_packages = set(packages.values())
decl_packages_by_name = {}
for (pkg, name) in declared:
    decl_packages_by_name.setdefault(name, set()).add(pkg)

SECRET_PATTERNS = [
    (r"eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.", "JWT literal"),
    (r"\bsk-(?:or-|proj-)?[A-Za-z0-9_-]{20,}\b", "API secret key"),
    (r"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}\b", "GitHub token"),
    (r"\bgithub_pat_[A-Za-z0-9_]{20,}\b", "GitHub PAT"),
]

for base in (APP, TEST):
    for path in sorted(kt_files(base)):
        rpath = rel(path)
        src = open(path, encoding="utf-8").read()
        stripped = strip_kotlin(src)
        check_balance(rpath, stripped)

        for pat, desc in SECRET_PATTERNS:
            if re.search(pat, src):
                errors.append(f"{rpath}: potential hardcoded {desc}")

        m = re.search(r"^\s*package\s+([\w.]+)", stripped, re.M)
        if not m:
            errors.append(f"{rpath}: missing package declaration")
            continue
        package = m.group(1)
        expected = os.path.relpath(os.path.dirname(path), os.path.join(base, "java")).replace(os.sep, ".")
        if package != expected:
            errors.append(f"{rpath}: package '{package}' does not match directory '{expected}'")

        for used in re.findall(r"R\.string\.([A-Za-z0-9_]+)", stripped):
            if used not in string_names:
                errors.append(f"{rpath}: missing string resource R.string.{used}")

        imports_list = re.findall(r"^\s*import\s+([\w.*]+)", stripped, re.M)
        imported_symbols = {imp.split(".")[-1] for imp in imports_list if not imp.endswith(".*")}
        star_packages = {imp[:-2] for imp in imports_list if imp.endswith(".*")}

        # Internal import resolution
        for imported in imports_list:
            if not imported.startswith("com.nexa.ai."):
                continue
            parts = imported.split(".")
            symbol = parts[-1]
            pkg = ".".join(parts[:-1])
            if pkg not in all_packages:
                errors.append(f"{rpath}: unknown package in import {imported}")
            elif symbol != "*" and (pkg, symbol) not in declared and symbol not in {
                "R", "BuildConfig", "MainActivity", "NexaApp",
            }:
                errors.append(f"{rpath}: cannot resolve import {imported}")

        # Compose State `by` delegation check
        has_val_by = bool(re.search(r"\b(?:val|var)\s+[A-Za-z_]\w*\s+by\s+(?:remember|rememberSaveable|viewModel\.|transition\.)", stripped))
        has_var_by = bool(re.search(r"\bvar\s+[A-Za-z_]\w*\s+by\s+(?:remember|rememberSaveable)", stripped))
        if has_val_by and "androidx.compose.runtime.getValue" not in imports_list and "androidx.compose.runtime" not in star_packages:
            errors.append(f"{rpath}: missing import androidx.compose.runtime.getValue for 'by' delegation")
        if has_var_by and "androidx.compose.runtime.setValue" not in imports_list and "androidx.compose.runtime" not in star_packages:
            errors.append(f"{rpath}: missing import androidx.compose.runtime.setValue for 'var ... by' delegation")

        # Capitalized type / Composable symbol resolution
        body_stripped = re.sub(r"^\s*(package|import)\s+.*$", "", stripped, flags=re.M)
        for ident in sorted(set(re.findall(r"(?<![.\w])([A-Z][a-zA-Z0-9_]*)\b", body_stripped))):
            if ident.isupper() or "_" in ident or len(ident) == 1:
                continue
            if (
                ident in KOTLIN_DEFAULT_TYPES
                or ident in file_all_capitalized_decls.get(rpath, set())
                or ident in pkg_symbols.get(package, set())
                or ident in imported_symbols
                or (ident in {"R", "BuildConfig"} and package == "com.nexa.ai")
            ):
                continue
            if any(p in star_packages for p in decl_packages_by_name.get(ident, set())):
                continue
            errors.append(f"{rpath}: unresolved type/composable reference '{ident}'")

        # Cross-package top-level helper calls (including inside string templates)
        no_comments = re.sub(r"//.*", "", src)
        no_comments = re.sub(r"/\*[\s\S]*?\*/", "", no_comments)
        body_with_strings = re.sub(r"^\s*(package|import)\s+.*$", "", no_comments, flags=re.M)
        for call_match in re.finditer(r"(?<![.\w])([a-z][A-Za-z0-9_]*)\s*\(", body_with_strings):
            fn = call_match.group(1)
            if fn not in decl_packages_by_name:
                continue
            pkgs = decl_packages_by_name[fn]
            if package in pkgs or fn in imported_symbols or any(p in star_packages for p in pkgs):
                continue
            # Skip if defined locally in this file
            if re.search(rf"\bfun\s+(?:[\w.<>, ?*]+\.)?{re.escape(fn)}\b", no_comments):
                continue
            errors.append(f"{rpath}: unimported call to '{fn}' (declared in {sorted(pkgs)})")

        # Unused imports (aware of `by` delegation and operators)
        body_wo_imports = re.sub(r"^\s*import\s+.*$", "", no_comments, flags=re.M)
        has_any_by = bool(re.search(r"\b(?:val|var)\s+[A-Za-z_][\w.]*\s+by\b", stripped))
        has_any_var_by = bool(re.search(r"\bvar\s+[A-Za-z_][\w.]*\s+by\b", stripped))
        for imported in imports_list:
            symbol = imported.split(".")[-1]
            if symbol == "*" or "." not in imported:
                continue
            if symbol == "getValue" and has_any_by:
                continue
            if symbol == "setValue" and has_any_var_by:
                continue
            if not re.search(r"\b" + re.escape(symbol) + r"\b", body_wo_imports):
                warnings.append(f"{rpath}: unused import {imported}")

# ---------------------------------------------------------------------------
# 2. Resources / manifest
# ---------------------------------------------------------------------------
for dirpath, _, filenames in os.walk(os.path.join(APP, "res")):
    for name in filenames:
        if name.endswith(".xml"):
            path = os.path.join(dirpath, name)
            try:
                ET.parse(path)
            except ET.ParseError as exc:
                errors.append(f"{rel(path)}: invalid XML ({exc})")

manifest = os.path.join(APP, "AndroidManifest.xml")
manifest_text = open(manifest, encoding="utf-8").read()
mtree = ET.parse(manifest)
AND_NS = "{http://schemas.android.com/apk/res/android}"
app_el = mtree.getroot().find("application")
if app_el is None:
    errors.append("AndroidManifest.xml: missing <application> element")
else:
    if f"{AND_NS}windowSoftInputMode" in app_el.attrib:
        errors.append("AndroidManifest.xml: android:windowSoftInputMode belongs on <activity>, not <application>")
    if app_el.attrib.get(f"{AND_NS}usesCleartextTraffic") != "false":
        errors.append("AndroidManifest.xml: android:usesCleartextTraffic must be 'false'")

refs = set(re.findall(r"@(string|drawable|mipmap|style|color)/([\w.]+)", manifest_text))
for kind, name in refs:
    if kind == "string" and name not in string_names:
        errors.append(f"AndroidManifest.xml: missing @string/{name}")
    if kind in ("drawable", "mipmap", "color", "style"):
        found = False
        for dirpath, _, filenames in os.walk(os.path.join(APP, "res")):
            for fname in filenames:
                if fname.startswith(name + ".") or fname == name + ".xml":
                    found = True
        for values_dir in ("values", "values-night"):
            values_path = os.path.join(APP, "res", values_dir)
            if not os.path.isdir(values_path):
                continue
            for fname in os.listdir(values_path):
                text = open(os.path.join(values_path, fname), encoding="utf-8").read()
                if re.search(rf'<{kind} name="{re.escape(name)}"', text) or (
                    kind == "style" and f'name="{name}"' in text
                ):
                    found = True
        if not found:
            errors.append(f"AndroidManifest.xml: unresolved @{kind}/{name}")

# ---------------------------------------------------------------------------
# 3. Gradle catalog keys
# ---------------------------------------------------------------------------
catalog_path = os.path.join(ROOT, "gradle", "libs.versions.toml")
catalog = open(catalog_path, encoding="utf-8").read()
for path in [os.path.join(ROOT, "build.gradle.kts"), os.path.join(ROOT, "app", "build.gradle.kts")]:
    if not os.path.exists(path):
        continue
    text = open(path, encoding="utf-8").read()
    text = re.sub(r"//.*", "", text)
    for ref in re.findall(r"libs\.([A-Za-z0-9_.]+)", text):
        parts = ref.split(".")
        if parts[0] == "plugins":
            section = "plugins"
            key = "-".join(parts[1:])
        elif parts[0] == "versions":
            section = "versions"
            key = parts[1]
        else:
            section = "libraries"
            key = "-".join(parts)
        if not re.search(rf"^{re.escape(key)}\s*=", catalog, re.M):
            errors.append(f"{rel(path)}: unknown catalog key libs.{ref} ({section}.{key})")

print(f"checked {len(packages)} Kotlin files, {len(string_names)} string resources")
if warnings:
    print("\nwarnings:")
    for w in sorted(set(warnings)):
        print("  -", w)
if errors:
    print("\nERRORS:")
    for e in sorted(set(errors)):
        print("  -", e)
    sys.exit(1)
print("\nstatic checks passed")
