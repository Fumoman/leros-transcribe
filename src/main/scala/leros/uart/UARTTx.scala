package leros.uart

import chisel3._
import chisel3.util._

/**
  * Core transmit part of Leros's UART.
  * 
  * Supported baud rate: 9600, 19200, 38400, 115200 bps.
  * 
  * Supported frame: 0(1 bit) + data(8 bits) + 1(1 bit)
  * 
  * @param frequency System clock frequency.
  */
class Tx(frequency: Int) extends Module {
    val io = IO(new Bundle {
        val tx = Output(UInt(1.W))
        val channel = Flipped(Decoupled(UInt(8.W)))
        val baudRateMode = Input(UInt(2.W))
    })

    val shiftReg = RegInit(0x3ff.U(10.W))
    val cntReg = RegInit(0.U(14.W))
    val bitsReg = RegInit(0.U(4.W))
    val cntLoad = Wire(UInt(14.W))
    cntLoad := MuxLookup(io.baudRateMode, (((frequency + 57600) / 115200) - 1).U(14.W))(Seq(
            0.U -> (((frequency + 57600) / 115200) - 1).U(14.W),
            1.U -> (((frequency + 19200) / 38400) - 1).U(14.W),
            2.U -> (((frequency + 9600) / 19200) - 1).U(14.W),
            3.U -> (((frequency + 4800) / 9600) - 1).U(14.W)
        ))

    when (cntReg === 0.U) {
        cntReg := cntLoad
        when (bitsReg === 0.U) {
            when (io.channel.valid) {
                shiftReg := 1.U(1.W) ## io.channel.bits ## 0.U(1.W)
                bitsReg :=  10.U
            }
        } .otherwise {
            shiftReg := 1.U(1.W) ## shiftReg(9, 1)
            bitsReg :=  bitsReg - 1.U
        }
    } .otherwise {
        cntReg := cntReg - 1.U
    }

    io.tx := shiftReg(0)
    io.channel.ready := (cntReg === 0.U) && (bitsReg === 0.U)
}
