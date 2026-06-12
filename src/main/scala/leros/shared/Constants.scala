package leros.shared

object Constants {
    val NOP = 0x00
    val ADD = 0x08
    val ADDI = 0x09
    val SUB = 0x0c
    val SUBI = 0x0d
    val SRA = 0x10
    val LD = 0x20
    val LDI = 0x21
    val AND = 0x22
    val ANDI = 0x23
    val OR = 0x24
    val ORI = 0x25
    val XOR = 0x26
    val XORI = 0x27
    val LDHI = 0x29
    val LDH2I = 0x2a
    val LDH3I = 0x2b
    val LDH4I = 0x2c
    val LDH5I = 0x2d
    val LDH6I = 0x2e
    val LDH7I = 0x2f
    val ST = 0x30
    val OUT = 0x39    // Deprecated
    val IN = 0x05     // Deprecated
    val JAL = 0x40
    val LDADDR = 0x50
    val LDIND = 0x60
    val LDINDB = 0x61
    val LDINDH = 0x62
    val LDINDW = 0x63
    val STIND = 0x70
    val STINDB = 0x71
    val STINDH = 0x72
    val STINDW = 0x73
    val BR = 0x80
    val BRZ = 0x90
    val BRNZ = 0xa0
    val BRP = 0xb0
    val BRN = 0xc0
    val SCALL = 0xff
    val BRANCH_MASK = 0xf0

    val nop = 0
    val add = 1
    val sub = 2
    val and = 3
    val or = 4
    val xor = 5
    val ld = 6
    val sra = 7
}