import androidx.compose.runtime.Composable
import no.nordicsemi.android.common.permissions.ble.RequireBluetooth
import no.nordicsemi.android.common.permissions.internet.RequireInternet

actual fun getPlatformName(): String = "Android"

@Composable
fun MainView() {
    App {
        RequireBluetooth {
            RequireInternet {
                Content()
            }
        }
    }
}
