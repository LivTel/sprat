// SpratMechanism Arduino Program
// $HeadURL$
// Version: $Revision$

#include <SPI.h>
// Ethernet parameters
#include <Ethernet.h>
#include <Messenger.h>
// We are using Adafruit's DHT library for the humidity sensor for now
// Copy to your libraries directory
#include "DHT.h"
// We are using Zej's copy of the OneWire and Dallas_Temperature libraries
// Copy from ~dev/src/sprat/arduino/ZejLibraries to the Arduino SDK libraries directory
#include <OneWire.h>
#include <DallasTemperature.h>
// We are usiong the i2cdevlib library installed  from jrowberg's tarball for the MPU6050 control
// The I2Cdev and MPU6050 sub-libraries are used out of the tarball.
#include "I2Cdev.h"
#include "MPU6050_6Axis_MotionApps20.h"
#include "Wire.h"

// length of string used for command parsing.
#define STRING_LENGTH                  (16)
#define ERROR_CODE_SUCCESS                      (0)
// errorCodes indicating mechanism positions (rather than an actual error)
// For on|off mechanisms
#define ERROR_CODE_ON                           (-1)
#define ERROR_CODE_OFF                          (-2)
// For in|out mechanisms
#define ERROR_CODE_IN                           (-1)
#define ERROR_CODE_OUT                          (-2)
#define ERROR_CODE_UNKNOWN                      (-3)
// error codes defining an actual error
#define ERROR_CODE_ROT_POS_NOT_ZERO             (1)
#define ERROR_CODE_ILLEGAL_GRISM_POS            (2)
#define ERROR_CODE_ILLEGAL_MIRROR_POS           (3)
#define ERROR_CODE_ILLEGAL_SLIT_POS             (4)
#define ERROR_CODE_ILLEGAL_GRISM_ROT_POS        (5)
#define ERROR_CODE_GRISM_NOT_IN                 (6)
#define ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS (7)
#define ERROR_CODE_NUMBER_OUT_OF_RANGE          (8)

// Pin declarations
#define MPU6050_INTERRUPT              (0)  // MPU6050 INT pin must be connected to Arduino interrupt 0, which is pin 2
#define PIN_MPU6050_INTERRUPT          (2)  // MPU6050 INT pin must be connected to Arduino interrupt 0, which is pin 2
#define PIN_TEMPERATURE                (5)  // All Dallas sensors on this OneWire bus
                                            // Data pin also has a 4.7k pullup resistor connected to the
                                            // 5v line, even though we are powering the Dallas from the 5v
                                            // directory (not using parasitic power mode)
#define PIN_HUMIDITY_0                 (6)  // DHT22, also used for temperature 0 at the moment. 
                                            // Data pin should have a 10k pullup resistor connected to 5v VCC, however
                                            // the phenoptix unit has an inbuilt 5.5k resistor instead.
#define PIN_ARC_LAMP_OUTPUT            (8)
#define PIN_HUMIDITY_1                 (1)
#define PIN_MIRROR_OUTPUT              (22) // IN = HIGH, OUT = LOW (RELAY1)
#define PIN_MIRROR_OUT_INPUT           (23)
#define PIN_SLIT_OUTPUT                (24) // IN = HIGH, OUT = LOW (RELAY2)
#define PIN_MIRROR_IN_INPUT            (25)
#define PIN_GRISM_OUTPUT               (26) // IN = HIGH, OUT = LOW (RELAY3)
#define PIN_SLIT_OUT_INPUT             (27)
#define PIN_GRISM_ROTATE_OUTPUT        (28) // Pos 1 = HIGH, Pos 0 = LOW (RELAY4)
#define PIN_SLIT_IN_INPUT              (29)
#define PIN_W_LAMP_OUTPUT              (30) // (RELAY5)
#define PIN_GRISM_OUT_INPUT            (31)
#define PIN_RELAY6_OUTPUT              (32) // unused
#define PIN_GRISM_IN_INPUT             (33)
#define PIN_RELAY7_OUTPUT              (34) // unused
#define PIN_GRISM_ROT_POS_0_INPUT      (35)
#define PIN_RELAY8_OUTPUT              (36) // unused
#define PIN_GRISM_ROT_POS_1_INPUT      (37)

// Use 12bit conversions for the Dallas sensors
#define TEMPERATURE_PRECISION          (12)

// Ethernet Sheild mac address
// Map to IP address
// Note for the EtherMega you define your own mac address
// This must be unique to the network you are on. Some URLs:
// http://www.freetronics.com/blogs/news/9457961-storing-arduino-ethernet-configuration-in-eeprom#.UycAEVT3Ly0
// http://forum.freetronics.com/viewtopic.php?f=25&t=5520
byte mac[] =     { 0xDE, 0xAD, 0xBE, 0xEF, 0x69, 0x56 };
// on site
//byte ip[]  =     {192, 168, 1, 77 };
//byte gateway[] = {192, 168, 1, 254 };
//byte subnet[]  = {255, 255, 255, 0 };
// ARI testing
byte ip[]  =     {150, 204, 240, 112 }; // lttest2
byte gateway[] = {150, 204, 241, 254 };
byte subnet[]  = {255, 255, 254, 0 };

EthernetServer server(23); // Telnet listens on port 23
EthernetClient client = 0; // Client needs to have global scope so it can be called
			 // from functions outside of loop, but we don't know
			 // what client is yet, so creating an empty object
// Instantiate Messenger object with the default separator (the space character)
// Messenger.cpp::process method in ~dev/src/arduino/arduino-0022/libraries/Messenger/Messenger.cpp
// should be modified so case 10 (LF) drops through to case 13 (CR) and terminates the message
Messenger message = Messenger(); 
// we use a flag separate from client.connected
// so we can recognize when a new connection has been created
boolean connectFlag = 0; 
// string used for command parsing
char string[STRING_LENGTH];
// error number
int errorNumber = 0;

// humidity sensor. Phenoptix DHT22 / AM2302
DHT dht0(PIN_HUMIDITY_0,DHT22);

// Dallas temperature data
OneWire oneWire(PIN_TEMPERATURE);
// Pass our oneWire reference to Dallas Temperature. 
DallasTemperature dallasTemperature(&oneWire);
// Dallas device addresses
DeviceAddress temperatureSensor1 = { 0x28, 0x77, 0x2D, 0x5D, 0x05, 0x00, 0x00, 0x67 };
DeviceAddress temperatureSensor2 = { 0x28, 0x73, 0x35, 0x5D, 0x05, 0x00, 0x00, 0xF9 };
// Extra variables for asynchronous polling of the temperatures
//unsigned long lastTempRequestTime = 0;
//diddly

// gyro variables
// Use default address (0x68) for Sparkfun board
MPU6050 mpu;
bool dmpReady = false;              // set true if DMP init was successful
uint8_t mpuIntStatus;               // holds actual interrupt status byte from MPU
uint16_t dmpPacketSize;             // expected DMP packet size (default is 42 bytes)
uint16_t dmpFifoCount;              // count of all bytes currently in FIFO
uint8_t dmpFifoBuffer[64];          // FIFO storage buffer
// orientation/motion vars
Quaternion dmpQuat;                 // [w, x, y, z]         quaternion container
VectorFloat dmpGravity;             // [x, y, z]            gravity vector
float dmpYPR[3];                       // [yaw, pitch, roll]   yaw/pitch/roll container and gravity vector
volatile bool mpuInterrupt = false; // indicates whether MPU interrupt pin has gone high

