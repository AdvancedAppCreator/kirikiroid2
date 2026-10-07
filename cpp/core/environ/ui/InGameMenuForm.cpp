#include "InGameMenuForm.h"
#include "cocos2d.h"
#include "cocos2d/MainScene.h"
#include "base/CCEventListenerTouch.h"
#include "ui/UIButton.h"
#include "ui/UIListView.h"
#include "ui/UIText.h"
#include "MenuItemIntf.h"
#include "ui/UIHelper.h"
#include "tjsGlobalStringMap.h"
#include "csd/CsdUIFactory.h"

using namespace cocos2d;
using namespace cocos2d::ui;

TVPInGameMenuForm *TVPInGameMenuForm::create(const std::string &title,
                                             tTJSNI_MenuItem *item) {
    auto *ret = new TVPInGameMenuForm;
    ret->autorelease();
    auto *naviBar = Csd::createNaviBar();
    ret->initFromFile(naviBar, Csd::createListView(), nullptr);
    ret->_naviBar = naviBar;
    ret->addBackdrop();
    ret->initMenu(title, item);
    ret->rearrangeLayout();
    return ret;
}

void TVPInGameMenuForm::addBackdrop() {
    // The in-game menu is opened over the live game with no other UI form on
    // the stack, so pushUIForm adds no dimming layer. Add our own full-screen
    // dark backdrop so the menu is readable and modal; tapping the dark area
    // (outside the menu column) dismisses the menu.
    const cocos2d::Size uiSize = TVPMainScene::GetInstance()->getUINodeSize();
    _backdrop = cocos2d::LayerColor::create(cocos2d::Color4B(0, 0, 0, 235),
                                            uiSize.width, uiSize.height);
    _backdrop->setPosition(cocos2d::Vec2::ZERO);

    auto *listener = cocos2d::EventListenerTouchOneByOne::create();
    listener->setSwallowTouches(true);
    listener->onTouchBegan = [this](cocos2d::Touch *touch, cocos2d::Event *) {
        const auto point = convertToNodeSpace(touch->getLocation());
        const bool insideMenu =
            (_naviBar && _naviBar->getBoundingBox().containsPoint(point)) ||
            (RootNode && RootNode->getBoundingBox().containsPoint(point));
        if(!insideMenu)
            TVPMainScene::GetInstance()->popUIForm(this);
        return true;
    };
    _eventDispatcher->addEventListenerWithSceneGraphPriority(listener,
                                                             _backdrop);

    addChild(_backdrop, -1);
}

void TVPInGameMenuForm::rearrangeLayout() {
    // The menu is assembled from nodes authored at a fixed 720-wide portrait
    // size. Scale the whole form by the standard UI scale and lay the pieces
    // out in that (unscaled) space: full-screen backdrop, navbar across the
    // top, list filling the rest, horizontally centered.
    auto *scene = TVPMainScene::GetInstance();
    const float scale = scene->getUIScale();
    cocos2d::Size sceneSize = scene->getUINodeSize() / scale;
    setScale(scale);
    setContentSize(sceneSize);

    if(_backdrop) {
        _backdrop->setContentSize(sceneSize);
        _backdrop->setPosition(cocos2d::Vec2::ZERO);
    }

    const float menuWidth = 720.0f;
    const float navHeight = 120.0f;
    float left = (sceneSize.width - menuWidth) / 2.0f;
    if(left < 0.0f)
        left = 0.0f;

    if(_naviBar) {
        _naviBar->setAnchorPoint(cocos2d::Vec2::ZERO);
        _naviBar->setContentSize(cocos2d::Size(menuWidth, navHeight));
        _naviBar->setPosition(
            cocos2d::Vec2(left, sceneSize.height - navHeight));
        cocos2d::ui::Helper::doLayout(_naviBar);
    }

    if(RootNode) {
        RootNode->setAnchorPoint(cocos2d::Vec2::ZERO);
        RootNode->setContentSize(
            cocos2d::Size(menuWidth, sceneSize.height - navHeight));
        RootNode->setPosition(cocos2d::Vec2(left, 0));
        // The inner ListView is authored at a fixed 960 tall and anchored at
        // the bottom, which pushes the items off the bottom of the screen.
        // Resize it to fill the root so items are laid out from the top down.
        if(_list) {
            _list->setAnchorPoint(cocos2d::Vec2::ZERO);
            _list->setPosition(cocos2d::Vec2::ZERO);
            _list->setContentSize(RootNode->getContentSize());
        }
        cocos2d::ui::Helper::doLayout(RootNode);
    }
}

