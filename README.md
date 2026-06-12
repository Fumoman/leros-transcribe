# Leros Processor — A Transcribed & Enhanced Chisel Implementation

[![Scala](https://img.shields.io/badge/Scala-2.13.14-red.svg)](https://www.scala-lang.org/)
[![Chisel](https://img.shields.io/badge/Chisel-6.5.0-blue.svg)](https://www.chisel-lang.org/)
[![License](https://img.shields.io/badge/License-BSD%202--Clause-green.svg)](LICENSE)

**Leros** is a tiny, soft-core processor with an *accumulator-based architecture*, originally designed by [Martin Schoeberl](http://www2.imm.dtu.dk/~masca/) and implemented in [Chisel](https://www.chisel-lang.org/). This repository is a **transcribed and enhanced** fork — it reimplements the core design from scratch while adding configurability (16/32/64‑bit), MMIO‑mapped UART, and a comprehensive test suite. It was developed as a hands‑on exercise while studying the book **[Digital Design with Chisel](http://www.imm.dtu.dk/~masca/chisel-book.pdf)**.

---

## Table of Contents

- [Background](#background)
- [About Leros](#about-leros)
- [What This Project Adds](#what-this-project-adds)
- [Architecture Overview](#architecture-overview)
  - [Processor Core](#processor-core)
  - [State Machine](#state-machine)
  - [Instruction Set](#instruction-set)
- [Project Structure](#project-structure)
- [Build & Usage](#build--usage)
  - [Prerequisites](#prerequisites)
  - [Compile & Simulate](#compile--simulate)
  - [Generate Verilog](#generate-verilog)
  - [Assemble `.asm` → `.mem`](#assemble-asm--mem)
- [Testing](#testing)
  - [Test Organization](#test-organization)
  - [Run Tests](#run-tests)
- [FPGA Deployment Notes](#fpga-deployment-notes)
- [References](#references)
- [License](#license)

---

## Background

I started this project while reading **[Digital Design with Chisel](http://www.imm.dtu.dk/~masca/chisel-book.pdf)** by Martin Schoeberl. The book uses Leros as a running example to teach modern hardware design with Chisel — a hardware construction language embedded in Scala. I decided to **transcribe** the original Leros sources line‑by‑line, verify my understanding by making targeted improvements, and eventually **open‑source** the result so others can learn from it as well.

> **Transcribe** here means: I rewrote every module by hand while studying the original, rather than copy‑pasting. This forced me to confront every design decision and gain deep familiarity with Chisel idioms.

---

## About Leros

Leros was created by **Martin Schoeberl** as both a teaching vehicle and a real‑world FPGA microcontroller. It first appeared in a 2011 paper *"Leros: A Tiny Microcontroller for FPGAs"* [[Schoeberl 2011]](#references) and was later revisited in *"Leros: The Return of the Accumulator Machine"* [[Schoeberl et al. 2019]](#references).

**Key characteristics:**

| Aspect | Detail |
| --- | --- |
| **Architecture** | Accumulator‑based — most operations use a single accumulator register |
| **Datapath width** | Originally 16‑bit; this fork supports **16, 32, and 64 bits** |
| **Instruction width** | Fixed **16 bits** (independent of datapath width) |
| **Memory model** | Harvard architecture (separate instruction & data memories) |
| **ISA style** | Minimal RISC — only ~30 instructions, no pipelines |
| **Implementation** | Purely in Chisel, with a 12‑state FSM controller |
| **Target** | FPGAs (vendor‑agnostic, inferred Block RAMs) |

The accumulator architecture was the dominant design pattern in early computers (1950s–1960s). Leros revisits it in the FPGA era, demonstrating that for resource‑constrained embedded workloads the simplicity of a single‑accumulator datapath can yield surprising efficiency — fewer registers, shorter critical paths, and lower LUT utilization compared to a general‑purpose register file.

The **original upstream repository** is at: **[github.com/leros-dev/leros](https://github.com/leros-dev/leros)**  
Project website (with documentation & publications): **[leros-dev.github.io](https://leros-dev.github.io/)**

---

## What This Project Adds

Compared to the original `leros-dev/leros`:

| # | Improvement | Detail |
| --- | --- | --- |
| 1 | **Configurable datapath width** | The entire design is parameterised — a single Scala source tree generates correct hardware for **16‑bit**, **32‑bit**, and **64‑bit** Leros variants. |
| 2 | **MMIO UART peripheral** | A full‑duplex UART (`Tx` + `Rx` + FIFO buffers) is integrated via memory‑mapped I/O. It supports four baud rates (9600–115200) and is usable from Leros assembly programs. |
| 3 | **Complete test suite** | Every module (ALU, decoder, data‑mem, instruction‑mem, UART) has dedicated unit tests. Integration tests run real assembly programs on the full system for all three bit‑widths. |
| 4 | **`Asm2Mem` utility** | A small tool that assembles `.asm` files into `.mem` hex dumps compatible with Verilog `$readmemh()`. |
| 5 | **Example programs** | `LerosSayingHello{16,32,64}.asm` — assembly programs that exercise UART echo and demonstrate the accumulator‑style programming model. |
| 6 | **Improved code readability** | Modules are extensively commented; magic numbers are replaced by named constants; the decoder is structured for clarity. |

---

## Architecture Overview

### Processor Core

```
                    ┌─────────────┐
   imemIO ◄────────►│  InstrMem   │
                    └──────┬──────┘
                           │ instr
                    ┌──────▼──────┐
                    │   Decode    │──── op, operand, enaMask, nextState, …
                    └──────┬──────┘
                           │
   ┌──────────┐     ┌──────▼──────┐     ┌──────────┐
   │  addrReg │◄────┤             ├────►│ DataMem  │
   └──────────┘     │    Leros    │     └──────────┘
   ┌──────────┐     │    Core     │     ┌──────────┐
   │   pcReg  │◄───►│    (FSM)    │◄───►│  MMIOs   │── uartTx / uartRx
   └──────────┘     └──────┬──────┘     └──────────┘
                           │
                    ┌──────▼──────┐
                    │  AluAccu    │── accu (accumulator register)
                    └─────────────┘
```

- **16‑bit fixed‑width instructions** keep code density high regardless of datapath.
- **Harvard architecture** — instruction fetch and data access happen over separate buses.
- The accumulator (`accu`) is the heart of the processor: almost every arithmetic, logical, and load operation writes it.

### State Machine

Execution is controlled by a 12‑state FSM defined in [`State.scala`](src/main/scala/leros/State.scala):

| State | Description |
| --- | --- |
| `fetch` | Fetch instruction, run decoder (most instructions complete here in a single cycle) |
| `loadAddr` | Load effective address into `addrReg` |
| `loadInd` | Indirect load from memory into accumulator |
| `store` | Direct store: accumulator → register file |
| `storeInd` | Indirect store: accumulator → memory[addr + offset] (full word) |
| `storeIndB` | Indirect byte store |
| `storeIndH` | Indirect half‑word store (32/64‑bit only) |
| `storeIndW` | Indirect word store (64‑bit only) |
| `branch` | Evaluate branch condition; update PC if taken |
| `jal` | Jump‑and‑link (function call): MEM[Rn] = PC+1; PC = ACC |
| `scall` | System call — sets the `exit` signal |

Most ALU and register‑register instructions execute entirely in the `fetch` state, giving Leros a **CPI close to 1** for simple code.

### Instruction Set

All instructions are 16 bits wide: **opcode (bits [15:8]) | operand (bits [7:0])**.

#### Register Operations

| Mnemonic | Opcode | Operation |
| --- | --- | --- |
| `add` | `0x08` | ACC = ACC + Rn |
| `sub` | `0x0c` | ACC = ACC − Rn |
| `and` | `0x22` | ACC = ACC & Rn |
| `or`  | `0x24` | ACC = ACC \| Rn |
| `xor` | `0x26` | ACC = ACC ^ Rn |
| `load`| `0x20` | ACC = Rn |

#### Immediate Operations

| Mnemonic | Opcode | Operation |
| --- | --- | --- |
| `addi` | `0x09` | ACC = ACC + imm (sign‑extended) |
| `subi` | `0x0d` | ACC = ACC − imm (sign‑extended) |
| `andi` | `0x23` | ACC = ACC & imm (zero‑extended) |
| `ori`  | `0x25` | ACC = ACC \| imm (zero‑extended) |
| `xori` | `0x27` | ACC = ACC ^ imm (zero‑extended) |
| `loadi` | `0x21` | ACC = imm (sign‑extended) |
| `loadhi` | `0x29` | ACC[15:8] = imm |
| `loadh2i`–`loadh7i` | `0x2a`–`0x2f` | ACC[23:16], …, ACC[63:56] = imm |
| `sra` | `0x10` | ACC = ACC >> 1 (arithmetic) |

#### Memory & Control Flow

| Mnemonic | Opcode | Operation |
| --- | --- | --- |
| `store` | `0x30` | Rn = ACC |
| `ldaddr` | `0x50` | addr = Rn |
| `ldind` | `0x60` | ACC = MEM[addr + offset] |
| `ldindb` | `0x61` | ACC = MEM[addr + offset] (byte, sign‑extended) |
| `ldindh` | `0x62` | ACC = MEM[addr + offset] (half‑word, sign‑extended) |
| `ldindw` | `0x63` | ACC = MEM[addr + offset] (word, sign‑extended) |
| `stind` | `0x70` | MEM[addr + offset] = ACC |
| `stindb` | `0x71` | MEM[addr + offset] = ACC (byte) |
| `stindh` | `0x72` | MEM[addr + offset] = ACC (half‑word) |
| `stindw` | `0x73` | MEM[addr + offset] = ACC (word) |
| `br` | `0x80` | PC += offset (unconditional) |
| `brz` | `0x90` | if ACC == 0 then PC += offset |
| `brnz` | `0xa0` | if ACC != 0 then PC += offset |
| `brp` | `0xb0` | if ACC >= 0 then PC += offset |
| `brn` | `0xc0` | if ACC < 0 then PC += offset |
| `jal` | `0x40` | MEM[Rn] = PC+1; PC = ACC |
| `scall` | `0xff` | System call (exit) |

> **Note:** The `in` (`0x05`) and `out` (`0x39`) instructions from the original Leros are **deprecated** in this fork; MMIO‑mapped UART replaces their function.

---

## Project Structure

```
Leros_transcribe/
├── build.sbt                              # SBT build definition
├── TODO.md                                # Completed task list
│
├── LerosSayingHello16.asm                  # Example: UART "Hello" echo (16-bit)
├── LerosSayingHello16.mem                  #   → assembled .mem for $readmemh
├── LerosSayingHello32.asm                  # Example: UART "Hello" echo (32-bit)
├── LerosSayingHello32.mem
├── LerosSayingHello64.asm                  # Example: UART "Hello" echo (64-bit)
├── LerosSayingHello64.mem
├── testAsm16.asm                           # Minimal UART echo test (16-bit)
├── testAsm32.asm                           # Minimal UART echo test (32-bit)
├── testAsm64.asm                           # Minimal UART echo test (64-bit)
│
├── src/
│   ├── main/scala/leros/
│   │   ├── shared/
│   │   │   └── Constants.scala             # Instruction & ALU opcode constants
│   │   ├── State.scala                     # FSM state enumeration
│   │   ├── AluAccu.scala                   # ALU + accumulator register
│   │   ├── DataMem.scala                   # Data memory (SyncReadMem)
│   │   ├── InstrMem.scala                  # Instruction memory (Block RAM / debug LUT)
│   │   ├── Decode.scala                    # Instruction decoder (combinational)
│   │   ├── Leros.scala                     # Processor core (FSM + datapath)
│   │   ├── LerosTop.scala                  # Top‑level integration (core + mems + MMIO)
│   │   ├── MMIO.scala                      # Memory‑mapped I/O manager (UART registers)
│   │   ├── uart/
│   │   │   ├── UART.scala                  # UART wrapper (Tx + Rx + FIFOs)
│   │   │   ├── UARTRx.scala               # UART receiver
│   │   │   └── UARTTx.scala               # UART transmitter
│   │   └── util/
│   │       ├── Assembler.scala             # Two‑pass assembler (.asm → machine code)
│   │       └── Asm2Mem.scala               # Assembler → Verilog‑compatible .mem file
│   │
│   └── test/scala/leros/
│       ├── AluAccuTest.scala               # ALU unit tests (16/32/64-bit)
│       ├── InstrMemTest.scala              # Instruction memory tests
│       ├── PeripheralUARTTest.scala        # UART Tx/Rx unit tests
│       └── LerosTopTest.scala              # System‑level integration tests
│
├── generated/
│   └── Leros.sv                            # Generated Verilog (core only)
│
└── generated_32/
    └── LerosTop.sv                         # Generated Verilog (32-bit variant)
```

---

## Build & Usage

### Prerequisites

| Tool | Version | Note |
| --- | --- | --- |
| [Scala](https://www.scala-lang.org/) | 2.13.14 | Language runtime |
| [SBT](https://www.scala-sbt.org/) | ≥ 1.x | Scala Build Tool |
| [Chisel](https://www.chisel-lang.org/) | 6.5.0 | Hardware construction DSL (fetched automatically by SBT) |
| [ChiselTest](https://github.com/ucb-bar/chiseltest) | 6.0.0 | Testing framework (fetched automatically by SBT) |

No manual installation of Chisel is needed — SBT resolves it from Maven Central.

### Compile & Simulate

```bash
# Enter SBT shell
sbt

# Compile all sources
compile

# Run all tests
test
```

### Generate Verilog

Verilog can be emitted for synthesis or inspection:

```bash
# Core only (default 32-bit)
sbt "runMain leros.Leros"

# Full system with UART, custom program and parameters
sbt "runMain leros.LerosTop <asm_file> <size> <instrMemAddrWidth> <dataMemAddrWidth> <frequency> <bufferDepth> <targetDir>"
```

**Examples:**

```bash
# 32-bit Leros, 8-bit instruction address, 12-bit data address, 50 MHz, FIFO depth 8
sbt "runMain leros.LerosTop LerosSayingHello32.asm 32 8 12 50000000 8 generated_32"

# 16-bit Leros
sbt "runMain leros.LerosTop LerosSayingHello16.asm 16 8 12 50000000 8 generated_16"

# 64-bit Leros
sbt "runMain leros.LerosTop LerosSayingHello64.asm 64 8 12 50000000 8 generated_64"
```

The generated `.sv` files will appear in the specified target directory.

### Assemble `.asm` → `.mem`

The `Asm2Mem` utility converts Leros assembly into hex‑format memory files suitable for Verilog `$readmemh()`:

```bash
sbt "runMain leros.util.Asm2Mem LerosSayingHello32.asm"
# Output: LerosSayingHello32.mem
```

---

## Testing

This project adopts a **bottom‑up** testing strategy: verify individual modules first, then integrate step‑by‑step.

### Test Organization

| Test Class | Scope | What it verifies |
| --- | --- | --- |
| [`AluAccuTest`](src/test/scala/leros/AluAccuTest.scala) | Unit | All 8 ALU operations, byte‑enable masks, sign‑extension, all three bit‑widths |
| [`InstrMemTest`](src/test/scala/leros/InstrMemTest.scala) | Unit | Debug mode (LUT from `.asm`) and production mode (Block RAM from `.bin`) |
| [`PeripheralUARTTest`](src/test/scala/leros/PeripheralUARTTest.scala) | Unit | UART frame transmit & receive at 115200 bps |
| [`LerosTopTest`](src/test/scala/leros/LerosTopTest.scala) | Integration | Full‑system echo test for **16‑bit, 32‑bit, and 64‑bit** Leros |

### Run Tests

```bash
# Run the entire test suite
sbt test

# Run a single test class
sbt "testOnly leros.AluAccuTest"

# Run with verbose output
sbt "testOnly leros.* -- -v"

# Run a specific test case by name pattern
sbt "testOnly leros.AluAccuTest -- -t \"ADD\""
```

All tests are **parameterised over bit‑width** (`Seq(16, 32, 64)`), so a single `sbt test` exercises every variant.

---

## FPGA Deployment Notes

When synthesizing the generated Verilog (e.g. `LerosTop.sv`) for an FPGA, be aware of the following:

### Instruction Memory Initialization

The generated instruction memory module contains a line like:

```verilog
$readmemh("LerosSayingHello32.mem", Memory);
```

This system call reads an external hex file to initialize the Block RAM at synthesis time. Most FPGA synthesis tools (Vivado, Quartus, etc.) require explicit opt‑in for this feature.

**Option A — Define the `ENABLE_INITIAL_MEM_` macro (recommended for Yosys/OSS flows):**

Add the following argument to your synthesis command:

```
-verilog_define ENABLE_INITIAL_MEM_
```

**Option B — Move `$readmemh` outside the `ifdef` guard:**

In the generated `.sv` file, locate the `$readmemh` call inside the `ifdef ENABLE_INITIAL_MEM_` block and move it outside, so it is always executed:

```verilog
// Before (as generated):
`ifdef ENABLE_INITIAL_MEM_
  $readmemh("LerosSayingHello32.mem", Memory);
`endif

// After (manual edit):
$readmemh("LerosSayingHello32.mem", Memory);
```

### Clock Constraints

- The default system frequency is **50 MHz**. Adjust the `frequency` parameter when generating Verilog if your board uses a different clock.
- The UART baud‑rate generator calculates division factors from `frequency` — make sure to regenerate (or manually adjust `cntLoad` values) if you change the clock.

### Resource Utilization (approximate, 32‑bit, `LerosSayingHello32.asm`)

| Resource | Count |
| --- | --- |
| LUTs | ~400 |
| Flip‑Flops | ~250 |
| Block RAMs | 2 (1 instruction, 1 data) |

Exact numbers depend on the synthesis tool and FPGA family.

---

## References

1. **Martin Schoeberl.** *"Leros: A Tiny Microcontroller for FPGAs"*. Proceedings of the 21st International Conference on Field Programmable Logic and Applications (FPL), 2011. [DOI: 10.1109/FPL.2011.13](https://doi.org/10.1109/FPL.2011.13)

2. **Martin Schoeberl, Thomas B. Preußer, et al.** *"Leros: The Return of the Accumulator Machine"*. Proceedings of ARCS 2019, LNCS 11479, Springer, 2019. [DOI: 10.1007/978-3-030-18656-2_9](https://doi.org/10.1007/978-3-030-18656-2_9)

3. **Martin Schoeberl.** *"Digital Design with Chisel"*. Online book, 2nd edition, 2024. [http://www.imm.dtu.dk/~masca/chisel-book.pdf](http://www.imm.dtu.dk/~masca/chisel-book.pdf)

4. **Leros original project.** Source code repository. [https://github.com/leros-dev/leros](https://github.com/leros-dev/leros)

5. **Leros project website.** Documentation & publications. [https://leros-dev.github.io/](https://leros-dev.github.io/)

6. **Chisel.** Hardware construction language. [https://www.chisel-lang.org/](https://www.chisel-lang.org/)

---

## License

This project is a derivative work based on the original [leros-dev/leros](https://github.com/leros-dev/leros) and is released under the same **BSD 2-Clause** license. See [LICENSE](LICENSE) for details.

> **Acknowledgements:** All credit for the Leros architecture and the original Chisel implementation goes to **Martin Schoeberl** and the Leros contributors. This repository adds parameterisation, MMIO UART, testing infrastructure, and documentation — it would not exist without the excellent foundation provided by the upstream project and the *Digital Design with Chisel* book.