// angle in degrees
double gyroAngleX = 0.0;
double gyroAngleY = 0.0;
double gyroAngleZ = 0.0;

// keep track of when sensors were last read
#define LAST_TIME_READ_COUNT             (2)
#define LAST_TIME_READ_INDEX_HUMIDITY0   (0)
#define LAST_TIME_READ_INDEX_TEMPERATURE (1)
unsigned long lastTimeRead[LAST_TIME_READ_COUNT];

// setup
// @see #dht0
// @see #setupDallas
// @see #setupMPU6050
// @see #lastTimeRead
// @see #LAST_TIME_READ_COUNT
void setup()
{
  int i;
  
  // configure pins
  //PIN_HUMIDITY_0
  dht0.begin();
  //PIN_HUMIDITY_1
  pinMode(PIN_ARC_LAMP_OUTPUT,OUTPUT);
  pinMode(PIN_MIRROR_OUTPUT,OUTPUT);
  pinMode(PIN_SLIT_OUTPUT,OUTPUT);
  pinMode(PIN_GRISM_OUTPUT,OUTPUT);
  pinMode(PIN_GRISM_ROTATE_OUTPUT,OUTPUT);
  pinMode(PIN_W_LAMP_OUTPUT,OUTPUT);
  pinMode(PIN_RELAY6_OUTPUT,OUTPUT);
  pinMode(PIN_RELAY7_OUTPUT,OUTPUT);
  pinMode(PIN_RELAY8_OUTPUT,OUTPUT);
  
  pinMode(PIN_MIRROR_OUT_INPUT,INPUT);
  pinMode(PIN_MIRROR_IN_INPUT,INPUT);
  pinMode(PIN_SLIT_OUT_INPUT,INPUT);
  pinMode(PIN_SLIT_IN_INPUT,INPUT);
  pinMode(PIN_GRISM_OUT_INPUT,INPUT);
  pinMode(PIN_GRISM_IN_INPUT,INPUT);
  pinMode(PIN_GRISM_ROT_POS_0_INPUT,INPUT);
  pinMode(PIN_GRISM_ROT_POS_1_INPUT,INPUT);
  
  // initialise lastTimeRead
  for(i=0; i < LAST_TIME_READ_COUNT; i++)
  {
      lastTimeRead[i] = 0;
  }
  // configure serial
  Serial.begin(9600);
  // setup Dallas temperature sensors :- after serial setup so we can log what we have found
  //PIN_TEMPERATURE
  setupDallas();
  // setup MPU6050 gyro/accelerometer
  setupMPU6050();
  // configure ethernet and callback routine
  Ethernet.begin(mac,ip,gateway,subnet);
  server.begin();
  // messenger callback function
  message.attach(messageReady);
}

// Setup the Dallas sensors on the onewire pin.
// @see #TEMPERATURE_PRECISION
// @see #dallasTemperature
// @see #dallasTemperature
void setupDallas()
{
  DeviceAddress tempDeviceAddress; // We'll use this variable to store a found device address
  int numberOfDevices; // Number of temperature devices found

  // Start up the library
  dallasTemperature.begin();
  
  // Grab a count of devices on the wire
  printTime(); Serial.println("setupDallas: Locating OneWire devices...");
  numberOfDevices = dallasTemperature.getDeviceCount();
  
  // locate devices on the bus
  printTime(); Serial.print("setupDallas: Found ");
  Serial.print(numberOfDevices, DEC);
  Serial.println(" OneWire devices.");

  // report parasite power requirements
  printTime(); Serial.print("setupDallas: Parasite power is: "); 
  if (dallasTemperature.isParasitePowerMode()) 
    Serial.println("ON");
  else 
    Serial.println("OFF");
  
  // Loop through each device, print out address
  for(int i=0;i<numberOfDevices; i++)
  {
    // Search the wire for address
    if(dallasTemperature.getAddress(tempDeviceAddress, i))
    {
	printTime(); Serial.print("setupDallas: Found device ");
	Serial.print(i, DEC);
	Serial.print(" with address: ");
	printDallasAddress(tempDeviceAddress);
	Serial.println();
		
	printTime(); Serial.print("setupDallas: Setting resolution to ");
	Serial.println(TEMPERATURE_PRECISION, DEC);
		
	// set the resolution to TEMPERATURE_PRECISION bit (Each Dallas/Maxim device is capable of several different resolutions)
	dallasTemperature.setResolution(tempDeviceAddress, TEMPERATURE_PRECISION);
		
	printTime(); Serial.print("setupDallas: Resolution actually set to: ");
	Serial.print(dallasTemperature.getResolution(tempDeviceAddress), DEC); 
	Serial.println();
    }
    else
    {
      printTime(); Serial.print("setupDallas: Found ghost device at ");
      Serial.print(i, DEC);
      Serial.print(" but could not detect address. Check power and cabling");
    }
  }
}

// Print the address of the specified Dallas temperature deviceAddress in hexidecimal.
// @param deviceAddress The device address to print.
void printDallasAddress(DeviceAddress deviceAddress)
{
  for (uint8_t i = 0; i < 8; i++)
  {
    if (deviceAddress[i] < 16) Serial.print("0");
    Serial.print(deviceAddress[i], HEX);
  }
}

