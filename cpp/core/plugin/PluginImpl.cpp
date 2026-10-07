//---------------------------------------------------------------------------
/*
        TVP2 ( T Visual Presenter 2 )  A script authoring tool
        Copyright (C) 2000 W.Dee <dee@kikyou.info> and contributors

        See details of license at "license.txt"
*/
//---------------------------------------------------------------------------
// "Plugins" class implementation / Service for plug-ins
//---------------------------------------------------------------------------
#include <set>
#include <algorithm>
#include <array>
#include <functional>
#include <memory>

#include <spdlog/spdlog.h>

#include "tjsCommHead.h"

#include "XP3Archive.h"
#include "ScriptMgnIntf.h"
#include "StorageIntf.h"
#include "PluginImpl.h"

#include "StorageImpl.h"

#include "EventIntf.h"
#include "TransIntf.h"
#include "tjsArray.h"
#include "DebugIntf.h"

#include "tjs.h"
#include "tjsConfig.h"
#include "ncbind.hpp"

#ifdef TVP_SUPPORT_KPI
#include "kmp_pi.h"
#endif

#include "FilePathUtil.h"
#include "Application.h"
#include "SysInitImpl.h"

#ifdef _MSC_VER
#define strcasecmp _stricmp
#endif

//---------------------------------------------------------------------------
bool TVPLoadInternalPlugin(const ttstr &_name);

void TVPLoadPlugin(const ttstr &name) {
    auto pluginName = name;
    if(TVPLoadInternalPlugin(pluginName)) {
        spdlog::debug("Loading Plugin: {} Success", name.AsStdString());
    } else {
        spdlog::error("Loading Plugin: {} Failed", name.AsStdString());
    }
}

//---------------------------------------------------------------------------
bool TVPUnloadPlugin(const ttstr &name) {
    // unload plugin
    return true;
}
//---------------------------------------------------------------------------

//---------------------------------------------------------------------------
// plug-in autoload support
//---------------------------------------------------------------------------
struct tTVPFoundPlugin {
    std::string Path;
    std::string Name;

    bool operator<(const tTVPFoundPlugin &rhs) const { return Name < rhs.Name; }
};

static tjs_int TVPAutoLoadPluginCount = 0;

static void TVPSearchPluginsAt(std::vector<tTVPFoundPlugin> &list,
                               std::string folder) {
    TVPListDir(folder, [&](const std::string &filename, int mask) {
        if(mask & S_IFREG) {
            if(!strcasecmp(filename.c_str() + filename.length() - 4, ".tpm")) {
                tTVPFoundPlugin fp;
                fp.Path = folder;
                fp.Name = filename;
                list.emplace_back(fp);
            }
        }
    });
}

void TVPLoadInternalPlugins() {
    ncbAutoRegister::AllRegist();
    ncbAutoRegister::LoadModule(TJS_W("xp3filter.dll"));
}

static void TVPLegacyXP3DecFilter(tTVPXP3ExtractionFilterInfo *info,
                                  tTJSVariant *) {
    auto *buffer = static_cast<tjs_uint8 *>(info->Buffer);
    const tjs_uint8 key = static_cast<tjs_uint8>(info->FileHash);
    for(tjs_uint i = 0; i < info->BufferSize; ++i)
        buffer[i] ^= key;
}

static bool TVPIsSupportedLegacyXP3Dec(const ttstr &storageName) {
    static constexpr std::array<tjs_uint8, 16> SupportedDigest = {
        0xc9, 0xf4, 0xf9, 0x1f, 0x3a, 0x9b, 0x0e, 0x57,
        0x07, 0xb3, 0x8b, 0x3f, 0x45, 0x31, 0x0d, 0x5b,
    };

    std::unique_ptr<tTJSBinaryStream> stream;
    try {
        stream.reset(TVPCreateStream(storageName, TJS_BS_READ));
    } catch(...) {
        return false;
    }

    TVP_md5_state_t state;
    TVP_md5_init(&state);
    std::array<tjs_uint8, 8192> buffer;
    tjs_uint64 remaining = stream->GetSize();
    try {
        while(remaining > 0) {
            const auto count = static_cast<tjs_uint>(
                std::min<tjs_uint64>(remaining, buffer.size()));
            stream->ReadBuffer(buffer.data(), count);
            TVP_md5_append(&state, buffer.data(), static_cast<int>(count));
            remaining -= count;
        }
    } catch(...) {
        return false;
    }

    std::array<tjs_uint8, 16> digest;
    TVP_md5_finish(&state, digest.data());
    return digest == SupportedDigest;
}

