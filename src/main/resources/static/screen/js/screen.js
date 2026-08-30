/* 现场服务终端：数据大屏 + AI 问答 + 办事指南 + 进度查询。
   底部导航切换视图；数据大屏 15 秒自动刷新；1920×1080 等比缩放。 */
(function () {
    'use strict';

    var REFRESH_MS = 15000;
    var QR_CONTENT = '绵城码上办 | 微信扫码体验小程序';

    var app = document.getElementById('app');
    var charts = {};

    /* ---------- 1920×1080 等比缩放 ---------- */
    function fit() {
        var scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
        app.style.transform = 'scale(' + scale + ')';
        app.style.transformOrigin = 'left top';
        app.style.width = '1920px';
        app.style.height = '1080px';
        app.style.position = 'absolute';
        app.style.left = ((window.innerWidth - 1920 * scale) / 2) + 'px';
        app.style.top = ((window.innerHeight - 1080 * scale) / 2) + 'px';
    }

    function chart(id) {
        if (!charts[id]) {
            charts[id] = echarts.init(document.getElementById(id));
        }
        return charts[id];
    }

    window.addEventListener('resize', function () {
        fit();
        Object.keys(charts).forEach(function (key) { charts[key].resize(); });
    });

    /* ---------- 视图切换 ---------- */
    var TITLES = {
        dashboard: '绵城码上办 · 数据大屏',
        chat: '绵城码上办 · AI 问答',
        guide: '绵城码上办 · 办事指南',
        track: '绵城码上办 · 进度查询'
    };

    function switchView(name) {
        document.querySelectorAll('.view').forEach(function (view) {
            view.classList.toggle('is-active', view.id === 'view-' + name);
        });
        document.querySelectorAll('.nav-item').forEach(function (item) {
            item.classList.toggle('is-active', item.dataset.view === name);
        });
        document.getElementById('view-title').textContent = TITLES[name] || TITLES.dashboard;
        Object.keys(charts).forEach(function (key) { charts[key].resize(); });
        if (name === 'guide' && catalogue === null) loadCatalogue();
        if (name === 'dashboard') loadOverview();
    }

    Array.prototype.forEach.call(document.querySelectorAll('.nav-item'), function (item) {
        item.addEventListener('click', function () { switchView(item.dataset.view); });
    });

    /* ---------- 时钟 ---------- */
    var WEEK = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    function tickClock() {
        var now = new Date();
        document.getElementById('clock-date').textContent =
            now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
        document.getElementById('clock-week').textContent = WEEK[now.getDay()];
        document.getElementById('clock-time').textContent =
            pad(now.getHours()) + ':' + pad(now.getMinutes()) + ':' + pad(now.getSeconds());
    }

    /* ---------- 数字滚动 ---------- */
    function animateValue(el, target) {
        var start = parseFloat(el.dataset.value || '0') || 0;
        if (start === target) { el.textContent = target; return; }
        var begin = performance.now();
        var duration = 800;
        function step(now) {
            var p = Math.min(1, (now - begin) / duration);
            var eased = 1 - Math.pow(1 - p, 3);
            el.textContent = Math.round(start + (target - start) * eased);
            if (p < 1) requestAnimationFrame(step);
            else el.dataset.value = String(target);
        }
        requestAnimationFrame(step);
    }

    function renderStats(id, items) {
        var host = document.getElementById(id);
        var previous = {};
        Array.prototype.forEach.call(host.querySelectorAll('.stat-item'), function (child) {
            previous[child.querySelector('.stat-label').textContent] =
                Number(child.querySelector('.stat-value').dataset.value || '0') || 0;
        });
        host.innerHTML = items.map(function (item) {
            return '<div class="stat-item"><div class="stat-value ' + item.color +
                '" data-value="0">0</div><div class="stat-label">' + item.label + '</div></div>';
        }).join('');
        Array.prototype.forEach.call(host.querySelectorAll('.stat-item'), function (child) {
            var label = child.querySelector('.stat-label').textContent;
            var valueEl = child.querySelector('.stat-value');
            var target = 0;
            for (var i = 0; i < items.length; i++) {
                if (items[i].label === label) { target = Number(items[i].value) || 0; break; }
            }
            valueEl.dataset.value = String(previous[label] || 0);
            animateValue(valueEl, target);
        });
    }

    function renderKpis(items) {
        var host = document.getElementById('kpi-row');
        var previous = {};
        Array.prototype.forEach.call(host.querySelectorAll('.kpi'), function (child) {
            previous[child.querySelector('.kpi-label').textContent] =
                Number(child.querySelector('.kpi-value').dataset.value || '0') || 0;
        });
        host.innerHTML = items.map(function (item) {
            return '<div class="kpi"><div class="kpi-value ' + item.color +
                '" data-value="0">0</div><div class="kpi-label">' + item.label + '</div></div>';
        }).join('');
        Array.prototype.forEach.call(host.querySelectorAll('.kpi'), function (child) {
            var label = child.querySelector('.kpi-label').textContent;
            var valueEl = child.querySelector('.kpi-value');
            var target = 0;
            for (var i = 0; i < items.length; i++) {
                if (items[i].label === label) { target = Number(items[i].value) || 0; break; }
            }
            valueEl.dataset.value = String(previous[label] || 0);
            animateValue(valueEl, target);
        });
    }

    function renderTrend(trend) {
        chart('chart-trend').setOption({
            grid: { left: 42, right: 18, top: 34, bottom: 30 },
            legend: { data: ['提交申请', '办结工单'], textStyle: { color: '#8fa8cf', fontSize: 12 },
                itemWidth: 14, itemHeight: 8, top: 2, right: 8 },
            tooltip: { trigger: 'axis' },
            xAxis: { type: 'category', data: trend.map(function (p) { return p.date + '\n' + p.weekday; }),
                axisLine: { lineStyle: { color: 'rgba(86,144,235,0.4)' } }, axisTick: { show: false },
                axisLabel: { color: '#8fa8cf', fontSize: 11, lineHeight: 15, interval: 0 } },
            yAxis: { type: 'value', minInterval: 1,
                splitLine: { lineStyle: { color: 'rgba(56,97,175,0.22)' } },
                axisLabel: { color: '#8fa8cf', fontSize: 11 } },
            series: [
                { name: '提交申请', type: 'bar', barWidth: 14,
                  data: trend.map(function (p) { return p.submitted; }),
                  itemStyle: { borderRadius: [4, 4, 0, 0],
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: '#35d0ff' }, { offset: 1, color: 'rgba(53,208,255,0.15)' }]) } },
                { name: '办结工单', type: 'line', smooth: true, symbolSize: 6,
                  data: trend.map(function (p) { return p.completed; }),
                  lineStyle: { color: '#ffc257', width: 2 }, itemStyle: { color: '#ffc257' },
                  areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: 'rgba(255,194,87,0.25)' }, { offset: 1, color: 'rgba(255,194,87,0)' }]) } }
            ]
        });
    }

    function renderHeat(topServices) {
        var sorted = topServices.slice().reverse();
        chart('chart-heat').setOption({
            grid: { left: 150, right: 46, top: 12, bottom: 10 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            xAxis: { type: 'value', minInterval: 1, splitLine: { show: false },
                axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false } },
            yAxis: { type: 'category', data: sorted.map(function (i) { return i.itemName; }),
                axisLine: { show: false }, axisTick: { show: false },
                axisLabel: { color: '#cfe4ff', fontSize: 13 } },
            series: [{ type: 'bar', barWidth: 16,
                label: { show: true, position: 'right', color: '#35d0ff', fontWeight: 700 },
                data: sorted.map(function (i) { return i.count; }),
                itemStyle: { borderRadius: [0, 8, 8, 0],
                  color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                        { offset: 0, color: 'rgba(77,139,255,0.35)' }, { offset: 1, color: '#35d0ff' }]) } }]
        });
    }

    var STATUS_LABEL = { SUBMITTED: '待受理', PROCESSING: '处理中', COMPLETED: '已完成',
        REJECTED: '未受理', CANCELLED: '已撤销' };
    var STATUS_COLOR = { SUBMITTED: 'c-gold', PROCESSING: 'c-cyan', COMPLETED: 'c-green',
        REJECTED: 'c-red', CANCELLED: 'c-blue' };

    function renderStatus(dist) {
        document.getElementById('request-status').innerHTML = Object.keys(STATUS_LABEL).map(function (key) {
            return '<div class="badge"><div class="badge-count ' + (STATUS_COLOR[key] || '') + '">' +
                (dist[key] || 0) + '</div><div class="badge-label">' + STATUS_LABEL[key] + '</div></div>';
        }).join('');
    }

    function renderFeed(list) {
        var host = document.getElementById('recent-completed');
        if (!list || !list.length) {
            host.innerHTML = '<li><span class="feed-title" style="color:#8fa8cf">暂无办结记录</span></li>';
            return;
        }
        host.innerHTML = list.map(function (item, index) {
            return '<li><span class="idx">' + (index + 1) + '</span><span class="feed-title">' +
                escapeHtml(item.itemName || '校园服务') + '</span><span class="feed-time">' +
                (item.completedAt || '') + '</span></li>';
        }).join('');
    }

    function escapeHtml(text) {
        return String(text == null ? '' : text)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    /* ---------- 数据大屏 ---------- */
    function loadOverview() {
        fetch('/api/public/screen/overview')
            .then(function (res) { return res.json(); })
            .then(function (body) {
                if (!body || body.success !== true || !body.data) throw new Error(body && body.message || 'bad');
                var data = body.data;
                renderStats('user-stats', [
                    { value: data.users.total, label: '累计用户', color: 'c-cyan' },
                    { value: data.users.students, label: '在校学生', color: 'c-blue' },
                    { value: data.users.todayNew, label: '今日新增', color: 'c-gold' }
                ]);
                renderKpis([
                    { value: data.requests.total, label: '累计服务申请', color: 'c-cyan' },
                    { value: data.requests.today, label: '今日新增申请', color: 'c-gold' },
                    { value: data.orders.completed, label: '累计办结工单', color: 'c-green' },
                    { value: data.ai.answers, label: 'AI 助手答疑', color: 'c-cyan' }
                ]);
                renderStats('community-stats', [
                    { value: data.community.posts, label: '社区动态', color: 'c-cyan' },
                    { value: data.community.comments, label: '互动评论', color: 'c-blue' },
                    { value: data.community.messages, label: '私信互动', color: 'c-gold' }
                ]);
                renderStats('ai-stats', [
                    { value: data.ai.conversations, label: 'AI 会话', color: 'c-cyan' },
                    { value: data.ai.answers, label: 'AI 回答', color: 'c-blue' },
                    { value: data.ai.todayAnswers, label: '今日回答', color: 'c-gold' }
                ]);
                renderTrend(data.trend);
                renderHeat(data.topServices);
                renderStatus(data.requests.statusDist || {});
                renderFeed(data.recentCompleted);
                document.getElementById('refresh-info').textContent =
                    '更新于 ' + data.generatedAt + ' · 每 15 秒自动刷新';
            })
            .catch(function () {
                document.getElementById('refresh-info').textContent = '数据更新失败，自动重试中…';
            })
            .finally(function () {
                setTimeout(function () {
                    if (document.getElementById('view-dashboard').classList.contains('is-active')) loadOverview();
                }, REFRESH_MS);
            });
    }

    /* ---------- AI 问答 ---------- */
    var chatHistory = [[], [], []];
    var chatBusy = false;

    function addMessage(role, text) {
        var empty = document.getElementById('chat-empty');
        if (empty) empty.remove();
        var wrapper = document.createElement('div');
        wrapper.className = 'msg ' + (role === 'user' ? 'msg-user' : 'msg-ai');
        var bubble = document.createElement('div');
        bubble.className = 'bubble';
        bubble.textContent = text;
        wrapper.appendChild(bubble);
        var body = document.getElementById('chat-body');
        body.appendChild(wrapper);
        body.scrollTop = body.scrollHeight;
        return wrapper;
    }

    function sendChat(question) {
        if (chatBusy || !question || !question.trim()) return;
        var q = question.trim();
        addMessage('user', q);
        document.getElementById('chat-text').value = '';
        chatBusy = true;
        var sendBtn = document.getElementById('chat-send');
        sendBtn.disabled = true;

        var bodyMessage = addMessage('ai', '思考中…');
        bodyMessage.classList.add('msg-typing');

        fetch('/api/public/screen/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question: q, history: chatHistory })
        }).then(function (res) { return res.json(); })
          .then(function (body) {
                if (!body || body.success !== true || !body.data) throw new Error(body && body.message || 'bad');
                chatHistory.push({ role: 'user', content: q });
                chatHistory.push({ role: 'assistant', content: body.data.answer });
                if (chatHistory.length > 6) chatHistory = chatHistory.slice(-6);
                bodyMessage.classList.remove('msg-typing');
                bodyMessage.querySelector('.bubble').textContent = body.data.answer;
          })
          .catch(function () {
                bodyMessage.classList.remove('msg-typing');
                bodyMessage.querySelector('.bubble').textContent = '抱歉，我暂时无法回答，请稍后重试。';
          })
          .finally(function () {
                chatBusy = false;
                sendBtn.disabled = false;
          });
    }

    document.getElementById('chat-send').addEventListener('click', function () {
        sendChat(document.getElementById('chat-text').value);
    });
    document.getElementById('chat-text').addEventListener('keydown', function (event) {
        if (event.key === 'Enter') sendChat(document.getElementById('chat-text').value);
    });

    /* ---------- 语音转写（按住说话） ---------- */
    var talkBtn = document.getElementById('chat-talk');
    var mediaRecorder = null;
    var audioChunks = [];
    var recording = false;

    async function startRecording() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            addMessage('ai', '当前浏览器不支持录音，请使用文字输入。');
            return;
        }
        try {
            var stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            var mimeType = '';
            var candidates = ['audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus', 'audio/wav'];
            for (var i = 0; i < candidates.length; i++) {
                if (window.MediaRecorder && MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(candidates[i])) {
                    mimeType = candidates[i]; break;
                }
            }
            if (!window.MediaRecorder) throw new Error('unsupported');
            mediaRecorder = new MediaRecorder(stream, mimeType ? { mimeType: mimeType } : undefined);
            audioChunks = [];
            mediaRecorder.ondataavailable = function (event) {
                if (event.data && event.data.size > 0) audioChunks.push(event.data);
            };
            mediaRecorder.onstop = onRecordingStop;
            mediaRecorder.start();
            recording = true;
            talkBtn.classList.add('recording');
            talkBtn.textContent = '松开结束';
        } catch (error) {
            addMessage('ai', '无法获取麦克风权限，请使用文字输入。');
        }
    }

    function stopRecording() {
        if (mediaRecorder && recording) {
            mediaRecorder.stop();
            recording = false;
            talkBtn.classList.remove('recording');
            talkBtn.textContent = '按住说话';
            mediaRecorder.stream.getTracks().forEach(function (track) { track.stop(); });
        }
    }

    function onRecordingStop() {
        if (!audioChunks.length) return;
        var blobType = mediaRecorder.mimeType || 'audio/webm';
        var blob = new Blob(audioChunks, { type: blobType });
        var thinking = addMessage('ai', '正在识别语音…');
        thinking.classList.add('msg-typing');

        var form = new FormData();
        var ext = blobType.indexOf('webm') >= 0 ? 'webm' :
            (blobType.indexOf('mp4') >= 0 ? 'm4a' : 'weba');
        form.append('file', blob, 'talk.' + ext);

        fetch('/api/public/screen/chat/audio', { method: 'POST', body: form })
            .then(function (res) { return res.json(); })
            .then(function (body) {
                if (!body || body.success !== true || !body.data || !body.data.text) throw new Error('bad');
                var text = body.data.text;
                thinking.classList.remove('msg-typing');
                thinking.querySelector('.bubble').textContent = '「' + text + '」';
                sendChat(text);
            })
            .catch(function () {
                thinking.classList.remove('msg-typing');
                thinking.querySelector('.bubble').textContent = '语音识别失败，请靠近麦克风再试一次，或改用文字输入。';
            });
    }

    function talkHandler(event) {
        event.preventDefault();
        if (event.type === 'pointerdown' || event.type === 'touchstart' || event.type === 'mousedown') {
            startRecording();
        } else {
            stopRecording();
        }
    }

    talkBtn.addEventListener('pointerdown', talkHandler);
    talkBtn.addEventListener('pointerup', talkHandler);
    talkBtn.addEventListener('pointercancel', stopRecording);
    talkBtn.addEventListener('pointerleave', function () {
        if (recording) stopRecording();
    });
    talkBtn.addEventListener('touchstart', talkHandler, { passive: false });
    talkBtn.addEventListener('touchend', talkHandler);

    /* ---------- 办事指南 ---------- */
    var catalogue = null;

    function loadCatalogue() {
        fetch('/api/public/screen/catalog')
            .then(function (res) { return res.json(); })
            .then(function (body) {
                if (!body || body.success !== true || !body.data) throw new Error('bad');
                catalogue = body.data;
                renderCategories();
            })
            .catch(function () {
                document.getElementById('guide-items').innerHTML =
                    '<li class="item-item"><div class="item-name">指南加载失败，请重试</div></li>';
            });
    }

    function renderCategories() {
        var host = document.getElementById('guide-cats');
        host.innerHTML = catalogue.categories.map(function (cat, index) {
            return '<li class="cat-item' + (index === 0 ? ' is-active' : '') +
                '" data-id="' + cat.id + '">' + escapeHtml(cat.name) + '</li>';
        }).join('');
        Array.prototype.forEach.call(host.querySelectorAll('.cat-item'), function (item) {
            item.addEventListener('click', function () {
                Array.prototype.forEach.call(host.querySelectorAll('.cat-item'), function (other) {
                    other.classList.toggle('is-active', other === item);
                });
                renderItems(Number(item.dataset.id));
            });
        });
        if (catalogue.categories.length) {
            renderItems(catalogue.categories[0].id);
        }
    }

    function renderItems(categoryId) {
        var category = catalogue.categories.find(function (cat) { return cat.id === categoryId; });
        var host = document.getElementById('guide-items');
        document.getElementById('guide-items-title').textContent = (category ? category.name : '') + ' · 服务事项';
        host.innerHTML = (category ? category.items : []).map(function (item) {
            return '<li class="item-item" data-id="' + item.id + '">' +
                '<div class="item-name">' + escapeHtml(item.name) + '</div>' +
                '<div class="item-summary">' + escapeHtml(item.summary || '') + '</div></li>';
        }).join('');
        Array.prototype.forEach.call(host.querySelectorAll('.item-item'), function (it) {
            it.addEventListener('click', function () {
                Array.prototype.forEach.call(host.querySelectorAll('.item-item'), function (other) {
                    other.classList.toggle('is-active', other === it);
                });
                renderDetail(Number(it.dataset.id));
            });
        });
        if (category && category.items.length) {
            renderDetail(category.items[0].id);
        } else {
            document.getElementById('guide-detail').innerHTML =
                '<p class="guide-placeholder">该分类暂无服务事项</p>';
        }
    }

    function renderDetail(itemId) {
        var item = null;
        for (var i = 0; i < catalogue.categories.length && !item; i++) {
            item = catalogue.categories[i].items.find(function (it) { return it.id === itemId; }) || null;
        }
        var host = document.getElementById('guide-detail');
        if (!item) {
            host.innerHTML = '<p class="guide-placeholder">点击左侧事项查看办理说明</p>';
            return;
        }
        host.innerHTML =
            '<div class="guide-name">' + escapeHtml(item.name) + '</div>' +
            '<div class="guide-meta">' + (item.slaHours ? '承诺时限 ' + item.slaHours + ' 小时' : '校园服务事项') + '</div>' +
            '<div class="guide-desc">' + escapeHtml(item.description || item.summary || '暂无说明') + '</div>' +
            '<div class="guide-section"><div class="guide-label">办理提示</div>' +
            '<div class="guide-desc">' + (item.summary ? escapeHtml(item.summary) : '请扫码进入小程序在线提交申请。') + '</div></div>';
    }

    /* ---------- 进度查询 ---------- */
    document.getElementById('track-go').addEventListener('click', doTrack);
    document.getElementById('track-no').addEventListener('keydown', function (event) {
        if (event.key === 'Enter') doTrack();
    });

    function doTrack() {
        var no = document.getElementById('track-no').value.trim();
        if (!no) return;
        var host = document.getElementById('track-result');
        host.innerHTML = '<p class="track-empty">查询中…</p>';
        fetch('/api/public/screen/track/' + encodeURIComponent(no))
            .then(function (res) { return res.json(); })
            .then(function (body) {
                if (!body || body.success !== true || !body.data) throw new Error(body && body.message || 'bad');
                renderTrack(body.data);
            })
            .catch(function () {
                host.innerHTML = '<p class="track-empty">未查到该单据，请核对单号后重试</p>';
            });
    }

    function renderTrack(data) {
        var host = document.getElementById('track-result');
        var timeline = (data.timeline || []).map(function (point) {
            return '<li><div class="timeline-time">' + (point.time || '') + '</div>' +
                '<div class="timeline-action">' + escapeHtml(point.actionText || '') +
                '<span class="note">' + escapeHtml(point.note || '') + '</span></div></li>';
        }).join('');
        host.innerHTML =
            '<div class="track-card"><div class="track-head">' +
            '<span class="track-title">' + escapeHtml(data.itemName || '校园服务') + '</span>' +
            '<span class="track-status">' + escapeHtml(data.statusText || data.status || '') + '</span></div>' +
            (data.orderNo ? '<div class="timeline-action">工单号：' + escapeHtml(data.orderNo) + '</div>' : '') +
            (data.requestNo ? '<div class="timeline-action">申请单号：' + escapeHtml(data.requestNo) + '</div>' : '') +
            '</div>' +
            (timeline
                ? '<div class="track-card"><ul class="track-timeline">' + timeline + '</ul></div>'
                : '<p class="track-empty">暂无办理记录</p>');
    }

    /* ---------- 启动 ---------- */
    fit();
    tickClock();
    setInterval(tickClock, 1000);

    new QRCode(document.getElementById('qrcode'), {
        text: QR_CONTENT, width: 108, height: 108,
        colorDark: '#0b1830', colorLight: '#ffffff',
        correctLevel: QRCode.CorrectLevel.M
    });

    loadOverview();
})();