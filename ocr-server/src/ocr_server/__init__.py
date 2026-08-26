import argparse
import sys
from pathlib import Path
from typing import Optional
import filetype


from fastapi.responses import JSONResponse
import uvicorn
from fastapi import FastAPI, Form, Request, UploadFile
from PIL import Image

from starlette.formparsers import MultiPartParser

from ocr_server.ocr import OCRAPIUtils
from ocr_server.pdf import PDFExecutor

MultiPartParser.max_part_size = 10 * 1024 * 1024  # 10MB
MultiPartParser.max_file_size = 20 * 1024 * 1024   # 20MB
sys.path.append(str(Path(__file__).resolve().parent.parent))


app = FastAPI()
ocr_execute = OCRAPIUtils()
pdf_execute = PDFExecutor()


@app.get("/")
def root():
    return {"message": "Welcome to RapidOCR API Server!"}


@app.post("/ocr")
async def ocr(
        file: Optional[UploadFile] = None,
):
    if not file:
        raise ValueError('file is null')
    file_byte = await file.read(2048)
    await file.seek(0)
    file_type = filetype.guess(file_byte)
    file_mime = file_type.mime
    if file_mime.startswith("image/"):
        img = Image.open(file.file)
        res = ocr_execute(img)
    elif file_mime == "application/pdf":
        has_text, blocks = pdf_execute(file.file)
        if has_text:
            res = blocks
        elif len(blocks) > 0:
            # 降级为 OCR
            img = Image.open(blocks[0])
            res = ocr_execute(img)
        else:
            raise ValueError('no data')
    else:
        raise ValueError('file type not support')

    return res

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={
            "code": 500,
            "msg": str(exc),
            "data": None
        }
    )

def main():
    parser = argparse.ArgumentParser("ocr-server")
    parser.add_argument("-ip", "--ip", type=str, default="0.0.0.0", help="IP Address")
    parser.add_argument("-p", "--port", type=int, default=9003, help="IP port")
    parser.add_argument(
        "-workers", "--workers", type=int, default=1, help="number of worker process"
    )
    args = parser.parse_args()

    # 修改 uvicorn 的默认日志配置
    log_config = uvicorn.config.LOGGING_CONFIG
    log_config["formatters"]["access"]["fmt"] = "%(asctime)s %(levelname)s %(message)s"
    log_config["formatters"]["default"]["fmt"] = "%(asctime)s %(levelname)s %(message)s"

    uvicorn.run(
        "ocr_server:app",
        host=args.ip,
        port=args.port,
        reload=False,
        workers=args.workers,
        log_config=log_config,
    )


if __name__ == "__main__":
    main()
