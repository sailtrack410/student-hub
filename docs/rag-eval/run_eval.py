#!/usr/bin/env python3
"""RAG 检索评测运行器（在本目录下运行）。

对 eval-set.json 中每道题计算 recall@k / MRR / nDCG / 干扰率，支持离线自检、
线上服务评测，以及对比两次运行结果。

用法（请先 cd 到本目录）：
  cd docs/rag-eval

  # 离线自检：关键词基线，不需要服务，用于验证评测集本身可判分
  python3 run_eval.py --strategy keyword

  # 存基线并评测线上，再对比（结果经重定向保存，脚本本身不写文件）
  python3 run_eval.py --strategy keyword --json > baseline.json
  python3 run_eval.py --strategy http --kb-id <知识库ID> --token <Token> --json > current.json
  python3 run_eval.py --compare

安全约束：
- 本脚本不含任何路径参数与路径运算，只按固定文件名读取同目录的 eval-set.json
  与 corpus.json，且不写任何文件（结果输出到 stdout），因此不存在由外部输入
  构造的文件路径。
- 网络请求仅允许 http/https、不得携带 userinfo、禁止重定向；目标解析后必须是
  回环地址，非回环须显式 --allow-host；链路本地(含云元数据)/保留/组播/未指定
  地址永远阻断。

数据文件：
- corpus.json    由 corpus/*.md 合并生成（见 README），供脚本单文件读取
- eval-set.json  评测问题与金标

设计说明：
- 金标以「文档 + 章节」标注，与分块策略解耦；换分块/换模型都复用同一份评测集。
- 离线策略的检索单元是「文档-章节」片段，线上策略是服务返回的命中分块，
  通过归属映射回金标，因此两种策略的指标可比。
"""
import argparse
import ipaddress
import json
import math
import re
import socket
import sys
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict

EVAL_SET_FILE = "eval-set.json"
CORPUS_FILE = "corpus.json"
BASELINE_FILE = "baseline.json"
CURRENT_FILE = "current.json"
ALLOWED_SCHEMES = ("http", "https")
MAX_RESPONSE_BYTES = 8 * 1024 * 1024


# ---------------------------------------------------------------- 网络目标校验

def validate_target(url, allow_hosts):
    """校验请求目标：协议、userinfo、解析后 IP 范围。"""
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in ALLOWED_SCHEMES:
        raise ValueError(f"仅允许 {'/'.join(ALLOWED_SCHEMES)}，收到: {parsed.scheme!r}")
    if parsed.username or parsed.password:
        raise ValueError("URL 不得携带用户名/密码")
    host = parsed.hostname
    if not host:
        raise ValueError("URL 缺少主机名")
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    try:
        infos = socket.getaddrinfo(host, port, proto=socket.IPPROTO_TCP)
    except socket.gaierror as exc:
        raise ValueError(f"无法解析主机 {host}: {exc}")
    if not infos:
        raise ValueError(f"主机 {host} 未解析到地址")
    allowed = host in allow_hosts
    for info in infos:
        addr = ipaddress.ip_address(info[4][0])
        if addr.is_link_local or addr.is_multicast or addr.is_unspecified or addr.is_reserved:
            raise ValueError(f"目标地址被阻断: {addr}")
        if addr.is_loopback:
            continue
        if not allowed:
            raise ValueError(f"目标 {host}({addr}) 非回环；如确需访问请显式 --allow-host {host}")
    return url


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """禁止跟随重定向，防止借跳转绕过目标校验。"""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise urllib.error.HTTPError(
            req.full_url, code, f"重定向被拒绝（可能用于绕过目标校验）: {newurl}", headers, fp)


def http_post_json(url, payload, token, timeout):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.build_opener(_NoRedirect).open(req, timeout=timeout) as resp:
        raw = resp.read(MAX_RESPONSE_BYTES + 1)
    if len(raw) > MAX_RESPONSE_BYTES:
        raise ValueError("响应体超过大小上限")
    return json.loads(raw.decode("utf-8"))


# ---------------------------------------------------------------- 语料与切分

def norm(text):
    """归一化全角/半角与空白，降低中英标点差异造成的匹配噪声。"""
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", text or ""))