bool TVPLoadInternalPlugin(const ttstr &_name) {
    /* 1. 拿到 ttstr 的原始缓冲区 */
    const tjs_char *src = _name.c_str();
    size_t len = _name.length();

    /* 2. 在 src 里找最后一个 '/' 或 '\\'，定位纯文件名起始 */
    const tjs_char *fileBegin = src;
    for(const tjs_char *p = src; *p; ++p) {
        if(*p == TJS_W('/') || *p == TJS_W('\\'))
            fileBegin = p + 1;
    }

    /* 3. 在 fileBegin 里找最后一个 '.' */
    const tjs_char *dot = nullptr;
    for(const tjs_char *p = fileBegin; *p; ++p) {
        if(*p == TJS_W('.'))
            dot = p; // 记录最后一个 '.'
    }

    /* 4. 检查后缀 .tpm（不区分大小写） */
    bool needReplace = false;
    if(dot && dot[1] && dot[2] && dot[3] && !dot[4]) // 长度正好 4：".tpm"
    {
        tjs_char low[5]; // 存放小写副本
        for(int i = 0; i < 4; ++i)
            low[i] = (tjs_char)towlower(dot[i]);
        low[4] = 0;

        if(TJS_strncmp(low, TJS_W(".tpm"), 4) == 0)
            needReplace = true;
    }

    if(!TJS_stricmp(fileBegin, TJS_W("xp3dec.tpm")) &&
       TVPIsSupportedLegacyXP3Dec(_name)) {
        TVPSetXP3ArchiveExtractionFilter(TVPLegacyXP3DecFilter);
        TVPAddImportantLog(
            TJS_W("(info) Enabled legacy xp3dec.tpm compatibility filter"));
        return true;
    }

    /* 5. 构造结果字符串 */
    if(needReplace) {
        /* 需要替换为 .dll，计算新长度 */
        size_t newLen = len - 3 + 3; // 去掉 "tpm" 加上 "dll"
        tjs_char *buf = new tjs_char[newLen + 1];

        /* 拷贝前缀（含 .） */
        TJS_strncpy(buf, src, dot - src + 1);
        buf[dot - src + 1] = 0;

        /* 追加 dll */
        TJS_strcat(buf, TJS_W("dll"));

        ttstr fixed(buf);
        delete[] buf;

        return ncbAutoRegister::LoadModule(TVPExtractStorageName(fixed));
    }
    return ncbAutoRegister::LoadModule(TVPExtractStorageName(_name));
}

void tvpLoadPlugins() {
    TVPLoadInternalPlugins();
    // This function searches plugins which have an extension of
    // ".tpm" in the default path:
    //    1. a folder which holds kirikiri executable
    //    2. "plugin" folder of it
    // Plugin load order is to be decided using its name;
    // aaa.tpm is to be loaded before aab.tpm (sorted by ASCII order)

    // search plugins from path: (exepath), (exepath)\system,
    // (exepath)\plugin
    std::vector<tTVPFoundPlugin> list;

    std::string exepath = ExtractFileDir(TVPNativeProjectDir.AsStdString());

    TVPSearchPluginsAt(list, exepath);
    TVPSearchPluginsAt(list, exepath + "/system");
    TVPSearchPluginsAt(list, exepath + "/plugin");

    // sort by filename
    std::sort(list.begin(), list.end());

    // load each plugin
    TVPAutoLoadPluginCount = (tjs_int)list.size();
    for(auto &i : list) {
        TVPAddImportantLog(ttstr(TJS_W("(info) Loading ")) +
                           ttstr(i.Name.c_str()));
        TVPLoadPlugin((i.Path + "/" + i.Name).c_str());
    }
}

//---------------------------------------------------------------------------
tjs_int TVPGetAutoLoadPluginCount() { return TVPAutoLoadPluginCount; }

//---------------------------------------------------------------------------
// some service functions for plugin
//---------------------------------------------------------------------------
#include <zlib.h>

int ZLIB_uncompress(unsigned char *dest, unsigned long *destlen,
                    const unsigned char *source, unsigned long sourcelen) {
    return uncompress(dest, destlen, source, sourcelen);
}

//---------------------------------------------------------------------------
int ZLIB_compress(unsigned char *dest, unsigned long *destlen,
                  const unsigned char *source, unsigned long sourcelen) {
    return compress(dest, destlen, source, sourcelen);
}

//---------------------------------------------------------------------------
int ZLIB_compress2(unsigned char *dest, unsigned long *destlen,
                   const unsigned char *source, unsigned long sourcelen,
                   int level) {
    return compress2(dest, destlen, source, sourcelen, level);
}
//---------------------------------------------------------------------------
#include "md5.h"

static char TVP_assert_md5_state_t_size[(sizeof(TVP_md5_state_t) >=
                                         sizeof(md5_state_t))];

