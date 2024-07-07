#!/bin/bash

bucketName=$1
binaryName=$2
dependencyScript=dependency.sh
readyScript=ready.sh
experimentScript=experiment.sh
configLog=config.log
baseFolder=$(pwd)

# get info
TOKEN=$(curl -s -X PUT "http://instance-data/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")

experimentId=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/meta-data/tags/instance/experimentId)
groupSize=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/meta-data/tags/instance/groupSize)

repeats=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/meta-data/tags/instance/repeats)
if [ -z "$repeats" ]; then
    repeats="8"
fi

atSize=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/meta-data/tags/instance/atSize)
if [ -z "$atSize" ]; then
    atSize="29"
fi

protocol=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/meta-data/tags/instance/protocol)
if [ -z "$protocol" ]; then
    protocol="normal"
fi

# instanceId=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/meta-data/instance-id/)
# region=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/dynamic/instance-identity/document | grep region | awk '{print $3}' | sed  's/"//g'|sed 's/,//g')
privateIp=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/meta-data/local-ipv4)
# publicIp=$(curl -s -f -H "X-aws-ec2-metadata-token: $TOKEN" http://instance-data/latest/meta-data/public-ipv4)
# own_ip=$(curl http://169.254.169.254/latest/meta-data/local-ipv4)

echo private ip is "$privateIp" | tee $configLog
# echo public ip is "$publicIp" | tee $configLog

# install runtime dependencies
bash $dependencyScript

for repeatCount in `seq -w $repeats`
do
    cd $baseFolder
    repeatFolder="Repeat$repeatCount"
    mkdir "$repeatFolder"
    cp -t "$repeatFolder" $binaryName
    cd "$repeatFolder"
    # extract binary file
    tar xf $binaryName

    experimentPrefix="replace-this-by-any-name/GroupSize$groupSize-ExpId$experimentId/"$repeatFolder""
    experimentFolder="s3://$bucketName/$experimentPrefix"
    echo experimentFolder $experimentFolder | tee $configLog

    readyList=allIpsReady.txt

    # wait until enough pairs going online and collect ip addresses in $readyList
    bash "$readyScript" "$privateIp" "$experimentPrefix" "$bucketName" "$readyList" "$groupSize"

    echo size $groupSize start running!
    cat $readyList

    # start experiment
    bash "$experimentScript" "$privateIp" "$readyList" "$atSize" "$protocol" "$experimentFolder"

    # remove this repeat
    rm -rf artifacts

done

# self-terminate
# aws ec2 terminate-instances --instance-ids $instanceId --region $region
echo shutdown in 10 seconds
sleep 10
shutdown +0
