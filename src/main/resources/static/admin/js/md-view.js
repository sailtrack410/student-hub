/* AdminUI.md-view：可复用 Markdown 渲染组件（Vue3 全局组件）
 * 用法：<md-view :source="text" :streaming="isStreaming" />
 * - source：Markdown 原文（响应式，流式增量更新会实时重渲染）
 * - streaming：true 时不渲染图片（流式中途 URL 不完整，渲染会裂图），结束后自动补渲染
 * - 内部 marked 解析 + DOMPurify 消毒，样式见 pages.css 的 .mdv
 */
(function () {
    'use strict';

    var ready = false;
    function ensureLibs() {
        if (ready) return true;
        if (!window.marked || !window.DOMPurify) return false;
        try {
            window.marked.setOptions({ breaks: true, gfm: true });
        } catch (e) { /* 已配置过则忽略 */ }
        ready = true;
        return true;
    }

    /* 容错预处理：LLM 常省略标记后的空格（###标题、-项目），CommonMark 不认，
       逐行补齐井号/列表符/引用符后的空格，不改动代码块内的内容 */
    function tolerateLooseMarkdown(src) {
        var lines = String(src).split('\n');
        var inFence = false;
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (/^\s*(```|~~~)/.test(line)) { inFence = !inFence; continue; }
            if (inFence) continue;
            // 标题：行首 1-6 个 # 后紧跟非空格非#
            lines[i] = line.replace(/^(#{1,6})([^#\s])/m, '$1 $2');
            // 无序/有序列表符与引用符后紧跟非空格
            lines[i] = lines[i].replace(/^(\s*[-*+>])([^>\s])/m, '$1 $2')
                               .replace(/^(\s*\d+\.)(\S)/m, '$1 $2');
        }
        return lines.join('\n');
    }

    function renderMarkdown(src, streaming) {
        if (!src) return '';
        if (!ensureLibs()) {
            // 库未加载时退化为纯文本，保证不白屏
            var esc = String(src).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            return '<div class="mdv-plain">' + esc + '</div>';
        }
        var html = window.marked.parse(tolerateLooseMarkdown(String(src)));
        var config = { ADD_ATTR: ['target'] };
        if (streaming) config.FORBID_TAGS = ['img'];
        return window.DOMPurify.sanitize(html, config);
    }

    window.AdminUI = window.AdminUI || {};

    window.AdminUI.mdView = {
        name: 'md-view',
        props: {
            source: { type: String, default: '' },
            streaming: { type: Boolean, default: false }
        },
        computed: {
            html: function () {
                return renderMarkdown(this.source, this.streaming);
            }
        },
        template: '<div class="mdv" v-html="html"></div>'
    };

    /** 页面侧一次性注册：Vue.app.component('md-view', AdminUI.mdView) */
    window.AdminUI.registerMdView = function (app) {
        app.component('md-view', window.AdminUI.mdView);
    };
})();
