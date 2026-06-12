package leros

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

import leros.util.Assembler
import org.scalatest.funspec.AnyFunSpec

class InstrMemTest extends AnyFlatSpec with ChiselScalatestTester {
    val codeArr = Assembler.getProgram("testAsm.asm")
    val codeSeq = codeArr.toIndexedSeq.map(_.asUInt(16.W))

    "Instruction Memory" should "load and serve instructions correctly" in {
        test(new InstrMem(8, "testAsm.asm", true)) {dut =>
            for (i <- 0 until codeArr.length) {
                dut.io.addr.poke(i.U)
                dut.clock.step()
                dut.io.instr.expect(codeSeq(i))
            }
        }
    }
}
