import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_highlight/flutter_highlight.dart';
import 'package:flutter_highlight/themes/atom-one-dark.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:markdown/markdown.dart' as md;

/// AI 对话 Markdown 渲染：GFM、代码高亮、Mermaid（mermaid.ink 转图）。
class MarkdownText extends StatelessWidget {
  final String data;
  final bool isUser;

  const MarkdownText({super.key, required this.data, this.isUser = false});

  @override
  Widget build(BuildContext context) {
    final baseColor = isUser ? Colors.white : const Color(0xFF1E293B);
    final mutedColor = isUser
        ? const Color(0xFFDBEAFE)
        : const Color(0xFF64748B);
    final styleSheet = MarkdownStyleSheet(
      p: TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 13.5,
        height: 1.55,
        color: baseColor,
      ),
      h1: TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 19,
        fontWeight: FontWeight.bold,
        color: baseColor,
      ),
      h2: TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 17,
        fontWeight: FontWeight.bold,
        color: baseColor,
      ),
      h3: TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 15,
        fontWeight: FontWeight.bold,
        color: baseColor,
      ),
      h4: TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 14,
        fontWeight: FontWeight.w600,
        color: baseColor,
      ),
      strong: TextStyle(fontWeight: FontWeight.bold, color: baseColor),
      em: TextStyle(fontStyle: FontStyle.italic, color: baseColor),
      a: TextStyle(
        color: isUser ? Colors.white : const Color(0xFF2563EB),
        decoration: TextDecoration.underline,
      ),
      code: TextStyle(
        fontFamily: 'monospace',
        fontSize: 12,
        backgroundColor: isUser
            ? const Color(0x33FFFFFF)
            : const Color(0xFFF1F5F9),
        color: isUser ? Colors.white : const Color(0xFF0F172A),
      ),
      blockquote: TextStyle(
        fontFamily: 'Noto Sans SC',
        fontSize: 13,
        height: 1.5,
        color: mutedColor,
      ),
      blockquoteDecoration: BoxDecoration(
        color: isUser ? const Color(0x22FFFFFF) : const Color(0xFFF8FAFC),
        border: Border(
          left: BorderSide(
            color: isUser ? Colors.white54 : const Color(0xFF93C5FD),
            width: 3,
          ),
        ),
      ),
      blockquotePadding: const EdgeInsets.fromLTRB(10, 6, 6, 6),
      listBullet: TextStyle(color: baseColor, fontSize: 13.5),
      listIndent: 18,
      blockSpacing: 8,
      tableHead: TextStyle(
        fontWeight: FontWeight.bold,
        color: baseColor,
        fontFamily: 'Noto Sans SC',
        fontSize: 12,
      ),
      tableBody: TextStyle(
        color: baseColor,
        fontFamily: 'Noto Sans SC',
        fontSize: 12,
      ),
      tableBorder: TableBorder.all(
        color: isUser ? Colors.white30 : const Color(0xFFE2E8F0),
      ),
      tablePadding: const EdgeInsets.all(6),
      horizontalRuleDecoration: BoxDecoration(
        border: Border(
          top: BorderSide(
            color: isUser ? Colors.white30 : const Color(0xFFE2E8F0),
          ),
        ),
      ),
    );

    final segments = _splitFencedBlocks(data);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: segments.map((segment) {
        if (segment is _CodeSegment) {
          return _CodeBlock(code: segment.code, language: segment.language);
        }
        final prose = (segment as _ProseSegment).text.trim();
        if (prose.isEmpty) return const SizedBox.shrink();
        return MarkdownBody(
          data: prose,
          selectable: true,
          extensionSet: md.ExtensionSet.gitHubFlavored,
          styleSheet: styleSheet,
        );
      }).toList(),
    );
  }

  List<Object> _splitFencedBlocks(String text) {
    final result = <Object>[];
    final pattern = RegExp(r'```([^\n`]*)\n([\s\S]*?)```', multiLine: true);
    var last = 0;
    for (final match in pattern.allMatches(text)) {
      if (match.start > last)
        result.add(_ProseSegment(text.substring(last, match.start)));
      result.add(
        _CodeSegment(
          language: match.group(1)?.trim() ?? '',
          code: match.group(2)?.trimRight() ?? '',
        ),
      );
      last = match.end;
    }
    if (last < text.length) result.add(_ProseSegment(text.substring(last)));
    if (result.isEmpty) result.add(_ProseSegment(text));
    return result;
  }
}

class _ProseSegment {
  final String text;
  const _ProseSegment(this.text);
}

class _CodeSegment {
  final String language;
  final String code;
  const _CodeSegment({required this.language, required this.code});
}

class _CodeBlock extends StatefulWidget {
  final String code;
  final String language;

  const _CodeBlock({required this.code, required this.language});

