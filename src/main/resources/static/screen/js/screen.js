/* 数据大屏：拉取 /api/public/screen/overview 并渲染。
   1920×1080 设计稿等比缩放，适配任意尺寸演示屏。 */
(function () {
    'use strict';

    var REFRESH_MS = 15000;

    /* 微信小程序二维码内容：正式小程序码可替换为小程序码图片地址，当前为占位文本码 */
    var QR_CONTENT = '绵城码上办 | 微信扫码体验小程序';

    var app = document.getElementById('app');

    /* 1920×1080 等比缩放 */
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

    var charts = {};

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
        if (start === target) {
            el.textContent = target;
            return;
        }
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

    function statItem(value, label, colorClass) {
        return '<div class="stat-item"><div class="stat-value ' + colorClass +
            '" data-value="0">' + value + '</div><div class="stat-label">' + label + '</div></div>';
    }

    function renderKpis(items) {
        var host = document.getElementById('kpi-row');
        var previous = {};
        Array.prototype.forEach.call(host.querySelectorAll('.kpi'), function (child) {
            var label = child.querySelector('.kpi-label').textContent;
            previous[label] = Number(child.querySelector('.kpi-value').dataset.value || '0') || 0;
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

    function renderStats(id, items) {
        var host = document.getElementById(id);
        // 记录旧值用于数字滚动过渡
        var previous = {};
        Array.prototype.forEach.call(host.querySelectorAll('.stat-item'), function (child) {
            var label = child.querySelector('.stat-label').textContent;
            previous[label] = Number(child.querySelector('.stat-value').dataset.value || '0') || 0;
        });
        host.innerHTML = items.map(function (item) {
            return statItem(item.value, item.label, item.color);
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

    /* ---------- 趋势图 ---------- */
    function renderTrend(trend) {
        var labels = trend.map(function (point) { return point.date + '\n' + point.weekday; });
        chart('chart-trend').setOption({
            grid: { left: 42, right: 18, top: 34, bottom: 30 },
            legend: {
                data: ['提交申请', '办结工单'],
                textStyle: { color: '#8fa8cf', fontSize: 12 },
                itemWidth: 14, itemHeight: 8, top: 2, right: 8
            },
            tooltip: { trigger: 'axis' },
            xAxis: {
                type: 'category', data: labels,
                axisLine: { lineStyle: { color: 'rgba(86,144,235,0.4)' } },
                axisTick: { show: false },
                axisLabel: { color: '#8fa8cf', fontSize: 11, lineHeight: 15, interval: 0 }
            },
            yAxis: {
                type: 'value', minInterval: 1,
                splitLine: { lineStyle: { color: 'rgba(56,97,175,0.22)' } },
                axisLabel: { color: '#8fa8cf', fontSize: 11 }
            },
            series: [
                {
                    name: '提交申请', type: 'bar', barWidth: 14,
                    data: trend.map(function (point) { return point.submitted; }),
                    itemStyle: {
                        borderRadius: [4, 4, 0, 0],
                        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                            { offset: 0, color: '#35d0ff' }, { offset: 1, color: 'rgba(53,208,255,0.15)' }])
                    }
                },
                {
                    name: '办结工单', type: 'line', smooth: true, symbolSize: 6,
                    data: trend.map(function (point) { return point.completed; }),
                    lineStyle: { color: '#ffc257', width: 2 },
                    itemStyle: { color: '#ffc257' },
                    areaStyle: {
                        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                            { offset: 0, color: 'rgba(255,194,87,0.25)' }, { offset: 1, color: 'rgba(255,194,87,0)' }])
                    }
                }
            ]
        });
    }

    /* ---------- 热度图 ---------- */
    function renderHeat(topServices) {
        var sorted = topServices.slice().reverse();
        chart('chart-heat').setOption({
            grid: { left: 130, right: 46, top: 12, bottom: 10 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            xAxis: {
                type: 'value', minInterval: 1,
                splitLine: { show: false },
                axisLabel: { show: false },
                axisLine: { show: false }, axisTick: { show: false }
            },
            yAxis: {
                type: 'category',
                data: sorted.map(function (item) { return item.itemName; }),
                axisLine: { show: false }, axisTick: { show: false },
                axisLabel: { color: '#cfe4ff', fontSize: 13 }
            },
            series: [{
                type: 'bar', barWidth: 16,
                label: { show: true, position: 'right', color: '#35d0ff', fontWeight: 700 },
                data: sorted.map(function (item) { return item.count; }),
                itemStyle: {
                    borderRadius: [0, 8, 8, 0],
                    color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                        { offset: 0, color: 'rgba(77,139,255,0.35)' },
                        { offset: 1, color: '#35d0ff' }])
                }
            }]
        });
    }

    /* ---------- 状态徽章 ---------- */
    var STATUS_LABEL = {
        SUBMITTED: '待受理', PROCESSING: '处理中', COMPLETED: '已完成',
        REJECTED: '未受理', CANCELLED: '已撤销'
    };
    var STATUS_COLOR = {
        SUBMITTED: 'c-gold', PROCESSING: 'c-cyan', COMPLETED: 'c-green',
        REJECTED: 'c-red', CANCELLED: 'c-blue'
    };

    function renderStatus(dist) {
        var host = document.getElementById('request-status');
        host.innerHTML = Object.keys(STATUS_LABEL).map(function (key) {
            return '<div class="badge"><div class="badge-count ' + (STATUS_COLOR[key] || '') +
                '">' + (dist[key] || 0) + '</div><div class="badge-label">' +
                STATUS_LABEL[key] + '</div></div>';
        }).join('');
    }

    /* ---------- 办结动态 ---------- */
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

    /* ---------- 数据拉取 ---------- */
    function load() {
        fetch('/api/public/screen/overview')
            .then(function (res) { return res.json(); })
            .then(function (body) {
                if (!body || body.success !== true || !body.data) {
                    throw new Error(body && body.message || 'bad response');
                }
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
                document.getElementById('refresh-info').textContent =
                    '数据更新失败，自动重试中…';
            })
            .finally(function () {
                setTimeout(load, REFRESH_MS);
            });
    }

    /* ---------- 启动 ---------- */
    fit();
    tickClock();
    setInterval(tickClock, 1000);

    new QRCode(document.getElementById('qrcode'), {
        text: QR_CONTENT,
        width: 108,
        height: 108,
        colorDark: '#0b1830',
        colorLight: '#ffffff',
        correctLevel: QRCode.CorrectLevel.M
    });

    load();
})();