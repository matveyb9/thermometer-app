package ru.matveyb9.diy.thermometerapp.data.model

import android.hardware.usb.UsbDevice

data class UsbDeviceInfo(
    val device: UsbDevice,
    val driverName: String,
    val portCount: Int = 1,
) {
    val displayName: String
        get() = device.productName?.takeIf { it.isNotBlank() }
            ?: "USB Device #${device.deviceId}"

    val manufacturerName: String
        get() = device.manufacturerName?.takeIf { it.isNotBlank() } ?: "—"

    /** /dev/bus/usb/001/002 → "bus/usb/001/002" */
    val portPath: String
        get() = device.deviceName.removePrefix("/dev/")

    val vendorId: String  get() = "%04X".format(device.vendorId)
    val productId: String get() = "%04X".format(device.productId)
    val vendorProductId: String get() = "VID: $vendorId  PID: $productId"
}
