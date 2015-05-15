# $HeadURL$

include			../../../Makefile.common
include			../../Makefile.common
include			../Makefile.common

INCDIR = $(SPRAT_CCD_SRC_HOME)/include
DOCSDIR = $(SPRAT_CCD_DOC_HOME)/cdocs

# Do we wish to link with CFITSIO routines?
FITSCFLAGS = -DCFITSIO=1 -I$(CFITSIOINCDIR) 
CFITSIOLIB = -lcfitsio

# Do we want to enable logging?
LOGGINGCFLAGS = -DLOGGING=10

# NGAT Astro MJD link informatiom
MJDCFLAGS 	= -DNGATASTRO=1 -I${LT_SRC_HOME}/ngatastro/include
MJDLIB		= -lngatastro


CFLAGS = -g $(CCHECKFLAG) -I$(INCDIR) -I$(JNIINCDIR) -I$(JNIMDINCDIR) $(ANDOR_CFLAGS) \
	$(FITSCFLAGS) $(MJDCFLAGS) $(LOGGINGCFLAGS) $(LOG_UDP_CFLAGS)

LINTFLAGS = -I$(INCDIR) -I$(JNIINCDIR) -I$(JNIMDINCDIR)
DOCFLAGS = -static
SRCS 		= 	ccd_global.c ccd_setup.c ccd_temperature.c ccd_exposure.c \
			ccd_fits_filename.c ccd_fits_header.c
HEADERS		=	$(SRCS:%.c=%.h)
OBJS		=	$(SRCS:%.c=%.o)
DOCS 		= 	$(SRCS:%.c=$(DOCSDIR)/%.html)
LIBNAME		=	lib$(SPRAT_HOME)_$(SPRAT_CCD_HOME)

top: shared docs

shared: $(LT_LIB_HOME)/$(LIBNAME).so

$(LT_LIB_HOME)/$(LIBNAME).so: $(OBJS)
	cc $(CCSHAREDFLAG) $(CFLAGS) $(OBJS) -o $@ -L$(LT_LIB_HOME) $(TIMELIB) $(CFITSIOLIB) $(MJDLIB) $(ANDOR_LDFLAGS)

static: $(LT_LIB_HOME)/$(LIBNAME).a

$(LT_LIB_HOME)/$(LIBNAME).a: $(OBJS)
	ar rcv $@ $?

docs: $(DOCS)

$(DOCS): $(SRCS)
	-$(CDOC) -d $(DOCSDIR) -h $(INCDIR) $(DOCFLAGS) $(SRCS)

$(DOCS) : $(SRCS)

staticdepend:
	makedepend $(MAKEDEPENDFLAGS) -- $(CFLAGS) -- $(SRCS)

depend:
	makedepend $(MAKEDEPENDFLAGS) -- $(CFLAGS) -- $(SRCS)

lint:
	$(LINT)	$(LINTFLAGS) $(SRCS)

clean:
	-$(RM) $(RM_OPTIONS) $(OBJS) $(LT_LIB_HOME)/$(LIBNAME).so $(LT_LIB_HOME)/$(LIBNAME)_g.so $(LT_LIB_HOME)/$(LIBNAME).a $(TIDY_OPTIONS)

tidy:
	-$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)
	-(cd $(INCDIR); $(RM) $(RM_OPTIONS) $(TIDY_OPTIONS);)

backup: tidy
	-$(RM) $(RM_OPTIONS) $(OBJS) 


# DO NOT DELETE

