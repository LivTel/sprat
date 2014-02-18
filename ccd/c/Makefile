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

checkout:
	-$(CO) $(CO_OPTIONS) $(SRCS)
	-(cd $(INCDIR); $(CO) $(CO_OPTIONS) $(HEADERS);)

checkin:
	-$(CI) $(CI_OPTIONS) $(SRCS)
	-(cd $(INCDIR); $(CI) $(CI_OPTIONS) $(HEADERS);)

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


