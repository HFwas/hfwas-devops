#!/usr/bin/env python3
"""常驻 PP-OCRv6 worker。stdout 只输出一行一条 JSON，日志走 stderr。

协议：
  启动成功: {"ok": true, "event": "ready"}
  识别请求: {"id": "1", "image": "/path/to.png"}
  识别响应: {"id": "1", "ok": true, "text": "...", "confidence": 0.9}
  退出:     {"cmd": "shutdown"}
"""
from __future__ import annotations

import json
import os
import sys
import traceback
from pathlib import Path

# PaddleOCR 3.x / modelscope 在 Python 3.13 上未设置该变量会崩溃。
os.environ.setdefault("HUB_DATASET_ENDPOINT", "https://modelscope.cn/api/v1/datasets")
os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")

DET_NAME = "PP-OCRv6_medium_det_onnx"
REC_NAME = "PP-OCRv6_medium_rec_onnx"
DEFAULT_MODEL_ROOT = Path(__file__).resolve().parent / "models"


def log(msg: str) -> None:
    print(msg, file=sys.stderr, flush=True)


def _model_dir(env_name: str, default_name: str) -> str:
    override = os.environ.get(env_name, "").strip()
    path = Path(override).expanduser() if override else DEFAULT_MODEL_ROOT / default_name
    onnx = path / "inference.onnx"
    yml = path / "inference.yml"
    if not onnx.is_file() or not yml.is_file():
        raise RuntimeError(
            f"本地 OCR 模型不存在: {path}。"
            "请将 ONNX 放到 file-parser/src/main/resources/ocr/models/，或运行 backend/scripts/prepare_ocr_models.py。"
        )
    return str(path)


def load_engine():
    det = _model_dir("FILE_PARSER_OCR_DET_MODEL_DIR", DET_NAME)
    rec = _model_dir("FILE_PARSER_OCR_REC_MODEL_DIR", REC_NAME)
    try:
        from paddleocr import PaddleOCR
    except ImportError as e:
        raise RuntimeError("需要 PaddleOCR 3.7.0: pip install paddleocr==3.7.0") from e

    # Python 3.13 没有官方 paddlepaddle wheel；3.7 可用 onnxruntime 跑 PP-OCRv6。
    engine = PaddleOCR(
        text_detection_model_name="PP-OCRv6_medium_det",
        text_detection_model_dir=det,
        text_recognition_model_name="PP-OCRv6_medium_rec",
        text_recognition_model_dir=rec,
        device="cpu",
        engine="onnxruntime",
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
    )
    log(f"using PaddleOCR 3.7 PP-OCRv6 (onnxruntime) det={det} rec={rec}")
    return engine


def _as_list(value) -> list:
    if value is None:
        return []
    if hasattr(value, "tolist"):
        value = value.tolist()
    return list(value)


def recognize(engine, image_path: str) -> tuple[str, float]:
    outputs = engine.predict(image_path)
    texts, scores = [], []
    for item in outputs or []:
        rec_texts = rec_scores = None
        if isinstance(item, dict) or hasattr(item, "get"):
            rec_texts = item.get("rec_texts")
            rec_scores = item.get("rec_scores")
        if rec_texts is None:
            rec_texts = getattr(item, "rec_texts", None)
            rec_scores = getattr(item, "rec_scores", None)
        texts.extend(t for t in _as_list(rec_texts) if t)
        scores.extend(float(s) for s in _as_list(rec_scores))
    text = "\n".join(texts)
    conf = sum(scores) / len(scores) if scores else 0.0
    return text, conf


def emit(payload: dict) -> None:
    sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def main() -> int:
    try:
        engine = load_engine()
    except Exception as e:
        log(traceback.format_exc())
        emit({"ok": False, "event": "ready", "error": str(e)})
        return 1

    emit({"ok": True, "event": "ready"})
    for raw in sys.stdin:
        line = raw.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except json.JSONDecodeError as e:
            emit({"ok": False, "id": None, "text": "", "confidence": 0.0, "error": str(e)})
            continue
        if req.get("cmd") == "shutdown":
            return 0
        req_id = req.get("id")
        image = req.get("image")
        try:
            text, conf = recognize(engine, image)
            emit({"id": req_id, "ok": True, "text": text, "confidence": conf})
        except Exception as e:
            log(traceback.format_exc())
            emit({"id": req_id, "ok": False, "text": "", "confidence": 0.0, "error": str(e)})
    return 0


if __name__ == "__main__":
    sys.exit(main())
