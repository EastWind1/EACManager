use axum::Router;
use axum::routing::{delete, get, post, put};

use crate::state::AppState;

pub mod handler;
pub mod model;
pub mod service;
pub mod store;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/api/company/", get(handler::get_all))
        .route("/api/company/", post(handler::create))
        .route("/api/company/", put(handler::update))
        .route("/api/company/{id}", delete(handler::disable))
}