// Setup the MPU6050 gyro/accelerometer
// @see #Wire
// @see #mpu
// @see #MPU6050_INTERRUPT
// @see #dmpDataReady
// @see #mpuIntStatus
// @see #dmpReady
// @see #dmpPacketSize
void setupMPU6050()
{
  uint8_t devStatus;
  
  // join I2C bus (I2Cdev library doesn't do this automatically)
  Wire.begin();
  TWBR = 24; // 400kHz I2C clock (200kHz if CPU is 8MHz)
  printTime(); Serial.println(F("setupMPU6050:Initializing I2C devices..."));
  mpu.initialize();
  printTime(); Serial.println(F("setupMPU6050:Testing MPU6050 device connections..."));
  printTime(); Serial.println(mpu.testConnection() ? F("setupMPU6050:MPU6050 connection successful") : F("setupMPU6050:MPU6050 connection failed"));
  printTime(); Serial.println(F("setupMPU6050: Initializing MPU6050 DMP..."));
  devStatus = mpu.dmpInitialize();
  // Slow down sample rate
  // The arduino gets FIFO overflows followed by Arduino lockup if we use the standard sample rate 
  // (~100-200Hz). 1Hz is sufficient
  // Rate defaults to 4: If gryo sample rate is 8kH this gives a DMP sample rate of 1.6kHz?
  // We also get FIFO overflows and lockups if we use 'D_0_22 inv_set_fifo_rate' = 0x01 and mpu.setRate(50);
  // Therefore change 'D_0_22 inv_set_fifo_rate' in MPU6050_6Axis_MotionApps20.h to 0x09
  printTime(); Serial.print(F("setupMPU6050: MPU6050 DMP Sampling Rate originally:"));
  Serial.print(mpu.getRate());
  Serial.println();
  // At 1Hz the device takes several minutes to slew to each new position - this is no good
  //  Serial.println(F("Setting sample rate to 1Hz..."));
  //  mpu.setRate(999);
  // At 10Hz the slew is fast enough, currently this doesn't seem to lock up the Arduino
  printTime(); Serial.println(F("setupMPU6050:Setting sample rate to 10Hz..."));
  mpu.setRate(9);// if 'D_0_22 inv_set_fifo_rate' is 0x09
  //mpu.setRate(50); // if 'D_0_22 inv_set_fifo_rate' is 0x01
  printTime(); Serial.print(F("setupMPU6050:DMP Sampling Rate now:"));
  Serial.print(mpu.getRate());
  Serial.println();
  // supply your own gyro offsets here, scaled for min sensitivity
  mpu.setXGyroOffset(220);
  mpu.setYGyroOffset(76);
  mpu.setZGyroOffset(-85);
  mpu.setZAccelOffset(1788); // 1688 factory default for my test chip
  // make sure it worked (returns 0 if so)
  if (devStatus == 0)
  {
    // turn on the DMP, now that it's ready
    printTime(); Serial.println(F("setupMPU6050:Enabling DMP..."));
    mpu.setDMPEnabled(true);

    // enable Arduino interrupt detection
    printTime(); Serial.println(F("setupMPU6050:Enabling interrupt detection (Arduino external interrupt 0)..."));
    attachInterrupt(MPU6050_INTERRUPT, dmpDataReady, RISING);
    mpuIntStatus = mpu.getIntStatus();

    // set our DMP Ready flag so the main loop() function knows it's okay to use it
    printTime(); Serial.println(F("setupMPU6050:DMP ready! Waiting for first interrupt..."));
    dmpReady = true;

    // get expected DMP packet size for later comparison
    dmpPacketSize = mpu.dmpGetFIFOPacketSize();
  }
  else
  {
    // ERROR!
    // 1 = initial memory load failed
    // 2 = DMP configuration updates failed
    // (if it's going to break, usually the code will be 1)
    printTime(); Serial.print(F("setupMPU6050:DMP Initialization failed (code "));
    Serial.print(devStatus);
    Serial.println(F(")"));
  }
}

// Interrupt routine called when the MPU6050 DMP (digital motion processor) has completed a calculation.
// This should get called at the rate setup by mpu.setRate.
// Here we set a flag (mpuInterrupt) wheich in the main loop causes us to read out the FIFO and retrieve the data.
// @see #mpuInterrupt
void dmpDataReady() 
{
  mpuInterrupt = true;
}


// main loop
// @see #server
// @see #client
// @see #monitorSensors
void loop()
{
  // check if new connecion available
  if(server.available() && !connectFlag)
  {
      connectFlag = 1;
      client = server.available();
      printTime(); Serial.println("loop:New client connected.");
  }
  // check for input from client
  if(client.connected() && client.available())
  {
      printTime(); Serial.print("loop:Reading characters from Ethernet:");
      while(client.available())
      {
        char ch;
        
        ch = client.read();
        Serial.print(ch);
        message.process(ch);
      }
      Serial.println();
  }
  // do other stuff here
  monitorSensors();
  checkMPU6050();
  // wait a bit to stop the arduino locking up
  delay(10);
}

// Monitor humidity,temperature periodically
// @see #dht0
// @see #dallasTemperature
// @see #LAST_TIME_READ_INDEX_HUMIDITY0
// @see #LAST_TIME_READ_INDEX_TEMPERATURE
// @see #LAST_TIME_READ_COUNT
// @see #lastTimeRead
void monitorSensors()
{
  unsigned long nowTime;
  int i;
  boolean retval;
  
  // get current time
  nowTime = millis();
  // check to see if time has wrapped (reset to 0)
  // nowTime should always be greater than or equal to each lastTimeRead variable.
  // If it isn't the internal clock must have wrapped to 0. Set lastTimeRead to nowTime
  // so sensor readings resume in a few seconds
  for(i=0; i < LAST_TIME_READ_COUNT; i++)
  {
    if(nowTime < lastTimeRead[i])
    {
        printTime(); Serial.print("monitorSensors:lastTimeRead[");
        Serial.print(i);
        Serial.print("]in the past:Reseting to nowTime ");
        Serial.print(nowTime);
        Serial.println(".");
        lastTimeRead[i] = nowTime;
    }
  }
  // The humidity sensor should only be read about once every 2 seconds
  // The DHT library has an internal clock and returns old values if read is queried more often than that.
  // However this is not available external to the library, so we have our own external clock here.
  if((nowTime - lastTimeRead[LAST_TIME_READ_INDEX_HUMIDITY0]) > 3000)
  {
    printTime(); Serial.println("monitorSensors:Reading dht0.");
    retval = dht0.read();
    if(retval)
    {
        printTime(); Serial.println("monitorSensors:Read dht0 successfully.");
        lastTimeRead[LAST_TIME_READ_INDEX_HUMIDITY0] = millis();
    }
    else
    {
        printTime(); Serial.println("monitorSensors:Failed to read dht0.");
    }
  }
  if((nowTime - lastTimeRead[LAST_TIME_READ_INDEX_TEMPERATURE]) > 5000)
  {
    printTime(); Serial.println("monitorSensors:Reading dallas temperatures.");
    dallasTemperature.requestTemperatures();
    printTime(); Serial.println("monitorSensors:Read dallas temperatures.");
    lastTimeRead[LAST_TIME_READ_INDEX_TEMPERATURE] = millis();
  }
}

// Routine to check whether the MPU6050 DMP interrupt flag is set.
// If it is set and the FIFO buffer has data, read the data until the FIFO is empty.
// Process the data and update the gyro angles accordingly
// @see #dmpReady
// @see #mpuInterrupt
// @see #dmpFifoCount
// @see #dmpPacketSize
// @see #mpuIntStatus
// @see #gyroAngleX
// @see #gyroAngleY
// @see #gyroAngleZ
void checkMPU6050()
{
    printTime(); Serial.println("checkMPU6050:Started.");
    if (!dmpReady)
    { 
      printTime(); Serial.println("checkMPU6050:dmpReady was false:terminating.");
      return;
    }
    // wait for MPU interrupt or extra packet(s) available
    if(!mpuInterrupt && dmpFifoCount < dmpPacketSize)
    {
      printTime(); Serial.println("checkMPU6050:mpuInterrupt was false or dmpFifoCount was too small:terminating.");
      return;
    }
    // reset interrupt flag and get INT_STATUS byte
    mpuInterrupt = false;
    mpuIntStatus = mpu.getIntStatus();

    // get current FIFO count
    dmpFifoCount = mpu.getFIFOCount();

    // check for overflow (this should never happen unless our code is too inefficient)
    if ((mpuIntStatus & 0x10) || dmpFifoCount == 1024)
    {
        // reset so we can continue cleanly
        printTime(); Serial.println(F("checkMPU6050: FIFO overflow!"));
        mpu.resetFIFO();
       printTime();  Serial.println(F("checkMPU6050: FIFO reset."));
    // otherwise, check for DMP data ready interrupt (this should happen frequently)
    } 
    else if (mpuIntStatus & 0x02)
    {
        printTime(); Serial.println(F("checkMPU6050: Waiting for FIFO to fill."));
        // wait for correct available data length, should be a VERY short wait
        while (dmpFifoCount < dmpPacketSize) 
          dmpFifoCount = mpu.getFIFOCount();

        printTime(); Serial.println(F("checkMPU6050:Reading FIFO."));
        // read a packet from FIFO
        mpu.getFIFOBytes(dmpFifoBuffer, dmpPacketSize);
        
        // track FIFO count here in case there is > 1 packet available
        // (this lets us immediately read more without waiting for an interrupt)
        dmpFifoCount -= dmpPacketSize;

        // display Euler angles in degrees
        mpu.dmpGetQuaternion(&dmpQuat, dmpFifoBuffer);
        mpu.dmpGetGravity(&dmpGravity, &dmpQuat);
        mpu.dmpGetYawPitchRoll(dmpYPR, &dmpQuat, &dmpGravity);
        // yaw = Z
        gyroAngleZ = dmpYPR[0] * 180/M_PI;
        // pitch = X
        gyroAngleX = dmpYPR[1] * 180/M_PI;
        // roll = Y
        gyroAngleY = dmpYPR[2] * 180/M_PI;
        printTime(); Serial.print("checkMPU6050: yaw pitch roll = ");
        Serial.print(gyroAngleZ);
        Serial.print(",");
        Serial.print(gyroAngleX);
        Serial.print(",");
        Serial.println(gyroAngleY);
    }
}

