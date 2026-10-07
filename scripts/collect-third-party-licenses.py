#!/usr/bin/env python3
"""Create a deterministic license bundle from resolved vcpkg installed trees."""

from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path, PurePosixPath


GENERATED_SHARE_FILES = {
    "vcpkg-cmake-wrapper.cmake",
    "vcpkg_abi_info.txt",
    "vcpkg.spdx.json",
    "vcpkg-spdx-resources.json",
}


def fail(message: str) -> None:
    raise RuntimeError(message)


def parse_status(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        fail(f"missing vcpkg status database: {path}")
    records: list[dict[str, str]] = []
    for block in path.read_text(encoding="utf-8").replace("\r\n", "\n").strip().split("\n\n"):
        record = {}
        for line in block.splitlines():
            if ": " in line:
                key, value = line.split(": ", 1)
                record[key] = value
        if record.get("Status") == "install ok installed":
            if "Package" not in record or "Architecture" not in record:
                fail(f"malformed installed-package record in {path}")
            records.append(record)
    if not records:
        fail(f"no installed packages recorded in {path}")
    return records


def package_has_payload(root: Path, package: str, architecture: str) -> bool:
    info = root / "vcpkg" / "info"
    lists = sorted(info.glob(f"{package}_*_{architecture}.list"))
    if not lists:
        fail(f"missing package file list for {package}:{architecture} under {root}")
    for list_file in lists:
        for raw in list_file.read_text(encoding="utf-8").splitlines():
            entry = raw.strip().replace("\\", "/")
            if not entry or entry.endswith("/"):
                continue
            path = PurePosixPath(entry)
            parts = path.parts
            if len(parts) >= 4 and parts[0] == architecture and parts[1] == "share":
                if parts[-1] in GENERATED_SHARE_FILES:
                    continue
                if parts[-1] == "usage":
                    continue
            return True
    return False


def collect(roots: list[Path]) -> tuple[dict[tuple[str, str], bytes], list[str]]:
    notices: dict[tuple[str, str], bytes] = {}
    metadata_only: list[str] = []
    for root in roots:
        root = root.resolve()
        records = parse_status(root / "vcpkg" / "status")
        for record in records:
            package = record["Package"]
            architecture = record["Architecture"]
            key = (architecture, package)
            copyright_file = root / architecture / "share" / package / "copyright"
            if not copyright_file.is_file():
                if package_has_payload(root, package, architecture):
                    fail(
                        "resolved package has distributable payload but no "
                        f"share/{package}/copyright: {package}:{architecture} ({root})"
                    )
                metadata_only.append(f"{package}:{architecture}")
                continue
            data = copyright_file.read_bytes()
            if not data:
                fail(f"empty copyright file: {copyright_file}")
            previous = notices.get(key)
            if previous is not None and previous != data:
                fail(f"conflicting copyright files for {package}:{architecture}")
            notices[key] = data
    if not notices:
        fail("no vcpkg copyright files found")
    return notices, sorted(set(metadata_only))


def render(notices: dict[tuple[str, str], bytes]) -> bytes:
    chunks = [
        b"KrKr2 resolved vcpkg third-party licenses\n"
        b"Generated deterministically from installed share/<port>/copyright files.\n"
    ]
    for (architecture, package), data in sorted(notices.items()):
        digest = hashlib.sha256(data).hexdigest()
        chunks.append(
            (
                f"\n{'=' * 78}\n"
                f"{package}:{architecture}\n"
                f"Source: {architecture}/share/{package}/copyright\n"
                f"SHA-256: {digest}\n"
                f"{'=' * 78}\n"
            ).encode("utf-8")
        )
        chunks.append(data)
        if not data.endswith(b"\n"):
            chunks.append(b"\n")
    return b"".join(chunks)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("installed_roots", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        notices, metadata_only = collect(args.installed_roots)
        output = args.output.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(render(notices))
    except (OSError, UnicodeError, RuntimeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    print(f"wrote {output} with {len(notices)} resolved package/triplet notices")
    if metadata_only:
        print(
            "metadata-only packages without distributable payload/copyright: "
            + ", ".join(metadata_only)
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
