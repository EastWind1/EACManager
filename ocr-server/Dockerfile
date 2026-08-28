FROM python:3.14-slim

ENV DEBIAN_FRONTEND=noninteractive \
    PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple \
    UV_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple

WORKDIR /app

RUN pip install --no-cache-dir uv

COPY pyproject.toml uv.lock README.md ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-install-project --no-dev

COPY src ./src
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev && \
    uv pip uninstall opencv-python && \
    uv pip install --no-cache opencv-python-headless

ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 9003

CMD ["ocr-server", "-ip", "0.0.0.0", "-p", "9003", "-workers", "2"]
