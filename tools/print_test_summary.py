#!/usr/bin/env python3
"""Prints a concise summary of Gradle JUnit XML reports and fails if no tests ran."""
import glob
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    files = sorted(glob.glob("app/build/test-results/testDebugUnitTest/TEST-*.xml"))
    if not files:
        print("ERROR: No JUnit XML reports found under app/build/test-results/testDebugUnitTest/")
        return 1

    total = failed = errors = 0
    for path in files:
        root = ET.parse(path).getroot()
        t = int(root.attrib.get("tests", 0))
        f = int(root.attrib.get("failures", 0))
        e = int(root.attrib.get("errors", 0))
        total += t
        failed += f
        errors += e
        print(f"  {root.attrib.get('name')}: {t} tests, {f} failures, {e} errors ({root.attrib.get('time')}s)")

    print(f"TOTAL: {total} unit tests executed, {failed} failures, {errors} errors")
    return 0 if (total > 0 and failed == 0 and errors == 0) else 1


if __name__ == "__main__":
    sys.exit(main())
