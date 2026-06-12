package leros.uart

import chisel3._
import chisel3.util._

/**
  * Core recieve part of Leros's UART.
  * 
  * Supported baud rate: 9600, 19200, 38400, 115200 bps.
  * 
  * Supported frame: 0(1 bit) + data(8 bits) + 1(1 bit)
  * 
  * @param frequency System clock frequency.
  */
class Rx(frequency: Int) extends Module {
    val io = IO(new Bundle {
        val rx = Input(UInt(1.W))
        val channel = Decoupled(UInt(8.W))
        val baudRateMode = Input(UInt(2.W))
    })

    val shiftReg = RegInit(0.U(8.W))
    val outReg = RegInit(0.U(8.W))
    val cntReg = RegInit((((frequency + 57600) / 115200) - 5).U(14.W)) // idle time before listening
    val bitsReg = RegInit(0.U(4.W))

    val rxReg = RegNext(RegNext(io.rx, 0.U), 0.U)
    val validReg = RegInit(false.B)
    val rxFalling = !rxReg && (RegNext(rxReg, 0.U) === 1.U)

    val cntLoad = Wire(UInt(14.W))
    val cntLoadStart = Wire(UInt(14.W))

    cntLoad := MuxLookup(io.baudRateMode, (((frequency + 57600) / 115200) - 1).U(14.W))(Seq(
            0.U -> (((frequency + 57600) / 115200) - 1).U(14.W),
            1.U -> (((frequency + 19200) / 38400) - 1).U(14.W),
            2.U -> (((frequency + 9600) / 19200) - 1).U(14.W),
            3.U -> (((frequency + 4800) / 9600) - 1).U(14.W)
        ))
    cntLoadStart := MuxLookup(io.baudRateMode, ((((3 * frequency / 2) + 57600) / 115200) - 1).U(14.W))(Seq(
            0.U -> ((((3 * frequency / 2) + 57600) / 115200) - 2).U(14.W),
            1.U -> ((((3 * frequency / 2) + 19200) / 38400) - 2).U(14.W),
            2.U -> ((((3 * frequency / 2) + 9600) / 19200) - 2).U(14.W),
            3.U -> ((((3 * frequency / 2) + 4800) / 9600) - 2).U(14.W)
        ))

    when (io.channel.fire) {
        validReg := false.B
    }

    when (cntReg === 0.U) {
        when (bitsReg === 0.U) {
            when (rxFalling) {
                cntReg := cntLoadStart
                bitsReg := 8.U
            }
        } .otherwise {
            cntReg := cntLoad
            shiftReg := rxReg ## shiftReg(7, 1)
            bitsReg := bitsReg - 1.U
            when (bitsReg === 1.U) {
                outReg := rxReg ## shiftReg(7, 1)
                validReg := true.B
            }
        }
    } .otherwise {
        cntReg := cntReg - 1.U
    }

    io.channel.valid := validReg
    io.channel.bits := outReg
}
