package leros

import chisel3._
import chisel3.util._

import leros.shared.Constants._
import leros.State._

class DecodeOut(size: Int) extends Bundle {
    val operand = UInt(size.W)
    val enaMask = UInt((size / 8).W)
    val op = UInt()
    val off = SInt((8 + log2Ceil(size / 8)).W)
    val brOff = SInt(12.W) // branch instructions only take 4 bits
    val isRegOpd = Bool()
    val useDecOpd = Bool()
    val nextState = State()
    val enaByte = Bool()
    val enaHalf = if (size >= 32) Some(Bool()) else None
    val enaWord = if (size >= 64) Some(Bool()) else None
    val isDataAccess = Bool()
    val brType = UInt(4.W)
}

object DecodeOut {
    def default(size: Int): DecodeOut = {
        val v = Wire(new DecodeOut(size))
        v.operand := 0.U
        v.enaMask := 0.U
        v.op := nop.U
        v.off := 0.S
        v.brOff := 0.S
        v.isRegOpd := false.B
        v.useDecOpd := false.B
        v.nextState := init
        v.enaByte := false.B
        if (size >= 32) {v.enaHalf.get := false.B}
        if (size >= 64) {v.enaWord.get := false.B}
        v.isDataAccess := false.B
        v.brType := 0.U
        v
    }
}

class Decode(size: Int) extends Module {
    require((size == 16 || size == 32 || size == 64), s"The bit width of decoder must be 16, 32 or 64, got $size")
    val io = IO(new Bundle {
        val din = Input(UInt(16.W))
        val dout = Output(new DecodeOut(size))
    })

    val d = DecodeOut.default(size)

    def mask(i: Int) = ((i >> 4) & 0x0f).asUInt

    val field = io.din(15, 12)
    when(field === mask(BR) || field === mask(BRZ) || field === mask(BRNZ) || field === mask(BRP) || field === mask(BRN)) {
        d.nextState := branch
    }

    val instr = io.din
    d.brType := field
    d.brOff := instr(11, 0).asSInt

    val noSext = WireDefault(false.B)
    val sigExt = Wire(SInt(size.W))
    sigExt := instr(7, 0).asSInt
    d.off := (sigExt << log2Ceil(size / 8))(7 + log2Ceil(size / 8), 0).asSInt
    d.operand := sigExt.asUInt
    when(noSext) {d.operand := instr(7, 0)}

    val MaskAll = (-1.S((size / 8).W)).asUInt