def split_sections(markdown):
    """按 ## 标题切分章节：{标题: 正文}，用于把金标 section 落到具体片段。"""
    sections, current, buf = {}, "__preamble__", []
    for line in markdown.splitlines():
        if line.startswith("## "):
            if buf:
                sections[current] = "\n".join(buf)
            current, buf = line[3:].strip(), []
        else:
            buf.append(line)
    if buf:
        sections[current] = "\n".join(buf)
    return sections


def load_json(name):
    try:
        with open(name, encoding="utf-8") as fh:
            return json.load(fh)
    except FileNotFoundError:
        raise ValueError(f"缺少数据文件 {name}（请在 docs/rag-eval 目录下运行）")


def build_units(corpus):
    """检索单元：每个 (文档, 章节) 为一个候选片段。"""
    units = []
    for doc, text in corpus.items():
        for title, body in split_sections(text).items():
            body = body.strip()
            if len(body) >= 10:
                units.append({"doc": doc, "section": title, "text": body,
                              "key": f"{doc}#{title}"})
    return units


# ---------------------------------------------------------------- 检索策略

def strategy_keyword(units, question, top_k):
    """基线：二元组覆盖率 + 章节标题加权。用于离线自检与下限对照。"""
    q = norm(question)
    grams = {q[i:i + 2] for i in range(len(q) - 1)}
    scored = []
    for u in units:
        t = norm(u["text"]) + norm(u["section"])
        matched = sum(1 for g in grams if g in t)
        base = matched / len(grams) if grams else 0.0
        bonus = 0.25 if any(g in norm(u["section"]) for g in grams) else 0.0
        scored.append((base + bonus, u))
    scored.sort(key=lambda x: -x[0])
    return [u for _, u in scored[:top_k]]


def guess_doc(units, content):
    """按内容二元组最大重叠判定分块归属文档。"""
    probe = norm(content)[:60]
    best, best_score = None, -1
    for doc in {u["doc"] for u in units}:
        doc_text = norm("".join(u["text"] for u in units if u["doc"] == doc))
        score = sum(1 for i in range(max(1, len(probe) - 1)) if probe[i:i + 2] in doc_text)
        if score > best_score:
            best, best_score = doc, score
    return best


def guess_section(units, doc, content):
    t = norm(content)
    best, best_score = None, -1
    for u in units:
        if u["doc"] != doc:
            continue
        ut = norm(u["text"])
        score = sum(1 for i in range(max(1, len(t) - 1)) if t[i:i + 2] in ut)
        if score > best_score:
            best, best_score = u["section"], score
    return best or "__unknown__"


def strategy_http(units, question, top_k, base_url, kb_id, token, timeout, allow_hosts):
    """线上策略：调用管理端检索测试接口，把命中分块映射回文档与章节。"""
    url = f"{base_url.rstrip('/')}/api/admin/knowledge-bases/{urllib.parse.quote(str(kb_id))}/test"
    validate_target(url, allow_hosts)
    body = http_post_json(url, {"question": question, "generateAnswer": False}, token, timeout)
    hits = (body.get("data") or {}).get("hits") or []
    known = {u["doc"] for u in units}
    results = []
    for h in hits:
        fname, content = h.get("fileName"), h.get("content", "")
        doc = fname if fname in known else guess_doc(units, content)
        section = guess_section(units, doc, content)
        results.append({"doc": doc, "section": section, "text": content,
                        "key": f"{doc}#{section}", "score": h.get("score"),
                        "rerankScore": h.get("rerankScore")})
    return results[:top_k]


# ---------------------------------------------------------------- 指标

def hit_of(retrieved, q):
    """判定是否命中金标：文档级命中 + 章节级命中，返回值区分层次。

    返回 "section"（文档与章节都对）、"doc"（文档对但章节未对上）、None（未命中）。
    区分层次是为了定位问题：文档级命中率低说明召回不够，命中但章节错说明排序不准。
    """
    if retrieved.get("doc") not in q["goldDocs"]:
        return None
    gold_sections = q.get("goldSections") or []
    if not gold_sections:
        return "section"
    sec, text = norm(retrieved.get("section") or ""), norm(retrieved.get("text") or "")
    for gs in gold_sections:
        ngs = norm(gs)
        if ngs in sec or ngs in text or (sec and sec in ngs):
            return "section"
        core = re.sub(r"[（(].*?[)）]", "", gs)
        if core and norm(core) in text:
            return "section"
    return "doc"


def dcg(gains):
    return sum(g / math.log2(i + 2) for i, g in enumerate(gains))


