package ui.server

import advertisement.KMMAdvertisementSettings
import advertisement.KMMBleAdvertiser
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import com.benasher44.uuid.uuidFrom
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import server.KMMBlePermission
import server.KMMBleServer
import server.KMMBleServerCharacteristic
import server.KMMBleServerCharacteristicConfig
import server.KMMBleServerProfile
import server.KMMBleServerServiceConfig
import server.KMMCharacteristicProperty
import ui.blinky.BlinkyLedParser.toDisplayString

private val BLINKY_SERVICE_UUID = uuidFrom("00001523-1212-efde-1523-785feabcd123")
private val BLINKY_BUTTON_CHARACTERISTIC_UUID = uuidFrom("00001524-1212-efde-1523-785feabcd123")
private val BLINKY_LED_CHARACTERISTIC_UUID = uuidFrom("00001525-1212-efde-1523-785feabcd123")

private const val TAG = "BLE-TAG"

class ServerViewModel : ScreenModel, KoinComponent {

    private val advertiser: KMMBleAdvertiser by inject()
    private val server: KMMBleServer by inject()

    private val _state = MutableStateFlow(ServerState())
    val state = _state.asStateFlow()

    private var buttonCharacteristic: KMMBleServerCharacteristic? = null

    init {
        //Define led characteristic
        val ledCharacteristic = KMMBleServerCharacteristicConfig(
            BLINKY_LED_CHARACTERISTIC_UUID,
            listOf(KMMCharacteristicProperty.READ, KMMCharacteristicProperty.WRITE),
            listOf(KMMBlePermission.READ, KMMBlePermission.WRITE),
            emptyList()
        )

        //Define button characteristic
        val buttonCharacteristic = KMMBleServerCharacteristicConfig(
            BLINKY_BUTTON_CHARACTERISTIC_UUID,
            listOf(KMMCharacteristicProperty.READ, KMMCharacteristicProperty.NOTIFY),
            listOf(KMMBlePermission.READ, KMMBlePermission.WRITE),
            emptyList()
        )

        //Put led and button characteristics inside a service
        val serviceConfig = KMMBleServerServiceConfig(
            BLINKY_SERVICE_UUID,
            listOf(ledCharacteristic, buttonCharacteristic)
        )

        coroutineScope.launch {
            server.startServer(listOf(serviceConfig), this)

            server.connections
                .onEach { it.values.forEach { setUpProfile(it) } }
                .launchIn(coroutineScope)
        }
    }

    private fun setUpProfile(profile: KMMBleServerProfile) {
        val service = profile.findService(BLINKY_SERVICE_UUID)!!
        val ledCharacteristic = service.findCharacteristic(BLINKY_LED_CHARACTERISTIC_UUID)!!
        val buttonCharacteristic = service.findCharacteristic(BLINKY_BUTTON_CHARACTERISTIC_UUID)!!

        this.buttonCharacteristic = buttonCharacteristic

        ledCharacteristic.value.onEach {
            Napier.i("set led ${it.toDisplayString()}", tag = TAG)
            _state.value = _state.value.copy(isLedOn = it.contentEquals(byteArrayOf(0x01)))
        }.launchIn(coroutineScope)

        buttonCharacteristic.value.onEach {
            Napier.i("set button ${it.toDisplayString()}", tag = TAG)
            _state.value = _state.value.copy(isButtonPressed = it.contentEquals(byteArrayOf(0x01)))
        }.launchIn(coroutineScope)
    }

    fun advertise() {
        coroutineScope.launch {
            advertiser.advertise(KMMAdvertisementSettings(
                name = "Super Server",
                uuid = BLINKY_SERVICE_UUID
            ))
            _state.value = _state.value.copy(isAdvertising = true)
        }
    }

    fun stopAdvertise() {
        coroutineScope.launch {
            advertiser.stop()
            _state.value = _state.value.copy(isAdvertising = false)
        }
    }

    fun onButtonPressedChanged(isButtonPressed: Boolean) {
        Napier.i("onButton pressed 1: $isButtonPressed", tag = TAG)
        val value = if (isButtonPressed) {
            byteArrayOf(0x01)
        } else {
            byteArrayOf(0x00)
        }
        coroutineScope.launch {
            buttonCharacteristic?.setValue(value)
        }
    }
}
