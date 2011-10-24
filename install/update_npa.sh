#!/bin/bash

# Update an existing NPA installation

INSTALL_DIR=/usr/local/npa
NPAUSER=npa
NPAGROUP=npa

cd ..
echo Updating NPA installtion to new version ..
$INSTALL_DIR/bin/npa stop
cp bin/* $INSTALL_DIR/bin/
cp wrapper-lib/* $INSTALL_DIR/wrapper-lib
cp lib/* $INSTALL_DIR/lib/
cp npa.jar $INSTALL_DIR
echo Changing $INSTALL_DIR/bin/npa file..
sed "s/RUN_AS_USER=npa/RUN_AS_USER=$NPAUSER/" $INSTALL_DIR/bin/npa > $INSTALL_DIR/bin/npa.new
mv $INSTALL_DIR/bin/npa.new $INSTALL_DIR/bin/npa


DATE=$(date +%d%m%y%H%M%S)
echo Renaming old npa.xml to npa.$DATE
mkdir $INSTALL_DIR/config/new-config 2>/dev/null
cp $INSTALL_DIR/config/npa.xml $INSTALL_DIR/config/npa.xml.$DATE
cp config/defaults.groovy $INSTALL_DIR/config/defaults.groovy.newrelease

# Backup old defaults and then copy in proxy and submittion config to new file
echo Renaming defaults.groovy to defaults.groovy.$DATE
cp $INSTALL_DIR/config/defaults.groovy $INSTALL_DIR/config/defaults.groovy.$DATE
echo Transferring proxy and submit config...
grep -v proxy config/defaults.groovy | grep -v submit > /tmp/def$$
egrep "(proxy|submit)" $INSTALL_DIR/config/defaults.groovy > /tmp/olddef$$

cp /tmp/olddef$$ $INSTALL_DIR/config/defaults.groovy
cat /tmp/def$$ >> $INSTALL_DIR/config/defaults.groovy

# Backup old defaults and then copy in proxy and submittion config to new file
echo Renaming wrapper.confto wrapper.conf.$DATE
cp $INSTALL_DIR/config/wrapper.conf $INSTALL_DIR/config/wrapper.conf.$DATE
cp config/wrapper.conf $INSTALL_DIR/config/wrapper.conf

echo Transferring java command line..
JAVA_COMM=$(grep wrapper.java.command $INSTALL_DIR/config/wrapper.conf.$DATE | egrep -v '^#')
OLD_JAVA_COMM=$(grep wrapper.java.command $INSTALL_DIR/config/wrapper.conf | egrep -v '^#')
sed -i "s@${OLD_JAVA_COMM}@${JAVA_COMM}@" $INSTALL_DIR/config/wrapper.conf


cp -r config/samples $INSTALL_DIR/config/


echo Changing ownership and permissions of files..
chmod 755 $INSTALL_DIR/bin/*
chmod 600 $INSTALL_DIR/config/npa.xml
chmod 600 $INSTALL_DIR/config/defaults.groovy
chown -R $NPAUSER:$NPAGROUP $INSTALL_DIR


$INSTALL_DIR/bin/npa start

echo Done.
