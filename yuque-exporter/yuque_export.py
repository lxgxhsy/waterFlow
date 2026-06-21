#!/usr/bin/env python3
"""
语雀全量文档导出工具
依赖: pip install requests html2text
用法: python yuque_export.py --token YOUR_TOKEN
      或设置环境变量 YUQUE_TOKEN=xxx
"""

import argparse
import os
import sys
import time
import re
from pathlib import Path

try:
    import requests
    import html2text
except ImportError:
    print("缺少依赖，请先运行: pip install requests html2text")
    sys.exit(1)

BASE_URL = "https://www.yuque.com/api/v2"
RATE_LIMIT_DELAY = 0.5  # seconds between requests


def make_safe_filename(name: str) -> str:
    return re.sub(r'[\\/:*?"<>|]', "_", name).strip()


class YuqueExporter:
    def __init__(self, token: str, output_dir: str = "yuque_export"):
        self.headers = {
            "X-Auth-Token": token,
            "Content-Type": "application/json",
            "User-Agent": "YuqueExporter/1.0",
        }
        self.output_dir = Path(output_dir)
        self.h2t = html2text.HTML2Text()
        self.h2t.ignore_links = False
        self.h2t.body_width = 0  # no line wrapping
        self.h2t.ignore_images = False

    def _get(self, path: str, params: dict = None) -> dict:
        url = f"{BASE_URL}{path}"
        r = requests.get(url, headers=self.headers, params=params, timeout=30)
        r.raise_for_status()
        time.sleep(RATE_LIMIT_DELAY)
        return r.json()

    def get_user(self) -> dict:
        return self._get("/user")["data"]

    def get_repos(self, login: str) -> list:
        repos = []
        offset = 0
        while True:
            data = self._get(f"/users/{login}/repos", {"offset": offset, "limit": 100, "type": "Book"})["data"]
            if not data:
                break
            repos.extend(data)
            offset += len(data)
            if len(data) < 100:
                break
        return repos

    def get_docs(self, namespace: str) -> list:
        return self._get(f"/repos/{namespace}/docs")["data"]

    def get_doc_content(self, namespace: str, slug: str) -> dict:
        return self._get(f"/repos/{namespace}/docs/{slug}")["data"]

    def export_doc(self, namespace: str, doc: dict, dest_dir: Path):
        slug = doc["slug"]
        title = doc["title"] or slug
        safe_title = make_safe_filename(title)
        filepath = dest_dir / f"{safe_title}.md"

        if filepath.exists():
            print(f"    跳过 (已存在): {title}")
            return

        try:
            full = self.get_doc_content(namespace, slug)
            body_html = full.get("body_html", "")

            if body_html:
                markdown = self.h2t.handle(body_html)
            else:
                markdown = full.get("body", "")

            with open(filepath, "w", encoding="utf-8") as f:
                f.write(f"# {title}\n\n")
                f.write(markdown.strip())
                f.write("\n")

            print(f"    ✓ {title}")
        except Exception as e:
            print(f"    ✗ {title} — 错误: {e}")

    def export_repo(self, repo: dict):
        name = repo["name"]
        namespace = repo["namespace"]
        repo_dir = self.output_dir / make_safe_filename(name)
        repo_dir.mkdir(parents=True, exist_ok=True)

        print(f"\n[知识库] {name}  ({namespace})")

        try:
            docs = self.get_docs(namespace)
        except Exception as e:
            print(f"  获取文档列表失败: {e}")
            return

        print(f"  共 {len(docs)} 篇文档")
        for doc in docs:
            self.export_doc(namespace, doc, repo_dir)

    def run(self):
        self.output_dir.mkdir(parents=True, exist_ok=True)

        print("正在获取用户信息...")
        user = self.get_user()
        login = user["login"]
        print(f"已登录: {user['name']} (@{login})\n")

        print("正在获取知识库列表...")
        repos = self.get_repos(login)
        print(f"共找到 {len(repos)} 个知识库")

        for repo in repos:
            self.export_repo(repo)

        print(f"\n✅ 导出完成！文件保存在: {self.output_dir.resolve()}")


def main():
    parser = argparse.ArgumentParser(description="语雀文档全量导出工具")
    parser.add_argument("--token", default=os.environ.get("YUQUE_TOKEN"), help="语雀个人 Token")
    parser.add_argument("--output", default="yuque_export", help="输出目录 (默认: yuque_export)")
    parser.add_argument("--namespace", help="只导出指定知识库，如 username/book-slug")
    args = parser.parse_args()

    if not args.token:
        print("错误: 请提供语雀 Token")
        print("  方式1: python yuque_export.py --token YOUR_TOKEN")
        print("  方式2: export YUQUE_TOKEN=YOUR_TOKEN")
        print("\n获取 Token: 语雀个人设置 → 账户 → Token")
        sys.exit(1)

    exporter = YuqueExporter(args.token, args.output)

    if args.namespace:
        # Single repo mode
        repo_mock = {"name": args.namespace.split("/")[-1], "namespace": args.namespace}
        exporter.output_dir.mkdir(parents=True, exist_ok=True)
        exporter.export_repo(repo_mock)
        print(f"\n✅ 导出完成！文件保存在: {exporter.output_dir.resolve()}")
    else:
        exporter.run()


if __name__ == "__main__":
    main()
