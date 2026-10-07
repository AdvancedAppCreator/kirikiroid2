#include "ncbind.hpp"
#include "StorageIntf.h"
#include "cocos2d.h"

#include <openssl/evp.h>
#include <openssl/pem.h>
#include <openssl/rsa.h>
#include <openssl/x509.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstring>
#include <mutex>
#include <memory>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#define NCB_MODULE_NAME TJS_W("sigcheck.dll")

namespace {

    constexpr tjs_int SignatureError = -2;

    struct StreamDeleter {
        void operator()(tTJSBinaryStream *stream) const { delete stream; }
    };

    using StreamPtr = std::unique_ptr<tTJSBinaryStream, StreamDeleter>;

    bool ReadStorage(const ttstr &name, std::vector<unsigned char> &data,
                     ttstr &error,
                     const std::atomic_bool *cancelled = nullptr) {
        StreamPtr stream(TVPCreateStream(name, TJS_BS_READ));
        if(!stream) {
            error = TJS_W("Cannot open file: ") + name;
            return false;
        }

        const tjs_uint64 size64 = stream->GetSize();
        if(size64 > static_cast<tjs_uint64>(SIZE_MAX)) {
            error = TJS_W("File is too large: ") + name;
            return false;
        }

        data.resize(static_cast<size_t>(size64));
        size_t offset = 0;
        while(offset < data.size()) {
            if(cancelled && cancelled->load()) {
                error = TJS_W("Cancelled");
                return false;
            }
            const auto chunk = static_cast<tjs_uint>(
                std::min<size_t>(data.size() - offset, 1024 * 1024));
            stream->ReadBuffer(data.data() + offset, chunk);
            offset += chunk;
        }
        return true;
    }

    size_t FindAlignedMark(const std::vector<unsigned char> &data,
                           const char *mark, size_t markSize) {
        for(size_t offset = 0; offset + markSize <= data.size(); offset += 16) {
            if(!std::memcmp(data.data() + offset, mark, markSize))
                return offset;
        }
        return std::string::npos;
    }

    bool DecodeBase64(std::string text, std::vector<unsigned char> &decoded) {
        text.erase(std::remove_if(
                       text.begin(), text.end(),
                       [](unsigned char ch) { return std::isspace(ch) != 0; }),
                   text.end());
        if(text.empty() || text.size() % 4 != 0)
            return false;

        decoded.resize(text.size() / 4 * 3);
        const int length = EVP_DecodeBlock(
            decoded.data(),
            reinterpret_cast<const unsigned char *>(text.data()),
            static_cast<int>(text.size()));
        if(length < 0)
            return false;

        size_t padding = 0;
        if(!text.empty() && text.back() == '=')
            ++padding;
        if(text.size() > 1 && text[text.size() - 2] == '=')
            ++padding;
        decoded.resize(static_cast<size_t>(length) - padding);
        return true;
    }

    using PublicKeyPtr = std::unique_ptr<EVP_PKEY, decltype(&EVP_PKEY_free)>;

    PublicKeyPtr ParsePublicKey(const std::string &publicKey) {
        static const std::string PublicKeyStart = "-----BEGIN PUBLIC KEY-----";
        static const std::string PublicKeyEnd = "-----END PUBLIC KEY-----";
        const size_t keyStart = publicKey.find(PublicKeyStart);
        const size_t keyEnd = publicKey.find(PublicKeyEnd);
        std::vector<unsigned char> keyData;
        if(keyStart == std::string::npos || keyEnd == std::string::npos ||
           keyEnd <= keyStart ||
           !DecodeBase64(
               publicKey.substr(keyStart + PublicKeyStart.size(),
                                keyEnd - keyStart - PublicKeyStart.size()),
               keyData))
            return PublicKeyPtr(nullptr, EVP_PKEY_free);

        const unsigned char *cursor = keyData.data();
        PublicKeyPtr key(
            d2i_PUBKEY(nullptr, &cursor, static_cast<long>(keyData.size())),
            EVP_PKEY_free);
        if(key && cursor == keyData.data() + keyData.size())
            return key;

        // Some legacy sigcheck users label a PKCS#1 RSAPublicKey blob as
        // "PUBLIC KEY". Accept that historical format after trying standard
        // SPKI.
        cursor = keyData.data();
        std::unique_ptr<RSA, decltype(&RSA_free)> rsa(
            d2i_RSAPublicKey(nullptr, &cursor,
                             static_cast<long>(keyData.size())),
            RSA_free);
        if(!rsa || cursor != keyData.data() + keyData.size())
            return PublicKeyPtr(nullptr, EVP_PKEY_free);

        key.reset(EVP_PKEY_new());
        if(!key || EVP_PKEY_assign_RSA(key.get(), rsa.release()) != 1)
            key.reset();
        return key;
    }

