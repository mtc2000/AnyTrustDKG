#!/bin/bash

# This script will query the AMI ID `new_ami_id` of an AMI with name `ami_name` in all regions in `regions.txt`

# Replace these variables with your actual values
ami_name="ReplaceThisByYourOwnAmiName"

# Check if the regions file exists
if [ ! -f "regions.txt" ]; then
    echo "Error: regions.txt file not found."
    exit 1
fi

# Read regions from the file into an array
target_regions=($(cat regions.txt))

# Loop through target regions and copy AMI if source and target regions are different
for target_region in "${target_regions[@]}"; do

    new_ami_id=$(aws ec2 describe-images \
    --no-dry-run \
    --region "$target_region" \
    --filters "Name=name,Values=$ami_name" \
    --query 'Images[*].[ImageId, State]' \
    --output text | tr '\t' ',')

    echo $target_region,$new_ami_id
done | tee query-amis.csv

# this script will create a CSV file (query-amis.csv) with contents similar to the following:

# us-east-1,ami-xxxxxxxxxxxxxxxxx
# us-east-2,ami-xxxxxxxxxxxxxxxxx
# us-west-1,ami-xxxxxxxxxxxxxxxxx
# us-west-2,ami-xxxxxxxxxxxxxxxxx
# ca-central-1,ami-xxxxxxxxxxxxxxxxx
# ap-south-1,ami-xxxxxxxxxxxxxxxxx
# ap-northeast-1,ami-xxxxxxxxxxxxxxxxx
# ap-northeast-2,ami-xxxxxxxxxxxxxxxxx
# ap-northeast-3,ami-xxxxxxxxxxxxxxxxx
# ap-southeast-1,ami-xxxxxxxxxxxxxxxxx
# ap-southeast-2,ami-xxxxxxxxxxxxxxxxx
# eu-central-1,ami-xxxxxxxxxxxxxxxxx
# eu-west-1,ami-xxxxxxxxxxxxxxxxx
# eu-west-2,ami-xxxxxxxxxxxxxxxxx
# eu-west-3,ami-xxxxxxxxxxxxxxxxx
# eu-north-1,ami-xxxxxxxxxxxxxxxxx
# sa-east-1,ami-xxxxxxxxxxxxxxxxx