// if this errors, sizeof(TVP_md5_state_t) is not equal to
// sizeof(md5_state_t). sizeof(TVP_md5_state_t) must be equal to
// sizeof(md5_state_t).
//---------------------------------------------------------------------------
void TVP_md5_init(TVP_md5_state_t *pms) { md5_init((md5_state_t *)pms); }

//---------------------------------------------------------------------------
void TVP_md5_append(TVP_md5_state_t *pms, const tjs_uint8 *data, int nbytes) {
    md5_append((md5_state_t *)pms, (const md5_byte_t *)data, nbytes);
}

//---------------------------------------------------------------------------
void TVP_md5_finish(TVP_md5_state_t *pms, tjs_uint8 *digest) {
    md5_finish((md5_state_t *)pms, digest);
}

//---------------------------------------------------------------------------
bool TVPRegisterGlobalObject(const tjs_char *name, iTJSDispatch2 *dsp) {
    // register given object to global object
    tTJSVariant val(dsp);
    iTJSDispatch2 *global = TVPGetScriptDispatch();
    tjs_error er;
    try {
        er = global->PropSet(TJS_MEMBERENSURE, name, nullptr, &val, global);
    } catch(...) {
        global->Release();
        return false;
    }
    global->Release();
    return TJS_SUCCEEDED(er);
}

//---------------------------------------------------------------------------
bool TVPRemoveGlobalObject(const tjs_char *name) {
    // remove registration of global object
    iTJSDispatch2 *global = TVPGetScriptDispatch();
    if(!global)
        return false;
    tjs_error er;
    try {
        er = global->DeleteMember(0, name, nullptr, global);
    } catch(...) {
        global->Release();
        return false;
    }
    global->Release();
    return TJS_SUCCEEDED(er);
}

//---------------------------------------------------------------------------
void TVPDoTryBlock(tTVPTryBlockFunction tryblock,
                   tTVPCatchBlockFunction catchblock,
                   tTVPFinallyBlockFunction finallyblock, void *data) {
    try {
        tryblock(data);
    } catch(const eTJS &e) {
        if(finallyblock)
            finallyblock(data);
        tTVPExceptionDesc desc;
        desc.type = TJS_W("eTJS");
        desc.message = e.getMessage();
        if(catchblock(data, desc))
            throw;
        return;
    } catch(...) {
        if(finallyblock)
            finallyblock(data);
        tTVPExceptionDesc desc;
        desc.type = TJS_W("unknown");
        if(catchblock(data, desc))
            throw;
        return;
    }
    if(finallyblock)
        finallyblock(data);
}
//---------------------------------------------------------------------------

//---------------------------------------------------------------------------
// TVPCreateNativeClass_Plugins
//---------------------------------------------------------------------------
tTJSNativeClass *TVPCreateNativeClass_Plugins() {
    auto *cls = new tTJSNC_Plugins();

    // setup some platform-specific members
    //---------------------------------------------------------------------------

    //-- methods

    //----------------------------------------------------------------------
    TJS_BEGIN_NATIVE_METHOD_DECL(/*func. name*/ link) {

        if(numparams < 1)
            return TJS_E_BADPARAMCOUNT;

        ttstr name = *param[0];

        TVPLoadPlugin(name);

        return TJS_S_OK;
    }
    TJS_END_NATIVE_STATIC_METHOD_DECL_OUTER(
        /*object to register*/ cls,
        /*func. name*/ link)
    //----------------------------------------------------------------------
    TJS_BEGIN_NATIVE_METHOD_DECL(/*func. name*/ unlink) {
        if(numparams < 1)
            return TJS_E_BADPARAMCOUNT;

        ttstr name = *param[0];

        bool res = TVPUnloadPlugin(name);

        if(result)
            *result = (tjs_int)res;

        return TJS_S_OK;
    }
    TJS_END_NATIVE_STATIC_METHOD_DECL_OUTER(
        /*object to register*/ cls,
        /*func. name*/ unlink)
    //----------------------------------------------------------------------
    TJS_BEGIN_NATIVE_METHOD_DECL(getList) {
        iTJSDispatch2 *array = TJSCreateArrayObject();
        try {
            tjs_int idx = 0;
            for(const ttstr &name : TVPRegisteredPlugins) {
                tTJSVariant val(name);
                array->PropSetByNum(TJS_MEMBERENSURE, idx++, &val, array);
            }
            if(result)
                *result = tTJSVariant(array, array);
        } catch(...) {
            array->Release();
            throw;
        }
        array->Release();
        return TJS_S_OK;
    }
    TJS_END_NATIVE_STATIC_METHOD_DECL_OUTER(cls, getList)
    //---------------------------------------------------------------------------

    //---------------------------------------------------------------------------
    return cls;
}
//---------------------------------------------------------------------------
