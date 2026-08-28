from io import BufferedReader, BytesIO
from pathlib import Path
from typing import Any, BinaryIO, cast

import pdfplumber

PdfSource = str | Path | BufferedReader | BytesIO


class PDFExecutor:
    """
    解析 PDF 文本
    若返回 True，表示存在文本，第二个返回值为文本行列表
    否则返回 False，第二个返回值为图片信息列表（pdfplumber 的 page.images 元素），供 OCR 处理
    """

    def __call__(self, path_or_fp: PdfSource | BinaryIO) -> tuple[bool, list[Any]]:
        source = cast(PdfSource, path_or_fp)

        texts: list[str] = []
        has_page = False
        with pdfplumber.open(source) as pdf:
            has_page = len(pdf.pages) > 0
            for page in pdf.pages:
                texts.extend(line["text"] for line in page.extract_text_lines())

        if len(texts) == 0 and has_page:
            images: list[dict[str, Any]] = []
            with pdfplumber.open(source) as pdf:
                for page in pdf.pages:
                    images.extend(page.images)
            return False, images
        return True, texts
