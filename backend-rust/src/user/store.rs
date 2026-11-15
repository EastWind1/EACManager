// 用户资料的读写统一收在 security::store，user 模块不再单独维护仓储。
pub use crate::security::store::{ProfileRow, find_profile, find_profiles};
