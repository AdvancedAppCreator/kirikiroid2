#!/bin/bash

#!/usr/bin/env bash
set -euo pipefail
: "${VCPKG_ROOT:?VCPKG_ROOT must point to a bootstrapped vcpkg checkout}"
cmake --preset="Linux Debug Config" -DENABLE_TESTS=OFF -DBUILD_TOOLS=OFF
cmake --build --preset="Linux Debug Build"

if [ ! -f "/usr/lib/libfmod.so" ]; then
    echo "install libfmod.so."
    sudo cp $(find ./out/linux/debug/ -name libfmodL.so) /usr/lib/
    sudo cp $(find ./out/linux/debug/ -name libfmod.so) /usr/lib/
    sudo ln -s /usr/lib/libfmod.so /usr/lib/libfmod.so.6
fi
