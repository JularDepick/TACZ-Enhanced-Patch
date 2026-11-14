#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""遗迹建筑蓝图工具.

批量处理输入目录下的蓝图文件:
    不带参数: 输入目录为脚本同目录的 .input, 输出目录为脚本同目录的 .output
    --input 只给一个: 该目录同时作为输入与输出
    --output 只给一个: 该目录同时作为输入与输出
    两个都给: 分别作为输入与输出
    --check: 只做格式识别与校验, 不转换; 不给时把输入目录的旧格式转成新格式写到输出目录

输出为单个 JSON 对象; 有文件校验未通过时退出码为 1.
旧版数字方块 ID 到现代方块状态的映射与模组内的 LegacyBlocks 一致, 内嵌为压缩表,
改动 LegacyBlocks 后需要同步重新生成该表.
"""

import argparse
import base64
import gzip
import json
import struct
import sys
from pathlib import Path

SPONGE_SUFFIX = ".schem"
ALPHA_SUFFIX = ".schematic"
DEFAULT_INPUT = ".input"
DEFAULT_OUTPUT = ".output"
DATA_VERSION = 3465
MAX_VOLUME = 4000000

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

_LEGACY_TABLE_B85 = (
    "ABzY8#`gDW0{^{S+j6Tq68#rH^I%=@Wmn}(T)SMdO=2b(Jb;s(Gd2G{YYCifg9JF>sXF_ZAgR06>eUw{_|L2;O&JxlUz^z}P"
    "NRKpUdvxanWfPzxViI|n=~%(@13(GE{@S*>uNm2S?(R+YBcE~D!e0{t>9Y}+g+0F-#0~?eSW>Xnxu$+Hw4McyBqt=<VCb?-"
    "9KlCf3n0+XrJwNNz_(NX5P2w#H8=V-R<I>U-pr!Wf$lBqpRUxm-"
    "H=h4l#SPkBzJ4VDk5^M+Dw3%sGkEw+)v0Eqa;XPiYq&awzZa-Y3i<-z3pTlr+01=EJyK3aroFjT~#%x{39|*!x>qa@c11y+-"
    "3aOItUwY)R`TXP9Jf8}lbFUcP?27QokBt%9$$UJ75c<r`r(#pV!0wdp>3SqA4QFYs5CmM>*~i2|!ika&v@E`z!XIF?R5ld)v"
    "3QyxF9EPZPLH33i?rgV;r)^b*NEub41F3wR}z%E~bgY@ks&GPaXlz9vxa*r6WzBX9*$GC`++BhA}m(tUcW@S{nnm8?>hZFU2"
    "H2FDT)5|<KXP-V<audU`9L+hZB;OX<1@iYO@(`8LzRdEi9ezhXc$ipG#{2iLE%b}2ZK6L|&cipgTdlU7-"
    "e<IHd)<bh=CIveisQXMeUR1X5&nJqnPvALB13R_bKF0{pQWW~Hze`fvBXkc9D}~1B*{KqV@{6V=Qw?D-"
    "^KjDd|cga`!AC^s9*AP;^69*e3xgRsY{qBa@?l-"
    "br3dt$9xWqtx24vjc9m2e>o&LVVUzRJGIz38?1Y8<Jmg+_U+=7WwhrO_;xvrw-"
    "H|1Y3pc6qw*N#u9kmaOkV!u;9I8U<^oU~#CNBI&9Zl7A<65#0cJ(<we@BNyCOcs=3c1)^Zp(ruW=r2Y-"
    "A21Q<ODGEIO`8C%WtF%elVzjIMo~zSn%FQQpo1N)zYB)q9dta5Ty5%PuR+>{NRbcr`;S?sFw=f)r;`f|W~~l42Dxrlj~+i)D"
    "76P=!-ayV2hi<ZhJrIJx~1Q)>k7FOH9yZ}g3ZYpJ0ANu*eer1-Ojh`qFt5df-"
    "?VsBY|@Tyr0m{XRv6VB`XwrjBehoD>Q92)T|GUdjRRll12gk=1%Ib{fem%QEKlBZ@L-"
    "RmTYOYdUDZ|Nq_P^iI6W1<7r?~rA=WAzfMDmdb9^pZrcWso7Ss(ngF{*su(p_zR3<{MnJN&SwC<VersB01LgxM+^`KHb`U9F"
    "yIuoQB>gAJn77#|g4*wH6$`e3~S=%R{?NzY^q}M@0c7v&t@`O-Fy5+DwZ+Tip*9;M-xs$!$DN<-"
    "3nfNNg`wcCMN4E+w=0A1E%(J~xT^Lh<+_8amoXt*$QLYxd=(aCz80=-yMfyqSEvH)-"
    "VR{;$h>w9Ec%j;7e=(LT$)YJQDS@@@uXVl8X)rex>K>HHq2jvO^N&HOjJB#x&hFdIibTBW&X$Y+x38O5K*teNd+TSOv=nr=F"
    "3Jo6rHb|}7zrsDGG9NEV~C=kroi@0BWF4*_<J1n6p?1<%*4+~q*0_qCS-"
    "{D&7`Fq^deEv>#@z+J=)HU9ggZ^e)`}K*~)5%tf9THR}qrXFs2C5RSYYMC;{q6RtUI%kpT*o>CVDQ#wk9VvCqt7SyIrunz1p"
    "=*rO{`Rvnzv%y6NuM=&JN!XjeI@sYvcMk(E<0WCuY{6*sIE)*vrbD*h|!P)swZTaCzd}@_K68RVi)|WheD|cJ;SsI^;>|o}#"
    "4jpyDGQ#-q1({`UXdH-"
    "^|d1C7%ygI`TySlG5^GHnLzA~`Yxc99&L0lR39&49bL`FK9qt;%WWjmm&~l=#z>`UzI)Cp<}Y_6!U4Q=X<$o7!53sCY`M6Vz"
    "^#+wbXXA0_eS<lmu9;^Yf4=iq%F{EYnmj{c;$>YzLlSRItdBCCV+SZEE>^y79lNX?Uy9fhu83jQ(OPm?<zY63V%RFdvTp}Rc"
    "#WDB>954Oza0;5uy*WkIiCEB;7^RcnHB!|3_>_zFJE_pq-"
    "x39T50pVX!?|vf(wQGe#Zr+d)M{jTLCnkwGoqdm0_Srr&^=^)z@Av-l#8KEoc5DpxkR2U?J#<IM-"
    "(KzhAfWZCb~>8lB5%J^A14}}qTD!6>2Oy+-"
    "#T`8dgzsTeCB4(_Lybw8&j4BG+f66x{9kSk>>bAT$udy{ii3=H_lSr#(htezbe78X<eP-nHI|~nqz_NqB$DHE~29$+^x|cz@"
    "l576A>JT+#W^#pnkNc!lr%v;2=5n@q>fp=*JHZnxh{-bZT>&)fx5pp<9pB5FBNX_3^?<4bn+-"
    "q(wSujx|Xq(Xlq!qtS_$XpB|%>2e~9<Bq3fr<1v!{n+fY^bqryg-Ln~h+Sm%jr!!~G?;y0>^`t^b5P~`>Ad@SL;DEJtKCFKw"
    "bMA%Zt^@Uotvd+9vsOcXB(e+|9pq{JxU@B-"
    "4^c`O!e;pmEVnn{U34CvHi#`p>uTDjyoq*ZLfVs`~fl7etA={V`M(Q9vcE<Ih!2nY;0hivP1MzHFoX^9L!fG==E&xCQd)1yu"
    "9B=ZEwTYS$#YEtgH6=Xk#-)KW5NuzKK#+?1#$IYPq?A^xM75<vk}^H=N@nF}d?xeBEPGC-"
    "0;=(#bn%j&<@*qGO$Wk47K&cRlKyh~OwE-"
    ">1mODVlv5f0(Jx(jW2)SHoXMsE$CC_RI&Yz_`vocv`QAA3V)BCm%dL>SGVy@h9&{d*H#-"
    "W)CuS%GA}vo^$YVtqwVOTDxXi?ZSHn=Gg{MBX`)n40)=-)95(b@`O!q*8!Pl?5(?w**t-"
    "d@377jSh<e;JY`0w=g*~m{OAcx+UJv=W&r;eR_y~-&sZ79v8*Ss`M)5>aYXDXX4X1r_5?1i<7!V?l-"
    "8lQCvd6X{~i5q=J(seqbXe_^1LAXmyxGKJ$}+k!AWyurQoDFwo-"
    "5s9a|~%X!IvZbEi5dA~>p2=u_n56u%93Qp@yE9ch~$s$;FwLw2lv_G)&r#TsLy{W_hD=D5>*Hs@?VquH4}j~j1*+B3e-"
    "J9&N1<KkrUxN(x=?`_wKX{!^@$RaY=*B0&{XCYy%9@DxaN>z*PO@59_SNY^<xmG-mrkf(g(OTCP&T%K_2%Eh-"
    "?%OQb%iq^((^?moDid|I_-0b&drrZaqB$CvRXG_j5pp#69s%%s9&P(QS7-5CuLxbz>dE)!@0(Pv<=>e!MJM00=-"
    "L!|dR@(;r`wH*^z^LFrI+5Rmy$K3o<7!S2FX68r!}=csVcMU=`}=Cl{xlKkfzzwiDud>5KXqHQ~g;AlO)%k{;iDbw<-"
    "p8{{Dc)px(D%wHP$u`l*Y7)K5d{Rv*O})V2B=#skxPU_kXvjbRekAKDny`2h{rpX3;*3j3XoL4EDVI|d4CzvwZTYR@-"
    "u^3Lz#_*?H<rcIo?NRP~(yGV~sp}T00&7+5D`|*r&n5w6xJ1VUnsN^5y*41}t{03j2)|&KfF?-%-"
    "N{aPSi782b7cx^4t!wxxN#3r{#E<*_F4MGxZ&zzt%J+-"
    "*l;@I9pj<EEv|m!FR&QFG^}<a{wASmiM86GTTC#kPBF_$2f|qmiSV@*&3j2A=vU+W~^Jw-"
    "%uk6;e>&nn|_6e!qC3lsXRYS*7mb%BJ*^HCs$TY@Db8P<NBsw;6>Cx!pak@vH6A>Jhqx32A2d`tK9p53~M{T!fQyyDG7+60W"
    "Urnh#@4*+hetrmYe>8~DKOg^^XZEWN@!t9A78J=R<3IPtx78f2%-"
    "sCn0o;8brE`BbwnXINXS=o^S$t@I)Gx|E{+3ylY4m^k<akNmDNFXp=OwR4Jo<ZnpVH{>dVA2KzxVa5#1OitK@_VK3;q47lK?"
    "~KGqiWL^*=z$diTD+=TJ@7d+0+ZZnwevJK9oYh=6u8y1!#(mv4#r=kDNB|D&x(hFmT6kuf(W`^d1`b|2$(dW?s)=0^rtLw$V"
    "m4-"
    "d13{?JS+i+^Ou!z)r@;4yU(43CV+s}mOwOoH%uWJJBEl%@MTYJ7bDx~t@B``%Y_HQw$lx!SLLOYX5J&!}pD$<<QrA^9cow^z"
    "GNPHy!+ldH`?vwE+|Eu`ITay8lgCO0Fy<K$|(U$-qH^r87x-"
    "23Hob!o1mtB+$bUELaM>ZWw+rg68zt`2t#=@+)Q+ugD|CAnXHSC?Lm+}nbCI=3d_>TzosZXs?B#MR?(u2<!Q_C1ayGl$=^-"
    "@&Z<(7~_&%*5>1W*&$@1}e}&7%YN$(D)nO<?Poom?`vK1?xb}gH_<aKz}{^B}6a_F+j*bs6gmIgn?KDVtM_~eIJ7t7`zH*OZ"
    "=>Z*=qJn&V$*y`Vn*dg0c|xFC^PY8Aufj{y$g<Ohsz^LQomv7m~^ae!*K}reaC5t3a*;H4hZj(SJ>{5O$-"
    "WIu$4#s4y6C&0(0F0(XhYDR7sVoPq`<Ca0hR328MZFCpzli$KdjV|7F@;F^nv$!SbhVsfN{xoON<LRd|%;I6s4;I6?{fx8A*"
    "1@0mGD{v2?YNhRP#YGKe5UL=AfrLT02*PC$u7YqKEat&N1Pd7~RIt#&0`{{A7F<+SF;`%|AQLcOkO`PCabJV^5==ChFC~6ya"
    "SFd+zJ%<WQ^y5nM#B-oG7OfBV7UyItH5)gb_ii{XQsh@33eLnS7ZnFE3yOo71)JfzXH1u>{nnHg8d5PLTmDiV6_ZZFz|J-"
    "o(F3YtYxrPf#*KhhLCnmGGM<Z8L(eN)gjogq3RIq*U&-"
    ";_G^f>0DCcqTnoVq2)2;d3&jhFxFDwHkZ}?60@N3aY9SXiRxt;R=D^V$D4MVE4`2iu1#}{)n(lVOZgu6fCVIhXNwI*?*^*)b"
    "r?Vx+0$OKFiUq*Vmil@@u`F&E*9#(;5MGip(YqvN!gfW<0OM>$$^hnUMaqQaij;}T6)6*xD^eyZSELNY%~qr==^RKI=$oxb8"
    "F-wnNg42(tw|Xmo2^M1P@Anu8Q`0(_4UF!C_qmDb^?$SfSUl+1nmhB5}GawJ_>pk0E+-"
    "n1VADH5CL$AkN{g#u8=$_S4f_81h7L9(!XIWK=R0+B+40u^hI+{3HZVl=LDGHij!Jchsaqi=nCf*K(W&GFU0~DaYeZRNnBAd"
    "0ceT^-7c6eFe4yR0F44j6hL85J3ZEXO*%lT07?Y_C;&6TNi6Bb(SOPQo$aJUe=dkfL60t0*0mG+oH)I-AUp;A^hN-"
    "o0_YS#rU3W^&?f*t0sIL7u&3R!qbaQ|6DntIY1=?U<<NZ#;#SZj3nEw0CktX%&?^g~SI{pD;#bf!3nEz1H`iCzTzRqhQTP*d"
    "(30Lj&_!<~+<*iwCBQF%dkG{<AXx&*5=ic8w-"
    "jykq;Sf{PtsO4f>IT9^wLU9S1*ZtL1({_5Klrr2>~U<lR&iupe3*@0pp%_8)rispCO%%zUU8VOQ2c;(2~(pvVUj0r9)jxAYC"
    "%(Du!|N2dX7-EP-GN1oyOCI@B#=t6&6@%IbvLVSE*gLQ+#Fj6+gWCyYc=QzzOc4K{0F+CbMU7+@r$s9=bZjH7}<MlzBLh8f9"
    "NDi~-"
    "aqp4u1sjtX`kyS9<NIDR~fFtQaL}*hJa$^+P05TH3LDE&Mg28`r&Y{&ET!{?X1bxGCNVp6M=OE!0B=FwTZs~Ad3!5!*UJK5e"
    "5o{yjBP4u;gpZK$5fVN`0{ar!m%zRR_9d_{foe(d;2R`BFIP;U7<&XGAQCS02Y@92ECFB%080Q^0>Bagmq4xrW+l)nfl~>DN"
    "?=n0m2z$8$^g2NYo=uE|D#_^hk2y}vJ{Z@!(U5BSvtt|fJ_BsDj-"
    "t<nF`2NfTRK#d)h4>;!y#v3P4qWr~>vBu&01M1?(wcuczJ8fh{S<WWnq~*_4|3gR;r=qRL-E0ccb}V|D$|-<As#TANohg-"
    "|xLW)1<`kOZh!Oq!WB1Fgy?!x+zWn7J{~s(@w%m?{8N0h$V+RNB&J3Zg6?nS&^cM<yZ4;*nX1vUp?~vQYq}0u&WMr~o<zxG4"
    "Zl0b(j-"
    "`VD>nih}hkSigexD_FmR^{Yk2kJ*(13KUSHVEzh*uVC>C7O!CO3Kp+m@d_5NVDSnTuVC>C7O!COszM=?pbG8Gf;I{mP{4p%a"
    "pXF9@NZje5Hkw#Q2{p!xKY530&Wy=qktQ=;+W2MONZsgnwG#MjErza-"
    "P%COG)&n*$vjNiK*>Z**+9umOxZxmR7}}G$y`j?K*?lG*+99rJV=@8n6}YvZOa<+U_z#udJ1M_npv)3Ql**Z3T9P$P999F^q"
    "f4H9qBo1C1yu@&T5I-"
    "k!F;W%#JjpoMd){l8Ge|3L?RZh{ZyPe+&Fu;@>LhX=e#F*Wa`$p|Dqr@2h21SuwlP(q2(MVoEZ@(M)wEGaSuaS2DxVOm-zx9"
    "8IK1rZ}2NkxWvwv~?gkFyWO<QZzGO$s|QH<&{iQG;?0bBt<jnl}u7JvtG$0MKkS{wtQr|E19HdTWQ*w(AJvFQgl^ql83D~ZE"
    "0vLPFox@B1&5xG9pS_AQFL+nS-"
    "{aB{K(Yt4d}L+SZlI)}7?R=&czMCF8Hwwx%Ty#zD#WtBDNB_^TNQCF8GV9F&Z|nsHDv{_2oC7=Lw09*n=5S+dl&8X^yE3taN"
    "hw#2oSGkGvemW;oeS+QjN-Dn_N1KAqL)<CufvNe#cfou(AYam+#*&4{!EHr^^4P<K|TU%sHM(>RVY&7txflm$0X<$wRa~hb_"
    "z?=r=G%%-uNe!@RfJ*~b8i3MkZRx1Wib7rga&GJJzyJLo@{lQ~%5(q#"
)


class Tag(object):
    __slots__ = ("type", "value")

    def __init__(self, tag_type, value):
        self.type = tag_type
        self.value = value


class NbtError(Exception):
    pass


def _read_exact(stream, length):
    data = stream.read(length)
    if len(data) != length:
        raise NbtError("标签数据不足")
    return data


def _read_number(stream, fmt):
    return struct.unpack(fmt, _read_exact(stream, struct.calcsize(fmt)))[0]


def read_nbt(path):
    with gzip.open(str(path), "rb") as stream:
        tag_type = _read_number(stream, ">B")
        if tag_type != TAG_COMPOUND:
            raise NbtError("根标签不是复合标签")
        _read_exact(stream, _read_number(stream, ">H"))
        return _read_payload(stream, TAG_COMPOUND)


def _read_payload(stream, tag_type):
    if tag_type == TAG_BYTE:
        return Tag(tag_type, _read_number(stream, ">b"))
    if tag_type == TAG_SHORT:
        return Tag(tag_type, _read_number(stream, ">h"))
    if tag_type == TAG_INT:
        return Tag(tag_type, _read_number(stream, ">i"))
    if tag_type == TAG_LONG:
        return Tag(tag_type, _read_number(stream, ">q"))
    if tag_type == TAG_FLOAT:
        return Tag(tag_type, _read_number(stream, ">f"))
    if tag_type == TAG_DOUBLE:
        return Tag(tag_type, _read_number(stream, ">d"))
    if tag_type == TAG_BYTE_ARRAY:
        length = _read_number(stream, ">i")
        return Tag(tag_type, _read_exact(stream, max(0, length)))
    if tag_type == TAG_STRING:
        length = _read_number(stream, ">H")
        return Tag(tag_type, _read_exact(stream, length).decode("utf-8", "replace"))
    if tag_type == TAG_LIST:
        inner = _read_number(stream, ">B")
        length = _read_number(stream, ">i")
        return Tag(tag_type, [_read_payload(stream, inner) for _ in range(max(0, length))])
    if tag_type == TAG_COMPOUND:
        value = {}
        while True:
            child = _read_number(stream, ">B")
            if child == TAG_END:
                break
            name = _read_payload(stream, TAG_STRING).value
            value[name] = _read_payload(stream, child)
        return Tag(tag_type, value)
    if tag_type == TAG_INT_ARRAY:
        length = _read_number(stream, ">i")
        return Tag(tag_type, [_read_number(stream, ">i") for _ in range(max(0, length))])
    if tag_type == TAG_LONG_ARRAY:
        length = _read_number(stream, ">i")
        return Tag(tag_type, [_read_number(stream, ">q") for _ in range(max(0, length))])
    raise NbtError("不支持的标签类型 %d" % tag_type)


def write_nbt(path, root):
    with gzip.open(str(path), "wb") as stream:
        stream.write(struct.pack(">B", TAG_COMPOUND))
        _write_string(stream, "")
        _write_payload(stream, root)
        stream.write(struct.pack(">B", TAG_END))


def _write_string(stream, text):
    raw = text.encode("utf-8")
    stream.write(struct.pack(">H", len(raw)))
    stream.write(raw)


def _write_payload(stream, tag):
    tag_type = tag.type
    value = tag.value
    if tag_type == TAG_BYTE:
        stream.write(struct.pack(">b", value))
    elif tag_type == TAG_SHORT:
        stream.write(struct.pack(">h", value))
    elif tag_type == TAG_INT:
        stream.write(struct.pack(">i", value))
    elif tag_type == TAG_LONG:
        stream.write(struct.pack(">q", value))
    elif tag_type == TAG_FLOAT:
        stream.write(struct.pack(">f", value))
    elif tag_type == TAG_DOUBLE:
        stream.write(struct.pack(">d", value))
    elif tag_type == TAG_BYTE_ARRAY:
        stream.write(struct.pack(">i", len(value)))
        stream.write(value)
    elif tag_type == TAG_STRING:
        _write_string(stream, value)
    elif tag_type == TAG_LIST:
        inner = TAG_END
        if value:
            inner = value[0].type
        stream.write(struct.pack(">B", inner))
        stream.write(struct.pack(">i", len(value)))
        for item in value:
            _write_payload(stream, item)
    elif tag_type == TAG_COMPOUND:
        for name in value:
            child = value[name]
            stream.write(struct.pack(">B", child.type))
            _write_string(stream, name)
            _write_payload(stream, child)
        stream.write(struct.pack(">B", TAG_END))
    elif tag_type == TAG_INT_ARRAY:
        stream.write(struct.pack(">i", len(value)))
        for item in value:
            stream.write(struct.pack(">i", item))
    elif tag_type == TAG_LONG_ARRAY:
        stream.write(struct.pack(">i", len(value)))
        for item in value:
            stream.write(struct.pack(">q", item))
    else:
        raise NbtError("不支持的标签类型 %d" % tag_type)


def detect_format(root):
    tag_map = root.value if isinstance(root, Tag) else root
    schematic = tag_map.get("Schematic")
    if schematic is not None and schematic.type == TAG_COMPOUND:
        version = schematic.value.get("Version")
        return "sponge", version.value if version is not None else None
    version = tag_map.get("Version")
    if version is not None and version.type == TAG_INT:
        return "sponge", version.value
    blocks = tag_map.get("Blocks")
    data = tag_map.get("Data")
    if blocks is not None and blocks.type == TAG_BYTE_ARRAY and data is not None and data.type == TAG_BYTE_ARRAY:
        return "alpha", None
    return "unknown", None


def _dimension(tag_map, key):
    tag = tag_map.get(key)
    if tag is None or tag.type not in (TAG_SHORT, TAG_INT):
        raise NbtError("缺少尺寸字段 %s" % key)
    return (tag.value & 0xFFFF) if tag.type == TAG_SHORT else tag.value


def _check_size(name, width, height, depth, errors):
    if width <= 0 or height <= 0 or depth <= 0:
        errors.append("%s 尺寸非法 %dx%dx%d" % (name, width, height, depth))
        return 0
    volume = width * height * depth
    if volume > MAX_VOLUME:
        errors.append("%s 方块数超出上限 %d" % (name, volume))
        return 0
    return volume


def _varint_ids(data, volume, name, errors):
    ids = []
    cursor = 0
    total = len(data)
    for _ in range(volume):
        value = 0
        shift = 0
        while True:
            if cursor >= total:
                errors.append("%s 方块数据在中途结束" % name)
                return ids
            current = data[cursor]
            cursor += 1
            value |= (current & 0x7F) << shift
            if (current & 0x80) == 0:
                break
            shift += 7
            if shift > 28:
                errors.append("%s 方块数据含非法变长整数" % name)
                return ids
        ids.append(value)
    return ids


def _state_syntax_ok(text):
    bracket = text.find("[")
    name = text if bracket < 0 else text[:bracket]
    if ":" not in name or name.startswith(":") or name.endswith(":"):
        return False
    if bracket < 0:
        return True
    if not text.endswith("]"):
        return False
    for pair in text[bracket + 1:-1].split(","):
        if "=" not in pair:
            return False
    return True


def validate(root, name):
    fmt, version = detect_format(root)
    errors = []
    info = {"path": name, "format": fmt, "version": version, "valid": False,
            "width": None, "height": None, "length": None, "palette": None, "errors": errors}
    if fmt == "unknown":
        errors.append("%s 无法识别的格式" % name)
        return info
    tag_map = root.value.get("Schematic").value if fmt == "sponge" and "Schematic" in root.value else root.value
    try:
        width = _dimension(tag_map, "Width")
        height = _dimension(tag_map, "Height")
        depth = _dimension(tag_map, "Length")
    except NbtError as error:
        errors.append("%s %s" % (name, error))
        return info
    info["width"] = width
    info["height"] = height
    info["length"] = depth
    volume = _check_size(name, width, height, depth, errors)
    if volume == 0:
        return info
    if fmt == "sponge":
        if version is None or version < 1 or version > 3:
            errors.append("%s 不支持的 Sponge 版本 %s" % (name, version))
            return info
        container = tag_map.get("Blocks")
        if version >= 3:
            if container is None or container.type != TAG_COMPOUND:
                errors.append("%s 版本 3 缺少 Blocks 子标签" % name)
                return info
            container = container.value
        else:
            container = tag_map
        palette = container.get("Palette")
        if palette is None or palette.type != TAG_COMPOUND or not palette.value:
            errors.append("%s 调色板缺失或为空" % name)
            return info
        info["palette"] = len(palette.value)
        palette_ids = set()
        for text, tag in palette.value.items():
            if tag.type != TAG_INT:
                errors.append("%s 调色板取值不是整数: %s" % (name, text))
                continue
            palette_ids.add(tag.value)
            if not _state_syntax_ok(text):
                errors.append("%s 调色板方块状态写法非法: %s" % (name, text))
        data_tag = container.get("Data" if version >= 3 else "BlockData")
        if data_tag is None or data_tag.type != TAG_BYTE_ARRAY or not data_tag.value:
            errors.append("%s 方块数据缺失" % name)
            return info
        ids = _varint_ids(data_tag.value, volume, name, errors)
        if len(ids) == volume:
            missing = sorted({value for value in ids if value not in palette_ids})
            if missing:
                errors.append("%s 方块数据引用了调色板外的编号 %s" % (name, missing[:5]))
    else:
        blocks = tag_map.get("Blocks")
        data = tag_map.get("Data")
        if blocks is None or data is None or len(blocks.value) < volume or len(data.value) < volume:
            errors.append("%s 方块数组短于尺寸" % name)
            return info
        add = tag_map.get("AddBlocks")
        if add is not None and add.type == TAG_BYTE_ARRAY and len(add.value) < (volume + 1) // 2:
            errors.append("%s AddBlocks 长度不足" % name)
    info["valid"] = not errors
    return info


def _load_legacy_table():
    raw = gzip.decompress(base64.b85decode(_LEGACY_TABLE_B85.encode("ascii")))
    data = json.loads(raw.decode("utf-8"))
    table = {}
    for key, value in data["table"].items():
        table[int(key)] = value
    return data["states"], table


def _legacy_state(states, table, legacy_id, data_value):
    if legacy_id == 0:
        return "minecraft:air"
    row = table.get(legacy_id)
    if row is None:
        return "minecraft:stone"
    slot = row if isinstance(row, int) else (row[data_value] if data_value < len(row) else -1)
    if slot < 0:
        return "minecraft:air"
    return states[slot]


def _encode_varint(value, out):
    while (value & 0xFFFFFF80) != 0:
        out.append((value & 0x7F) | 0x80)
        value >>= 7
    out.append(value & 0x7F)


def convert_alpha(root, states, table):
    tag_map = root.value
    width = _dimension(tag_map, "Width")
    height = _dimension(tag_map, "Height")
    depth = _dimension(tag_map, "Length")
    volume = width * height * depth
    blocks = tag_map["Blocks"].value
    meta = tag_map["Data"].value
    add_tag = tag_map.get("AddBlocks")
    add = add_tag.value if add_tag is not None and add_tag.type == TAG_BYTE_ARRAY else b""
    palette = {}
    order = []
    encoded = bytearray()
    for index in range(volume):
        legacy_id = blocks[index] & 0xFF
        if add and (index >> 1) < len(add):
            high = add[index >> 1] & 0xFF
            legacy_id |= ((high & 0x0F) << 8) if (index & 1) == 0 else ((high & 0xF0) << 4)
        state = _legacy_state(states, table, legacy_id, meta[index] & 0xFF)
        if state not in palette:
            palette[state] = len(order)
            order.append(state)
        _encode_varint(palette[state], encoded)
    palette_tag = Tag(TAG_COMPOUND, {})
    for state in order:
        palette_tag.value[state] = Tag(TAG_INT, palette[state])
    root_tag = Tag(TAG_COMPOUND, {
        "Version": Tag(TAG_INT, 2),
        "DataVersion": Tag(TAG_INT, DATA_VERSION),
        "Width": Tag(TAG_SHORT, width),
        "Height": Tag(TAG_SHORT, height),
        "Length": Tag(TAG_SHORT, depth),
        "Offset": Tag(TAG_INT_ARRAY, [0, 0, 0]),
        "PaletteMax": Tag(TAG_INT, len(order)),
        "Palette": palette_tag,
        "BlockData": Tag(TAG_BYTE_ARRAY, bytes(encoded)),
    })
    return root_tag, {"palette": len(order), "block_data": len(encoded)}


def _resolve_dirs(args):
    """返回输入目录, 输出目录, 以及两者是否需要在缺失时创建.

    显式指定的输入目录不创建, 缺失时按空目录处理;
    显式指定的输出目录缺失时创建; 两者都未指定时创建默认的 .input 与 .output.
    """
    script_dir = Path(__file__).resolve().parent
    if args.input and args.output:
        return Path(args.input).resolve(), Path(args.output).resolve(), False, True
    if args.input:
        resolved = Path(args.input).resolve()
        return resolved, resolved, False, False
    if args.output:
        resolved = Path(args.output).resolve()
        return resolved, resolved, True, True
    return (script_dir / DEFAULT_INPUT).resolve(), (script_dir / DEFAULT_OUTPUT).resolve(), True, True


def _collect(directory, suffixes):
    if not directory.is_dir():
        return []
    return [path for path in sorted(directory.iterdir())
            if path.is_file() and path.suffix.lower() in suffixes]


def _check_file(path):
    entry = {"name": path.name, "path": str(path)}
    try:
        root = read_nbt(path)
    except Exception as error:
        entry.update({"format": "unreadable", "version": None, "valid": False, "errors": [str(error)]})
        return entry
    entry.update(validate(root, path.name))
    entry["path"] = str(path)
    return entry


def _convert_file(path, output_dir, states, table):
    entry = _check_file(path)
    if entry.get("format") != "alpha" or not entry.get("valid"):
        entry["converted"] = None
        return entry
    target = output_dir / (path.stem + SPONGE_SUFFIX)
    try:
        root = read_nbt(path)
        converted, meta = convert_alpha(root, states, table)
        target.parent.mkdir(parents=True, exist_ok=True)
        write_nbt(target, converted)
        check = validate(read_nbt(target), target.name)
        entry["converted"] = {
            "target": str(target),
            "palette": meta["palette"],
            "block_data": meta["block_data"],
            "valid": check["valid"],
            "errors": check["errors"],
        }
        entry["valid"] = check["valid"]
    except Exception as error:
        entry["converted"] = {"target": str(target), "valid": False, "errors": [str(error)]}
        entry["valid"] = False
    return entry


def main(argv):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(add_help=True, description="遗迹建筑蓝图批量校验与转换")
    parser.add_argument("--check", action="store_true", help="只做格式识别与校验, 不转换")
    parser.add_argument("--input", default=None, help="输入目录, 默认脚本同目录的 .input")
    parser.add_argument("--output", default=None, help="输出目录, 默认脚本同目录的 .output; 只给一个目录时输入输出同目录")
    args = parser.parse_args(argv)
    input_dir, output_dir, create_input, create_output = _resolve_dirs(args)
    created = {"input": False, "output": False}
    if create_input and not input_dir.is_dir():
        input_dir.mkdir(parents=True, exist_ok=True)
        created["input"] = True
    if not args.check and create_output and not output_dir.is_dir():
        output_dir.mkdir(parents=True, exist_ok=True)
        created["output"] = True

    results = []
    skipped = []
    if args.check:
        for path in _collect(input_dir, (SPONGE_SUFFIX, ALPHA_SUFFIX)):
            results.append(_check_file(path))
    else:
        states, table = _load_legacy_table()
        for path in _collect(input_dir, (ALPHA_SUFFIX,)):
            results.append(_convert_file(path, output_dir, states, table))
        skipped = [str(path) for path in _collect(input_dir, (SPONGE_SUFFIX,))]

    summary = {
        "total": len(results),
        "valid": sum(1 for item in results if item.get("valid")),
        "invalid": sum(1 for item in results if not item.get("valid")),
        "by_format": {},
        "converted": sum(1 for item in results if item.get("converted")),
        "skipped": len(skipped),
    }
    for item in results:
        key = item.get("format", "unknown")
        summary["by_format"][key] = summary["by_format"].get(key, 0) + 1
    report = {
        "tool": "tacz_enhanced_patch.blueprints",
        "mode": "check" if args.check else "convert",
        "input": str(input_dir),
        "output": str(output_dir),
        "input_exists": input_dir.is_dir(),
        "output_exists": output_dir.is_dir(),
        "created": created,
        "ok": summary["invalid"] == 0,
        "files": results,
        "skipped": skipped,
        "summary": summary,
    }
    json.dump(report, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
