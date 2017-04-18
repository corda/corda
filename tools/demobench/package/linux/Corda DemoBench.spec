Summary: Corda DemoBench
Name: demobench
Version: @pkg_version@
Release: 1
License: Unknown
Vendor: Unknown
Prefix: /opt
Provides: demobench
Requires: ld-linux.so.2 libX11.so.6 libXext.so.6 libXi.so.6 libXrender.so.1 libXtst.so.6 libasound.so.2 libc.so.6 libdl.so.2 libgcc_s.so.1 libm.so.6 libpthread.so.0 libthread_db.so.1
Autoprov: 0
Autoreq: 0

#avoid ARCH subfolder
%define _rpmfilename %%{NAME}-%%{VERSION}-%%{RELEASE}.%%{ARCH}.rpm

#comment line below to enable effective jar compression
#it could easily get your package size from 40 to 15Mb but 
#build time will substantially increase and it may require unpack200/system java to install
%define __jar_repack %{nil}

%define _javaHome %{getenv:JAVA_HOME}

%description
Corda DemoBench

%prep

%build

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}/opt
cp -r %{_sourcedir}/Corda\ DemoBench %{buildroot}/opt
mkdir -p %{buildroot}/opt/Corda\ DemoBench/runtime/bin
cp %{_javaHome}/bin/java %{buildroot}/opt/Corda\ DemoBench/runtime/bin

%files

/opt/Corda\ DemoBench

%post


xdg-desktop-menu install --novendor /opt/Corda\ DemoBench/Corda\ DemoBench.desktop

if [ "false" = "true" ]; then
    cp /opt/Corda\ DemoBench/demobench.init /etc/init.d/demobench
    if [ -x "/etc/init.d/demobench" ]; then
        /sbin/chkconfig --add demobench
        if [ "false" = "true" ]; then
            /etc/init.d/demobench start
        fi
    fi
fi

%preun

xdg-desktop-menu uninstall --novendor /opt/Corda\ DemoBench/Corda\ DemoBench.desktop

if [ "false" = "true" ]; then
    if [ -x "/etc/init.d/demobench" ]; then
        if [ "true" = "true" ]; then
            /etc/init.d/demobench stop
        fi
        /sbin/chkconfig --del demobench
        rm -f /etc/init.d/demobench
    fi
fi

%clean
