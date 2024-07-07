#!/bin/bash

# fetch all IP ready files in a specific repeat and save them to `ready.txt`.
# this is useful when a repeat takes abnormally longer than expected time to complete.
# when the number of objects within the `ready` folder is less than
# the group size, the experiment never starts.

bucketName=replace-this-by-your-bucket-name
experimentPrefix=replace-this-by-any-name/replace-this-by-a-valid-experiment-folder/replace-this-by-a-specific-hanging-repeat

aws s3api list-objects \
 --bucket "$bucketName" \
 --prefix "$experimentPrefix/ready/" \
 --output text \
 --query "Contents[*].[Key]" | \
 xargs -I {} basename {} | \
 sort -V | \
 tee ready.txt | \
 wc -l