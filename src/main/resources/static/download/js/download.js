/** 发布页：版本接口是元数据唯一来源；二维码始终打开落地页。 */
(function () {
    'use strict';
    var LATEST_API = '/api/public/app-version/latest?platform=ANDROID&channel=OFFICIAL';
    var state = { status: 'loading', downloadUrl: '' };
    var ua = navigator.userAgent;
    var wechat = /MicroMessenger/i.test(ua);
    var ios = /iPad|iPhone|iPod/i.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    var $ = function (id) { return document.getElementById(id); };
    var $$ = function (selector, root) { return Array.prototype.slice.call((root || document).querySelectorAll(selector)); };
    var reduced = function () { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; };
    function setText(id, text) { var el = $(id); if (el) el.textContent = text; }
    function formatSize(bytes) {
        var n = Number(bytes);
        return n > 0 ? (n / 1024 / 1024).toFixed(1) + ' MB' : '未提供';
    }
    function downloadUrl(value) {
        try { var url = new URL(value || '', location.origin); return value && /^https?:$/.test(url.protocol) ? url.href : ''; }
        catch (error) { return ''; }
    }
    function updateLinks() {
        var ready = state.status === 'ready';
        var enabled = ready && !wechat && !ios;
        $$('.js-download').forEach(function (link) {
            link.href = enabled ? state.downloadUrl : '#download';
            link.setAttribute('aria-disabled', ready ? 'false' : 'true');
            link.setAttribute('aria-busy', state.status === 'loading' ? 'true' : 'false');
            if (wechat || ios) {
                var label = link.querySelector('strong') || link.querySelector('span') || link;
                label.textContent = ios ? '查看 iOS 使用说明' : '查看微信下载说明';
                link.setAttribute('aria-describedby', 'platformGuide');
            }
        });
    }
    function setStatus(status, text) {
        state.status = status;
        document.documentElement.dataset.releaseState = status;
        setText('releaseStatus', text);
        $('retryLatest').hidden = status !== 'error';
        updateLinks();
    }
    function resetMetadata(label) {
        ['heroVersionBadge', 'heroMeta', 'mainMeta', 'dockMeta', 'storeSize', 'storeVersion',
         'packageSha', 'versionBadge', 'versionDate', 'versionCode', 'packageSize', 'publishedAt'].forEach(function (id) { setText(id, label); });
        $('packageSha').removeAttribute('title');
        $('changelogList').textContent = '';
        var li = document.createElement('li'); li.textContent = label; $('changelogList').appendChild(li);
    }
    function renderLatest(latest) {
        var url = downloadUrl(latest.downloadUrl);
        if (!url || !latest.versionName) throw new Error('发布信息缺少下载地址或版本号');
        state.downloadUrl = url;
        var version = 'v' + latest.versionName;
        var size = formatSize(latest.packageSize);
        var date = latest.publishedAt ? String(latest.publishedAt).slice(0, 10) : '未提供';
        setText('heroVersionBadge', version); setText('storeVersion', version); setText('versionBadge', version);
        setText('heroMeta', version + ' · ' + size + ' · Android'); setText('mainMeta', version + ' · ' + size);
        setText('dockMeta', 'Android · ' + version); setText('storeSize', size); setText('packageSize', size);
        setText('versionDate', date); setText('publishedAt', date); setText('versionCode', 'BUILD ' + latest.versionCode);
        var sha = String(latest.packageSha256 || '');
        setText('packageSha', sha ? sha.slice(0, 12).toUpperCase() + '…' : '未提供');
        if (sha) $('packageSha').title = sha;
        $('changelogList').textContent = '';
        var notes = String(latest.updateLog || '').split(/[；;\n]+/).map(function (line) { return line.trim(); }).filter(Boolean);
        (notes.length ? notes : ['此版本未填写更新说明。']).forEach(function (note) {
            var li = document.createElement('li'); li.textContent = note; $('changelogList').appendChild(li);
        });
        setStatus('ready', '当前发布版：' + version + '。下载按钮与上方版本信息对应。');
    }
    function renderQr() {
        var box = $('qrBox');
        var target = new URL('/download/#download', location.origin).href;
        box.dataset.target = target;
        if (typeof window.QRCode !== 'function') return;
        box.textContent = '';
        try { new window.QRCode(box, {text: target, width: 260, height: 260, colorDark: '#07101f', colorLight: '#ffffff', correctLevel: window.QRCode.CorrectLevel.M}); }
        catch (error) { box.textContent = '请在手机浏览器打开 ' + target; }
    }
    function loadLatest() {
        resetMetadata('正在获取…');
        setStatus('loading', '正在获取当前发布版本…');
        var controller = new AbortController();
        var timer = setTimeout(function () { controller.abort(); }, 10000);
        fetch(LATEST_API, {headers: {Accept: 'application/json'}, credentials: 'same-origin', cache: 'no-store', signal: controller.signal})
            .then(function (response) { if (!response.ok) throw new Error('HTTP ' + response.status); return response.json(); })
            .then(function (body) {
                if (!body || body.success !== true || !body.data) throw new Error('版本信息异常');
                if (body.data.latest) renderLatest(body.data.latest);
                else { resetMetadata('暂无发布版本'); setStatus('empty', '此渠道暂未发布 Android 安装包，请稍后再来。'); }
            })
            .catch(function () { resetMetadata('版本信息暂不可用'); setStatus('error', '版本信息获取失败，请检查网络后重新获取。'); })
            .finally(function () { clearTimeout(timer); });
    }
    function initDownload() {
        $('retryLatest').addEventListener('click', loadLatest);
        if (wechat) setText('platformGuide', '当前在微信内，请点击右上角「…」，选择「在浏览器打开」，再下载 Android 安装包。');
        else if (ios) setText('platformGuide', '当前设备为 iOS，暂未提供 iOS 安装包。可通过页面下方的「自助服务终端」体验网页版。');
        else setText('platformGuide', /Android/i.test(ua) ? '下载 Android 安装包后，按系统安装提示操作。兼容情况以系统提示为准。' : '电脑可直接保存 Android 安装包，或用 Android 手机扫码打开此页。iOS 暂未提供安装入口。');
        $$('.js-download').forEach(function (link) { link.addEventListener('click', function (event) {
            if (state.status !== 'ready' || wechat || ios) {
                event.preventDefault(); $('downloadGuide').scrollIntoView({behavior: reduced() ? 'auto' : 'smooth', block: 'center'}); $('downloadGuide').focus({preventScroll: true});
            }
        }); });
        renderQr();
    }
    function initHeader() {
        var header = $('siteHeader');
        var dock = $('mobileDock');
        if (!header) return;

        var updateHeader = function () {
            var offset = window.scrollY || document.documentElement.scrollTop || 0;
            header.classList.toggle('is-scrolled', offset > 8);
            if (dock) dock.classList.toggle('is-visible', offset > 560);
        };
        window.addEventListener('scroll', updateHeader, { passive: true });
        updateHeader();
    }

    function initMenu() {
        var header = $('siteHeader');
        var toggle = $('menuToggle');
        if (!header || !toggle) return;

        var setOpen = function (open) {
            header.classList.toggle('menu-open', open);
            document.body.classList.toggle('menu-open', open);
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            toggle.setAttribute('aria-label', open ? '关闭菜单' : '打开菜单');
        };

        toggle.addEventListener('click', function () {
            setOpen(!header.classList.contains('menu-open'));
        });

        $$('#navLinks a').forEach(function (link) {
            link.addEventListener('click', function () { setOpen(false); });
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && header.classList.contains('menu-open')) setOpen(false);
        });
    }

    function initReveal() {
        var elements = $$('.reveal');
        if (!elements.length) return;

        if (!('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            elements.forEach(function (el) { el.classList.add('is-visible'); });
            return;
        }

        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) return;
                entry.target.classList.add('is-visible');
                observer.unobserve(entry.target);
            });
        }, { rootMargin: '0px 0px -8% 0px', threshold: 0.08 });

        elements.forEach(function (el) { observer.observe(el); });
    }

    function initFaq() {
        // 同一时间只展开一个答案，阅读节奏更干净。
        var details = $$('.faq-list details');
        details.forEach(function (item) {
            item.addEventListener('toggle', function () {
                if (!item.open) return;
                details.forEach(function (other) {
                    if (other !== item) other.open = false;
                });
            });
        });
    }

    function initGallery() {
        var viewport = $('galleryViewport'), prev = $('galleryPrev'), next = $('galleryNext'), wrap = $('galleryDots');
        var shots = $$('.shot', viewport), selected = 0, source = null;
        var dialog = $('screenshotDialog');
        var dots = shots.map(function (shot, index) {
            shot.id = 'gallery-shot-' + index;
            var dot = document.createElement('button'); dot.type = 'button';
            dot.setAttribute('aria-label', '查看第 ' + (index + 1) + ' 张截图'); dot.setAttribute('aria-controls', shot.id);
            dot.addEventListener('click', function () { show(index); }); wrap.appendChild(dot);
            var link = shot.querySelector('a');
            link.addEventListener('click', function (event) {
                if (typeof dialog.showModal !== 'function') return;
                event.preventDefault(); source = link;
                var img = link.querySelector('img'); $('screenshotImage').src = img.src; $('screenshotImage').alt = img.alt;
                setText('screenshotCaption', img.alt); dialog.showModal();
            });
            return dot;
        });
        dialog.addEventListener('close', function () { if (source) source.focus({preventScroll: true}); });
        dialog.addEventListener('click', function (event) { if (event.target === dialog) dialog.close(); });
        function sync(index) {
            selected = index;
            dots.forEach(function (dot, i) { dot.classList.toggle('is-active', i === index); dot.setAttribute('aria-current', i === index ? 'true' : 'false'); });
            prev.disabled = selected === 0; next.disabled = selected === shots.length - 1;
        }
        function show(index, focus) {
            index = Math.max(0, Math.min(index, shots.length - 1));
            var shot = shots[index];
            // Use viewport-relative geometry; offsetLeft can use a different offset parent.
            var left = viewport.scrollLeft + shot.getBoundingClientRect().left - viewport.getBoundingClientRect().left - (viewport.clientWidth - shot.clientWidth) / 2;
            viewport.scrollTo({left: left, behavior: reduced() ? 'auto' : 'smooth'}); sync(index);
            if (focus) dots[index].focus();
        }
        function keyboard(event) {
            var index = selected;
            if (event.key === 'ArrowRight') index++;
            else if (event.key === 'ArrowLeft') index--;
            else if (event.key === 'Home') index = 0;
            else if (event.key === 'End') index = shots.length - 1;
            else return;
            event.preventDefault(); show(index, wrap.contains(event.target));
        }
        viewport.addEventListener('keydown', keyboard); wrap.addEventListener('keydown', keyboard);
        var ticking = false;
        viewport.addEventListener('scroll', function () {
            if (ticking) return; ticking = true;
            requestAnimationFrame(function () {
                var center = viewport.getBoundingClientRect().left + viewport.clientWidth / 2, best = 0, distance = Infinity;
                shots.forEach(function (shot, i) { var rect = shot.getBoundingClientRect(), d = Math.abs(rect.left + rect.width / 2 - center); if (d < distance) { distance = d; best = i; } });
                sync(best); ticking = false;
            });
        }, {passive: true});
        prev.addEventListener('click', function () { show(selected - 1); }); next.addEventListener('click', function () { show(selected + 1); });
        sync(0);
    }
    function init() { initHeader(); initMenu(); initReveal(); initFaq(); initGallery(); initDownload(); loadLatest(); }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
