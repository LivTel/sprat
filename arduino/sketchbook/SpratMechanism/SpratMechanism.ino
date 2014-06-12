// SpratMechanism Arduino Program
// $HeadURL$
// Version: $Revision$

#include <SPI.h>
// Ethernet parameters
#include <Ethernet.h>
#include <Messenger.h>

// length of string used for command parsing.
#define STRING_LENGTH                  (16)
// timeout in milloiseconds for mechanism movement
#define DEFAULT_MOVE_TIMEOUT           (10000)
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
#define ERROR_CODE_GRISM_ROT_POS_NOT_KNOWN      (1)
#define ERROR_CODE_ILLEGAL_GRISM_POS            (2)
#define ERROR_CODE_ILLEGAL_MIRROR_POS           (3)
#define ERROR_CODE_ILLEGAL_SLIT_POS             (4)
#define ERROR_CODE_ILLEGAL_GRISM_ROT_POS        (5)
#define ERROR_CODE_GRISM_NOT_OUT                (6)
#define ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS (7)
#define ERROR_CODE_NUMBER_OUT_OF_RANGE          (8)

// Pin declarations
#define PIN_ARC_LAMP_OUTPUT            (2)
#define PIN_TEMPERATURE_0              (5)
#define PIN_HUMIDITY_0                 (6)
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

// movement timeouts, all in milliseconds
int moveTimeout = DEFAULT_MOVE_TIMEOUT;

// gyro variables
// angle in degrees
double gyroAngleX = 0.0;
double gyroAngleY = 0.0;
double gyroAngleZ = 0.0;

// setup
void setup()
{
  // configure pins
  // TODO
  //PIN_TEMPERATURE_0
  //PIN_HUMIDITY_0
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
  // configure serial
  Serial.begin(9600);
  // configure ethernet and callback routine
  Ethernet.begin(mac,ip,gateway,subnet);
  server.begin();
  // messenger callback function
  message.attach(messageReady);
}