// Messenger callback function
// handles commands of the form:
// help
// arclamp [on|off]
// grism [in|out]
// gyro
// humidity <n>
// mirror [in|out]
// rotation [0|1]
// slit [in|out]
// temperature <n>
// wlamp [on|off]
// Engineering commands:
// relay <n> [on|off]
// @see #arcLampOn
// @see #arcLampOff
// @see #getArcLampStatus
// @see #grismIn
// @see #grismOut
// @see #getGrismStatus
// @see #getGyroPosition
// @see #gyroAngleX
// @see #gyroAngleY
// @see #gyroAngleZ
// @see #getHumidity
// @see #getTemperature
// @see #mirrorIn
// @see #mirrorIn
// @see #getMirrorStatus
// @see #relayInOut
// @see #rotation
// @see #getRotation
// @see #slitIn
// @see #slitOut
// @see #getSlitStatus
// @see #wLampOn
// @see #wLampOff
// @see #getWLampStatus
// @see #ERROR_CODE_SUCCESS
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_ILLEGAL_GRISM_ROT_POS
// @see #ERROR_CODE_ILLEGAL_GRISM_POS
// @see #ERROR_CODE_ILLEGAL_MIRROR_POS
// @see #ERROR_CODE_ILLEGAL_SLIT_POS
// @see #ERROR_CODE_ROT_POS_NOT_ZERO
// @see #ERROR_CODE_GRISM_NOT_IN
// @see #ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS
// @see #ERROR_CODE_NUMBER_OUT_OF_RANGE
void messageReady()
{
  double dvalue;
  int errorCode,position,sensorNumber,rotationPosition,relayNumber,isOn,pinNumber,currentState;
  
  printTime(); Serial.println("messageReady:Processing message.");
  if(message.available())
  {
    if(message.checkString("arclamp"))
    {
      if(message.checkString("on"))
      {
        errorCode = arcLampOn();
      }
      else if(message.checkString("off"))
      {
        errorCode = arcLampOff();
      }
      else
      {
         errorCode = getArcLampStatus();
      }
      switch(errorCode)
      {
        case ERROR_CODE_ON:
          client.println("on");
          break;
        case ERROR_CODE_OFF:
          client.println("off");
          break;
        default:
          client.print("error ");
          client.println(errorCode);
          break;
      }
    }   
    else if(message.checkString("grism"))
    {
      if(message.checkString("in"))
      {
        errorCode = grismIn();
      }
      else if(message.checkString("out"))
      {
        errorCode = grismOut();
      }
      else
      {
         errorCode = getGrismStatus();
      }
      switch(errorCode)
      {
        case ERROR_CODE_IN:
          client.println("in");
          break;
        case ERROR_CODE_OUT:
          client.println("out");
          break;
        case ERROR_CODE_UNKNOWN:
          client.println("unknown");
          break;
        case ERROR_CODE_ROT_POS_NOT_ZERO:
          client.print("error ");
          client.print(ERROR_CODE_ROT_POS_NOT_ZERO);
          client.println(" Grism Rotation not in position 0: Failing to start move.");
          break;
        case ERROR_CODE_ILLEGAL_GRISM_POS:
          client.print("error ");
          client.print(ERROR_CODE_ILLEGAL_GRISM_POS);
          client.println(" Illegal Grism Current Position: Both IN and OUT sensors are high.");
          break;
       default:
          client.print("error ");
          client.println(errorCode);
          break;
      }
    }   
    else if(message.checkString("gyro"))
    {
      // diddly rewrite, use dmp variables to determine whether there is an error
      // delete getGyroPosition
      errorCode = getGyroPosition();
      switch(errorCode)
      {
        case ERROR_CODE_SUCCESS:
          client.print("ok ");
          client.print(gyroAngleX);
          client.print(" ");
          client.print(gyroAngleY);
          client.print(" ");
          client.println(gyroAngleZ);
          break;
        default:
          client.print("error ");
          client.println(errorCode);
          break;
      }      
    }   
    else if(message.checkString("help"))
    {
      client.println("Sprat Mechanism help:");
      client.println("help");
      client.println("arclamp [on|off]");
      client.println("grism [in|out]");
      client.println("gyro");
      client.println("humidity <n>");
      client.println("input [mirrorout|mirrorin|slitout|slitin|grismout|grismin|rotpos0|rotpos1]");
      client.println("mirror [in|out]");
      client.println("rotation [0|1]");
      client.println("slit [in|out]");
      client.println("temperature <n>");
      client.println("wlamp [on|off]");
      client.println("Engineering commands:");
      client.println("relay <n> [on|off]");
    }   
    else if(message.checkString("humidity"))
    {
      sensorNumber = message.readInt();
      dvalue = getHumidity(sensorNumber);
      client.print("ok ");
      client.println(dvalue);
    }   
    else if(message.checkString("input"))
    {
      pinNumber = -1;
      if(message.checkString("mirrorout"))
      {
        pinNumber = PIN_MIRROR_OUT_INPUT;
      }
      else if(message.checkString("mirrorin"))
      {
        pinNumber = PIN_MIRROR_IN_INPUT;
      }
      else if(message.checkString("slitout"))
      {
        pinNumber = PIN_SLIT_OUT_INPUT;
      }
      else if(message.checkString("slitin"))
      {
        pinNumber = PIN_SLIT_IN_INPUT;
      }
      else if(message.checkString("grismout"))
      {
        pinNumber = PIN_GRISM_OUT_INPUT;
      }
      else if(message.checkString("grismin"))
      {
        pinNumber = PIN_GRISM_IN_INPUT;
      }
      else if(message.checkString("rotpos0"))
      {
        pinNumber = PIN_GRISM_ROT_POS_0_INPUT;
      }
      else if(message.checkString("rotpos1"))
      {
        pinNumber = PIN_GRISM_ROT_POS_1_INPUT;
      }
      if(pinNumber != -1)
      {
        currentState = digitalRead(pinNumber);
        client.println(currentState);
      }
      else
      {
        client.println("error Unknown input");
      }
    }
    else if(message.checkString("mirror"))
    {
      if(message.checkString("in"))
      {
        errorCode = mirrorIn();
      }
      else if(message.checkString("out"))
      {
        errorCode = mirrorOut();
      }
      else
      {
         errorCode = getMirrorStatus();
      }
      switch(errorCode)
      {
        case ERROR_CODE_IN:
          client.println("in");
          break;
        case ERROR_CODE_OUT:
          client.println("out");
          break;
        case ERROR_CODE_UNKNOWN:
          client.println("unknown");
          break;
        case ERROR_CODE_ILLEGAL_MIRROR_POS:          
          client.print("error ");
          client.print(ERROR_CODE_ILLEGAL_MIRROR_POS);
          client.println(" Illegal Mirror Current Position: Both IN and OUT sensors are high.");
          break;
        default:
          client.print("error ");
          client.println(errorCode);
          break;
      }
    }
    else if(message.checkString("relay"))
    {
      relayNumber = message.readInt();
      if(message.checkString("on"))
        isOn = 1;
      else  if(message.checkString("off"))
        isOn = 0;
      else
      {
          isOn = -1;
          message.copyString(string,STRING_LENGTH);
          client.print("error Illegal onoff argument for relay command:");
          client.println(string);
      }
      if(isOn > -1)
      {
          errorCode = relayOnOff(relayNumber,isOn);
          switch(errorCode)
          {
            case ERROR_CODE_ON:
              client.println("on");
              break;
            case ERROR_CODE_OFF:
              client.println("off");
              break;
            case ERROR_CODE_GRISM_NOT_IN:
              client.print("error ");
              client.print(ERROR_CODE_GRISM_NOT_IN);
              client.println(" Grism was not IN: cannot move grism rotation mechanism in.");
              break;
            case ERROR_CODE_ROT_POS_NOT_ZERO:
              client.print("error ");
              client.print(ERROR_CODE_ROT_POS_NOT_ZERO);
              client.println(" Grism Rotation was not in position 0: cannot move grism mechanism.");
              break;
            case ERROR_CODE_NUMBER_OUT_OF_RANGE:
              client.print("error ");
              client.print(ERROR_CODE_NUMBER_OUT_OF_RANGE);
              client.println(" Input parameter was out of range.");
              break;
            default:
              client.print("error ");
              client.println(errorCode);
              break;
          }
      }
    }
    else if(message.checkString("rotation"))
    {
      if(message.checkString("0"))
      {
        rotationPosition = 0;
        errorCode = rotation(rotationPosition);
      }        
      else if(message.checkString("1"))
      {
        rotationPosition = 1;
        errorCode = rotation(rotationPosition);
      }
      else
      {
        errorCode = getRotation();
      }
      switch(errorCode)
      {
        case 0:
          client.println("0");
          break;
        case 1:
          client.println("1");
          break;
        case ERROR_CODE_UNKNOWN:
          client.println("unknown");
          break;
        case ERROR_CODE_ILLEGAL_GRISM_ROT_POS:
          client.print("error ");
          client.print(ERROR_CODE_ILLEGAL_GRISM_ROT_POS);
          client.println(" Illegal Grism Rotation position: Both 0 and 1 sensors are high at the same time.");
          break;
        case ERROR_CODE_GRISM_NOT_IN:
          client.print("error ");
          client.print(ERROR_CODE_GRISM_NOT_IN);
          client.println(" Grism was not IN: cannot move grism rotation mechanism to position 1.");
          break;
        case ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS:
          client.print("error ");
          client.print(ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS);
          client.println(" Illegal grism rotation target position.");
          break;
       default:
          client.print("error ");
          client.println(errorCode);
          break;
      }
    }   
    else if(message.checkString("slit"))
    {
      if(message.checkString("in"))
      {
        errorCode = slitIn();
      }
      else if(message.checkString("out"))
      {
        errorCode = slitOut();
      }
      else
      {
         errorCode = getSlitStatus();
      }
      switch(errorCode)
      {
        case ERROR_CODE_IN:
          client.println("in");
          break;
        case ERROR_CODE_OUT:
          client.println("out");
          break;
        case ERROR_CODE_UNKNOWN:
          client.println("unknown");
          break;
        case ERROR_CODE_ILLEGAL_SLIT_POS:          
          client.print("error ");
          client.print(ERROR_CODE_ILLEGAL_SLIT_POS);
          client.println(" Illegal Slit Current Position: Both IN and OUT sensors are high.");
          break;
        default:
          client.print("error ");
          client.println(errorCode);
          break;
      }
    }   
    else if(message.checkString("temperature"))
    {
      sensorNumber = message.readInt();
      dvalue = getTemperature(sensorNumber);
      client.print("ok ");
      client.println(dvalue);
    }   
    else if(message.checkString("wlamp"))
    {
      if(message.checkString("on"))
      {
        errorCode = wLampOn();
      }
      else if(message.checkString("off"))
      {
        errorCode = wLampOff();
      }
      else
      {
         errorCode = getWLampStatus();
      }
      switch(errorCode)
      {
        case ERROR_CODE_ON:
          client.println("on");
          break;
        case ERROR_CODE_OFF:
          client.println("off");
          break;
        default:
          client.print("error ");
          client.println(errorCode);
          break;
      }
    }   
    else
    {
      message.copyString(string,STRING_LENGTH);
      client.print("error Unknown command:");
      client.println(string);
    }
  }
  // close connection to client
  client.stop();
  connectFlag = 0;
  client = 0;
}

