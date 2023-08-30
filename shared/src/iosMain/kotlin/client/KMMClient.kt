package client

import exception.GattException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.darwin.NSObject
import scanner.KMMDevice
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Suppress("CONFLICTING_OVERLOADS")
actual class KMMClient : NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {

    private lateinit var peripheral: CBPeripheral

    private val manager = CBCentralManager()

    private var onDeviceConnected: ((DeviceConnectionState) -> Unit)? = null
    private var onDeviceDisconnected: (() -> Unit)? = null
    private var onServicesDiscovered: ((OperationStatus) -> Unit)? = null

    private var services: KMMServices? = null

    private val _bleState = MutableStateFlow(CBManagerStateUnknown)
    val bleState: StateFlow<CBManagerState> = _bleState.asStateFlow()

    private fun onEvent(event: IOSGattEvent) {
        services?.onEvent(event)
    }

    actual suspend fun connect(device: KMMDevice) {
        peripheral = device.peripheral
        bleState.first { it == CBCentralManagerStatePoweredOn }
        return suspendCoroutine { continuation ->
            onDeviceConnected = {
                onDeviceConnected = null
                continuation.resume(Unit)
            }
            manager.connectPeripheral(peripheral, null)
        }
    }

    actual suspend fun disconnect() {
        return suspendCoroutine { continuation ->
            onDeviceDisconnected = {
                onDeviceDisconnected = null
                continuation.resume(Unit)
            }
            manager.cancelPeripheralConnection(peripheral)
        }
    }

    actual suspend fun discoverServices(): KMMServices {
        return suspendCoroutine { continuation ->
            onServicesDiscovered = {
                peripheral.services?.toDomain()?.let {
                    services = it
                    continuation.resume(it)
                } ?: run {
                    continuation.resumeWithException(GattException())
                }
            }
            peripheral.discoverServices(null)
        }
    }

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        peripheral.services?.map {
            it as CBService
        }?.onEach {
            peripheral.discoverCharacteristics(null, it)
        }
        onServicesDiscovered?.invoke(getOperationStatus(didDiscoverServices))
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?,
    ) {
        peripheral.services
            ?.map { it as CBService }
            ?.map { it.characteristics }
            ?.mapNotNull { it?.map { it as CBCharacteristic } }
            ?.onEach {
                it.forEach {
                    peripheral.discoverDescriptorsForCharacteristic(it)
                }
            }
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverDescriptorsForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        onServicesDiscovered?.invoke(getOperationStatus(error))
        //todo aggregate responses
    }

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        _bleState.value = central.state
    }

    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        onDeviceConnected?.invoke(DeviceDisconnected)
    }

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        onDeviceConnected?.invoke(DeviceConnected)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        onDeviceDisconnected?.invoke()
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        onEvent(
            OnGattCharacteristicRead(
                peripheral,
                didUpdateValueForCharacteristic.value as NSData
            )
        )
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        onEvent(
            OnGattCharacteristicWrite(
                peripheral,
                didWriteValueForCharacteristic.value as NSData
            )
        )
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForDescriptor: CBDescriptor,
        error: NSError?,
    ) {
        onEvent(OnGattDescriptorWrite(peripheral, didWriteValueForDescriptor.value as NSData))
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForDescriptor: CBDescriptor,
        error: NSError?,
    ) {
        onEvent(OnGattDescriptorRead(peripheral, didUpdateValueForDescriptor.value as NSData))
    }

    private fun getOperationStatus(error: NSError?): OperationStatus {
        return error?.let { OperationError } ?: OperationSuccess
    }
}
