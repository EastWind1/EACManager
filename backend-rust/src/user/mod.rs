use axum::Router;
use axum::routing::{get, post, put};

use crate::state::AppState;

pub mod handler;
pub mod model;
pub mod service;
pub mod store;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/api/user/token", post(handler::login))
        .route(
            "/api/user/initialization",
            get(handler::initialization_status),
        )
        .route("/api/user/initialization", post(handler::initialize))
        .route("/api/user/logout", put(handler::logout))
        .route("/api/user/", get(handler::get_all))
        .route("/api/user/", post(handler::create))
        .route("/api/user/", put(handler::update))
}