    bool ReadSignature(const ttstr &filename,
                       const std::vector<unsigned char> &fileData,
                       size_t embeddedOffset,
                       std::vector<unsigned char> &signature, ttstr &error,
                       const std::atomic_bool *cancelled) {
        std::vector<unsigned char> signatureData;
        if(embeddedOffset != std::string::npos) {
            if(embeddedOffset >= fileData.size()) {
                error = TJS_W("Invalid embedded signature offset");
                return false;
            }
            signatureData.assign(fileData.begin() + embeddedOffset,
                                 fileData.end());
        } else if(!ReadStorage(filename + TJS_W(".sig"), signatureData, error,
                               cancelled)) {
            return false;
        }

        const auto nul =
            std::find(signatureData.begin(), signatureData.end(), '\0');
        std::string text(signatureData.begin(), nul);
        static const std::string Marker = "-- SIGNATURE - SHA256/PSS/RSA --";
        if(text.compare(0, Marker.size(), Marker) != 0) {
            error = TJS_W("Invalid signature file format");
            return false;
        }
        if(!DecodeBase64(text.substr(Marker.size()), signature)) {
            error = TJS_W("Invalid base64 signature");
            return false;
        }
        return true;
    }

    bool VerifySignature(const ttstr &filename, const std::string &publicKey,
                         ttstr &error, const std::atomic_bool *cancelled) {
        std::vector<unsigned char> fileData;
        if(!ReadStorage(filename, fileData, error, cancelled))
            return false;

        static const char EmbedMark[] = "XOPT_EMBED_AREA_";
        static const char ReleaseMark[] = "XRELEASE_SIG____";
        static const unsigned char XP3Mark[] = {
            'X', 'P', '3', 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b, 0x67, 0x01,
        };

        const size_t embedOffset =
            FindAlignedMark(fileData, EmbedMark, sizeof(EmbedMark) - 1);
        const size_t releaseOffset =
            FindAlignedMark(fileData, ReleaseMark, sizeof(ReleaseMark) - 1);
        const size_t xp3Offset = FindAlignedMark(
            fileData, reinterpret_cast<const char *>(XP3Mark), sizeof(XP3Mark));
        const bool executable = embedOffset != std::string::npos &&
            releaseOffset != std::string::npos;

        std::vector<unsigned char> signature;
        const size_t signatureOffset =
            executable ? releaseOffset + 16 + 4 : std::string::npos;
        if(!ReadSignature(filename, fileData, signatureOffset, signature,
                          error, cancelled))
            return false;

        unsigned char digest[EVP_MAX_MD_SIZE];
        unsigned int digestSize = 0;
        std::unique_ptr<EVP_MD_CTX, decltype(&EVP_MD_CTX_free)> digestContext(
            EVP_MD_CTX_new(), EVP_MD_CTX_free);
        if(!digestContext ||
           EVP_DigestInit_ex(digestContext.get(), EVP_sha256(), nullptr) != 1) {
            error = TJS_W("Cannot initialize SHA-256");
            return false;
        }

        if(executable) {
            if(cancelled && cancelled->load()) {
                error = TJS_W("Cancelled");
                return false;
            }
            if(EVP_DigestUpdate(digestContext.get(), fileData.data(),
                                embedOffset) != 1) {
                error = TJS_W("Cannot hash executable");
                return false;
            }
            const size_t suffixOffset =
                xp3Offset == std::string::npos ? fileData.size() : xp3Offset;
            if(suffixOffset < fileData.size() &&
               EVP_DigestUpdate(digestContext.get(),
                                fileData.data() + suffixOffset,
                                fileData.size() - suffixOffset) != 1) {
                error = TJS_W("Cannot hash executable suffix");
                return false;
            }
        } else {
            for(size_t offset = 0; offset < fileData.size();) {
                if(cancelled && cancelled->load()) {
                    error = TJS_W("Cancelled");
                    return false;
                }
                const size_t chunk =
                    std::min<size_t>(fileData.size() - offset, 1024 * 1024);
                if(EVP_DigestUpdate(digestContext.get(),
                                    fileData.data() + offset, chunk) != 1) {
                    error = TJS_W("Cannot hash file");
                    return false;
                }
                offset += chunk;
            }
        }

        if(EVP_DigestFinal_ex(digestContext.get(), digest, &digestSize) != 1) {
            error = TJS_W("Cannot finish SHA-256");
            return false;
        }

        auto key = ParsePublicKey(publicKey);
        if(!key) {
            error = TJS_W("Invalid public key");
            return false;
        }

        std::unique_ptr<EVP_PKEY_CTX, decltype(&EVP_PKEY_CTX_free)>
            verifyContext(EVP_PKEY_CTX_new(key.get(), nullptr),
                          EVP_PKEY_CTX_free);
        if(!verifyContext || EVP_PKEY_verify_init(verifyContext.get()) <= 0 ||
           EVP_PKEY_CTX_set_rsa_padding(verifyContext.get(),
                                        RSA_PKCS1_PSS_PADDING) <= 0 ||
           EVP_PKEY_CTX_set_signature_md(verifyContext.get(), EVP_sha256()) <=
               0 ||
           EVP_PKEY_CTX_set_rsa_pss_saltlen(verifyContext.get(), 32) <= 0) {
            error = TJS_W("Cannot initialize RSA-PSS verification");
            return false;
        }

        const int result =
            EVP_PKEY_verify(verifyContext.get(), signature.data(),
                            signature.size(), digest, digestSize);
        if(result == 0)
            error = TJS_W("Signature verification failed");
        else if(result < 0)
            error = TJS_W("RSA-PSS verification error");
        return result == 1;
    }

