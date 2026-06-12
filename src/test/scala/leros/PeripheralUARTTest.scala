package leros

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

import leros.uart._

class PeripherialUARTTest extends AnyFlatSpec with ChiselScalatestTester {
    val frequency = 5000000
    val cyclePerBit = 43

    def sendFrame(dut: PeripheralUART, data: Int): Unit = {
        dut.io.baudRateMode.poke(0.U)
        dut.io.rx.poke(0.U)
        dut.clock.step(cyclePerBit)

        for (i <- 0 until 8) {
            val bit = (data >> i) & 1
            dut.io.rx.poke(bit.U)
            dut.clock.step(cyclePerBit)
        }

        dut.io.rx.poke(1.U)
        dut.clock.step(cyclePerBit)
    }

    "PeripherialUART" should "receive a frame correctly" in {
        test(new PeripheralUART(frequency, 8)) { dut =>
            dut.io.rx.poke(1.U)
            dut.io.rxChannel.valid.expect(false.B)
            dut.io.rxChannel.ready.poke(false.B)
            dut.io.txChannel.valid.poke(false.B)
            dut.io.txChannel.bits.poke(0x00.U)
            dut.clock.step(cyclePerBit)
            sendFrame(dut, 0x55)
            while (!dut.io.rxChannel.valid.peekBoolean()) {
                dut.clock.step()
            }
            dut.io.rxChannel.bits.expect(0x55.U)
            dut.io.rxChannel.ready.poke(true.B)
            dut.clock.step()
            dut.io.rxChannel.ready.poke(false.B)
        }
    }

    "PeripherialUART" should "transmit a frame correctly" in {
        test(new PeripheralUART(frequency, 8)) { dut =>
            dut.io.rx.poke(1.U)
            dut.io.rxChannel.ready.poke(false.B)
            dut.io.baudRateMode.poke(0.U)
            dut.io.txChannel.ready.expect(true.B)
            dut.io.txChannel.valid.poke(false.B)
            dut.io.txChannel.bits.poke(0x00.U)
            dut.clock.step(cyclePerBit)
            dut.io.txChannel.valid.poke(true.B)
            dut.io.txChannel.bits.poke(0x55.U)
            dut.clock.step()
            dut.io.txChannel.valid.poke(false.B)
            dut.io.txChannel.bits.poke(0x00.U)
            while (dut.io.tx.peekInt() == 1) {
                dut.clock.step()
            }
            for (i <- 0 until (10 * cyclePerBit)) {
                if ((i % cyclePerBit) == (cyclePerBit / 2)) {
                    dut.io.tx.expect(((0x2aa >> (i / cyclePerBit)) & 1).U)
                }
                dut.clock.step()
            }
        }
    }
}
