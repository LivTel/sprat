# Makefile
# $HeadURL$
include ../../../../../../Makefile.common
include	../../../../../Makefile.common

PACKAGEDIR	= ngat/sprat/ccd/command
BINDIR 		= $(SPRAT_BIN_HOME)/java/$(PACKAGEDIR)
SRCS 		= Command.java IntegerReplyCommand.java MultrunFilenameReplyCommand.java \
		AbortCommand.java BiasCommand.java ConfigCommand.java DarkCommand.java \
		FitsHeaderAddCommand.java FitsHeaderClearCommand.java FitsHeaderDeleteCommand.java \
		MultBiasCommand.java MultDarkCommand.java MultrunCommand.java ShutdownCommand.java \
		StatusExposureLengthCommand.java StatusExposureMultrunCommand.java \
		StatusExposureRunCommand.java StatusExposureStartTimeCommand.java StatusExposureStatusCommand.java \
		StatusMultrunCountCommand.java StatusMultrunIndexCommand.java StatusMultrunBinXCommand.java \
		StatusMultrunBinYCommand.java StatusTemperatureGetCommand.java StatusTemperatureStatusCommand.java \
		TemperatureSetCommand.java


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
