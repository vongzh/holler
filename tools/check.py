#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Holler 静态自检（无需 JDK/Android SDK）。
重点：剥离字符串字面量、行注释、块注释后再做括号配平，避免假阳性。

用法：python tools/check.py
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java")

# 这些导入靠「运算符约定」生效，名字不会出现在源码里，属正常保留。
# 例：Compose 的 `var x by remember {}` 依赖 getValue/setValue 运算符。
IMPORT_WHITELIST = {
    "getValue",
    "setValue",
    "provideDelegate",
    "component1",
    "component2",
    "component3",
    "component4",
    "component5",
}


def strip_code(text: str) -> str:
    """把字符串字面量 / 字符 / 注释替换为等长空白，保留换行以维持行号。"""
    out = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        # 行注释
        if ch == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
            continue
        # 块注释（Kotlin 支持嵌套）
        if ch == "/" and nxt == "*":
            depth = 1
            out.append(" ")
            out.append(" ")
            i += 2
            while i < n and depth > 0:
                if text[i] == "/" and i + 1 < n and text[i + 1] == "*":
                    depth += 1
                    out.append(" ")
                    out.append(" ")
                    i += 2
                elif text[i] == "*" and i + 1 < n and text[i + 1] == "/":
                    depth -= 1
                    out.append(" ")
                    out.append(" ")
                    i += 2
                else:
                    out.append("\n" if text[i] == "\n" else " ")
                    i += 1
            continue
        # 原始字符串 """..."""
        if ch == '"' and text.startswith('"""', i):
            out.append(" ")
            out.append(" ")
            out.append(" ")
            i += 3
            while i < n and not text.startswith('"""', i):
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append(" ")
            out.append(" ")
            out.append(" ")
            i += 3
            continue
        # 普通字符串
        if ch == '"':
            out.append(" ")
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    out.append(" ")
                    i += 1
                    if i < n:
                        out.append("\n" if text[i] == "\n" else " ")
                        i += 1
                    continue
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append(" ")
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def check_balance(path: str) -> list:
    text = open(path, encoding="utf-8").read()
    code = strip_code(text)
    errs = []
    for op, cl, name in (("{", "}", "花括号"), ("(", ")", "圆括号"), ("[", "]", "方括号")):
        a, b = code.count(op), code.count(cl)
        if a != b:
            errs.append(f"{name} {op}{cl} = {a}/{b}")
    return errs


def check_imports(path: str) -> list:
    text = open(path, encoding="utf-8").read()
    code = strip_code(text)
    errs = []
    for m in re.finditer(r"^import\s+([\w.]+)", code, re.M):
        fq = m.group(1)
        if fq.endswith(".*"):
            continue
        short = fq.split(".")[-1]
        if short in IMPORT_WHITELIST:
            continue
        # 统计剥离注释后的正文里 short 的出现次数
        body = code[: m.start()] + code[m.end():]
        if not re.search(r"\b" + re.escape(short) + r"\b", body):
            errs.append(f"未使用导入: {fq}")
    return errs


def check_dup_fun(path: str) -> list:
    text = open(path, encoding="utf-8").read()
    code = strip_code(text)
    names = re.findall(r"^\s*(?:private\s+|internal\s+|public\s+)?(?:suspend\s+)?fun\s+(\w+)", code, re.M)
    seen, dups = set(), []
    for nm in names:
        if nm in seen:
            dups.append(f"同名函数重复定义: {nm}")
        seen.add(nm)
    return dups


def main() -> int:
    files = []
    for dp, _, fns in os.walk(ROOT):
        for fn in fns:
            if fn.endswith(".kt"):
                files.append(os.path.join(dp, fn))
    files.sort()

    total_lines = 0
    bad = 0

    print("=" * 58)
    print(f"Holler 静态自检  ({len(files)} 个 .kt)")
    print("=" * 58)

    for f in files:
        rel = os.path.relpath(f, os.path.dirname(ROOT)).replace("\\", "/")
        lines = sum(1 for _ in open(f, encoding="utf-8"))
        total_lines += lines
        errs = check_balance(f) + check_imports(f) + check_dup_fun(f)
        if errs:
            bad += 1
            print(f"\n❌ {rel}  ({lines} 行)")
            for e in errs:
                print(f"     - {e}")

    print("\n" + "-" * 58)
    if bad == 0:
        print(f"✅ 全部通过：{len(files)} 个文件 / {total_lines} 行")
    else:
        print(f"⚠ {bad}/{len(files)} 个文件有问题，共 {total_lines} 行")
    print("-" * 58)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
