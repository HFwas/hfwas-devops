#!/usr/bin/env python3
"""把 PP-OCRv6 ONNX 准备到 file-parser resources，随应用打包离线加载。

默认目录：backend/file-parser/src/main/resources/ocr/models/
已存在则跳过；否则优先从 ~/.paddlex 或 ~/.hfwas-devops 复制，再不行才联网下载。
"""
from __future__ import annotations

import argparse
import os
import shutil
import sys
from pathlib import Path

DET_NAME = "PP-OCRv6_medium_det_onnx"
REC_NAME = "PP-OCRv6_medium_rec_onnx"
DET_REPO = f"PaddlePaddle/{DET_NAME}"
REC_REPO = f"PaddlePaddle/{REC_NAME}"
REQUIRED = ("inference.onnx", "inference.yml")
COPY_FILES = ("inference.onnx", "inference.yml", "inference.json")


def default_root() -> Path:
    override = os.environ.get("FILE_PARSER_OCR_MODEL_ROOT", "").strip()
    if override:
        return Path(override).expanduser()
    return (
        Path(__file__).resolve().parent.parent
        / "file-parser"
        / "src"
        / "main"
        / "resources"
        / "ocr"
        / "models"
    )


def model_ready(directory: Path) -> bool:
    return all((directory / name).is_file() for name in REQUIRED)


def copy_model_files(src: Path, dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    for name in COPY_FILES:
        file = src / name
        if file.is_file():
            shutil.copy2(file, dest / name)


def download(repo_id: str, dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    from huggingface_hub import snapshot_download

    snapshot_download(repo_id=repo_id, local_dir=str(dest))


def candidate_sources(name: str) -> list[Path]:
    home = Path.home()
    return [
        home / ".paddlex" / "official_models" / name,
        home / ".hfwas-devops" / "models" / "ppocrv6" / name,
    ]


def ensure_one(name: str, repo_id: str, dest: Path) -> None:
    if model_ready(dest):
        print(f"OCR 模型已存在: {dest}", file=sys.stderr)
        return

    for src in candidate_sources(name):
        if model_ready(src):
            print(f"复制 {src} -> {dest}", file=sys.stderr)
            copy_model_files(src, dest)
            return

    print(f"下载 {repo_id} -> {dest}（仅首次，之后离线）", file=sys.stderr)
    download(repo_id, dest)
    if not model_ready(dest):
        raise SystemExit(f"模型下载不完整: {dest}，需要 {', '.join(REQUIRED)}")


def main() -> int:
    parser = argparse.ArgumentParser(description="准备 resources 内的 PP-OCRv6 ONNX 模型")
    parser.add_argument("--check", action="store_true", help="只检查，不下载")
    args = parser.parse_args()

    root = default_root()
    det = Path(os.environ.get("FILE_PARSER_OCR_DET_MODEL_DIR", root / DET_NAME)).expanduser()
    rec = Path(os.environ.get("FILE_PARSER_OCR_REC_MODEL_DIR", root / REC_NAME)).expanduser()

    if args.check:
        if model_ready(det) and model_ready(rec):
            print(det)
            print(rec)
            return 0
        print(f"resources 中 OCR 模型缺失: det={det} rec={rec}", file=sys.stderr)
        return 1

    ensure_one(DET_NAME, DET_REPO, det)
    ensure_one(REC_NAME, REC_REPO, rec)
    print(det)
    print(rec)
    return 0


if __name__ == "__main__":
    sys.exit(main())
