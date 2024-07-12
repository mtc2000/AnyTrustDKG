#!/bin/bash

# supply a valid experiment folder path in ../run/logs/ to this script
# it reports the status of the provisioned instances.

if [ -z "$1" ]; then
    echo ExperimentId folder missing
    exit 1
fi

expFolder=$(echo $1 | sed 's/\/$//')

tempFile=$(mktemp)

printf '' > $tempFile
while IFS=',' read -r region count;
do
    if [ ! -f "$expFolder/$region" ]; then
        continue
    fi

    echo "$region,"
    # cat $expFolder/$region | jq '.Instances[].InstanceId' | xargs -I {} echo {}

    expId=$(cat $expFolder/$region | jq '.Instances[0].Tags[2].Value')

    if [ -z "$expId" ]; then
        continue
    fi

    aws ec2 describe-instances \
    --region "$region" \
    --instance-ids $(cat $expFolder/$region | jq '.Instances[].InstanceId' | xargs -I {} echo {}) \
    --query "Reservations[*].Instances[*].[PublicIpAddress,State.Name,Placement.AvailabilityZone]" \
    --output text | tr '\t' ',' | tee -a $tempFile
done < $expFolder/tmp.*
echo '------------------------------------'
sort -V $tempFile | head
echo '------------------------------------'
echo total count:
cat $tempFile | wc -l
echo running count:
cat $tempFile | grep 'running' | wc -l
echo pending count:
cat $tempFile | grep 'pending' | wc -l
read -p "view pending? (Y/n) " option
if [ "$option" != "n" ]; then
    echo $tempFile
    cat $tempFile | grep 'pending' | tee -a $expFolder/pending
fi
read -p "kill them all? (y/N) " option
if [ "$option" != "y" ]; then
    echo do nothing
    exit 0
fi
while IFS=',' read -r region count;
do
    if [ ! -f "$expFolder/$region" ]; then
        continue
    fi

    instanceIds=$(cat $expFolder/$region | jq '.Instances[].InstanceId' | xargs -I {} echo {})

    if [ -z "$instanceIds" ]; then
        continue
    fi

    aws ec2 terminate-instances \
    --region "$region" \
    --instance-ids $instanceIds > /dev/null &
done < $expFolder/tmp.*
