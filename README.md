### App testing
To test app without usb device, accessory app can be used to simulate usb device responses - https://github.com/agamula90/UsbAccessory

### Usb device specifics
- Raw bytes = bytes of size 7 + first 2 bytes are (0xFE,0x44)
- When bytes from app don't contain (), then redundant \r byte should be send at end
- When bytes from device are not raw bytes, then command will contain redundant space at end

### Signing
To sign with ismet.jks use password / alias as telesuper default password

### TODO's:
- remove "storage" module