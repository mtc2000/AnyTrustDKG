#!/bin/bash

# supply a valid experiment folder path in ../run/logs/ to this script
# it reports the status of the provisioned instances.

if [ -z "$1" ]; then
    echo ExperimentFolder missing
    exit 1
fi

expFolder=$(echo $1 | sed 's/\/$//')
expId=$(basename $1 | cut -d'-' -f2-)

read -p "check expId: $expId"

tempFile=$(mktemp)

echo > $tempFile
while IFS=',' read -r region count;
do
    if [ "$count" == 0 ]; then
        continue
    fi
    printf "$region,"
    aws ec2 describe-instances \
    --region "$region" \
    --filters "Name=tag:experimentId,Values=$expId" \
              "Name=instance-state-name,Values=running" \
    --query "Reservations[*].Instances[*].[PublicIpAddress]" \
    --output text | tee -a $tempFile
done < $expFolder/tmp.*
echo '-----------------'
sort -V $tempFile | head
cat $tempFile | wc -l
read -p "kill them all? (y/N) " option
if [ "$option" != "y" ]; then
    echo do nothing
    exit 0
fi
while IFS=',' read -r region count;
do
    instanceIds=$(aws ec2 describe-instances \
    --region "$region" \
    --filters "Name=tag:experimentId,Values=$expId" \
              "Name=instance-state-name,Values=running" \
    --query "Reservations[*].Instances[*].[InstanceId]" \
    --output text)

    if [ -z "$instanceIds" ]; then
        continue
    fi

    aws ec2 terminate-instances \
    --region "$region" \
    --instance-ids $instanceIds
done < $expFolder/tmp.*
