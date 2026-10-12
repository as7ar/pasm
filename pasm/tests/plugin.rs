use pasm::__internal::take_captured_logs;
use pasm::*;

#[plugin(name = "TestPlugin", authors = ["A", "B"], version = "1.2.3")]
struct TestPlugin {
    value: u32,
}

impl TestPlugin {
    fn on_enabled(&mut self) {
        self.value = 7;
        logger::info(format!("enabled {}", self.value));
    }

    fn on_disabled(&mut self) {
        logger::warn(format!("disabled {}", self.value));
    }
}

#[test]
fn lifecycle_runs_hooks_and_keeps_state() {
    assert_eq!(__pasm_abi_version(), 1);
    assert_eq!(__pasm_plugin_disable(), 4);
    assert_eq!(__pasm_plugin_enable(), 0);
    assert_eq!(__pasm_plugin_enable(), 3);
    assert_eq!(__pasm_plugin_disable(), 0);
    assert_eq!(__pasm_plugin_disable(), 4);
    assert_eq!(
        take_captured_logs(),
        vec![(1, "enabled 7".to_string()), (2, "disabled 7".to_string())]
    );
}

#[test]
fn metadata_has_expected_length() {
    let expected = "{\"kind\":\"plugin\",\"abi\":1,\"name\":\"TestPlugin\",\"authors\":[\"A\",\"B\"],\"version\":\"1.2.3\"}";
    assert_eq!(__pasm_meta_plugin() & 0xFFFF_FFFF, expected.len() as u64);
}
