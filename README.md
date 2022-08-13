App specifics:
- Debug build, used for testing - not need usb being connected. 
Instead it can use usb simulation, implemented here - https://github.com/agamula90/UsbAccessory.
This usb simulation app will handle same sort of communication, as communication with real usb device.
It will map request bytes to response bytes with some randomness for failures.
- There are some specifics of release build, related to real usb device, that is used for testing.
When command being sent doesn't contain (), then \r byte should be send together with command's bytes
When received bytes are not of size 7 and first != 0xFE and second != 0x44, then received bytes will contain 1 space at the end.
App has 2 workarounds to handle those 2 specifics: 
  - it automatically add \r byte when command is not raw byte array
  - it automatically trim last byte when bytes are not raw bytes (currently raw bytes identified as bytes with size 7 and static 1st+2nd bytes, mentioned few lines above)

TODO's:
- test charts after updating chart library
- refactor libraries to kotlin
- get rid of deprecation warnings