    void InvokeIfPresent(iTJSDispatch2 *owner, const tjs_char *name,
                         tjs_int count, tTJSVariant **params) {
        if(!owner)
            return;
        try {
            owner->FuncCall(0, name, nullptr, nullptr, count, params, owner);
        } catch(...) {
        }
    }

    struct SignatureOperation {
        SignatureOperation(tjs_int handler, iTJSDispatch2 *owner,
                           const tTJSVariant &callbackInfo) :
            Handler(handler), Owner(owner), Info(callbackInfo) {
            if(Owner)
                Owner->AddRef();
        }

        ~SignatureOperation() {
            if(Owner)
                Owner->Release();
        }

        tjs_int Handler;
        iTJSDispatch2 *Owner;
        tTJSVariant Info;
        std::atomic_bool Cancelled{ false };
    };

    std::atomic<tjs_int> LastHandler{ 0 };
    std::mutex OperationsMutex;
    std::unordered_map<tjs_int, std::shared_ptr<SignatureOperation>> Operations;

    void
    PostSignatureResult(const std::shared_ptr<SignatureOperation> &operation,
                        tjs_int result, const ttstr &message) {
        cocos2d::Director::getInstance()
            ->getScheduler()
            ->performFunctionInCocosThread([operation, result, message] {
                const tjs_int finalResult =
                    operation->Cancelled.load() ? -1 : result;
                const ttstr finalMessage =
                    finalResult == -1 ? TJS_W("Cancelled") : message;

                tTJSVariant handlerValue(operation->Handler);
                tTJSVariant percent(100);
                tTJSVariant *progress[] = { &handlerValue, &operation->Info,
                                            &percent };
                InvokeIfPresent(operation->Owner,
                                TJS_W("onCheckSignatureProgress"), 3, progress);

                tTJSVariant resultValue(finalResult);
                tTJSVariant messageValue(finalMessage);
                tTJSVariant *done[] = { &handlerValue, &operation->Info,
                                        &resultValue, &messageValue };
                InvokeIfPresent(operation->Owner, TJS_W("onCheckSignatureDone"),
                                4, done);

                std::lock_guard<std::mutex> lock(OperationsMutex);
                Operations.erase(operation->Handler);
            });
    }

    bool CancelSignatureOperation(tjs_int handler) {
        std::lock_guard<std::mutex> lock(OperationsMutex);
        const auto found = Operations.find(handler);
        if(found == Operations.end())
            return false;
        found->second->Cancelled.store(true);
        return true;
    }

    class WindowSigCheck {
    public:
        void SetOwner(iTJSDispatch2 *owner) { Owner = owner; }

        tjs_int checkSignature(const tjs_char *filename,
                               const tjs_char *publicKey,
                               tTJSVariant info = tTJSVariant()) {
            const tjs_int handler = ++LastHandler;
            auto operation =
                std::make_shared<SignatureOperation>(handler, Owner, info);
            {
                std::lock_guard<std::mutex> lock(OperationsMutex);
                Operations.emplace(handler, operation);
            }

            const ttstr filenameCopy(filename ? filename : TJS_W(""));
            const std::string publicKeyCopy =
                publicKey ? ttstr(publicKey).AsStdString() : std::string();
            std::thread([operation, filenameCopy, publicKeyCopy] {
                ttstr error;
                tjs_int result = SignatureError;
                try {
                    if(filenameCopy.IsEmpty() || publicKeyCopy.empty()) {
                        error = TJS_W("Filename and public key are required");
                    } else if(operation->Cancelled.load()) {
                        result = -1;
                        error = TJS_W("Cancelled");
                    } else if(VerifySignature(filenameCopy, publicKeyCopy, error,
                                              &operation->Cancelled)) {
                        result = 1;
                    } else if(error == TJS_W("Signature verification failed")) {
                        result = 0;
                    }
                } catch(...) {
                    error = TJS_W("Unable to read or verify signature");
                }
                PostSignatureResult(operation, result, error);
            }).detach();
            return handler;
        }

        bool cancelCheckSignature(tjs_int handler) {
            return CancelSignatureOperation(handler);
        }
        bool stopCheckSignature(tjs_int handler) {
            return CancelSignatureOperation(handler);
        }

    private:
        iTJSDispatch2 *Owner = nullptr;
    };

} // namespace

NCB_GET_INSTANCE_HOOK(WindowSigCheck){
    NCB_INSTANCE_GETTER(objthis){ ClassT *instance = GetNativeInstance(objthis);
if(!instance) {
    instance = new ClassT();
    instance->SetOwner(objthis);
    SetNativeInstance(objthis, instance);
}
return instance;
}
}
;

NCB_ATTACH_CLASS_WITH_HOOK(WindowSigCheck, Window) {
    NCB_METHOD(checkSignature);
    NCB_METHOD(cancelCheckSignature);
    NCB_METHOD(stopCheckSignature);
}
