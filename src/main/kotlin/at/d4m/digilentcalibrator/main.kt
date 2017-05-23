package at.d4m.digilentcalibrator

import at.d4m.digilentcalibrator.Sachen.crc16
import at.d4m.digilentcalibrator.Sachen.shiftShortRightBy
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import javax.usb.UsbDevice
import javax.usb.UsbEndpoint
import javax.usb.UsbHostManager
import javax.usb.UsbHub
import javax.usb.event.UsbPipeDataEvent
import javax.usb.event.UsbPipeErrorEvent
import javax.usb.event.UsbPipeListener
import kotlin.experimental.and

class MyArgs(parser: ArgParser) {
    val temperature by parser.storing("-t", "--temperature", help = "The temperature") { toInt() }
    val vendorId by parser.storing("-v", "--vendor-id", help = "The vendor id as hex like: '0451'\nDefault is 0x0451") { toInt(16) }.default(0x0451)
    val deviceId by parser.storing("-d", "--device-id", help = "The device id as hex like: 'BEF3'\nDefault is 0xBEF3") { toInt(16) }.default(0xBEF3)
}

fun main(args: Array<String>) {
    mainBody {
        MyArgs(ArgParser(args)).run {
            //0xBEF3 deviceId
            //0x0451 vendorId
            val services = UsbHostManager.getUsbServices()



            println("USB Service Implementation:" + services.impDescription)
            println("Implementation version: " + services.impVersion)
            println("Service API version: " + services.apiVersion)

            val findDevice = findDevice(services.rootUsbHub, vendorId, deviceId)

            findDevice?.let {
                println(it.manufacturerString)

                val iface = it.activeUsbConfiguration.getUsbInterface(1)
                iface.claim { true }
                try {
                    for (usbEndpoint in iface.usbEndpoints) {
                        if (usbEndpoint is UsbEndpoint) {
                            println(usbEndpoint.usbEndpointDescriptor.bEndpointAddress())
                        }
                    }
                    val endpoint: UsbEndpoint? = iface.getUsbEndpoint(0x01.toByte())

                    endpoint?.let {
                        println("not null!")
                        val pipe = it.usbPipe
                        pipe.open()

                        pipe.addUsbPipeListener(object : UsbPipeListener {
                            override fun dataEventOccurred(event: UsbPipeDataEvent?) {
                                println(event?.data?.asList()?.map { it.toHex() })
                            }

                            override fun errorEventOccurred(event: UsbPipeErrorEvent?) {
                                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                            }

                        })
//                        while (true) {
                        val sendTemperature = createTemperatureDataFrame(temperature)
                        pipe.syncSubmit(sendTemperature)
                        Thread.sleep(2000)
//                        }
                    }
                } finally {
                    iface.release();
                }
            }
        }
    }
}

private fun findDevice(hub: UsbHub, vendorId: Int = 0, productId: Int = 0): UsbDevice? {
    for (device in hub.attachedUsbDevices) {
        if (device is UsbDevice) {
            val desc = device.usbDeviceDescriptor
            if (desc.idVendor() == vendorId.toShort() && desc.idProduct() == productId.toShort()) return device
            if (device is UsbHub) {
                findDevice(device, vendorId, productId)?.let { return it }
            }
        }
    }
    return null
}

private fun createTemperatureDataFrame(temperature: Int): ByteArray {
    val buf = ByteArray(6)
    val crcbuf = ByteArray(3)

    buf[0] = 0xFF.toByte()                          // start of frame
    buf[1] = 2                                 // two bytes data will follow
    buf[2] = (temperature and 0xFF).toByte()          // temperature low byte
    buf[3] = (temperature shr 8 and 0xFF).toByte()     // temperature high byte

    System.arraycopy(buf, 1, crcbuf, 0, 3)
    val crc = crc16(crcbuf, 3, 0)

    buf[4] = (crc and (0xFF)).toByte()                      // crc16
    buf[5] = (shiftShortRightBy(crc, 8) and 0xFF).toByte()  // crc16

    return buf
}

fun Short.toHex(): String {
    return Integer.toHexString(this.toInt() and 0xffff)
}

fun Byte.toHex(): String {
    return Integer.toHexString(this.toInt() and 0xff)
}