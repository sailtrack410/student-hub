/**
 * 轻量 Markdown → HTML 转换器（供原生 rich-text 渲染）。
 * 覆盖 AI 回复常见语法：标题、加粗、斜体、行内代码、代码块、无序/有序列表。
 * 仅输出 rich-text 信任的标签（p/div/br/strong/em/code），避免第三方依赖。
 */

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** 行内语法：先转义，再处理行内代码与加粗/斜体。 */
function inline(text) {
  let s = escapeHtml(text);
  s = s.replace(/`([^`\n]+)`/g, '<code style="background:#eef2f7;border-radius:4rpx;padding:1rpx 8rpx;font-size:24rpx;color:#334155;">$1</code>');
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>');
  return s;
}

function paragraphLine(line) {
  return `<p style="line-height:1.6;margin:4rpx 0;">${line}</p>`;
}

function markdownToHtml(markdown) {
  const source = markdown == null ? '' : String(markdown);
  if (!source.trim()) return '';

  const html = [];
  const lines = source.replace(/\r\n/g, '\n').split('\n');

  let i = 0;
  let paragraph = [];
  const drainParagraph = () => {
    if (paragraph.length) {
      html.push(paragraph.map((line) => paragraphLine(inline(line))).join(''));
      paragraph = [];
    }
  };

  while (i < lines.length) {
    const trimmed = lines[i].trim();

    // 代码块 ``` ... ```
    if (trimmed.startsWith('```')) {
      drainParagraph();
      const codeLines = [];
      i += 1;
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        codeLines.push(escapeHtml(lines[i]));
        i += 1;
      }
      i += 1; // 结尾 ```
      html.push(`<div style="background:#f6f8fa;border-radius:8rpx;padding:12rpx 16rpx;margin:6rpx 0;font-size:24rpx;color:#334155;white-space:pre-wrap;word-break:break-all;">${codeLines.join('\n')}</div>`);
      continue;
    }

    // 标题（h1~h3）
    const heading = trimmed.match(/^(#{1,3})\s+(.*)$/);
    if (heading) {
      drainParagraph();
      const font = heading[1].length === 1 ? 30 : heading[1].length === 2 ? 27 : 25;
      html.push(`<p style="font-size:${font}rpx;font-weight:700;margin:10rpx 0 4rpx;line-height:1.45;">${inline(heading[2])}</p>`);
      i += 1;
      continue;
    }

    // 无序列表
    if (/^\s*[-*]\s+/.test(trimmed)) {
      drainParagraph();
      const items = [];
      while (i < lines.length && /^\s*[-*]\s+/.test(lines[i].trim())) {
        items.push(`<p style="line-height:1.6;margin:3rpx 0;">•&nbsp;${inline(lines[i].trim().replace(/^\s*[-*]\s+/, ''))}</p>`);
        i += 1;
      }
      html.push(`<div style="margin:4rpx 0 4rpx 8rpx;">${items.join('')}</div>`);
      continue;
    }

    // 有序列表
    if (/^\s*\d+[.、)]\s*/.test(trimmed)) {
      drainParagraph();
      const items = [];
      let order = 1;
      while (i < lines.length && /^\s*\d+[.、)]\s*/.test(lines[i].trim())) {
        items.push(`<p style="line-height:1.6;margin:3rpx 0;">${order}.&nbsp;${inline(lines[i].trim().replace(/^\s*\d+[.、)]\s*/, ''))}</p>`);
        order += 1;
        i += 1;
      }
      html.push(`<div style="margin:4rpx 0 4rpx 8rpx;">${items.join('')}</div>`);
      continue;
    }

    // 空行分段
    if (!trimmed) {
      drainParagraph();
      i += 1;
      continue;
    }

    paragraph.push(trimmed.replace(/\s+$/, ''));
    i += 1;
  }

  drainParagraph();
  return html.join('');
}

module.exports = { markdownToHtml };