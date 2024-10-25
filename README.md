# Elite source code for the TI-99/4A

Chance of success meter [....10%....20%.|..30%....40%....50%....60%....70%....80%....90%....100%]

This is a rather insane attempt to convert the 6502 BBC source code for Elite (tape version) into 
TMS9900 source code for the TI-99/4A.

The plan is to start by writing a transpiler that converts 6502 into TMS9900. Then gradually convert the transpiled 
code into efficient TMS9900 code.

The BBC code is writing to the BBC video memory at >6000, which is the cartridge space on the TI-99/4A, so we 
need RAM is this area, and then we need to transfer this to the VDP.



