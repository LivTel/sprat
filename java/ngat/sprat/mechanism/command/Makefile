# Makefile
# $HeadURL$
include ../../../../../../Makefile.common
include	../../../../../Makefile.common

PACKAGEDIR	= ngat/sprat/mechanism/command
BINDIR 		= $(SPRAT_BIN_HOME)/java/$(PACKAGEDIR)
SRCS 		= Command.java DoubleReplyCommand.java HumidityCommand.java TemperatureCommand.java \
		InOutReplyCommand.java OnOffReplyCommand.java GrismCommand.java MirrorCommand.java SlitCommand.java \
		ArcLampCommand.java WLampCommand.java \
		RotationCommand.java GyroCommand.java

OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)

DIRS 		= 

top: $(OBJS)
	@for i in $(DIRS); \
	do \
		(echo making in $$i...; cd $$i; $(MAKE) ); \
	done;

$(BINDIR)/%.class:%.java
	$(JAVAC) $(JAVAC_FLAGS) -sourcepath .:$(SPRAT_SRC_HOME)/java \
		-classpath $(SPRAT_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) -d $(SPRAT_BIN_HOME)/java $<

clean: tidy
	-$(RM) $(RM_OPTIONS) $(OBJS)
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

backup: tidy
	@for i in $(DIRS); \
	do \
		(echo backup in $$i...; cd $$i; $(MAKE) backup); \
	done;