def evaluate(results_by_q, questions):
    """逐题打分。

    拒答题（expectRefusal=true）没有金标，不参与召回类指标的分母，
    否则会把"检索不到正确答案"与"本就不该有答案"混为一谈，污染整体指标。
    它们只在拒答相关的统计里体现。
    """
    rows = []
    for q in questions:
        retrieved = results_by_q.get(q["id"], [])
        if q.get("expectRefusal"):
            rows.append({
                "id": q["id"], "category": q["category"], "refusal": True,
                "retrieved_any": bool(retrieved),
                "top1_doc": retrieved[0].get("doc") if retrieved else None,
                "recall@1": None, "recall@3": None, "recall@5": None,
                "mrr": None, "ndcg@10": None, "distractor@1": None,
            })
            continue
        levels = [hit_of(r, q) for r in retrieved]
        gains = [1 if lv else 0 for lv in levels]
        doc_gains = [1 if lv in ("section", "doc") else 0 for lv in levels]
        first = next((i + 1 for i, g in enumerate(gains) if g), None)
        first_doc = next((i + 1 for i, g in enumerate(doc_gains) if g), None)
        ideal = dcg(sorted(gains[:10], reverse=True)) or 1.0
        rows.append({
            "id": q["id"], "category": q["category"], "refusal": False,
            "recall@1": 1.0 if gains[:1] and gains[0] else 0.0,
            "recall@3": 1.0 if sum(gains[:3]) else 0.0,
            "recall@5": 1.0 if sum(gains[:5]) else 0.0,
            "mrr": (1.0 / first) if first else 0.0,
            "ndcg@10": dcg(gains[:10]) / ideal,
            # 文档级：只看文档是否召回，不要求章节对上，用于分离召回与排序问题
            "doc_recall@5": 1.0 if sum(doc_gains[:5]) else 0.0,
            "doc_mrr": (1.0 / first_doc) if first_doc else 0.0,
            "top1_doc": retrieved[0]["doc"] if retrieved else None,
            "distractor@1": (1.0 if retrieved and q.get("distractors")
                             and retrieved[0].get("doc") in q["distractors"] else 0.0),
        })
    return rows


def summarize(rows):
    """聚合指标。召回类指标只在有金标的题上计算。"""
    scored = [r for r in rows if not r["refusal"]]
    refusals = [r for r in rows if r["refusal"]]
    total = len(scored)
    avg = lambda k: sum(r[k] for r in scored) / total if total else 0.0
    summary = {k: avg(k) for k in ("recall@1", "recall@3", "recall@5", "mrr",
                                   "ndcg@10", "distractor@1",
                                   "doc_recall@5", "doc_mrr")}
    summary["total"] = len(rows)
    summary["scored"] = total
    summary["refusal_count"] = len(refusals)
    by_cat = defaultdict(list)
    for r in scored:
        by_cat[r["category"]].append(r)
    summary["by_category"] = {
        c: {"n": len(rs), "recall@5": sum(x["recall@5"] for x in rs) / len(rs),
            "mrr": sum(x["mrr"] for x in rs) / len(rs),
            "doc_recall@5": sum(x["doc_recall@5"] for x in rs) / len(rs)}
        for c, rs in sorted(by_cat.items())}
    # 陷阱题 vs 常规题：拒答题已排除，二者才有可比性
    trap = [r for c, rs in by_cat.items() if c.startswith("trap") for r in rs]
    plain = [r for r in scored if not r["category"].startswith("trap")]
    if trap:
        summary["trap_recall@5"] = sum(r["recall@5"] for r in trap) / len(trap)
    if plain:
        summary["plain_recall@5"] = sum(r["recall@5"] for r in plain) / len(plain)
    return summary


