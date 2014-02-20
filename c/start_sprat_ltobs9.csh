#!/bin/csh
limit coredumpsize unlimited
unsetenv eSTAR_CONFIG_DIR
# Set the ld library path
if ( ${?LD_LIBRARY_PATH} == 0) then
    setenv LD_LIBRARY_PATH "/home/dev/bin/lib/i386-linux:/usr/lib"
endif
foreach dir ( "/home/dev/bin/lib/i386-linux" "/home/dev/bin/estar/lib/i386-linux" "/home/dev/src/andor/andor-2.85.30000/lib/")
    setenv LD_LIBRARY_PATH "${dir}:${LD_LIBRARY_PATH}"
end
cd ~dev/bin/sprat/c/i386-linux
./sprat -config_filename /home/dev/bin/sprat/c/i386-linux/ltobs9.sprat.c.properties
#-help 