ccd_global.o: /usr/include/stdio.h /usr/include/features.h
ccd_global.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
ccd_global.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
ccd_global.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
ccd_global.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
ccd_global.o: /usr/include/libio.h /usr/include/_G_config.h
ccd_global.o: /usr/include/wchar.h /usr/include/bits/wchar.h
ccd_global.o: /usr/include/gconv.h
ccd_global.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
ccd_global.o: /usr/include/bits/stdio_lim.h /usr/include/bits/sys_errlist.h
ccd_global.o: /usr/include/errno.h /usr/include/bits/errno.h
ccd_global.o: /usr/include/linux/errno.h /usr/include/asm/errno.h
ccd_global.o: /usr/include/asm-generic/errno.h
ccd_global.o: /usr/include/asm-generic/errno-base.h
ccd_global.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/limits.h
ccd_global.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/syslimits.h
ccd_global.o: /usr/include/limits.h /usr/include/bits/posix1_lim.h
ccd_global.o: /usr/include/bits/local_lim.h /usr/include/linux/limits.h
ccd_global.o: /usr/include/bits/posix2_lim.h /usr/include/sys/types.h
ccd_global.o: /usr/include/time.h /usr/include/string.h
ccd_global.o: /usr/include/sys/time.h /usr/include/bits/time.h
ccd_global.o: /usr/include/sys/select.h /usr/include/bits/select.h
ccd_global.o: /usr/include/bits/sigset.h /usr/include/unistd.h
ccd_global.o: /usr/include/bits/posix_opt.h /usr/include/bits/confname.h
ccd_global.o: /usr/include/getopt.h
ccd_global.o: /home/dev/src/andor/andor-2.85.30000/include/atmcdLXd.h
ccd_global.o: /home/dev/src/log_udp/include/log_udp.h
ccd_global.o: /home/dev/src/sprat/ccd/include/ccd_exposure.h
ccd_global.o: /home/dev/src/sprat/ccd/include/ccd_fits_header.h
ccd_global.o: /home/dev/src/cfitsio3310/include/fitsio.h
ccd_global.o: /usr/include/stdlib.h
ccd_global.o: /home/dev/src/cfitsio3310/include/longnam.h
ccd_global.o: /home/dev/src/sprat/ccd/include/ccd_fits_filename.h
ccd_global.o: /home/dev/src/sprat/ccd/include/ccd_global.h
ccd_global.o: /home/dev/src/sprat/ccd/include/ccd_setup.h
ccd_global.o: /home/dev/src/sprat/ccd/include/ccd_temperature.h
ccd_setup.o: /usr/include/stdio.h /usr/include/features.h
ccd_setup.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
ccd_setup.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
ccd_setup.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
ccd_setup.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
ccd_setup.o: /usr/include/libio.h /usr/include/_G_config.h
ccd_setup.o: /usr/include/wchar.h /usr/include/bits/wchar.h
ccd_setup.o: /usr/include/gconv.h
ccd_setup.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
ccd_setup.o: /usr/include/bits/stdio_lim.h /usr/include/bits/sys_errlist.h
ccd_setup.o: /usr/include/stdlib.h /usr/include/string.h /usr/include/fcntl.h
ccd_setup.o: /usr/include/bits/fcntl.h /usr/include/sys/types.h
ccd_setup.o: /usr/include/time.h /usr/include/signal.h
ccd_setup.o: /usr/include/bits/sigset.h /usr/include/bits/signum.h
ccd_setup.o: /usr/include/bits/siginfo.h /usr/include/bits/sigaction.h
ccd_setup.o: /usr/include/errno.h /usr/include/bits/errno.h
ccd_setup.o: /usr/include/linux/errno.h /usr/include/asm/errno.h
ccd_setup.o: /usr/include/asm-generic/errno.h
ccd_setup.o: /usr/include/asm-generic/errno-base.h /usr/include/math.h
ccd_setup.o: /usr/include/bits/huge_val.h /usr/include/bits/mathdef.h
ccd_setup.o: /usr/include/bits/mathcalls.h /usr/include/unistd.h
ccd_setup.o: /usr/include/bits/posix_opt.h /usr/include/bits/confname.h
ccd_setup.o: /usr/include/getopt.h
ccd_setup.o: /home/dev/src/andor/andor-2.85.30000/include/atmcdLXd.h
ccd_setup.o: /home/dev/src/log_udp/include/log_udp.h
ccd_setup.o: /home/dev/src/sprat/ccd/include/ccd_global.h
ccd_setup.o: /home/dev/src/sprat/ccd/include/ccd_setup.h
ccd_temperature.o: /usr/include/stdio.h /usr/include/features.h
ccd_temperature.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
ccd_temperature.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
ccd_temperature.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
ccd_temperature.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
ccd_temperature.o: /usr/include/libio.h /usr/include/_G_config.h
ccd_temperature.o: /usr/include/wchar.h /usr/include/bits/wchar.h
ccd_temperature.o: /usr/include/gconv.h
ccd_temperature.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
ccd_temperature.o: /usr/include/bits/stdio_lim.h
ccd_temperature.o: /usr/include/bits/sys_errlist.h /usr/include/string.h
ccd_temperature.o: /usr/include/math.h /usr/include/bits/huge_val.h
ccd_temperature.o: /usr/include/bits/mathdef.h /usr/include/bits/mathcalls.h
ccd_temperature.o: /usr/include/time.h
ccd_temperature.o: /home/dev/src/andor/andor-2.85.30000/include/atmcdLXd.h
ccd_temperature.o: /home/dev/src/log_udp/include/log_udp.h
ccd_temperature.o: /home/dev/src/sprat/ccd/include/ccd_global.h
ccd_temperature.o: /home/dev/src/sprat/ccd/include/ccd_setup.h
ccd_temperature.o: /home/dev/src/sprat/ccd/include/ccd_temperature.h
ccd_exposure.o: /usr/include/stdio.h /usr/include/features.h
ccd_exposure.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
ccd_exposure.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
ccd_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
ccd_exposure.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
ccd_exposure.o: /usr/include/libio.h /usr/include/_G_config.h
ccd_exposure.o: /usr/include/wchar.h /usr/include/bits/wchar.h
ccd_exposure.o: /usr/include/gconv.h
ccd_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
ccd_exposure.o: /usr/include/bits/stdio_lim.h /usr/include/bits/sys_errlist.h
ccd_exposure.o: /usr/include/stdlib.h /usr/include/string.h
ccd_exposure.o: /usr/include/fcntl.h /usr/include/bits/fcntl.h
ccd_exposure.o: /usr/include/sys/types.h /usr/include/time.h
ccd_exposure.o: /usr/include/signal.h /usr/include/bits/sigset.h
ccd_exposure.o: /usr/include/bits/signum.h /usr/include/bits/siginfo.h
ccd_exposure.o: /usr/include/bits/sigaction.h /usr/include/errno.h
ccd_exposure.o: /usr/include/bits/errno.h /usr/include/linux/errno.h
ccd_exposure.o: /usr/include/asm/errno.h /usr/include/asm-generic/errno.h
ccd_exposure.o: /usr/include/asm-generic/errno-base.h /usr/include/math.h
ccd_exposure.o: /usr/include/bits/huge_val.h /usr/include/bits/mathdef.h
ccd_exposure.o: /usr/include/bits/mathcalls.h /usr/include/unistd.h
ccd_exposure.o: /usr/include/bits/posix_opt.h /usr/include/bits/confname.h
ccd_exposure.o: /usr/include/getopt.h
ccd_exposure.o: /home/dev/src/andor/andor-2.85.30000/include/atmcdLXd.h
ccd_exposure.o: /home/dev/src/cfitsio3310/include/fitsio.h
ccd_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/limits.h
ccd_exposure.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/syslimits.h
ccd_exposure.o: /usr/include/limits.h /usr/include/bits/posix1_lim.h
ccd_exposure.o: /usr/include/bits/local_lim.h /usr/include/linux/limits.h
ccd_exposure.o: /usr/include/bits/posix2_lim.h
ccd_exposure.o: /home/dev/src/cfitsio3310/include/longnam.h
ccd_exposure.o: /home/dev/src/log_udp/include/log_udp.h
ccd_exposure.o: /home/dev/src/sprat/ccd/include/ccd_global.h
ccd_exposure.o: /home/dev/src/sprat/ccd/include/ccd_exposure.h
ccd_exposure.o: /home/dev/src/sprat/ccd/include/ccd_fits_header.h
ccd_exposure.o: /home/dev/src/sprat/ccd/include/ccd_fits_filename.h
ccd_exposure.o: /home/dev/src/sprat/ccd/include/ccd_setup.h
ccd_exposure.o: /home/dev/src/sprat/ccd/include/ccd_temperature.h
ccd_exposure.o: /home/dev/src/ngatastro/include/ngat_astro.h
ccd_exposure.o: /home/dev/src/ngatastro/include/ngat_astro_mjd.h
ccd_fits_filename.o: /usr/include/errno.h /usr/include/features.h
ccd_fits_filename.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
ccd_fits_filename.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
ccd_fits_filename.o: /usr/include/bits/errno.h /usr/include/linux/errno.h
ccd_fits_filename.o: /usr/include/asm/errno.h
ccd_fits_filename.o: /usr/include/asm-generic/errno.h
ccd_fits_filename.o: /usr/include/asm-generic/errno-base.h
ccd_fits_filename.o: /usr/include/stdio.h
ccd_fits_filename.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
ccd_fits_filename.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
ccd_fits_filename.o: /usr/include/libio.h /usr/include/_G_config.h
ccd_fits_filename.o: /usr/include/wchar.h /usr/include/bits/wchar.h
ccd_fits_filename.o: /usr/include/gconv.h
ccd_fits_filename.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
ccd_fits_filename.o: /usr/include/bits/stdio_lim.h
ccd_fits_filename.o: /usr/include/bits/sys_errlist.h /usr/include/stdlib.h
ccd_fits_filename.o: /usr/include/string.h /usr/include/sys/time.h
ccd_fits_filename.o: /usr/include/time.h /usr/include/bits/time.h
ccd_fits_filename.o: /usr/include/sys/select.h /usr/include/bits/select.h
ccd_fits_filename.o: /usr/include/bits/sigset.h /usr/include/unistd.h
ccd_fits_filename.o: /usr/include/bits/posix_opt.h
ccd_fits_filename.o: /usr/include/bits/confname.h /usr/include/getopt.h
ccd_fits_filename.o: /usr/include/dirent.h /usr/include/bits/dirent.h
ccd_fits_filename.o: /usr/include/sys/types.h /usr/include/sys/stat.h
ccd_fits_filename.o: /usr/include/bits/stat.h /usr/include/fcntl.h
ccd_fits_filename.o: /usr/include/bits/fcntl.h
ccd_fits_filename.o: /home/dev/src/log_udp/include/log_udp.h
ccd_fits_filename.o: /home/dev/src/sprat/ccd/include/ccd_fits_filename.h
ccd_fits_filename.o: /home/dev/src/sprat/ccd/include/ccd_global.h
ccd_fits_header.o: /usr/include/errno.h /usr/include/features.h
ccd_fits_header.o: /usr/include/sys/cdefs.h /usr/include/bits/wordsize.h
ccd_fits_header.o: /usr/include/gnu/stubs.h /usr/include/gnu/stubs-32.h
ccd_fits_header.o: /usr/include/bits/errno.h /usr/include/linux/errno.h
ccd_fits_header.o: /usr/include/asm/errno.h /usr/include/asm-generic/errno.h
ccd_fits_header.o: /usr/include/asm-generic/errno-base.h /usr/include/stdio.h
ccd_fits_header.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stddef.h
ccd_fits_header.o: /usr/include/bits/types.h /usr/include/bits/typesizes.h
ccd_fits_header.o: /usr/include/libio.h /usr/include/_G_config.h
ccd_fits_header.o: /usr/include/wchar.h /usr/include/bits/wchar.h
ccd_fits_header.o: /usr/include/gconv.h
ccd_fits_header.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/stdarg.h
ccd_fits_header.o: /usr/include/bits/stdio_lim.h
ccd_fits_header.o: /usr/include/bits/sys_errlist.h /usr/include/stdlib.h
ccd_fits_header.o: /usr/include/string.h /usr/include/time.h
ccd_fits_header.o: /usr/include/unistd.h /usr/include/bits/posix_opt.h
ccd_fits_header.o: /usr/include/bits/confname.h /usr/include/getopt.h
ccd_fits_header.o: /home/dev/src/cfitsio3310/include/fitsio.h
ccd_fits_header.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/limits.h
ccd_fits_header.o: /usr/lib/gcc/i386-redhat-linux/4.1.2/include/syslimits.h
ccd_fits_header.o: /usr/include/limits.h /usr/include/bits/posix1_lim.h
ccd_fits_header.o: /usr/include/bits/local_lim.h /usr/include/linux/limits.h
ccd_fits_header.o: /usr/include/bits/posix2_lim.h
ccd_fits_header.o: /home/dev/src/cfitsio3310/include/longnam.h
ccd_fits_header.o: /home/dev/src/log_udp/include/log_udp.h
ccd_fits_header.o: /home/dev/src/sprat/ccd/include/ccd_fits_header.h
ccd_fits_header.o: /home/dev/src/sprat/ccd/include/ccd_global.h
