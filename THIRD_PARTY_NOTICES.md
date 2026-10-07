# Third-party notices

This file records publication-sensitive bundled material. It supplements the
source headers and the inherited composite `LICENSE`; it is not a substitute
for those terms.

## Provenance-bearing assets

| Material | Evidence retained in this tree | License / action |
| --- | --- | --- |
| `ui/cocos-studio/NotoSansCJK-Regular.ttc` | SHA-256 `b76b0433203017ca80401b2ee0dd69350349871c4b19d504c34dbdd80541690a`; internal name table identifies Noto Sans CJK 2.004, copyright © 2014-2021 Adobe, and OFL 1.1 | `LICENSES/OFL-1.1.txt` is the verbatim Source Han Sans 2.004 notice and OFL text, including the Reserved Font Name `Source`. |
| `android-async-http-1.4.9.jar` | SHA-256 `78c0470307c0e3ea4476d3dcee861c74c137c56babc3234440c480d8afd82150`; package namespace and version correspond to LoopJ Android Asynchronous Http Client 1.4.9 | Apache-2.0; upstream: <https://github.com/android-async-http/android-async-http/tree/1.4.9>. |
| `httpclient-4.4.1.1.jar` | SHA-256 `bbb0c82a2d27016caec4e0034e2e1cc082a6cc8c9ce7f03a93d87dbd04ff480b`; `cz.msebera.android.httpclient` namespace | Apache-2.0; upstream: <https://github.com/smarek/httpclient-android/tree/4.4.1.1>. |
| `com.android.vending.expansion.zipfile.jar` | SHA-256 `841078e42ba6ab87d5decec28a59b8737d4bdaf1518c3a36c8fd7668390c2ca5`; Google APK Expansion Zip Library namespace | Apache-2.0 source: <https://android.googlesource.com/platform/tools/swt/+/refs/heads/mirror-goog-studio-main/sdkmanager/libs/zip/>. |
| Gradle wrapper JAR | SHA-256 `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046` | Gradle, Apache-2.0; distribution ZIP is checksum-pinned in wrapper properties. |

The inherited JARs contain no useful embedded license manifest; therefore the
source/version mapping and exact hashes above are material to redistribution.
If a binary differs from the listed hash, re-establish its provenance before
publishing it.

## Removed unverifiable material

- `oppoSDK.jar` and its direct Oiface calls were removed. The binary carried no
  locally verifiable redistribution grant. Vendor performance hints are now
  no-ops; core rendering and game execution are unchanged.
- `tests/test_files/emote/ezsave.pimg` and its asset-specific test were removed.
  Local history only identified it as an Emote/PSB test fixture and did not
  establish redistribution rights. PSB runtime support remains.
- Unused `cpp/core/utils/iconv/utf8.h` was removed, avoiding redistribution of
  a historical GNU Library GPL component that was not part of the build.

## Source and generated dependencies

KiriKiri/Kirikiri Z, libjpeg-turbo, zlib, libpng, FreeType, Apache-licensed
components, JPEG XR, Ogg/Vorbis/Theora and other inherited notices are
reproduced in `LICENSE`. The application is distributed as a
GPL-2.0-or-later combined work; GPL/LGPL-covered files and retained component
terms are identified in `COPYING`.
The vcpkg manifest resolves further libraries; distributors must collect the
installed `share/<port>/copyright` files and satisfy each dependency license.

### RAR extraction and the 7-Zip overlay

RAR extraction uses libarchive's permissively licensed implementation. The
direct RARLAB UnRAR dependency and overlay have been removed.

The pinned 7-Zip overlay is still used for 7z-format reading and writing. It
explicitly excludes `CPP/7zip/Compress/Rar*`, the RAR archive handlers, and
RAR-specific crypto sources from compilation and excludes their headers from
the installed package. Code governed by 7-Zip's additional unRAR restriction
is therefore not compiled, linked, or installed. The remaining selected 7-Zip
sources are LGPL-2.1-or-later, BSD-2-Clause, BSD-3-Clause, or public domain as
recorded in the installed 7-Zip copyright file.

