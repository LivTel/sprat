# Makefile
# $HeadURL$
include ../../../../../Makefile.common
include	../../../../Makefile.common

PACKAGEDIR	= ngat/sprat/test
BINDIR 		= $(SPRAT_BIN_HOME)/java/$(PACKAGEDIR)
DOCSDIR 	= $(SPRAT_DOC_HOME)/javadocs/$(PACKAGEDIR)
DOCFLAGS 	= -version -author -private

SRCS 		= SicfTCPClientConnectionThread.java SicfTCPServerConnectionThread.java SicfTCPServer.java \
		SendConfigCommand.java SendGetStatusCommand.java SendMultrunCommand.java 
#SendDarkCommand.java \
#		SendMultBiasCommand.java SendMultDarkCommand.java 
# SendBiasCommand.java 
OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)
DOCS 		= $(SRCS:%.java=$(DOCSDIR)/%.html)

top: $(OBJS)

$(BINDIR)/%.class:%.java
	$(JAVAC) -sourcepath .:/home/dev/src:/home/dev/src/sprat/java \
		-classpath $(SPRAT_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) -d $(SPRAT_BIN_HOME)/java $<

clean: tidy
	-$(RM) $(RM_OPTIONS) $(OBJS)

tidy:
	-$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)

backup: tidy