  @override
  State<_CodeBlock> createState() => _CodeBlockState();
}

class _CodeBlockState extends State<_CodeBlock> {
  bool _preview = false;

  String get _label => widget.language.isEmpty ? 'code' : widget.language;

  bool get _supportsPreview => true;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFF0F172A),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFF1E293B)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.fromLTRB(10, 5, 4, 5),
            color: const Color(0xFF1E293B),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    _label,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 12,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ),
                _buildModeSwitch(),
                const SizedBox(width: 2),
                IconButton(
                  visualDensity: VisualDensity.compact,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(
                    minWidth: 28,
                    minHeight: 24,
                  ),
                  icon: const Icon(
                    Icons.copy_rounded,
                    size: 14,
                    color: Color(0xFF94A3B8),
                  ),
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: widget.code));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('代码已复制'),
                        duration: Duration(seconds: 1),
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
          AnimatedSwitcher(
            duration: MediaQuery.disableAnimationsOf(context) ? Duration.zero : const Duration(milliseconds: 220),
            child: _preview
                ? KeyedSubtree(
                    key: const ValueKey('preview'),
                    child: _buildPreview(),
                  )
                : SingleChildScrollView(
                    key: const ValueKey('code'),
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.all(10),
                    child: HighlightView(
                      widget.code,
                      language: widget.language.isEmpty
                          ? 'plaintext'
                          : widget.language,
                      theme: atomOneDarkTheme,
                      padding: EdgeInsets.zero,
                      textStyle: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 12,
                      ),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildModeSwitch() {
    return Container(
      width: 108,
      height: 26,
      padding: const EdgeInsets.all(2),
      decoration: BoxDecoration(
        color: const Color(0xFF0F172A),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: const Color(0xFF334155)),
      ),
      child: Stack(
        children: [
          AnimatedAlign(
            duration: MediaQuery.disableAnimationsOf(context) ? Duration.zero : const Duration(milliseconds: 220),
            curve: Curves.easeOutCubic,
            alignment: _preview ? Alignment.centerRight : Alignment.centerLeft,
            child: FractionallySizedBox(
              widthFactor: 0.5,
              heightFactor: 1,
              child: Container(
                decoration: BoxDecoration(
                  color: const Color(0xFF2563EB),
                  borderRadius: BorderRadius.circular(4),
                ),
              ),
            ),
          ),
          Row(
            children: [
              Expanded(
                child: GestureDetector(
                  onTap: () => setState(() => _preview = false),
                  behavior: HitTestBehavior.opaque,
                  child: Center(
                    child: Text(
                      '代码',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: _preview
                            ? const Color(0xFF64748B)
                            : Colors.white,
                      ),
                    ),
                  ),
                ),
              ),
              Expanded(
                child: GestureDetector(
                  onTap: _supportsPreview
                      ? () => setState(() => _preview = true)
                      : null,
                  behavior: HitTestBehavior.opaque,
                  child: Center(
                    child: Text(
                      '预览',
                      style: TextStyle(
                        fontFamily: 'Noto Sans SC',
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: _preview
                            ? Colors.white
                            : _supportsPreview
                            ? const Color(0xFF64748B)
                            : const Color(0xFF475569),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPreview() {
    final language = widget.language.toLowerCase();
    if (language == 'mermaid') {
      return _MermaidDiagram(code: widget.code);
    }
    if (language == 'markdown' || language == 'md') {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(8),
        ),
        child: MarkdownText(data: widget.code),
      );
    }
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Text(
        '该代码块暂无可视化预览',
        style: TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: 12,
          color: Color(0xFF64748B),
        ),
      ),
    );
  }
}

class _MermaidDiagram extends StatelessWidget {
  final String code;

  const _MermaidDiagram({required this.code});

  @override
  Widget build(BuildContext context) {
    final encoded = base64Url.encode(utf8.encode(code)).replaceAll('=', '');
    final url = 'https://mermaid.ink/img/$encoded?bgColor=FFFFFF';
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(bottom: Radius.circular(9)),
      child: ColoredBox(
        color: Colors.white,
        child: Image.network(
          url,
          width: double.infinity,
          fit: BoxFit.contain,
          loadingBuilder: (context, child, progress) {
            if (progress == null) return child;
            return const SizedBox(
              height: 80,
              child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
            );
          },
          errorBuilder: (context, error, stack) => _MermaidFallback(code: code),
        ),
      ),
    );
  }
}

class _MermaidFallback extends StatelessWidget {
  final String code;

  const _MermaidFallback({required this.code});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(6),
      ),
      child: SelectableText(
        code,
        style: const TextStyle(
          fontFamily: 'monospace',
          fontSize: 12,
          color: Color(0xFF475569),
        ),
      ),
    );
  }
}
