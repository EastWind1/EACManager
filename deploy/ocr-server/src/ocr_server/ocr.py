import os
from typing import Dict
from PIL import Image
import numpy as np

from rapidocr import RapidOCR

class OCRAPIUtils:
    def __init__(self) -> None:
        det_model_path = os.getenv("det_model_path", None)
        cls_model_path = os.getenv("cls_model_path", None)
        rec_model_path = os.getenv("rec_model_path", None)

        if det_model_path is None or cls_model_path is None or rec_model_path is None:
            self.ocr = RapidOCR()
        else:
            self.ocr = RapidOCR(
                params={
                    "Det.model_path": det_model_path,
                    "Cls.model_path": cls_model_path,
                    "Rec.model_path": rec_model_path,
                }
            )

    def __call__(
            self, ori_img: Image.Image, use_det=None, use_cls=False, use_rec=None, **kwargs
    ) -> Dict:
        img = np.array(ori_img)
        ocr_res = self.ocr(
            img, use_det=use_det, use_cls=use_cls, use_rec=use_rec, **kwargs
        )

        if ocr_res.txts is None:
            return []

        return list(ocr_res.txts)