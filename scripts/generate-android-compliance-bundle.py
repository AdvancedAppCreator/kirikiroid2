#!/usr/bin/env python3
"""Bundle exact FFmpeg source and Android LGPL relinking materials."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path


FFMPEG_LIBS = {
    "libavcodec.a",
    "libavfilter.a",
    "libavformat.a",
    "libavutil.a",
    "libswresample.a",
    "libswscale.a",
}
FIXED_TIME = (1980, 1, 1, 0, 0, 0)


def fail(message: str) -> None:
    raise RuntimeError(message)


def cache_value(cache: Path, name: str) -> str:
    pattern = re.compile(rf"^{re.escape(name)}(?::[^=]*)?=(.*)$")
    for line in cache.read_text(encoding="utf-8", errors="replace").splitlines():
        match = pattern.match(line)
        if match:
            return match.group(1)
    fail(f"{name} is absent from {cache}")


def git_clean(root: Path) -> bool:
    result = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=all"],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    )
    return not result.stdout.strip()


def find_link_output(build: Path) -> str:
    for line in (build / "build.ninja").read_text(
        encoding="utf-8", errors="replace"
    ).splitlines():
        if line.startswith("build ") and "libkrkr2.so:" in line:
            return line[6 : line.index(": ")].replace("$:", ":")
    fail(f"libkrkr2.so link edge is absent from {build / 'build.ninja'}")


def direct_link_inputs(build: Path, output: str) -> list[Path]:
    result = subprocess.run(
        ["ninja", "-C", str(build), "-t", "query", output],
        check=True,
        capture_output=True,
        text=True,
    )
    inputs: list[Path] = []
    in_inputs = False
    for line in result.stdout.splitlines():
        if line.startswith("  input:"):
            in_inputs = True
            continue
        if line.startswith("  outputs:"):
            break
        if not in_inputs or not line.startswith("    "):
            continue
        value = line.strip().removeprefix("| ")
        candidate = Path(value)
        if not candidate.is_absolute():
            candidate = build / candidate
        if candidate.is_file() and candidate.suffix.lower() in {".o", ".a", ".so"}:
            inputs.append(candidate.resolve())
    if not inputs:
        fail(f"no direct object/library inputs found for {output}")
    return sorted(set(inputs), key=lambda path: str(path).lower())


def link_command(build: Path) -> str:
    result = subprocess.run(
        ["ninja", "-C", str(build), "-t", "commands", "krkr2"],
        check=True,
        capture_output=True,
        text=True,
    )
    commands = [line for line in result.stdout.splitlines() if "libkrkr2.so" in line]
    if not commands:
        fail(f"final krkr2 link command is absent from {build}")
    return commands[-1] + "\n"


def archive_name(path: Path, build: Path, installed: Path, abi: str) -> str:
    for base, label in ((build, "build"), (installed, "vcpkg_installed")):
        try:
            relative = path.relative_to(base)
            return f"relink/{abi}/{label}/{relative.as_posix()}"
        except ValueError:
            pass
    digest = hashlib.sha256(str(path).encode()).hexdigest()[:12]
    return f"relink/{abi}/external/{digest}-{path.name}"


def zip_file(zf: zipfile.ZipFile, source: Path, name: str) -> str:
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = (0o100644 & 0xFFFF) << 16
    digest = hashlib.sha256()
    with source.open("rb") as src, zf.open(info, "w") as dst:
        while chunk := src.read(1024 * 1024):
            digest.update(chunk)
            dst.write(chunk)
    return digest.hexdigest()


def zip_bytes(zf: zipfile.ZipFile, data: bytes, name: str) -> str:
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = (0o100644 & 0xFFFF) << 16
    zf.writestr(info, data)
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--build-dir", action="append", required=True, type=Path)
    parser.add_argument(
        "--allow-dirty",
        action="store_true",
        help="permit a non-clean worktree (validation only; never use for release)",
    )
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        if not args.allow_dirty and not git_clean(root):
            fail("release compliance bundles require a clean Git worktree")

        builds = [path.resolve() for path in args.build_dir]
        caches = [build / "CMakeCache.txt" for build in builds]
        if any(not cache.is_file() for cache in caches):
            fail("every --build-dir must contain CMakeCache.txt")
        vcpkg_roots = {Path(cache_value(cache, "Z_VCPKG_ROOT_DIR")) for cache in caches}
        if len(vcpkg_roots) != 1:
            fail("builds do not use one exact vcpkg checkout")
        vcpkg_root = vcpkg_roots.pop().resolve()
        source_candidates = sorted(
            (vcpkg_root / "buildtrees" / "ffmpeg" / "src").glob("*.clean")
        )
        if len(source_candidates) != 1:
            fail("expected exactly one patched FFmpeg *.clean source directory")
        ffmpeg_source = source_candidates[0]

        args.output.parent.mkdir(parents=True, exist_ok=True)
        manifest: dict[str, str] = {}
        with zipfile.ZipFile(
            args.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6
        ) as zf:
            tracked_docs = [
                "COPYING",
                "LICENSE",
                "LICENSES/GPL-2.0-or-later.txt",
                "LICENSES/LGPL-2.1-or-later.txt",
                "THIRD_PARTY_NOTICES.md",
                "vcpkg/ports/ffmpeg/portfile.cmake",
                "vcpkg/ports/ffmpeg/0001-android-ffmpeg.patch",
                "vcpkg/ports/ffmpeg/0001-fixed-mac.patch",
                "vcpkg/ports/ffmpeg/0001-operand-shr-error.patch",
                "vcpkg/triplets/arm64-android.cmake",
                "vcpkg/triplets/x64-android.cmake",
                "vcpkg-configuration.json",
                "vcpkg.json",
            ]
            for relative in tracked_docs:
                source = root / relative
                manifest[relative] = zip_file(zf, source, relative)

            for source in sorted(path for path in ffmpeg_source.rglob("*") if path.is_file()):
                relative = source.relative_to(ffmpeg_source).as_posix()
                name = f"ffmpeg-source/{relative}"
                manifest[name] = zip_file(zf, source, name)

            seen_abis: set[str] = set()
            for build, cache in zip(builds, caches):
                abi = cache_value(cache, "ANDROID_ABI")
                if abi not in {"arm64-v8a", "x86_64"} or abi in seen_abis:
                    fail(f"invalid or duplicate Android ABI: {abi}")
                seen_abis.add(abi)
                installed = Path(cache_value(cache, "VCPKG_INSTALLED_DIR")).resolve()
                output = find_link_output(build)
                inputs = direct_link_inputs(build, output)
                ffmpeg_inputs = [path for path in inputs if path.name in FFMPEG_LIBS]
                if {path.name for path in ffmpeg_inputs} != FFMPEG_LIBS:
                    fail(f"{abi} link does not contain the expected static FFmpeg libraries")
                materials = [path for path in inputs if path.name not in FFMPEG_LIBS]
                target = Path(output)
                if not target.is_absolute():
                    target = build / target
                materials.extend(
                    [
                        target.resolve(),
                        cache,
                        build / "build.ninja",
                        build / "CMakeFiles" / "rules.ninja",
                        installed / "vcpkg" / "status",
                        installed
                        / cache_value(cache, "VCPKG_TARGET_TRIPLET")
                        / "share"
                        / "ffmpeg"
                        / "copyright",
                    ]
                )
                for source in sorted(set(materials), key=lambda path: str(path).lower()):
                    if not source.is_file():
                        fail(f"missing relinking material: {source}")
                    name = archive_name(source, build, installed, abi)
                    manifest[name] = zip_file(zf, source, name)
                command_name = f"relink/{abi}/link-command.txt"
                manifest[command_name] = zip_bytes(
                    zf, link_command(build).encode("utf-8"), command_name
                )
                ffmpeg_list = "".join(
                    f"{path.name}  {hashlib.sha256(path.read_bytes()).hexdigest()}\n"
                    for path in sorted(ffmpeg_inputs)
                )
                list_name = f"relink/{abi}/ffmpeg-libraries-to-replace.txt"
                manifest[list_name] = zip_bytes(zf, ffmpeg_list.encode(), list_name)

            if seen_abis != {"arm64-v8a", "x86_64"}:
                fail("both arm64-v8a and x86_64 build directories are required")
            revision = subprocess.run(
                ["git", "rev-parse", "HEAD"],
                cwd=root,
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip()
            readme = f"""Android FFmpeg source and relinking materials
================================================

Repository revision: {revision}

The application links FFmpeg statically under LGPL-2.1-or-later. The
ffmpeg-source directory is the exact patched source tree used by both Android
builds. For each ABI, relink/ contains every direct object and non-FFmpeg
library input, the original linked libkrkr2.so, CMake/Ninja metadata, and the
exact original link command. Build replacement FFmpeg archives from the
included source/configuration, replace the six archives listed in
ffmpeg-libraries-to-replace.txt, adjust absolute tool/output prefixes in the
recorded command for your NDK installation, and run that command from the
corresponding build directory layout.

The archive manifest records SHA-256 for every payload. System NDK libraries
and tools are intentionally not redistributed; use NDK 28.0.13004108.
"""
            manifest["README.txt"] = zip_bytes(zf, readme.encode(), "README.txt")
            manifest_data = "".join(
                f"{digest}  {name}\n" for name, digest in sorted(manifest.items())
            ).encode()
            zip_bytes(zf, manifest_data, "SHA256SUMS")
        print(f"wrote {args.output.resolve()} ({args.output.stat().st_size} bytes)")
        return 0
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
