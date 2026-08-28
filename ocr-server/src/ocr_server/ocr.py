import os
from typing import cast

import numpy as np
from PIL import Image
from rapidocr import RapidOCR
from rapidocr.utils.output import RapidOCROutput


class OCRAPIUtils:
    def __init__(self) -> None:
        det_model_path = os.getenv("det_model_path")
        cls_model_path = os.getenv("cls_model_path")
        rec_model_path = os.getenv("rec_model_path")

        if det_model_path is None or cls_model_path is None or rec_model_path is None:
            self.ocr: RapidOCR = RapidOCR()
        else:
            self.ocr = RapidOCR(
                params={
                    "Det.model_path": det_model_path,
                    "Cls.model_path": cls_model_path,
                    "Rec.model_path": rec_model_path,
                }
            )

    def __call__(
        self,
        ori_img: Image.Image,
        use_det: bool | None = None,
        use_cls: bool = False,
        use_rec: bool | None = None,
    ) -> list[str]:
        img = np.array(ori_img)
        # det+rec 全流程时返回 RapidOCROutput，联合类型中的其他分支仅用于单项调用
        ocr_res = cast(
            RapidOCROutput,
            self.ocr(img, use_det=use_det, use_cls=use_cls, use_rec=use_rec),
        )

        if ocr_res.txts is None:
            return []

        return list(ocr_res.txts)
