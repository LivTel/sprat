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

# DO NOT DELETE

/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/signal.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/features.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/sys/cdefs.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/wordsize.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/gnu/stubs.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/gnu/stubs-32.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/sigset.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/typesizes.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/signum.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/siginfo.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/sigaction.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/asm/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/sigstack.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/pthreadtypes.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/sigthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/stdio.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/libio.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/_G_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/gconv.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/stdio_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/sys_errlist.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/stdlib.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/sys/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/sys/select.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/select.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/sys/sysmacros.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/alloca.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/string.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/log_udp/include/log_udp.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/commandserver/include/command_server.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/sprat/ccd/include/ccd_fits_filename.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/sprat/ccd/include/ccd_global.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/sprat/ccd/include/ccd_setup.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/sprat/ccd/include/ccd_temperature.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/sprat/include/sprat_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/sprat/include/sprat_global.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/pthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /usr/include/bits/setjmp.h
/home/dev/bin/sprat/c/i386-linux/sprat_main.o: /home/dev/src/sprat/include/sprat_server.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/features.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/sys/cdefs.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/wordsize.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/gnu/stubs.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/gnu/stubs-32.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/linux/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/asm/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/asm-generic/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/asm-generic/errno-base.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/stdio.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/typesizes.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/libio.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/_G_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/gconv.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/stdio_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/sys_errlist.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/string.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/unistd.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/posix_opt.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/confname.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/getopt.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/log_udp/include/log_udp.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/commandserver/include/command_server.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/sprat/ccd/include/ccd_exposure.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/sprat/ccd/include/ccd_fits_header.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/cfitsio3310/include/fitsio.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/stdlib.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/sys/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/sys/select.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/select.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/sigset.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/sys/sysmacros.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/pthreadtypes.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/alloca.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/limits.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/syslimits.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/limits.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/posix1_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/local_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/linux/limits.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/posix2_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/cfitsio3310/include/longnam.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/sprat/ccd/include/ccd_fits_filename.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/sprat/ccd/include/ccd_setup.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/sprat/ccd/include/ccd_temperature.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/sprat/include/sprat_command.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/sprat/include/sprat_global.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/pthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/signal.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/signum.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/siginfo.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/sigaction.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/asm/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/sigstack.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/sigthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /usr/include/bits/setjmp.h
/home/dev/bin/sprat/c/i386-linux/sprat_command.o: /home/dev/src/sprat/include/sprat_multrun.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/stdio.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/features.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/sys/cdefs.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/wordsize.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/gnu/stubs.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/gnu/stubs-32.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/typesizes.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/libio.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/_G_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/gconv.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/stdio_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/sys_errlist.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/string.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/unistd.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/posix_opt.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/confname.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/getopt.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /home/dev/src/estar/config/include/estar_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /home/dev/src/log_udp/include/log_udp.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /home/dev/src/sprat/include/sprat_global.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/pthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/signal.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/sigset.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/signum.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/siginfo.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/sigaction.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/asm/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/sigstack.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/pthreadtypes.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/sigthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /usr/include/bits/setjmp.h
/home/dev/bin/sprat/c/i386-linux/sprat_config.o: /home/dev/src/sprat/include/sprat_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/features.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/sys/cdefs.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/wordsize.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/gnu/stubs.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/gnu/stubs-32.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/linux/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/asm/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/asm-generic/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/asm-generic/errno-base.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/pthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/typesizes.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/signal.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/sigset.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/signum.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/siginfo.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/sigaction.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/asm/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/sigstack.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/pthreadtypes.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/sigthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/setjmp.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/stdio.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/libio.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/_G_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/gconv.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/stdio_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/sys_errlist.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/stdlib.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/sys/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/sys/select.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/select.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/sys/sysmacros.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/alloca.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/string.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/unistd.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/posix_opt.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/bits/confname.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /usr/include/getopt.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /home/dev/src/log_udp/include/log_create.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /home/dev/src/log_udp/include/log_udp.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /home/dev/src/log_udp/include/log_general.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /home/dev/src/commandserver/include/command_server.h
/home/dev/bin/sprat/c/i386-linux/sprat_global.o: /home/dev/src/sprat/include/sprat_global.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/features.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/sys/cdefs.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/wordsize.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/gnu/stubs.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/gnu/stubs-32.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/linux/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/asm/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/asm-generic/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/asm-generic/errno-base.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/stdio.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/typesizes.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/libio.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/_G_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/gconv.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/stdio_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/sys_errlist.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/string.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/unistd.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/posix_opt.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/confname.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/getopt.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /home/dev/src/log_udp/include/log_udp.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /home/dev/src/sprat/ccd/include/ccd_fits_filename.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /home/dev/src/sprat/ccd/include/ccd_fits_header.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /home/dev/src/cfitsio3310/include/fitsio.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/stdlib.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/sys/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/sys/select.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/select.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/sigset.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/sys/sysmacros.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/pthreadtypes.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/alloca.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/limits.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/syslimits.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/limits.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/posix1_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/local_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/linux/limits.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/posix2_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /home/dev/src/cfitsio3310/include/longnam.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /home/dev/src/sprat/include/sprat_global.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/pthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/signal.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/signum.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/siginfo.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/sigaction.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/asm/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/sigstack.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/sigthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /usr/include/bits/setjmp.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /home/dev/src/sprat/include/sprat_multrun.h
/home/dev/bin/sprat/c/i386-linux/sprat_multrun.o: /home/dev/src/sprat/ccd/include/ccd_setup.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/features.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/sys/cdefs.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/wordsize.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/gnu/stubs.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/gnu/stubs-32.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/linux/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/asm/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/asm-generic/errno.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/asm-generic/errno-base.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/stdio.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/types.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/typesizes.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/libio.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/_G_config.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/wchar.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/gconv.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/stdio_lim.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/sys_errlist.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/string.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/time.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/unistd.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/posix_opt.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/confname.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/getopt.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /home/dev/src/log_udp/include/log_udp.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /home/dev/src/commandserver/include/command_server.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /home/dev/src/sprat/include/sprat_command.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /home/dev/src/sprat/include/sprat_global.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/pthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/endian.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/sched.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/signal.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/sigset.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/signum.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/siginfo.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/sigaction.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/asm/sigcontext.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/sigstack.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/pthreadtypes.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/sigthread.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /usr/include/bits/setjmp.h
/home/dev/bin/sprat/c/i386-linux/sprat_server.o: /home/dev/src/sprat/include/sprat_server.h
