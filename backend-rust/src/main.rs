use billmanager::config::Config;
use billmanager::error::AppResult;
use billmanager::server::run;
use billmanager::state::AppState;

#[tokio::main]
async fn main() -> AppResult<()> {
    // 日志级别由 RUST_LOG 控制，默认只输出 info 以上
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
        )
        .init();

    let config = Config::load().map_err(billmanager::error::AppError::Internal)?;
    let state = AppState::new(config)
        .await
        .map_err(billmanager::error::AppError::Internal)?;
    run(state).await
}