// Try and turn the arclamp on.
// @return Return an error code: ERROR_CODE_ON on success, or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
// @see #PIN_ARC_LAMP_OUTPUT
int arcLampOn()
{
  printTime(); Serial.println("arcLampOn:Started.");
  digitalWrite(PIN_ARC_LAMP_OUTPUT,HIGH);
  return ERROR_CODE_ON;
}

// Try and turn the arclamp on.
// @return Return an error code: ERROR_CODE_OFF on success, or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
// @see #PIN_ARC_LAMP_OUTPUT
int arcLampOff()
{
  printTime(); Serial.println("arcLampOff:Started.");
  digitalWrite(PIN_ARC_LAMP_OUTPUT,LOW);
  return ERROR_CODE_OFF;
}

// Return an integer describing whether the arclamp is turned on, or off.
// @return Return an error code: ERROR_CODE_ON | ERROR_CODE_OFF on success, 
//         or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
// @see #PIN_ARC_LAMP_OUTPUT
int getArcLampStatus()
{
  int currentState;

  printTime(); Serial.println("getArcLampStatus:Started.");
  currentState = digitalRead(PIN_ARC_LAMP_OUTPUT);
  //currentState = bitRead(PORTD,PIN_ARC_LAMP_OUTPUT);
  if(currentState == HIGH)
    return ERROR_CODE_ON;
  else
    return ERROR_CODE_OFF;
}

