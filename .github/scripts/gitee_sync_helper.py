#!/usr/bin/env python3
"""Gitee Release 同步辅助脚本。

GitHub Actions 里内联 python 多行字符串极易破坏 YAML 缩进，
因此把逻辑拆到独立脚本，工作流只负责传参调用。
"""
import json
import sys


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: gitee_sync_helper.py <command>", file=sys.stderr)
        return 2
    cmd = sys.argv[1]

    if cmd == "release-id":
        # 从 stdin 读 Gitee release JSON，输出 id（失败输出空）
        try:
            d = json.load(sys.stdin)
            print(d.get("id", "") if isinstance(d, dict) else "")
        except Exception:
            print("")
        return 0

    if cmd == "attach-names":
        # 从 stdin 读附件列表，每行一个 name
        try:
            d = json.load(sys.stdin)
            if isinstance(d, list):
                for item in d:
                    name = item.get("name", "")
                    if name:
                        print(name)
        except Exception:
            pass
        return 0

    if cmd == "attach-count":
        try:
            d = json.load(sys.stdin)
            print(len(d) if isinstance(d, list) else 0)
        except Exception:
            print(0)
        return 0

    if cmd == "content-sha":
        try:
            d = json.load(sys.stdin)
            print(d.get("sha", "") if isinstance(d, dict) else "")
        except Exception:
            print("")
        return 0

    if cmd == "version":
        try:
            print(json.load(sys.stdin).get("version", ""))
        except Exception:
            print("")
        return 0

    if cmd == "create-payload":
        import os

        payload = {
            "access_token": os.environ.get("GITEE_TOKEN", ""),
            "tag_name": os.environ.get("TAG_NAME", ""),
            "target_commitish": "main",
            "name": os.environ.get("TAG_NAME", ""),
            "body": os.environ.get("BODY", ""),
            "prerelease": False,
        }
        print(json.dumps(payload))
        return 0

    if cmd == "version-payload":
        import base64
        import os

        ref = os.environ.get("TAG_NAME", "")
        version = {
            "version": ref.lstrip("v"),
            "notes": os.environ.get("NOTES", ""),
            "apk": "https://gitee.com/TDSI/fanhua-camera/releases/download/{}/{}".format(
                ref, os.environ.get("APK_NAME", "")
            ),
        }
        body = {
            "access_token": os.environ.get("GITEE_TOKEN", ""),
            "message": "update version channel to {}".format(ref),
            "content": base64.b64encode(
                json.dumps(version, ensure_ascii=False, indent=2).encode("utf-8")
            ).decode(),
            "branch": "master",
        }
        sha = os.environ.get("SHA", "")
        if sha:
            body["sha"] = sha
        print(json.dumps(body))
        return 0

    print("unknown command: {}".format(cmd), file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
