package leros

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

import leros.shared.Constants._

class AluAccuTest extends AnyFlatSpec with ChiselScalatestTester {
    val sizes = Seq(16, 32, 64)

    def stepAlu(dut: AluAccu, op: Int, din: BigInt, enaMask: Int,
                enaByte: Boolean = false, enaHalf: Boolean = false,
                enaWord: Boolean = false, off: Int = 0): BigInt = {
        dut.io.op.poke(op.U)
        dut.io.din.poke(din.U)
        dut.io.enaMask.poke(enaMask.U)
        dut.io.enaByte.poke(enaByte.B)
        dut.io.enaHalf.foreach(_.poke(enaHalf.B))
        dut.io.enaWord.foreach(_.poke(enaWord.B))
        dut.io.off.poke(off.U)
        dut.clock.step()
        dut.io.accu.peek().litValue
    }

    sizes.foreach { size =>
        s"${size}-bits ALU" should "perform LD, NOP correctly" in {
            test(new AluAccu(size)) { dut =>
                val dinN1: BigInt = if (size == 16) 0x0123
                                    else if (size == 32) 0x01234567L
                                    else 0x0123456789abcdefL
                val dinN2 = (BigInt(1) << size) - 1
                val enaMask = (1 << (size / 8)) - 1
                stepAlu(dut, ld, dinN1, enaMask)
                stepAlu(dut, nop, dinN2, enaMask)
                dut.io.accu.expect(dinN1.U)
            }
        }

        s"${size}-bits ALU" should "perform ADD, SUB correctly" in {
            test(new AluAccu(size)) { dut =>
                val dinN1 = (BigInt(1) << size) - 1
                val dinN2 = 1
                val enaMask = (1 << (size / 8)) - 1
                stepAlu(dut, ld, dinN1, enaMask)
                stepAlu(dut, add, dinN2, enaMask)
                dut.io.accu.expect(0.U)
                stepAlu(dut, add, dinN1, enaMask)
                dut.io.accu.expect(dinN1.U)
                stepAlu(dut, sub, dinN1, enaMask)
                dut.io.accu.expect(0.U)
                stepAlu(dut, sub, dinN2, enaMask)
                dut.io.accu.expect(dinN1.U)
            }
        }

        s"${size}-bits ALU" should "perform AND, OR, XOR correctly" in {
            test(new AluAccu(size)) { dut =>
                val dinN1: BigInt = if (size == 16) BigInt("FF00", 16)
                                    else if (size == 32) BigInt("FF00FF00", 16)
                                    else BigInt("FF00FF00FF00FF00", 16)
                val dinN2: BigInt = if (size == 16) BigInt("F0F0", 16)
                                    else if (size == 32) BigInt("F0F0F0F0", 16)
                                    else BigInt("F0F0F0F0F0F0F0F0", 16)
                val exptResAnd: BigInt = if (size == 16) BigInt("F000", 16)
                                         else if (size == 32) BigInt("F000F000", 16)
                                         else BigInt("F000F000F000F000", 16)
                val exptResOr: BigInt = if (size == 16) BigInt("FFF0", 16)
                                        else if (size == 32) BigInt("FFF0FFF0", 16)
                                        else BigInt("FFF0FFF0FFF0FFF0", 16)
                val exptResXor: BigInt = if (size == 16) BigInt("0FF0", 16)
                                         else if (size == 32) BigInt("0FF00FF0", 16)
                                         else BigInt("0FF00FF00FF00FF0", 16)
                val enaMask = (1 << (size / 8)) - 1
                stepAlu(dut, ld, dinN1, enaMask)
                stepAlu(dut, and, dinN2, enaMask)
                dut.io.accu.expect(exptResAnd.U)
                stepAlu(dut, ld, dinN1, enaMask)
                stepAlu(dut, or, dinN2, enaMask)
                dut.io.accu.expect(exptResOr.U)
                stepAlu(dut, ld, dinN1, enaMask)
                stepAlu(dut, xor, dinN2, enaMask)
                dut.io.accu.expect(exptResXor.U)
            }
        }

        s"${size}-bits ALU" should "perform SRA correctly" in {
            test(new AluAccu(size)) { dut =>
                val din = BigInt(1) << (size - 1)
                val enaMask = (1 << (size / 8)) - 1
                stepAlu(dut, ld, din, enaMask)
                stepAlu(dut, sra, 0, enaMask)
                dut.io.accu.expect((din + (din >> 1)).U)
            }
        }

        s"${size}-bits ALU" should "update only selected bytes via enaMask" in {
            test(new AluAccu(size)) { dut =>
                val din = (BigInt(1) << size) - 1
                val enaMaskFull = (1 << (size / 8)) - 1
                val enaMaskTest = if (size == 16) 2
                                  else if (size == 32) 0xa
                                  else 0xaa
                val exptRes = if (size == 16) BigInt("FF00", 16)
                              else if (size == 32) BigInt("FF00FF00", 16)
                              else BigInt("FF00FF00FF00FF00", 16)
                stepAlu(dut, ld, 0, enaMaskFull)
                stepAlu(dut, ld, din, enaMaskTest)
                dut.io.accu.expect(exptRes.U)
            }
        }

        s"${size}-bits ALU" should "load byte with sign extension" in {
            test(new AluAccu(size)) { dut =>
                val din: BigInt = if (size == 16) BigInt("3210", 16)
                                  else if (size == 32) BigInt("76543210", 16)
                                  else BigInt("fedcba9876543210", 16)
                val enaMask = (1 << (size / 8)) - 1
                val exptRes = Seq(0x10, 0x32, 0x54, 0x76, 0x98, 0xba, 0xdc, 0xfe)
                for (i <- 0 until (size / 8)) {
                    stepAlu(dut, ld, din, enaMask, enaByte = true, off = i)
                    dut.io.accu.expect((BigInt(exptRes(i).toByte) & ((BigInt(1) << size) - 1)).asUInt)
                }
            }
        }

        if (size >= 32) {
            s"${size}-bits ALU" should "load half-word with sign extension" in {
                test(new AluAccu(size)) { dut =>
                    val din = if (size == 32) BigInt("76543210", 16)
                              else BigInt("fedcba9876543210", 16)
                    val enaMask = (1 << (size / 8)) - 1
                    val exptRes = Seq(0x3210, 0x7654, 0xba98, 0xfedc)
                    for (i <- 0 until (size / 16)) {
                        stepAlu(dut, ld, din, enaMask, enaHalf = true, off = (i << 1))
                        dut.io.accu.expect((BigInt(exptRes(i).toShort) & ((BigInt(1) << size) - 1)).asUInt)
                    }
                }
            }
        }

        if (size == 64) {
            s"64-bits ALU" should "load word with sign extension" in {
                test(new AluAccu(64)) { dut =>
                    val din = BigInt("fedcba9876543210", 16)
                    val enaMask = (1 << 8) - 1
                    val exptRes = Seq(0x76543210, 0xfedcba98)
                    for (i <- 0 until 2) {
                        stepAlu(dut, ld, din, enaMask, enaWord = true, off = (i << 2))
                        dut.io.accu.expect((BigInt(exptRes(i).toInt) & ((BigInt(1) << 64) - 1)).asUInt)
                    }
                }
            }
        }
        
    }
}