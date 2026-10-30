#!/usr/bin/env python3
"""按真实接口链路生成 App 演示数据。

默认连接本机 9000 端口的 dev 后端，使用 dev 账号：
requester / dispatcher / worker，密码 DevOnly123!。

链路：
  提交申请 -> 调度派单 -> 维修员开始 -> 现场/完工照片 -> 报修人确认 -> 评价
会生成四条申请：已提交、处理中、待验收、已办结并评价。

用法：
  python3 tools/seed_app_demo.py
  python3 tools/seed_app_demo.py --base-url http://127.0.0.1:9000
"""

from __future__ import annotations

import argparse
import base64
import sys
from datetime import datetime
from typing import Any

import requests

PASSWORD = "DevOnly123!"
# 1x1 PNG，用于真实文件上传；不会伪造外部图片 URL。
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk"
    "+A8AAQUBAScY42YAAAAASUVORK5CYII="
)


class DemoError(RuntimeError):
    pass


class Api:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def request(
        self,
        method: str,
        path: str,
        token: str | None = None,
        *,
        params: dict[str, Any] | None = None,
        json: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
        files: dict[str, Any] | None = None,
    ) -> Any:
        all_headers = {"Accept": "application/json"}
        if token:
            all_headers["Authorization"] = f"Bearer {token}"
        if headers:
            all_headers.update(headers)
        response = requests.request(
            method,
            self.base_url + path,
            params=params,
            json=json,
            headers=all_headers,
            files=files,
            timeout=30,
        )
        try:
            payload = response.json()
        except ValueError as exc:
            raise DemoError(
                f"{method} {path} 返回非 JSON（HTTP {response.status_code}）"
            ) from exc
        if not response.ok or payload.get("success") is not True:
            raise DemoError(
                f"{method} {path} 失败：HTTP {response.status_code} "
                f"{payload.get('code')} {payload.get('message')}"
            )
        return payload.get("data")

    def login(self, username: str) -> str:
        data = self.request(
            "POST",
            "/api/common/auth/login",
            json={"username": username, "password": PASSWORD},
        )
        token = (data or {}).get("accessToken")
        if not token:
            raise DemoError(f"{username} 登录没有返回 accessToken")
        return token

    def upload_png(self, token: str, filename: str) -> str:
        data = self.request(
            "POST",
            "/api/app/files",
            token,
            files={"file": (filename, PNG, "image/png")},
        )
        file_id = (data or {}).get("fileId")
        if not file_id:
            raise DemoError("文件上传没有返回 fileId")
        return str(file_id)


def find_item(services: list[dict[str, Any]], item_code: str) -> str:
    for category in services:
        for item in category.get("items", []):
            if item.get("itemCode") == item_code:
                return str(item["itemId"])
    raise DemoError(f"服务目录中找不到 {item_code}")


def upload_requester_photo(api: Api, token: str, name: str) -> list[str]:
    return [api.upload_png(token, f"{name}.png")]


def submit(
    api: Api,
    token: str,
    item_id: str,
    title: str,
    form: dict[str, Any],
    run_id: str,
    scenario: str,
) -> int:
    data = api.request(
        "POST",
        "/api/app/service-requests",
        token,
        headers={"Idempotency-Key": f"demo-{run_id}-{scenario}"},
        json={"itemId": item_id, "title": title, "formData": form},
    )
    request_id = (data or {}).get("requestId")
    if not request_id:
        raise DemoError(f"{scenario} 提交没有返回 requestId")
    return int(request_id)


def find_pending_order(api: Api, dispatcher_token: str, request_id: int) -> int:
    data = api.request(
        "GET",
        "/api/admin/work-orders",
        dispatcher_token,
        params={"status": "PENDING_DISPATCH", "pageNum": 1, "pageSize": 100},
    )
    for record in (data or {}).get("records", []):
        if int(record.get("requestId", -1)) == request_id:
            return int(record["orderId"])
    raise DemoError(f"派单队列找不到申请 {request_id} 的工单")


def dispatch(api: Api, dispatcher_token: str, request_id: int) -> int:
    order_id = find_pending_order(api, dispatcher_token, request_id)
    api.request(
        "POST",
        f"/api/admin/work-orders/{order_id}/assign",
        dispatcher_token,
        json={"assigneeId": 1003, "comment": "演示派单：请按预约时间上门"},
    )
    return order_id


def worker_start(api: Api, worker_token: str, order_id: int) -> None:
    api.request("POST", f"/api/app/work-orders/{order_id}/start", worker_token)


