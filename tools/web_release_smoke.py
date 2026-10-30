#!/usr/bin/env python3
"""Browser regression for release metadata states, accessibility and admin loading.
Run against a local static server (API responses are intercepted explicitly).
"""
import json
import os
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

BASE = os.environ.get('WEB_SMOKE_URL', 'http://127.0.0.1:9126')
OUT = Path(os.environ.get('WEB_SMOKE_OUT', 'artifacts/remaining-optimization-20261025/web'))
OUT.mkdir(parents=True, exist_ok=True)
checks = []
def passed(name):
    checks.append(name)
    print('PASS: ' + name, flush=True)
def reply(route, data, status=200):
    route.fulfill(status=status, content_type='application/json', body=json.dumps({'success': status == 200, 'data': data, 'message': '测试接口暂不可用' if status != 200 else 'ok'}))
def release(context, mode='ready'):
    def handler(route):
        if mode == 'error': reply(route, None, 503)
        else: reply(route, {'latest': None if mode == 'empty' else {'versionName':'2.6.1','versionCode':261,'packageSize':20971520,'publishedAt':'2026-10-25T12:00:00','packageSha256':'abc123'*10+'abcd','downloadUrl':'/api/public/app-version/261/package','updateLog':'修复下载\n优化登录'}})
    context.route('**/api/public/app-version/latest?*', handler)