// main loop
void loop()
{
  // check if new connecion available
  if(server.available() && !connectFlag)
  {
      connectFlag = 1;
      client = server.available();
      Serial.println("loop:New client connected.");
  }
  // check for input from client
  if(client.connected() && client.available())
  {
      Serial.print("loop:Reading characters from Ethernet:");
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
  // wait a bit to stop the arduino locking up
  delay(10);
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
// @see #ERROR_CODE_GRISM_ROT_POS_NOT_KNOWN
// @see #ERROR_CODE_ILLEGAL_GRISM_POS
// @see #ERROR_CODE_ILLEGAL_MIRROR_POS
// @see #ERROR_CODE_ILLEGAL_SLIT_POS
// @see #ERROR_CODE_ILLEGAL_GRISM_ROT_POS
// @see #ERROR_CODE_GRISM_NOT_OUT
// @see #ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS
// @see #ERROR_CODE_NUMBER_OUT_OF_RANGE
void messageReady()
{
  double dvalue;
  int errorCode,position,sensorNumber,rotationPosition,relayNumber,isOn;
  
  Serial.println("messageReady:Processing message.");
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
        case ERROR_CODE_GRISM_ROT_POS_NOT_KNOWN:
          client.print("error ");
          client.print(ERROR_CODE_GRISM_ROT_POS_NOT_KNOWN);
          client.println(" Grism Rotation not in a known position: Failing to start move.");
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
            case ERROR_CODE_GRISM_NOT_OUT:
              client.print("error ");
              client.print(ERROR_CODE_GRISM_NOT_OUT);
              client.println(" Grism was not OUT: cannot move grism rotation mechanism.");
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
        case ERROR_CODE_GRISM_NOT_OUT:
          client.print("error ");
          client.print(ERROR_CODE_GRISM_NOT_OUT);
          client.println(" Grism was not OUT: cannot move grism rotation mechanism.");
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
  Serial.println("arcLampOn:Started.");
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
  Serial.println("arcLampOff:Started.");
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

  Serial.println("getArcLampStatus:Started.");
  currentState = digitalRead(PIN_ARC_LAMP_OUTPUT);
  //currentState = bitRead(PORTD,PIN_ARC_LAMP_OUTPUT);
  if(currentState == HIGH)
    return ERROR_CODE_ON;
  else
    return ERROR_CODE_OFF;
}

// Try and move the grism into the beam.
// We can only move the grism in in the grism rotation is in a know position (due to mechanism interlocking constraints).
// If it is moving, we should not start moving the grism in.
// @return Return an error code: ERROR_CODE_OUT or ERROR_CODE_IN or ERROR_CODE_UNKNOWN (the current grism position) 
//         if we start moving the grism successfully, or a non-zero number for failure. 
//         Error 1 (ERROR_CODE_GRISM_ROT_POS_NOT_KNOWN) is:Grism Rotation not in a known position: Failing to start move.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_GRISM_ROT_POS_NOT_KNOWN
// @see #getGrismStatus
// @see #getRotation
int grismIn()
{
  int grismRotationStatus;
  
  Serial.println("grismIn:Started.");
  // We can only move the grism in if the grism rotation is in a known position.
  // If it is moving, we should not start moving the grism in.
  grismRotationStatus = getRotation();
  if(grismRotationStatus == ERROR_CODE_UNKNOWN) // we are moving
  {
    Serial.println("grismIn:Error:Grism Rotation not in a known position: Failing to start move.");
    return ERROR_CODE_GRISM_ROT_POS_NOT_KNOWN;
  }
  digitalWrite(PIN_GRISM_OUTPUT,HIGH);
  return getGrismStatus();
}

// Try and move the grism out of the beam.
// @return Return an error code: ERROR_CODE_IN or ERROR_CODE_OUT or ERROR_CODE_UNKNOWN (the current grism position) 
//         if we start moving the grism successfully, or a non-zero number for failure.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
// @see #getGrismStatus
int grismOut()
{
  Serial.println("grismOut:Started.");
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

  Serial.println("getGrismStatus:Started.");
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
    Serial.print("Error ");
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
  
  Serial.println("getGyroPosition:Started.");
  errorCode = ERROR_CODE_SUCCESS;
  // TODO
  gyroAngleX = 0.0;
  gyroAngleY = 0.0;
  gyroAngleZ = 0.0;
  return errorCode;
}

// Get the current humidity measured at the specified sensor.
// @param sensorNumber The sensor to use, an integer between 0 and 1 less than the number of humidity sensors.
// @return A double, respresenting the relative humidity measured at the specified sensor (0..100%). Note there
//         is no way to return an error if the measurement failed at the moment.
double getHumidity(int sensorNumber)
{
  double dvalue;
  
  Serial.print("getHumidity:Started for sensor number ");
  Serial.print(sensorNumber);
  Serial.println(".");
  // TODO
  dvalue = 0.0;
  return dvalue;
}

// Get the current temperature measured at the specified sensor.
// @param sensorNumber The sensor to use, an integer between 0 and 1 less than the number of temperature sensors.
// @return A double, respresenting the temperature measured at the specified sensor. Note there
//         is no way to return an error if the measurement failed at the moment.
double getTemperature(int sensorNumber)
{
  double dvalue;
  
  Serial.print("getTemperature:Started for sensor number ");
  Serial.print(sensorNumber);
  Serial.println(".");
  // TODO
  dvalue = 0.0;
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
  Serial.println("mirrorIn:Started.");
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
  Serial.println("mirrorOut:Started.");
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

  Serial.println("getMirrorStatus:Started.");
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
    Serial.print("Error ");
    Serial.print(ERROR_CODE_ILLEGAL_MIRROR_POS);
    Serial.println(" Illegal Mirror Current Position: Both IN and OUT sensors are high.");
    currentState = ERROR_CODE_ILLEGAL_MIRROR_POS;
  }
  return currentState;
}

// Engineering command to turn a relay on or off.
// If we are changing relay 4 (PIN_GRISM_ROTATE_OUTPUT) we use getGrismStatus
// to ensure the grism is out before we change state.
// @param relayNumber The relay to turn on from 1 to 8.
// @param isOn Whether to turn the relay on or off :- 0 is off, 1 is on.
// @return We return ERROR_CODE_ON or ERROR_CODE_OFF on success, and ERROR_CODE_GRISM_NOT_OUT 
//         or ERROR_CODE_NUMBER_OUT_OF_RANGE on failure.
// @see #getGrismStatus
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
// @see #ERROR_CODE_GRISM_NOT_OUT
int relayOnOff(int relayNumber,int isOn)
{
    int pinNumber,errorCode,grismStatus;
    
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
        Serial.print("Error ");
        Serial.print(ERROR_CODE_NUMBER_OUT_OF_RANGE);
        Serial.print(":Relay Number ");
        Serial.print(relayNumber);
        Serial.println(" out of range (1..8)");
        return ERROR_CODE_NUMBER_OUT_OF_RANGE;
    }
    // If we are rotating the grism check this is allowed
    if(pinNumber == PIN_GRISM_ROTATE_OUTPUT)
    {
      // If the grism is not out, we cannot start moving the grism rotation stage due to it's design.
      grismStatus = getGrismStatus();
      if(grismStatus != ERROR_CODE_OUT)
      {
        Serial.print("Error ");
        Serial.print(ERROR_CODE_GRISM_NOT_OUT);
        Serial.print(" Grism was not OUT: cannot move grism rotation mechanism. Current grism status: ");
        Serial.print(grismStatus);
        Serial.println(".");
        return ERROR_CODE_GRISM_NOT_OUT;
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
        Serial.print("Error ");
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
//         Error ERROR_CODE_GRISM_NOT_OUT: Grism was not OUT: cannot move grism rotation mechanism.
//         Error ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS: Illegal grism rotation target position.
// @see #ERROR_CODE_UNKNOWN
// @see #ERROR_CODE_GRISM_NOT_OUT
// @see #ERROR_CODE_ILLEGAL_GRISM_ROT_TARGET_POS
// @see #PIN_GRISM_ROTATE_OUTPUT
// @see #getGrismStatus
// @see #getRotation
int rotation(int targetPosition)
{
  int grismStatus;
  
  Serial.print("rotation:Started rotating grism to position: ");
  Serial.print(targetPosition);
  Serial.println(".");
  // If the grism is not out, we cannot start moving the grism rotation stage due to it's design.
  grismStatus = getGrismStatus();
  if(grismStatus != ERROR_CODE_OUT)
  {
    Serial.print("Error ");
    Serial.print(ERROR_CODE_GRISM_NOT_OUT);
    Serial.print(" Grism was not OUT: cannot move grism rotation mechanism. Current grism status: ");
    Serial.print(grismStatus);
    Serial.println(".");
    return ERROR_CODE_GRISM_NOT_OUT;
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
    Serial.print("Error ");
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

  Serial.println("getRotation:Started.");
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
    Serial.print("Error ");
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
  Serial.println("slitIn:Started.");
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
  Serial.println("slitOut:Started.");
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

  Serial.println("getSlitStatus:Started.");
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
    Serial.print("Error ");
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
  Serial.println("wLampOn:Started.");
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
  Serial.println("wLampOff:Started.");
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

  Serial.println("getWLampStatus:Started.");
  currentState = digitalRead(PIN_W_LAMP_OUTPUT);
  //currentState = bitRead(PORTD,PIN_W_LAMP_OUTPUT);
  if(currentState == HIGH)
    return ERROR_CODE_ON;
  else
    return ERROR_CODE_OFF;
}