def add_photos(
    api: Api,
    worker_token: str,
    order_id: int,
    arrival: bool = True,
    completion: bool = True,
) -> None:
    if arrival:
        file_id = api.upload_png(worker_token, "现场照片.png")
        api.request(
            "POST",
            f"/api/app/work-orders/{order_id}/arrival-photo",
            worker_token,
            json={"fileId": int(file_id)},
        )
    if completion:
        file_id = api.upload_png(worker_token, "完工照片.png")
        api.request(
            "POST",
            f"/api/app/work-orders/{order_id}/completion-photo",
            worker_token,
            json={"fileId": int(file_id), "remark": "已处理完毕，现场清理完成"},
        )


def make_requests(api: Api, run_id: str) -> list[dict[str, Any]]:
    requester = api.login("requester")
    dispatcher = api.login("dispatcher")
    worker = api.login("worker")

    services = api.request("GET", "/api/app/services", requester)
    dorm_item = find_item(services, "REPAIR-DORM")
    public_item = find_item(services, "REPAIR-PUBLIC")

    results: list[dict[str, Any]] = []

    # 1) 已提交，等待调度
    form1 = {
        "contactPhone": "13800000001",
        "dormBuilding": "博宁社区-博梦1",
        "address": "302",
        "faultType": "空调",
        "description": "空调开机后只吹风不制冷，已经持续两天，希望尽快上门检查。",
        "photos": upload_requester_photo(api, requester, "空调故障"),
    }
    rid1 = submit(api, requester, dorm_item, "宿舍空调不制冷", form1, run_id, "submitted")
    results.append({"scenario": "已提交", "requestId": rid1, "orderId": None})

    # 2) 已派单并开始处理
    form2 = {
        "contactPhone": "13800000001",
        "dormBuilding": "博宁社区-博梦2",
        "address": "315",
        "faultType": "漏水",
        "description": "卫生间洗手台水龙头关不紧，持续滴水，地面已经有积水。",
        "photos": upload_requester_photo(api, requester, "水龙头漏水"),
    }
    rid2 = submit(api, requester, dorm_item, "宿舍水龙头漏水", form2, run_id, "processing")
    oid2 = dispatch(api, dispatcher, rid2)
    worker_start(api, worker, oid2)
    results.append({"scenario": "处理中", "requestId": rid2, "orderId": oid2})

    # 3) 已完工拍照，等待学生确认
    form3 = {
        "contactPhone": "13800000001",
        "address": "明德楼 (教学楼A栋) · 302 多媒体教室",
        "faultType": "多媒体设备",
        "description": "教室投影仪无法开机，电源指示灯不亮，上午第一节课需要使用。",
        "photos": upload_requester_photo(api, requester, "投影仪故障"),
    }
    rid3 = submit(api, requester, public_item, "教室投影仪无法开机", form3, run_id, "pending")
    oid3 = dispatch(api, dispatcher, rid3)
    worker_start(api, worker, oid3)
    add_photos(api, worker, oid3)
    results.append({"scenario": "待验收", "requestId": rid3, "orderId": oid3})

    # 4) 已确认完工并评价
    form4 = {
        "contactPhone": "13800000001",
        "dormBuilding": "博宁社区-博梦3",
        "address": "401",
        "faultType": "门窗",
        "description": "宿舍门锁松动，关门后仍有明显晃动，需要紧固或更换锁芯。",
        "photos": upload_requester_photo(api, requester, "门锁故障"),
    }
    rid4 = submit(api, requester, dorm_item, "宿舍门锁松动", form4, run_id, "completed")
    oid4 = dispatch(api, dispatcher, rid4)
    worker_start(api, worker, oid4)
    add_photos(api, worker, oid4)
    api.request(
        "POST",
        f"/api/app/work-orders/{oid4}/confirm",
        requester,
        json={},
    )
    api.request(
        "POST",
        f"/api/app/service-requests/{rid4}/evaluation",
        requester,
        json={
            "rating": 5,
            "tags": "响应神速,技术精湛,态度亲切",
            "comment": "师傅上门很快，检查仔细，处理完还把现场清理干净了。",
        },
    )
    results.append({"scenario": "已办结并评价", "requestId": rid4, "orderId": oid4})

    return results


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 App 真实链路演示数据")
    parser.add_argument("--base-url", default="http://127.0.0.1:9000")
    parser.add_argument(
        "--run-id",
        default=datetime.now().strftime("%Y%m%d-%H%M%S"),
        help="同一次运行的幂等键前缀",
    )
    args = parser.parse_args()
    api = Api(args.base_url)
    print(f"连接 {api.base_url}，run-id={args.run_id}")
    try:
        results = make_requests(api, args.run_id)
    except DemoError as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 1
    print("\n演示数据已生成：")
    for item in results:
        order = f" / 工单 {item['orderId']}" if item["orderId"] else ""
        print(f"  - {item['scenario']}: requestId={item['requestId']}{order}")
    print("\n用 requester 账号打开「我的申请」即可看到这四条记录。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
