package leros

import chisel3._
import chisel3.util._
import leros.shared.Constants._
import leros.State._

/**
  * Core module of Leros
  */
class Leros(size: Int = 32, instrMemAddrWidth: Int = 8, dataMemAddrWidth: Int = 16) extends Module {
    require((size == 16 || size == 32 || size == 64), s"Leros top size must be 16, 32 or 64, got $size")
    val imemIO = IO(Flipped(new InstrMemIO(instrMemAddrWidth)))
    val dmemIO = IO(Flipped(new DataMemIO(size, dataMemAddrWidth)))

    val pcReg = RegInit(-1.S(instrMemAddrWidth.W).asUInt)
    val pcNext = WireDefault(pcReg + 1.U)
    val firstClockReg = RegInit(true.B)
    imemIO.addr := pcNext
    firstClockReg := false.B
    val instr = Mux(firstClockReg, 0.U, imemIO.instr)

    val dec = Module(new Decode(size))
    val decReg = RegInit(DecodeOut.default(size))
    val decout = dec.io.dout
    dec.io.din := instr

    val addrReg = RegInit(0.U(size.W))
    val effAddr = (addrReg.asSInt + decout.off).asUInt
    val effAddrMem = (effAddr >> log2Ceil(size / 8))(dataMemAddrWidth - 1, 0)
    val offReg = RegNext(effAddr(log2Ceil(size / 8) - 1, 0).asUInt)

    val alu = Module(new AluAccu(size))
    val accu = alu.io.accu
    val memAddr = Mux(decout.isDataAccess, effAddrMem, instr(7, 0))
    val memAddrReg = RegNext(memAddr)
    val readData = dmemIO.rdData
    
    dmemIO.rdAddr := memAddr
    dmemIO.wrAddr := memAddrReg
    dmemIO.wrData := accu
    dmemIO.wr := false.B
    dmemIO.wrMask := -1.S((size / 8).W).asUInt
    alu.io.op := decReg.op
    alu.io.enaMask := 0.U
    alu.io.enaByte := decReg.enaByte
    if (size >= 32) {alu.io.enaHalf.get := decReg.enaHalf.get}
    if (size >= 64) {alu.io.enaWord.get := decReg.enaWord.get}
    alu.io.off := offReg
    alu.io.din := Mux(decReg.useDecOpd, decReg.operand, readData)
    
    val exit = RegInit(false.B)

    val stateReg = RegInit(fetch)

    when (stateReg =/= fetch) {
      stateReg := fetch
      pcReg := pcNext
      alu.io.enaMask := decReg.enaMask
    }

    switch(stateReg) {
      is (fetch) {
        stateReg := decout.nextState
        decReg := decout
      }
      is (loadAddr) {
        addrReg := readData
      }
      is (loadInd) {
        // do nothing
      }
      is (store) {
        dmemIO.wr := true.B
      }
      is (storeInd) {
        dmemIO.wr := true.B
      }
      is (storeIndB) {
        dmemIO.wr := true.B
        dmemIO.wrMask := (1.U << offReg)((size / 8) - 1, 0)
        dmemIO.wrData := Fill((size / 8), accu(7, 0))
      }
      is (storeIndH) {
        if (size >= 32) {
          dmemIO.wr := true.B
          dmemIO.wrMask := (3.U << offReg)((size / 8) - 1, 0)
          dmemIO.wrData := Fill((size / 16), accu(15, 0))
        }
      }
      is (storeIndW) {
        if (size >= 64) {
          dmemIO.wr := true.B
          dmemIO.wrMask := (15.U << offReg)((size / 8) - 1, 0)
          dmemIO.wrData := Fill((size / 32), accu(31, 0))
        }
      }
      is (branch) {
        val doBranch = WireDefault(false.B)
        switch(decReg.brType) {
          is ((BR >> 4).U) {
            doBranch := true.B
          }
          is ((BRZ >> 4).U) {
            doBranch := (accu === 0.U)
          }
          is ((BRNZ >> 4).U) {
            doBranch := (accu =/= 0.U)
          }
          is ((BRP >> 4).U) {
            doBranch := (accu(size - 1) === 0.U)
          }
          is ((BRN >> 4).U) {
            doBranch := (accu(size - 1) =/= 0.U)
          }
        }
        when (doBranch) {
          pcNext := (pcReg.asSInt + decReg.brOff).asUInt
        }
      }
      is (jal) {
        pcNext := accu
        dmemIO.wr := true.B
        dmemIO.wrData := pcReg + 1.U
      }
      is (scall) {
        exit := RegNext(true.B)
      }
    }
}

object Leros extends App {
  emitVerilog(new Leros, Array("--target-dir", "generated"))
}
