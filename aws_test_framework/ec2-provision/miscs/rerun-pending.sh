#!/bin/bash

# this may not work properly. :\
# please use with caution.

# this script attempts to terminate EC2 instances of `pending` status and replace them
# by provisioning new instances while copying old configurations and metadata.

protocol=$1
expId=$1-$2
groupSize=$3
batchSize=$4
repeats=$5
version=$6
expFolder=$7

echo "protocol($1) expId($experimentId) gsize($3) bsize($4) repeats($5) version($6) expFolder($7)"
read -p "confirm"

expFolder=$(echo $expFolder | sed 's/\/$//')

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
              "Name=instance-state-name,Values=pending" \
    --query "Reservations[*].Instances[*].[InstanceId,PublicIpAddress]" \
    --output text | tr '\t' ',' | tee -a $tempFile
done < $expFolder/tmp.*
echo pending count:
cat $tempFile | wc -l
printf '-----------------'
sort -V $tempFile
printf '-----------------'
read -p "replace them all? (y/N) " option
if [ "$option" != "y" ]; then
    echo do nothing
    exit 0
fi

read -p "s3 ready url" s3url
s3url=$(echo $s3url | sed 's/\/$//')
echo $s3url

while IFS=',' read -r region oricount;
do
    regionPending=$(mktemp)

    aws ec2 describe-instances \
    --region "$region" \
    --filters "Name=tag:experimentId,Values=$expId" \
              "Name=instance-state-name,Values=pending" \
    --query "Reservations[*].Instances[*].[InstanceId,PublicIpAddress]" \
    --output text | tr '\t' ',' > $regionPending

    if [ ! -s "$regionPending" ]; then
        continue
    fi

    aws ec2 terminate-instances \
    --region "$region" \
    --instance-ids $(cat $regionPending | cut -d',' -f1)

    sleep 10

    cat $regionPending | cut -d',' -f2 | xargs -I {} aws s3 rm $s3url/{}

    count=$(cat $regionPending | wc -l)

    echo $region,$count
    imageId=$(cat ../preparation/amis.csv | grep $region | cut -d',' -f2)

    aws ec2 run-instances \
        --no-dry-run \
        --count $count \
        --key-name ReplaceThisByYourOwnKey \
        --image-id $imageId \
        --region $region \
        --cli-input-json file://ec2simple-$version.json \
        --user-data file://user-script-$version.sh \
        --tag-specifications "ResourceType=instance,Tags=[\
        {Key=experimentId,Value=$experimentId},\
        {Key=groupSize,Value=$groupSize},\
        {Key=repeats,Value=$repeats},\
        {Key=batchSize,Value=$batchSize},\
        {Key=protocol,Value=$protocol}]" > logs/$groupSize-$experimentId/$region.re

done < $expFolder/tmp.*
