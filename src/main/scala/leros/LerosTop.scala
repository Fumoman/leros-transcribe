package leros

import chisel3._
import chisel3.util._
import leros.State._
import leros.shared.Constants._

/**
  * Leros top level
  */
class LerosTop(prog: String, size: Int = 32, instrMemAddrWidth: Int = 8, dataMemAddrWidth: Int = 12, frequency: Int = 50000000, bufferDepth: Int = 8) extends Module {
    val io = IO(new Bundle {
        val uartTx = Output(UInt(1.W))
        val uartRx = Input(UInt(1.W))
    })

    val leros = Module(new Leros(size, instrMemAddrWidth, (dataMemAddrWidth + 1)))
    val instrMem = Module(new InstrMem(instrMemAddrWidth, prog, false))
    val dataMem = Module(new DataMem(size, dataMemAddrWidth))
    val mmios = Module(new MMIOs(size, dataMemAddrWidth, frequency, bufferDepth))

    instrMem.io <> leros.imemIO

    dataMem.io.rdAddr := leros.dmemIO.rdAddr(dataMemAddrWidth - 1, 0)
    dataMem.io.wr := !leros.dmemIO.wrAddr(dataMemAddrWidth) && leros.dmemIO.wr
    dataMem.io.wrAddr := leros.dmemIO.wrAddr(dataMemAddrWidth - 1, 0)
    dataMem.io.wrData := leros.dmemIO.wrData
    dataMem.io.wrMask := leros.dmemIO.wrMask

    mmios.io.rdAddr := leros.dmemIO.rdAddr(dataMemAddrWidth - 1, 0)
    mmios.io.wr := leros.dmemIO.wrAddr(dataMemAddrWidth) && leros.dmemIO.wr
    mmios.io.wrAddr := leros.dmemIO.wrAddr(dataMemAddrWidth - 1, 0)
    mmios.io.wrData := leros.dmemIO.wrData
    mmios.io.wrMask := leros.dmemIO.wrMask

    leros.dmemIO.rdData := Mux(RegNext(leros.dmemIO.rdAddr(dataMemAddrWidth)), mmios.io.rdData, dataMem.io.rdData)

    io.uartTx := mmios.io.tx
    mmios.io.rx := io.uartRx
}

object LerosTop extends App {
    emitVerilog(new LerosTop(args(0), args(1).toInt, args(2).toInt, args(3).toInt, args(4).toInt, args(5).toInt), Array("--target-dir", args(6)))
}
