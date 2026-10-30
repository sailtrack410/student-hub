#!/usr/bin/env python3
"""Campus OS 管理端冒烟脚本（Playwright / Chromium）。

覆盖：
1. 真实登录页登录（admin / DevOnly123! 默认开发账号，可用环境变量覆盖）；
2. 39 个管理端路由逐页加载，断言无 console error / pageerror，内容区非空；
3. /api/admin/context 校区上下文接口与顶栏校区切换器；
4. 广告审核页、广告主页、审计页关键元素；5. 8 个新增页面关键内容标记；
5. 逐页截图与 summary.json 结果。

运行：
    python3 tools/admin_smoke.py
    ADMIN_BASE_URL=http://127.0.0.1:9000 python3 tools/admin_smoke.py

脚本只访问本地开发环境，默认输出 artifacts/admin-smoke/。
"""
import json
import os
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = os.environ.get("ADMIN_BASE_URL", "http://127.0.0.1:9000").rstrip("/")
USERNAME = os.environ.get("ADMIN_USERNAME", "admin")
PASSWORD = os.environ.get("ADMIN_PASSWORD", "DevOnly123!")
OUT = Path(os.environ.get("ADMIN_SMOKE_OUT", "artifacts/admin-smoke"))
ROUTES = [
    "dashboard", "services", "requests", "leave", "workorders", "eval",
    "community-overview", "community-posts", "community", "community-appeals",
    "community-sections", "community-topics", "community-words", "community-official",
    "community-accounts", "community-ads", "community-advertisers",
    "achievements", "ai", "knowledge", "aitest", "users", "roles", "orgs", "dicts",
    "app-versions", "homework", "notices", "audit", "campuses", "system-config",
    "analytics", "sla", "file-storage", "scheduled-tasks", "push", "ai-security",
    "content-calendar", "creators",
]
ROUTE_TITLES = {
    "dashboard": "总览", "services": "服务目录", "requests": "服务申请", "workorders": "工单调度",
    "leave": "请假审批", "eval": "服务评价", "community-overview": "社区概览", "community-posts": "帖子管理",
    "community": "举报处置", "community-appeals": "申诉复核", "community-sections": "分区管理",
    "community-topics": "话题管理", "community-words": "屏蔽词", "community-official": "官方通知",
    "community-accounts": "官方与运营账号", "community-ads": "信息流广告",
    "community-advertisers": "广告主管理", "achievements": "成就管理", "ai": "AI 基座",
    "knowledge": "知识库", "aitest": "AI 对话测试", "users": "用户管理", "roles": "角色权限",
    "orgs": "组织架构", "dicts": "字典管理", "app-versions": "版本发布", "homework": "作业管理",
    "notices": "通知公告", "audit": "审计日志", "campuses": "校区管理", "system-config": "系统配置",
    "analytics": "数据分析", "sla": "服务 SLA", "file-storage": "文件存储",
    "scheduled-tasks": "定时任务", "push": "推送运营", "ai-security": "AI 安全策略",
    "content-calendar": "内容日历", "creators": "创作者运营",
}


