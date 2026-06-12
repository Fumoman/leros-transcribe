package leros

import chisel3._
import chisel3.util._

import leros.uart._

class MMIOsIO(size: Int, memAddrWidth: Int) extends DataMemIO(size, memAddrWidth) {
    val tx = Output(UInt(1.W))
    val rx = Input(UInt(1.W))
}

/**
  * Leros's Memory-Mapped I/O(MMIO) devices.
  *
  * @param size The bit width of the ALU accumulator register.
  * @param memAddrWidth The bit width of memory bus.
  * @param frequency System clock frequency.
  * @param bufferDepth The depth of buffers in UART module.
  */
class MMIOs(size: Int, memAddrWidth: Int, frequency: Int, bufferDepth: Int) extends Module {
    val io = IO(new MMIOsIO(size, memAddrWidth))

    val uartN1 = Module(new PeripheralUART(frequency, bufferDepth))
    // Add more UART modules or other peripheral devices if needed:
    // val uartN2 = Module(new PeripheralUART(frequency, bufferDepth))
    // ......

    io.tx := uartN1.io.tx
    uartN1.io.rx := io.rx

    val ADDR_UART1_DATA   = 0xf00.U(memAddrWidth.W)
    val ADDR_UART1_STATUS = 0xf04.U(memAddrWidth.W)
    val ADDR_UART1_CTRL   = 0xf08.U(memAddrWidth.W)
    val ADDR_UART1_CMD    = 0xf0c.U(memAddrWidth.W)

    val CMD_NOP = 0x00.U(8.W)
    val CMD_UPDATE = 0x01.U(8.W)

    val uartCtrlRegN1 = RegInit(0.U(8.W)) // bits(1, 0) -> baudRateMode, bits(7, 2) -> reserved.
    uartN1.io.baudRateMode := uartCtrlRegN1(1, 0)

    // Write bus logic
    uartN1.io.txChannel.valid := io.wr && (io.wrAddr === ADDR_UART1_DATA)
    uartN1.io.txChannel.bits  := io.wrData(7, 0)

    when (io.wr && (io.wrAddr === ADDR_UART1_CTRL)) {
        uartCtrlRegN1 := io.wrData(7, 0)
    }

    val rxPopTrigger = io.wr && (io.wrAddr === ADDR_UART1_CMD) && (io.wrData(7, 0) === CMD_UPDATE)
    uartN1.io.rxChannel.ready := rxPopTrigger

    // Read Bus Logic
    val uartStateWireN1 = WireDefault(0.U(size.W))
    uartStateWireN1 := 0.U((size - 2).W) ## uartN1.io.txChannel.ready ## uartN1.io.rxChannel.valid

    io.rdData := MuxLookup(RegNext(io.rdAddr), 0.U(size.W))(Seq(
        ADDR_UART1_DATA   -> (0.U((size - 8).W) ## uartN1.io.rxChannel.bits),
        ADDR_UART1_STATUS -> uartStateWireN1,
        ADDR_UART1_CTRL   -> (0.U((size - 8).W) ## uartCtrlRegN1)
    ))
}