// Try and move the grism into the beam.
// We can only move the grism if the grism rotation is in position 0 (due to mechanism interlocking constraints).
// If the grism rotation cylinder is not stowed, it may be damaged by moving the grism in or out.
// @return Return an error code: ERROR_CODE_OUT or ERROR_CODE_IN or ERROR_CODE_UNKNOWN (the current grism position) 
//         if we start moving the grism successfully, or a non-zero number for failure. 
//         Error 1 (ERROR_CODE_ROT_POS_NOT_ZERO) is: Grism Rotation not in position 0: Failing to start move.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_ROT_POS_NOT_ZERO
// @see #getGrismStatus
// @see #getRotation
int grismIn()
{
  int grismRotationStatus;
  
  printTime(); Serial.println("grismIn:Started.");
  // We can only move the grism if the grism rotation is in position 0.
  grismRotationStatus = getRotation();
  if(grismRotationStatus != 0) // The grism rotation cylinder is not stowed
  {
    printTime(); Serial.println("grismIn:Error:Grism Rotation not in position 0: Failing to start move.");
    return ERROR_CODE_ROT_POS_NOT_ZERO;
  }
  digitalWrite(PIN_GRISM_OUTPUT,HIGH);
  return getGrismStatus();
}

// Try and move the grism out of the beam.
// We can only move the grism if the grism rotation is in position 0 (due to mechanism interlocking constraints).
// If the grism rotation cylinder is not stowed, it may be damaged by moving the grism in or out.
// @return Return an error code: ERROR_CODE_IN or ERROR_CODE_OUT or ERROR_CODE_UNKNOWN (the current grism position) 
//         if we start moving the grism successfully, or a non-zero number for failure.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_ROT_POS_NOT_ZERO
// @see #getGrismStatus
int grismOut()
{
  int grismRotationStatus;
  
  printTime(); Serial.println("grismOut:Started.");
  // We can only move the grism if the grism rotation is in position 0.
  grismRotationStatus = getRotation();
  if(grismRotationStatus != 0) // The grism rotation cylinder is not stowed
  {
    printTime(); Serial.println("grismOut:Error:Grism Rotation not in position 0: Failing to start move.");
    return ERROR_CODE_ROT_POS_NOT_ZERO;
  }
  digitalWrite(PIN_GRISM_OUTPUT,LOW);
  return getGrismStatus();
}

// Get the current status of the grism mechanism.
// @return Return an error code: ERROR_CODE_IN or ERROR_CODE_OUT if the grism is in a known position,
//         ERROR_CODE_UNKNOWN if the grism is not in a known position, or a positive error code if
//         an error occured.
//         Error ERROR_CODE_ILLEGAL_GRISM_POS: The Grism is both IN and OUT at the same time.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #PIN_GRISM_OUT_INPUT
// @see #PIN_GRISM_IN_INPUT
// @see #ERROR_CODE_ILLEGAL_GRISM_POS
int getGrismStatus()
{
  int isIn,isOut,currentState;

  printTime(); Serial.println("getGrismStatus:Started.");
  isIn = digitalRead(PIN_GRISM_IN_INPUT);
  isOut = digitalRead(PIN_GRISM_OUT_INPUT);
  if((isIn == HIGH) && (isOut == LOW))
    currentState = ERROR_CODE_IN;
  else if((isIn == LOW) && (isOut == HIGH))
    currentState = ERROR_CODE_OUT;
  else if((isIn == LOW) && (isOut == LOW))
    currentState = ERROR_CODE_UNKNOWN;
  else // isIn and isOut both true - an error
  {
    // Error ERROR_CODE_ILLEGAL_GRISM_POS: The Grism is both IN and OUT at the same time.
    printTime(); Serial.print("Error ");
    Serial.print(ERROR_CODE_ILLEGAL_GRISM_POS);
    Serial.println(" Illegal Grism Current Position: Both IN and OUT sensors are high.");
    currentState = ERROR_CODE_ILLEGAL_GRISM_POS;
  }
  return currentState;
}

// Get the current gyro position. The global variables gyroAngleX,gyroAngleY and gyroAngleZ
// should be updated with the current gyro position on success.
// @return Return an error code: ERROR_CODE_SUCCESS if the position is updated successfully,
//         a positive integer error code if the operation failed.
// @see #getGyroPosition
// @see #gyroAngleX
// @see #gyroAngleY
// @see #gyroAngleZ
// @see #ERROR_CODE_SUCCESS
int getGyroPosition()
{
  int errorCode;
  
  printTime(); Serial.println("getGyroPosition:Started.");
  errorCode = ERROR_CODE_SUCCESS;
  // gyroAngles actually retrieved via an interupt : see checkMPU6050
  return errorCode;
}

// Get the current humidity measured at the specified sensor.
// Sensor 0 is currently humidity sensor 0 (dht0).
// @param sensorNumber The sensor to use, an integer between 0 and 1 less than the number of humidity sensors.
// @return A double, respresenting the relative humidity measured at the specified sensor (0..100%). Note there
//         is no way to return an error if the measurement failed at the moment.
// @see #dht0
double getHumidity(int sensorNumber)
{
  float fvalue;
  double dvalue;
  
  printTime(); Serial.print("getHumidity:Started for sensor number ");
  Serial.print(sensorNumber);
  Serial.println(".");
  if(sensorNumber == 0)
  {
    fvalue = dht0.readHumidity();
    dvalue = (double)fvalue;
  }
  else
    dvalue = 0.0;
  printTime(); Serial.print("getHumidity:Returning humidity ");
  Serial.print(fvalue);
  Serial.print("(");
  Serial.print(dvalue);
  Serial.println(")%.");
  return dvalue;
}

// Get the current temperature measured at the specified sensor.
// Sensor 0 is currently the temperature from humidity sensor 0 (dht0).
// Sensor 1 is currently the temperature from Dallas temperature sensor 1 
// Sensor 2 is currently the temperature from Dallas temperature sensor 2 
// @param sensorNumber The sensor to use, an integer between 0 and 1 less than the number of temperature sensors.
// @return A double, respresenting the temperature measured at the specified sensor. Note there
//         is no way to return an error if the measurement failed at the moment.
// @see #dht0
// @see #dallasTemperature
// @see #temperatureSensor1
double getTemperature(int sensorNumber)
{
  float fvalue;
  double dvalue;
  
  printTime(); Serial.print("getTemperature:Started for sensor number ");
  Serial.print(sensorNumber);
  Serial.println(".");
  if(sensorNumber == 0)
  {
    fvalue = dht0.readTemperature();
    dvalue = (float)fvalue;
  }
  else if(sensorNumber == 1)
  {
    fvalue = dallasTemperature.getTempC(temperatureSensor1);
    dvalue = (float)fvalue;
  }
  else if(sensorNumber == 2)
  {
    fvalue = dallasTemperature.getTempC(temperatureSensor2);
    dvalue = (float)fvalue;
  }
  else
    dvalue = 0.0;    
  printTime(); Serial.print("getTemperature:Returning temperature ");
  Serial.print(fvalue);
  Serial.print("(");
  Serial.print(dvalue);
  Serial.println(") C.");
  return dvalue;
}

// Try and move the mirror into the beam.
// @return Return an error code: ERROR_CODE_OUT or ERROR_CODE_IN or ERROR_CODE_UNKNOWN (the current mirror position) 
//         if we start moving the mirror successfully, or a non-zero number for failure. 
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #PIN_MIRROR_OUTPUT
// @see #getMirrorStatus
int mirrorIn()
{
  printTime(); Serial.println("mirrorIn:Started.");
  digitalWrite(PIN_MIRROR_OUTPUT,HIGH);
  return getMirrorStatus();
}

