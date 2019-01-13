# j4004
An Intel 4004 CPU emulator
This was a fun little project blending two of my skills, hardware and software. I used to be an ASIC engineer (could you have guessed from my username)? I thought it would be fun to write a near cycle-accurate model of the venerable Intel 4004 microprocessor; the first commercially available microprocessor from Intel.
This project is written in Kotlin (I know the project is titled "J"4004), with an optional graphics front-end. The graphics allowed me to visualize the processor in action. I modeled the UI to look like the block diagram you can find on the internet.
I started this project in Go, but I found the performance with the GUI/etc was a little flaky, so I ported it all over to Kotlin.

![Visualizer Program in Action](https://dl.dropboxusercontent.com/s/csgyh05u654fybz/J%204004.jpg?dl=0)

As you can see in this image, the CPU clock is running at about 1000kHz. The real CPU was rated at 750kHz, so we are a little faster at the moment :)
**However**,  the intent of this project was not to make a fast emulator, but rather something that actually models the CPU and its peripherals. 
## Project update as of 01/13/2019

 - 4004 CPU
 - 4001 ROM with I/O read and write ports (read is not working yet)
 - CPU/ROM/IO visualizer (see picture above)
 - ALU is implemented with ADD and SUB
 - All the accumulator commands have been implemented and unit tested
 - LED count program added using the ALU. Down to a loop of 5 instructions now :)
 - Unit tests for all implemented instructions and ROM
 - Current clock performance is 1000kHz
