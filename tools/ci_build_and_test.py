#!/usr/bin/env python3
"""Runs the Nexa AI Gradle build, unit tests, APK verification, and /v1/health probe on CI.

Also emits GitHub Actions workflow annotations (::notice / ::error) and $GITHUB_STEP_SUMMARY
so full diagnostics are visible both in the GitHub UI and via the Check Runs REST API.
"""
import glob
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET


def gh_escape(text: str) -> str:
    return (
        text.replace("%", "%25")
        .replace("\r", "%0D")
        .replace("\n", "%0A")
    )


def emit_annotation(level: str, title: str, body: str) -> None:
    # GitHub annotations truncate around ~4 KB; chunk into 3200-char slices (max 8 chunks).
    clean = body.strip() or "(empty)"
    chunk_size = 3200
    chunks = [clean[i:i + chunk_size] for i in range(0, min(len(clean), chunk_size * 8), chunk_size)]
    for idx, chunk in enumerate(chunks):
        suffix = f" ({idx + 1}/{len(chunks)})" if len(chunks) > 1 else ""
        print(f"::{level} title={title}{suffix}::{gh_escape(chunk)}", flush=True)


def append_summary(markdown: str) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as fh:
            fh.write(markdown + "\n\n")


def run_cmd(cmd: list[str]) -> tuple[int, str]:
    print(f"\n$ {' '.join(cmd)}", flush=True)
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    lines: list[str] = []
    assert proc.stdout is not None
    for line in proc.stdout:
        sys.stdout.write(line)
        lines.append(line)
    proc.wait()
    return proc.returncode, "".join(lines)


def extract_gradle_errors(output: str) -> str:
    interesting = []
    lines = output.splitlines()
    for i, line in enumerate(lines):
        if (
            line.startswith("e: ")
            or "FAILURE:" in line
            or line.startswith("* What went wrong:")
            or "Execution failed for task" in line
            or "AAPT:" in line
            or "Unresolved reference" in line
            or "Exception" in line
            or "FAILED" in line
        ):
            start = max(0, i - 1)
            end = min(len(lines), i + 6)
            interesting.append("\n".join(lines[start:end]))
    if interesting:
        deduped = "\n---\n".join(dict.fromkeys(interesting))
        return deduped[-12000:]
    return "\n".join(lines[-80:])


def probe_health() -> None:
    url = "https://opencode-production-ef5d07d3.up.railway.app/v1/health"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            body = resp.read(2000).decode("utf-8", errors="replace")
            msg = f"HTTP {resp.status} from {url}\nBody: {body}"
            print(msg, flush=True)
            emit_annotation("notice", "HealthProbe", msg)
            append_summary(f"### Live Gateway `/v1/health`\n- Status: **HTTP {resp.status}**\n```json\n{body}\n```")
    except urllib.error.HTTPError as exc:
        body = exc.read(1000).decode("utf-8", errors="replace")
        msg = f"HTTP {exc.code} from {url}\nBody: {body}"
        print(msg, flush=True)
        emit_annotation("warning", "HealthProbe", msg)
        append_summary(f"### Live Gateway `/v1/health`\n- Status: **HTTP {exc.code}**\n```\n{body}\n```")
    except Exception as exc:
        msg = f"Probe error for {url}: {exc}"
        print(msg, flush=True)
        emit_annotation("warning", "HealthProbe", msg)


def summarize_junit() -> tuple[bool, str]:
    files = sorted(glob.glob("app/build/test-results/testDebugUnitTest/TEST-*.xml"))
    if not files:
        return False, "No JUnit XML reports generated."

    total = failed = errors = 0
    report_lines = []
    failure_details = []
    for path in files:
        root = ET.parse(path).getroot()
        t = int(root.attrib.get("tests", 0))
        f = int(root.attrib.get("failures", 0))
        e = int(root.attrib.get("errors", 0))
        total += t
        failed += f
        errors += e
        report_lines.append(
            f"{root.attrib.get('name')}: {t} tests, {f} failures, {e} errors ({root.attrib.get('time')}s)"
        )
        for tc in root.findall("testcase"):
            for tag in ("failure", "error"):
                el = tc.find(tag)
                if el is not None:
                    detail = (el.text or el.attrib.get("message") or "").strip()
                    failure_details.append(
                        f"{tc.attrib.get('classname')}#{tc.attrib.get('name')}:\n"
                        + "\n".join(detail.splitlines()[:20])
                    )

    header = f"TOTAL: {total} unit tests, {failed} failures, {errors} errors"
    summary = header + "\n" + "\n".join(report_lines)
    if failure_details:
        summary += "\n\nFAILURES:\n" + "\n\n".join(failure_details)
    ok = total > 0 and failed == 0 and errors == 0
    return ok, summary


def verify_apk() -> tuple[bool, str]:
    apk = "app/build/outputs/apk/debug/app-debug.apk"
    if not os.path.isfile(apk):
        return False, f"APK not found at {apk}"
    size_mb = os.path.getsize(apk) / (1024 * 1024)
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    apksigners = sorted(glob.glob(os.path.join(android_home, "build-tools", "*", "apksigner")))
    aapt2s = sorted(glob.glob(os.path.join(android_home, "build-tools", "*", "aapt2")))

    details = [f"APK: {apk} ({size_mb:.2f} MB)"]
    if apksigners:
        rc, out = run_cmd([apksigners[-1], "verify", "--verbose", apk])
        details.append(out.strip())
        if rc != 0:
            return False, "\n".join(details)
    if aapt2s:
        rc, out = run_cmd([aapt2s[-1], "dump", "badging", apk])
        badging = [
            ln for ln in out.splitlines()
            if ln.startswith(("package:", "sdkVersion:", "targetSdkVersion:", "uses-permission:", "application-label:", "launchable-activity:"))
        ]
        details.extend(badging)
        if rc != 0:
            return False, "\n".join(details)
    return True, "\n".join(details)


def main() -> int:
    os.chmod("./gradlew", 0o755)
    probe_health()

    overall_ok = True

    # 1. Assemble Debug APK
    rc_apk, out_apk = run_cmd(["./gradlew", "assembleDebug", "--stacktrace"])
    if rc_apk != 0:
        overall_ok = False
        err = extract_gradle_errors(out_apk)
        emit_annotation("error", "AssembleDebugFailed", err)
        append_summary(f"### `assembleDebug` FAILED\n```\n{err}\n```")
    else:
        apk_ok, apk_info = verify_apk()
        if not apk_ok:
            overall_ok = False
            emit_annotation("error", "ApkVerifyFailed", apk_info)
        else:
            emit_annotation("notice", "AssembleDebug", apk_info)
            append_summary(f"### Installable Debug APK Verified\n```\n{apk_info}\n```")

    # 2. Run JVM Unit Tests
    rc_test, out_test = run_cmd(["./gradlew", "testDebugUnitTest", "--stacktrace"])
    junit_ok, junit_summary = summarize_junit()
    if rc_test != 0 or not junit_ok:
        overall_ok = False
        err = junit_summary + "\n\n---\n" + extract_gradle_errors(out_test)
        emit_annotation("error", "UnitTestsFailed", err)
        append_summary(f"### `testDebugUnitTest` FAILED\n```\n{err}\n```")
    else:
        emit_annotation("notice", "UnitTestsPassed", junit_summary)
        append_summary(f"### JVM Unit Tests Passed\n```\n{junit_summary}\n```")

    return 0 if overall_ok else 1


if __name__ == "__main__":
    sys.exit(main())
