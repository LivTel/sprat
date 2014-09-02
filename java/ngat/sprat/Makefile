# Makefile
# $HeadURL$
include ../../../Makefile.common
include ../../../../Makefile.common

MAKE 		= make
PACKAGEDIR	= ngat/sprat/
BINDIR 		= $(SPRAT_BIN_HOME)/java/$(PACKAGEDIR)
CONFIG_BINDIR 	= $(SPRAT_BIN_HOME)/java
SCRIPT_BINDIR 	= $(SPRAT_BIN_HOME)/java
SRCS 		= $(MAIN_SRCS) $(IMPL_SRCS)
MAIN_SRCS 	= SpratConstants.java SpratStatus.java Sprat.java \
		  SpratTCPClientConnectionThread.java SpratTCPServerConnectionThread.java SpratTCPServer.java


IMPL_SRCS = $(BASE_IMPL_SRCS) $(CALIBRATE_IMPL_SRCS) $(EXPOSE_IMPL_SRCS) $(INTERRUPT_IMPL_SRCS) $(SETUP_IMPL_SRCS)
BASE_IMPL_SRCS		= JMSCommandImplementation.java CommandImplementation.java UnknownCommandImplementation.java \
			HardwareImplementation.java FITSImplementation.java ACQUIREImplementation.java
CALIBRATE_IMPL_SRCS 	= CALIBRATEImplementation.java ARCImplementation.java \
			BIASImplementation.java DARKImplementation.java \
			LAMPFLATImplementation.java MULTBIASImplementation.java MULTDARKImplementation.java \
			SKYFLATImplementation.java 
EXPOSE_IMPL_SRCS	= EXPOSEImplementation.java MULTRUNImplementation.java 
INTERRUPT_IMPL_SRCS 	= GET_STATUSImplementation.java
SETUP_IMPL_SRCS		= CONFIGImplementation.java
OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)

CONFIG_SRCS	= sprat1.sprat.properties sprat1.sprat.net.properties sprat1.fits.properties \
		  ltobs9.sprat.properties ltobs9.sprat.net.properties ltobs9.fits.properties \
		  frodospec1.sprat.properties frodospec1.sprat.net.properties frodospec1.fits.properties \
		  ioi1.sprat.properties ioi1.sprat.net.properties ioi1.fits.properties
CONFIG_OBJS	= $(CONFIG_SRCS:%.properties=$(CONFIG_BINDIR)/%.properties)
SCRIPT_SRCS	= 
SCRIPT_OBJS	= 
DIRS 		= ccd mechanism test

top: dirs $(OBJS) config scripts dirs

#
# Java classes
#
$(BINDIR)/%.class:%.java
	$(JAVAC) -sourcepath .:$(SPRAT_SRC_HOME)/java \
		-classpath $(SPRAT_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) -d $(SPRAT_BIN_HOME)/java $<
#
# configuration files
#
config: $(CONFIG_OBJS) $(INI_OBJS)

$(CONFIG_BINDIR)/%.properties: %.properties
	$(CP) $< $@

#
# run scripts
#
scripts: $(SCRIPT_OBJS)

#
# sub dirs
#
dirs:
	@for i in $(DIRS); \
	do \
		(echo making in $$i...; cd $$i; $(MAKE) ); \
	done;

#
# other targets
#
checkin:
	-$(CI) $(CI_OPTIONS) $(SRCS)
	-@for i in $(DIRS); \
	do \
		(echo checkin in $$i...; cd $$i; $(MAKE) checkin; $(CI) $(CI_OPTIONS) Makefile); \
	done;

checkout:
	$(CO) $(CO_OPTIONS) $(SRCS)
	@for i in $(DIRS); \
	do \
		(echo checkout in $$i...; cd $$i; $(CO) $(CO_OPTIONS) Makefile; $(MAKE) checkout); \
	done;

depend:
	echo no depend in ngat directory.

clean: tidy
	-$(RM) $(RM_OPTIONS) $(OBJS)
	-$(RM) $(RM_OPTIONS) $(JARLIBDIR)/$(JAR_FILE)
	@for i in $(DIRS); \
	do \
		(echo clean in $$i...; cd $$i; $(MAKE) clean); \
	done;

tidy:
	-$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)
	@for i in $(DIRS); \
	do \
		(echo tidy in $$i...; cd $$i; $(MAKE) tidy); \
	done;

backup: tidy checkin
	@for i in $(DIRS); \
	do \
		(echo backup in $$i...; cd $$i; $(MAKE) backup); \
	done;
