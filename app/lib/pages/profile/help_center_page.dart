import 'package:flutter/material.dart';

import '../service/my_requests_page.dart';

class HelpCenterPage extends StatelessWidget {
  const HelpCenterPage({super.key});

  static const _faqs = <({String q, String a})>[
    (q: '如何完成教务系统实名认证？', a: '在「我的 → 账号中心 → 教务绑定」输入教务系统学号和密码，认证成功后可同步课表与成绩。'),
    (q: '报修申请多久会有人处理？', a: '一般 24 小时内会有后勤师傅接单；紧急水电问题请同时联系宿管值班室。'),
    (q: '发布的内容为什么仅自己可见？', a: '内容命中社区安全规则时会先进入人工审核，审核通过后自动恢复公开；你也可以在内容详情页提交申诉。'),
    (q: '如何佩戴或更换勋章？', a: '进入「我的 → 荣誉典藏室」，点击已解锁勋章选择「佩戴」即可。'),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7F9),
      appBar: AppBar(
        backgroundColor: const Color(0xFFF6F7F9),
        foregroundColor: const Color(0xFF20221F),
        elevation: 0,
        scrolledUnderElevation: 0,
        title: const Text('帮助中心', style: TextStyle(fontSize: 17)),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(22, 18, 22, 32),
        children: [
          const Text(
            '常见问题',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: Color(0xFF7B7B70),
            ),
          ),
          const SizedBox(height: 8),
          for (final faq in _faqs)
            Container(
              decoration: const BoxDecoration(
                border: Border(bottom: BorderSide(color: Color(0xFFDCDAD2))),
              ),
              child: ExpansionTile(
                shape: const Border(),
                collapsedShape: const Border(),
                tilePadding: EdgeInsets.zero,
                iconColor: const Color(0xFF8A7041),
                collapsedIconColor: const Color(0xFF7B7B70),
                childrenPadding: const EdgeInsets.fromLTRB(0, 0, 18, 17),
                title: Text(
                  faq.q,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    color: Color(0xFF20221F),
                  ),
                ),
                children: [
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      faq.a,
                      style: const TextStyle(
                        fontSize: 12,
                        height: 1.7,
                        color: Color(0xFF7B7B70),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 29),
          const Text(
            '服务与申诉',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: Color(0xFF7B7B70),
            ),
          ),
          const SizedBox(height: 9),
          const Text(
            '报修进度、工单处理与服务评价可在工单中心查看。',
            style: TextStyle(
              fontSize: 12,
              height: 1.7,
              color: Color(0xFF7B7B70),
            ),
          ),
          const SizedBox(height: 18),
          OutlinedButton(
            onPressed: () => Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => const MyRequestsPage())),
            style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFF8A7041),
              side: const BorderSide(color: Color(0xFFB59A62)),
              minimumSize: const Size.fromHeight(46),
            ),
            child: const Text('前往工单中心'),
          ),
        ],
      ),
    );
  }
}
