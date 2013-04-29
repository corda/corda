/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_POWERPC_ENCODE_H
#define AVIAN_CODEGEN_ASSEMBLER_POWERPC_ENCODE_H

#ifdef powerpc
#undef powerpc
#endif

namespace avian {
namespace codegen {
namespace powerpc {

namespace isa {
// INSTRUCTION FORMATS
inline int D(int op, int rt, int ra, int d) { return op<<26|rt<<21|ra<<16|(d & 0xFFFF); }
// inline int DS(int op, int rt, int ra, int ds, int xo) { return op<<26|rt<<21|ra<<16|ds<<2|xo; }
inline int I(int op, int li, int aa, int lk) { return op<<26|(li & 0x3FFFFFC)|aa<<1|lk; }
inline int B(int op, int bo, int bi, int bd, int aa, int lk) { return op<<26|bo<<21|bi<<16|(bd & 0xFFFC)|aa<<1|lk; }
// inline int SC(int op, int lev) { return op<<26|lev<<5|2; }
inline int X(int op, int rt, int ra, int rb, int xo, int rc) { return op<<26|rt<<21|ra<<16|rb<<11|xo<<1|rc; }
inline int XL(int op, int bt, int ba, int bb, int xo, int lk) { return op<<26|bt<<21|ba<<16|bb<<11|xo<<1|lk; }
inline int XFX(int op, int rt, int spr, int xo) { return op<<26|rt<<21|((spr >> 5) | ((spr << 5) & 0x3E0))<<11|xo<<1; }
// inline int XFL(int op, int flm, int frb, int xo, int rc) { return op<<26|flm<<17|frb<<11|xo<<1|rc; }
// inline int XS(int op, int rs, int ra, int sh, int xo, int sh2, int rc) { return op<<26|rs<<21|ra<<16|sh<<11|xo<<2|sh2<<1|rc; }
inline int XO(int op, int rt, int ra, int rb, int oe, int xo, int rc) { return op<<26|rt<<21|ra<<16|rb<<11|oe<<10|xo<<1|rc; }
// inline int A(int op, int frt, int fra, int frb, int frc, int xo, int rc) { return op<<26|frt<<21|fra<<16|frb<<11|frc<<6|xo<<1|rc; }
inline int M(int op, int rs, int ra, int rb, int mb, int me, int rc) { return op<<26|rs<<21|ra<<16|rb<<11|mb<<6|me<<1|rc; }
// inline int MD(int op, int rs, int ra, int sh, int mb, int xo, int sh2, int rc) { return op<<26|rs<<21|ra<<16|sh<<11|mb<<5|xo<<2|sh2<<1|rc; }
// inline int MDS(int op, int rs, int ra, int rb, int mb, int xo, int rc) { return op<<26|rs<<21|ra<<16|rb<<11|mb<<5|xo<<1|rc; }
// INSTRUCTIONS
inline int lbz(int rt, int ra, int i) { return D(34, rt, ra, i); }
inline int lbzx(int rt, int ra, int rb) { return X(31, rt, ra, rb, 87, 0); }
inline int lha(int rt, int ra, int i) { return D(42, rt, ra, i); }
inline int lhax(int rt, int ra, int rb) { return X(31, rt, ra, rb, 343, 0); }
// inline int lhz(int rt, int ra, int i) { return D(40, rt, ra, i); }
inline int lhzx(int rt, int ra, int rb) { return X(31, rt, ra, rb, 279, 0); }
inline int lwz(int rt, int ra, int i) { return D(32, rt, ra, i); }
inline int lwzx(int rt, int ra, int rb) { return X(31, rt, ra, rb, 23, 0); }
inline int stb(int rs, int ra, int i) { return D(38, rs, ra, i); }
inline int stbx(int rs, int ra, int rb) { return X(31, rs, ra, rb, 215, 0); }
inline int sth(int rs, int ra, int i) { return D(44, rs, ra, i); }
inline int sthx(int rs, int ra, int rb) { return X(31, rs, ra, rb, 407, 0); }
inline int stw(int rs, int ra, int i) { return D(36, rs, ra, i); }
inline int stwu(int rs, int ra, int i) { return D(37, rs, ra, i); }
inline int stwux(int rs, int ra, int rb) { return X(31, rs, ra, rb, 183, 0); }
inline int stwx(int rs, int ra, int rb) { return X(31, rs, ra, rb, 151, 0); }
inline int add(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 266, 0); }
inline int addc(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 10, 0); }
inline int adde(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 138, 0); }
inline int addi(int rt, int ra, int i) { return D(14, rt, ra, i); }
inline int addic(int rt, int ra, int i) { return D(12, rt, ra, i); }
inline int addis(int rt, int ra, int i) { return D(15, rt, ra, i); }
inline int subf(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 40, 0); }
inline int subfc(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 8, 0); }
inline int subfe(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 136, 0); }
inline int subfic(int rt, int ra, int i) { return D(8, rt, ra, i); }
inline int subfze(int rt, int ra) { return XO(31, rt, ra, 0, 0, 200, 0); }
inline int mullw(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 235, 0); }
// inline int mulhw(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 75, 0); }
inline int mulhwu(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 11, 0); }
// inline int mulli(int rt, int ra, int i) { return D(7, rt, ra, i); }
inline int divw(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 491, 0); }
// inline int divwu(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 459, 0); }
// inline int divd(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 489, 0); }
// inline int divdu(int rt, int ra, int rb) { return XO(31, rt, ra, rb, 0, 457, 0); }
inline int neg(int rt, int ra) { return XO(31, rt, ra, 0, 0, 104, 0); }
inline int and_(int rt, int ra, int rb) { return X(31, ra, rt, rb, 28, 0); }
inline int andi(int rt, int ra, int i) { return D(28, ra, rt, i); }
inline int andis(int rt, int ra, int i) { return D(29, ra, rt, i); }
inline int or_(int rt, int ra, int rb) { return X(31, ra, rt, rb, 444, 0); }
inline int ori(int rt, int ra, int i) { return D(24, rt, ra, i); }
inline int xor_(int rt, int ra, int rb) { return X(31, ra, rt, rb, 316, 0); }
inline int oris(int rt, int ra, int i) { return D(25, rt, ra, i); }
inline int xori(int rt, int ra, int i) { return D(26, rt, ra, i); }
inline int xoris(int rt, int ra, int i) { return D(27, rt, ra, i); }
inline int rlwinm(int rt, int ra, int i, int mb, int me) { return M(21, ra, rt, i, mb, me, 0); }
inline int rlwimi(int rt, int ra, int i, int mb, int me) { return M(20, ra, rt, i, mb, me, 0); }
inline int slw(int rt, int ra, int sh) { return X(31, ra, rt, sh, 24, 0); }
// inline int sld(int rt, int ra, int rb) { return X(31, ra, rt, rb, 27, 0); }
inline int srw(int rt, int ra, int sh) { return X(31, ra, rt, sh, 536, 0); }
inline int sraw(int rt, int ra, int sh) { return X(31, ra, rt, sh, 792, 0); }
inline int srawi(int rt, int ra, int sh) { return X(31, ra, rt, sh, 824, 0); }
inline int extsb(int rt, int rs) { return X(31, rs, rt, 0, 954, 0); }
inline int extsh(int rt, int rs) { return X(31, rs, rt, 0, 922, 0); }
inline int mfspr(int rt, int spr) { return XFX(31, rt, spr, 339); }
inline int mtspr(int spr, int rs) { return XFX(31, rs, spr, 467); }
inline int b(int i) { return I(18, i, 0, 0); }
inline int bl(int i) { return I(18, i, 0, 1); }
inline int bcctr(int bo, int bi, int lk) { return XL(19, bo, bi, 0, 528, lk); }
inline int bclr(int bo, int bi, int lk) { return XL(19, bo, bi, 0, 16, lk); }
inline int bc(int bo, int bi, int bd, int lk) { return B(16, bo, bi, bd, 0, lk); }
inline int cmp(int bf, int ra, int rb) { return X(31, bf << 2, ra, rb, 0, 0); }
inline int cmpl(int bf, int ra, int rb) { return X(31, bf << 2, ra, rb, 32, 0); }
inline int cmpi(int bf, int ra, int i) { return D(11, bf << 2, ra, i); }
inline int cmpli(int bf, int ra, int i) { return D(10, bf << 2, ra, i); }
inline int sync(int L) { return X(31, L, 0, 0, 598, 0); }
// PSEUDO-INSTRUCTIONS
inline int li(int rt, int i) { return addi(rt, 0, i); }
inline int lis(int rt, int i) { return addis(rt, 0, i); }
inline int slwi(int rt, int ra, int i) { return rlwinm(rt, ra, i, 0, 31-i); }
inline int srwi(int rt, int ra, int i) { return rlwinm(rt, ra, 32-i, i, 31); }
// inline int sub(int rt, int ra, int rb) { return subf(rt, rb, ra); }
// inline int subc(int rt, int ra, int rb) { return subfc(rt, rb, ra); }
// inline int subi(int rt, int ra, int i) { return addi(rt, ra, -i); }
// inline int subis(int rt, int ra, int i) { return addis(rt, ra, -i); }
inline int mr(int rt, int ra) { return or_(rt, ra, ra); }
inline int mflr(int rx) { return mfspr(rx, 8); }
inline int mtlr(int rx) { return mtspr(8, rx); }
inline int mtctr(int rd) { return mtspr(9, rd); }
inline int bctr() { return bcctr(20, 0, 0); }
inline int bctrl() { return bcctr(20, 0, 1); }
inline int blr() { return bclr(20, 0, 0); }
inline int blt(int i) { return bc(12, 0, i, 0); }
inline int bgt(int i) { return bc(12, 1, i, 0); }
inline int bge(int i) { return bc(4, 0, i, 0); }
inline int ble(int i) { return bc(4, 1, i, 0); }
inline int beq(int i) { return bc(12, 2, i, 0); }
inline int bne(int i) { return bc(4, 2, i, 0); }
inline int cmpw(int ra, int rb) { return cmp(0, ra, rb); }
inline int cmplw(int ra, int rb) { return cmpl(0, ra, rb); }
inline int cmpwi(int ra, int i) { return cmpi(0, ra, i); }
inline int cmplwi(int ra, int i) { return cmpli(0, ra, i); }
inline int trap() { return 0x7fe00008; } // todo: macro-ify

} // namespace isa

} // namespace powerpc
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_POWERPC_ENCODE_H

