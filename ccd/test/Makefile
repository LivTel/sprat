# $Header$

include ../../../Makefile.common
include ../../Makefile.common
include ../Makefile.common

INCDIR 		= $(SPRAT_CCD_SRC_HOME)/include
TESTDIR 	= test
BINDIR 		= $(SPRAT_CCD_BIN_HOME)/$(TESTDIR)/$(HOSTTYPE)
DOCSDIR 	= $(SPRAT_CCD_DOC_HOME)/$(TESTDIR)

CFLAGS 		= -g -I$(INCDIR) -I$(CFITSIOINCDIR) -I$(JNIINCDIR) -I$(JNIMDINCDIR) $(LOG_UDP_CFLAGS)
LDFLAGS		= -L$(LT_LIB_HOME) $(ANDOR_LDFLAGS) -l$(LIBNAME) -lcfitsio $(TIMELIB) $(SOCKETLIB) -lm -lc 
DOCFLAGS 	= -static

SRCS 		= test_exposure_andor_lowlevel.c test_temperature_andor_lowlevel.c test_camera_select.c \
		test_temperature.c test_exposure.c
OBJS 		= $(SRCS:%.c=%.o)
PROGS 		= $(SRCS:%.c=$(BINDIR)/%)
DOCS 		= $(SRCS:%.c=$(DOCSDIR)/%.html)
SCRIPT_SRCS	= 
SCRIPT_BINS	= $(SCRIPT_SRCS:%=$(BINDIR)/%)
LIBNAME		= $(SPRAT_HOME)_$(SPRAT_CCD_HOME)

top: $(PROGS) scripts docs

$(BINDIR)/%: %.o
	$(CC) -o $@ $< -L$(LT_LIB_HOME) -l$(LIBNAME) -lcfitsio $(TIMELIB) $(SOCKETLIB) -lm -lc $(ANDOR_LDFLAGS)

$(BINDIR)/test_exposure_andor_lowlevel: $(BINDIR)/test_exposure_andor_lowlevel.o
	$(CC) -o $@ $< -L$(LT_LIB_HOME)  -lcfitsio $(TIMELIB) $(SOCKETLIB) -lm -lc $(ANDOR_LDFLAGS)

$(BINDIR)/test_exposure_andor_lowlevel.o: test_exposure_andor_lowlevel.c
	$(CC) -o $@ -c $< -I$(CFITSIOINCDIR) $(ANDOR_CFLAGS)

$(BINDIR)/test_temperature_andor_lowlevel: $(BINDIR)/test_temperature_andor_lowlevel.o
	$(CC) -o $@ $< -L$(LT_LIB_HOME)  -lcfitsio $(TIMELIB) $(SOCKETLIB) -lm -lc $(ANDOR_LDFLAGS)

$(BINDIR)/test_temperature_andor_lowlevel.o: test_temperature_andor_lowlevel.c
	$(CC) -o $@ -c $< -I$(CFITSIOINCDIR) $(ANDOR_CFLAGS)

$(BINDIR)/test_camera_select: $(BINDIR)/test_camera_select.o
	$(CC) -o $@ $< -L$(LT_LIB_HOME)  -lcfitsio $(TIMELIB) $(SOCKETLIB) -lm -lc $(ANDOR_LDFLAGS)

$(BINDIR)/test_camera_select.o: test_camera_select.c
	$(CC) -o $@ -c $< -I$(CFITSIOINCDIR) $(ANDOR_CFLAGS)

$(BINDIR)/%: %.o
	$(CC) -o $@ $< $(LDFLAGS) 

scripts: $(SCRIPT_BINS)

$(BINDIR)/%.csh:%.csh
	$(CP) $< $@

$(BINDIR)/%:%
	$(CP) $< $@

docs: $(DOCS)

$(DOCS): $(SRCS)
	-$(CDOC) -d $(DOCSDIR) -h $(INCDIR) $(DOCFLAGS) $(SRCS)

