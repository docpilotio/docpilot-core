@echo off
title DocPilot Version Tag Creator

echo ========================================
echo DocPilot Version Tag Creator
echo ========================================
echo.

echo Creating version tags...

git tag -a v0.1.0 840d26a -m "Stable DocPilot Core MVP"
git tag -a v0.2.0 a3e6eb5 -m "RFC-0005 Source Model"
git tag -a v0.3.0 3053f7a -m "RFC-0006 Kotlin Lexer"
git tag -a v0.4.0 0c1699b -m "RFC-0007 Kotlin Symbol Extractor"
git tag -a v0.5.0 733ee7f -m "RFC-0008 Project Source Index"

echo.
echo Current Tags
echo --------------------------
git tag

echo.
echo Pushing tags to GitHub...
git push origin --tags

echo.
echo ========================================
echo Completed.
echo ========================================
echo.

pause