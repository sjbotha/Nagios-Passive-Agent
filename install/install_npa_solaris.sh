#!/bin/bash

NPAUSER=oradev
NPAGROUP=dbadev
INSTALLDIR=/usr/local/corelogic/npa

if [ $(who am i | awk '{print $1}') == 'root' ]; then
  echo Root access is available, creating user and group.
  groupadd $NPAGROUP
  useradd -G $NPAGROUP $NPAUSER


mkdir -p $INSTALLDIR/db

cd ..
cp -r npa.jar db config lib wrapper-lib bin logs $INSTALLDIR
if [ $? -eq 0 ]; then
        echo Copied files ok.
else
        echo Copying files to $INSTALLDIR produced errors!
fi

echo Changing $INSTALLDIR/bin/npa file..
sed "s/RUN_AS_USER=npa/RUN_AS_USER=$NPAUSER/" $INSTALLDIR/bin/npa > $INSTALLDIR/bin/npa.new
mv $INSTALLDIR/bin/npa.new $INSTALLDIR/bin/npa
chmod 755 $INSTALLDIR/bin/*

chown -R $NPAUSER:$NPAGROUP $INSTALLDIR
if [ $? -eq 0 ]; then
        echo Installtion completed.  NPA installed in $INSTALLDIR
else
        Change of ownership failed!
        exit 1
fi


if [ $(who am i | awk '{print $1}') == 'root' ]; then
ln -s $INSTALLDIR/bin/npa /etc/init.d/npa
ln -s /etc/init.d/npa /etc/rc3.d/S99npa
ln -s /etc/init.d/npa /etc/rc1.d/K01npa
ln -s /etc/init.d/npa /etc/rc2.d/K01npa

if [ $? -eq 0 ]; then

        echo Successfully installed npa service.
        exit 0
else
        echo Chkconfig command to install npa service failed!
        exit 1
fi

  echo No root access, service was not installed.
fi
