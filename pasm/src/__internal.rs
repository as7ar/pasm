use std::alloc::Layout;
use std::cell::{Cell, UnsafeCell};

pub const ABI_VERSION: i32 = 1;

pub const LOG_DEBUG: i32 = 0;
pub const LOG_INFO: i32 = 1;
pub const LOG_WARN: i32 = 2;
pub const LOG_ERROR: i32 = 3;

pub const STATUS_OK: i32 = 0;
pub const STATUS_REENTRANT: i32 = 2;
pub const STATUS_ALREADY_ENABLED: i32 = 3;
pub const STATUS_NOT_ENABLED: i32 = 4;

/// Default no-op lifecycle hooks. Inherent methods defined by the plugin take priority.
pub trait LifecycleDefaults {
    fn on_enabled(&mut self) {}
    fn on_disabled(&mut self) {}
}

impl<T> LifecycleDefaults for T {}

/// Single-threaded global storage for the plugin instance of one WASM module.
pub struct Slot<T> {
    value: UnsafeCell<Option<T>>,
    busy: Cell<bool>,
}

unsafe impl<T> Sync for Slot<T> {}

impl<T> Slot<T> {
    pub const fn new() -> Self {
        Self {
            value: UnsafeCell::new(None),
            busy: Cell::new(false),
        }
    }
}

impl<T> Default for Slot<T> {
    fn default() -> Self {
        Self::new()
    }
}

/// Creates the plugin instance and runs `on_enabled`. Returns a status code.
pub fn plugin_enable<T: Default>(slot: &Slot<T>, on_enabled: fn(&mut T)) -> i32 {
    if slot.busy.replace(true) {
        return STATUS_REENTRANT;
    }
    install_panic_hook();
    let value = unsafe { &mut *slot.value.get() };
    let status = match value {
        Some(_) => STATUS_ALREADY_ENABLED,
        None => {
            on_enabled(value.insert(T::default()));
            STATUS_OK
        }
    };
    slot.busy.set(false);
    status
}

/// Runs `on_disabled` and drops the plugin instance. Returns a status code.
pub fn plugin_disable<T>(slot: &Slot<T>, on_disabled: fn(&mut T)) -> i32 {
    if slot.busy.replace(true) {
        return STATUS_REENTRANT;
    }
    let value = unsafe { &mut *slot.value.get() };
    let status = match value {
        None => STATUS_NOT_ENABLED,
        Some(instance) => {
            on_disabled(instance);
            *value = None;
            STATUS_OK
        }
    };
    slot.busy.set(false);
    status
}

/// Allocates `len` bytes in guest memory for the host. Returns 0 on failure.
pub fn alloc(len: u32) -> u32 {
    let Ok(layout) = Layout::from_size_align(len.max(1) as usize, 1) else {
        return 0;
    };
    let ptr = unsafe { std::alloc::alloc(layout) };
    if ptr.is_null() {
        return 0;
    }
    match u32::try_from(ptr as usize) {
        Ok(address) => address,
        Err(_) => {
            unsafe { std::alloc::dealloc(ptr, layout) };
            0
        }
    }
}

/// Releases memory previously returned by `alloc`.
pub fn dealloc(ptr: u32, len: u32) {
    if ptr == 0 {
        return;
    }
    let Ok(layout) = Layout::from_size_align(len.max(1) as usize, 1) else {
        return;
    };
    unsafe { std::alloc::dealloc(ptr as usize as *mut u8, layout) };
}

/// Packs a static string into `(ptr << 32) | len` for returning it to the host.
pub fn pack_static_str(value: &'static str) -> u64 {
    ((value.as_ptr() as usize as u64) << 32) | value.len() as u64
}

fn install_panic_hook() {
    std::panic::set_hook(Box::new(|info| {
        host_log(LOG_ERROR, &format!("panic: {info}"));
    }));
}

#[cfg(target_arch = "wasm32")]
#[link(wasm_import_module = "pasm")]
extern "C" {
    #[link_name = "log"]
    fn host_log_import(level: i32, ptr: *const u8, len: i32) -> i32;
}

/// Sends one log line to the PASM host.
#[cfg(target_arch = "wasm32")]
pub fn host_log(level: i32, message: &str) {
    let _ = unsafe { host_log_import(level, message.as_ptr(), message.len() as i32) };
}

/// Captures log lines in memory so plugins can be tested on the native target.
#[cfg(not(target_arch = "wasm32"))]
pub fn host_log(level: i32, message: &str) {
    CAPTURED_LOGS.with(|logs| logs.borrow_mut().push((level, message.to_owned())));
}

#[cfg(not(target_arch = "wasm32"))]
thread_local! {
    static CAPTURED_LOGS: std::cell::RefCell<Vec<(i32, String)>> = const { std::cell::RefCell::new(Vec::new()) };
}

/// Drains the log lines captured on the current thread (native target only).
#[cfg(not(target_arch = "wasm32"))]
pub fn take_captured_logs() -> Vec<(i32, String)> {
    CAPTURED_LOGS.with(|logs| std::mem::take(&mut *logs.borrow_mut()))
}
