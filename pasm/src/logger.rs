use crate::__internal::{host_log, LOG_DEBUG, LOG_ERROR, LOG_INFO, LOG_WARN};

/// Writes a debug message to the server console. Hidden by default on Paper.
pub fn debug(message: impl AsRef<str>) {
    host_log(LOG_DEBUG, message.as_ref());
}

/// Writes an info message to the server console.
pub fn info(message: impl AsRef<str>) {
    host_log(LOG_INFO, message.as_ref());
}

/// Writes a warning message to the server console.
pub fn warn(message: impl AsRef<str>) {
    host_log(LOG_WARN, message.as_ref());
}

/// Writes an error message to the server console.
pub fn error(message: impl AsRef<str>) {
    host_log(LOG_ERROR, message.as_ref());
}
