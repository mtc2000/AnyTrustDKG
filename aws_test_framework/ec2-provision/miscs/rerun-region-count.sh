#!/bin/bash

# this may not work properly. :\
# please use with caution.

# this script manually provision `count` number of instances in a specific `region`
# according to ec2simple-01.json and user-script-01.sh

# Check if the regions file exists
if [ "$#" -lt 7 ]; then
    echo not enough arguments
    exit 1
fi

protocol=$1
experimentId=$1-$2
groupSize=$3
batchSize=$4
repeats=$5
version=$6
region=$7
count=$8

echo "protocol($1) expId($experimentId) gsize($3) bsize($4) repeats($5) version($6) region($7) count($8)"
read -p "confirm"

echo simple checks passed!
echo total group size $groupSize


mkdir -p logs/$groupSize-$experimentId

read -p "press to continue"

echo $region,$count
imageId=$(cat ../preparation/amis.csv | grep $region | cut -d',' -f2)

echo $imageId

read

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
    {Key=protocol,Value=$protocol}]" | \
    tee -a logs/$groupSize-$experimentId/re-$region
