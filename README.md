# Elite source code for the TI-99/4A

This is an attempt to convert the 6502 BBC source code for Elite (tape version) into 
TMS9900 source code for the TI-99/4A.

The plan is to start by writing a transpiler that converts 6502 into TMS9900. Then gradually convert the transpiled 
code into efficient TMS9900 code.

The BBC code is writing to the BBC video memory at >6000, which is the cartridge space on the TI-99/4A, so we 
need RAM is this area, and then we need to transfer those data to the VDP.



