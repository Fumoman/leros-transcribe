package leros

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

class LerosTopTest extends AnyFlatSpec with ChiselScalatestTester {
    val sizes = Seq(16, 32, 64)
    val frequency = 5000000
    val cyclePerBit = 43

    def sendFrameToLeros(dut: LerosTop, data: Int): Unit = {
        dut.io.uartRx.poke(0.U)
        dut.clock.step(cyclePerBit)
        for (i <- 0 until 8) {
            val bit = (data >> i) & 1
            dut.io.uartRx.poke(bit.U)
            dut.clock.step(cyclePerBit)
        }
        dut.io.uartRx.poke(1.U)
        dut.clock.step(cyclePerBit)
    }

    sizes.foreach { size =>
        s"${size}-bits Leros" should "echo correctly" in {
            test(new LerosTop(s"testAsm${size}.asm", size, 8, 12, frequency, 8)) { dut =>
                dut.io.uartRx.poke(1.U)
                dut.clock.step(100)
                val sendFrame = fork {
                    sendFrameToLeros(dut, 0x55)
                    dut.clock.step(50)
                }
                dut.io.uartTx.expect(1.U)
                while (dut.io.uartTx.peekInt() == 1) {
                    dut.clock.step()
                }
                for (i <- 0 until (10 * cyclePerBit)) {
                    if ((i % cyclePerBit) == (cyclePerBit / 2)) {
                        dut.io.uartTx.expect(((0x2aa >> (i / cyclePerBit)) & 1).U)
                    }
                    dut.clock.step()
                }
                sendFrame.join()
            }
        }
    }
}