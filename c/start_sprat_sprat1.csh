#!/bin/csh
limit coredumpsize unlimited
unsetenv eSTAR_CONFIG_DIR
# Set the ld library path
if ( ${?LD_LIBRARY_PATH} == 0) then
    setenv LD_LIBRARY_PATH "/icc/bin/lib/i386-linux:/usr/lib"
endif
foreach dir ( "/icc/bin/lib/i386-linux" "/icc/bin/estar/lib/i386-linux")
    setenv LD_LIBRARY_PATH "${dir}:${LD_LIBRARY_PATH}"
end
cd /icc/bin/sprat/c/i386-linux
./sprat -config_filename /icc/bin/sprat/c/i386-linux/sprat.c.properties -sprat_log_level 5 -ccd_log_level 5 -command_server_log_level 5
#-help 
