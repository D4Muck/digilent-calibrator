package at.d4m.digilentcalibrator

import at.d4m.digilentcalibrator.Sachen.crc16
import at.d4m.digilentcalibrator.Sachen.shiftShortRightBy
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPort.NO_PARITY
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import kotlin.experimental.and

class MyArgs(parser: ArgParser) {
    val temperature by parser.storing("-t", "--temperature", help = "The temperature") { toInt() }
}

fun main(args: Array<String>) {
    mainBody {
        MyArgs(ArgParser(args)).run {
            val temp = temperature
            val serialPort = SerialPort.getCommPorts().filter { it.descriptivePortName.contains("XDS110") }.firstOrNull() ?: error("No board found")

            println("Using " + serialPort.descriptivePortName)

            serialPort.baudRate = 9600
            serialPort.parity = NO_PARITY
            serialPort.numDataBits = 8
            serialPort.numStopBits = 1

            val sendTemperature = createTemperatureDataFrame(temp)

            println("Sending " + sendTemperature.map { it.toHex() }.toList())

            println(serialPort.openPort())
            serialPort.outputStream.write(sendTemperature)
            serialPort.outputStream.flush()
            serialPort.closePort()
            Thread.sleep(2000)
        }
    }
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

fun Byte.toHex(): String {
    return Integer.toHexString(this.toInt() and 0xff)
}