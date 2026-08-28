import argparse
import sys
from pathlib import Path
from typing import Any

import filetype
import uvicorn
from fastapi import FastAPI, Request, UploadFile
from fastapi.responses import JSONResponse
from PIL import Image
from starlette.formparsers import MultiPartParser
from uvicorn.config import LOGGING_CONFIG

from ocr_server.ocr import OCRAPIUtils
from ocr_server.pdf import PDFExecutor

MultiPartParser.max_part_size = 10 * 1024 * 1024  # 10MB
sys.path.append(str(Path(__file__).resolve().parent.parent))




def guess_mime(data: bytes) -> str | None:
    kind = filetype.guess(data)
    if kind is None:
        return None
    return str(kind.mime)


app = FastAPI()
ocr_execute = OCRAPIUtils()
pdf_execute = PDFExecutor()


@app.get("/")
def root() -> dict[str, str]:
    return {"message": "Welcome to RapidOCR API Server!"}


@app.post("/ocr")
async def ocr(file: UploadFile | None = None) -> dict[str, Any]:
    if not file:
        raise ValueError("file is null")
    file_byte = await file.read(2048)
    await file.seek(0)
    file_mime = guess_mime(file_byte)
    if file_mime is None:
        raise ValueError("file type not support")

    res: list[str]
    if file_mime.startswith("image/"):
        img = Image.open(file.file)
        res = ocr_execute(img)
    elif file_mime == "application/pdf":
        has_text, blocks = pdf_execute(file.file)
        if has_text:
            res = blocks
        elif len(blocks) > 0:
            # 降级为 OCR：blocks 为图片信息字典，取其存储的图片流重建 PIL 图像
            img = Image.open(blocks[0])
            res = ocr_execute(img)
        else:
            raise ValueError("no data")
    else:
        raise ValueError("file type not support")

    return {"code": 200, "msg": "success", "data": res}


@app.exception_handler(Exception)
async def global_exception_handler(_: Request, exc: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=500, content={"code": 500, "msg": str(exc), "data": None}
    )


def main() -> None:
    parser = argparse.ArgumentParser("ocr-server")
    parser.add_argument("-ip", "--ip", type=str, default="0.0.0.0", help="IP Address")
    parser.add_argument("-p", "--port", type=int, default=9003, help="IP port")
    parser.add_argument(
        "-workers", "--workers", type=int, default=1, help="number of worker process"
    )
    args = parser.parse_args()
    ip: str = str(args.ip)
    port: int = int(args.port)
    workers: int = int(args.workers)

    log_config = LOGGING_CONFIG
    log_config["formatters"]["access"]["fmt"] = "%(asctime)s %(levelname)s %(message)s"
    log_config["formatters"]["default"]["fmt"] = "%(asctime)s %(levelname)s %(message)s"

    uvicorn.run(
        "ocr_server:app",
        host=ip,
        port=port,
        reload=False,
        workers=workers,
        log_config=log_config,
    )


if __name__ == "__main__":
    main()