def ready_page(browser, **kwargs):
    context = browser.new_context(**kwargs)
    release(context)
    page = context.new_page(); page.goto(BASE + '/download/'); page.wait_for_load_state('networkidle')
    page.wait_for_function("document.documentElement.dataset.releaseState === 'ready'")
    return context, page

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context, page = ready_page(browser, viewport={'width':1440,'height':1000}, reduced_motion='reduce')
    errors = []; page.on('pageerror', lambda e: errors.append(str(e)))
    assert page.locator('#heroVersionBadge').inner_text() == 'v2.6.1'
    assert '/261/package' in page.locator('.js-download').first.get_attribute('href')
    assert page.locator('#packageSize').inner_text() == '20.0 MB'
    assert page.locator('#changelogList li').count() == 2
    passed('release success: version, package URL, size, notes share API metadata')
    assert page.locator('#qrBox').get_attribute('data-target') == BASE + '/download/#download'
    passed('QR opens landing page, not package')
    assert page.locator('#galleryDots button').count() == 5
    assert page.locator('#galleryDots button').first.bounding_box()['width'] >= 48
    assert page.locator('#galleryNext').bounding_box()['height'] >= 48
    page.locator('#galleryDots button').first.focus(); page.keyboard.press('End')
    assert page.locator('#galleryDots button').last.get_attribute('aria-current') == 'true'
    page.keyboard.press('Home')
    assert page.locator('#galleryDots button').first.get_attribute('aria-current') == 'true'
    passed('gallery 48px targets, End/Home keyboard and current state')
    shot = page.locator('.shot-frame').first; shot.click()
    assert page.locator('#screenshotDialog').evaluate('(el) => el.open')
    assert page.locator('#screenshotImage').get_attribute('alt').startswith('首页')
    page.keyboard.press('Escape')
    assert not page.locator('#screenshotDialog').evaluate('(el) => el.open')
    assert shot.evaluate('(el) => el === document.activeElement')
    passed('screenshot modal, Escape and focus restoration')
    assert page.locator('.hero-enter').first.evaluate('(el) => getComputedStyle(el).animationName') == 'none'
    passed('reduced motion disables animation')
    page.screenshot(path=str(OUT/'download-desktop.png'), full_page=True)
    assert not errors
    context.close()
    for mode, text in [('empty','暂无发布版本'),('error','版本信息暂不可用')]:
        context = browser.new_context(); release(context, mode); page = context.new_page()
        page.goto(BASE+'/download/'); page.wait_for_load_state('networkidle')
        assert page.locator('#heroVersionBadge').inner_text() == text
        assert page.locator('.js-download').first.get_attribute('aria-disabled') == 'true'
        assert page.locator('.js-download').first.get_attribute('href') == '#download'
        if mode == 'error':
            assert page.locator('#retryLatest').is_visible()
            context.unroute('**/api/public/app-version/latest?*'); release(context)
            page.locator('#retryLatest').click(); page.wait_for_function("document.documentElement.dataset.releaseState === 'ready'")
            assert '/261/package' in page.locator('.js-download').first.get_attribute('href')
        passed('release ' + mode + ': no stale APK, accurate state' + (' and retry recovers' if mode == 'error' else ''))
        context.close()
    for label, agent in [('WeChat','Mozilla/5.0 (Linux; Android 14) MicroMessenger/8.0'),('iOS','Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)')]:
        context, page = ready_page(browser, viewport={'width':390,'height':844}, user_agent=agent, is_mobile=True, has_touch=True, reduced_motion='reduce')
        assert page.locator('.js-download').first.get_attribute('aria-disabled') == 'false'
        assert page.locator('.js-download').first.get_attribute('href') == '#download'
        assert '说明' in page.locator('.hero-actions .js-download').inner_text()
        assert ('浏览器' if label=='WeChat' else 'iOS') in page.locator('#platformGuide').inner_text()
        page.locator('.hero-actions .js-download').click()
        assert page.locator('#downloadGuide').evaluate('(el) => el === document.activeElement')
        page.screenshot(path=str(OUT/('download-'+label.lower()+'.png')), full_page=True)
        passed(label + ' routes to device-specific instructions')
        context.close()
    context = browser.new_context(java_script_enabled=False, viewport={'width':390,'height':844})
    page=context.new_page(); page.goto(BASE+'/download/'); page.wait_for_load_state('networkidle')
    assert page.locator('.section-head.reveal').first.evaluate('(el) => getComputedStyle(el).opacity') == '1'
    assert '/latest/package' in page.locator('.js-download').first.get_attribute('href')
    assert page.locator('.shot-frame').first.get_attribute('href').endswith('.webp')
    passed('no JavaScript: sections readable, stable download and image links work')
    context.close()
    # No token: only login page is fetched.
    context=browser.new_context(); page=context.new_page(); page_scripts=[]
    page.on('request', lambda r: page_scripts.append(r.url) if '/js/pages/' in r.url else None)
    context.route('**/api/**', lambda r: reply(r, {'enabled':False}))
    page.goto(BASE+'/admin/#/login'); page.wait_for_load_state('networkidle'); page.wait_for_selector('.login-panel')
    assert [urlparse(u).path for u in page_scripts] == ['/admin/js/pages/login.js']
    assert all('?v=' in u for u in page_scripts)
    passed('admin anonymous startup loads one content-versioned login page')
    context.close()
    # Restore token, enforce allowed routes, exercise error panel and density.
    context=browser.new_context(viewport={'width':1440,'height':1000}); context.add_init_script("localStorage.setItem('studenthub_admin_token', 'mock-token')")
    api_calls=[]
    def admin_api(route):
        path=urlparse(route.request.url).path; api_calls.append((route.request.method,path))
        if path=='/api/common/auth/me': data={'id':1,'displayName':'测试管理','roles':['PLATFORM_ADMIN'],'permissions':['platform:all']}
        elif path=='/api/admin/context': data={'userId':1,'displayName':'测试管理','platformAdmin':True,'campusId':1,'campusName':'测试校区','campuses':[{'campusId':1,'campusName':'测试校区','status':'ENABLED'}]}
        elif path=='/api/admin/system/health': data={'components':[{'name':'MySQL','status':'UP'},{'name':'Redis','status':'UP'}]}
        elif path=='/api/admin/app-versions': data=[]
        elif path=='/api/admin/injected-failure': reply(route,None,503); return
        elif path.startswith('/api/admin/ai/'): data=[]
        else: data={}
        reply(route,data)
    context.route('**/api/**',admin_api)
    page=context.new_page(); page_scripts=[]; errors=[]
    page.on('request',lambda r:page_scripts.append(r.url) if '/js/pages/' in r.url else None)
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto(BASE+'/admin/#/app-versions'); page.wait_for_load_state('networkidle'); page.wait_for_selector('.content .pgw')
    assert [urlparse(u).path for u in page_scripts] == ['/admin/js/pages/versions.js']
    passed('admin authorized route loads only its page after context')
    page.locator('.density-toggle').click(); assert page.locator('html').get_attribute('data-density')=='compact'
    page.reload(); page.wait_for_load_state('networkidle')
    assert page.locator('html').get_attribute('data-density')=='compact'
    assert page.locator('.density-toggle').inner_text()=='紧凑'
    page.locator('.density-toggle').click(); assert page.locator('html').get_attribute('data-density')=='comfortable'
    passed('admin comfortable/compact density persists across reload')
    page.evaluate("window.AdminApi.post('/api/admin/injected-failure', {operation: 'test'}).catch(() => {})")
    page.wait_for_selector('.api-error-panel')
    assert '测试接口暂不可用' in page.locator('.api-error-panel').inner_text()
    assert 'POST /api/admin/injected-failure' in page.locator('.api-error-panel').inner_text()
    page.screenshot(path=str(OUT/'admin-error-density.png'),full_page=True)
    page.locator('.api-error-panel button').first.click(); page.wait_for_load_state('networkidle')
    assert len([call for call in api_calls if call==('POST','/api/admin/injected-failure')])==1
    assert page.locator('.api-error-panel').count()==0
    passed('shared API error panel reloads data without replaying failed POST')
    page.evaluate("location.hash='#/ai'"); page.wait_for_load_state('networkidle'); page.wait_for_selector('.ax-pane')
    assert any('/aiconfig.js?' in url for url in page_scripts) and any('/aitest.js?' in url for url in page_scripts)
    assert not errors, errors
    passed('AI lazy dependency loads, no uncaught page errors')
    context.close()
    # Unauthorized route must never load that page script.
    context=browser.new_context(); context.add_init_script("localStorage.setItem('studenthub_admin_token','limited-token')")
    def limited_api(route):
        path=urlparse(route.request.url).path
        if path=='/api/common/auth/me': reply(route,{'id':2,'displayName':'审核员','roles':[],'permissions':['community:moderate']})
        elif path=='/api/admin/context': reply(route,{'userId':2,'campusId':1,'campusName':'测试校区','campuses':[]})
        else: reply(route,{})
    context.route('**/api/**',limited_api); page=context.new_page(); requested=[]
    page.on('request',lambda r:requested.append(r.url) if '/js/pages/' in r.url else None)
    page.goto(BASE+'/admin/#/app-versions'); page.wait_for_load_state('networkidle')
    assert not any('/versions.js?' in u for u in requested)
    passed('unauthorized route does not fetch its page module')
    context.close(); browser.close()
(OUT/'summary.json').write_text(json.dumps({'passed':len(checks),'checks':checks},ensure_ascii=False,indent=2)+'\n')
print('WEB SMOKE PASS: '+str(len(checks))+' checks')
