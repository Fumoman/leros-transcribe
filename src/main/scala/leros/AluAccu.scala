package leros

import chisel3._
import chisel3.util._

import leros.shared.Constants._

/**
  * Leros ALU including the accumulator register.
  * 
  * @param size The bit width of the ALU accumulator register.
  */
class AluAccu(size: Int) extends Module {
    require((size == 16 || size == 32 || size == 64), s"The bit width of ALU must be 16, 32 or 64, got $size")
    val io = IO(new Bundle {
      val op = Input(UInt(3.W))
      val din = Input(UInt(size.W))
      val enaMask = Input(UInt((size / 8).W))
      val enaByte = Input(Bool())
      val enaHalf = if (size >= 32) Some(Input(Bool())) else None
      val enaWord = if (size >= 64) Some(Input(Bool())) else None
      val off = Input(UInt((log2Ceil(size / 8)).W))
      val accu = Output(UInt(size.W))
    })

    val accuReg = RegInit(0.U(size.W))

    val op = io.op
    val a = accuReg
    val b = io.din
    val res = WireDefault(a)

    switch(op) {
      is(nop.U) {
        res := a
      }
      is(add.U) {
        res := a + b
      }
      is(sub.U) {
        res := a - b
      }
      is(and.U) {
        res := a & b
      }
      is(or.U) {
        res := a | b
      }
      is(xor.U) {
        res := a ^ b
      }
      is(ld.U) {
        res := b
      }
      is(sra.U) {
        res := a(size - 1) ## a(size - 1, 1)
      }
    }

    val signExt = Wire(SInt(size.W))
    val bytes = Wire(Vec((size / 8), UInt(8.W)))
    for (i <- 0 until (size / 8)) {
      bytes(i) := res(8 * i + 7, 8 * i)
    }
    signExt := bytes(io.off).asSInt

    val isEnaHalf = io.enaHalf.getOrElse(false.B)
    val isEnaWord = io.enaWord.getOrElse(false.B)

    if (size >= 32) {
      val halfWords = Wire(Vec((size / 16), UInt(16.W)))
      for (i <- 0 until (size / 16)) {
        halfWords(i) := res(16 * i + 15, 16 * i)
      }
      when (isEnaHalf) {
        signExt := halfWords((io.off >> 1)(log2Ceil(size / 16) - 1, 0).asUInt).asSInt
      }
    }
    if (size >= 64) {
      val words = Wire(Vec((size / 32), UInt(32.W)))
      for (i <- 0 until (size / 32)) {
        words(i) := res(32 * i + 31, 32 * i)
      }
      when (isEnaWord) {
        signExt := words((io.off >> 2)(0).asUInt).asSInt
      }
    }

    val split = Wire(Vec((size / 8), UInt(8.W)))
    for (i <- 0 until (size / 8)) {
      split(i) := Mux(io.enaMask(i), res(8 * i + 7, 8 * i), accuReg(8 * i + 7, 8 * i))
    }

    when((io.enaByte || isEnaHalf || isEnaWord) && io.enaMask.andR) {
      accuReg := signExt.asUInt
    } .otherwise {
      accuReg := split.asUInt
    }

    io.accu := accuReg
}