// Try and move the mirror out of the beam.
// @return Return an error code: ERROR_CODE_OUT or ERROR_CODE_IN or ERROR_CODE_UNKNOWN (the current mirror position) 
//         if we start moving the mirror successfully, or a non-zero number for failure. 
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #PIN_MIRROR_OUTPUT
// @see #getMirrorStatus
int mirrorOut()
{
  printTime(); Serial.println("mirrorOut:Started.");
  digitalWrite(PIN_MIRROR_OUTPUT,LOW);
  return getMirrorStatus();
}

// Get the current status of the mirror mechanism.
// @return Return an error code: ERROR_CODE_IN or ERROR_CODE_OUT if the mirror is in a known position,
//         ERROR_CODE_UNKNOWN if the mirror is not in a known position, or a positive error code if
//         an error occured.
//         Error ERROR_CODE_ILLEGAL_MIRROR_POS: Illegal Mirror Current Position: Both IN and OUT sensors are high.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_ILLEGAL_MIRROR_POS
// @see #PIN_MIRROR_IN_INPUT
// @see #PIN_MIRROR_OUT_INPUT
int getMirrorStatus()
{
  int isIn,isOut,currentState;

  printTime(); Serial.println("getMirrorStatus:Started.");
  isIn = digitalRead(PIN_MIRROR_IN_INPUT);
  isOut = digitalRead(PIN_MIRROR_OUT_INPUT);
  if((isIn == HIGH) && (isOut == LOW))
    currentState = ERROR_CODE_IN;
  else if((isIn == LOW) && (isOut == HIGH))
    currentState = ERROR_CODE_OUT;
  else if((isIn == LOW) && (isOut == LOW))
    currentState = ERROR_CODE_UNKNOWN;
  else // isIn and isOut both true - an error
  {
    // Error ERROR_CODE_ILLEGAL_MIRROR_POS: The Mirror is both IN and OUT at the same time.
    printTime(); Serial.print("Error ");
    Serial.print(ERROR_CODE_ILLEGAL_MIRROR_POS);
    Serial.println(" Illegal Mirror Current Position: Both IN and OUT sensors are high.");
    currentState = ERROR_CODE_ILLEGAL_MIRROR_POS;
  }
  return currentState;
}

// Engineering command to turn a relay on or off.
// If we are changing relay 3 (PIN_GRISM_OUTPUT) we use getRotation
// to ensure the grism rotation cylinder is out of the way (position 0) before we change state.
// If we are moving relay 4 in (PIN_GRISM_ROTATE_OUTPUT) we use getGrismStatus
// to ensure the grism is in before we change state.
// @param relayNumber The relay to turn on from 1 to 8.
// @param isOn Whether to turn the relay on or off :- 0 is off, 1 is on.
// @return We return ERROR_CODE_ON or ERROR_CODE_OFF on success, and ERROR_CODE_GRISM_NOT_IN, ERROR_CODE_ROT_POS_NOT_ZERO
//         or ERROR_CODE_NUMBER_OUT_OF_RANGE on failure.
// @see #getGrismStatus
// @see #getRotation
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
// @see #ERROR_CODE_OUT
// @see #PIN_MIRROR_OUTPUT 
// @see #PIN_SLIT_OUTPUT
// @see #PIN_GRISM_OUTPUT
// @see #PIN_GRISM_ROTATE_OUTPUT
// @see #PIN_W_LAMP_OUTPUT
// @see #PIN_RELAY6_OUTPUT
// @see #PIN_RELAY7_OUTPUT
// @see #PIN_RELAY8_OUTPUT
// @see #ERROR_CODE_NUMBER_OUT_OF_RANGE
// @see #ERROR_CODE_GRISM_NOT_IN
// @see #ERROR_CODE_ROT_POS_NOT_ZERO
int relayOnOff(int relayNumber,int isOn)
{
    int pinNumber,errorCode,grismStatus,grismRotationStatus;
    
    switch(relayNumber)
    {
      case 1:
        pinNumber = PIN_MIRROR_OUTPUT;
        break;
      case 2:
        pinNumber = PIN_SLIT_OUTPUT;
        break;
      case 3:
        pinNumber = PIN_GRISM_OUTPUT;
        break;
      case 4:
        pinNumber = PIN_GRISM_ROTATE_OUTPUT;
        break;
      case 5:
        pinNumber = PIN_W_LAMP_OUTPUT;
        break;
      case 6:
        pinNumber = PIN_RELAY6_OUTPUT;
        break;
      case 7:
        pinNumber = PIN_RELAY7_OUTPUT;
        break;
      case 8:
        pinNumber = PIN_RELAY8_OUTPUT;
        break;
      default:
        printTime(); Serial.print("Error ");
        Serial.print(ERROR_CODE_NUMBER_OUT_OF_RANGE);
        Serial.print(":Relay Number ");
        Serial.print(relayNumber);
        Serial.println(" out of range (1..8)");
        return ERROR_CODE_NUMBER_OUT_OF_RANGE;
    }
    // Check the grism rotation is stowed before moving the grism in or out of the beam 
    if(pinNumber == PIN_GRISM_OUTPUT)
    {
      grismRotationStatus = getRotation();
      if(grismRotationStatus != 0)
      {
        printTime(); Serial.print("Error ");
        Serial.print(ERROR_CODE_ROT_POS_NOT_ZERO);
        Serial.print(" Grism Rotation position was not 0: cannot move grism mechanism. Current grism status: ");
        Serial.print(grismStatus);
        Serial.println(".");
        return ERROR_CODE_ROT_POS_NOT_ZERO;
      }
    }
    // If we are rotating the grism check the grism is in the beam
    if(pinNumber == PIN_GRISM_ROTATE_OUTPUT)
    {
      // If the grism is not in, we cannot start moving the grism rotation stage in due to it's design.
      grismStatus = getGrismStatus();
      if((isOn == 1) && (grismStatus != ERROR_CODE_IN))
      {
        printTime(); Serial.print("Error ");
        Serial.print(ERROR_CODE_GRISM_NOT_IN);
        Serial.print(" Grism was not IN: cannot move grism rotation mechanism to position 1. Current grism status: ");
        Serial.print(grismStatus);
        Serial.println(".");
        return ERROR_CODE_GRISM_NOT_IN;
      }
    }
    if(isOn == 0)
    {
      digitalWrite(pinNumber,LOW);
      errorCode = ERROR_CODE_OFF;
    }
    else if(isOn == 1)
    {
      digitalWrite(pinNumber,HIGH);
      errorCode = ERROR_CODE_ON;
    }
    else
    {
        printTime(); Serial.print("Error ");
        Serial.print(ERROR_CODE_NUMBER_OUT_OF_RANGE);
        Serial.print(":IsOn Number ");
        Serial.print(isOn);
        Serial.println(" out of range (0..1)");
        return ERROR_CODE_NUMBER_OUT_OF_RANGE;
    }
    return errorCode;
}

