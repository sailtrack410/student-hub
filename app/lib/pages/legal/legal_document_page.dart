import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';
import 'legal_document_content.dart';

/// 应用内法律文档页（用户协议 / 隐私政策）。
///
/// - 正文全部内置在 App 中，通过 [Navigator.push] 打开，不依赖外部 URL；
/// - 版本号与生效日期统一取自 [legal_document_content]；
/// - 文档为试运行文本，页面顶部与底部标注“正式上线前需法务复核”。
class LegalDocumentPage extends StatelessWidget {
  const LegalDocumentPage({super.key, required this.type});

  final LegalDocumentType type;

  /// 打开法律文档页的统入口，供登录页与设置页复用。
  static Future<void> open(BuildContext context, LegalDocumentType type) {
    return Navigator.of(context).push<void>(
      MaterialPageRoute<void>(builder: (_) => LegalDocumentPage(type: type)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final sections = legalSectionsOf(type);
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 20,
            color: Color(0xFF0F172A),
          ),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Text(
          '《${type.title}》',
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
        children: [
          _buildHeader(),
          for (final section in sections) _buildSection(section),
          const SizedBox(height: 6),
          _buildFooter(),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            type.title,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0F172A),
              height: 1.3,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            type.subtitle,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
              height: 1.5,
            ),
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: [
              _buildTag('版本 $legalDocumentVersion'),
              _buildTag('生效日期 $legalDocumentEffectiveDate'),
            ],
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            decoration: BoxDecoration(
              color: const Color(0xFFFFF7ED),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFFFED7AA)),
            ),
            child: const Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  Icons.info_outline_rounded,
                  size: 15,
                  color: Color(0xFFF59E0B),
                ),
                SizedBox(width: 6),
                Expanded(
                  child: Text(
                    legalDraftNotice,
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      color: Color(0xFFB45309),
                      height: 1.5,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTag(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        text,
        style: const TextStyle(
          fontFamily: 'Noto Sans SC',
          fontSize: 12,
          color: Color(0xFF475569),
          height: 1.2,
        ),
      ),
    );
  }

  Widget _buildSection(LegalSection section) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 3,
                height: 14,
                decoration: BoxDecoration(
                  color: AppColors.brand,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  section.heading,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                    height: 1.3,
                  ),
                ),
              ),
            ],
          ),
          for (final paragraph in section.paragraphs)
            Padding(
              padding: const EdgeInsets.only(top: 8, bottom: 6),
              child: Text(
                paragraph,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 13,
                  color: Color(0xFF334155),
                  height: 1.7,
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildFooter() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 8),
          Text(
            '文档版本 $legalDocumentVersion · 生效日期 $legalDocumentEffectiveDate',
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
              height: 1.5,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            '绵阳城市学院校园服务平台运营方',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
              height: 1.5,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '$legalDraftNotice如对本文档有疑问，可通过 App 内“帮助中心与反馈”入口联系运营方。',
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }
}
