# 静态链接产物，运行镜像只需 musl 基础库
FROM rust:1-slim AS build
WORKDIR /src

# 依赖单独一层，改代码不必重下 crates
COPY Cargo.toml Cargo.lock ./
RUN mkdir src && echo "fn main() {}" > src/main.rs && echo "" > src/lib.rs \
    && cargo build --release --locked \
    && rm -rf src

COPY src ./src
COPY config ./config
RUN touch src/main.rs src/lib.rs && cargo build --release --locked

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates libssl3 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/target/release/billmanager ./backend-rust
COPY config ./config
ENV ATTACHMENT_PATH=/app/attachment
EXPOSE 8080
CMD ["./backend-rust"]