### FFmpeg 3.3.9 overlay and link mode

The overlay pins FFmpeg `n3.3.9` (port revision 1 and the SHA-512 in
`vcpkg/ports/ffmpeg/portfile.cmake`). The existing Android configure logs
record these content-affecting options exactly (compiler, prefix, library-path,
and tool paths omitted because they are build-machine-specific):

```text
--enable-pic --disable-doc --enable-debug=3 --enable-runtime-cpudetect
--target-os=android --enable-jni --enable-mediacodec
--enable-avcodec --enable-avformat --enable-avfilter
--enable-swresample --enable-swscale --disable-ffmpeg --enable-small
--disable-ffplay --disable-ffprobe --disable-avdevice --disable-programs
--enable-cross-compile --extra-libs=-lm --pkg-config-flags=--static
--disable-asm
```

Release adds `--enable-optimizations --enable-stripping`; debug adds
`--disable-optimizations --disable-stripping`. The architecture is `arm64` or
`x86_64` respectively. No log enables GPL, version3, or nonfree components.
The port reads FFmpeg's configure summary and refuses to package an
unrecognized license outcome.

Existing Android arm64-v8a and x86_64 release/debug build logs both report
`License: LGPL version 2.1 or later`. Their custom triplets set
`VCPKG_LIBRARY_LINKAGE static`; installed FFmpeg libraries are `.a` archives,
and both generated `krkr2` target descriptions link those archives. Thus the
verified Android targets statically link LGPL-2.1-or-later FFmpeg into
`libkrkr2.so`. Android binary publication must provide the LGPL notice and
license plus the source and application object/relinking materials required by
LGPL 2.1 section 6.

The Windows preset selects `x64-windows-static-md`, so configuration requires
static FFmpeg linkage, but no Windows build output was present to independently
verify the final link. Linux and macOS presets do not pin a vcpkg triplet, so
their FFmpeg linkage follows the selected vcpkg triplet and cannot be claimed
from repository configuration alone. Before publishing any of those targets,
record the resolved triplet, inspect the final link output, and apply the
corresponding LGPL section 6 path.

### Release license artifact

After each release build, create one deterministic notice artifact from every
vcpkg installed tree used by that release:

```text
python scripts/collect-third-party-licenses.py --output dist/THIRD_PARTY_LICENSES.txt <vcpkg_installed> [<vcpkg_installed> ...]
```

The script derives the resolved package/triplet set from `vcpkg/status`,
includes every installed `share/<port>/copyright`, de-duplicates repeated
target trees, and exits nonzero if status, package file lists, or a copyright
required by an installed payload is missing. Bundle the generated file with the
corresponding binary; do not reuse one from a different resolved graph.

### Android source and relinking artifact

After the arm64-v8a and x86_64 native builds, create the exact patched FFmpeg
source and application relinking-material archive:

```text
python scripts/generate-android-compliance-bundle.py --output dist/android-ffmpeg-source-relink.zip --build-dir <arm64-build-dir> --build-dir <x86_64-build-dir>
```

The command requires a clean Git worktree and both ABIs built from one vcpkg
checkout. It verifies that all six expected static FFmpeg archives are final
link inputs, then bundles the patched FFmpeg source, applicable patches and
configuration, every direct application object/non-FFmpeg library input, the
original binary, exact link command, build metadata, notices, and a SHA-256
manifest. Publish this archive and the license artifact with every Android APK.
`--allow-dirty` exists only to validate prospective changes and must not be
used for a release.

## Items requiring release-owner review

These are not papered over by unsupported claims:

- The exact historical URL represented by the recorded `upstream/dev` merge
  cannot be recovered from local Git objects. The exact revision and this
  limitation are documented in the READMEs.
- For each binary target, the owner must archive the resolved dependency
  artifact, verify the final FFmpeg link mode, and provide all GPL complete
  corresponding source and LGPL source/object/relinking materials. Signed
  Android publication additionally requires an owner-controlled signing key.
