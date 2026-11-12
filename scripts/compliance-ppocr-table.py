"""Recognize a scanned table page with installed local PP-OCRv5 ONNX models."""
import sys
from pathlib import Path

from rapidocr import ModelType, OCRVersion, RapidOCR


def main():
    image = Path(sys.argv[1])
    models = Path(__file__).resolve().parent / "models"
    detector = models / "ch_PP-OCRv5_det_server.onnx"
    recognizer = models / "ch_PP-OCRv5_rec_server.onnx"
    if not detector.is_file() or not recognizer.is_file():
        raise FileNotFoundError("Local PP-OCRv5 models are missing")

    engine = RapidOCR(params={
        "Global.use_cls": False,
        "Global.text_score": 0.5,
        "Det.model_path": str(detector),
        "Det.ocr_version": OCRVersion.PPOCRV5,
        "Det.model_type": ModelType.SERVER,
        "Rec.model_path": str(recognizer),
        "Rec.ocr_version": OCRVersion.PPOCRV5,
        "Rec.model_type": ModelType.SERVER,
        "EngineConfig.onnxruntime.intra_op_num_threads": 2,
        "EngineConfig.onnxruntime.inter_op_num_threads": 1,
    })
    result = engine(image.read_bytes())
    if result.boxes is None:
        return
    for index, (box, text, score) in enumerate(zip(result.boxes, result.txts, result.scores), 1):
        left, top = (int(value) for value in box.min(axis=0))
        right, bottom = (int(value) for value in box.max(axis=0))
        text = " ".join(text.split())
        print(f"5\t1\t1\t1\t{index}\t1\t{left}\t{top}\t{right-left}\t{bottom-top}\t{score*100:.2f}\t{text}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
