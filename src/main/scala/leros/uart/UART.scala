package leros.uart

import chisel3._
import chisel3.util._

/**
  * A single UART module that will be implemented in MMIOs module.
  *
  * @param frequency System clock frequency.
  * @param bufferDepth The depth of buffers.
  */
class PeripheralUART(frequency: Int, bufferDepth: Int) extends Module {
    val io = IO(new Bundle {
        val tx = Output(UInt(1.W))
        val rx = Input(UInt(1.W))
        val txChannel = Flipped(Decoupled(UInt(8.W)))
        val rxChannel = Decoupled(UInt(8.W))
        val baudRateMode = Input(UInt(2.W))
    })

    val uartTx = Module(new Tx(frequency))
    val uartRx = Module(new Rx(frequency))

    uartTx.io.channel <> Queue(io.txChannel, bufferDepth)
    io.rxChannel <> Queue(uartRx.io.channel, bufferDepth)
    io.tx := uartTx.io.tx
    uartRx.io.rx := io.rx
    uartTx.io.baudRateMode := io.baudRateMode
    uartRx.io.baudRateMode := io.baudRateMode
}