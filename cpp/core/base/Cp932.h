#pragma once

#include <cstddef>
#include <string>

// Decodes a CP932 (Windows Shift-JIS / MS932 / Windows-31J) byte buffer to
// UTF-16. Self-contained (bundled mapping table) so it works on platforms whose
// iconv/ICU backends lack a Shift-JIS codec (e.g. Android bionic iconv).
//
// Invalid byte sequences are replaced with U+FFFD.
std::u16string TVPDecodeCp932(const char *data, size_t size);

// Returns true when the buffer contains at least one valid CP932 multibyte
// sequence and no malformed CP932 lead-byte sequence.
bool TVPLooksLikeCp932(const char *data, size_t size);
