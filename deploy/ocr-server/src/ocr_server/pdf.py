from io import BufferedReader, BytesIO
import pathlib
from typing import Union

import pdfplumber

class PDFExecutor:
    '''
    解析 PDF 文本
    若返回 True，表示存在文本，返回文本列表
    否则返回图片列表，供 OCR 处理
    '''
    def __call__(self, path_or_fp):
        
        texts = []
        has_page = False
        with pdfplumber.open(path_or_fp) as pdf:
            has_page = len(pdf.pages) > 0
            for page in pdf.pages:
                texts.extend([line['text'] for line in page.extract_text_lines()])
             
        if len(texts) == 0 and has_page:
            images = []
            with pdfplumber.open(path_or_fp) as pdf:
                for page in pdf.pages:
                    images.extend(page.images)
            return False, images
        else:
            return True, texts