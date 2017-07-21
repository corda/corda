Summary: Corda DemoBench
Name: corda-demobench
Version: @pkg_version@
Release: 1
License: Unknown
Vendor: Unknown
Prefix: /opt
Provides: corda-demobench
Requires: ld-linux.so.2 libX11.so.6 libXext.so.6 libXi.so.6 libXrender.so.1 libXtst.so.6 libasound.so.2 libc.so.6 libdl.so.2 libgcc_s.so.1 libm.so.6 libpthread.so.0 libthread_db.so.1
Obsoletes: demobench
Autoprov: 0
Autoreq: 0

#avoid ARCH subfolder
%define _rpmfilename %%{NAME}-%%{VERSION}-%%{RELEASE}.%%{ARCH}.rpm

#comment line below to enable effective jar compression
#it could easily get your package size from 40 to 15Mb but 
#build time will substantially increase and it may require unpack200/system java to install
%define __jar_repack %{nil}

%define _javaHome %{getenv:JAVA_HOME}
%define _bugfixdir %{_sourcedir}/CordaDemoBench/app/bugfixes
%define _rtJar %{_sourcedir}/CordaDemoBench/runtime/lib/rt.jar

%description
Corda DemoBench

%prep

%build

%install
# Apply bugfixes to installed rt.jar
if [ -f %{_bugfixdir}/apply.sh ]; then
    chmod ugo+x %{_bugfixdir}/apply.sh
    %{_bugfixdir}/apply.sh %{_rtJar}
    rm -rf %{_bugfixdir}
fi
rm -rf %{buildroot}
mkdir -p %{buildroot}/opt
cp -r %{_sourcedir}/CordaDemoBench %{buildroot}/opt
mkdir -p %{buildroot}/opt/CordaDemoBench/runtime/bin
cp %{_javaHome}/bin/java %{buildroot}/opt/CordaDemoBench/runtime/bin

%files

/opt/CordaDemoBench

%post


xdg-desktop-menu install --novendor /opt/CordaDemoBench/CordaDemoBench.desktop

if [ "false" = "true" ]; then
    cp /opt/CordaDemoBench/corda-demobench.init /etc/init.d/corda-demobench
    if [ -x "/etc/init.d/corda-demobench" ]; then
        /sbin/chkconfig --add corda-demobench
        if [ "false" = "true" ]; then
            /etc/init.d/corda-demobench start
        fi
    fi
fi

%preun

xdg-desktop-menu uninstall --novendor /opt/CordaDemoBench/CordaDemoBench.desktop

if [ "false" = "true" ]; then
    if [ -x "/etc/init.d/corda-demobench" ]; then
        if [ "true" = "true" ]; then
            /etc/init.d/corda-demobench stop
        fi
        /sbin/chkconfig --del corda-demobench
        rm -f /etc/init.d/corda-demobench
    fi
fi

%clean
