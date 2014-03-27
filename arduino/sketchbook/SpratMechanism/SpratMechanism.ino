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
#define ERROR_CODE_SUCCESS             (0)
// errorCodes indicating mechanism positions (rather than an actual error)
// For on|off mechanisms
#define ERROR_CODE_ON                  (-1)
#define ERROR_CODE_OFF                 (-2)
// For in|out mechanisms
#define ERROR_CODE_IN                  (-1)
#define ERROR_CODE_OUT                 (-2)
#define ERROR_CODE_UNKNOWN             (-3)

// Ethernet Sheild mac address
// Map to IP address
// Note for the EtherMega you define your own mac address
// This must be unique to the network you are on. Som URLs:
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
void messageReady()
{
  double dvalue;
  int errorCode,position,sensorNumber,rotationPosition;
  
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
        default:
          client.print("error ");
          client.println(errorCode);
          break;
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
int arcLampOn()
{
  Serial.println("arcLampOn:Started.");
  // TODO
  return ERROR_CODE_ON;
}

// Try and turn the arclamp on.
// @return Return an error code: ERROR_CODE_OFF on success, or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
int arcLampOff()
{
  Serial.println("arcLampOff:Started.");
  // TODO
  return ERROR_CODE_OFF;
}

// Return an integer describing whether the arclamp is turned on, or off.
// @return Return an error code: ERROR_CODE_ON | ERROR_CODE_OFF on success, 
//         or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
int getArcLampStatus()
{
  int currentState;

  Serial.println("getArcLampStatus:Started.");
  // TODO
  return currentState;
}

// Try and move the grism into the beam.
// @return Return an error code: ERROR_CODE_IN on success, 
//          ERROR_CODE_UNKNOWN if the grism ends up in an indeterminate state,
//         or a non-zero number for failure.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_UNKNOWN
int grismIn()
{
  Serial.println("grismIn:Started.");
  // TODO
  return ERROR_CODE_IN;
}

// Try and move the grism out of the beam.
// @return Return an error code: ERROR_CODE_OUT on success, 
//          ERROR_CODE_UNKNOWN if the grism ends up in an indeterminate state,
//         or a non-zero number for failure.
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
int grismOut()
{
  Serial.println("grismOut:Started.");
  // TODO
  return ERROR_CODE_OUT;
}

// Get the current status of the grism mechanism.
// @return Return an error code: ERROR_CODE_IN or ERROR_CODE_OUT if the grism is in a known position,
//         ERROR_CODE_UNKNOWN if the grism is not in a known position, or a positive error code if
//         an error occured.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
int getGrismStatus()
{
  int currentState;

  Serial.println("getGrismStatus:Started.");
  // TODO
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
// @return Return an error code: ERROR_CODE_IN on success, 
//          ERROR_CODE_UNKNOWN if the mirror ends up in an indeterminate state,
//         or a non-zero number for failure.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_UNKNOWN
int mirrorIn()
{
  Serial.println("mirrorIn:Started.");
  // TODO
  return ERROR_CODE_IN;
}

// Try and move the mirror out of the beam.
// @return Return an error code: ERROR_CODE_OUT on success, 
//          ERROR_CODE_UNKNOWN if the mirror ends up in an indeterminate state,
//         or a non-zero number for failure.
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
int mirrorOut()
{
  Serial.println("mirrorOut:Started.");
  // TODO
  return ERROR_CODE_OUT;
}

// Get the current status of the mirror mechanism.
// @return Return an error code: ERROR_CODE_IN or ERROR_CODE_OUT if the mirror is in a known position,
//         ERROR_CODE_UNKNOWN if the mirror is not in a known position, or a positive error code if
//         an error occured.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
int getMirrorStatus()
{
  int currentState;

  Serial.println("getMirrorStatus:Started.");
  // TODO
  return currentState;
}

// Try and rotate the grism to the specified angle.
// @param targetPosition The position to rotate the grism to, one of 0|1.
// @return Return an error code: 0|1 on success, 
//          ERROR_CODE_UNKNOWN if the grism rotation mechanism ends up in an indeterminate state,
//         or a number greater than 1 for failure.
// @see #ERROR_CODE_UNKNOWN
int rotation(int targetPosition)
{
  int actualPosition;

  // TODO
  actualPosition = targetPosition;
  return actualPosition;
}

// Return the current rotation position of the grism.
// @return Return a rotation position or an error code: 0|1 on success, 
//          ERROR_CODE_UNKNOWN if the grism rotation mechanism ends up in an indeterminate state,
//         or a number greater than 1 for failure.
// @see #ERROR_CODE_UNKNOWN
int getRotation()
{
  int actualPosition;

  // TODO
  actualPosition = ERROR_CODE_UNKNOWN;
  return actualPosition;  
}

// Try and move the slit into the beam.
// @return Return an error code: ERROR_CODE_IN on success, 
//          ERROR_CODE_UNKNOWN if the slit ends up in an indeterminate state,
//         or a non-zero number for failure.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_UNKNOWN
int slitIn()
{
  Serial.println("slitIn:Started.");
  // TODO
  return ERROR_CODE_IN;
}

// Try and move the slit out of the beam.
// @return Return an error code: ERROR_CODE_OUT on success, 
//          ERROR_CODE_UNKNOWN if the slit ends up in an indeterminate state,
//         or a non-zero number for failure.
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
int slitOut()
{
  Serial.println("slitOut:Started.");
  // TODO
  return ERROR_CODE_OUT;
}

// Get the current status of the slit mechanism.
// @return Return an error code: ERROR_CODE_IN or ERROR_CODE_OUT if the slit is in a known position,
//         ERROR_CODE_UNKNOWN if the slit is not in a known position, or a positive error code if
//         an error occured.
// @see #ERROR_CODE_IN
// @see #ERROR_CODE_OUT
// @see #ERROR_CODE_UNKNOWN
int getSlitStatus()
{
  int currentState;

  Serial.println("getSlitStatus:Started.");
  // TODO
  return currentState;
}

// Try and turn the wlamp on.
// @return Return an error code: ERROR_CODE_ON on success, or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
int wLampOn()
{
  Serial.println("wLampOn:Started.");
  // TODO
  return ERROR_CODE_ON;
}

// Try and turn the wlamp on.
// @return Return an error code: ERROR_CODE_OFF on success, or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
int wLampOff()
{
  Serial.println("wLampOff:Started.");
  // TODO
  return ERROR_CODE_OFF;
}

// Return an integer describing whether the wlamp is turned on, or off.
// @return Return an error code: ERROR_CODE_ON | ERROR_CODE_OFF on success, 
//         or a non-zero number for failure.
// @see #ERROR_CODE_ON
// @see #ERROR_CODE_OFF
int getWLampStatus()
{
  int currentState;

  Serial.println("getWLampStatus:Started.");
  // TODO
  return currentState;
}