    switch(instr(15, 8)) {
        is(ADD.U) {
            d.op := add.U
            d.enaMask := MaskAll
            d.isRegOpd := true.B
        }
        is(ADDI.U) {
            d.op := add.U
            d.enaMask := MaskAll
            d.useDecOpd := true.B
        }
        is(SUB.U) {
            d.op := sub.U
            d.enaMask := MaskAll
            d.isRegOpd := true.B
        }
        is(SUBI.U) {
            d.op := sub.U
            d.enaMask := MaskAll
            d.useDecOpd := true.B
        }
        is(SRA.U) {
            d.op := sra.U
            d.enaMask := MaskAll
        }
        is(LD.U) {
            d.op := ld.U
            d.enaMask := MaskAll
            d.isRegOpd := true.B
        }
        is(LDI.U) {
            d.op := ld.U
            d.enaMask := MaskAll
            d.useDecOpd := true.B
        }
        is(AND.U) {
            d.op := and.U
            d.enaMask := MaskAll
            d.isRegOpd := true.B
        }
        is(ANDI.U) {
            d.op := and.U
            d.enaMask := MaskAll
            noSext := true.B
            d.useDecOpd := true.B
        }
        is(OR.U) {
            d.op := or.U
            d.enaMask := MaskAll
            d.isRegOpd := true.B
        }
        is(ORI.U) {
            d.op := or.U
            d.enaMask := MaskAll
            noSext := true.B
            d.useDecOpd := true.B
        }
        is(XOR.U) {
            d.op := xor.U
            d.enaMask := MaskAll
            d.isRegOpd := true.B
        }
        is(XORI.U) {
            d.op := xor.U
            d.enaMask := MaskAll
            noSext := true.B
            d.useDecOpd := true.B
        }
        is(LDHI.U) {
            d.op := ld.U
            d.enaMask := ((1 << (size / 8)) - 2).U
            d.operand := (sigExt << 8)(size - 1, 0)
            d.useDecOpd := true.B
        }
        is(LDH2I.U) {
            if (size >= 32) {
                d.op := ld.U
                d.enaMask := ((1 << (size / 8)) - 4).U
                d.operand := (sigExt << 16)(size - 1, 0)
                d.useDecOpd := true.B
            }
        }
        is(LDH3I.U) {
            if (size >= 32) {
                d.op := ld.U
                d.enaMask := ((1 << (size / 8)) - 8).U
                d.operand := (sigExt << 24)(size - 1, 0)
                d.useDecOpd := true.B
            }
        }
        is(LDH4I.U) {
            if (size >= 64) {
                d.op := ld.U
                d.enaMask := ((1 << (size / 8)) - 16).U
                d.operand := (sigExt << 32)(size - 1, 0)
                d.useDecOpd := true.B
            }
        }
        is(LDH5I.U) {
            if (size >= 64) {
                d.op := ld.U
                d.enaMask := ((1 << (size / 8)) - 32).U
                d.operand := (sigExt << 40)(size - 1, 0)
                d.useDecOpd := true.B
            }
        }
        is(LDH6I.U) {
            if (size >= 64) {
                d.op := ld.U
                d.enaMask := ((1 << (size / 8)) - 64).U
                d.operand := (sigExt << 48)(size - 1, 0)
                d.useDecOpd := true.B
            }
        }
        is(LDH7I.U) {
            if (size >= 64) {
                d.op := ld.U
                d.enaMask := ((1 << (size / 8)) - 128).U
                d.operand := (sigExt << 56)(size - 1, 0)
                d.useDecOpd := true.B
            }
        }
        is(ST.U) {
            d.nextState := store
        }
        is(LDADDR.U) {
            d.nextState := loadAddr
        }
        is(LDIND.U) {
            d.nextState := loadInd
            d.isDataAccess := true.B
            d.op := ld.U
            d.enaMask := MaskAll
        }
        is(LDINDB.U) {
            d.nextState := loadInd
            d.isDataAccess := true.B
            d.enaByte := true.B
            d.op := ld.U
            d.enaMask := MaskAll
            d.off := sigExt(7 + log2Ceil(size / 8), 0).asSInt
        }
        is(LDINDH.U) {
            if (size >= 32) {
                d.nextState := loadInd
                d.isDataAccess := true.B
                d.enaHalf.get := true.B
                d.op := ld.U
                d.enaMask := MaskAll
                d.off := (sigExt << 1)(7 + log2Ceil(size / 8), 0).asSInt
            }
        }
        is(LDINDW.U) {
            if (size >= 64) {
                d.nextState := loadInd
                d.isDataAccess := true.B
                d.enaWord.get := true.B
                d.op := ld.U
                d.enaMask := MaskAll
                d.off := (sigExt << 2)(7 + log2Ceil(size / 8), 0).asSInt
            }
        }
        is(STIND.U) {
            d.nextState := storeInd
            d.isDataAccess := true.B
        }
        is(STINDB.U) {
            d.nextState := storeIndB
            d.isDataAccess := true.B
            d.off := sigExt(7 + log2Ceil(size / 8), 0).asSInt
        }
        is(STINDH.U) {
            if (size >= 32) {
                d.nextState := storeIndH
                d.isDataAccess := true.B
                d.off := (sigExt << 1)(7 + log2Ceil(size / 8), 0).asSInt
            }
        }
        is(STINDW.U) {
            if (size >= 64) {
                d.nextState := storeIndW
                d.isDataAccess := true.B
                d.off := (sigExt << 2)(7 + log2Ceil(size / 8), 0).asSInt
            }
        }
        is(JAL.U) {
            d.nextState := jal
        }
        is(SCALL.U) {
            d.nextState := scall
        }
    }

    io.dout := d
}