void TVPInGameMenuForm::bindHeaderController(const Node *allNodes) {
    _title = allNodes->getChildByName<Button *>("title");
    if(_title)
        _title->setEnabled(false);
}

void TVPInGameMenuForm::bindBodyController(const Node *allNodes) {
    _list = allNodes->getChildByName<ListView *>("list");
    if(NaviBar.Left) {
        NaviBar.Left->addClickEventListener([this](cocos2d::Ref *) {
            TVPMainScene::GetInstance()->popUIForm(this);
        });
    }
}

void TVPInGameMenuForm::initMenu(const std::string &title,
                                 tTJSNI_MenuItem *item) {
    _list->removeAllItems();
    if(_title) {
        if(title.empty()) {
            ttstr caption;
            item->GetCaption(caption);
            _title->setTitleText(caption.AsStdString());
        } else {
            _title->setTitleText(title);
        }
    }

    int count = item->GetChildren().size();
    int idx = 0;
    ttstr seperator = TJS::TJSMapGlobalStringMap(TJS_W("-"));
    for(int i = 0; i < count; ++i) {
        tTJSNI_MenuItem *subitem =
            static_cast<tTJSNI_MenuItem *>(item->GetChildren().at(i));
        ttstr caption;
        subitem->GetCaption(caption);
        if(caption.IsEmpty() || caption == TJS_W("+"))
            continue;
        _list->pushBackCustomItem(
            createMenuItem(idx, subitem, caption.AsStdString()));
        if(caption != seperator)
            ++idx;
    }
}

cocos2d::ui::Widget *
TVPInGameMenuForm::createMenuItem(int idx, tTJSNI_MenuItem *item,
                                  const std::string &caption) {
    iPreferenceItem *ret = nullptr;
    const cocos2d::Size &size = _list->getContentSize();
    if(!item->GetChildren().empty()) {
        ret = CreatePreferenceItem<tPreferenceItemSubDir>(idx, size, caption);
        ret->addClickEventListener([=](Ref *) {
            TVPMainScene::GetInstance()->pushUIForm(create(caption, item));
        });
    } else if(item->GetGroup() > 0 || item->GetRadio()) {
        auto getter = [=]() -> bool { return item->GetChecked(); };
        auto setter = [=](bool) {
            item->OnClick();
            TVPMainScene::GetInstance()->popAllUIForm();
        };
        ret = CreatePreferenceItem<tPreferenceItemCheckBox>(
            idx, size, caption, [=](tPreferenceItemCheckBox *item) {
                item->_getter = getter;
                item->_setter = setter;
            });
    } else if(item->GetChecked()) {
        auto getter = [=]() -> bool { return item->GetChecked(); };
        auto setter = [=](bool) { item->OnClick(); };
        ret = CreatePreferenceItem<tPreferenceItemCheckBox>(
            idx, size, caption, [=](tPreferenceItemCheckBox *item) {
                item->_getter = getter;
                item->_setter = setter;
            });
    } else if(caption == "-") {
        float w = size.width;
        Widget *sep =
            Csd::createSeperateItem(w, 2.0f, Color4F(0.6f, 0.6f, 0.6f, 1.0f));
        return sep;
    } else {
        ret = CreatePreferenceItem<tPreferenceItemConstant>(idx, size, caption);
        ret->addClickEventListener([=](Ref *) {
            TVPMainScene::GetInstance()->scheduleOnce(
                [c = TVPMainScene::GetInstance()](float) { c->popAllUIForm(); },
                0, "close_menu");
            item->OnClick();
        });
        ret->setTouchEnabled(true);
    }
    return ret;
}

void TVPShowPopMenu(tTJSNI_MenuItem *menu) {
    TVPMainScene::GetInstance()->pushUIForm(
        TVPInGameMenuForm::create(std::string(), menu));
}