depend:
	makedepend $(MAKEDEPENDFLAGS) -- $(CFLAGS) $(ANDOR_CFLAGS) -- $(SRCS)

clean:
	$(RM) $(RM_OPTIONS) $(OBJS) $(PROGS) $(TIDY_OPTIONS)

tidy:
	$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)

backup: tidy
	$(RM) $(RM_OPTIONS) $(OBJS)

checkin:
	-$(CI) $(CI_OPTIONS) $(SRCS)

checkout:
	-$(CO) $(CO_OPTIONS) $(SRCS)

#
# $Log$
#
# DO NOT DELETE

test_exposure_andor_lowlevel.o: /usr/include/stdio.h /usr/include/features.h
test_exposure_andor_lowlevel.o: /usr/include/sys/cdefs.h
test_exposure_andor_lowlevel.o: /usr/include/bits/wordsize.h
test_exposure_andor_lowlevel.o: /usr/include/gnu/stubs.h
test_exposure_andor_lowlevel.o: /usr/include/gnu/stubs-32.h
test_exposure_andor_lowlevel.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
test_exposure_andor_lowlevel.o: /usr/include/bits/types.h
test_exposure_andor_lowlevel.o: /usr/include/bits/typesizes.h
test_exposure_andor_lowlevel.o: /usr/include/libio.h /usr/include/_G_config.h
test_exposure_andor_lowlevel.o: /usr/include/wchar.h
test_exposure_andor_lowlevel.o: /usr/include/bits/wchar.h
test_exposure_andor_lowlevel.o: /usr/include/gconv.h
test_exposure_andor_lowlevel.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
test_exposure_andor_lowlevel.o: /usr/include/bits/stdio_lim.h
test_exposure_andor_lowlevel.o: /usr/include/bits/sys_errlist.h
test_exposure_andor_lowlevel.o: /usr/include/stdlib.h /usr/include/string.h
test_exposure_andor_lowlevel.o: /usr/include/fcntl.h
test_exposure_andor_lowlevel.o: /usr/include/bits/fcntl.h
test_exposure_andor_lowlevel.o: /usr/include/sys/types.h /usr/include/time.h
test_exposure_andor_lowlevel.o: /usr/include/signal.h
test_exposure_andor_lowlevel.o: /usr/include/bits/sigset.h
test_exposure_andor_lowlevel.o: /usr/include/bits/signum.h
test_exposure_andor_lowlevel.o: /usr/include/bits/siginfo.h
test_exposure_andor_lowlevel.o: /usr/include/bits/sigaction.h
test_exposure_andor_lowlevel.o: /usr/include/errno.h
test_exposure_andor_lowlevel.o: /usr/include/bits/errno.h
test_exposure_andor_lowlevel.o: /usr/include/linux/errno.h
test_exposure_andor_lowlevel.o: /usr/include/asm/errno.h
test_exposure_andor_lowlevel.o: /usr/include/asm-generic/errno.h
test_exposure_andor_lowlevel.o: /usr/include/asm-generic/errno-base.h
test_exposure_andor_lowlevel.o: /usr/include/math.h
test_exposure_andor_lowlevel.o: /usr/include/bits/huge_val.h
test_exposure_andor_lowlevel.o: /usr/include/bits/mathdef.h
test_exposure_andor_lowlevel.o: /usr/include/bits/mathcalls.h
test_exposure_andor_lowlevel.o: /usr/include/unistd.h
test_exposure_andor_lowlevel.o: /usr/include/bits/posix_opt.h
test_exposure_andor_lowlevel.o: /usr/include/bits/confname.h
test_exposure_andor_lowlevel.o: /usr/include/getopt.h
test_exposure_andor_lowlevel.o: /home/dev/src/andor/andor-2.85.30000/include/atmcdLXd.h
test_exposure_andor_lowlevel.o: /home/dev/src/cfitsio3310/include/fitsio.h
test_exposure_andor_lowlevel.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/limits.h
test_exposure_andor_lowlevel.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/syslimits.h
test_exposure_andor_lowlevel.o: /usr/include/limits.h
test_exposure_andor_lowlevel.o: /usr/include/bits/posix1_lim.h
test_exposure_andor_lowlevel.o: /usr/include/bits/local_lim.h
test_exposure_andor_lowlevel.o: /usr/include/linux/limits.h
test_exposure_andor_lowlevel.o: /usr/include/bits/posix2_lim.h
test_exposure_andor_lowlevel.o: /home/dev/src/cfitsio3310/include/longnam.h
test_temperature_andor_lowlevel.o: /usr/include/stdio.h
test_temperature_andor_lowlevel.o: /usr/include/features.h
test_temperature_andor_lowlevel.o: /usr/include/sys/cdefs.h
test_temperature_andor_lowlevel.o: /usr/include/bits/wordsize.h
test_temperature_andor_lowlevel.o: /usr/include/gnu/stubs.h
test_temperature_andor_lowlevel.o: /usr/include/gnu/stubs-32.h
test_temperature_andor_lowlevel.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
test_temperature_andor_lowlevel.o: /usr/include/bits/types.h
test_temperature_andor_lowlevel.o: /usr/include/bits/typesizes.h
test_temperature_andor_lowlevel.o: /usr/include/libio.h
test_temperature_andor_lowlevel.o: /usr/include/_G_config.h
test_temperature_andor_lowlevel.o: /usr/include/wchar.h
test_temperature_andor_lowlevel.o: /usr/include/bits/wchar.h
test_temperature_andor_lowlevel.o: /usr/include/gconv.h
test_temperature_andor_lowlevel.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
test_temperature_andor_lowlevel.o: /usr/include/bits/stdio_lim.h
test_temperature_andor_lowlevel.o: /usr/include/bits/sys_errlist.h
test_temperature_andor_lowlevel.o: /usr/include/stdlib.h
test_temperature_andor_lowlevel.o: /usr/include/string.h /usr/include/fcntl.h
test_temperature_andor_lowlevel.o: /usr/include/bits/fcntl.h
test_temperature_andor_lowlevel.o: /usr/include/sys/types.h
test_temperature_andor_lowlevel.o: /usr/include/time.h /usr/include/signal.h
test_temperature_andor_lowlevel.o: /usr/include/bits/sigset.h
test_temperature_andor_lowlevel.o: /usr/include/bits/signum.h
test_temperature_andor_lowlevel.o: /usr/include/bits/siginfo.h
test_temperature_andor_lowlevel.o: /usr/include/bits/sigaction.h
test_temperature_andor_lowlevel.o: /usr/include/errno.h
test_temperature_andor_lowlevel.o: /usr/include/bits/errno.h
test_temperature_andor_lowlevel.o: /usr/include/linux/errno.h
test_temperature_andor_lowlevel.o: /usr/include/asm/errno.h
test_temperature_andor_lowlevel.o: /usr/include/asm-generic/errno.h
test_temperature_andor_lowlevel.o: /usr/include/asm-generic/errno-base.h
test_temperature_andor_lowlevel.o: /usr/include/math.h
test_temperature_andor_lowlevel.o: /usr/include/bits/huge_val.h
test_temperature_andor_lowlevel.o: /usr/include/bits/mathdef.h
test_temperature_andor_lowlevel.o: /usr/include/bits/mathcalls.h
test_temperature_andor_lowlevel.o: /usr/include/unistd.h
test_temperature_andor_lowlevel.o: /usr/include/bits/posix_opt.h
test_temperature_andor_lowlevel.o: /usr/include/bits/confname.h
test_temperature_andor_lowlevel.o: /usr/include/getopt.h
test_temperature_andor_lowlevel.o: /home/dev/src/andor/andor-2.85.30000/include/atmcdLXd.h
test_camera_select.o: /usr/include/stdio.h /usr/include/features.h
test_camera_select.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
test_camera_select.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
test_camera_select.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
test_camera_select.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
test_camera_select.o: /usr/include/libio.h /usr/include/_G_config.h
test_camera_select.o: /usr/include/wchar.h /usr/include/bits/wchar.h
test_camera_select.o: /usr/include/gconv.h
test_camera_select.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
test_camera_select.o: /usr/include/bits/stdio_lim.h
test_camera_select.o: /usr/include/bits/sys_errlist.h /usr/include/stdlib.h
test_camera_select.o: /usr/include/string.h /usr/include/fcntl.h
test_camera_select.o: /usr/include/bits/fcntl.h /usr/include/sys/types.h
test_camera_select.o: /usr/include/time.h /usr/include/signal.h
test_camera_select.o: /usr/include/bits/sigset.h /usr/include/bits/signum.h
test_camera_select.o: /usr/include/bits/siginfo.h
test_camera_select.o: /usr/include/bits/sigaction.h /usr/include/errno.h
test_camera_select.o: /usr/include/bits/errno.h /usr/include/linux/errno.h
test_camera_select.o: /usr/include/asm/errno.h
test_camera_select.o: /usr/include/asm-generic/errno.h
test_camera_select.o: /usr/include/asm-generic/errno-base.h
test_camera_select.o: /usr/include/math.h /usr/include/bits/huge_val.h
test_camera_select.o: /usr/include/bits/mathdef.h
test_camera_select.o: /usr/include/bits/mathcalls.h /usr/include/unistd.h
test_camera_select.o: /usr/include/bits/posix_opt.h
test_camera_select.o: /usr/include/bits/confname.h /usr/include/getopt.h
test_camera_select.o: /home/dev/src/andor/andor-2.85.30000/include/atmcdLXd.h
test_temperature.o: /usr/include/stdio.h /usr/include/features.h
test_temperature.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
test_temperature.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
test_temperature.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
test_temperature.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
test_temperature.o: /usr/include/libio.h /usr/include/_G_config.h
test_temperature.o: /usr/include/wchar.h /usr/include/bits/wchar.h
test_temperature.o: /usr/include/gconv.h
test_temperature.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
test_temperature.o: /usr/include/bits/stdio_lim.h
test_temperature.o: /usr/include/bits/sys_errlist.h /usr/include/string.h
test_temperature.o: /usr/include/stdlib.h /usr/include/time.h
test_temperature.o: /home/dev/src/sprat/ccd/include/ccd_global.h
test_temperature.o: /home/dev/src/sprat/ccd/include/ccd_temperature.h
test_exposure.o: /usr/include/stdio.h /usr/include/features.h
test_exposure.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
test_exposure.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
test_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
test_exposure.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
test_exposure.o: /usr/include/libio.h /usr/include/_G_config.h
test_exposure.o: /usr/include/wchar.h /usr/include/bits/wchar.h
test_exposure.o: /usr/include/gconv.h
test_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
test_exposure.o: /usr/include/bits/stdio_lim.h
test_exposure.o: /usr/include/bits/sys_errlist.h /usr/include/string.h
test_exposure.o: /usr/include/time.h
test_exposure.o: /home/dev/src/cfitsio3310/include/fitsio.h
test_exposure.o: /usr/include/stdlib.h
test_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/limits.h
test_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/syslimits.h
test_exposure.o: /usr/include/limits.h /usr/include/bits/posix1_lim.h
test_exposure.o: /usr/include/bits/local_lim.h /usr/include/linux/limits.h
test_exposure.o: /usr/include/bits/posix2_lim.h
test_exposure.o: /home/dev/src/cfitsio3310/include/longnam.h
test_exposure.o: /home/dev/src/sprat/ccd/include/ccd_exposure.h
test_exposure.o: /home/dev/src/sprat/ccd/include/ccd_fits_header.h
test_exposure.o: /home/dev/src/sprat/ccd/include/ccd_global.h
test_exposure.o: /home/dev/src/sprat/ccd/include/ccd_setup.h
