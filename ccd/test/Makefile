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
	makedepend $(MAKEDEPENDFLAGS) -- $(CFLAGS) -- $(SRCS)

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

test_exposure.o: /usr/include/stdio.h /usr/include/features.h
test_exposure.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
test_exposure.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
test_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/stddef.h
test_exposure.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
test_exposure.o: /usr/include/libio.h /usr/include/_G_config.h
test_exposure.o: /usr/include/wchar.h /usr/include/bits/wchar.h
test_exposure.o: /usr/include/gconv.h
test_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/stdarg.h
test_exposure.o: /usr/include/bits/stdio_lim.h
test_exposure.o: /usr/include/bits/sys_errlist.h /usr/include/string.h
test_exposure.o: /usr/include/time.h /usr/include/bits/time.h
test_exposure.o: /home/dev/src/log_udp/include/log_udp.h
test_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_temperature.h
test_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_setup.h
test_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_exposure.h
test_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_global.h
test_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_fits_header.h
test_exposure.o: /home/dev/src/cfitsio3200/include/fitsio.h
test_exposure.o: /usr/include/stdlib.h /usr/include/sys/types.h
test_exposure.o: /usr/include/endian.h /usr/include/bits/endian.h
test_exposure.o: /usr/include/sys/select.h /usr/include/bits/select.h
test_exposure.o: /usr/include/bits/sigset.h /usr/include/sys/sysmacros.h
test_exposure.o: /usr/include/bits/pthreadtypes.h /usr/include/alloca.h
test_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/limits.h
test_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/syslimits.h
test_exposure.o: /usr/include/limits.h /usr/include/bits/posix1_lim.h
test_exposure.o: /usr/include/bits/local_lim.h /usr/include/linux/limits.h
test_exposure.o: /usr/include/bits/posix2_lim.h
test_exposure.o: /home/dev/src/cfitsio3200/include/longnam.h
test_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_fits_filename.h
test_temperature.o: /usr/include/stdio.h /usr/include/features.h
test_temperature.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
test_temperature.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
test_temperature.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/stddef.h
test_temperature.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
test_temperature.o: /usr/include/libio.h /usr/include/_G_config.h
test_temperature.o: /usr/include/wchar.h /usr/include/bits/wchar.h
test_temperature.o: /usr/include/gconv.h
test_temperature.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/stdarg.h
test_temperature.o: /usr/include/bits/stdio_lim.h
test_temperature.o: /usr/include/bits/sys_errlist.h /usr/include/string.h
test_temperature.o: /usr/include/time.h /usr/include/bits/time.h
test_temperature.o: /home/dev/src/log_udp/include/log_udp.h
test_temperature.o: /home/dev/src/ringo3/ccd/include/ccd_temperature.h
test_temperature.o: /home/dev/src/ringo3/ccd/include/ccd_setup.h
test_temperature.o: /home/dev/src/ringo3/ccd/include/ccd_exposure.h
test_temperature.o: /home/dev/src/ringo3/ccd/include/ccd_global.h
test_temperature.o: /home/dev/src/ringo3/ccd/include/ccd_fits_header.h
test_temperature.o: /home/dev/src/cfitsio3200/include/fitsio.h
test_temperature.o: /usr/include/stdlib.h /usr/include/sys/types.h
test_temperature.o: /usr/include/endian.h /usr/include/bits/endian.h
test_temperature.o: /usr/include/sys/select.h /usr/include/bits/select.h
test_temperature.o: /usr/include/bits/sigset.h /usr/include/sys/sysmacros.h
test_temperature.o: /usr/include/bits/pthreadtypes.h /usr/include/alloca.h
test_temperature.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/limits.h
test_temperature.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/syslimits.h
test_temperature.o: /usr/include/limits.h /usr/include/bits/posix1_lim.h
test_temperature.o: /usr/include/bits/local_lim.h /usr/include/linux/limits.h
test_temperature.o: /usr/include/bits/posix2_lim.h
test_temperature.o: /home/dev/src/cfitsio3200/include/longnam.h
test_temperature.o: /home/dev/src/ringo3/ccd/include/ccd_fits_filename.h
test_multi_exposure.o: /usr/include/stdio.h /usr/include/features.h
test_multi_exposure.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
test_multi_exposure.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
test_multi_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/stddef.h
test_multi_exposure.o: /usr/include/bits/types.h
test_multi_exposure.o: /usr/include/bits/typesizes.h /usr/include/libio.h
test_multi_exposure.o: /usr/include/_G_config.h /usr/include/wchar.h
test_multi_exposure.o: /usr/include/bits/wchar.h /usr/include/gconv.h
test_multi_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/stdarg.h
test_multi_exposure.o: /usr/include/bits/stdio_lim.h
test_multi_exposure.o: /usr/include/bits/sys_errlist.h /usr/include/string.h
test_multi_exposure.o: /usr/include/time.h /usr/include/bits/time.h
test_multi_exposure.o: /home/dev/src/log_udp/include/log_udp.h
test_multi_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_temperature.h
test_multi_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_setup.h
test_multi_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_multi_exposure.h
test_multi_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_global.h
test_multi_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_exposure.h
test_multi_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_fits_header.h
test_multi_exposure.o: /home/dev/src/cfitsio3200/include/fitsio.h
test_multi_exposure.o: /usr/include/stdlib.h /usr/include/sys/types.h
test_multi_exposure.o: /usr/include/endian.h /usr/include/bits/endian.h
test_multi_exposure.o: /usr/include/sys/select.h /usr/include/bits/select.h
test_multi_exposure.o: /usr/include/bits/sigset.h
test_multi_exposure.o: /usr/include/sys/sysmacros.h
test_multi_exposure.o: /usr/include/bits/pthreadtypes.h /usr/include/alloca.h
test_multi_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/limits.h
test_multi_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.1/include/syslimits.h
test_multi_exposure.o: /usr/include/limits.h /usr/include/bits/posix1_lim.h
test_multi_exposure.o: /usr/include/bits/local_lim.h
test_multi_exposure.o: /usr/include/linux/limits.h
test_multi_exposure.o: /usr/include/bits/posix2_lim.h
test_multi_exposure.o: /home/dev/src/cfitsio3200/include/longnam.h
test_multi_exposure.o: /home/dev/src/ringo3/ccd/include/ccd_fits_filename.h
