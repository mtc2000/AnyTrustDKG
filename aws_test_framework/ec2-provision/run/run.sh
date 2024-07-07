#!/bin/bash

# Check if the regions file exists
if [ "$#" -lt 6 ]; then
    echo not enough arguments
    echo "protocol(\$1) expId(\$experimentId) gsize(\$3) atSize(\$4) repeats(\$5) version(\$6)"
    exit 1
fi

if [ ! -f ../preparation/amis.csv]; then
    echo ../preparation/amis.csv does not exist!
    exit 1
fi

protocol=$1

groupSize=$3
atSize=$4
repeats=$5
version=$6

experimentId=$1-$2-atSize$4

echo "protocol($1) expId($experimentId) gsize($3) atSize($4) repeats($5) version($6)"
read -p "confirm"

# Get the number of lines in the file
num_lines=$(cat regions.txt | wc -l)

# Calculate the quotient and remainder
quotient=$((groupSize / num_lines))
remainder=$((groupSize % num_lines))

echo q="$quotient" r="$remainder"

runningInstances=$(mktemp)

# Distribute the size evenly among the lines
awk -v q="$quotient" -v r="$remainder" '{ print $0 "," (NR <= r ? q + 1 : q) }' "regions.txt" | tee "$runningInstances" | grep -v ',0'

echo simple checks passed!
echo total group size $groupSize

aws s3 rm s3://replace-this-by-your-bucket-name/replace-this-by-any-name/GroupSize$groupSize-ExpId$experimentId/ --recursive --dryrun

if [ -d "logs/$groupSize-$experimentId" ]; then
    read -p "press to delete logs/$groupSize-$experimentId"
    rm -rf logs/$groupSize-$experimentId
fi


mkdir -p logs/$groupSize-$experimentId

read -p "press to continue"

cp -t logs/$groupSize-$experimentId "$runningInstances"

aws s3 rm s3://replace-this-by-your-bucket-name/replace-this-by-any-name/GroupSize$groupSize-ExpId$experimentId/ --recursive

# Iterate over each row in the text
cat "$runningInstances" | while IFS=',' read -r region count; do

    if [ "$count" == 0 ]; then
        continue
    fi

    echo $region,$count
    imageId=$(cat ../preparation/amis.csv | grep $region | cut -d',' -f2)
    # for ATDKG, the following AMI is used:
    # us-east-1
    # ami-04e5276ebb8451442
    # Amazon Linux 2023 AMI 2023.4.20240416.0 x86_64 HVM kernel-6.1

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
        {Key=atSize,Value=$atSize},\
        {Key=protocol,Value=$protocol}]" > logs/$groupSize-$experimentId/$region
done
