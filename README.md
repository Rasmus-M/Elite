# Elite source code for the TI-99/4A

This is an attempt to convert the 6502 BBC source code for Elite (tape version) into 
TMS9900 source code for the TI-99/4A.

The plan is to start by writing a transpiler that converts 6502 into TMS9900. Then gradually convert the transpiled 
code into efficient TMS9900 code.

It's not a project that's very likely to succeed. Even if the code is converted, it will most likely be too slow to be 
playable on the TI-99/4A.

The BBC code is writing to the BBC video memory at >6000, which is the cartridge space on the TI-99/4A, so we 
need RAM is this area, and then we need to transfer those data to the TMS9918A VDP periodically. Fortunately the BBC
and the TMS9918A share some characteristics of the video memory layout, like the division into characters.

Because the converted code is much bigger than the original (every 8 bit instruction expands to at least 16 bits), 
it will not fit into the 32K memory expansion. It is expected that the code size can be reduced over time, or it can be 
split into pages banks, but for now it needs an emulator with RAM at >0000, >4000 and >6000 to load. 

The plan is that the generated JSON file can be loaded into the JS99'er emulator.

