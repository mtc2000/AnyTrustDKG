#!/bin/bash

bucketName=replace-this-by-your-bucket-name
binaryName=binary.01.tar.gz
instanceScript=instance.sh

instanceStdoutLog=instanceStdout.log
instanceStderrLog=instanceStderr.log
instanceFullLog=instanceFull.log

# install dependencies or using a 

# update yum src
yum update -y

# install utility tools
yum install -y tmux htop less tar gzip

mkdir /experiments
cd /experiments

# download the program to run
aws s3 cp s3://$bucketName/$binaryName $binaryName
tar xf $binaryName

if [ ! -f "$instanceScript" ]; then
    echo "$instanceScript does not exist."
    exit 1
fi

# run script per instance
bash "$instanceScript" "$bucketName" "$binaryName"
