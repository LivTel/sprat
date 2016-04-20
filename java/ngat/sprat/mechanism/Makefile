# Makefile
# $HeadURL$
include ../../../../Makefile.common
include ../../../../../Makefile.common

MAKE 		= make
PACKAGEDIR	= ngat/sprat/mechanism
DIRS 		= command
BINDIR 		= $(SPRAT_BIN_HOME)/java/$(PACKAGEDIR)
SRCS 		= MoveInOutMechanism.java MoveRotationMechanism.java MoveBrokenSlitMechanism.java
OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)


top: dirs objs

dirs:
	@for i in $(DIRS); \
	do \
		(echo making in $$i...; cd $$i; $(MAKE) ); \
	done;

objs: $(OBJS)

$(BINDIR)/%.class:%.java
	$(JAVAC) -sourcepath .:$(SPRAT_SRC_HOME)/java \
		-classpath $(SPRAT_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) -d $(SPRAT_BIN_HOME)/java $<

depend:
	echo no depend in ngat directory.

clean:
	-$(RM) $(RM_OPTIONS) $(OBJS)
	-$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)
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

backup: 
	@for i in $(DIRS); \
	do \
		(echo backup in $$i...; cd $$i; $(MAKE) backup); \
	done;
