use proc_macro::TokenStream;
use proc_macro2::TokenStream as TokenStream2;
use quote::quote;
use syn::spanned::Spanned;
use syn::{parse_macro_input, Error, Expr, ExprArray, ExprLit, ItemStruct, Lit, LitStr};

struct PluginArgs {
    name: LitStr,
    version: LitStr,
    authors: Vec<LitStr>,
}

/// Marks a struct as the plugin entry point and generates the WASM exports, lifecycle glue and metadata.
#[proc_macro_attribute]
pub fn plugin(args: TokenStream, input: TokenStream) -> TokenStream {
    let item = parse_macro_input!(input as ItemStruct);
    match expand_plugin(args.into(), item) {
        Ok(tokens) => tokens.into(),
        Err(error) => error.to_compile_error().into(),
    }
}

fn expand_plugin(args: TokenStream2, item: ItemStruct) -> syn::Result<TokenStream2> {
    let args = parse_plugin_args(args)?;
    if !item.generics.params.is_empty() {
        return Err(Error::new(
            item.generics.span(),
            "#[plugin] does not support generic structs",
        ));
    }
    validate_name(&args.name)?;
    if args.version.value().trim().is_empty() {
        return Err(Error::new(
            args.version.span(),
            "plugin version must not be empty",
        ));
    }

    let ident = &item.ident;
    let authors: Vec<String> = args.authors.iter().map(LitStr::value).collect();
    let metadata = plugin_metadata_json(&args.name.value(), &authors, &args.version.value());

    Ok(quote! {
        #[derive(::core::default::Default)]
        #item

        const _: () = assert!(::pasm::__internal::ABI_VERSION == 1, "pasm and pasm-macros ABI versions differ");

        #[doc(hidden)]
        static __PASM_PLUGIN_SLOT: ::pasm::__internal::Slot<#ident> = ::pasm::__internal::Slot::new();

        #[doc(hidden)]
        static __PASM_PLUGIN_META: &str = #metadata;

        #[doc(hidden)]
        #[unsafe(no_mangle)]
        pub extern "C" fn __pasm_abi_version() -> i32 {
            ::pasm::__internal::ABI_VERSION
        }

        #[doc(hidden)]
        #[unsafe(no_mangle)]
        pub extern "C" fn __pasm_alloc(len: u32) -> u32 {
            ::pasm::__internal::alloc(len)
        }

        #[doc(hidden)]
        #[unsafe(no_mangle)]
        pub extern "C" fn __pasm_free(ptr: u32, len: u32) {
            ::pasm::__internal::dealloc(ptr, len)
        }

        #[doc(hidden)]
        #[unsafe(no_mangle)]
        pub extern "C" fn __pasm_meta_plugin() -> u64 {
            ::pasm::__internal::pack_static_str(__PASM_PLUGIN_META)
        }

        #[doc(hidden)]
        #[unsafe(no_mangle)]
        pub extern "C" fn __pasm_plugin_enable() -> i32 {
            ::pasm::__internal::plugin_enable(&__PASM_PLUGIN_SLOT, |plugin: &mut #ident| {
                use ::pasm::__internal::LifecycleDefaults as _;
                plugin.on_enabled()
            })
        }

        #[doc(hidden)]
        #[unsafe(no_mangle)]
        pub extern "C" fn __pasm_plugin_disable() -> i32 {
            ::pasm::__internal::plugin_disable(&__PASM_PLUGIN_SLOT, |plugin: &mut #ident| {
                use ::pasm::__internal::LifecycleDefaults as _;
                plugin.on_disabled()
            })
        }
    })
}

fn parse_plugin_args(args: TokenStream2) -> syn::Result<PluginArgs> {
    let mut name: Option<LitStr> = None;
    let mut version: Option<LitStr> = None;
    let mut authors: Vec<LitStr> = Vec::new();

    let parser = syn::meta::parser(|meta| {
        if meta.path.is_ident("name") {
            name = Some(meta.value()?.parse()?);
            return Ok(());
        }
        if meta.path.is_ident("version") {
            version = Some(meta.value()?.parse()?);
            return Ok(());
        }
        if meta.path.is_ident("authors") {
            let array: ExprArray = meta.value()?.parse()?;
            for element in array.elems {
                match element {
                    Expr::Lit(ExprLit {
                        lit: Lit::Str(author),
                        ..
                    }) => authors.push(author),
                    other => {
                        return Err(Error::new(other.span(), "authors must be string literals"))
                    }
                }
            }
            return Ok(());
        }
        Err(meta.error("unsupported key; expected `name`, `authors` or `version`"))
    });
    syn::parse::Parser::parse2(parser, args)?;

    let Some(name) = name else {
        return Err(Error::new(
            proc_macro2::Span::call_site(),
            "#[plugin] requires `name = \"...\"`",
        ));
    };
    let Some(version) = version else {
        return Err(Error::new(
            proc_macro2::Span::call_site(),
            "#[plugin] requires `version = \"...\"`",
        ));
    };
    Ok(PluginArgs {
        name,
        version,
        authors,
    })
}

fn validate_name(name: &LitStr) -> syn::Result<()> {
    let value = name.value();
    if value.is_empty() {
        return Err(Error::new(name.span(), "plugin name must not be empty"));
    }
    if !value
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || matches!(c, '_' | '-' | '.'))
    {
        return Err(Error::new(
            name.span(),
            "plugin name may only contain ASCII letters, digits, '_', '-' and '.'",
        ));
    }
    Ok(())
}

fn plugin_metadata_json(name: &str, authors: &[String], version: &str) -> String {
    let authors: Vec<String> = authors
        .iter()
        .map(|author| format!("\"{}\"", json_escape(author)))
        .collect();
    format!(
        "{{\"kind\":\"plugin\",\"abi\":1,\"name\":\"{}\",\"authors\":[{}],\"version\":\"{}\"}}",
        json_escape(name),
        authors.join(","),
        json_escape(version)
    )
}

fn json_escape(value: &str) -> String {
    let mut escaped = String::with_capacity(value.len());
    for c in value.chars() {
        match c {
            '"' => escaped.push_str("\\\""),
            '\\' => escaped.push_str("\\\\"),
            '\n' => escaped.push_str("\\n"),
            '\r' => escaped.push_str("\\r"),
            '\t' => escaped.push_str("\\t"),
            c if (c as u32) < 0x20 => escaped.push_str(&format!("\\u{:04x}", c as u32)),
            c => escaped.push(c),
        }
    }
    escaped
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn escapes_special_characters() {
        assert_eq!(json_escape("a\"b\\c\n\u{1}"), "a\\\"b\\\\c\\n\\u0001");
    }

    #[test]
    fn builds_metadata_json() {
        let json = plugin_metadata_json("Demo", &["A".to_string(), "B\"q".to_string()], "1.0.0");
        assert_eq!(
            json,
            "{\"kind\":\"plugin\",\"abi\":1,\"name\":\"Demo\",\"authors\":[\"A\",\"B\\\"q\"],\"version\":\"1.0.0\"}"
        );
    }

    #[test]
    fn rejects_invalid_names() {
        let bad: LitStr = syn::parse_quote!("bad name");
        let good: LitStr = syn::parse_quote!("Good-Name_1.0");
        assert!(validate_name(&bad).is_err());
        assert!(validate_name(&good).is_ok());
    }
}
