@echo off
if "%VCPKG_ROOT%"=="" (
  echo VCPKG_ROOT must point to a vcpkg checkout bootstrapped at the baseline in vcpkg-configuration.json.
  exit /b 1
)
cmake --preset="Windows Debug Config" -DENABLE_TESTS=OFF -DBUILD_TOOLS=OFF
if errorlevel 1 exit /b %errorlevel%
cmake --build --preset="Windows Debug Build"