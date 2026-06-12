package leros.util

import java.io.{File, PrintWriter}

/**
 * Reads a .asm file, assembles it into 16-bit binary machine code,
 * and writes the result as hexadecimal-character strings (one per line)
 * to a .mem file, compatible with Verilog's $readmemh().
 *
 * Usage: Asm2Mem <input.asm>
 * Output: <input>.mem
 */
object Asm2Mem {

  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      println("Usage: Asm2Mem <file_name.asm>")
      sys.exit(1)
    }

    val asmPath = args(0)
    val asmFile = new File(asmPath)

    if (!asmFile.exists()) {
      println(s"Error: file not found: $asmPath")
      sys.exit(1)
    }

    // Derive output path: strip .asm and append .mem
    val outPath = asmPath.stripSuffix(".asm") + ".mem"

    println(s"Assembling: $asmPath")

    val program: Array[Int] = Assembler.getProgram(asmPath)

    val writer = new PrintWriter(outPath)
    try {
      for (instr <- program) {
        // Each instruction is 16-bit wide; format as 4-char hex string
        val hexStr = f"${instr & 0xffff}%04x"
        writer.println(hexStr)
      }
      println(s"Wrote ${program.length} instructions to: $outPath")
    } finally {
      writer.close()
    }
  }
}