def check(condition, message, failures):
    if condition:
        print("  ok   " + message)
    else:
        print("  FAIL " + message)
        failures.append(message)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    failures = []
    summary = {"base": BASE, "routes": [], "checks": [], "failures": []}

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 900}, locale="zh-CN")
        page = context.new_page()
        console_errors = []
        page.on("console", lambda m: console_errors.append(m.text) if m.type == "error" else None)
        page.on("pageerror", lambda e: console_errors.append(str(e)))

        # 1. 登录页真实登录
        page.goto(BASE + "/admin", wait_until="networkidle")
        page.wait_for_timeout(800)
        inputs = page.query_selector_all("input")
        check(len(inputs) >= 2, "登录页渲染用户名/密码输入框", failures)
        inputs[0].fill(USERNAME)
        inputs[1].fill(PASSWORD)
        page.click(".login-btn")
        page.wait_for_selector(".content", timeout=20000)
        page.wait_for_timeout(1200)
        check("#/dashboard" in page.url, "登录成功进入总览", failures)
        check(page.query_selector(".campus-name") is not None, "顶栏渲染当前校区", failures)
        summary["campus_label"] = page.inner_text(".campus-name")

        # 2. 校区上下文接口与下拉
        token = page.evaluate("localStorage.getItem('studenthub_admin_token')")
        context_resp = context.request.get(
            BASE + "/api/admin/context",
            headers={"Authorization": "Bearer " + (token or "")},
        )
        context_json = context_resp.json()
        context_data = context_json.get("data") or {}
        check(context_resp.ok and context_data.get("platformAdmin") is True, "GET /api/admin/context 返回平台管理员上下文", failures)
        check(bool(context_data.get("campuses")), "上下文包含可访问校区列表", failures)
        summary["context"] = {
            "campusId": context_data.get("campusId"),
            "campusName": context_data.get("campusName"),
            "campuses": len(context_data.get("campuses") or []),
            "platformAdmin": context_data.get("platformAdmin"),
        }
        page.click(".campus-pill")
        page.wait_for_timeout(400)
        options = page.query_selector_all(".campus-opt")
        check(len(options) >= 1, "校区切换器可展开并列出校区", failures)
        summary["campus_options"] = [o.inner_text().split("\n")[0] for o in options]
        # 有第二个校区时切换一次再切回，验证 localStorage / X-Campus-Id 链路
        if len(context_data.get("campuses") or []) >= 2:
            current = context_data.get("campusId")
            target = next((c for c in context_data["campuses"] if str(c["campusId"]) != str(current)), None)
            if target:
                page.evaluate(
                    """(name) => { const el = [...document.querySelectorAll('.campus-opt')]
                        .find(o => o.innerText.includes(name)); if (el) el.click(); }""",
                    target["campusName"],
                )
                page.wait_for_timeout(3000)
                check(page.inner_text(".campus-name") == target["campusName"], "切换校区后顶栏显示目标校区", failures)
                check(page.evaluate("localStorage.getItem('admin_campus_id')") == str(target["campusId"]),
                      "切换校区写入 localStorage", failures)
                # 切回默认校区，避免污染后续截图
                page.click(".campus-pill")
                page.wait_for_timeout(300)
                page.evaluate(
                    """(name) => { const el = [...document.querySelectorAll('.campus-opt')]
                        .find(o => o.innerText.includes(name)); if (el) el.click(); }""",
                    context_data["campuses"][0]["campusName"],
                )
                page.wait_for_timeout(2500)
        else:
            page.keyboard.press("Escape")
            page.wait_for_timeout(200)

        # 3. 逐路由加载
        for route in ROUTES:
            console_errors.clear()
            resp = page.goto(BASE + "/admin/#/" + route, wait_until="networkidle")
            page.wait_for_timeout(1000)
            content = page.query_selector(".content")
            body = content.inner_text() if content else ""
            title = page.inner_text("h1") if page.query_selector("h1") else ""
            expected = ROUTE_TITLES[route]
            route_errors = list(console_errors)
            bad_marker = any(marker in body for marker in ("页面脚本未加载", "页面加载失败", "未找到页面"))
            # 同一文档内 hash 导航时 Playwright 返回 None；不能当作失败。
            ok = (resp is None or resp.ok) and bool(body.strip()) and not route_errors and not bad_marker
            if expected not in title and expected not in body:
                ok = False
                route_errors.append("标题未包含：" + expected)
            page.screenshot(path=str(OUT / (route + ".png")), full_page=True)
            summary["routes"].append({
                "route": route,
                "status": resp.status if resp else None,
                "title": title[:80],
                "errors": route_errors[:10],
            })
            check(ok, "路由 #/%s 加载正常（%s）" % (route, expected), failures)
            if route_errors:
                print("       " + " | ".join(route_errors[:3]))

        # 4. 页面关键元素
        page.goto(BASE + "/admin/#/community-ads", wait_until="networkidle")
        page.wait_for_timeout(1200)
        check("审核" in page.inner_text(".content") and "待审核" in page.inner_text(".content"),
              "信息流广告页包含审核状态与待审核 KPI", failures)
        page.goto(BASE + "/admin/#/community-advertisers", wait_until="networkidle")
        page.wait_for_timeout(1000)
        check("广告主列表" in page.inner_text(".content") and "新建广告主" in page.inner_text(".content"),
              "广告主管理页可访问", failures)
        page.goto(BASE + "/admin/#/audit", wait_until="networkidle")
        page.wait_for_timeout(1000)
        audit_text = page.inner_text(".content")
        check("关键写操作" in audit_text and "全部动作" in audit_text, "审计页包含关键写操作 KPI 与动作筛选", failures)

        # 5. 本轮新增页面关键元素（防止“路由能开、内容未渲染”）
        new_page_checks = [
            ("analytics", "按天趋势"),
            ("leave", "待审批"),
            ("sla", "按服务项"),
            ("file-storage", "存储设置"),
            ("scheduled-tasks", "任务清单"),
            ("push", "推送任务"),
            ("ai-security", "提示词版本"),
            ("content-calendar", "运营位"),
            ("creators", "创作者"),
        ]
        for route, marker in new_page_checks:
            page.goto(BASE + "/admin/#/" + route, wait_until="networkidle")
            page.wait_for_timeout(900)
            text = page.inner_text(".content")
            check(marker in text and "页面加载失败" not in text,
                  "新增页 #/%s 渲染关键内容（%s）" % (route, marker), failures)

        # 6. 本轮补齐项内容检查：C01 时间范围/导出、C12/C13 入口、AI 成本、功能开关灰度
        page.goto(BASE + "/admin/#/community-overview", wait_until="networkidle")
        page.wait_for_timeout(1000)
        overview_text = page.inner_text(".content")
        check("近 30 天" in overview_text and "导出" in overview_text,
              "C01 社区概览含 7/30 天切换与导出", failures)
        page.goto(BASE + "/admin/#/community-ads", wait_until="networkidle")
        page.wait_for_timeout(1000)
        ads_text = page.inner_text(".content")
        check("投放计划" in ads_text and "素材库" in ads_text,
              "C13 广告页含投放计划/素材库页签", failures)
        page.goto(BASE + "/admin/#/community-accounts", wait_until="networkidle")
        page.wait_for_timeout(900)
        check("官方代发" in page.inner_text(".content"), "C12 官方账号页含官方代发入口", failures)
        page.goto(BASE + "/admin/#/analytics", wait_until="networkidle")
        page.wait_for_timeout(1000)
        analytics_text = page.inner_text(".content")
        check("AI 成本" in analytics_text and "单价" in analytics_text,
              "I01 数据分析页含 AI 成本区块", failures)
        page.goto(BASE + "/admin/#/system-config", wait_until="networkidle")
        page.wait_for_timeout(900)
        check("功能开关" in page.inner_text(".content"), "SYS02 系统配置页含功能开关入口", failures)

        summary["failures"] = failures
        summary["ok"] = not failures
        (OUT / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        browser.close()

    print()
    print("smoke: %d routes, %d failures" % (len(ROUTES), len(failures)))
    if failures:
        for item in failures:
            print(" - " + item)
        return 1
    print("all good, summary: " + str(OUT / "summary.json"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
