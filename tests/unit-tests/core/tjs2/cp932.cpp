#include <catch2/catch_test_macros.hpp>

#include "Cp932.h"

TEST_CASE("decode CP932 text") {
    const char text[] = {
        static_cast<char>(0x82), static_cast<char>(0xB1),
        static_cast<char>(0x82), static_cast<char>(0xF1),
        static_cast<char>(0x82), static_cast<char>(0xC9),
        static_cast<char>(0x82), static_cast<char>(0xBF),
        static_cast<char>(0x82), static_cast<char>(0xCD),
    };

    REQUIRE(TVPLooksLikeCp932(text, sizeof(text)));
    REQUIRE(TVPDecodeCp932(text, sizeof(text)) == u"こんにちは");
}

TEST_CASE("reject malformed CP932 detection") {
    const char malformed[] = { static_cast<char>(0x82), 0x20 };
    REQUIRE_FALSE(TVPLooksLikeCp932(malformed, sizeof(malformed)));
    REQUIRE(TVPDecodeCp932(malformed, sizeof(malformed)) ==
            std::u16string{ u'\uFFFD', u' ' });

    const char windows1252[] = { static_cast<char>(0xE9), 0x20 };
    REQUIRE_FALSE(TVPLooksLikeCp932(windows1252, sizeof(windows1252)));
}

TEST_CASE("decode CP932 single-byte extensions") {
    const char text[] = {
        static_cast<char>(0x80), static_cast<char>(0xA0),
        static_cast<char>(0xFD), static_cast<char>(0xFE),
        static_cast<char>(0xFF),
    };
    REQUIRE(TVPDecodeCp932(text, sizeof(text)) ==
            std::u16string{ u'\u0080', u'\uF8F0', u'\uF8F1', u'\uF8F2',
                            u'\uF8F3' });

    const char mixed[] = {
        static_cast<char>(0x82),
        static_cast<char>(0xA0),
        static_cast<char>(0xFD),
    };
    REQUIRE(TVPLooksLikeCp932(mixed, sizeof(mixed)));
}
