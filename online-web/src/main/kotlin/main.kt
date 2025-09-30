import kotlinx.browser.document
import kotlinx.dom.appendText

fun main() {
    document.body?.appendText("ZeroPay WASM loaded – factors coming soon")
}