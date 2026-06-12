package leros

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline
import leros.util.Assembler

class InstrMemIO(memAddrWidth: Int) extends Bundle {
    val addr = Input(UInt(memAddrWidth.W))
    val instr = Output(UInt(16.W))
}

/**
  * Instruction memory.
  * Verilog generation from "dbgMen = true" results in logic(LUT).
  * For "dbgMen = true", prog is the name of .asm file.
  * For "dbgMen = false", prog is the name of .mem file.
  */
class InstrMem(memAddrWidth: Int, prog: String, dbgMen: Boolean = false) extends Module {
    val io = IO(new InstrMemIO(memAddrWidth))
    if (dbgMen) {
        val code = Assembler.getProgram(prog)
        require((1 << memAddrWidth) >= code.length, "Program too large")
        val program = VecInit(code.toIndexedSeq.map(_.asUInt(16.W)))
        val memReg = RegNext(io.addr, 0.U)
        val index = math.max(1, log2Ceil(code.length))
        io.instr := program(memReg(index - 1, 0))
    } else {
        val instrMem = SyncReadMem((1 << memAddrWidth), UInt(16.W))
        loadMemoryFromFileInline(instrMem, prog, firrtl.annotations.MemoryLoadFileType.Hex)
        io.instr := instrMem.read(io.addr)
    }
}