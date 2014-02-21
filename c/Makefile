# $HeadURL$

include ../../Makefile.common
include ../Makefile.common
include ../ccd/Makefile.common

BINDIR			= $(SPRAT_BIN_HOME)/c/$(HOSTTYPE)
INCDIR 			= $(SPRAT_SRC_HOME)/include
DOCSDIR 		= $(SPRAT_DOC_HOME)/cdocs

DEBUG_CFLAGS		= -DSPRAT_DEBUG=10

# log_udp library
LOG_UDP_CFLAGS		= -I$(LOG_UDP_SRC_HOME)/include
LOG_UDP_LDFLAGS		= -l$(LOG_UDP_HOME)

# command_server library
COMMANDSERVER_HOME        = commandserver
COMMANDSERVER_SRC_HOME    = $(LT_SRC_HOME)/$(COMMANDSERVER_HOME)
COMMANDSERVER_LIBRARYNAME = $(COMMANDSERVER_HOME)
COMMAND_SERVER_CFLAGS 	  = -I$(COMMANDSERVER_SRC_HOME)/include
COMMAND_SERVER_LDFLAGS 	  = -l$(COMMANDSERVER_LIBRARYNAME)

# ccd library
CCD_CFLAGS 		= -I$(SPRAT_CCD_SRC_HOME)/include
CCD_LDFLAGS 		= -l$(SPRAT_CCD_LIBNAME)

# CFITSIO
CFITSIO_CFLAGS		= -I$(CFITSIOINCDIR) 
CFITSIO_LDFLAGS		= -lcfitsio

# NGAT Astro MJD link informatiom
MJD_CFLAGS 		= -DNGATASTRO=1 -I${LT_SRC_HOME}/ngatastro/include
MJD_LDFLAGS		= -lngatastro

# estar_config
CONFIG_CFLAGS		= -I$(eSTAR_SRC_HOME)/config/include/
CONFIG_LDFLAGS		= -L$(eSTAR_LIB_HOME) -lestar_config

CFLAGS 			= -g -I$(INCDIR) $(DEBUG_CFLAGS) $(LOG_UDP_CFLAGS) $(COMMAND_SERVER_CFLAGS) \
				$(CCD_CFLAGS) $(CFITSIO_CFLAGS) $(MJD_CFLAGS) $(CONFIG_CFLAGS) 
DOCFLAGS 		= -static

EXE_SRCS		= sprat_main.c
OBJ_SRCS		= sprat_command.c sprat_config.c sprat_global.c sprat_multrun.c sprat_server.c

SRCS			= $(EXE_SRCS) $(OBJ_SRCS)
HEADERS			= $(OBJ_SRCS:%.c=$(INCDIR)/%.h)
EXE_OBJS		= $(EXE_SRCS:%.c=$(BINDIR)/%.o)
OBJS			= $(SRCS:%.c=$(BINDIR)/%.o)
EXES			= $(BINDIR)/sprat
DOCS 			= $(SRCS:%.c=$(DOCSDIR)/%.html)
CONFIG_SRCS		= ltobs9.sprat.c.properties sprat1.sprat.c.properties
CONFIG_BINS		= $(CONFIG_SRCS:%.properties=$(BINDIR)/%.properties)
SCRIPT_SRCS		= start_sprat_ltobs9.csh start_sprat_sprat1.csh
SCRIPT_BINS		= $(SCRIPT_SRCS:%=$(BINDIR)/%)

top: $(EXES) $(CONFIG_BINS) $(SCRIPT_BINS) docs

$(BINDIR)/sprat: $(OBJS)
	$(CC) $^ -o $@  -L$(LT_LIB_HOME) $(COMMAND_SERVER_LDFLAGS) \
		$(LOG_UDP_LDFLAGS) $(CCD_LDFLAGS) $(ANDOR_LDFLAGS) $(CFITSIO_LDFLAGS) $(MJD_LDFLAGS) \
		$(CONFIG_LDFLAGS) $(TIMELIB) $(SOCKETLIB) -lm -lc
$(BINDIR)/%.o: %.c
	$(CC) -c $(CFLAGS) $< -o $@  

$(BINDIR)/%.properties: %.properties
	$(CP) $< $@  

$(BINDIR)/%: %
	$(CP) $< $@  

docs: $(DOCS)

$(DOCS): $(SRCS)
	-$(CDOC) -d $(DOCSDIR) -h $(INCDIR) $(DOCFLAGS) $(SRCS)

$(DOCS) : $(SRCS)

depend:
	makedepend $(MAKEDEPENDFLAGS) -p$(BINDIR)/ -- $(CFLAGS) -- $(SRCS)

clean:
	$(RM) $(RM_OPTIONS) $(EXES) $(OBJS) $(TIDY_OPTIONS)

tidy:
	$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)

backup: tidy
	$(RM) $(RM_OPTIONS) $(OBJS)

checkin:
	-$(CI) $(CI_OPTIONS) $(SRCS) $(CONFIG_SRCS)
	-(cd $(INCDIR); $(CI) $(CI_OPTIONS) $(HEADERS);)

checkout:
	-$(CO) $(CO_OPTIONS) $(SRCS) $(CONFIG_SRCS)
	-(cd $(INCDIR); $(CO) $(CO_OPTIONS) $(HEADERS);)