// Try and rotate the grism to the specified angle.
// @param targetPosition The position to rotate the grism to, one of 0|1.
// @return Return an error code: 0|1 on success, 
//          ERROR_CODE_UNKNOWN if the grism rotation mechanism ends up in an indeterminate state,
//         or a number greater than 1 for failure.
//         Error ERROR_CODE_GRISM_NOT_IN: Grism was not IN: cannot move grism rotation mechanism in.
//         Error ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS: Illegal grism rotation target position.
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_GRISM_NOT_IN
// @see #ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS
// @see #PIN_GRISM_ROTATE_OUTPUT
// @see #getGrismStatus
// @see #getRotation
int rotation(int targetPosition)
{
  int grismStatus;
  
  printTime(); Serial.print("rotation:Started rotating grism to position: ");
  Serial.print(targetPosition);
  Serial.println(".");
  // If the grism is not in, we cannot start moving the grism rotation stage to position 1 due to it's design.
  if(targetPosition == 1)
  {
    grismStatus = getGrismStatus();
    if(grismStatus != ERROR_CODE_IN)
    {
      printTime(); Serial.print("Error ");
      Serial.print(ERROR_CODE_GRISM_NOT_IN);
      Serial.print(" Grism was not IN: cannot move grism rotation mechanism to position 1. Current grism status: ");
      Serial.print(grismStatus);
      Serial.println(".");
      return ERROR_CODE_GRISM_NOT_IN;
    }
  }
  if(targetPosition == 0)
  {
    digitalWrite(PIN_GRISM_ROTATE_OUTPUT,LOW);
  }
  else if(targetPosition == 1)
  {
    digitalWrite(PIN_GRISM_ROTATE_OUTPUT,HIGH);
  }
  else
  {
    printTime(); Serial.print("Error ");
    Serial.print(ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS);
    Serial.print(" Illegal grism rotation target position: ");
    Serial.print(targetPosition);
    Serial.println(".");
    return ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS;
  }
  return getRotation();
}

// Return the current rotation position of the grism.
// @return Return a rotation position or an error code: 0|1 on success, 
//          ERROR_CODE_UNKNOWN if the grism rotation mechanism ends up in an indeterminate state,
//         or a number greater than 1 for failure.
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_ILLEGAL_GRISM_ROT_POS
// @see #PIN_GRISM_ROT_POS_0_INPUT
// @see #PIN_GRISM_ROT_POS_1_INPUT
int getRotation()
{
  int isInPos0,isInPos1,currentState;

  printTime(); Serial.println("getRotation:Started.");
  isInPos0 = digitalRead(PIN_GRISM_ROT_POS_0_INPUT);
  isInPos1 = digitalRead(PIN_GRISM_ROT_POS_1_INPUT);
  if((isInPos0 == HIGH) && (isInPos1 == LOW))
    currentState = 0;
  else if((isInPos0 == LOW) && (isInPos1 == HIGH))
    currentState = 1;
  else if((isInPos0 == LOW) && (isInPos1 == LOW))
    currentState = ERROR_CODE_UNKNOWN;
  else // isInPos0 and isInPos0 both true - an error
  {
    // Error ERROR_CODE_ILLEGAL_GRISM_ROT_POS: The Grism is in Rotation position 0 and 1 at the same time.
    printTime(); Serial.print("Error ");
    Serial.print(ERROR_CODE_ILLEGAL_GRISM_ROT_POS);
    Serial.println(" The Grism is in Rotation position 0 and 1 at the same time.");
    currentState = ERROR_CODE_ILLEGAL_GRISM_ROT_POS;
  }
  return currentState;
}

// Try and move the slit into the beam.
// @return Return an error code: ERROR_CODE_OUT or ERROR_CODE_IN or ERROR_CODE_UNKNOWN (the current slit position) 
//         if we start moving the slit successfully, or a non-zero number for failure. 
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #PIN_SLIT_OUTPUT
// @see #getSlitStatus
int slitIn()
{
  printTime(); Serial.println("slitIn:Started.");
  digitalWrite(PIN_SLIT_OUTPUT,HIGH);
  return getSlitStatus();
}

// Try and move the slit out of the beam.
// @return Return an error code: ERROR_CODE_OUT or ERROR_CODE_IN or ERROR_CODE_UNKNOWN (the current slit position) 
//         if we start moving the slit successfully, or a non-zero number for failure. 
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #PIN_SLIT_OUTPUT
// @see #getSlitStatus
int slitOut()
{
  printTime(); Serial.println("slitOut:Started.");
  digitalWrite(PIN_SLIT_OUTPUT,LOW);
  return getSlitStatus();
}

// Get the current status of the slit mechanism.
// @return Return an error code: ERROR_CODE_IN or ERROR_CODE_OUT if the slit is in a known position,
//         ERROR_CODE_UNKNOWN if the slit is not in a known position, or a positive error code if
//         an error occured.
//         Error ERROR_CODE_ILLEGAL_SLIT_POS: Illegal Slit Current Position: Both IN and OUT sensors are high.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_ILLEGAL_SLIT_POS
// @see #PIN_SLIT_IN_INPUT
// @see #PIN_SLIT_OUT_INPUT
int getSlitStatus()
{
  int isIn,isOut,currentState;

  printTime(); Serial.println("getSlitStatus:Started.");
  isIn = digitalRead(PIN_SLIT_IN_INPUT);
  isOut = digitalRead(PIN_SLIT_OUT_INPUT);
  if((isIn == HIGH) && (isOut == LOW))
    currentState = ERROR_CODE_IN;
  else if((isIn == LOW) && (isOut == HIGH))
    currentState = ERROR_CODE_OUT;
  else if((isIn == LOW) && (isOut == LOW))
    currentState = ERROR_CODE_UNKNOWN;
  else // isIn and isOut both true - an error
  {
    // Error ERROR_CODE_ILLEGAL_SLIT_POS: The Slit is both IN and OUT at the same time.
    printTime(); Serial.print("Error ");
    Serial.print(ERROR_CODE_ILLEGAL_SLIT_POS);
    Serial.println(" Illegal Slit Current Position: Both IN and OUT sensors are high.");
    currentState = ERROR_CODE_ILLEGAL_SLIT_POS;
  }
  return currentState;
}

// Try and turn the wlamp on.
// @return Return an error code: ERROR_CODE_ON on success, or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
// @see #PIN_W_LAMP_OUTPUT
int wLampOn()
{
  printTime(); Serial.println("wLampOn:Started.");
  digitalWrite(PIN_W_LAMP_OUTPUT,HIGH);
  return ERROR_CODE_ON;
}

// Try and turn the wlamp on.
// @return Return an error code: ERROR_CODE_OFF on success, or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
// @see #PIN_W_LAMP_OUTPUT
int wLampOff()
{
  printTime(); Serial.println("wLampOff:Started.");
  digitalWrite(PIN_W_LAMP_OUTPUT,LOW);
  return ERROR_CODE_OFF;
}

// Return an integer describing whether the wlamp is turned on, or off.
// @return Return an error code: ERROR_CODE_ON | ERROR_CODE_OFF on success, 
//         or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
// @see #PIN_W_LAMP_OUTPUT
int getWLampStatus()
{
  int currentState;

  printTime(); Serial.println("getWLampStatus:Started.");
  currentState = digitalRead(PIN_W_LAMP_OUTPUT);
  //currentState = bitRead(PORTD,PIN_W_LAMP_OUTPUT);
  if(currentState == HIGH)
    return ERROR_CODE_ON;
  else
    return ERROR_CODE_OFF;
}

// Print the current time in milliseconds followed by a space, to the serial line.
// Used to start logging lines .
void printTime()
{
  Serial.print(millis());
  Serial.print(" ");
}
