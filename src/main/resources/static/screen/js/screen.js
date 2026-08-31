/* 校园自助服务终端：待机吸引屏 → 主控台（功能+要闻同屏）→ 单任务流。
   枢纽式导航（无底部 Tab）；无人操作自动退回待机屏；进度查询配屏上键盘；
   1920×1080 等比缩放适配现场触摸屏。 */
(function () {
    'use strict';

    var REFRESH_MS = 15000;
    var HOME_IDLE_MS = 60000;   // 主控台 60 秒无操作回待机屏
    var TASK_IDLE_MS = 90000;   // 任务流 90 秒无操作回主控台
    var QR_CONTENT = '绵城码上办 | 微信扫码体验小程序';

    var app = document.getElementById('app');
    var charts = {};

    /* ==================== 1920×1080 等比缩放 ==================== */
    function fit() {
        var scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
        app.style.transform = 'scale(' + scale + ')';
        app.style.transformOrigin = 'left top';
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

    /* ==================== 工具 ==================== */
    function $(id) { return document.getElementById(id); }

    function escapeHtml(text) {
        return String(text == null ? '' : text)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function fetchData(url, options) {
        return fetch(url, options)
            .then(function (res) { return res.json(); })
            .then(function (body) {
                if (!body || body.success !== true || !body.data) {
                    throw new Error((body && body.message) || 'bad');
                }
                return body.data;
            });
    }

    var toastTimer = null;
    function toast(text) {
        var el = $('toast');
        el.textContent = text;
        el.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { el.hidden = true; }, 2600);
    }

    /* ==================== 终端状态机：attract / home / chat / guide / track / data ==================== */
    var state = 'attract';
    var idleDeadline = 0;

    function isTask(name) { return name === 'chat' || name === 'guide' || name === 'track' || name === 'data'; }

    function enterAttract() {
        state = 'attract';
        $('attract').classList.add('is-active');
        $('idle-chip').hidden = true;
        closeDrawer();
        stopRecording();
    }

    function switchView(name) {
        state = name;
        $('attract').classList.remove('is-active');
        document.querySelectorAll('.view').forEach(function (view) {
            view.classList.toggle('is-active', view.id === 'view-' + name);
        });
        resetIdle();
        if (name === 'home') {
            loadHomePulse();
        } else if (name === 'guide' && catalogue === null) {
            loadCatalogue();
        } else if (name === 'data') {
            loadOverview();
            requestAnimationFrame(function () {
                Object.keys(charts).forEach(function (key) { charts[key].resize(); });
            });
        }
    }

    function resetIdle() {
        idleDeadline = Date.now() + (state === 'home' ? HOME_IDLE_MS : TASK_IDLE_MS);
    }

    // 全局触摸监听：待机屏任意触摸进入主控台，其他情况重置空闲计时
    document.addEventListener('pointerdown', function () {
        if (state === 'attract') {
            switchView('home');
        } else {
            resetIdle();
        }
    }, true);

    // 空闲巡检：剩余 15 秒起在顶栏给出倒计时提示
    setInterval(function () {
        if (state === 'attract') return;
        var remain = idleDeadline - Date.now();
        var chip = $('idle-chip');
        if (remain <= 0) {
            chip.hidden = true;
            if (state === 'home') {
                enterAttract();
            } else {
                switchView('home');
                toast('长时间未操作，已自动返回首页');
            }
            return;
        }
        if (remain <= 15000) {
            chip.hidden = false;
            chip.textContent = Math.ceil(remain / 1000) + ' 秒后' + (state === 'home' ? '进入待机' : '返回首页');
        } else {
            chip.hidden = true;
        }
    }, 1000);

    document.querySelectorAll('[data-goto]').forEach(function (el) {
        el.addEventListener('click', function () { switchView(el.dataset.goto); });
    });

    /* ==================== 时钟与问候 ==================== */
    var WEEK = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    function tickClock() {
        var now = new Date();
        var date = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
        var time = pad(now.getHours()) + ':' + pad(now.getMinutes()) + ':' + pad(now.getSeconds());
        $('clock-date').textContent = date;
        $('clock-week').textContent = WEEK[now.getDay()];
        $('clock-time').textContent = time;
        $('attract-clock').textContent = time;
        $('attract-date').textContent = date + ' ・ ' + WEEK[now.getDay()];
    }

    function greeting() {
        var h = new Date().getHours();
        if (h < 6) return '夜深了，注意休息';
        if (h < 11) return '早上好呀同学';
        if (h < 13) return '中午好呀同学';
        if (h < 18) return '下午好呀同学';
        return '晚上好呀同学';
    }

    /* ==================== 数字滚动 ==================== */
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

    /* ==================== 主控台：实时预览 + 走马灯 ==================== */
    var SUGGESTIONS = ['请假怎么办理？', '宿舍报修在哪里申请？', '在读证明去哪开？', '活动场地怎么预约？', '创意集市摊位怎么申请？'];
    var suggestIndex = 0;
    setInterval(function () {
        suggestIndex = (suggestIndex + 1) % SUGGESTIONS.length;
        $('preview-chat').textContent = '「' + SUGGESTIONS[suggestIndex] + '」';
    }, 3500);

    var homePulseAt = 0;
    function loadHomePulse() {
        if (Date.now() - homePulseAt < REFRESH_MS) return;
        homePulseAt = Date.now();
        fetchData('/api/public/screen/overview')
            .then(function (data) {
                $('preview-data').textContent = '今日申请 ' + data.requests.today +
                    ' ・ 今日办结 ' + data.orders.completedToday + ' ・ AI 今日答疑 ' + data.ai.todayAnswers;
                renderHomeTicker(data.recentCompleted || []);
            })
            .catch(function () { /* 主控台静默，下轮再试 */ });
    }

    // 横向走马灯需要内容两份，translateX(-50%) 才能无缝循环
    function fillTicker(el, html) {
        el.innerHTML = html + html;
    }

    function renderHomeTicker(list) {
        var el = $('home-ticker');
        if (!list.length) {
            el.style.animation = 'none';
            el.innerHTML = '<span class="ticker-item">暂无最新办结记录</span>';
            return;
        }
        el.style.animation = '';
        fillTicker(el, list.map(function (item) {
            return '<span class="ticker-item"><b>已办结</b>' + escapeHtml(item.itemName || '校园服务') +
                '　' + escapeHtml(item.completedAt || '') + '</span>';
        }).join(''));
    }

    /* ==================== 信息要闻 ==================== */
    var notices = [];
    var newsFilter = '';

    function loadNotices() {
        fetchData('/api/public/screen/notices')
            .then(function (data) {
                notices = data.notices || [];
                renderNews();
                renderAttractTicker();
            })
            .catch(function () {
                $('news-list').innerHTML = '<li class="news-empty">要闻加载失败，稍后自动重试</li>';
            });
    }

    function renderAttractTicker() {
        var el = $('attract-ticker');
        if (!notices.length) {
            el.style.animation = 'none';
            el.innerHTML = '<span class="ticker-item">欢迎来到绵城码上办校园自助服务终端</span>';
            return;
        }
        fillTicker(el, notices.map(function (n) {
            return '<span class="ticker-item"><b>【' + escapeHtml(n.typeText) + '】</b>' +
                escapeHtml(n.title) + '</span>';
        }).join(''));
    }

    function renderNews() {
        var host = $('news-list');
        var list = newsFilter ? notices.filter(function (n) { return n.type === newsFilter; }) : notices;
        if (!list.length) {
            host.classList.remove('auto');
            host.innerHTML = '<li class="news-empty">暂无该类要闻</li>';
            return;
        }
        var html = list.map(function (n) {
            return '<li class="news-item"><div class="news-item-top">' +
                '<span class="news-tag news-tag-' + escapeHtml(n.type) + '">' + escapeHtml(n.typeText) + '</span>' +
                '<span class="news-date">' + escapeHtml((n.publishDate || '').slice(5)) + '</span></div>' +
                '<div class="news-title">' + escapeHtml(n.title) + '</div>' +
                (n.summary ? '<div class="news-summary">' + escapeHtml(n.summary) + '</div>' : '') +
                '</li>';
        }).join('');
        host.classList.remove('auto');
        host.innerHTML = html;
        // 内容超出可视区才开启纵向滚动：复制一份首尾相接
        requestAnimationFrame(function () {
            var clip = $('news-scroll');
            if (host.scrollHeight > clip.clientHeight + 30) {
                host.innerHTML = html + html;
                host.classList.add('auto');
            }
        });
    }

    document.querySelectorAll('.news-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            document.querySelectorAll('.news-tab').forEach(function (other) {
                other.classList.toggle('is-active', other === tab);
            });
            newsFilter = tab.dataset.type;
            renderNews();
        });
    });

    /* ==================== 数据一览 ==================== */
    function renderStats(id, items) {
        var host = $(id);
        var previous = {};
        Array.prototype.forEach.call(host.querySelectorAll('.stat'), function (child) {
            previous[child.querySelector('.label').textContent] =
                Number(child.querySelector('.num').dataset.value || '0') || 0;
        });
        host.innerHTML = items.map(function (item) {
            return '<div class="stat"><div class="num ' + item.color +
                '" data-value="0">0</div><div class="label">' + item.label + '</div></div>';
        }).join('');
        Array.prototype.forEach.call(host.querySelectorAll('.stat'), function (child) {
            var label = child.querySelector('.label').textContent;
            var valueEl = child.querySelector('.num');
            var target = 0;
            for (var i = 0; i < items.length; i++) {
                if (items[i].label === label) { target = Number(items[i].value) || 0; break; }
            }
            valueEl.dataset.value = String(previous[label] || 0);
            animateValue(valueEl, target);
        });
    }

    function renderKpis(items) {
        var host = $('kpi-row');
        host.innerHTML = items.map(function (item, index) {
            return '<div class="kpi' + (index ? ' k' + (index + 1) : '') + '"><div class="num">' +
                item.value + '</div><div class="label">' + item.label + '</div></div>';
        }).join('');
    }

    function renderTrend(trend) {
        chart('chart-trend').setOption({
            grid: { left: 42, right: 18, top: 34, bottom: 34 },
            legend: { data: ['提交申请', '办结工单'], textStyle: { color: '#64748B', fontSize: 13 },
                itemWidth: 14, itemHeight: 8, top: 0, right: 8 },
            tooltip: { trigger: 'axis' },
            xAxis: { type: 'category', data: trend.map(function (p) { return p.date + '\n' + p.weekday; }),
                axisLine: { lineStyle: { color: '#CBD5E1' } }, axisTick: { show: false },
                axisLabel: { color: '#64748B', fontSize: 12, lineHeight: 16, interval: 0 } },
            yAxis: { type: 'value', minInterval: 1,
                splitLine: { lineStyle: { color: '#EDF2F7' } },
                axisLabel: { color: '#64748B', fontSize: 12 } },
            series: [
                { name: '提交申请', type: 'bar', barWidth: 16,
                  data: trend.map(function (p) { return p.submitted; }),
                  itemStyle: { borderRadius: [4, 4, 0, 0],
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: '#2563EB' }, { offset: 1, color: 'rgba(37,99,235,0.35)' }]) } },
                { name: '办结工单', type: 'line', smooth: true, symbolSize: 7,
                  data: trend.map(function (p) { return p.completed; }),
                  lineStyle: { color: '#D97706', width: 2.5 }, itemStyle: { color: '#D97706' },
                  areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: 'rgba(217,119,6,0.2)' }, { offset: 1, color: 'rgba(217,119,6,0)' }]) } }
            ]
        });
    }

    function renderHeat(topServices) {
        var sorted = topServices.slice().reverse();
        chart('chart-heat').setOption({
            grid: { left: 150, right: 50, top: 12, bottom: 10 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            xAxis: { type: 'value', minInterval: 1, splitLine: { show: false },
                axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false } },
            yAxis: { type: 'category', data: sorted.map(function (i) { return i.itemName; }),
                axisLine: { show: false }, axisTick: { show: false },
                axisLabel: { color: '#0F172A', fontSize: 14 } },
            series: [{ type: 'bar', barWidth: 18,
                label: { show: true, position: 'right', color: '#2563EB', fontWeight: 700, fontSize: 14 },
                data: sorted.map(function (i) { return i.count; }),
                itemStyle: { borderRadius: [0, 9, 9, 0],
                  color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                        { offset: 0, color: 'rgba(37,99,235,0.3)' }, { offset: 1, color: '#14B8A6' }]) } }]
        });
    }

    var STATUS_LABEL = { SUBMITTED: '待受理', PROCESSING: '处理中', COMPLETED: '已完成',
        REJECTED: '未受理', CANCELLED: '已撤销' };

    function renderStatus(dist) {
        $('request-status').innerHTML = Object.keys(STATUS_LABEL).map(function (key) {
            return '<span class="status-badge">' + STATUS_LABEL[key] + '<b>' + (dist[key] || 0) + '</b></span>';
        }).join('');
    }

    function renderFeed(list) {
        var host = $('recent-completed');
        if (!list || !list.length) {
            host.innerHTML = '<li><span class="fname" style="color:#64748B">暂无办结记录</span></li>';
            return;
        }
        host.innerHTML = list.map(function (item) {
            return '<li><span class="dot"></span><span class="fname">' +
                escapeHtml(item.itemName || '校园服务') + '</span><span class="ftime">' +
                escapeHtml(item.completedAt || '') + '</span></li>';
        }).join('');
    }

    function loadOverview() {
        fetchData('/api/public/screen/overview')
            .then(function (data) {
                renderStats('user-stats', [
                    { value: data.users.total, label: '累计用户', color: '' },
                    { value: data.users.students, label: '在校学生', color: 'teal' },
                    { value: data.users.todayNew, label: '今日新增', color: 'orange' }
                ]);
                renderKpis([
                    { value: data.requests.total, label: '累计服务申请' },
                    { value: data.requests.today, label: '今日新增申请' },
                    { value: data.orders.completed, label: '累计办结工单' },
                    { value: data.ai.answers, label: 'AI 助手答疑' }
                ]);
                renderStats('community-stats', [
                    { value: data.community.posts, label: '社区动态', color: '' },
                    { value: data.community.comments, label: '互动评论', color: 'teal' },
                    { value: data.community.messages, label: '私信互动', color: 'orange' }
                ]);
                renderStats('ai-stats', [
                    { value: data.ai.conversations, label: 'AI 会话', color: '' },
                    { value: data.ai.answers, label: 'AI 回答', color: 'teal' },
                    { value: data.ai.todayAnswers, label: '今日回答', color: 'purple' }
                ]);
                renderTrend(data.trend);
                renderHeat(data.topServices);
                renderStatus(data.requests.statusDist || {});
                renderFeed(data.recentCompleted);
            })
            .catch(function (e) { console.warn('overview 加载失败', e); /* 数据页静默重试 */ })
            .finally(function () {
                setTimeout(function () {
                    if (state === 'data') loadOverview();
                }, REFRESH_MS);
            });
    }

    /* ==================== AI 问答 ==================== */
    var chatHistory = [];
    var chatBusy = false;

    function addMessage(role, text) {
        var empty = $('chat-empty');
        if (empty) empty.remove();
        var wrapper = document.createElement('div');
        wrapper.className = 'msg ' + (role === 'user' ? 'msg-user' : 'msg-ai');
        var bubble = document.createElement('div');
        bubble.className = 'msg-bubble';
        bubble.textContent = text;
        wrapper.appendChild(bubble);
        var body = $('chat-body');
        body.appendChild(wrapper);
        body.scrollTop = body.scrollHeight;
        return wrapper;
    }

    function addTyping(text) {
        var wrapper = addMessage('ai', text || '');
        wrapper.classList.add('msg-typing');
        wrapper.querySelector('.msg-bubble').innerHTML = '<i></i><i></i><i></i>';
        return wrapper;
    }

    function sendChat(question) {
        if (chatBusy || !question || !question.trim()) return;
        var q = question.trim();
        addMessage('user', q);
        $('chat-text').value = '';
        chatBusy = true;
        $('chat-send').disabled = true;

        var pending = addTyping();

        fetchData('/api/public/screen/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question: q, history: chatHistory })
        }).then(function (data) {
                chatHistory.push({ role: 'user', content: q });
                chatHistory.push({ role: 'assistant', content: data.answer });
                if (chatHistory.length > 6) chatHistory = chatHistory.slice(-6);
                pending.classList.remove('msg-typing');
                pending.querySelector('.msg-bubble').textContent = data.answer;
          })
          .catch(function () {
                pending.classList.remove('msg-typing');
                pending.querySelector('.msg-bubble').textContent = '抱歉，我暂时无法回答，请稍后重试。';
          })
          .finally(function () {
                chatBusy = false;
                $('chat-send').disabled = false;
                $('chat-body').scrollTop = $('chat-body').scrollHeight;
          });
    }

    $('chat-send').addEventListener('click', function () { sendChat($('chat-text').value); });
    $('chat-text').addEventListener('keydown', function (event) {
        if (event.key === 'Enter') sendChat($('chat-text').value);
    });
    document.querySelectorAll('.suggest-chip').forEach(function (chip) {
        chip.addEventListener('click', function () {
            switchView('chat');
            sendChat(chip.dataset.q);
        });
    });

    /* ==================== 语音转写（按住说话） ==================== */
    var talkBtn = $('chat-talk');
    var mediaRecorder = null;
    var audioChunks = [];
    var recording = false;

    function talkLabel(text) {
        talkBtn.querySelector('span').textContent = text;
    }

    async function startRecording() {
        if (recording) return;
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            addMessage('ai', '当前设备不支持录音，请使用文字输入。');
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
            talkBtn.classList.add('is-recording');
            talkLabel('松开结束');
        } catch (error) {
            addMessage('ai', '无法获取麦克风权限，请使用文字输入。');
        }
    }

    function stopRecording() {
        if (mediaRecorder && recording) {
            mediaRecorder.stop();
            recording = false;
            talkBtn.classList.remove('is-recording');
            talkLabel('按住说话');
            mediaRecorder.stream.getTracks().forEach(function (track) { track.stop(); });
        }
    }

    function onRecordingStop() {
        if (!audioChunks.length) return;
        var blobType = mediaRecorder.mimeType || 'audio/webm';
        var blob = new Blob(audioChunks, { type: blobType });
        var pending = addTyping();

        var form = new FormData();
        var ext = blobType.indexOf('webm') >= 0 ? 'webm' :
            (blobType.indexOf('mp4') >= 0 ? 'm4a' : 'weba');
        form.append('file', blob, 'talk.' + ext);

        fetchData('/api/public/screen/chat/audio', { method: 'POST', body: form })
            .then(function (data) {
                var text = data.text;
                pending.classList.remove('msg-typing');
                pending.querySelector('.msg-bubble').textContent = '「' + text + '」';
                sendChat(text);
            })
            .catch(function () {
                pending.classList.remove('msg-typing');
                pending.querySelector('.msg-bubble').textContent = '语音识别失败，请靠近麦克风再试一次，或改用文字输入。';
            });
    }

    talkBtn.addEventListener('pointerdown', function (event) { event.preventDefault(); startRecording(); });
    talkBtn.addEventListener('pointerup', stopRecording);
    talkBtn.addEventListener('pointercancel', stopRecording);
    talkBtn.addEventListener('pointerleave', function () { if (recording) stopRecording(); });

    /* ==================== 办事指南 ==================== */
    var catalogue = null;

    function loadCatalogue() {
        fetchData('/api/public/screen/catalog')
            .then(function (data) {
                catalogue = data;
                renderCategories();
                renderGuidePreview();
            })
            .catch(function () {
                $('guide-items').innerHTML =
                    '<li class="guide-empty">指南加载失败，请返回重试</li>';
            });
    }

    function renderGuidePreview() {
        var names = [];
        var total = 0;
        catalogue.categories.forEach(function (cat) {
            cat.items.forEach(function (item) {
                total++;
                if (names.length < 3) names.push(item.name);
            });
        });
        $('preview-guide').textContent = names.join(' ・ ') + '　共 ' + total + ' 项服务';
    }

    function renderCategories() {
        var host = $('guide-cats');
        host.innerHTML = catalogue.categories.map(function (cat, index) {
            return '<li class="guide-cat' + (index === 0 ? ' is-active' : '') + '" data-i="' + index + '">' +
                '<span>' + escapeHtml(cat.name) + '</span>' +
                '<span class="cat-count">' + cat.items.length + '</span></li>';
        }).join('');
        Array.prototype.forEach.call(host.querySelectorAll('.guide-cat'), function (el) {
            el.addEventListener('click', function () {
                Array.prototype.forEach.call(host.querySelectorAll('.guide-cat'), function (other) {
                    other.classList.toggle('is-active', other === el);
                });
                renderItems(Number(el.dataset.i));
            });
        });
        if (catalogue.categories.length) renderItems(0);
    }

    function renderItems(catIndex) {
        var category = catalogue.categories[catIndex];
        var host = $('guide-items');
        if (!category || !category.items.length) {
            host.innerHTML = '<li class="guide-empty">该分类暂无服务事项</li>';
            return;
        }
        host.innerHTML = category.items.map(function (item) {
            return '<li class="guide-item" data-id="' + item.id + '">' +
                '<div class="guide-item-name">' + escapeHtml(item.name) +
                (item.slaHours ? '<span class="guide-item-sla">承诺 ' + item.slaHours + 'h</span>' : '') +
                '</div>' +
                '<div class="guide-item-summary">' + escapeHtml(item.summary || item.description || '') + '</div>' +
                '</li>';
        }).join('');
        Array.prototype.forEach.call(host.querySelectorAll('.guide-item'), function (el) {
            el.addEventListener('click', function () { openDrawer(el.dataset.id); });
        });
    }

    // 后端雪花 ID 以字符串下发（防 JS 精度丢失），比较时统一按字符串
    function findItem(itemId) {
        for (var i = 0; i < catalogue.categories.length; i++) {
            var cat = catalogue.categories[i];
            var item = cat.items.find(function (it) { return String(it.id) === String(itemId); });
            if (item) return { item: item, category: cat };
        }
        return null;
    }

    function openDrawer(itemId) {
        var found = findItem(itemId);
        if (!found) return;
        var item = found.item;
        $('guide-drawer').innerHTML =
            '<button class="drawer-close" id="drawer-close">' +
            '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>' +
            '返回列表</button>' +
            '<h3 class="drawer-title">' + escapeHtml(item.name) + '</h3>' +
            '<div class="drawer-meta">' +
            '<span class="drawer-tag">' + escapeHtml(found.category.name) + '</span>' +
            (item.slaHours ? '<span class="drawer-tag warn">承诺时限 ' + item.slaHours + ' 小时</span>' : '') +
            '</div>' +
            '<div class="drawer-sec"><h4>办理说明</h4><p>' + escapeHtml(item.description || item.summary || '暂无说明') + '</p></div>' +
            (item.summary && item.description
                ? '<div class="drawer-sec"><h4>服务简介</h4><p>' + escapeHtml(item.summary) + '</p></div>' : '') +
            '<div class="drawer-qr">' +
            '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><line x1="14" y1="14" x2="21" y2="14"/><line x1="14" y1="21" x2="21" y2="21"/><line x1="21" y1="14" x2="21" y2="21"/></svg>' +
            '<p>本终端仅供查询。微信扫码进入「绵城码上办」小程序，搜索<b>「' + escapeHtml(item.name) +
            '」</b>即可在线提交申请，并可回到本终端凭单号查询办理进度。</p></div>';
        $('guide-mask').hidden = false;
        $('drawer-close').addEventListener('click', closeDrawer);
    }

    function closeDrawer() {
        $('guide-mask').hidden = true;
    }

    $('guide-mask').addEventListener('click', function (event) {
        if (event.target === $('guide-mask')) closeDrawer();
    });

    /* ==================== 进度查询（屏上键盘） ==================== */
    var trackPrefix = 'SR-';
    var trackDigits = '';
    var TRACK_MAX = 32;

    function renderTrackDisplay() {
        $('track-display').textContent = trackPrefix + trackDigits;
    }

    document.querySelectorAll('.kbd-chip').forEach(function (chip) {
        chip.addEventListener('click', function () {
            document.querySelectorAll('.kbd-chip').forEach(function (other) {
                other.classList.toggle('is-active', other === chip);
            });
            trackPrefix = chip.dataset.prefix;
            renderTrackDisplay();
        });
    });

    $('kbd-grid').addEventListener('click', function (event) {
        var key = event.target.closest('.kbd-key');
        if (!key) return;
        var value = key.dataset.key;
        if (value === 'back') {
            trackDigits = trackDigits.slice(0, -1);
        } else if (trackDigits.length < TRACK_MAX) {
            trackDigits += value;
        }
        renderTrackDisplay();
    });

    $('track-clear').addEventListener('click', function () {
        trackDigits = '';
        renderTrackDisplay();
    });

    $('track-go').addEventListener('click', doTrack);

    function doTrack() {
        if (!trackDigits) {
            toast('请先用右侧键盘输入单号');
            return;
        }
        var no = trackPrefix + trackDigits;
        var host = $('track-result');
        host.innerHTML = '<div class="track-placeholder"><p>查询中…</p></div>';
        fetchData('/api/public/screen/track/' + encodeURIComponent(no))
            .then(renderTrack)
            .catch(function (error) {
                host.innerHTML = '<div class="track-error">未查到该单据' +
                    '<small>' + escapeHtml(error.message && error.message !== 'bad' ? error.message : '请核对单号后重试，或扫码在小程序「我的申请」中查看') + '</small></div>';
            });
    }

    function renderTrack(data) {
        var host = $('track-result');
        var done = data.status === 'COMPLETED';
        var points = (data.timeline || []).slice().reverse();
        var timeline = points.map(function (point) {
            return '<li class="track-node">' +
                '<div class="track-node-action">' + escapeHtml(point.actionText || '') + '</div>' +
                '<div class="track-node-time">' + escapeHtml(point.time || '') + '</div>' +
                (point.note && point.note !== point.actionText
                    ? '<div class="track-node-note">' + escapeHtml(point.note) + '</div>' : '') +
                '</li>';
        }).join('');
        host.innerHTML =
            '<div class="track-order-head">' +
            '<span class="track-order-name">' + escapeHtml(data.itemName || '校园服务') + '</span>' +
            '<span class="track-status' + (done ? ' done' : '') + '">' + escapeHtml(data.statusText || data.status || '') + '</span>' +
            '</div>' +
            '<div class="track-order-no">' +
            (data.requestNo ? '申请单号 ' + escapeHtml(data.requestNo) : '') +
            (data.orderNo ? '　・　工单号 ' + escapeHtml(data.orderNo) : '') +
            (data.submittedAt ? '　・　提交于 ' + escapeHtml(data.submittedAt) : '') +
            '</div>' +
            (timeline
                ? '<ul class="track-timeline">' + timeline + '</ul>'
                : '<div class="track-placeholder"><p>暂无办理记录，请耐心等待受理</p></div>');
    }

    /* ==================== 启动 ==================== */
    fit();
    tickClock();
    setInterval(tickClock, 1000);
    $('home-greeting').textContent = greeting();
    renderTrackDisplay();

    new QRCode($('qrcode'), {
        text: QR_CONTENT, width: 120, height: 120,
        colorDark: '#0F172A', colorLight: '#ffffff',
        correctLevel: QRCode.CorrectLevel.M
    });

    loadNotices();
    setInterval(loadNotices, 10 * 60 * 1000);
    loadCatalogue();
    loadHomePulse();
    enterAttract();
})();
