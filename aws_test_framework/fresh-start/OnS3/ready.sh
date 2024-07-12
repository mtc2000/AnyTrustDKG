#!/bin/bash

publicIp=$1
experimentPrefix=$2
bucketName=$3
readyList=$4
groupSize=$5

experimentFolder="s3://$bucketName/$experimentPrefix"
readySize=-1

# report ready to s3
touch "$publicIp"

aws s3 cp "$publicIp" "$experimentFolder/ready/$publicIp"
while true
do
    # check if file is ready on S3
    aws s3 cp "$experimentFolder/ready/$publicIp" "$publicIp.s3"
    if [ -f "$publicIp.s3" ]; then
        rm "$publicIp.s3"
        break
    fi
    # retry upload
    aws s3 cp "$publicIp" "$experimentFolder/ready/$publicIp"
done

# warning note: when the number of objects within the `ready` folder is less than the group size,
# the experiment never starts.

while [ "$readySize" != "$groupSize" ];
do
    printf "group size: $groupSize currently seeing # servers ready:"
    echo $readySize
    readySize=$(aws s3api list-objects \
        --bucket "$bucketName" \
        --prefix "$experimentPrefix/ready/" \
        --output text \
        --query "Contents[*].[Key]" | \
        xargs -I {} basename {} | \
        sort -V | \
        tee $readyList | \
        wc -l)
    echo sleeping 0.5s!
    sleep 0.5
done