def print_report(summary, rows):
    print("=" * 68)
    print("RAG 检索评测结果")
    print("=" * 68)
    print(f"题目总数: {summary['total']}"
          f"（计分 {summary['scored']} 题 + 拒答题 {summary['refusal_count']} 题）")
    print("\n章节级命中（文档与章节都需对上，最严格）:")
    for k in ("recall@1", "recall@3", "recall@5", "mrr", "ndcg@10"):
        print(f"  {k:<11}{summary[k]:.3f}")
    print("\n文档级命中（只要求召回到正确文档）:")
    print(f"  {'doc_R@5':<11}{summary['doc_recall@5']:.3f}")
    print(f"  {'doc_MRR':<11}{summary['doc_mrr']:.3f}")
    print(f"\n  干扰率@1     {summary['distractor@1']:.3f}   (越低越好)")
    if "plain_recall@5" in summary:
        print(f"\n  常规题 Recall@5   {summary['plain_recall@5']:.3f}")
        print(f"  陷阱题 Recall@5   {summary['trap_recall@5']:.3f}")
    print("\n分类明细（n 不含拒答题）:")
    for c, m in summary["by_category"].items():
        print(f"  {c:22s} n={m['n']:3d}  R@5={m['recall@5']:.3f}  "
              f"docR@5={m['doc_recall@5']:.3f}  MRR={m['mrr']:.3f}")
    misses = [r for r in rows if not r["refusal"] and r["recall@5"] == 0]
    if misses:
        print(f"\n未召回（前 5 名内无正确章节）{len(misses)} 题:")
        for r in misses:
            print(f"  {r['id']:6s} [{r['category']:20s}] top1={r['top1_doc']}")


def compare():
    """对比固定的 baseline.json 与 current.json。"""
    a = load_json(BASELINE_FILE)
    b = load_json(CURRENT_FILE)
    print("=" * 68)
    print("对比  A=baseline.json   B=current.json")
    print("=" * 68)
    print(f"{'指标':<14}{'A':>9}{'B':>9}{'变化':>11}")
    for k in ("recall@1", "recall@3", "recall@5", "mrr", "ndcg@10", "distractor@1"):
        av, bv = a.get(k, 0), b.get(k, 0)
        delta = bv - av
        arrow = "↑" if delta > 0.0005 else ("↓" if delta < -0.0005 else "=")
        print(f"{k:<14}{av:>9.3f}{bv:>9.3f}{delta:>+10.3f} {arrow}")
    ra = {r["id"]: r for r in a.get("rows", [])}
    rb = {r["id"]: r for r in b.get("rows", [])}
    regressed = [q for q in ra if q in rb and rb[q]["mrr"] < ra[q]["mrr"] - 1e-9]
    improved = [q for q in ra if q in rb and rb[q]["mrr"] > ra[q]["mrr"] + 1e-9]
    print(f"\n提升 {len(improved)} 题，退步 {len(regressed)} 题")
    if regressed:
        print("退步题目（需人工检查）:")
        for q in regressed:
            print(f"  {q}  MRR {ra[q]['mrr']:.2f} → {rb[q]['mrr']:.2f}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strategy", choices=["keyword", "http"], default="keyword")
    ap.add_argument("--top-k", type=int, default=10)
    ap.add_argument("--json", action="store_true", help="输出 JSON 到 stdout")
    ap.add_argument("--base-url", default="http://127.0.0.1:9000")
    ap.add_argument("--kb-id", default=None)
    ap.add_argument("--token", default=None)
    ap.add_argument("--timeout", type=int, default=30)
    ap.add_argument("--allow-host", action="append", default=[],
                    help="显式允许的非回环主机，可重复传入")
    ap.add_argument("--compare", action="store_true",
                    help="对比 baseline.json 与 current.json")
    args = ap.parse_args()

    if args.compare:
        compare()
        return

    try:
        eval_set = load_json(EVAL_SET_FILE)
        corpus = load_json(CORPUS_FILE)
        units = build_units(corpus)
    except ValueError as exc:
        print(f"错误：{exc}", file=sys.stderr)
        sys.exit(2)

    if args.strategy == "http" and not args.kb_id:
        print("错误：--strategy http 需要 --kb-id", file=sys.stderr)
        sys.exit(2)

    results_by_q = {}
    for q in eval_set["questions"]:
        try:
            if args.strategy == "keyword":
                results_by_q[q["id"]] = strategy_keyword(units, q["question"], args.top_k)
            else:
                results_by_q[q["id"]] = strategy_http(
                    units, q["question"], args.top_k, args.base_url,
                    args.kb_id, args.token, args.timeout, set(args.allow_host))
        except ValueError as exc:
            print(f"错误：{exc}", file=sys.stderr)
            sys.exit(2)

    rows = evaluate(results_by_q, eval_set["questions"])
    summary = summarize(rows)

    if args.json:
        print(json.dumps(dict(summary, rows=rows, strategy=args.strategy,
                              topK=args.top_k), ensure_ascii=False, indent=2))
    else:
        print_report(summary, rows)


if __name__ == "__main__":
    main()