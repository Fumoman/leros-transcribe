package leros

import chisel3._
import leros.util.Assembler

class DataMemIO(size: Int, memAddrWidth: Int) extends Bundle {
    val rdAddr = Input(UInt(memAddrWidth.W))
    val rdData = Output(UInt(size.W))
    val wrAddr = Input(UInt(memAddrWidth.W))
    val wrData = Input(UInt(size.W))
    val wr = Input(Bool())
    val wrMask = Input(UInt((size / 8).W))
}

/**
  * Data memory.
  */
class DataMem(size: Int, memAddrWidth: Int) extends Module {
    require((size == 16 || size == 32 || size == 64), s"Data bit width must be 16, 32 or 64, got $size")
    val io = IO(new DataMemIO(size, memAddrWidth))

    val entries = 1 << memAddrWidth
    val wrVec = Wire(Vec((size / 8), UInt(8.W)))
    val wrMask = Wire(Vec((size / 8), Bool()))
    for (i <- 0 until (size / 8)) {
        wrVec(i) := io.wrData(i * 8 + 7, i * 8)
        wrMask(i) := io.wrMask(i)
    }
    val rdVec = Wire(Vec((size / 8), UInt(8.W)))

    val mem = SyncReadMem(entries, Vec((size / 8), UInt(8.W)))
    rdVec := mem.read(io.rdAddr)
    when (io.wr) {
        mem.write(io.wrAddr, wrVec, wrMask)
    }

    io.rdData := rdVec.asUInt
}
