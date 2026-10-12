use pasm::*;

#[plugin(name = "HelloPlugin", authors = ["AS7AR"], version = "0.1.0")]
struct HelloPlugin {
    enable_count: u32,
}

impl HelloPlugin {
    fn on_enabled(&mut self) {
        self.enable_count += 1;
        logger::info(format!(
            "HelloPlugin enabled (count = {})",
            self.enable_count
        ));
        logger::warn("this is a warning from Rust");
        logger::error("this is an error from Rust");
    }

    fn on_disabled(&mut self) {
        logger::info(format!(
            "HelloPlugin disabled (count = {})",
            self.enable_count
        ));
    }
}
